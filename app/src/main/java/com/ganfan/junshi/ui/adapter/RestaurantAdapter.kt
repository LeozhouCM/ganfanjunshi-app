package com.ganfan.junshi.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.R
import com.ganfan.junshi.data.local.entity.RestaurantEntity
import com.ganfan.junshi.data.model.RestaurantMatchStats
import com.ganfan.junshi.databinding.ItemRestaurantBinding
import java.util.Calendar
import kotlin.math.roundToInt

class RestaurantAdapter(
    private val onItemClick: (RestaurantEntity) -> Unit,
    private val onFavoriteClick: (RestaurantEntity) -> Unit
) : ListAdapter<RestaurantEntity, RestaurantAdapter.ViewHolder>(DIFF) {

    private var matchStats: Map<Long, RestaurantMatchStats> = emptyMap()
    private var favoriteIds: Set<Long> = emptySet()

    fun updateMatchStats(stats: Map<Long, RestaurantMatchStats>) {
        matchStats = stats
        notifyDataSetChanged()
    }

    fun updateFavoriteIds(ids: Set<Long>) {
        favoriteIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRestaurantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, matchStats[item.id], favoriteIds.contains(item.id))
    }

    inner class ViewHolder(private val binding: ItemRestaurantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RestaurantEntity, stats: RestaurantMatchStats?, isFavorite: Boolean) {
            val context = binding.root.context

            binding.gjName.text = item.name
            binding.gjRating.text = if (item.rating > 0) {
                String.format("%.1f", item.rating)
            } else {
                "暂无"
            }
            binding.gjPrice.text = if (item.avgPrice > 0) {
                context.getString(R.string.gj_restaurant_price_format, item.avgPrice)
            } else {
                context.getString(R.string.gj_restaurant_price_pending)
            }
            binding.gjCuisine.text = item.cuisine.ifBlank { "美食" }
            binding.gjDistance.text = formatDistance(context, item.distance)

            val openNow = isRestaurantOpenNow()
            binding.gjOpenStatus.text = context.getString(
                if (openNow) R.string.gj_restaurant_open else R.string.gj_restaurant_closed
            )
            binding.gjOpenStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (openNow) R.color.gj_nav_active else R.color.gj_text_secondary
                )
            )
            binding.gjHours.text = context.getString(R.string.gj_restaurant_hours_default)

            binding.gjFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_star_favorite_yellow else R.drawable.ic_star_outline_gray
            )

            val postCount = stats?.openPostCount ?: 0
            val seekingPeople = stats?.totalSeekingPeople ?: 0
            if (postCount > 0) {
                binding.gjSocialRow.visibility = View.VISIBLE
                binding.gjMatchInfo.text = context.getString(
                    R.string.gj_restaurant_match_info_format,
                    postCount,
                    seekingPeople.coerceAtLeast(postCount)
                )
            } else {
                binding.gjSocialRow.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.gjFavorite.setOnClickListener { onFavoriteClick(item) }
        }

        private fun formatDistance(context: android.content.Context, distance: Double?): String {
            if (distance == null) return ""
            val text = when {
                distance >= 1000 -> "${(distance / 1000.0).let { if (it >= 10) it.roundToInt() else String.format("%.1f", it) }}km"
                else -> "${distance.toInt()}m"
            }
            return context.getString(R.string.gj_restaurant_distance_format, text)
        }

        private fun isRestaurantOpenNow(): Boolean {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return hour in 10..21
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RestaurantEntity>() {
            override fun areItemsTheSame(old: RestaurantEntity, new: RestaurantEntity) = old.id == new.id
            override fun areContentsTheSame(old: RestaurantEntity, new: RestaurantEntity) = old == new
        }
    }
}
