package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.data.local.entity.MatchApplicationEntity
import com.ganfan.junshi.databinding.ItemMatchApplicationBinding

class MatchApplicationAdapter(
    private val isPublisher: Boolean,
    private val onApprove: (MatchApplicationEntity) -> Unit,
    private val onReject: (MatchApplicationEntity) -> Unit
) : ListAdapter<MatchApplicationEntity, MatchApplicationAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMatchApplicationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemMatchApplicationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MatchApplicationEntity) {
            binding.gjName.text = item.applicantName
            binding.gjStatus.text = when (item.status) {
                "pending" -> "待审核"
                "approved" -> "已同意"
                "rejected" -> "已拒绝"
                else -> item.status
            }
            val showActions = isPublisher && item.status == "pending"
            binding.gjBtnApprove.visibility = if (showActions) View.VISIBLE else View.GONE
            binding.gjBtnReject.visibility = if (showActions) View.VISIBLE else View.GONE
            binding.gjBtnApprove.setOnClickListener { onApprove(item) }
            binding.gjBtnReject.setOnClickListener { onReject(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MatchApplicationEntity>() {
            override fun areItemsTheSame(a: MatchApplicationEntity, b: MatchApplicationEntity) = a.id == b.id
            override fun areContentsTheSame(a: MatchApplicationEntity, b: MatchApplicationEntity) = a == b
        }
    }
}
