package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.data.local.entity.MatchPostEntity
import com.ganfan.junshi.databinding.ItemRestaurantMatchBinding

class RestaurantMatchAdapter(
    private val onJoinClick: (MatchPostEntity) -> Unit
) : ListAdapter<MatchPostEntity, RestaurantMatchAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRestaurantMatchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRestaurantMatchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MatchPostEntity) {
            binding.gjTitle.text = item.title
            binding.gjPublisher.text = "${item.publisherName} 正在约饭"
            binding.gjTime.text = item.mealTime
            binding.gjPeople.text = "${item.currentPeople}/${item.maxPeople} 人"
            binding.gjJoin.setOnClickListener { onJoinClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MatchPostEntity>() {
            override fun areItemsTheSame(old: MatchPostEntity, new: MatchPostEntity) = old.id == new.id
            override fun areContentsTheSame(old: MatchPostEntity, new: MatchPostEntity) = old == new
        }
    }
}
