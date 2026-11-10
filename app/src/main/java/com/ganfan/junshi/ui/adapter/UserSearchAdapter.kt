package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.data.local.entity.UserEntity
import com.ganfan.junshi.databinding.ItemUserSearchBinding

class UserSearchAdapter(
    private val onClick: (UserEntity) -> Unit
) : ListAdapter<UserEntity, UserSearchAdapter.VH>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemUserSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemUserSearchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: UserEntity) {
            binding.gjTvNickname.text = user.nickname
            binding.gjTvUserId2.text = if (!user.userId2.isNullOrBlank()) "ID: ${user.userId2}" else "ID: 未设置"
            binding.root.setOnClickListener { onClick(user) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<UserEntity>() {
        override fun areItemsTheSame(old: UserEntity, new: UserEntity) = old.id == new.id
        override fun areContentsTheSame(old: UserEntity, new: UserEntity) = old == new
    }
}
