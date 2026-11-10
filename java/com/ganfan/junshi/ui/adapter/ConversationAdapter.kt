package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.entity.ConversationEntity
import com.ganfan.junshi.databinding.ItemConversationBinding
import kotlinx.coroutines.launch

class ConversationAdapter(
    private val repository: GanfanRepository,
    private val scope: LifecycleCoroutineScope? = null,
    private val onItemClick: (ConversationEntity) -> Unit
) : ListAdapter<ConversationEntity, ConversationAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConversationEntity) {
            binding.gjTitle.text = if (item.lastMessage.isBlank()) "开始聊天吧" else ""
            binding.gjTitle.visibility = if (item.lastMessage.isBlank()) android.view.View.VISIBLE else android.view.View.GONE
            binding.gjLastMessage.text = item.lastMessage.ifBlank { "暂无消息" }
            binding.gjTime.text = item.lastTime
            binding.root.setOnClickListener { onItemClick(item) }

            scope?.launch {
                val spark = repository.getSparkLevel(item.partnerId)
                binding.gjPeerName.text = if (spark > 0) {
                    "${item.partnerName} 🔥$spark"
                } else {
                    item.partnerName
                }
            } ?: run {
                binding.gjPeerName.text = item.partnerName
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConversationEntity>() {
            override fun areItemsTheSame(old: ConversationEntity, new: ConversationEntity) = old.id == new.id
            override fun areContentsTheSame(old: ConversationEntity, new: ConversationEntity) = old == new
        }
    }
}
