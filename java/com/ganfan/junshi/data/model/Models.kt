package com.ganfan.junshi.data.model

data class UserDto(
    val id: Long = 0,
    val phone: String = "",
    val nickname: String = "",
    val avatarUrl: String? = null,
    val bio: String? = null,
    val city: String? = null,
    val creditScore: Int = 100,
    val userId2: String? = null
)

data class LoginRequest(val phone: String, val password: String)
data class LoginResponse(val token: String, val user: UserDto)

data class RestaurantDto(
    val id: Long = 0,
    val amapPoiId: String? = null,
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String = "",
    val avgPrice: Int = 0,
    val cuisine: String = "",
    val rating: Double = 0.0,
    val tags: List<String> = emptyList(),
    val distance: Double? = null
)

data class MatchPostDto(
    val id: Long = 0,
    val publisherId: Long = 0,
    val publisherName: String = "",
    val restaurantId: Long? = null,
    val restaurantName: String? = null,
    val title: String = "",
    val mealTime: String = "",
    val maxPeople: Int = 4,
    val currentPeople: Int = 1,
    val budget: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val status: String = "open"
)

data class CreateMatchRequest(
    val title: String,
    val mealTime: String,
    val maxPeople: Int,
    val budget: Int,
    val restaurantId: Long? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

data class ReviewDto(
    val id: Long = 0,
    val userId: Long = 0,
    val userName: String = "",
    val avatarUrl: String? = null,
    val restaurantId: Long = 0,
    val restaurantName: String = "",
    val rating: Int = 5,
    val content: String = "",
    val images: List<String> = emptyList(),
    val createdAt: String = "",
    val parentId: Long = 0,
    val likesCount: Int = 0
)

data class ChatMessageDto(
    val id: Long = 0,
    val conversationId: Long = 0,
    val senderId: Long = 0,
    val senderName: String = "",
    val msgType: String = "text",
    val content: String = "",
    val sentAt: String = ""
)

data class AiChatRequest(val message: String, val context: String? = null)
data class AiChatResponse(val reply: String)

data class FollowDto(
    val followerId: Long = 0,
    val followingId: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class ConversationDto(
    val id: Long = 0,
    val userId: Long = 0,
    val partnerId: Long = 0,
    val partnerName: String = "",
    val partnerAvatar: String? = null,
    val lastMessage: String = "",
    val lastTime: String = "",
    val unreadCount: Int = 0,
    val matchPostId: Long = 0
)

data class MatchApplicationDto(
    val id: Long = 0,
    val postId: Long = 0,
    val applicantId: Long = 0,
    val applicantName: String = "",
    val publisherId: Long = 0,
    val status: String = "pending",
    val message: String? = null,
    val createdAt: String = ""
)

/** 消息-邀约 Tab 统一展示项 */
data class MatchInviteItem(
    val id: Long,
    val type: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val postId: Long = 0,
    val relatedUserId: Long = 0,
    val relatedUserName: String = ""
)

data class RegisterRequest(
    val phone: String,
    val password: String,
    val userId2: String,
    val nickname: String
)

data class FollowStatusResponse(
    val isFollowing: Boolean,
    val isMutual: Boolean
)

data class ApiResult<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
)

data class ConversationSummary(
    val conversationId: Long,
    val title: String,
    val peerName: String,
    val lastMessage: String,
    val time: String,
    val restaurantName: String?
)

data class RestaurantMatchStats(
    val openPostCount: Int,
    val totalSeekingPeople: Int,
    val maxTableSize: Int = 0
)
