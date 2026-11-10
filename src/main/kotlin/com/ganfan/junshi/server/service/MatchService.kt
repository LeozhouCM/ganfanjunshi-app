package com.ganfan.junshi.server.service

import com.ganfan.junshi.server.database.tables.*
import com.ganfan.junshi.server.model.MatchPostDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object MatchService {
    private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 状态机：recruiting → matched → completed / cancelled */
    enum class PostStatus(val value: String) {
        RECRUITING("recruiting"),
        MATCHED("matched"),
        COMPLETED("completed"),
        CANCELLED("cancelled")
    }

    /** 申请结果 */
    data class MatchResult(
        val success: Boolean,
        val message: String,
        val post: MatchPostDto? = null,
        val needNotify: Boolean = false,
        val notifyUserId: Long? = null,
        val notifyContent: String? = null
    )

    /** 提交拼桌申请 */
    fun apply(postId: Long, applicantId: Long, applyMessage: String?): MatchResult {
        return transaction {
            val post = MatchPosts.selectAll().where { MatchPosts.id eq postId }.singleOrNull()
                ?: return@transaction MatchResult(false, "拼桌帖不存在")

            if (post[MatchPosts.status] != PostStatus.RECRUITING.value) {
                return@transaction MatchResult(false, "该拼桌已结束")
            }
            if (post[MatchPosts.publisherId] == applicantId) {
                return@transaction MatchResult(false, "不能申请自己的拼桌")
            }

            // 检查是否已申请
            val existing = MatchApplications.selectAll()
                .where { (MatchApplications.postId eq postId) and (MatchApplications.applicantId eq applicantId) }
                .singleOrNull()
            if (existing != null) {
                val st = existing[MatchApplications.status]
                return@transaction when (st) {
                    "approved" -> MatchResult(false, "已加入该拼桌")
                    "pending" -> MatchResult(false, "已申请，等待通过")
                    else -> {
                        // 之前被拒绝，允许重新申请
                        MatchApplications.update({ MatchApplications.id eq existing[MatchApplications.id] }) {
                            it[MatchApplications.status] = "pending"
                            it[MatchApplications.message] = applyMessage
                            it[MatchApplications.updatedAt] = LocalDateTime.now()
                        }
                        MatchResult(true, "重新申请成功", needNotify = true,
                            notifyUserId = post[MatchPosts.publisherId],
                            notifyContent = "有人申请加入你的拼桌「${post[MatchPosts.title]}」")
                    }
                }
            }

            // 新建申请
            MatchApplications.insert {
                it[MatchApplications.postId] = postId
                it[MatchApplications.applicantId] = applicantId
                it[MatchApplications.message] = applyMessage
                it[MatchApplications.status] = "pending"
                it[MatchApplications.createdAt] = LocalDateTime.now()
            }

            MatchResult(true, "申请成功", needNotify = true,
                notifyUserId = post[MatchPosts.publisherId],
                notifyContent = "有人申请加入你的拼桌「${post[MatchPosts.title]}」")
        }
    }

    /** 通过申请 */
    fun approve(postId: Long, applicationId: Long, operatorId: Long): MatchResult {
        return transaction {
            val post = MatchPosts.selectAll().where { MatchPosts.id eq postId }.singleOrNull()
                ?: return@transaction MatchResult(false, "拼桌帖不存在")

            if (post[MatchPosts.publisherId] != operatorId) {
                return@transaction MatchResult(false, "只有发帖人可以操作")
            }
            if (post[MatchPosts.status] != PostStatus.RECRUITING.value) {
                return@transaction MatchResult(false, "该拼桌已结束")
            }

            val app = MatchApplications.selectAll()
                .where { MatchApplications.id eq applicationId }
                .singleOrNull()
                ?: return@transaction MatchResult(false, "申请不存在")

            if (app[MatchApplications.status] != "pending") {
                return@transaction MatchResult(false, "该申请已处理")
            }

            // 通过
            MatchApplications.update({ MatchApplications.id eq applicationId }) {
                it[MatchApplications.status] = "approved"
                it[MatchApplications.updatedAt] = LocalDateTime.now()
            }
            val newCount = post[MatchPosts.currentPeople] + 1
            MatchPosts.update({ MatchPosts.id eq postId }) {
                it[MatchPosts.currentPeople] = newCount
            }

            // 满员自动转 matched
            if (newCount >= post[MatchPosts.maxPeople]) {
                MatchPosts.update({ MatchPosts.id eq postId }) {
                    it[MatchPosts.status] = PostStatus.MATCHED.value
                    it[MatchPosts.updatedAt] = LocalDateTime.now()
                }
            }

            MatchResult(true, "已通过",
                needNotify = true,
                notifyUserId = app[MatchApplications.applicantId],
                notifyContent = "你已成功加入拼桌「${post[MatchPosts.title]}」")
        }
    }

    /** 拒绝申请 */
    fun reject(postId: Long, applicationId: Long, operatorId: Long): MatchResult {
        return transaction {
            val post = MatchPosts.selectAll().where { MatchPosts.id eq postId }.singleOrNull()
                ?: return@transaction MatchResult(false, "拼桌帖不存在")

            if (post[MatchPosts.publisherId] != operatorId) {
                return@transaction MatchResult(false, "只有发帖人可以操作")
            }

            val app = MatchApplications.selectAll()
                .where { MatchApplications.id eq applicationId }
                .singleOrNull()
                ?: return@transaction MatchResult(false, "申请不存在")

            MatchApplications.update({ MatchApplications.id eq applicationId }) {
                it[MatchApplications.status] = "rejected"
                it[MatchApplications.updatedAt] = LocalDateTime.now()
            }

            MatchResult(true, "已拒绝",
                notifyUserId = app[MatchApplications.applicantId],
                notifyContent = "你的拼桌申请被拒绝")
        }
    }

    /** 关闭拼桌 */
    fun close(postId: Long, operatorId: Long, reason: String = "completed"): MatchResult {
        return transaction {
            val post = MatchPosts.selectAll().where { MatchPosts.id eq postId }.singleOrNull()
                ?: return@transaction MatchResult(false, "拼桌帖不存在")
            if (post[MatchPosts.publisherId] != operatorId) {
                return@transaction MatchResult(false, "只有发帖人可以操作")
            }

            val newStatus = if (reason == "cancelled") PostStatus.CANCELLED.value else PostStatus.COMPLETED.value
            MatchPosts.update({ MatchPosts.id eq postId }) {
                it[MatchPosts.status] = newStatus
                it[MatchPosts.updatedAt] = LocalDateTime.now()
            }

            // 通知所有已通过申请者
            val applicants = MatchApplications.selectAll()
                .where { (MatchApplications.postId eq postId) and (MatchApplications.status eq "approved") }
                .map { it[MatchApplications.applicantId] }
            val notifyMsg = if (reason == "cancelled") "拼桌「${post[MatchPosts.title]}」已被取消" else "拼桌「${post[MatchPosts.title]}」已完成"

            MatchResult(true, "已${if (reason == "cancelled") "取消" else "完成"}",
                needNotify = applicants.isNotEmpty(),
                notifyUserId = applicants.firstOrNull(),
                notifyContent = notifyMsg)
        }
    }

    /** 获取拼桌帖的申请列表 */
    fun listApplications(postId: Long, operatorId: Long): List<MatchApplicationDto> {
        return transaction {
            val post = MatchPosts.selectAll().where { MatchPosts.id eq postId }.singleOrNull()
                ?: return@transaction emptyList()
            if (post[MatchPosts.publisherId] != operatorId) return@transaction emptyList()

            (MatchApplications innerJoin Users).selectAll()
                .where { MatchApplications.postId eq postId }
                .orderBy(MatchApplications.createdAt to SortOrder.DESC)
                .map { row ->
                    MatchApplicationDto(
                        id = row[MatchApplications.id],
                        postId = row[MatchApplications.postId],
                        applicantId = row[MatchApplications.applicantId],
                        applicantName = row[Users.nickname],
                        message = row[MatchApplications.message],
                        status = row[MatchApplications.status],
                        createdAt = row[MatchApplications.createdAt]?.format(dtf) ?: ""
                    )
                }
        }
    }

    data class MatchApplicationDto(
        val id: Long,
        val postId: Long,
        val applicantId: Long,
        val applicantName: String,
        val message: String?,
        val status: String,
        val createdAt: String
    )
}
