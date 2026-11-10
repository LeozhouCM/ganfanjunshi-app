package com.ganfan.junshi.server.service

import com.ganfan.junshi.server.database.tables.*
import com.ganfan.junshi.server.model.MatchPostDto
import com.ganfan.junshi.server.model.ReviewDto
import com.ganfan.junshi.server.plugins.dtf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object SocialFeedService {

    data class FeedItem(
        val type: String,          // "match" | "review"
        val match: MatchPostDto? = null,
        val review: ReviewDto? = null,
        val createdAt: String
    )

    /** 首页 Feed 流：关注用户的动态 + 热门拼桌 + 精选点评 */
    fun getFeed(userId: Long, page: Int = 0, size: Int = 20): List<FeedItem> {
        return transaction {
            val feedItems = mutableListOf<FeedItem>()

            // 1. 关注用户的拼桌帖
            val followingIds = UserRelations.selectAll()
                .where { UserRelations.followerId eq userId }
                .map { it[UserRelations.followeeId] }
            val allVisibleIds = followingIds + userId  // 含自己的帖

            val matchItems = (MatchPosts innerJoin Users innerJoin Restaurants)
                .selectAll()
                .where {
                    (MatchPosts.publisherId inList allVisibleIds) and
                    (MatchPosts.status eq "recruiting")
                }
                .orderBy(MatchPosts.createdAt to SortOrder.DESC)
                .limit(size).offset(page * size.toLong())
                .map { row ->
                    FeedItem(
                        type = "match",
                        match = MatchPostDto(
                            row[MatchPosts.id], row[MatchPosts.publisherId], row[Users.nickname],
                            row[MatchPosts.restaurantId], row[Restaurants.name], row[MatchPosts.title],
                            row[MatchPosts.mealTime]?.format(dtf) ?: "",
                            row[MatchPosts.maxPeople], row[MatchPosts.currentPeople],
                            row[MatchPosts.budget],
                            row[MatchPosts.lat]?.toDouble(), row[MatchPosts.lng]?.toDouble(),
                            row[MatchPosts.note], row[MatchPosts.status]
                        ),
                        createdAt = row[MatchPosts.createdAt]?.format(dtf) ?: ""
                    )
                }
            feedItems.addAll(matchItems)

            // 2. 精选点评（关注+自己 + 高赞）
            val reviewItems = (Reviews innerJoin Users innerJoin Restaurants)
                .selectAll()
                .where {
                    Reviews.userId inList allVisibleIds
                }
                .orderBy(Reviews.createdAt to SortOrder.DESC)
                .limit(size).offset(page * size.toLong())
                .map { row ->
                    FeedItem(
                        type = "review",
                        review = ReviewDto(
                            row[Reviews.id], row[Reviews.userId], row[Users.nickname],
                            row[Reviews.restaurantId], row[Restaurants.name],
                            row[Reviews.rating].toDouble(), row[Reviews.content],
                            parseImages(row[Reviews.images]),
                            statusToInt(row[Reviews.status]), row[Reviews.likeCount],
                            row[Reviews.createdAt]?.format(dtf) ?: "",
                            row[Reviews.updatedAt]?.format(dtf)
                        ),
                        createdAt = row[Reviews.createdAt]?.format(dtf) ?: ""
                    )
                }
            feedItems.addAll(reviewItems)

            // 时间倒序，截取
            feedItems.sortedByDescending { it.createdAt }.take(size)
        }
    }

    private fun statusToInt(status: String): Int = when (status) {
        "published" -> 1
        "pending" -> 0
        "reported" -> 2
        "deleted" -> 3
        else -> 0
    }

    private fun parseImages(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            if (json.startsWith("[")) {
                Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
            } else json.split(",").filter { it.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }
}
