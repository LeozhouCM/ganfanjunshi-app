package com.ganfan.junshi.server.plugins

import com.ganfan.junshi.server.database.tables.*
import com.ganfan.junshi.server.model.*
import com.ganfan.junshi.server.service.AiService
import com.ganfan.junshi.server.service.MatchService
import com.ganfan.junshi.server.service.PushService
import com.ganfan.junshi.server.service.SocialFeedService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import at.favre.lib.crypto.bcrypt.BCrypt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun Application.configureRouting() {
    routing {
        intercept(ApplicationCallPipeline.Call) {
            println("[${call.request.httpMethod.value}] ${call.request.uri}")
        }
        get("/") {
            call.respond(mapOf("app" to "干饭军师 API", "version" to "1.0"))
        }

        // ==================== 认证 ====================
        post("/api/auth/login") {
            val req = call.receive<LoginRequest>()
            val existing = transaction {
                Users.selectAll().where { Users.phone eq req.phone }.singleOrNull()
            }
            if (existing == null) {
                call.respond(ApiResult<LoginResponse>(code = 404, message = "用户不存在，请先注册"))
                return@post
            }
            val passwordHash = existing[Users.passwordHash]
            if (passwordHash == null || !BCrypt.verifyer().verify(req.password.toCharArray(), passwordHash).verified) {
                call.respond(ApiResult<LoginResponse>(code = 401, message = "密码错误"))
                return@post
            }
            val dto = UserDto(
                id = existing[Users.id],
                phone = existing[Users.phone],
                nickname = existing[Users.nickname],
                avatarUrl = existing[Users.avatarUrl],
                gender = existing[Users.gender],
                status = existing[Users.status]
            )
            call.respond(ApiResult(data = LoginResponse(generateToken(dto), dto)))
        }

        // ==================== 注册 ====================
        post("/api/auth/register") {
            val req = call.receive<RegisterRequest>()
            val existing = transaction {
                Users.selectAll().where { Users.phone eq req.phone }.singleOrNull()
            }
            if (existing != null) {
                call.respond(ApiResult<LoginResponse>(code = 409, message = "该手机号已注册"))
                return@post
            }
            val hashedPassword = BCrypt.withDefaults().hashToString(12, req.password.toCharArray())
            val dto = transaction {
                val id = Users.insert {
                    it[phone] = req.phone
                    it[passwordHash] = hashedPassword
                    it[userId2] = req.userId2
                    it[nickname] = req.nickname
                    it[gender] = 0
                    it[status] = 1
                    it[createdAt] = LocalDateTime.now()
                } get Users.id
                UserTasteProfiles.insert {
                    it[userId] = id
                    it[spiceLevel] = 0
                }
                val newUser = Users.selectAll().where { Users.id eq id }.single()
                UserDto(
                    id = newUser[Users.id],
                    phone = newUser[Users.phone],
                    nickname = newUser[Users.nickname],
                    avatarUrl = newUser[Users.avatarUrl],
                    gender = newUser[Users.gender],
                    status = newUser[Users.status]
                )
            }
            call.respond(ApiResult(data = LoginResponse(generateToken(dto), dto)))
        }

        // ==================== 无需认证的 AI 军师接口 ====================
        post("/api/ai/chat/guest") {
            val req = call.receive<AiChatRequest>()
            val reply = if (!req.context.isNullOrBlank()) {
                AiService.replyWithContext(req.message, req.context)
            } else {
                AiService.reply(req.message)
            }
            call.respond(ApiResult(data = AiChatResponse(reply, 0)))
        }

        authenticate("auth-jwt") {
            // ==================== 用户 ====================
            get("/api/users/me") {
                val uid = call.userId()!!
                val user = transaction {
                    Users.selectAll().where { Users.id eq uid }.single()
                }
                call.respond(ApiResult(data = UserDto(
                    user[Users.id], user[Users.phone], user[Users.nickname],
                    user[Users.avatarUrl], user[Users.gender], user[Users.status]
                )))
            }

            // ==================== 餐厅 ====================
            get("/api/restaurants/nearby") {
                val lat = call.request.queryParameters["lat"]?.toDoubleOrNull() ?: 39.9
                val lng = call.request.queryParameters["lng"]?.toDoubleOrNull() ?: 116.4
                val list = transaction {
                    Restaurants.selectAll().map { row ->
                        val dist = row[Restaurants.lat]?.toDouble()?.let { rLat ->
                            row[Restaurants.lng]?.toDouble()?.let { rLng ->
                                val dLat = rLat - lat
                                val dLng = rLng - lng
                                sqrt(dLat * dLat + dLng * dLng) * 111000
                            }
                        }
                        row.toDto(dist)
                    }.sortedBy { it.distance ?: Double.MAX_VALUE }
                }
                call.respond(ApiResult(data = list))
            }

            get("/api/restaurants/{id}") {
                val id = call.parameters["id"]!!.toLong()
                val dto = transaction {
                    Restaurants.selectAll().where { Restaurants.id eq id }.singleOrNull()?.toDto(null)
                }
                if (dto == null) call.respond(ApiResult<RestaurantDto>(code = 404, message = "not found"))
                else call.respond(ApiResult(data = dto))
            }

            // ==================== 拼桌帖 ====================
            get("/api/match/posts") {
                val list = transaction {
                    (MatchPosts innerJoin Users).selectAll().orderBy(MatchPosts.id to SortOrder.DESC).map { row ->
                        val restName = row[MatchPosts.restaurantId]?.let { rid ->
                            Restaurants.selectAll().where { Restaurants.id eq rid }.singleOrNull()?.get(Restaurants.name)
                        }
                        MatchPostDto(
                            row[MatchPosts.id], row[MatchPosts.publisherId], row[Users.nickname],
                            row[MatchPosts.restaurantId], restName, row[MatchPosts.title],
                            row[MatchPosts.mealTime]?.format(dtf) ?: "",
                            row[MatchPosts.maxPeople], row[MatchPosts.currentPeople],
                            row[MatchPosts.budget],
                            row[MatchPosts.lat]?.toDouble(), row[MatchPosts.lng]?.toDouble(),
                            row[MatchPosts.note], row[MatchPosts.status]
                        )
                    }
                }
                call.respond(ApiResult(data = list))
            }

            post("/api/match/posts") {
                val uid = call.userId()!!
                val req = call.receive<CreateMatchRequest>()
                val mealTimeParsed = try {
                    LocalDateTime.parse(req.mealTime, dtf)
                } catch (_: Exception) {
                    LocalDateTime.now().plusHours(2)
                }
                val id = transaction {
                    MatchPosts.insert {
                        it[publisherId] = uid
                        it[restaurantId] = req.restaurantId
                        it[title] = req.title
                        it[mealTime] = mealTimeParsed
                        it[maxPeople] = req.maxPeople
                        it[currentPeople] = 1
                        it[budget] = req.budget
                        it[lat] = req.lat.toBigDecimal()
                        it[lng] = req.lng.toBigDecimal()
                        it[note] = req.note
                        it[status] = "recruiting"
                        it[createdAt] = LocalDateTime.now()
                    } get MatchPosts.id
                }
                val post = transaction {
                    val row = (MatchPosts innerJoin Users).selectAll().where { MatchPosts.id eq id }.single()
                    MatchPostDto(
                        row[MatchPosts.id], row[MatchPosts.publisherId], row[Users.nickname],
                        row[MatchPosts.restaurantId], null, row[MatchPosts.title],
                        row[MatchPosts.mealTime]?.format(dtf) ?: "",
                        row[MatchPosts.maxPeople], row[MatchPosts.currentPeople],
                        row[MatchPosts.budget],
                        row[MatchPosts.lat]?.toDouble(), row[MatchPosts.lng]?.toDouble(),
                        row[MatchPosts.note], row[MatchPosts.status]
                    )
                }
                call.respond(ApiResult(data = post))
            }

            post("/api/match/posts/{id}/join") {
                val postId = call.parameters["id"]!!.toLong()
                val uid = call.userId()!!
                transaction {
                    MatchApplications.insert {
                        it[MatchApplications.postId] = postId
                        it[applicantId] = uid
                        it[status] = "approved"
                        it[createdAt] = LocalDateTime.now()
                    }
                    val current = MatchPosts.selectAll().where { MatchPosts.id eq postId }.single()[MatchPosts.currentPeople]
                    MatchPosts.update({ MatchPosts.id eq postId }) {
                        it[currentPeople] = current + 1
                    }
                }
                call.respond(ApiResult<Unit>(message = "joined"))
            }

            // ==================== 点评 ====================
            get("/api/reviews") {
                val list = transaction {
                    (Reviews innerJoin Users innerJoin Restaurants).selectAll()
                        .orderBy(Reviews.id to SortOrder.DESC).map { it.toReviewDto() }
                }
                call.respond(ApiResult(data = list))
            }

            post("/api/reviews") {
                val uid = call.userId()!!
                val req = call.receive<CreateReviewRequest>()
                val now = LocalDateTime.now()
                val id = transaction {
                    Reviews.insert {
                        it[userId] = uid
                        it[restaurantId] = req.restaurantId
                        it[rating] = req.rating.toBigDecimal()
                        it[content] = req.content
                        it[images] = if (req.images.isNotEmpty()) Gson().toJson(req.images) else null
                        it[status] = "pending"
                        it[likeCount] = 0
                        it[createdAt] = now
                    } get Reviews.id
                }
                val dto = transaction {
                    (Reviews innerJoin Users innerJoin Restaurants).selectAll()
                        .where { Reviews.id eq id }.single().toReviewDto()
                }
                call.respond(ApiResult(data = dto))
            }

            // ==================== 更新用户信息 ====================
            put("/api/users/me") {
                val uid = call.userId()!!
                val req = call.receive<UpdateUserRequest>()
                val dto = transaction {
                    Users.update({ Users.id eq uid }) { stmt ->
                        req.nickname?.let { stmt[nickname] = it }
                        req.avatarUrl?.let { stmt[avatarUrl] = it }
                        req.gender?.let { stmt[gender] = it }
                        stmt[updatedAt] = LocalDateTime.now()
                    }
                    val user = Users.selectAll().where { Users.id eq uid }.single()
                    UserDto(
                        user[Users.id], user[Users.phone], user[Users.nickname],
                        user[Users.avatarUrl], user[Users.gender], user[Users.status]
                    )
                }
                call.respond(ApiResult(data = dto))
            }

            // ==================== 口味画像 ====================
            get("/api/users/me/taste-profile") {
                val uid = call.userId()!!
                val profile = transaction {
                    UserTasteProfiles.selectAll()
                        .where { UserTasteProfiles.userId eq uid }
                        .singleOrNull()
                }
                val dto = if (profile == null) {
                    TasteProfileDto(
                        spiceLevel = 3,
                        avgBudget = null,
                        favoriteCuisines = "",
                        dietaryRestrictions = ""
                    )
                } else {
                    TasteProfileDto(
                        spiceLevel = profile[UserTasteProfiles.spiceLevel],
                        avgBudget = profile[UserTasteProfiles.avgBudget],
                        favoriteCuisines = profile[UserTasteProfiles.favoriteCuisines] ?: "",
                        dietaryRestrictions = profile[UserTasteProfiles.dietaryRestrictions] ?: ""
                    )
                }
                call.respond(ApiResult(data = dto))
            }

            put("/api/users/me/taste-profile") {
                val uid = call.userId()!!
                val req = call.receive<UpdateTasteProfileRequest>()
                val now = LocalDateTime.now()
                transaction {
                    val exists = UserTasteProfiles.selectAll()
                        .where { UserTasteProfiles.userId eq uid }
                        .singleOrNull()
                    if (exists == null) {
                        UserTasteProfiles.insert {
                            it[userId] = uid
                            it[spiceLevel] = req.spiceLevel ?: 3
                            it[avgBudget] = req.avgBudget
                            it[favoriteCuisines] = req.favoriteCuisines
                            it[dietaryRestrictions] = req.dietaryRestrictions
                            it[updatedAt] = now
                        }
                    } else {
                        UserTasteProfiles.update({ UserTasteProfiles.userId eq uid }) { stmt ->
                            req.spiceLevel?.let { v -> stmt[spiceLevel] = v }
                            req.avgBudget?.let { v -> stmt[avgBudget] = v }
                            req.favoriteCuisines?.let { v -> stmt[favoriteCuisines] = v }
                            req.dietaryRestrictions?.let { v -> stmt[dietaryRestrictions] = v }
                            stmt[updatedAt] = now
                        }
                    }
                }
                val updated = transaction {
                    UserTasteProfiles.selectAll()
                        .where { UserTasteProfiles.userId eq uid }
                        .single()
                }
                call.respond(ApiResult(data = TasteProfileDto(
                    spiceLevel = updated[UserTasteProfiles.spiceLevel],
                    avgBudget = updated[UserTasteProfiles.avgBudget],
                    favoriteCuisines = updated[UserTasteProfiles.favoriteCuisines] ?: "",
                    dietaryRestrictions = updated[UserTasteProfiles.dietaryRestrictions] ?: ""
                )))
            }

            get("/api/taste/profile") {
                val uid = call.userId()!!
                val profile = transaction {
                    UserTasteProfiles.selectAll()
                        .where { UserTasteProfiles.userId eq uid }
                        .singleOrNull()
                }
                val dto = if (profile == null) {
                    TasteProfileDto(spiceLevel = 3, avgBudget = null, favoriteCuisines = "", dietaryRestrictions = "")
                } else {
                    TasteProfileDto(
                        spiceLevel = profile[UserTasteProfiles.spiceLevel],
                        avgBudget = profile[UserTasteProfiles.avgBudget],
                        favoriteCuisines = profile[UserTasteProfiles.favoriteCuisines] ?: "",
                        dietaryRestrictions = profile[UserTasteProfiles.dietaryRestrictions] ?: ""
                    )
                }
                call.respond(ApiResult(data = dto))
            }

            // ==================== 聊天 ====================
            get("/api/conversations") {
                val uid = call.userId()!!
                val Partner = Users.alias("partner")
                val list = transaction {
                    ChatConversations
                        .join(Partner, JoinType.INNER, ChatConversations.partnerId, Partner[Users.id])
                        .selectAll()
                        .where { ChatConversations.userId eq uid }
                        .orderBy(ChatConversations.lastTime to SortOrder.DESC)
                        .map { row ->
                            ConversationDto(
                                conversationId = row[ChatConversations.id],
                                partnerId = row[ChatConversations.partnerId] ?: 0,
                                partnerName = row[Partner[Users.nickname]],
                                partnerAvatar = row[Partner[Users.avatarUrl]],
                                lastMessage = row[ChatConversations.lastMessage],
                                lastTime = row[ChatConversations.lastTime]?.format(dtf) ?: "",
                                unreadCount = 0
                            )
                        }
                }
                call.respond(ApiResult(data = list))
            }

            get("/api/chat/{conversationId}/messages") {
                val cid = call.parameters["conversationId"]!!.toLong()
                val list = transaction {
                    (ChatMessages innerJoin Users).selectAll()
                        .where { ChatMessages.conversationId eq cid }
                        .orderBy(ChatMessages.sentAt to SortOrder.ASC).map { row ->
                            ChatMessageDto(
                                row[ChatMessages.id], row[ChatMessages.conversationId],
                                row[ChatMessages.senderId], row[Users.nickname],
                                row[ChatMessages.type], row[ChatMessages.content],
                                row[ChatMessages.sentAt]?.format(dtf) ?: ""
                            )
                        }
                }
                call.respond(ApiResult(data = list))
            }

            post("/api/chat/{conversationId}/messages") {
                val cid = call.parameters["conversationId"]!!.toLong()
                val uid = call.userId()!!
                val body = call.receive<Map<String, String>>()
                val user = transaction { Users.selectAll().where { Users.id eq uid }.single() }
                val now = LocalDateTime.now()
                val id = transaction {
                    ChatMessages.insert {
                        it[conversationId] = cid
                        it[senderId] = uid
                        it[type] = "text"
                        it[content] = body["content"] ?: ""
                        it[sentAt] = now
                    } get ChatMessages.id
                }
                // 更新会话最后消息
                transaction {
                    ChatConversations.update({ ChatConversations.id eq cid }) {
                        it[lastMessage] = body["content"] ?: ""
                        it[lastTime] = now
                    }
                }
                // 检查接收方是否在线，离线则插入通知
                val conv = transaction {
                    ChatConversations.selectAll().where { ChatConversations.id eq cid }.singleOrNull()
                }
                val partnerId: Long? = conv?.get(ChatConversations.partnerId)
                if (partnerId != null && partnerId != uid) {
                    // 检查对方是否通过 WebSocket 在线
                    val partnerOnline = userChatConnections[partnerId]?.isNotEmpty() == true
                    if (!partnerOnline) {
                        transaction {
                            Notifications.insert {
                                it[Notifications.userId] = partnerId
                                it[Notifications.type] = "chat"
                                it[title] = "新消息"
                                it[content] = "${user[Users.nickname]}: ${body["content"] ?: ""}"
                                it[actionType] = "chat"
                                it[actionId] = cid.toString()
                                it[Notifications.isRead] = false
                            }
                        }
                    }
                    // WebSocket 推送消息给配对用户
                    if (partnerOnline) {
                        val matchPostId = conv?.get(ChatConversations.matchPostId)
                        val partnerConvId = transaction {
                            ChatConversations.selectAll()
                                .where { ChatConversations.userId eq partnerId }
                                .andWhere { ChatConversations.matchPostId eq (matchPostId ?: 0L) }
                                .singleOrNull()?.get(ChatConversations.id)
                        }
                        if (partnerConvId != null) {
                            val partnerSessions = userChatConnections[partnerId]
                            if (partnerSessions != null) {
                                val msgJson = Gson().toJson(mapOf(
                                    "type" to "new_message",
                                    "conversationId" to partnerConvId,
                                    "message" to ChatMessageDto(
                                        id = id,
                                        conversationId = partnerConvId,
                                        senderId = uid,
                                        senderName = user[Users.nickname],
                                        type = "text",
                                        content = body["content"] ?: "",
                                        sentAt = now.format(dtf)
                                    )
                                ))
                                partnerSessions.forEach { session ->
                                    session.send(Frame.Text(msgJson))
                                }
                            }
                        }
                    }
                }
                call.respond(ApiResult(data = ChatMessageDto(
                    id, cid, uid, user[Users.nickname], "text", body["content"] ?: "", now.format(dtf)
                )))
            }

            // ==================== AI 对话 ====================
            post("/api/ai/chat") {
                val uid = call.userId()!!
                val req = call.receive<AiChatRequest>()
                val convId = transaction {
                    val cid = req.conversationId ?: AiConversations.insert {
                        it[userId] = uid
                        it[title] = req.message.take(30)
                        it[createdAt] = LocalDateTime.now()
                    } get AiConversations.id
                    // 保存用户消息
                    AiMessages.insert {
                        it[conversationId] = cid
                        it[role] = "user"
                        it[content] = req.message
                        it[createdAt] = LocalDateTime.now()
                    }
                    cid
                }
                val reply = if (!req.context.isNullOrBlank()) {
                    AiService.replyWithContext(req.message, req.context)
                } else {
                    AiService.reply(req.message)
                }
                transaction {
                    AiMessages.insert {
                        it[conversationId] = convId
                        it[role] = "assistant"
                        it[content] = reply
                        it[createdAt] = LocalDateTime.now()
                    }
                    AiConversations.update({ AiConversations.id eq convId }) {
                        it[updatedAt] = LocalDateTime.now()
                    }
                }
                call.respond(ApiResult(data = AiChatResponse(reply, convId)))
            }

            // ==================== 收藏 ====================
            get("/api/favorites") {
                val uid = call.userId()!!
                val list = transaction {
                    (Favorites innerJoin Restaurants).selectAll()
                        .where { Favorites.userId eq uid }
                        .orderBy(Favorites.id to SortOrder.DESC).map { row ->
                            row.toFavoritesDto()
                        }
                }
                call.respond(ApiResult(data = list))
            }

            post("/api/favorites/{restaurantId}") {
                val uid = call.userId()!!
                val rid = call.parameters["restaurantId"]!!.toLong()
                transaction {
                    Favorites.insert {
                        it[userId] = uid
                        it[restaurantId] = rid
                        it[createdAt] = LocalDateTime.now()
                    }
                }
                call.respond(ApiResult<Unit>(message = "favorited"))
            }

            delete("/api/favorites/{restaurantId}") {
                val uid = call.userId()!!
                val rid = call.parameters["restaurantId"]!!.toLong()
                transaction {
                    Favorites.deleteWhere { (Favorites.userId eq uid) and (Favorites.restaurantId eq rid) }
                }
                call.respond(ApiResult<Unit>(message = "removed"))
            }

            // ==================== 拼桌匹配 ====================
            post("/api/match/posts/{id}/apply") {
                val postId = call.parameters["id"]!!.toLong()
                val uid = call.userId()!!
                val body = call.receive<Map<String, String>>()
                val result = MatchService.apply(postId, uid, body["message"])
                if (result.needNotify && result.notifyUserId != null) {
                    PushService.push(result.notifyUserId, "match_apply", "新申请",
                        result.notifyContent ?: "", postId)
                    // 向桌主发送入席申请通知（持久化到 DB）
                    val postTitle = transaction {
                        MatchPosts.selectAll().where { MatchPosts.id eq postId }.singleOrNull()?.get(MatchPosts.title)
                    } ?: ""
                    val applicantName = transaction {
                        Users.selectAll().where { Users.id eq uid }.singleOrNull()?.get(Users.nickname)
                    } ?: ""
                    transaction {
                        Notifications.insert {
                            it[Notifications.userId] = result.notifyUserId
                            it[Notifications.type] = "match"
                            it[title] = "新的入席申请"
                            it[content] = "${applicantName} 申请加入「${postTitle}」"
                            it[actionType] = "match"
                            it[actionId] = postId.toString()
                            it[Notifications.isRead] = false
                        }
                    }
                }
                call.respond(ApiResult<Unit>(
                    code = if (result.success) 0 else -1,
                    message = result.message
                ))
            }

            post("/api/match/posts/{id}/applications/{appId}/approve") {
                val postId = call.parameters["id"]!!.toLong()
                val appId = call.parameters["appId"]!!.toLong()
                val uid = call.userId()!!
                val result = MatchService.approve(postId, appId, uid)
                if (result.success && result.notifyUserId != null) {
                    // 创建双方聊天会话
                    val convId = transaction {
                        val conv1Id = ChatConversations.insert {
                            it[matchPostId] = postId
                            it[userId] = uid
                            it[partnerId] = result.notifyUserId
                            it[ChatConversations.lastMessage] = "约饭成功，快来聊聊吧～"
                            it[ChatConversations.lastTime] = LocalDateTime.now()
                            it[createdAt] = LocalDateTime.now()
                        } get ChatConversations.id
                        ChatConversations.insert {
                            it[matchPostId] = postId
                            it[userId] = result.notifyUserId
                            it[partnerId] = uid
                            it[ChatConversations.lastMessage] = "约饭成功，快来聊聊吧～"
                            it[ChatConversations.lastTime] = LocalDateTime.now()
                            it[createdAt] = LocalDateTime.now()
                        }
                        conv1Id
                    }
                    // 向申请人发送拼桌成功通知（持久化到 DB）
                    val postTitle = transaction {
                        MatchPosts.selectAll().where { MatchPosts.id eq postId }.singleOrNull()?.get(MatchPosts.title)
                    } ?: ""
                    transaction {
                        Notifications.insert {
                            it[Notifications.userId] = result.notifyUserId
                            it[Notifications.type] = "match"
                            it[title] = "拼桌成功"
                            it[content] = "你已成功加入「${postTitle}」拼桌，快去聊天吧～"
                            it[actionType] = "chat"
                            it[actionId] = convId.toString()
                            it[Notifications.isRead] = false
                        }
                    }
                    PushService.push(result.notifyUserId, "match_approved", "申请通过",
                        result.notifyContent ?: "", postId)
                }
                call.respond(ApiResult<Unit>(
                    code = if (result.success) 0 else -1,
                    message = result.message
                ))
            }

            post("/api/match/posts/{id}/applications/{appId}/reject") {
                val postId = call.parameters["id"]!!.toLong()
                val appId = call.parameters["appId"]!!.toLong()
                val uid = call.userId()!!
                val result = MatchService.reject(postId, appId, uid)
                if (result.needNotify && result.notifyUserId != null) {
                    PushService.push(result.notifyUserId, "match_rejected", "申请拒绝",
                        result.notifyContent ?: "", postId)
                }
                call.respond(ApiResult<Unit>(
                    code = if (result.success) 0 else -1,
                    message = result.message
                ))
            }

            post("/api/match/posts/{id}/close") {
                val postId = call.parameters["id"]!!.toLong()
                val uid = call.userId()!!
                val body = call.receive<Map<String, String>>()
                val reason = body["reason"] ?: "completed"
                val result = MatchService.close(postId, uid, reason)
                if (result.needNotify && result.notifyUserId != null) {
                    PushService.push(result.notifyUserId, "match_closed", "拼桌更新",
                        result.notifyContent ?: "", postId)
                }
                call.respond(ApiResult<Unit>(
                    code = if (result.success) 0 else -1,
                    message = result.message
                ))
            }

            get("/api/match/posts/{id}/applications") {
                val postId = call.parameters["id"]!!.toLong()
                val uid = call.userId()!!
                val list = MatchService.listApplications(postId, uid)
                call.respond(ApiResult(data = list))
            }

            // ==================== Feed 流 ====================
            get("/api/feed") {
                val uid = call.userId()!!
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                val feed = SocialFeedService.getFeed(uid, page, size)
                call.respond(ApiResult(data = feed))
            }

            get("/api/social/feed") {
                val uid = call.userId()!!
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                val feed = SocialFeedService.getFeed(uid, page, size)
                call.respond(ApiResult(data = feed))
            }

            // ==================== 推送通知 ====================
            get("/api/notifications") {
                val uid = call.userId()!!
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                val list = transaction {
                    Notifications.selectAll()
                        .where { Notifications.userId eq uid }
                        .orderBy(Notifications.createdAt to SortOrder.DESC)
                        .limit(size, offset = (page * size).toLong())
                        .map { row ->
                            NotificationDto(
                                id = row[Notifications.id],
                                userId = row[Notifications.userId],
                                type = row[Notifications.type],
                                title = row[Notifications.title],
                                content = row[Notifications.content],
                                avatarUrl = row[Notifications.avatarUrl],
                                actionType = row[Notifications.actionType],
                                actionId = row[Notifications.actionId],
                                isRead = row[Notifications.isRead],
                                createdAt = row[Notifications.createdAt]?.format(dtf) ?: ""
                            )
                        }
                }
                call.respond(ApiResult(data = list))
            }

            get("/api/notifications/unread-count") {
                val uid = call.userId()!!
                val count = transaction {
                    Notifications.selectAll()
                        .where { (Notifications.userId eq uid) and (Notifications.isRead eq false) }
                        .count()
                }
                call.respond(ApiResult(data = mapOf("count" to count)))
            }

            put("/api/notifications/{id}/read") {
                val uid = call.userId()!!
                val nid = call.parameters["id"]!!.toLong()
                transaction {
                    Notifications.update({ (Notifications.id eq nid) and (Notifications.userId eq uid) }) {
                        it[Notifications.isRead] = true
                    }
                }
                call.respond(ApiResult<Unit>(message = "marked read"))
            }

            put("/api/notifications/read-all") {
                val uid = call.userId()!!
                val count = transaction {
                    Notifications.update({ (Notifications.userId eq uid) and (Notifications.isRead eq false) }) {
                        it[Notifications.isRead] = true
                    }
                }
                call.respond(ApiResult(data = mapOf("marked" to count)))
            }

            // ==================== 关注 ====================
            post("/api/users/{userId}/follow") {
                val uid = call.userId()!!
                val targetId = call.parameters["userId"]!!.toLong()
                if (uid == targetId) {
                    call.respond(ApiResult<Unit>(code = -1, message = "不能关注自己"))
                    return@post
                }
                transaction {
                    val exists = UserRelations.selectAll()
                        .where { (UserRelations.followerId eq uid) and (UserRelations.followeeId eq targetId) }
                        .singleOrNull()
                    if (exists == null) {
                        UserRelations.insert {
                            it[followerId] = uid
                            it[followeeId] = targetId
                            it[createdAt] = LocalDateTime.now()
                        }
                    }
                }
                PushService.push(targetId, "new_follower", "新关注", "有人关注了你")
                call.respond(ApiResult<Unit>(message = "followed"))
            }

            delete("/api/users/{userId}/follow") {
                val uid = call.userId()!!
                val targetId = call.parameters["userId"]!!.toLong()
                transaction {
                    UserRelations.deleteWhere {
                        (UserRelations.followerId eq uid) and (UserRelations.followeeId eq targetId)
                    }
                }
                call.respond(ApiResult<Unit>(message = "unfollowed"))
            }
        }
    }
}

private fun ResultRow.toDto(distance: Double?) = RestaurantDto(
    id = this[Restaurants.id],
    amapPoiId = this[Restaurants.amapPoiId],
    name = this[Restaurants.name],
    address = this[Restaurants.address],
    lat = this[Restaurants.lat]?.toDouble(),
    lng = this[Restaurants.lng]?.toDouble(),
    cuisine = this[Restaurants.cuisine],
    avgPrice = this[Restaurants.avgPrice],
    rating = this[Restaurants.rating]?.toDouble() ?: 0.0,
    coverUrl = this[Restaurants.coverUrl],
    phone = this[Restaurants.phone],
    openingHours = this[Restaurants.openingHours],
    distance = distance
)

private fun ResultRow.toFavoritesDto() = FavoritesDto(
    id = this[Favorites.id],
    userId = this[Favorites.userId],
    restaurantId = this[Favorites.restaurantId],
    createdAt = this[Favorites.createdAt]?.format(dtf) ?: ""
)

private fun parseImageJson(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        if (json.startsWith("[")) Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
        else json.split(",").filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun mapReviewStatus(status: String): Int = when (status) {
    "published" -> 1
    "pending" -> 0
    "reported" -> 2
    "deleted" -> 3
    else -> 0
}

private fun ResultRow.toReviewDto() = ReviewDto(
    id = this[Reviews.id],
    userId = this[Reviews.userId],
    userName = this[Users.nickname],
    restaurantId = this[Reviews.restaurantId],
    restaurantName = this[Restaurants.name],
    rating = this[Reviews.rating]?.toDouble() ?: 0.0,
    content = this[Reviews.content],
    images = parseImageJson(this[Reviews.images]),
    status = mapReviewStatus(this[Reviews.status]),
    likeCount = this[Reviews.likeCount] ?: 0,
    createdAt = this[Reviews.createdAt]?.format(dtf) ?: "",
    updatedAt = this[Reviews.updatedAt]?.format(dtf)
)
