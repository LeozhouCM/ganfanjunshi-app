package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.data.model.MatchInviteItem
import com.ganfan.junshi.databinding.ItemMatchInviteBinding

class MatchInviteAdapter(
    private val onApprove: (MatchInviteItem) -> Unit,
    private val onReject: (MatchInviteItem) -> Unit,
    private val onAcceptBuddy: (MatchInviteItem) -> Unit,
    private val onView: (MatchInviteItem) -> Unit
) : ListAdapter<MatchInviteItem, MatchInviteAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMatchInviteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemMatchInviteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MatchInviteItem) {
            binding.gjTitle.text = item.title
            binding.gjSubtitle.text = item.subtitle
            binding.gjActions.visibility = View.VISIBLE
            binding.gjBtnPrimary.visibility = View.GONE
            binding.gjBtnSecondary.visibility = View.GONE

            when (item.type) {
                "apply_in" -> {
                    binding.gjBtnPrimary.visibility = View.VISIBLE
                    binding.gjBtnSecondary.visibility = View.VISIBLE
                    binding.gjBtnPrimary.text = "同意"
                    binding.gjBtnSecondary.text = "拒绝"
                    binding.gjBtnPrimary.setOnClickListener { onApprove(item) }
                    binding.gjBtnSecondary.setOnClickListener { onReject(item) }
                }
                "buddy_share" -> {
                    binding.gjBtnPrimary.visibility = View.VISIBLE
                    binding.gjBtnSecondary.visibility = View.VISIBLE
                    binding.gjBtnPrimary.text = "接受"
                    binding.gjBtnSecondary.text = "婉拒"
                    binding.gjBtnPrimary.setOnClickListener { onAcceptBuddy(item) }
                    binding.gjBtnSecondary.setOnClickListener { onReject(item) }
                }
                else -> {
                    binding.gjActions.visibility = View.GONE
                }
            }
            binding.root.setOnClickListener { onView(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MatchInviteItem>() {
            override fun areItemsTheSame(a: MatchInviteItem, b: MatchInviteItem) = a.id == b.id && a.type == b.type
            override fun areContentsTheSame(a: MatchInviteItem, b: MatchInviteItem) = a == b
        }
    }
}
