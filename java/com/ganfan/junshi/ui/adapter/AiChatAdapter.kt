package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.R
import com.ganfan.junshi.data.local.entity.AiMessageEntity

class AiChatAdapter : ListAdapter<AiMessageEntity, AiChatAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
    }

    private object DiffCallback : DiffUtil.ItemCallback<AiMessageEntity>() {
        override fun areItemsTheSame(old: AiMessageEntity, new: AiMessageEntity) = old.id == new.id
        override fun areContentsTheSame(old: AiMessageEntity, new: AiMessageEntity) = old == new
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).role == "user") VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = getItem(position)
        val isUser = msg.role == "user"
        val context = holder.itemView.context

        holder.senderLabel.text = if (isUser) "我" else "军师"
        holder.contentText.text = msg.content

        val contentParams = holder.contentText.layoutParams as ViewGroup.MarginLayoutParams
        if (isUser) {
            contentParams.marginStart = 60
            contentParams.marginEnd = 16
            holder.contentText.setBackgroundColor(ContextCompat.getColor(context, R.color.gj_bubble_mine))
        } else {
            contentParams.marginStart = 16
            contentParams.marginEnd = 60
            holder.contentText.setBackgroundColor(ContextCompat.getColor(context, R.color.gj_bubble_other))
        }
        holder.contentText.layoutParams = contentParams
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderLabel: TextView = view.findViewById(R.id.gj_msg_sender)
        val contentText: TextView = view.findViewById(R.id.gj_msg_content)
    }
}
