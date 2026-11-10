package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.R
import com.ganfan.junshi.data.local.entity.RestaurantEntity
import com.ganfan.junshi.databinding.ItemFeaturedRestaurantBinding

class FeaturedRestaurantAdapter(
    private val onItemClick: (RestaurantEntity) -> Unit
) : ListAdapter<RestaurantEntity, FeaturedRestaurantAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeaturedRestaurantBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFeaturedRestaurantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RestaurantEntity) {
            binding.gjBannerCuisine.text = item.cuisine
            binding.gjName.text = item.name
            binding.gjRating.text = "★ ${item.rating}"
            binding.gjReviewCount.text = binding.root.context.getString(
                R.string.gj_review_count_format,
                (item.rating * 66).toInt()
            )
            binding.gjPrice.text = "人均 ¥${item.avgPrice}/人"
            binding.gjAddress.text = item.address
            binding.gjTags.text = item.tags.replace(",", " · ")
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RestaurantEntity>() {
            override fun areItemsTheSame(old: RestaurantEntity, new: RestaurantEntity) = old.id == new.id
            override fun areContentsTheSame(old: RestaurantEntity, new: RestaurantEntity) = old == new
        }
    }
}
