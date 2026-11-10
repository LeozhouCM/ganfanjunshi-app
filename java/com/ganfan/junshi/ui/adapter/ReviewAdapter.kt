package com.ganfan.junshi.ui.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.Constants
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.entity.ReviewEntity
import com.ganfan.junshi.databinding.ItemReviewBinding
import com.ganfan.junshi.ui.profile.UserProfileActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewAdapter(
    private val scope: LifecycleCoroutineScope,
    private val repository: GanfanRepository,
    private val onLikeToggle: ((ReviewEntity) -> Unit)? = null
) : ListAdapter<ReviewAdapter.Row, ReviewAdapter.VH>(ReviewDiff()) {

    private var allReviews: List<ReviewEntity> = emptyList()
    private val expandedIds = mutableSetOf<Long>()
    private val collapsedPreviewCount = 2

    var onReplyClick: ((ReviewEntity) -> Unit)? = null

    fun submitAll(reviews: List<ReviewEntity>) {
        allReviews = reviews
        submitList(buildRows())
    }

    private fun buildRows(): List<Row> {
        val rows = mutableListOf<Row>()
        val topLevel = allReviews.filter { it.parentId == 0L }
        for (parent in topLevel) {
            rows.add(Row(parent, isReply = false))
            val replies = allReviews.filter { it.parentId == parent.id }
            if (replies.isEmpty()) continue
            val expanded = expandedIds.contains(parent.id)
            val visible = if (expanded) replies else replies.take(collapsedPreviewCount)
            visible.forEach { rows.add(Row(it, isReply = true)) }
            if (!expanded && replies.size > collapsedPreviewCount) {
                rows.add(Row(parent, isReply = false, isExpandToggle = true, hiddenCount = replies.size - collapsedPreviewCount))
            } else if (expanded && replies.size > collapsedPreviewCount) {
                rows.add(Row(parent, isReply = false, isCollapseToggle = true))
            }
        }
        return rows
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: Row) {
            if (row.isExpandToggle) {
                bindExpandToggle(row)
                return
            }
            if (row.isCollapseToggle) {
                bindCollapseToggle(row)
                return
            }
            bindReview(row.review, row.isReply)
        }

        private fun bindExpandToggle(row: Row) {
            binding.gjUserName.visibility = View.GONE
            binding.gjIvAvatar.visibility = View.GONE
            binding.gjRating.visibility = View.GONE
            binding.gjTime.visibility = View.GONE
            binding.gjContent.visibility = View.GONE
            binding.gjReplyIndicator.visibility = View.GONE
            binding.gjBtnLike.visibility = View.GONE
            binding.gjLikeCount.visibility = View.GONE
            binding.gjBtnReply.visibility = View.GONE
            binding.gjBtnExpand.visibility = View.VISIBLE
            binding.gjBtnExpand.text = "展开 ${row.hiddenCount} 条回复"
            binding.gjBtnExpand.setOnClickListener {
                expandedIds.add(row.review.id)
                submitList(buildRows())
            }
        }

        private fun bindCollapseToggle(row: Row) {
            binding.gjUserName.visibility = View.GONE
            binding.gjIvAvatar.visibility = View.GONE
            binding.gjRating.visibility = View.GONE
            binding.gjTime.visibility = View.GONE
            binding.gjContent.visibility = View.GONE
            binding.gjReplyIndicator.visibility = View.GONE
            binding.gjBtnLike.visibility = View.GONE
            binding.gjLikeCount.visibility = View.GONE
            binding.gjBtnReply.visibility = View.GONE
            binding.gjBtnExpand.visibility = View.VISIBLE
            binding.gjBtnExpand.text = "收起回复"
            binding.gjBtnExpand.setOnClickListener {
                expandedIds.remove(row.review.id)
                submitList(buildRows())
            }
        }

        private fun bindReview(review: ReviewEntity, isReply: Boolean) {
            binding.gjUserName.visibility = View.VISIBLE
            binding.gjIvAvatar.visibility = View.VISIBLE
            binding.gjRating.visibility = View.VISIBLE
            binding.gjTime.visibility = View.VISIBLE
            binding.gjContent.visibility = View.VISIBLE
            binding.gjBtnLike.visibility = View.VISIBLE
            binding.gjLikeCount.visibility = View.VISIBLE
            binding.gjBtnExpand.visibility = View.GONE

            binding.gjUserName.text = review.userName
            binding.gjContent.text = review.content
            binding.gjTime.text = formatTime(review.createdAt)
            binding.gjRating.text = "${review.rating}分"
            binding.gjLikeCount.text = "${review.likesCount} 赞"

            if (isReply) {
                binding.root.setPadding(48, binding.root.paddingTop, binding.root.paddingRight, binding.root.paddingBottom)
                binding.gjReplyIndicator.visibility = View.VISIBLE
                binding.gjBtnReply.visibility = View.GONE
            } else {
                binding.root.setPadding(12, binding.root.paddingTop, binding.root.paddingRight, binding.root.paddingBottom)
                binding.gjReplyIndicator.visibility = View.GONE
                binding.gjBtnReply.visibility = View.VISIBLE
            }

            scope.launch {
                val liked = repository.isReviewLiked(review.id)
                binding.gjBtnLike.setBackgroundResource(
                    if (liked) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
                )
            }
            binding.gjBtnLike.setOnClickListener {
                scope.launch {
                    repository.toggleReviewLike(review.id)
                    onLikeToggle?.invoke(review)
                }
            }

            binding.gjIvAvatar.setOnClickListener {
                if (review.userId == repository.session.userId) return@setOnClickListener
                binding.root.context.startActivity(Intent(binding.root.context, UserProfileActivity::class.java).apply {
                    putExtra(Constants.EXTRA_USER_ID, review.userId)
                    putExtra(Constants.EXTRA_USER_NAME, review.userName)
                })
            }

            binding.gjBtnReply.setOnClickListener { onReplyClick?.invoke(review) }
        }
    }

    data class Row(
        val review: ReviewEntity,
        val isReply: Boolean,
        val isExpandToggle: Boolean = false,
        val isCollapseToggle: Boolean = false,
        val hiddenCount: Int = 0
    )

    private class ReviewDiff : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(old: Row, new: Row) =
            old.review.id == new.review.id && old.isExpandToggle == new.isExpandToggle &&
                old.isCollapseToggle == new.isCollapseToggle

        override fun areContentsTheSame(old: Row, new: Row) = old == new
    }

    companion object {
        private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        fun formatTime(createdAt: String): String {
            return try {
                val millis = createdAt.toLong()
                dateFormat.format(Date(if (millis > 1_000_000_000_000L) millis else millis * 1000))
            } catch (e: Exception) {
                createdAt
            }
        }
    }
}
