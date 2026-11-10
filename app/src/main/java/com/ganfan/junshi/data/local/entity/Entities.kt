package com.ganfan.junshi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long,
    val phone: String,
    val nickname: String,
    val avatarUrl: String?,
    val bio: String?,
    val city: String?,
    val creditScore: Int,
    val realName: String? = null,
    val realNameVerified: Boolean = false,
    val attendanceRate: Int = 95,
    val lateRate: Int = 5,
    val blockCount: Int = 0,
    val userId2: String? = null
)

@Entity(tableName = "restaurants")
data class RestaurantEntity(
    @PrimaryKey val id: Long,
    val amapPoiId: String?,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String,
    val avgPrice: Int,
    val cuisine: String,
    val rating: Double,
    val tags: String,
    val distance: Double?
)

@Entity(tableName = "favorites", primaryKeys = ["userId", "restaurantId"])
data class FavoriteEntity(
    val userId: Long,
    val restaurantId: Long,
    val restaurantName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_taste")
data class UserTasteEntity(
    @PrimaryKey val userId: Long,
    val spiceLevel: Int,
    val budgetMin: Int,
    val budgetMax: Int,
    val cuisines: String,
    val taboos: String
)

@Entity(tableName = "match_posts")
data class MatchPostEntity(
    @PrimaryKey val id: Long,
    val publisherId: Long,
    val publisherName: String,
    val restaurantId: Long?,
    val restaurantName: String?,
    val title: String,
    val mealTime: String,
    val maxPeople: Int,
    val currentPeople: Int,
    val budget: Int,
    val lat: Double,
    val lng: Double,
    val status: String
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: Long,
    val conversationId: Long,
    val senderId: Long,
    val senderName: String,
    val msgType: String,
    val content: String,
    val sentAt: String
)

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey val id: Long,
    val userId: Long,
    val userName: String,
    val avatarUrl: String? = null,
    val restaurantId: Long,
    val restaurantName: String,
    val rating: Int,
    val content: String,
    val images: String,
    val createdAt: String,
    val parentId: Long = 0,
    val likesCount: Int = 0
)

@Entity(tableName = "review_drafts")
data class ReviewDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val restaurantId: Long,
    val content: String,
    val rating: Int,
    val imagePath: String?
)

@Entity(tableName = "follows", primaryKeys = ["followerId", "followingId"])
data class FollowEntity(
    val followerId: Long,
    val followingId: Long,
    val sourceMatchPostId: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "match_applications")
data class MatchApplicationEntity(
    @PrimaryKey val id: Long,
    val postId: Long,
    val applicantId: Long,
    val applicantName: String,
    val publisherId: Long,
    val status: String,
    val message: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "buddy_relations", primaryKeys = ["userId", "partnerId"])
data class BuddyEntity(
    val userId: Long,
    val partnerId: Long,
    val partnerName: String,
    val sparkLevel: Int = 1,
    val mealCount: Int = 1,
    val sourceMatchPostId: Long,
    val lastMealAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "meal_confirmations", primaryKeys = ["matchPostId", "userId"])
data class MealConfirmationEntity(
    val matchPostId: Long,
    val userId: Long,
    val confirmedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "buddy_match_invites")
data class BuddyMatchInviteEntity(
    @PrimaryKey val id: Long,
    val matchPostId: Long,
    val fromUserId: Long,
    val fromUserName: String,
    val toUserId: Long,
    val restaurantName: String,
    val postTitle: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "review_likes", primaryKeys = ["reviewId", "userId"])
data class ReviewLikeEntity(
    val reviewId: Long,
    val userId: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Long,
    val userId: Long,
    val partnerId: Long,
    val partnerName: String,
    val partnerAvatar: String? = null,
    val lastMessage: String = "",
    val lastTime: String = "",
    val unreadCount: Int = 0,
    val matchPostId: Long = 0
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromUserId: Long,
    val toUserId: Long,
    val type: String,
    val content: String,
    val relatedReviewId: Long = 0,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_conversations")
data class AiConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_messages")
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
