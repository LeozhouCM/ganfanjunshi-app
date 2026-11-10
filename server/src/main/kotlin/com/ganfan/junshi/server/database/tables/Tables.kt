package com.ganfan.junshi.server.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

// =====================================================
// 1. 用户表
// =====================================================
object Users : Table("users") {
    val id = long("id").autoIncrement()
    val phone = varchar("phone", 20).nullable()
    val userId2 = varchar("user_id2", 50).nullable()
    val passwordHash = varchar("password_hash", 255).nullable()
    val nickname = varchar("nickname", 50)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val gender = integer("gender").default(0)
    val status = integer("status").default(1)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 2. 口味画像表
// =====================================================
object UserTasteProfiles : Table("user_taste_profiles") {
    val userId = long("user_id").references(Users.id)
    val spiceLevel = integer("spice_level").default(0)
    val avgBudget = integer("avg_budget").nullable()
    val favoriteCuisines = text("favorite_cuisines").nullable()
    val dietaryRestrictions = text("dietary_restrictions").nullable()
    val updatedAt = datetime("updated_at").nullable()
    override val primaryKey = PrimaryKey(userId)
}

// =====================================================
// 3. 用户关注表
// =====================================================
object UserRelations : Table("user_relations") {
    val id = long("id").autoIncrement()
    val followerId = long("follower_id").references(Users.id)
    val followeeId = long("followee_id").references(Users.id)
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 4. 餐厅表
// =====================================================
object Restaurants : Table("restaurants") {
    val id = long("id").autoIncrement()
    val amapPoiId = varchar("amap_poi_id", 100).nullable()
    val name = varchar("name", 100)
    val address = varchar("address", 300).nullable()
    val lat = decimal("lat", 10, 7).nullable()
    val lng = decimal("lng", 10, 7).nullable()
    val cuisine = varchar("cuisine", 50).nullable()
    val avgPrice = integer("avg_price").nullable()
    val rating = decimal("rating", 2, 1).default(java.math.BigDecimal("0"))
    val coverUrl = varchar("cover_url", 500).nullable()
    val phone = varchar("phone", 20).nullable()
    val openingHours = varchar("opening_hours", 200).nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 5. 收藏表
// =====================================================
object Favorites : Table("favorites") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val restaurantId = long("restaurant_id").references(Restaurants.id)
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 6. 拼桌帖表
// =====================================================
object MatchPosts : Table("match_posts") {
    val id = long("id").autoIncrement()
    val publisherId = long("publisher_id").references(Users.id)
    val restaurantId = long("restaurant_id").references(Restaurants.id).nullable()
    val title = varchar("title", 100)
    val mealTime = datetime("meal_time")
    val maxPeople = integer("max_people")
    val currentPeople = integer("current_people").default(1)
    val budget = integer("budget").nullable()
    val lat = decimal("lat", 10, 7).nullable()
    val lng = decimal("lng", 10, 7).nullable()
    val note = text("note").nullable()
    val status = varchar("status", 20).default("recruiting")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 7. 拼桌申请表
// =====================================================
object MatchApplications : Table("match_applications") {
    val id = long("id").autoIncrement()
    val postId = long("post_id").references(MatchPosts.id)
    val applicantId = long("applicant_id").references(Users.id)
    val message = varchar("message", 200).nullable()
    val status = varchar("status", 20).default("pending")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 8. 点评表
// =====================================================
object Reviews : Table("reviews") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val restaurantId = long("restaurant_id").references(Restaurants.id)
    val rating = decimal("rating", 2, 1)
    val content = text("content").nullable()
    val images = text("images").nullable()
    val status = varchar("status", 20).default("published")
    val likeCount = integer("like_count").default(0)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 9. AI 对话会话表
// =====================================================
object AiConversations : Table("ai_conversations") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val title = varchar("title", 100).nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 10. AI 消息表
// =====================================================
object AiMessages : Table("ai_messages") {
    val id = long("id").autoIncrement()
    val conversationId = long("conversation_id").references(AiConversations.id)
    val role = varchar("role", 20)
    val content = text("content")
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 11. 饭桌聊天会话表
// =====================================================
object ChatConversations : Table("chat_conversations") {
    val id = long("id").autoIncrement()
    val matchPostId = long("match_post_id")
    val userId = long("user_id").nullable()
    val partnerId = long("partner_id").nullable()
    val lastMessage = text("last_message").nullable()
    val lastTime = datetime("last_time").nullable()
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 12. 聊天消息表
// =====================================================
object ChatMessages : Table("chat_messages") {
    val id = long("id").autoIncrement()
    val conversationId = long("conversation_id").references(ChatConversations.id)
    val senderId = long("sender_id").references(Users.id)
    val content = text("content").nullable()
    val type = varchar("type", 20).default("text")
    val sentAt = datetime("sent_at")
    override val primaryKey = PrimaryKey(id)
}

// =====================================================
// 13. 通知表
// =====================================================
object Notifications : Table("notifications") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val type = varchar("type", 30)
    val title = varchar("title", 200)
    val content = text("content")
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val actionType = varchar("action_type", 50).nullable()
    val actionId = varchar("action_id", 100).nullable()
    val isRead = bool("is_read").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}
