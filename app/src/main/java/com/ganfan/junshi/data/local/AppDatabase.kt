package com.ganfan.junshi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ganfan.junshi.data.local.dao.*
import com.ganfan.junshi.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        RestaurantEntity::class,
        FavoriteEntity::class,
        UserTasteEntity::class,
        MatchPostEntity::class,
        ChatMessageEntity::class,
        ReviewEntity::class,
        ReviewDraftEntity::class,
        FollowEntity::class,
        ReviewLikeEntity::class,
        ConversationEntity::class,
        NotificationEntity::class,
        MatchApplicationEntity::class,
        BuddyEntity::class,
        MealConfirmationEntity::class,
        BuddyMatchInviteEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun restaurantDao(): RestaurantDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun userTasteDao(): UserTasteDao
    abstract fun matchPostDao(): MatchPostDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun reviewDao(): ReviewDao
    abstract fun reviewDraftDao(): ReviewDraftDao
    abstract fun followDao(): FollowDao
    abstract fun reviewLikeDao(): ReviewLikeDao
    abstract fun conversationDao(): ConversationDao
    abstract fun notificationDao(): NotificationDao
    abstract fun matchApplicationDao(): MatchApplicationDao
    abstract fun buddyDao(): BuddyDao
    abstract fun mealConfirmationDao(): MealConfirmationDao
    abstract fun buddyMatchInviteDao(): BuddyMatchInviteDao
    abstract fun aiConversationDao(): AiConversationDao
    abstract fun aiMessageDao(): AiMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ganfan_junshi.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
