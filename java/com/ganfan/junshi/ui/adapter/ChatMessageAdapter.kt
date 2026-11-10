package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.R
import com.ganfan.junshi.data.local.entity.ChatMessageEntity

class ChatMessageAdapter(
    private val currentUserId: Long
) : ListAdapter<ChatMessageEntity, ChatMessageAdapter.ChatViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_bubble, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowOther: View = itemView.findViewById(R.id.gj_row_other)
        private val rowMine: View = itemView.findViewById(R.id.gj_row_mine)
        private val bubbleOther: TextView = itemView.findViewById(R.id.gj_bubble_other)
        private val bubbleMine: TextView = itemView.findViewById(R.id.gj_bubble_mine)

        fun bind(message: ChatMessageEntity) {
            val isMine = message.senderId == currentUserId
            if (isMine) {
                rowMine.visibility = View.VISIBLE
                rowOther.visibility = View.GONE
                bubbleMine.text = message.content
            } else {
                rowOther.visibility = View.VISIBLE
                rowMine.visibility = View.GONE
                bubbleOther.text = message.content
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ChatMessageEntity>() {
            override fun areItemsTheSame(oldItem: ChatMessageEntity, newItem: ChatMessageEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatMessageEntity, newItem: ChatMessageEntity) =
                oldItem == newItem
        }
    }
}
