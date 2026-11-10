package com.ganfan.junshi.server.model

import java.time.LocalDateTime

data class ApiResult<T>(val code: Int = 0, val message: String = "ok", val data: T? = null)

data class UserDto(
    val id: Long,
    val phone: String?,
    val nickname: String,
    val avatarUrl: String? = null,
    val gender: Int = 0,
    val status: Int = 1
)

data class LoginRequest(val phone: String, val password: String)
data class LoginResponse(val token: String, val user: UserDto)

data class RegisterRequest(val phone: String, val password: String, val userId2: String, val nickname: String)

data class RestaurantDto(
    val id: Long,
    val amapPoiId: String?,
    val name: String,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    val cuisine: String?,
    val avgPrice: Int?,
    val rating: Double,
    val coverUrl: String? = null,
    val phone: String? = null,
    val openingHours: String? = null,
    val distance: Double? = null
)

data class MatchPostDto(
    val id: Long,
    val publisherId: Long,
    val publisherName: String,
    val restaurantId: Long?,
    val restaurantName: String?,
    val title: String,
    val mealTime: String,
    val maxPeople: Int,
    val currentPeople: Int,
    val budget: Int?,
    val lat: Double?,
    val lng: Double?,
    val note: String? = null,
    val status: String
)

data class CreateMatchRequest(
    val title: String,
    val mealTime: String,
    val maxPeople: Int,
    val budget: Int,
    val restaurantId: Long? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val note: String? = null
)

data class ReviewDto(
    val id: Long,
    val userId: Long,
    val userName: String,
    val restaurantId: Long,
    val restaurantName: String,
    val rating: Double,
    val content: String?,
    val images: List<String>,
    val status: Int,
    val likeCount: Int,
    val createdAt: String,
    val updatedAt: String?
)

data class ChatMessageDto(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
    val senderName: String,
    val type: String,
    val content: String?,
    val sentAt: String
)

data class AiConversationDto(
    val id: Long,
    val userId: Long,
    val title: String?,
    val createdAt: String,
    val updatedAt: String?
)

data class AiMessageDto(
    val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAt: String
)

data class AiChatRequest(val message: String, val conversationId: Long? = null, val context: String? = null)
data class AiChatResponse(val reply: String, val conversationId: Long)

data class FavoritesDto(
    val id: Long,
    val userId: Long,
    val restaurantId: Long,
    val createdAt: String
)

data class CreateReviewRequest(
    val restaurantId: Long,
    val rating: Double,
    val content: String? = null,
    val images: List<String> = emptyList()
)

data class UpdateUserRequest(
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val gender: Int? = null
)

data class TasteProfileDto(
    val spiceLevel: Int,
    val avgBudget: Int?,
    val favoriteCuisines: String,
    val dietaryRestrictions: String
)

data class UpdateTasteProfileRequest(
    val spiceLevel: Int? = null,
    val avgBudget: Int? = null,
    val favoriteCuisines: String? = null,
    val dietaryRestrictions: String? = null
)

data class NotificationDto(
    val id: Long,
    val userId: Long,
    val type: String,
    val title: String,
    val content: String,
    val avatarUrl: String? = null,
    val actionType: String? = null,
    val actionId: String? = null,
    val isRead: Boolean = false,
    val createdAt: String
)

data class ConversationDto(
    val conversationId: Long,
    val partnerId: Long,
    val partnerName: String,
    val partnerAvatar: String?,
    val lastMessage: String?,
    val lastTime: String?,
    val unreadCount: Int
)
