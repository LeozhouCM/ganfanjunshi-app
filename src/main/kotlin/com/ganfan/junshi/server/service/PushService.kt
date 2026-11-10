package com.ganfan.junshi.server.service

import com.ganfan.junshi.server.database.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PushService {
    private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    data class Notification(
        val id: Long,
        val userId: Long,
        val type: String,       // "match_apply" | "match_approved" | "match_rejected" | "match_closed" | "new_follower" | "review_like" | "chat_message"
        val title: String,
        val content: String,
        val relatedId: Long?,   // 关联对象 ID（帖子/点评/会话）
        val createdAt: String,
        val read: Boolean
    )

    // 临时使用内存通知列表（正式环境应建 notifications 表）
    private val notifications = mutableListOf<Notification>()
    private var idCounter = 0L

    /** 发送推送通知 */
    fun push(userId: Long, type: String, title: String, content: String, relatedId: Long? = null): Notification {
        val notif = Notification(
            id = ++idCounter,
            userId = userId,
            type = type,
            title = title,
            content = content,
            relatedId = relatedId,
            createdAt = LocalDateTime.now().format(dtf),
            read = false
        )
        notifications.add(notif)
        return notif
    }

    /** 获取用户通知列表 */
    fun getNotifications(userId: Long, page: Int = 0, size: Int = 20): List<Notification> {
        return notifications
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
            .drop(page * size)
            .take(size)
    }

    /** 未读数 */
    fun getUnreadCount(userId: Long): Int {
        return notifications.count { it.userId == userId && !it.read }
    }

    /** 标记已读 */
    fun markRead(userId: Long, notificationId: Long? = null): Int {
        val targets = if (notificationId != null) {
            notifications.filter { it.id == notificationId && it.userId == userId }
        } else {
            notifications.filter { it.userId == userId && !it.read }
        }
        targets.forEach { it.copy(read = true).let { n -> notifications.replaceAll { if (it.id == n.id) n else it } } }
        return targets.size
    }

    /** 清除所有通知（仅供测试使用） */
    fun clearAll() {
        notifications.clear()
        idCounter = 0
    }
}
