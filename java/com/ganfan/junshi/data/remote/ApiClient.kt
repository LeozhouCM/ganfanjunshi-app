package com.ganfan.junshi.data.remote

import com.ganfan.junshi.BuildConfig
import com.ganfan.junshi.data.model.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface GanfanApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResult<LoginResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResult<UserDto>

    @GET("api/users/me")
    suspend fun getMe(): ApiResult<UserDto>

    @GET("api/users/search")
    suspend fun searchUsers(@Query("keyword") keyword: String): ApiResult<List<UserDto>>

    @GET("api/users/{id}")
    suspend fun getUserById(@Path("id") id: Long): ApiResult<UserDto>

    @POST("api/users/{id}/follow")
    suspend fun followUser(@Path("id") id: Long): ApiResult<Unit>

    @DELETE("api/users/{id}/follow")
    suspend fun unfollowUser(@Path("id") id: Long): ApiResult<Unit>

    @GET("api/users/{id}/follow-status")
    suspend fun getFollowStatus(@Path("id") id: Long): ApiResult<FollowStatusResponse>

    @GET("api/restaurants/nearby")
    suspend fun getNearbyRestaurants(
        @Query("lat") lat: Double = 39.9,
        @Query("lng") lng: Double = 116.4,
        @Query("radius") radius: Int = 3000
    ): ApiResult<List<RestaurantDto>>

    @GET("api/restaurants/{id}")
    suspend fun getRestaurant(@Path("id") id: Long): ApiResult<RestaurantDto>

    @GET("api/match/posts")
    suspend fun getMatchPosts(): ApiResult<List<MatchPostDto>>

    @POST("api/match/posts")
    suspend fun createMatchPost(@Body request: CreateMatchRequest): ApiResult<MatchPostDto>

    @POST("api/match/posts/{id}/join")
    suspend fun joinMatchPost(@Path("id") id: Long, @Body body: Map<String, String> = emptyMap()): ApiResult<MatchApplicationDto>

    @POST("api/match/posts/{id}/apply")
    suspend fun applyToMatchPost(
        @Path("id") postId: Long,
        @Body body: Map<String, String>
    ): ApiResult<MatchApplicationDto>

    @GET("api/match/posts/{id}/applications")
    suspend fun getApplicationsForPost(@Path("id") postId: Long): ApiResult<List<MatchApplicationDto>>

    @POST("api/match/posts/{postId}/applications/{appId}/approve")
    suspend fun approveMatchApplication(
        @Path("postId") postId: Long,
        @Path("appId") appId: Long
    ): ApiResult<Unit>

    @POST("api/match/posts/{postId}/applications/{appId}/reject")
    suspend fun rejectMatchApplication(
        @Path("postId") postId: Long,
        @Path("appId") appId: Long
    ): ApiResult<Unit>

    @GET("api/match/applications/mine")
    suspend fun getMyMatchApplications(): ApiResult<List<MatchApplicationDto>>

    @GET("api/reviews")
    suspend fun getReviews(): ApiResult<List<ReviewDto>>

    @GET("api/reviews/restaurant/{restaurantId}")
    suspend fun getRestaurantReviews(@Path("restaurantId") restaurantId: Long): ApiResult<List<ReviewDto>>

    @POST("api/reviews")
    suspend fun createReview(@Body review: ReviewDto): ApiResult<ReviewDto>

    @POST("api/reviews/{id}/like")
    suspend fun likeReview(@Path("id") reviewId: Long): ApiResult<Unit>

    @DELETE("api/reviews/{id}/like")
    suspend fun unlikeReview(@Path("id") reviewId: Long): ApiResult<Unit>

    @GET("api/chat/{conversationId}/messages")
    suspend fun getChatMessages(@Path("conversationId") conversationId: Long): ApiResult<List<ChatMessageDto>>

    @POST("api/chat/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: Long,
        @Body body: Map<String, String>
    ): ApiResult<ChatMessageDto>

    @GET("api/conversations")
    suspend fun getConversations(): ApiResult<List<ConversationDto>>

    @POST("api/conversations")
    suspend fun createConversation(@Body body: Map<String, Any>): ApiResult<ConversationDto>

    @POST("api/ai/chat")
    suspend fun aiChat(@Body request: AiChatRequest): ApiResult<AiChatResponse>

    /** 无需认证的 AI 接口（游客/离线 Token 兜底） */
    @POST("api/ai/chat/guest")
    suspend fun aiChatGuest(@Body request: AiChatRequest): ApiResult<AiChatResponse>

    @GET("api/ai/conversations")
    suspend fun getAiConversations(): ApiResult<List<Map<String, Any>>>

    @POST("api/favorites/{restaurantId}")
    suspend fun addFavorite(@Path("restaurantId") restaurantId: Long): ApiResult<Unit>

    @DELETE("api/favorites/{restaurantId}")
    suspend fun removeFavorite(@Path("restaurantId") restaurantId: Long): ApiResult<Unit>
}

object ApiClient {
    private var token: String? = null

    fun setToken(newToken: String?) {
        token = newToken
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder().apply {
            token?.let { addHeader("Authorization", "Bearer $it") }
        }.build()
        chain.proceed(request)
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: GanfanApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GanfanApiService::class.java)
    }
}

/**
 * WebSocket 客户端 — 连接到 /ws/chat/{conversationId} 接收实时消息。
 */
class ChatWebSocketClient(
    private val conversationId: Long,
    private val token: String,
    private val onMessageReceived: (conversationId: Long, senderId: Long, senderName: String, content: String, sentAt: String) -> Unit
) {
    private var webSocket: WebSocket? = null

    fun connect() {
        val baseUrl = BuildConfig.BASE_URL.trimEnd('/')
        val wsScheme = if (baseUrl.startsWith("https")) "wss" else "ws"
        val wsHost = baseUrl.removePrefix("https://").removePrefix("http://")
        val wsUrl = "$wsScheme://$wsHost/ws/chat/$conversationId?token=$token"

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket 长连接，不超时
            .build()

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                android.util.Log.d("ChatWebSocket", "Connected to conversation $conversationId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val senderId = json.optLong("senderId", 0)
                    val senderName = json.optString("senderName", "")
                    val content = json.optString("content", "")
                    val sentAt = json.optString("sentAt", "")
                    if (senderId > 0 && content.isNotBlank()) {
                        onMessageReceived(conversationId, senderId, senderName, content, sentAt)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatWebSocket", "Failed to parse message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.e("ChatWebSocket", "Connection failed", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.d("ChatWebSocket", "Connection closed: $reason")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Activity closed")
        webSocket = null
    }
}
