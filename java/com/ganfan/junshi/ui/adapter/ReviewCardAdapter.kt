package com.ganfan.junshi.ui.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.Constants
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.entity.ReviewEntity
import com.ganfan.junshi.databinding.ItemReviewCardBinding
import com.ganfan.junshi.databinding.ViewReviewBottomBarBinding
import com.ganfan.junshi.ui.profile.UserProfileActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewCardAdapter(
    private val scope: LifecycleCoroutineScope,
    private val repository: GanfanRepository,
    private val onRequestReply: (ReviewEntity) -> Unit,
    private val onDataChanged: () -> Unit
) : ListAdapter<ReviewEntity, ReviewCardAdapter.VH>(DIFF) {

    private var allReviews: List<ReviewEntity> = emptyList()
    private val expandedContentIds = mutableSetOf<Long>()
    private val expandedReplyIds = mutableSetOf<Long>()
    private val collapsedReplyPreview = 2

    fun submitReviews(reviews: List<ReviewEntity>, topLevelOnly: List<ReviewEntity>) {
        allReviews = reviews
        submitList(topLevelOnly)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemReviewCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemReviewCardBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(review: ReviewEntity) {
            val replies = allReviews.filter { it.parentId == review.id }
            val contentExpanded = expandedContentIds.contains(review.id)
            val repliesExpanded = expandedReplyIds.contains(review.id)

            binding.gjUserName.text = review.userName
            binding.gjUserMeta.text = review.restaurantName
            binding.gjTime.text = formatTime(review.createdAt)
            binding.gjRatingBar.rating = review.rating.toFloat()
            binding.gjRatingLabel.text = ratingLabel(review.rating)
            binding.gjContent.text = review.content
            binding.gjContent.maxLines = if (contentExpanded) Int.MAX_VALUE else 3
            binding.gjExpandContent.visibility =
                if (!contentExpanded && review.content.length > 80) View.VISIBLE else View.GONE
            binding.gjExpandContent.setOnClickListener {
                expandedContentIds.add(review.id)
                bind(review)
            }

            binding.gjIvAvatar.setOnClickListener {
                if (review.userId == repository.session.userId) return@setOnClickListener
                binding.root.context.startActivity(
                    Intent(binding.root.context, UserProfileActivity::class.java).apply {
                        putExtra(Constants.EXTRA_USER_ID, review.userId)
                        putExtra(Constants.EXTRA_USER_NAME, review.userName)
                    }
                )
            }

            bindReplies(review, replies, repliesExpanded)
            bindBottomBar(binding.gjBottomBar, review, replies.size)
        }

        private fun bindReplies(review: ReviewEntity, replies: List<ReviewEntity>, expanded: Boolean) {
            val container = binding.gjRepliesContainer
            container.removeAllViews()
            if (replies.isEmpty()) {
                container.visibility = View.GONE
                binding.gjExpandReplies.visibility = View.GONE
                return
            }
            val visible = if (expanded) replies else replies.take(collapsedReplyPreview)
            container.visibility = View.VISIBLE
            val inflater = LayoutInflater.from(binding.root.context)
            for (reply in visible) {
                val row = inflater.inflate(R.layout.item_review_reply_inline, container, false) as TextView
                row.text = "${reply.userName}：${reply.content}"
                row.setPadding(0, 4, 0, 4)
                container.addView(row)
            }
            val hidden = replies.size - visible.size
            if (!expanded && hidden > 0) {
                binding.gjExpandReplies.visibility = View.VISIBLE
                binding.gjExpandReplies.text = "展开 $hidden 条回复"
                binding.gjExpandReplies.setOnClickListener {
                    expandedReplyIds.add(review.id)
                    bind(review)
                }
            } else if (expanded && replies.size > collapsedReplyPreview) {
                binding.gjExpandReplies.visibility = View.VISIBLE
                binding.gjExpandReplies.text = "收起回复"
                binding.gjExpandReplies.setOnClickListener {
                    expandedReplyIds.remove(review.id)
                    bind(review)
                }
            } else {
                binding.gjExpandReplies.visibility = View.GONE
            }
        }

        private fun bindBottomBar(bar: ViewReviewBottomBarBinding, review: ReviewEntity, replyCount: Int) {
            bar.gjCommentCount.text = if (replyCount > 0) "评论 $replyCount" else "评论"
            bar.gjFakeInput.setOnClickListener { onRequestReply(review) }
            bar.gjBtnComment.setOnClickListener { onRequestReply(review) }
            scope.launch {
                val liked = repository.isReviewLiked(review.id)
                bar.gjHelpfulIcon.text = if (liked) "👍" else "👍"
                bar.gjHelpfulLabel.text = if (liked) "已赞 ${review.likesCount}" else "有帮助 ${review.likesCount}"
            }
            bar.gjBtnHelpful.setOnClickListener {
                scope.launch {
                    repository.toggleReviewLike(review.id)
                    onDataChanged()
                }
            }
        }
    }

    private fun ratingLabel(rating: Int): String = when {
        rating >= 5 -> "超预期"
        rating >= 4 -> "满意"
        rating >= 3 -> "一般"
        else -> "待改进"
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReviewEntity>() {
            override fun areItemsTheSame(a: ReviewEntity, b: ReviewEntity) = a.id == b.id
            override fun areContentsTheSame(a: ReviewEntity, b: ReviewEntity) =
                a.content == b.content && a.likesCount == b.likesCount
        }

        private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        fun formatTime(createdAt: String): String {
            return try {
                val millis = createdAt.toLong()
                dateFormat.format(Date(if (millis > 1_000_000_000_000L) millis else millis * 1000))
            } catch (_: Exception) {
                createdAt
            }
        }
    }
}
