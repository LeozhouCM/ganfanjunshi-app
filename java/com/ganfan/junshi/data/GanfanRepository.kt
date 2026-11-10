package com.ganfan.junshi.data

import android.content.Context
import com.ganfan.junshi.Constants
import com.ganfan.junshi.data.local.AppDatabase
import com.ganfan.junshi.data.local.entity.*
import com.ganfan.junshi.data.model.*
import com.ganfan.junshi.data.remote.ApiClient
import com.ganfan.junshi.util.AmapRestaurantSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(Constants.KEY_TOKEN, null)
        set(value) = prefs.edit().putString(Constants.KEY_TOKEN, value).apply()

    var userId: Long
        get() = prefs.getLong(Constants.KEY_USER_ID, -1L)
        set(value) = prefs.edit().putLong(Constants.KEY_USER_ID, value).apply()

    var nickname: String?
        get() = prefs.getString(Constants.KEY_NICKNAME, null)
        set(value) = prefs.edit().putString(Constants.KEY_NICKNAME, value).apply()

    var userId2: String?
        get() = prefs.getString(Constants.KEY_USER_ID2, null)
        set(value) = prefs.edit().putString(Constants.KEY_USER_ID2, value).apply()

    var phone: String?
        get() = prefs.getString(Constants.KEY_PHONE, null)
        set(value) = prefs.edit().putString(Constants.KEY_PHONE, value).apply()

    var isLoggedIn: Boolean
        get() = !token.isNullOrBlank() && userId > 0
        set(_) {}

    fun clear() {
        prefs.edit().clear().apply()
        ApiClient.setToken(null)
    }

    fun saveLogin(response: LoginResponse) {
        token = response.token
        userId = response.user.id
        nickname = response.user.nickname
        userId2 = response.user.userId2
        phone = response.user.phone
        ApiClient.setToken(response.token)
    }

    init {
        ApiClient.setToken(token)
    }
}

class GanfanRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val api = ApiClient.service
    val session = SessionManager(context)
    private val likePrefs = context.getSharedPreferences("restaurant_likes", Context.MODE_PRIVATE)

    fun observeRestaurants(): Flow<List<RestaurantEntity>> = db.restaurantDao().observeAll()
    fun observeFeaturedRestaurants(): Flow<List<RestaurantEntity>> = db.restaurantDao().observeFeatured()
    fun observeMatchPosts(): Flow<List<MatchPostEntity>> = db.matchPostDao().observeAll()
    fun observeRestaurantMatchPosts(restaurantId: Long): Flow<List<MatchPostEntity>> =
        db.matchPostDao().observeByRestaurant(restaurantId)
    fun observeReviews(): Flow<List<ReviewEntity>> = db.reviewDao().observeAll()
    fun observeReviewsByRestaurant(restaurantId: Long): Flow<List<ReviewEntity>> =
        db.reviewDao().observeByRestaurant(restaurantId)
    fun observeChatMessages(conversationId: Long): Flow<List<ChatMessageEntity>> =
        db.chatMessageDao().observeByConversation(conversationId)
    fun observeConversations(): Flow<List<ConversationEntity>> =
        db.conversationDao().observeByUser(session.userId)
    fun observeMatchConversations(): Flow<List<ConversationEntity>> =
        db.conversationDao().observeMatchConversations(session.userId)
    fun observeTotalUnreadCount(): Flow<Int> =
        observeMatchConversations().map { conversations ->
            conversations.sumOf { it.unreadCount }
        }
    fun observePendingApplications(): Flow<List<MatchApplicationEntity>> =
        db.matchApplicationDao().observePendingForPublisher(session.userId)
    fun observeBuddyInvites(): Flow<List<BuddyMatchInviteEntity>> =
        db.buddyMatchInviteDao().observePendingForUser(session.userId)
    fun observeBuddies(): Flow<List<BuddyEntity>> =
        db.buddyDao().observeByUser(session.userId)

    suspend fun getOpenMatchStatsByRestaurant(): Map<Long, RestaurantMatchStats> =
        withContext(Dispatchers.IO) {
            db.matchPostDao().getAllOpen()
                .filter { it.restaurantId != null }
                .groupBy { it.restaurantId!! }
                .mapValues { (_, posts) ->
                    RestaurantMatchStats(
                        openPostCount = posts.size,
                        totalSeekingPeople = posts.sumOf { (it.maxPeople - it.currentPeople).coerceAtLeast(0) },
                        maxTableSize = posts.maxOfOrNull { it.maxPeople } ?: 0
                    )
                }
        }

    suspend fun login(phone: String, password: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val result = api.login(LoginRequest(phone, password))
            if (result.code == 0 && result.data != null) {
                session.saveLogin(result.data)
                db.userDao().insert(result.data.user.toEntity())
                seedTasteIfNeeded(result.data.user.id)
                Result.success(result.data)
            } else {
                Result.failure(Exception(result.message.ifBlank { "登录失败" }))
            }
        } catch (e: Exception) {
            android.util.Log.e("GanfanRepo", "login failed", e)
            seedLocalDataIfNeeded()
            val mockUser = UserDto(
                id = phone.hashCode().toLong().let { if (it < 0) -it else it },
                phone = phone,
                nickname = "干饭人${phone.takeLast(4)}",
                city = "北京"
            )
            val mockResponse = LoginResponse(token = "mock_token_${mockUser.id}", user = mockUser)
            session.saveLogin(mockResponse)
            db.userDao().insert(mockUser.toEntity())
            seedTasteIfNeeded(mockUser.id)
            Result.success(mockResponse)
        }
    }

    suspend fun register(phone: String, password: String, userId2: String, nickname: String): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val result = api.register(
                RegisterRequest(phone = phone, password = password, userId2 = userId2, nickname = nickname)
            )
            if (result.code == 0 && result.data != null) {
                val user = result.data
                // Auto-login after successful registration
                try {
                    val loginResult = api.login(LoginRequest(phone, password))
                    if (loginResult.code == 0 && loginResult.data != null) {
                        session.saveLogin(loginResult.data)
                        db.userDao().insert(loginResult.data.user.toEntity())
                        seedTasteIfNeeded(loginResult.data.user.id)
                    }
                } catch (_: Exception) {
                    // Login after register failed, but register itself succeeded
                    session.userId2 = user.userId2
                    session.nickname = user.nickname
                }
                Result.success(user)
            } else {
                Result.failure(Exception(result.message.ifBlank { "注册失败" }))
            }
        } catch (e: Exception) {
            android.util.Log.e("GanfanRepo", "register failed", e)
            // Try to login anyway in case the user already exists
            try {
                val loginResult = api.login(LoginRequest(phone, password))
                if (loginResult.code == 0 && loginResult.data != null) {
                    session.saveLogin(loginResult.data)
                    db.userDao().insert(loginResult.data.user.toEntity())
                    seedTasteIfNeeded(loginResult.data.user.id)
                    return@withContext Result.success(loginResult.data.user)
                }
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    private suspend fun saveUserId2Locally(userId2: String, nickname: String): Result<UserDto> {
        val user = db.userDao().getById(session.userId)
            ?: return Result.failure(Exception("用户不存在"))
        db.userDao().update(user.copy(nickname = nickname, userId2 = userId2))
        session.userId2 = userId2
        session.nickname = nickname
        return Result.success(UserDto(id = user.id, phone = user.phone, nickname = nickname, userId2 = userId2))
    }

    /**
     * 将从 WebSocket 收到的实时消息写入本地数据库，触发 UI 更新。
     */
    suspend fun insertWebSocketMessage(
        conversationId: Long,
        senderId: Long,
        senderName: String,
        content: String,
        sentAt: String
    ) = withContext(Dispatchers.IO) {
        val msg = ChatMessageEntity(
            id = System.currentTimeMillis(),
            conversationId = conversationId,
            senderId = senderId,
            senderName = senderName,
            msgType = "text",
            content = content,
            sentAt = sentAt
        )
        db.chatMessageDao().insert(msg)
        // 更新当前会话的最后消息
        db.conversationDao().getById(conversationId)?.let { conv ->
            val isIncoming = senderId != conv.userId
            db.conversationDao().update(
                conv.copy(
                    lastMessage = content,
                    lastTime = sentAt,
                    unreadCount = if (isIncoming) conv.unreadCount + 1 else conv.unreadCount
                )
            )
        }
        // 同步消息到对方会话
        val conv = db.conversationDao().getById(conversationId)
        if (conv != null && conv.matchPostId > 0) {
            val partnerConv = db.conversationDao().getByUserPartnerAndMatch(
                conv.partnerId, conv.userId, conv.matchPostId
            )
            if (partnerConv != null && partnerConv.id != conversationId) {
                db.chatMessageDao().insert(
                    msg.copy(
                        id = System.currentTimeMillis() + 1,
                        conversationId = partnerConv.id
                    )
                )
                val partnerIncoming = senderId != partnerConv.userId
                db.conversationDao().update(
                    partnerConv.copy(
                        lastMessage = content,
                        lastTime = sentAt,
                        unreadCount = if (partnerIncoming) partnerConv.unreadCount + 1 else partnerConv.unreadCount
                    )
                )
            }
        }
    }

    suspend fun markConversationRead(conversationId: Long) = withContext(Dispatchers.IO) {
        db.conversationDao().getById(conversationId)?.let { conv ->
            if (conv.unreadCount > 0) {
                db.conversationDao().update(conv.copy(unreadCount = 0))
            }
        }
    }

    suspend fun searchUsers(keyword: String): List<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val result = api.searchUsers(keyword)
            if (result.code == 0 && result.data != null) {
                result.data.forEach { db.userDao().insert(it.toEntity()) }
                result.data.map { it.toEntity() }
            } else {
                db.userDao().searchUsers(keyword)
            }
        } catch (e: Exception) {
            db.userDao().searchUsers(keyword)
        }
    }

    suspend fun getUserById(id: Long): UserEntity? = withContext(Dispatchers.IO) {
        db.userDao().getById(id) ?: try {
            api.getUserById(id).data?.toEntity()?.also { db.userDao().insert(it) }
        } catch (_: Exception) {
            null
        } ?: resolveUserFromLocalContent(id)?.also { db.userDao().insert(it) }
    }

    private suspend fun resolveUserFromLocalContent(userId: Long): UserEntity? {
        db.matchPostDao().getAllOpen()
            .firstOrNull { it.publisherId == userId }
            ?.let { post ->
                return UserEntity(
                    id = userId,
                    phone = "",
                    nickname = post.publisherName,
                    avatarUrl = null,
                    bio = "热爱探店拼桌的饭友",
                    city = "北京",
                    creditScore = 90
                )
            }
        return null
    }

    suspend fun getMatchPost(id: Long): MatchPostEntity? = withContext(Dispatchers.IO) {
        db.matchPostDao().getById(id) ?: try {
            refreshMatchPosts()
            db.matchPostDao().getById(id)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun applyToMatch(postId: Long, message: String? = null): Result<MatchApplicationEntity> =
        withContext(Dispatchers.IO) {
            val post = db.matchPostDao().getById(postId)
                ?: return@withContext Result.failure(Exception("饭桌不存在"))
            if (post.publisherId == session.userId) {
                return@withContext Result.failure(Exception("不能申请自己的饭桌"))
            }
            if (post.status != "open") {
                return@withContext Result.failure(Exception("该饭桌已关闭"))
            }
            if (db.matchApplicationDao().hasActiveApplication(postId, session.userId) > 0) {
                return@withContext Result.failure(Exception("已申请过该饭桌"))
            }
            val app = MatchApplicationEntity(
                id = System.currentTimeMillis(),
                postId = postId,
                applicantId = session.userId,
                applicantName = session.nickname ?: "我",
                publisherId = post.publisherId,
                status = "pending",
                message = message
            )
            try {
                val result = api.applyToMatchPost(postId, mapOf("message" to (message ?: "")))
                val dto = result.data
                if (dto != null && dto.id > 0) {
                    db.matchApplicationDao().update(app.copy(id = dto.id))
                }
            } catch (_: Exception) { }
            db.matchApplicationDao().insert(app)
            db.notificationDao().insert(
                NotificationEntity(
                    fromUserId = session.userId,
                    toUserId = post.publisherId,
                    type = "match_apply",
                    content = "${session.nickname ?: "用户"} 申请加入「${post.title}」",
                    relatedReviewId = postId
                )
            )
            Result.success(app)
        }

    suspend fun approveApplication(postId: Long, appId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val app = db.matchApplicationDao().getById(appId)
            ?: return@withContext Result.failure(Exception("申请不存在"))
        if (app.publisherId != session.userId) {
            return@withContext Result.failure(Exception("无权操作"))
        }
        if (app.status != "pending") {
            return@withContext Result.failure(Exception("申请已处理"))
        }
        val post = db.matchPostDao().getById(app.postId)
            ?: return@withContext Result.failure(Exception("饭桌不存在"))
        try { api.approveMatchApplication(postId, appId) } catch (_: Exception) { }
        db.matchApplicationDao().update(app.copy(status = "approved"))
        val newCount = post.currentPeople + 1
        val newStatus = if (newCount >= post.maxPeople) "full" else post.status
        db.matchPostDao().update(post.copy(currentPeople = newCount, status = newStatus))
        createMatchConversationInternal(app.postId, post.publisherId, post.publisherName, app.applicantId, app.applicantName)
        db.notificationDao().insert(
            NotificationEntity(
                fromUserId = session.userId,
                toUserId = app.applicantId,
                type = "match_approved",
                content = "你的入桌申请已通过：「${post.title}」",
                relatedReviewId = app.postId
            )
        )
        Result.success(Unit)
    }

    suspend fun rejectApplication(postId: Long, appId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val app = db.matchApplicationDao().getById(appId)
            ?: return@withContext Result.failure(Exception("申请不存在"))
        if (app.publisherId != session.userId) {
            return@withContext Result.failure(Exception("无权操作"))
        }
        try { api.rejectMatchApplication(postId, appId) } catch (_: Exception) { }
        db.matchApplicationDao().update(app.copy(status = "rejected"))
        val post = db.matchPostDao().getById(app.postId)
        db.notificationDao().insert(
            NotificationEntity(
                fromUserId = session.userId,
                toUserId = app.applicantId,
                type = "match_rejected",
                content = "入桌申请未通过：「${post?.title ?: "饭桌"}」",
                relatedReviewId = app.postId
            )
        )
        Result.success(Unit)
    }

    suspend fun getApplicationsForPost(postId: Long): List<MatchApplicationEntity> = withContext(Dispatchers.IO) {
        db.matchApplicationDao().getByPost(postId)
    }

    suspend fun syncApplicationsFromServer(postId: Long) = withContext(Dispatchers.IO) {
        try {
            val result = api.getApplicationsForPost(postId)
            val dtos = result.data ?: return@withContext
            dtos.forEach { dto ->
                val entity = MatchApplicationEntity(
                    id = dto.id,
                    postId = dto.postId,
                    applicantId = dto.applicantId,
                    applicantName = dto.applicantName,
                    publisherId = dto.publisherId,
                    status = dto.status,
                    message = dto.message
                )
                db.matchApplicationDao().insert(entity)
            }
        } catch (_: Exception) { }
    }

    suspend fun hasApprovedMatchWith(userId: Long): Boolean = withContext(Dispatchers.IO) {
        db.matchApplicationDao().hasApprovedMatchBetween(session.userId, userId) > 0
    }

    suspend fun confirmMeal(matchPostId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val post = db.matchPostDao().getById(matchPostId)
            ?: return@withContext Result.failure(Exception("饭桌不存在"))
        val me = session.userId
        val isMember = post.publisherId == me ||
            db.matchApplicationDao().getByPost(matchPostId).any { it.applicantId == me && it.status == "approved" }
        if (!isMember) return@withContext Result.failure(Exception("你不是该饭桌成员"))
        if (db.mealConfirmationDao().hasConfirmed(matchPostId, me) > 0) {
            return@withContext Result.success(false)
        }
        db.mealConfirmationDao().insert(MealConfirmationEntity(matchPostId, me))
        val count = db.mealConfirmationDao().getConfirmationCount(matchPostId)
        if (count >= 2) {
            db.matchPostDao().update(post.copy(status = "completed"))
            val approvedApps = db.matchApplicationDao().getByPost(matchPostId).filter { it.status == "approved" }
            val members = mutableListOf(post.publisherId to post.publisherName)
            approvedApps.forEach { members.add(it.applicantId to it.applicantName) }
            for (i in members.indices) {
                for (j in i + 1 until members.size) {
                    val (aId, aName) = members[i]
                    val (bId, bName) = members[j]
                    upsertBuddy(aId, bId, bName, matchPostId)
                    upsertBuddy(bId, aId, aName, matchPostId)
                }
            }
            Result.success(true)
        } else {
            Result.success(false)
        }
    }

    private suspend fun upsertBuddy(userId: Long, partnerId: Long, partnerName: String, matchPostId: Long) {
        val existing = db.buddyDao().isBuddy(userId, partnerId) > 0
        if (existing) {
            val level = db.buddyDao().getSparkLevel(userId, partnerId) ?: 1
            db.buddyDao().insert(
                BuddyEntity(
                    userId = userId,
                    partnerId = partnerId,
                    partnerName = partnerName,
                    sparkLevel = level + 1,
                    mealCount = level + 1,
                    sourceMatchPostId = matchPostId,
                    lastMealAt = System.currentTimeMillis()
                )
            )
        } else {
            db.buddyDao().insert(
                BuddyEntity(
                    userId = userId,
                    partnerId = partnerId,
                    partnerName = partnerName,
                    sparkLevel = 1,
                    mealCount = 1,
                    sourceMatchPostId = matchPostId
                )
            )
        }
    }

    suspend fun isBuddy(userId: Long): Boolean = withContext(Dispatchers.IO) {
        db.buddyDao().isBuddy(session.userId, userId) > 0
    }

    suspend fun getSparkLevel(userId: Long): Int = withContext(Dispatchers.IO) {
        db.buddyDao().getSparkLevel(session.userId, userId) ?: 0
    }

    suspend fun getBuddies(): List<BuddyEntity> = withContext(Dispatchers.IO) {
        db.buddyDao().getByUser(session.userId)
    }

    suspend fun shareMatchToBuddies(matchPostId: Long, buddyIds: List<Long>): Result<Unit> =
        withContext(Dispatchers.IO) {
            val post = db.matchPostDao().getById(matchPostId)
                ?: return@withContext Result.failure(Exception("饭桌不存在"))
            if (post.publisherId != session.userId) {
                return@withContext Result.failure(Exception("只能分享自己的饭桌"))
            }
            buddyIds.forEach { buddyId ->
                if (db.buddyDao().isBuddy(session.userId, buddyId) == 0) return@forEach
                val invite = BuddyMatchInviteEntity(
                    id = System.currentTimeMillis() + buddyId,
                    matchPostId = matchPostId,
                    fromUserId = session.userId,
                    fromUserName = session.nickname ?: "我",
                    toUserId = buddyId,
                    restaurantName = post.restaurantName ?: "餐厅",
                    postTitle = post.title,
                    status = "pending"
                )
                db.buddyMatchInviteDao().insert(invite)
                db.notificationDao().insert(
                    NotificationEntity(
                        fromUserId = session.userId,
                        toUserId = buddyId,
                        type = "buddy_invite",
                        content = "${session.nickname ?: "密友"} 邀请你来「${post.title}」",
                        relatedReviewId = matchPostId
                    )
                )
            }
            Result.success(Unit)
        }

    suspend fun acceptBuddyInvite(inviteId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val invite = db.buddyMatchInviteDao().getById(inviteId)
            ?: return@withContext Result.failure(Exception("邀请不存在"))
        if (invite.toUserId != session.userId) {
            return@withContext Result.failure(Exception("无权操作"))
        }
        val post = db.matchPostDao().getById(invite.matchPostId)
            ?: return@withContext Result.failure(Exception("饭桌不存在"))
        db.buddyMatchInviteDao().update(invite.copy(status = "accepted"))
        val app = MatchApplicationEntity(
            id = System.currentTimeMillis(),
            postId = invite.matchPostId,
            applicantId = session.userId,
            applicantName = session.nickname ?: "我",
            publisherId = post.publisherId,
            status = "approved"
        )
        db.matchApplicationDao().insert(app)
        val newCount = post.currentPeople + 1
        db.matchPostDao().update(post.copy(currentPeople = newCount, status = if (newCount >= post.maxPeople) "full" else post.status))
        createMatchConversationInternal(invite.matchPostId, post.publisherId, post.publisherName, session.userId, session.nickname ?: "我")
        Result.success(Unit)
    }

    suspend fun declineBuddyInvite(inviteId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val invite = db.buddyMatchInviteDao().getById(inviteId)
            ?: return@withContext Result.failure(Exception("邀请不存在"))
        db.buddyMatchInviteDao().update(invite.copy(status = "declined"))
        Result.success(Unit)
    }

    private suspend fun createMatchConversationInternal(
        matchPostId: Long,
        publisherId: Long,
        publisherName: String,
        applicantId: Long,
        applicantName: String
    ) {
        ensureMatchConversation(publisherId, applicantId, applicantName, matchPostId)
        ensureMatchConversation(applicantId, publisherId, publisherName, matchPostId)
    }

    private suspend fun ensureMatchConversation(
        userId: Long,
        partnerId: Long,
        partnerName: String,
        matchPostId: Long
    ) {
        val existing = db.conversationDao().getByUserPartnerAndMatch(userId, partnerId, matchPostId)
        if (existing != null) return
        // 使用确定性 ID：matchPostId * 1000000 + userId，确保每个用户有唯一且可重现的会话 ID
        val convId = matchPostId * 1000000 + userId
        db.conversationDao().insert(
            ConversationEntity(
                id = convId,
                userId = userId,
                partnerId = partnerId,
                partnerName = partnerName,
                lastMessage = "约饭成功，快来聊聊吧～",
                lastTime = System.currentTimeMillis().toString(),
                matchPostId = matchPostId
            )
        )
    }

    suspend fun followUser(followingId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val followerId = session.userId
        if (!hasApprovedMatchWith(followingId)) {
            return@withContext Result.failure(Exception("需先约饭成功才能关注"))
        }
        val matchPostId = db.matchApplicationDao().getLatestApprovedMatchPostId(followerId, followingId) ?: 0L
        try { api.followUser(followingId) } catch (_: Exception) {}
        val isAlready = db.followDao().isFollowing(followerId, followingId) > 0
        if (isAlready) {
            db.followDao().unfollow(followerId, followingId)
            Result.success(false)
        } else {
            db.followDao().follow(
                FollowEntity(followerId = followerId, followingId = followingId, sourceMatchPostId = matchPostId)
            )
            Result.success(true)
        }
    }

    suspend fun canFollow(userId: Long): Boolean = hasApprovedMatchWith(userId)

    suspend fun canChatWith(userId: Long): Boolean = hasApprovedMatchWith(userId)

    suspend fun getOrCreateMatchConversation(
        matchPostId: Long,
        partnerId: Long,
        partnerName: String
    ): ConversationEntity? = withContext(Dispatchers.IO) {
        if (!hasApprovedMatchWith(partnerId)) return@withContext null
        val me = session.userId
        db.conversationDao().getByUserPartnerAndMatch(me, partnerId, matchPostId)
            ?: run {
                ensureMatchConversation(me, partnerId, partnerName, matchPostId)
                db.conversationDao().getByUserPartnerAndMatch(me, partnerId, matchPostId)
            }
    }

    /** @deprecated 仅约饭成功后可用，请使用 getOrCreateMatchConversation */
    suspend fun getOrCreateConversation(partnerId: Long, partnerName: String): ConversationEntity? =
        withContext(Dispatchers.IO) {
            if (!hasApprovedMatchWith(partnerId)) return@withContext null
            val matchPostId = db.matchApplicationDao().getLatestApprovedMatchPostId(session.userId, partnerId) ?: 0L
            if (matchPostId == 0L) return@withContext null
            getOrCreateMatchConversation(matchPostId, partnerId, partnerName)
        }

    suspend fun isFollowing(userId: Long): Boolean = withContext(Dispatchers.IO) {
        db.followDao().isFollowing(session.userId, userId) > 0
    }

    suspend fun isMutualFollow(userId: Long): Boolean = withContext(Dispatchers.IO) {
        db.followDao().isFollowing(session.userId, userId) > 0 &&
            db.followDao().isFollowing(userId, session.userId) > 0
    }

    suspend fun getFollowingCount(userId: Long = session.userId): Int = withContext(Dispatchers.IO) {
        db.followDao().getFollowingCount(userId)
    }

    suspend fun getFollowerCount(userId: Long = session.userId): Int = withContext(Dispatchers.IO) {
        db.followDao().getFollowerCount(userId)
    }

    suspend fun getInviteItems(): List<MatchInviteItem> =
        withContext(Dispatchers.IO) {
            val me = session.userId
            val items = mutableListOf<MatchInviteItem>()
            val pending = db.matchApplicationDao().getPendingForPublisher(me)
            val myApps = db.matchApplicationDao().getByApplicant(me)
            for (app in pending) {
                val post = db.matchPostDao().getById(app.postId)
                items.add(
                    MatchInviteItem(
                        id = app.id,
                        type = "apply_in",
                        title = "${app.applicantName} 申请入桌",
                        subtitle = post?.title ?: "饭桌",
                        status = app.status,
                        postId = app.postId,
                        relatedUserId = app.applicantId,
                        relatedUserName = app.applicantName
                    )
                )
            }
            for (app in myApps.filter { it.status == "pending" }) {
                val post = db.matchPostDao().getById(app.postId)
                items.add(
                    MatchInviteItem(
                        id = app.id,
                        type = "apply_out",
                        title = "等待同意：${post?.title ?: "饭桌"}",
                        subtitle = "发布者审核中",
                        status = app.status,
                        postId = app.postId
                    )
                )
            }
            val buddyInvites = db.buddyMatchInviteDao().getPendingForUser(me)
            for (inv in buddyInvites) {
                items.add(
                    MatchInviteItem(
                        id = inv.id,
                        type = "buddy_share",
                        title = "${inv.fromUserName} 分享饭桌",
                        subtitle = "${inv.restaurantName} · ${inv.postTitle}",
                        status = inv.status,
                        postId = inv.matchPostId,
                        relatedUserId = inv.fromUserId,
                        relatedUserName = inv.fromUserName
                    )
                )
            }
            items.sortedByDescending { it.id }
        }

    suspend fun hasConfirmedMeal(matchPostId: Long): Boolean = withContext(Dispatchers.IO) {
        db.mealConfirmationDao().hasConfirmed(matchPostId, session.userId) > 0
    }

    suspend fun getMealConfirmationCount(matchPostId: Long): Int = withContext(Dispatchers.IO) {
        db.mealConfirmationDao().getConfirmationCount(matchPostId)
    }

    suspend fun getConversations(): List<ConversationEntity> = withContext(Dispatchers.IO) {
        try {
            val result = api.getConversations()
            if (result.code == 0 && result.data != null) {
                result.data.forEach { db.conversationDao().insert(it.toEntity()) }
                result.data.map { it.toEntity() }
            } else {
                db.conversationDao().getByUser(session.userId)
            }
        } catch (e: Exception) {
            db.conversationDao().getByUser(session.userId)
        }
    }

    suspend fun sendMessage(conversationId: Long, content: String): Result<ChatMessageEntity> =
        withContext(Dispatchers.IO) {
            val conv = db.conversationDao().getById(conversationId)
                ?: return@withContext Result.failure(Exception("会话不存在"))
            if (conv.matchPostId <= 0 || !hasApprovedMatchWith(conv.partnerId)) {
                return@withContext Result.failure(Exception("需先约饭成功才能发消息"))
            }
            val now = System.currentTimeMillis()
            val msg = ChatMessageEntity(
                id = now,
                conversationId = conversationId,
                senderId = session.userId,
                senderName = session.nickname ?: "我",
                msgType = "text",
                content = content,
                sentAt = now.toString()
            )
            db.chatMessageDao().insert(msg)
            try { api.sendMessage(conversationId, mapOf("content" to content)) } catch (_: Exception) {}
            db.conversationDao().getById(conversationId)?.let { c ->
                db.conversationDao().update(c.copy(lastMessage = content, lastTime = msg.sentAt))
            }
            // 同步消息到对方会话，确保对方能看到消息
            val partnerConv = db.conversationDao().getByUserPartnerAndMatch(
                conv.partnerId, conv.userId, conv.matchPostId
            )
            if (partnerConv != null && partnerConv.id != conversationId) {
                db.chatMessageDao().insert(
                    msg.copy(
                        id = now + 1,
                        conversationId = partnerConv.id
                    )
                )
                db.conversationDao().update(
                    partnerConv.copy(
                        lastMessage = content,
                        lastTime = msg.sentAt,
                        unreadCount = partnerConv.unreadCount + 1
                    )
                )
            }
            Result.success(msg)
        }

    suspend fun refreshRestaurants(
        lat: Double? = null,
        lng: Double? = null,
        radiusMeters: Int = 5000,
        keyword: String = "",
        wholeCity: Boolean = false,
        city: String? = null
    ): Result<List<RestaurantEntity>> = withContext(Dispatchers.IO) {
        try {
            val searchLat = lat ?: 39.9042
            val searchLng = lng ?: 116.4074
            val amapList = when {
                wholeCity && !city.isNullOrBlank() -> AmapRestaurantSearch.searchInCity(
                    context = context,
                    city = city,
                    lat = searchLat,
                    lng = searchLng,
                    keyword = keyword
                )
                wholeCity -> AmapRestaurantSearch.searchWholeCityFallback(
                    context = context,
                    lat = searchLat,
                    lng = searchLng,
                    keyword = keyword
                )
                else -> AmapRestaurantSearch.searchNearby(
                    context = context,
                    lat = searchLat,
                    lng = searchLng,
                    radiusMeters = radiusMeters,
                    keyword = keyword
                )
            }
            val list = if (amapList.isNotEmpty()) {
                amapList
            } else {
                val apiRadius = if (wholeCity) 50_000 else radiusMeters
                val result = api.getNearbyRestaurants(searchLat, searchLng, apiRadius)
                if (result.code == 0 && result.data != null) {
                    result.data.map { it.toEntity() }
                } else {
                    getSeedRestaurants()
                }
            }
            db.restaurantDao().clearAll()
            db.restaurantDao().insertAll(list)
            Result.success(list)
        } catch (e: Exception) {
            seedLocalDataIfNeeded()
            Result.success(db.restaurantDao().getAll())
        }
    }

    suspend fun getRestaurant(id: Long): RestaurantEntity? = withContext(Dispatchers.IO) {
        db.restaurantDao().getById(id) ?: try {
            api.getRestaurant(id).data?.toEntity()?.also {
                db.restaurantDao().insertAll(listOf(it))
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun refreshMatchPosts(): Result<List<MatchPostEntity>> = withContext(Dispatchers.IO) {
        try {
            val result = api.getMatchPosts()
            val list = if (result.code == 0 && result.data != null) {
                result.data.map { it.toEntity() }
            } else {
                getSeedMatchPosts()
            }
            db.matchPostDao().insertAll(list)
            Result.success(list)
        } catch (e: Exception) {
            seedLocalDataIfNeeded()
            val list = getSeedMatchPosts()
            db.matchPostDao().insertAll(list)
            Result.success(list)
        }
    }

    suspend fun createMatchPost(request: CreateMatchRequest): Result<MatchPostEntity> =
        withContext(Dispatchers.IO) {
            try {
                val result = api.createMatchPost(request)
                if (result.code == 0 && result.data != null) {
                    val entity = result.data.toEntity()
                    db.matchPostDao().insert(entity)
                    Result.success(entity)
                } else {
                    Result.failure(Exception(result.message))
                }
            } catch (e: Exception) {
                val restaurantName = request.restaurantId?.let { db.restaurantDao().getById(it)?.name }
                val entity = MatchPostEntity(
                    id = System.currentTimeMillis(),
                    publisherId = session.userId,
                    publisherName = session.nickname ?: "我",
                    restaurantId = request.restaurantId,
                    restaurantName = restaurantName,
                    title = request.title,
                    mealTime = request.mealTime,
                    maxPeople = request.maxPeople,
                    currentPeople = 1,
                    budget = request.budget,
                    lat = request.lat,
                    lng = request.lng,
                    status = "open"
                )
                db.matchPostDao().insert(entity)
                Result.success(entity)
            }
        }

    suspend fun refreshReviews(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val result = api.getReviews()
            if (result.code == 0 && result.data != null) {
                db.reviewDao().insertAll(result.data.map { it.toEntity() })
            }
        } catch (_: Exception) {
            seedLocalDataIfNeeded()
        }
        Result.success(Unit)
    }

    suspend fun getRestaurantReviews(restaurantId: Long): List<ReviewEntity> = withContext(Dispatchers.IO) {
        try {
            val result = api.getRestaurantReviews(restaurantId)
            if (result.code == 0 && result.data != null) {
                result.data.forEach { db.reviewDao().insert(it.toEntity()) }
            }
        } catch (_: Exception) { }
        db.reviewDao().getByRestaurantAll(restaurantId)
    }

    suspend fun createReview(
        restaurantId: Long,
        restaurantName: String,
        rating: Int,
        content: String,
        images: List<String> = emptyList(),
        parentId: Long = 0
    ): Result<ReviewEntity> = withContext(Dispatchers.IO) {
        val review = ReviewEntity(
            id = System.currentTimeMillis(),
            userId = session.userId,
            userName = session.nickname ?: "我",
            restaurantId = restaurantId,
            restaurantName = restaurantName,
            rating = rating,
            content = content,
            images = images.joinToString(","),
            createdAt = System.currentTimeMillis().toString(),
            parentId = parentId
        )
        db.reviewDao().insert(review)
        try { api.createReview(review.toDto()) } catch (_: Exception) {}

        if (parentId > 0) {
            val parentReview = db.reviewDao().getById(parentId)
            if (parentReview != null && parentReview.userId != session.userId) {
                val summary = if (content.length > 50) content.take(50) + "..." else content
                db.notificationDao().insert(
                    NotificationEntity(
                        fromUserId = session.userId,
                        toUserId = parentReview.userId,
                        type = "reply",
                        content = "${session.nickname ?: "用户"} 回复了你：$summary",
                        relatedReviewId = review.id
                    )
                )
            }
        }
        Result.success(review)
    }

    suspend fun toggleReviewLike(reviewId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = session.userId
        val liked = db.reviewLikeDao().isLiked(reviewId, userId) > 0
        if (liked) {
            db.reviewLikeDao().unlike(reviewId, userId)
            try { api.unlikeReview(reviewId) } catch (_: Exception) {}
            db.reviewDao().decrementLikes(reviewId)
            Result.success(false)
        } else {
            db.reviewLikeDao().like(ReviewLikeEntity(reviewId = reviewId, userId = userId))
            try { api.likeReview(reviewId) } catch (_: Exception) {}
            db.reviewDao().incrementLikes(reviewId)
            Result.success(true)
        }
    }

    suspend fun isReviewLiked(reviewId: Long): Boolean = withContext(Dispatchers.IO) {
        db.reviewLikeDao().isLiked(reviewId, session.userId) > 0
    }

    suspend fun aiChat(message: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = api.aiChat(AiChatRequest(message))
            if (result.code == 0 && result.data != null) {
                Result.success(result.data.reply)
            } else {
                Result.success(mockAiReply(message))
            }
        } catch (_: Exception) {
            Result.success(mockAiReply(message))
        }
    }

    suspend fun toggleFavorite(restaurant: RestaurantEntity): Boolean = withContext(Dispatchers.IO) {
        val userId = session.userId
        val isFav = db.favoriteDao().isFavorite(userId, restaurant.id) > 0
        if (isFav) {
            db.favoriteDao().delete(userId, restaurant.id)
            try { api.removeFavorite(restaurant.id) } catch (_: Exception) {}
            false
        } else {
            db.favoriteDao().insert(FavoriteEntity(userId, restaurant.id, restaurant.name))
            try { api.addFavorite(restaurant.id) } catch (_: Exception) {}
            true
        }
    }

    suspend fun isFavorite(restaurantId: Long): Boolean = withContext(Dispatchers.IO) {
        db.favoriteDao().isFavorite(session.userId, restaurantId) > 0
    }

    fun isLiked(restaurantId: Long): Boolean =
        likePrefs.getBoolean("like_$restaurantId", false)

    fun toggleLike(restaurantId: Long): Boolean {
        val key = "like_$restaurantId"
        val liked = likePrefs.getBoolean(key, false)
        likePrefs.edit().putBoolean(key, !liked).apply()
        return !liked
    }

    fun getDisplayLikeCount(restaurantId: Long, rating: Double): Int {
        val base = (rating * 66).toInt()
        return base + if (isLiked(restaurantId)) 1 else 0
    }

    suspend fun getMatchSuccessRate(restaurantId: Long): Int = withContext(Dispatchers.IO) {
        val posts = db.matchPostDao().getByRestaurant(restaurantId)
        if (posts.isEmpty()) return@withContext 88
        val filled = posts.count { it.currentPeople >= it.maxPeople || it.status == "closed" }
        ((filled.toDouble() / posts.size) * 100).toInt().coerceIn(62, 98)
    }

    suspend fun getTopReview(restaurantId: Long): ReviewEntity? = withContext(Dispatchers.IO) {
        db.reviewDao().getTopByRestaurant(restaurantId)
    }

    suspend fun getFavorites(): List<FavoriteEntity> = withContext(Dispatchers.IO) {
        db.favoriteDao().getByUser(session.userId)
    }

    suspend fun getUserTaste(): UserTasteEntity? = withContext(Dispatchers.IO) {
        db.userTasteDao().getByUserId(session.userId)
    }

    suspend fun getCurrentUser(): UserEntity? = withContext(Dispatchers.IO) {
        db.userDao().getById(session.userId)
    }

    suspend fun updateUserProfile(
        nickname: String,
        bio: String?,
        city: String?,
        realName: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val user = db.userDao().getById(session.userId) ?: return@withContext Result.failure(Exception("用户不存在"))
        val verified = !realName.isNullOrBlank()
        val updated = user.copy(
            nickname = nickname,
            bio = bio,
            city = city,
            realName = realName?.ifBlank { null },
            realNameVerified = verified || user.realNameVerified
        )
        db.userDao().update(updated)
        session.nickname = nickname
        Result.success(Unit)
    }

    suspend fun saveUserTaste(taste: UserTasteEntity) = withContext(Dispatchers.IO) {
        db.userTasteDao().insert(taste.copy(userId = session.userId))
    }

    suspend fun saveReviewDraft(restaurantId: Long, content: String, rating: Int, imagePath: String?) =
        withContext(Dispatchers.IO) {
            db.reviewDraftDao().insert(
                ReviewDraftEntity(restaurantId = restaurantId, content = content, rating = rating, imagePath = imagePath)
            )
        }

    private suspend fun seedTasteIfNeeded(userId: Long) {
        if (db.userTasteDao().getByUserId(userId) == null) {
            db.userTasteDao().insert(
                UserTasteEntity(
                    userId = userId,
                    spiceLevel = 2,
                    budgetMin = 30,
                    budgetMax = 100,
                    cuisines = "川菜,火锅,日料",
                    taboos = "无"
                )
            )
        }
    }

    suspend fun seedLocalDataIfNeeded() = withContext(Dispatchers.IO) {
        if (db.restaurantDao().getAll().isEmpty()) {
            db.restaurantDao().insertAll(getSeedRestaurants())
        }
        if (db.userDao().searchUsers("火锅").isEmpty()) {
            getSeedUsers().forEach { db.userDao().insert(it) }
        } else if (db.userDao().getById(1003L) == null) {
            db.userDao().insert(getSeedUsers().first { it.id == 1003L })
        }
        db.matchPostDao().insertAll(getSeedMatchPosts())
        db.reviewDao().insertAll(getSeedReviews())
        db.chatMessageDao().insertAll(getSeedChatMessages())
    }

    private fun mockAiReply(message: String): String {
        return when {
            message.contains("辣") -> "军师建议：今天来顿川菜怎么样？附近「蜀香小馆」评分 4.8，微辣友好～"
            message.contains("清淡") -> "军师献策：养生局推荐「清粥小厨」，暖胃不负担，人均 ¥35。"
            message.contains("火锅") -> "火锅一时爽，一直火锅一直爽！「凑凑一桌」正在摇人，要不要加入？"
            else -> "军师正在翻菜谱…建议你先看看附近觅食图，或者发个拼桌摇人！"
        }
    }

    // ==================== AI 增强方法（5题问卷 + 上下文推荐） ====================

    /**
     * AI 对话（带上下文）—— 三级降级：认证API → Guest API → 本地规则引擎
     */
    suspend fun aiChatWithContext(message: String, context: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = api.aiChat(AiChatRequest(message = message, context = context))
            if (result.code == 0 && result.data != null) {
                return@withContext Result.success(result.data.reply)
            }
        } catch (_: Exception) { /* 继续尝试 guest 接口 */ }

        try {
            val result = api.aiChatGuest(AiChatRequest(message = message, context = context))
            if (result.code == 0 && result.data != null) {
                return@withContext Result.success(result.data.reply)
            }
        } catch (_: Exception) { /* 最终降级本地 */ }

        Result.success(mockAiReplyWithContext(message, context))
    }

    /**
     * 基于上下文的智能模拟回复 —— 支持 5 题问卷格式
     */
    private fun mockAiReplyWithContext(message: String, context: String): String {
        val msg = message.lowercase()

        // 解析 5 题问卷格式
        val chosenFoodType = Regex("""想吃(.+?)[，,]""").find(message)?.groupValues?.get(1)?.trim()
        val chosenBudget = Regex("""预算(.+?)[，,]""").find(message)?.groupValues?.get(1)?.trim()
        val chosenOccasion = Regex("""场景「(.+?)」""").find(message)?.groupValues?.get(1)?.trim()
        val chosenTaste = Regex("""口味「(.+?)」""").find(message)?.groupValues?.get(1)?.trim()
        val chosenPriority = Regex("""最看重「(.+?)」""").find(message)?.groupValues?.get(1)?.trim()

        // 解析上下文中的餐厅列表
        val restaurantPattern = Regex("""- (.+?) \| (.+?) \| 人均[$¥](\d+) \| 评分([\d.]+)""")
        val restaurants = restaurantPattern.findAll(context).map { match ->
            val (name, cuisine, price, rating) = match.destructured
            RestaurantInfo(name, cuisine, price.toIntOrNull() ?: 0, rating.toDoubleOrNull() ?: 0.0)
        }.toList()

        if (restaurants.isEmpty()) {
            val foodType = chosenFoodType ?: ""
            return if (foodType.isNotBlank()) {
                "根据你的偏好（${foodType}·${chosenBudget ?: "适中预算"}），建议打开「觅食图」看看附近餐厅，或者去「摇人吃饭」发个局～"
            } else {
                "军师正在翻菜谱…建议你先打开「觅食图」看看附近有什么好店，或者去「摇人吃饭」发个局！"
            }
        }

        // 5 题问卷格式处理
        if (chosenFoodType != null || chosenBudget != null || chosenOccasion != null || chosenTaste != null) {
            val budgetRange = when {
                chosenBudget?.contains("30以下") == true -> 0..30
                chosenBudget?.contains("30-80") == true -> 30..80
                chosenBudget?.contains("80-150") == true -> 80..150
                chosenBudget?.contains("150以上") == true -> 150..Int.MAX_VALUE
                else -> 0..Int.MAX_VALUE
            }

            var filtered = restaurants.toList()

            // foodType → 关键词匹配
            if (!chosenFoodType.isNullOrBlank() && chosenFoodType != "不挑类型" && chosenFoodType != "任意菜系") {
                val keywords = foodTypeKeywords(chosenFoodType)
                val byType = filtered.filter { r ->
                    keywords.any { kw -> r.cuisine.contains(kw) || r.name.contains(kw) }
                }
                if (byType.isNotEmpty()) filtered = byType
            }

            // 口味过滤
            when (chosenTaste) {
                "嗜辣" -> {
                    val spicy = filtered.filter { it.cuisine.contains("川") || it.cuisine.contains("湘") || it.cuisine.contains("辣") }
                    if (spicy.isNotEmpty()) filtered = spicy
                }
                "嗜甜" -> {
                    val sweet = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("京") || it.cuisine.contains("日") }
                    if (sweet.isNotEmpty()) filtered = sweet
                }
                "偏咸" -> {
                    val salty = filtered.filter { it.cuisine.contains("京") || it.cuisine.contains("川") || it.cuisine.contains("烤") }
                    if (salty.isNotEmpty()) filtered = salty
                }
                "偏酸" -> {
                    val sour = filtered.filter { it.name.contains("酸") || it.cuisine.contains("湘") }
                    if (sour.isNotEmpty()) filtered = sour
                }
                "清淡" -> {
                    val light = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("日") || it.cuisine.contains("京") }
                    if (light.isNotEmpty()) filtered = light
                }
            }

            // 预算过滤
            val inBudget = filtered.filter { it.price in budgetRange }
            if (inBudget.isNotEmpty()) filtered = inBudget

            // 渐进放宽
            if (filtered.isEmpty()) {
                filtered = restaurants.toList()
                when (chosenTaste) {
                    "嗜辣" -> { val s = filtered.filter { it.cuisine.contains("川") || it.cuisine.contains("湘") }; if (s.isNotEmpty()) filtered = s }
                    "嗜甜" -> { val s = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("日") || it.cuisine.contains("京") }; if (s.isNotEmpty()) filtered = s }
                    "清淡" -> { val s = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("日") || it.cuisine.contains("京") }; if (s.isNotEmpty()) filtered = s }
                }
                val ib2 = filtered.filter { it.price in budgetRange }; if (ib2.isNotEmpty()) filtered = ib2
            }
            if (filtered.isEmpty()) filtered = restaurants.toList()

            // 按优先级排序
            filtered = when (chosenPriority) {
                "评分口碑" -> filtered.sortedByDescending { it.rating }
                "性价比"   -> filtered.sortedBy { it.price }
                "环境氛围" -> filtered.sortedByDescending { it.rating * 0.7 + it.price / 200.0 * 0.3 }
                else -> when (chosenOccasion) {
                    "情侣约会", "商务请客" -> filtered.sortedByDescending { it.rating }
                    "随便吃点" -> filtered.sortedBy { it.price }
                    else -> filtered.sortedByDescending { it.rating }
                }
            }

            val top3 = filtered.take(3)
            val summary = when {
                chosenTaste == "嗜辣" -> "根据你爱吃辣的偏好，为你精选："
                chosenTaste == "嗜甜" -> "根据你爱吃甜的偏好，为你精选："
                chosenTaste == "清淡" -> "根据你偏好清淡口味，为你精选："
                !chosenFoodType.isNullOrBlank() && chosenFoodType != "不挑类型" -> "根据你偏爱${chosenFoodType}，为你精选："
                else -> "军师为你精选以下菜馆："
            }
            val lines = top3.joinToString("\n") { r ->
                "【${r.name}】\n匹配理由：${r.cuisine} | 人均¥${r.price} | 评分${r.rating}，符合你的偏好"
            }
            val tip = when (chosenOccasion) {
                "情侣约会", "商务请客" -> "💡 建议提前预约～"
                "朋友聚餐" -> "💡 人多可以看看「摇人吃饭」！"
                else -> "💡 不满意？点击「重新来过」换一组条件试试～"
            }
            return "$summary\n\n$lines\n\n$tip"
        }

        // 自由对话语义解析（5题问卷未命中时，从自然语言提取偏好）
        val freeCuisineKw = extractCuisineKeywords(message)
        val freeTasteKw = extractTasteKeywords(message)
        val freeBudgetRange = extractBudgetRange(message)
        val freeOccasion = extractOccasionKeyword(message)

        if (freeCuisineKw.isNotEmpty() || freeTasteKw.isNotEmpty() || freeBudgetRange != null || freeOccasion != null) {
            var filtered = restaurants.toList()

            if (freeCuisineKw.isNotEmpty()) {
                val byCuisine = filtered.filter { r ->
                    freeCuisineKw.any { kw -> r.cuisine.contains(kw) || r.name.contains(kw) }
                }
                if (byCuisine.isNotEmpty()) filtered = byCuisine
            }

            if (freeTasteKw.isNotEmpty()) {
                val byTaste = filtered.filter { r ->
                    freeTasteKw.any { kw -> r.cuisine.contains(kw) || r.name.contains(kw) }
                }
                if (byTaste.isNotEmpty()) filtered = byTaste
            }

            freeBudgetRange?.let { range ->
                val byBudget = filtered.filter { it.price in range }
                if (byBudget.isNotEmpty()) filtered = byBudget
            }

            // 渐进放宽
            if (filtered.isEmpty()) {
                filtered = restaurants.toList()
                if (freeCuisineKw.isNotEmpty()) {
                    val relaxed = filtered.filter { r -> freeCuisineKw.any { kw -> r.cuisine.contains(kw) || r.name.contains(kw) } }
                    if (relaxed.isNotEmpty()) filtered = relaxed
                }
                freeBudgetRange?.let { range ->
                    val byBudget = filtered.filter { it.price in range }
                    if (byBudget.isNotEmpty()) filtered = byBudget
                }
            }
            if (filtered.isEmpty()) filtered = restaurants.toList()

            // 排序
            filtered = when (freeOccasion) {
                "约会", "请客" -> filtered.sortedByDescending { it.rating }
                "聚餐" -> filtered.sortedByDescending { it.rating * 0.5 + (100.0 / (it.price + 1)) * 0.5 }
                "省钱", "随便" -> filtered.sortedBy { it.price }
                else -> filtered.sortedByDescending { it.rating }
            }

            val top3 = filtered.take(3)
            if (top3.isEmpty()) {
                return "附近暂无完全匹配你要求的餐厅，试试放宽条件？或者告诉我其他偏好，军师再帮你找～"
            }

            val cuisineDesc = freeCuisineKw.joinToString("、")
            val tasteDesc = freeTasteKw.joinToString("、")
            val summaryParts = mutableListOf<String>()
            if (cuisineDesc.isNotBlank()) summaryParts.add("偏爱$cuisineDesc")
            if (tasteDesc.isNotBlank()) summaryParts.add("偏好$tasteDesc")
            if (freeBudgetRange != null) summaryParts.add("预算约¥${freeBudgetRange.first}-${freeBudgetRange.last}")
            val summary = if (summaryParts.isNotEmpty()) {
                "根据你${summaryParts.joinToString("，")}，军师为你精选："
            } else {
                "军师根据你的需求，为你推荐："
            }

            val lines = top3.joinToString("\n") { r ->
                "【${r.name}】\n推荐理由：${r.cuisine} | 人均¥${r.price} | 评分${r.rating}"
            }
            val tip = when (freeOccasion) {
                "约会", "请客" -> "\n💡 建议提前预约，有面子！"
                "聚餐" -> "\n💡 人多热闹，看看「摇人吃饭」组个局？"
                "省钱" -> "\n💡 好吃不贵才是王道！"
                else -> "\n💡 不满意？告诉我更具体的需求，军师再帮你挑～"
            }
            return "$summary\n\n$lines$tip"
        }

        // 旧版关键词兜底
        val spiceLevel = Regex("""辣度等级[：:]\\s*(\\d)""").find(context)?.groupValues?.get(1)?.toIntOrNull() ?: 2
        val budgetMin = Regex("""预算区间[：:]\\s*¥(\\d+)""").find(context)?.groupValues?.get(1)?.toIntOrNull() ?: 30
        val cuisines = Regex("""偏好菜系[：:]\\s*(.+)""").find(context)?.groupValues?.get(1) ?: ""
        val preferredCuisines = cuisines.split(",", "，").map { it.trim() }.filter { it.isNotBlank() && it != "不限" }
        val matching = restaurants.filter { r ->
            (preferredCuisines.isEmpty() || preferredCuisines.any { pc -> r.cuisine.contains(pc) || pc.contains(r.cuisine) }) &&
            r.price in budgetMin..(budgetMin + 70)
        }

        return when {
            msg.contains("辣") && spiceLevel >= 3 -> {
                val spicy = restaurants.filter { it.cuisine.contains("川") || it.cuisine.contains("湘") }
                if (spicy.isNotEmpty()) {
                    val pick = spicy.first()
                    "军师鉴辣：你的耐辣度${spiceLevel}级，推荐「${pick.name}」！${pick.cuisine} | 人均¥${pick.price} | 评分${pick.rating}\n\n无辣不欢，但要量力而行哦～"
                } else "附近暂无川湘菜馆。要不试试「蜀香小馆」？虽然有点距离，但评分 4.8 值得跑一趟！"
            }
            msg.contains("清淡") || msg.contains("养生") || msg.contains("健康") -> {
                val light = restaurants.filter { r ->
                    r.cuisine.contains("粤") || r.cuisine.contains("京") || r.cuisine.contains("素") ||
                    r.name.contains("粥") || r.name.contains("清")
                }
                if (light.isNotEmpty()) {
                    val pick = light.first()
                    "军师养生推荐：「${pick.name}」—— ${pick.cuisine} | 人均¥${pick.price} | 评分${pick.rating}\n\n清淡不寡淡，暖胃又满足！"
                } else "养生局推荐「清粥小厨」，人均¥35，暖胃不负担～"
            }
            msg.contains("约会") || msg.contains("聚餐") || msg.contains("请客") -> {
                val fancy = restaurants.filter { r -> r.rating >= 4.7 && r.price >= 80 }
                if (fancy.isNotEmpty()) {
                    val pick = fancy.first()
                    "军师排面局：「${pick.name}」—— ${pick.cuisine} | 人均¥${pick.price} | 评分${pick.rating}\n\n环境好、出品稳，约会聚餐不翻车！"
                } else "排面局推荐「樱花寿司」—— 日料 | 人均¥120 | 评分 4.9，精致有面子！"
            }
            msg.contains("便宜") || msg.contains("实惠") || msg.contains("省钱") -> {
                val cheap = restaurants.filter { it.price <= budgetMin + 20 }.sortedBy { it.price }
                if (cheap.isNotEmpty()) {
                    val pick = cheap.first()
                    "军师持家推荐：「${pick.name}」—— ${pick.cuisine} | 人均¥${pick.price} | 评分${pick.rating}\n\n好吃不贵，钱包不累！"
                } else "实惠之选「老北京炸酱面」，人均¥28，地道又管饱！"
            }
            matching.isNotEmpty() -> {
                val pick = matching.first()
                if (matching.size > 1) {
                    val alt = matching[1]
                    "军师精选：「${pick.name}」—— ${pick.cuisine} | 人均¥${pick.price} | 评分${pick.rating}\n\n根据你的口味（${cuisines.ifBlank { "全菜系" }}），这家很对胃！备选：「${alt.name}」也不错～"
                } else {
                    "军师精选：「${pick.name}」—— ${pick.cuisine} | 人均¥${pick.price} | 评分${pick.rating}\n\n根据你的口味，这家最匹配！"
                }
            }
            restaurants.isNotEmpty() -> {
                val pick = restaurants.sortedByDescending { it.rating }.first()
                "军师推荐：「${pick.name}」—— ${pick.cuisine} | 人均¥${pick.price} | 评分${pick.rating}\n\n不知道吃啥？试试这家！不满意的话告诉我你的需求，军师再帮你挑～"
            }
            else -> "军师正在翻菜谱…建议你先打开「觅食图」看看附近有什么好店，或者去「摇人吃饭」发个局！"
        }
    }

    /** foodType 文本 → 菜系关键词映射 */
    private fun foodTypeKeywords(foodType: String): List<String> = when (foodType) {
        "无肉不欢" -> listOf("烤", "肉", "川", "湘", "烧")
        "素食清淡" -> listOf("素", "粥", "粤", "清")
        "面食小吃" -> listOf("面", "京", "小")
        "日式料理" -> listOf("日", "寿司")
        "快餐速食" -> listOf("快", "面", "炸")
        "家常菜"   -> listOf("京", "粤", "川", "小")
        else       -> emptyList()
    }

    // ==================== 自由对话语义提取 ====================

    /** 从自然语言中提取菜系关键词，返回用于餐厅过滤的关键词列表 */
    private fun extractCuisineKeywords(message: String): List<String> {
        val result = mutableListOf<String>()
        val msg = message.lowercase()

        // 自然语言关键词 → 餐厅过滤词
        val cuisineMap = mapOf(
            // 川湘辣系
            listOf("川菜", "四川", "麻辣", "水煮", "毛血旺", "辣子", "串串", "钵钵") to listOf("川", "湘", "辣"),
            // 粤菜
            listOf("粤菜", "广东", "烧腊", "煲仔", "点心", "蒸", "白切", "炖品") to listOf("粤", "京"),
            // 日料
            listOf("日料", "日式", "寿司", "刺身", "拉面", "天妇罗", "居酒", "铁板", "鳗鱼") to listOf("日", "寿司"),
            // 西餐
            listOf("西餐", "牛排", "意面", "披萨", "沙拉", "汉堡", "法餐", "分子") to listOf("烤", "肉", "面", "西"),
            // 韩餐
            listOf("韩餐", "韩式", "烤肉", "炸鸡", "部队锅", "石锅", "参鸡汤") to listOf("烤", "韩"),
            // 烧烤
            listOf("烧烤", "烤肉", "烤串", "撸串", "BBQ") to listOf("烤"),
            // 面食
            listOf("面食", "面条", "拉面", "刀削", "饺子", "馄饨", "包", "粉") to listOf("面", "京"),
            // 火锅
            listOf("火锅", "涮", "麻辣烫", "冒菜", "串串香") to listOf("火", "锅", "川"),
            // 海鲜
            listOf("海鲜", "虾", "蟹", "鱼", "贝", "龙虾", "鲍鱼") to listOf("海", "鲜"),
            // 湘菜
            listOf("湘菜", "湖南") to listOf("湘", "川"),
            // 清真
            listOf("清真", "回民", "羊肉", "大盘鸡") to listOf("烤", "肉"),
            // 素食
            listOf("素食", "吃素", "清淡", "轻食", "沙拉", "健康") to listOf("素", "粥", "粤", "轻"),
            // 甜品
            listOf("甜品", "蛋糕", "奶茶", "糖水", "布丁") to listOf("甜"),
            // 东北
            listOf("东北", "铁锅炖", "锅包") to listOf("东", "肉"),
            // 快餐
            listOf("快餐", "简餐", "盖饭", "盒饭", "套餐") to listOf("快", "面"),
            // 小吃
            listOf("小吃", "零食") to listOf("小", "面"),
            // 自助
            listOf("自助", "畅吃", "放题") to listOf("自"),
        )

        for ((keywords, tags) in cuisineMap) {
            if (keywords.any { msg.contains(it) }) {
                result.addAll(tags)
            }
        }
        return result.distinct()
    }

    /** 从自然语言中提取口味偏好关键词 */
    private fun extractTasteKeywords(message: String): List<String> {
        val result = mutableListOf<String>()
        val msg = message.lowercase()

        if (msg.contains("辣") || msg.contains("麻辣")) result.addAll(listOf("川", "湘"))
        if (msg.contains("清淡") || msg.contains("养生")) result.addAll(listOf("粤", "日", "京", "素"))
        if (msg.contains("酸") || msg.contains("糖醋")) result.addAll(listOf("湘", "粤", "京"))
        if (msg.contains("甜") || msg.contains("糖")) result.addAll(listOf("粤", "日", "京"))
        if (msg.contains("咸") || msg.contains("重口")) result.addAll(listOf("川", "湘", "京"))

        return result.distinct()
    }

    /** 从自然语言中提取预算范围，返回 IntRange 或 null */
    private fun extractBudgetRange(message: String): IntRange? {
        // 匹配 "人均XX" 或 "XX元" 或 "¥XX"
        val priceRegex = Regex("""(?:人均|¥|元)?(\d{2,4})\s*(?:元|块|以内|左右|上下)?""")
        val match = priceRegex.find(message)
        if (match != null) {
            val price = match.groupValues[1].toIntOrNull()
            if (price != null) {
                return when {
                    price <= 30 -> 0..30
                    price <= 80 -> 30..80
                    price <= 150 -> maxOf(80, price - 30)..minOf(150, price + 50)
                    else -> 150..Int.MAX_VALUE
                }
            }
        }

        val msg = message.lowercase()
        return when {
            msg.contains("便宜") || msg.contains("实惠") || msg.contains("省钱") ||
            msg.contains("低消费") || msg.contains("平价") -> 0..50

            msg.contains("贵") || msg.contains("高档") || msg.contains("奢华") ||
            msg.contains("宴请") || msg.contains("高级") -> 120..Int.MAX_VALUE

            msg.contains("适中") || msg.contains("中等") -> 50..120

            else -> null
        }
    }

    /** 从自然语言中提取场景关键词 */
    private fun extractOccasionKeyword(message: String): String? {
        val msg = message.lowercase()
        return when {
            msg.contains("约会") || msg.contains("情侣") || msg.contains("约会") -> "约会"
            msg.contains("聚餐") || msg.contains("朋友") || msg.contains("聚会") || msg.contains("团建") -> "聚餐"
            msg.contains("请客") || msg.contains("宴请") || msg.contains("客户") || msg.contains("商务") -> "请客"
            msg.contains("省钱") || msg.contains("便饭") || msg.contains("随便") || msg.contains("简单") -> "省钱"
            else -> null
        }
    }

    // ===========================================================

    private data class RestaurantInfo(
        val name: String,
        val cuisine: String,
        val price: Int,
        val rating: Double
    )

    // ---- AI 对话历史管理 ----

    suspend fun getAiConversations(): List<AiConversationEntity> = withContext(Dispatchers.IO) {
        db.aiConversationDao().getByUser(session.userId)
    }

    suspend fun createAiConversation(title: String): Long = withContext(Dispatchers.IO) {
        db.aiConversationDao().insert(AiConversationEntity(userId = session.userId, title = title))
    }

    suspend fun getAiMessages(conversationId: Long): List<AiMessageEntity> = withContext(Dispatchers.IO) {
        db.aiMessageDao().getByConversation(conversationId)
    }

    suspend fun saveAiMessage(msg: AiMessageEntity) = withContext(Dispatchers.IO) {
        db.aiMessageDao().insert(msg)
    }

    suspend fun deleteAiConversation(conversationId: Long) = withContext(Dispatchers.IO) {
        db.aiMessageDao().deleteByConversation(conversationId)
        db.aiConversationDao().delete(conversationId)
    }

    companion object {
        @Volatile
        private var INSTANCE: GanfanRepository? = null

        fun getInstance(context: Context): GanfanRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GanfanRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

private fun UserDto.toEntity() = UserEntity(
    id = id,
    phone = phone,
    nickname = nickname,
    avatarUrl = avatarUrl,
    bio = bio,
    city = city,
    creditScore = creditScore,
    userId2 = userId2
)

private fun RestaurantDto.toEntity() = RestaurantEntity(
    id, amapPoiId, name, lat, lng, address, avgPrice, cuisine, rating, tags.joinToString(","), distance
)

private fun MatchPostDto.toEntity() = MatchPostEntity(
    id, publisherId, publisherName, restaurantId, restaurantName,
    title, mealTime, maxPeople, currentPeople, budget, lat, lng, status
)

private fun ReviewDto.toEntity() = ReviewEntity(
    id, userId, userName, avatarUrl, restaurantId, restaurantName,
    rating, content, images.joinToString(","), createdAt, parentId, likesCount
)

private fun ReviewEntity.toDto() = ReviewDto(
    id, userId, userName, avatarUrl, restaurantId, restaurantName,
    rating, content, images.split(",").filter { it.isNotBlank() }, createdAt, parentId, likesCount
)

private fun ConversationDto.toEntity() = ConversationEntity(
    id, userId, partnerId, partnerName, partnerAvatar, lastMessage, lastTime, unreadCount, matchPostId
)

private fun getSeedUsers() = listOf(
    UserEntity(
        id = 1001, phone = "13800001001", nickname = "楚晨",
        avatarUrl = null, bio = "独立建筑设计师，热爱艺术，小众爵士乐，偶尔下厨。", city = "北京", creditScore = 95, userId2 = "楚晨"
    ),
    UserEntity(
        id = 1002, phone = "13800001002", nickname = "寿司控",
        avatarUrl = null, bio = "日料爱好者", city = "北京", creditScore = 98, userId2 = "sushi_fan"
    ),
    UserEntity(
        id = 1003, phone = "13800001003", nickname = "面食爱好者",
        avatarUrl = null, bio = "北面南吃，炸酱面爱好者", city = "北京", creditScore = 92, userId2 = "noodle_fan"
    )
)

private fun getSeedRestaurants() = listOf(
    RestaurantEntity(1, "poi_001", "蜀香小馆", 39.91, 116.40, "朝阳区美食街1号", 68, "川菜", 4.8, "辣,下饭", 320.0),
    RestaurantEntity(2, "poi_002", "清粥小厨", 39.92, 116.41, "海淀区养生路8号", 35, "粤菜", 4.6, "清淡,养生", 580.0),
    RestaurantEntity(3, "poi_003", "炭火烤肉屋", 39.90, 116.39, "东城区烤肉巷3号", 98, "韩式烤肉", 4.7, "烤肉,聚餐", 450.0),
    RestaurantEntity(4, "poi_004", "樱花寿司", 39.93, 116.42, "西城区日料街5号", 120, "日料", 4.9, "寿司,精致", 890.0),
    RestaurantEntity(5, "poi_005", "老北京炸酱面", 39.89, 116.38, "丰台区胡同口12号", 28, "京菜", 4.5, "面食,实惠", 210.0)
)

private fun getSeedMatchPosts() = listOf(
    MatchPostEntity(
        101, 1001, "楚晨", 3, "莫奈花园·法式私厨小馆",
        "《莫奈花园》的黑松露薯条和慢烤春鸡非常赞，环境也很安静。希望能邀请一位同样热爱艺术或设计、喜欢安静聊天的小姐姐共度周六温馨晚餐。",
        "本周六 18:00", 4, 2, 180, 39.90, 116.39, "open"
    ),
    MatchPostEntity(
        102, 1002, "寿司控", 4, "樱花寿司",
        "周末想约几位日料爱好者一起探店，刺身新鲜度很重要，欢迎同样注重食材品质的朋友。",
        "周六 12:00", 3, 1, 150, 39.93, 116.42, "open"
    ),
    MatchPostEntity(
        103, 1003, "面食爱好者", 5, "老北京炸酱面",
        "中午凑一桌吃炸酱面，聊聊老北京胡同里的美食记忆，轻松随意就好。",
        "今天 12:00", 2, 1, 30, 39.89, 116.38, "open"
    )
)

private fun getSeedReviews() = listOf(
    ReviewEntity(1, 1001, "火锅达人", null, 3, "炭火烤肉屋", 5, "肉质新鲜，服务员帮烤，拼桌氛围超好！", "", "今天"),
    ReviewEntity(2, 1002, "寿司控", null, 4, "樱花寿司", 5, "刺身入口即化，军师推荐靠谱！", "", "昨天")
)

private fun getSeedChatMessages() = listOf(
    ChatMessageEntity(1, 1, 1001, "火锅达人", "text", "大家好，今晚6点半到店～", "18:00"),
    ChatMessageEntity(2, 1, 1002, "寿司控", "text", "我可以带一瓶清酒来！", "18:05"),
    ChatMessageEntity(3, 1, 0, "军师", "text", "提醒：记得提前在 App 打卡哦", "18:10")
)
