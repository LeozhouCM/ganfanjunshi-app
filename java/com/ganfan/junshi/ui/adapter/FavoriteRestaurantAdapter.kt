package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.data.local.entity.FavoriteEntity
import com.ganfan.junshi.databinding.ItemFavoriteRestaurantBinding

data class FavoriteDisplayItem(
    val restaurantId: Long,
    val restaurantName: String,
    val cuisine: String,
    val rating: Double
)

class FavoriteRestaurantAdapter(
    private val onItemClick: (FavoriteDisplayItem) -> Unit,
    private val onUnfavorite: (FavoriteDisplayItem) -> Unit
) : ListAdapter<FavoriteDisplayItem, FavoriteRestaurantAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteRestaurantBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFavoriteRestaurantBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FavoriteDisplayItem) {
            binding.gjFavName.text = item.restaurantName
            binding.gjFavCuisine.text = item.cuisine
            binding.gjFavRating.text = String.format("%.1f", item.rating)
            binding.root.setOnClickListener { onItemClick(item) }
            binding.gjUnfavBtn.setOnClickListener { onUnfavorite(item) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<FavoriteDisplayItem>() {
        override fun areItemsTheSame(old: FavoriteDisplayItem, new: FavoriteDisplayItem) =
            old.restaurantId == new.restaurantId
        override fun areContentsTheSame(old: FavoriteDisplayItem, new: FavoriteDisplayItem) =
            old == new
    }
}
