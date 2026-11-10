package com.ganfan.junshi.data.local.dao

import androidx.room.*
import com.ganfan.junshi.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE userId2 = :userId2 LIMIT 1")
    suspend fun getByUserId2(userId2: String): UserEntity?

    @Query("SELECT * FROM users WHERE userId2 LIKE '%' || :keyword || '%' OR nickname LIKE '%' || :keyword || '%' LIMIT 50")
    suspend fun searchUsers(keyword: String): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)
}

@Dao
interface RestaurantDao {
    @Query("SELECT * FROM restaurants ORDER BY rating DESC")
    fun observeFeatured(): Flow<List<RestaurantEntity>>

    @Query("SELECT * FROM restaurants ORDER BY distance ASC")
    fun observeAll(): Flow<List<RestaurantEntity>>

    @Query("SELECT * FROM restaurants ORDER BY distance ASC")
    suspend fun getAll(): List<RestaurantEntity>

    @Query("SELECT * FROM restaurants WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RestaurantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(restaurants: List<RestaurantEntity>)

    @Query("DELETE FROM restaurants")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: Long): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getByUser(userId: Long): List<FavoriteEntity>

    @Query("SELECT COUNT(*) FROM favorites WHERE userId = :userId AND restaurantId = :restaurantId")
    suspend fun isFavorite(userId: Long, restaurantId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE userId = :userId AND restaurantId = :restaurantId")
    suspend fun delete(userId: Long, restaurantId: Long)
}

@Dao
interface UserTasteDao {
    @Query("SELECT * FROM user_taste WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: Long): UserTasteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(taste: UserTasteEntity)

    @Update
    suspend fun update(taste: UserTasteEntity)
}

@Dao
interface MatchPostDao {
    @Query("SELECT * FROM match_posts ORDER BY id DESC")
    fun observeAll(): Flow<List<MatchPostEntity>>

    @Query("SELECT * FROM match_posts WHERE restaurantId = :restaurantId AND status = 'open' ORDER BY id DESC")
    fun observeByRestaurant(restaurantId: Long): Flow<List<MatchPostEntity>>

    @Query("SELECT * FROM match_posts WHERE restaurantId = :restaurantId")
    suspend fun getByRestaurant(restaurantId: Long): List<MatchPostEntity>

    @Query("SELECT * FROM match_posts WHERE status = 'open'")
    suspend fun getAllOpen(): List<MatchPostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<MatchPostEntity>)

    @Query("SELECT * FROM match_posts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MatchPostEntity?

    @Update
    suspend fun update(post: MatchPostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: MatchPostEntity)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeByConversation(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id DESC LIMIT 1")
    suspend fun getLatestByConversation(conversationId: Long): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages ORDER BY id DESC")
    suspend fun getAllOrderedDesc(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE parentId = 0 ORDER BY id DESC")
    fun observeAll(): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE restaurantId = :restaurantId AND parentId = 0 ORDER BY id DESC")
    fun observeByRestaurant(restaurantId: Long): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE restaurantId = :restaurantId AND parentId = 0 ORDER BY id DESC")
    suspend fun getByRestaurant(restaurantId: Long): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE restaurantId = :restaurantId ORDER BY id DESC")
    suspend fun getByRestaurantAll(restaurantId: Long): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE restaurantId = :restaurantId ORDER BY id DESC LIMIT 1")
    suspend fun getTopByRestaurant(restaurantId: Long): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE parentId = :parentId ORDER BY id ASC")
    suspend fun getReplies(parentId: Long): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(review: ReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reviews: List<ReviewEntity>)

    @Query("UPDATE reviews SET likesCount = likesCount + 1 WHERE id = :reviewId")
    suspend fun incrementLikes(reviewId: Long)

    @Query("UPDATE reviews SET likesCount = MAX(0, likesCount - 1) WHERE id = :reviewId")
    suspend fun decrementLikes(reviewId: Long)
}

@Dao
interface ReviewDraftDao {
    @Insert
    suspend fun insert(draft: ReviewDraftEntity): Long

    @Query("SELECT * FROM review_drafts ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): ReviewDraftEntity?
}

@Dao
interface FollowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun follow(follow: FollowEntity)

    @Query("DELETE FROM follows WHERE followerId = :followerId AND followingId = :followingId")
    suspend fun unfollow(followerId: Long, followingId: Long)

    @Query("SELECT COUNT(*) FROM follows WHERE followerId = :followerId AND followingId = :followingId")
    suspend fun isFollowing(followerId: Long, followingId: Long): Int

    @Query("SELECT COUNT(*) FROM follows WHERE followerId = :userId")
    suspend fun getFollowingCount(userId: Long): Int

    @Query("SELECT COUNT(*) FROM follows WHERE followingId = :userId")
    suspend fun getFollowerCount(userId: Long): Int
}

@Dao
interface ReviewLikeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(like: ReviewLikeEntity)

    @Query("DELETE FROM review_likes WHERE reviewId = :reviewId AND userId = :userId")
    suspend fun unlike(reviewId: Long, userId: Long)

    @Query("SELECT COUNT(*) FROM review_likes WHERE reviewId = :reviewId AND userId = :userId")
    suspend fun isLiked(reviewId: Long, userId: Long): Int
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY lastTime DESC")
    fun observeByUser(userId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY lastTime DESC")
    suspend fun getByUser(userId: Long): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE userId = :userId AND partnerId = :partnerId LIMIT 1")
    suspend fun getByUserAndPartner(userId: Long, partnerId: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE userId = :userId AND matchPostId > 0 ORDER BY lastTime DESC")
    fun observeMatchConversations(userId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE userId = :userId AND partnerId = :partnerId AND matchPostId = :matchPostId LIMIT 1")
    suspend fun getByUserPartnerAndMatch(userId: Long, partnerId: Long, matchPostId: Long): ConversationEntity?

    @Update
    suspend fun update(conversation: ConversationEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE toUserId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: Long): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE toUserId = :userId AND isRead = 0")
    suspend fun getUnreadCount(userId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)
}

@Dao
interface MatchApplicationDao {
    @Query("SELECT * FROM match_applications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MatchApplicationEntity>>

    @Query("SELECT * FROM match_applications WHERE postId = :postId ORDER BY createdAt DESC")
    suspend fun getByPost(postId: Long): List<MatchApplicationEntity>

    @Query("SELECT * FROM match_applications WHERE publisherId = :userId AND status = 'pending' ORDER BY createdAt DESC")
    suspend fun getPendingForPublisher(userId: Long): List<MatchApplicationEntity>

    @Query("SELECT * FROM match_applications WHERE applicantId = :userId ORDER BY createdAt DESC")
    suspend fun getByApplicant(userId: Long): List<MatchApplicationEntity>

    @Query("SELECT * FROM match_applications WHERE publisherId = :userId AND status = 'pending' ORDER BY createdAt DESC")
    fun observePendingForPublisher(userId: Long): Flow<List<MatchApplicationEntity>>

    @Query("SELECT * FROM match_applications WHERE applicantId = :userId ORDER BY createdAt DESC")
    fun observeByApplicant(userId: Long): Flow<List<MatchApplicationEntity>>

    @Query("SELECT * FROM match_applications WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MatchApplicationEntity?

    @Query("SELECT COUNT(*) FROM match_applications WHERE postId = :postId AND applicantId = :applicantId AND status IN ('pending','approved')")
    suspend fun hasActiveApplication(postId: Long, applicantId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM match_applications ma
        INNER JOIN match_posts mp ON ma.postId = mp.id
        WHERE ma.status = 'approved'
        AND ((ma.applicantId = :userA AND mp.publisherId = :userB)
          OR (ma.applicantId = :userB AND mp.publisherId = :userA))
    """)
    suspend fun hasApprovedMatchBetween(userA: Long, userB: Long): Int

    @Query("""
        SELECT ma.postId FROM match_applications ma
        INNER JOIN match_posts mp ON ma.postId = mp.id
        WHERE ma.status = 'approved'
        AND ((ma.applicantId = :userA AND mp.publisherId = :userB)
          OR (ma.applicantId = :userB AND mp.publisherId = :userA))
        ORDER BY ma.createdAt DESC LIMIT 1
    """)
    suspend fun getLatestApprovedMatchPostId(userA: Long, userB: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(application: MatchApplicationEntity)

    @Update
    suspend fun update(application: MatchApplicationEntity)
}

@Dao
interface BuddyDao {
    @Query("SELECT * FROM buddy_relations WHERE userId = :userId ORDER BY lastMealAt DESC")
    fun observeByUser(userId: Long): Flow<List<BuddyEntity>>

    @Query("SELECT * FROM buddy_relations WHERE userId = :userId ORDER BY lastMealAt DESC")
    suspend fun getByUser(userId: Long): List<BuddyEntity>

    @Query("SELECT COUNT(*) FROM buddy_relations WHERE userId = :userId AND partnerId = :partnerId")
    suspend fun isBuddy(userId: Long, partnerId: Long): Int

    @Query("SELECT sparkLevel FROM buddy_relations WHERE userId = :userId AND partnerId = :partnerId LIMIT 1")
    suspend fun getSparkLevel(userId: Long, partnerId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(buddy: BuddyEntity)

    @Update
    suspend fun update(buddy: BuddyEntity)
}

@Dao
interface MealConfirmationDao {
    @Query("SELECT COUNT(*) FROM meal_confirmations WHERE matchPostId = :matchPostId AND userId = :userId")
    suspend fun hasConfirmed(matchPostId: Long, userId: Long): Int

    @Query("SELECT COUNT(*) FROM meal_confirmations WHERE matchPostId = :matchPostId")
    suspend fun getConfirmationCount(matchPostId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(confirmation: MealConfirmationEntity)
}

@Dao
interface AiConversationDao {
    @Query("SELECT * FROM ai_conversations WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeByUser(userId: Long): Flow<List<AiConversationEntity>>

    @Query("SELECT * FROM ai_conversations WHERE userId = :userId ORDER BY updatedAt DESC")
    suspend fun getByUser(userId: Long): List<AiConversationEntity>

    @Query("SELECT * FROM ai_conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AiConversationEntity?

    @Insert
    suspend fun insert(conversation: AiConversationEntity): Long

    @Update
    suspend fun update(conversation: AiConversationEntity)

    @Query("DELETE FROM ai_conversations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AiMessageDao {
    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeByConversation(conversationId: Long): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getByConversation(conversationId: Long): List<AiMessageEntity>

    @Insert
    suspend fun insert(message: AiMessageEntity): Long

    @Insert
    suspend fun insertAll(messages: List<AiMessageEntity>)

    @Query("DELETE FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)
}

@Dao
interface BuddyMatchInviteDao {
    @Query("SELECT * FROM buddy_match_invites WHERE toUserId = :userId AND status = 'pending' ORDER BY createdAt DESC")
    suspend fun getPendingForUser(userId: Long): List<BuddyMatchInviteEntity>

    @Query("SELECT * FROM buddy_match_invites WHERE toUserId = :userId AND status = 'pending' ORDER BY createdAt DESC")
    fun observePendingForUser(userId: Long): Flow<List<BuddyMatchInviteEntity>>

    @Query("SELECT * FROM buddy_match_invites WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BuddyMatchInviteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invite: BuddyMatchInviteEntity)

    @Update
    suspend fun update(invite: BuddyMatchInviteEntity)
}
