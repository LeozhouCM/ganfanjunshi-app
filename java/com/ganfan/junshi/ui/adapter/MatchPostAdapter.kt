package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.R
import com.ganfan.junshi.data.local.entity.MatchPostEntity
import com.ganfan.junshi.data.local.entity.UserEntity
import com.ganfan.junshi.databinding.ItemMatchPostBinding

class MatchPostAdapter(
    private val onItemClick: (MatchPostEntity) -> Unit
) : ListAdapter<MatchPostEntity, MatchPostAdapter.ViewHolder>(DIFF) {

    private var userMap: Map<Long, UserEntity> = emptyMap()

    fun setUserMap(users: Map<Long, UserEntity>) {
        userMap = users
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMatchPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMatchPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MatchPostEntity) {
            val user = userMap[item.publisherId]
            val handle = user?.userId2?.let { "@$it" }
                ?: "@${item.publisherName.take(4)}"
            val bio = user?.bio?.takeIf { it.isNotBlank() }
                ?: "热爱探店拼桌的饭友"

            binding.gjPublisher.text = item.publisherName
            binding.gjMeta.text = "$handle · ${item.mealTime}"
            binding.gjBio.text = "\"$bio\""
            binding.gjTitle.text = "\"${item.title}\""
            binding.gjRestaurant.text = item.restaurantName ?: "地点待定"
            binding.gjTime.text = item.mealTime

            val remaining = (item.maxPeople - item.currentPeople).coerceAtLeast(0)
            binding.gjExpect.text = if (remaining > 0) {
                binding.root.context.getString(R.string.gj_match_expect_format, remaining)
            } else {
                binding.root.context.getString(R.string.gj_match_budget_format, item.budget)
            }

            when (item.status) {
                "full" -> {
                    binding.gjStatus.text = binding.root.context.getString(R.string.gj_match_status_full)
                    binding.gjStatus.setBackgroundResource(R.drawable.bg_match_status_full)
                    binding.gjStatus.setTextColor(
                        ContextCompat.getColor(binding.root.context, R.color.gj_text_secondary)
                    )
                }
                else -> {
                    binding.gjStatus.text = binding.root.context.getString(R.string.gj_match_status_recruiting)
                    binding.gjStatus.setBackgroundResource(R.drawable.bg_match_status_recruiting)
                    binding.gjStatus.setTextColor(
                        ContextCompat.getColor(binding.root.context, R.color.gj_match_status_recruiting_text)
                    )
                }
            }

            binding.gjBio.visibility = View.VISIBLE
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MatchPostEntity>() {
            override fun areItemsTheSame(old: MatchPostEntity, new: MatchPostEntity) = old.id == new.id
            override fun areContentsTheSame(old: MatchPostEntity, new: MatchPostEntity) = old == new
        }
    }
}
