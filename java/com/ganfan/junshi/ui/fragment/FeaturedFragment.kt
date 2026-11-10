package com.ganfan.junshi.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.databinding.FragmentFeaturedBinding
import com.ganfan.junshi.ui.adapter.FeaturedRestaurantAdapter
import com.ganfan.junshi.ui.main.MainActivity
import com.ganfan.junshi.ui.restaurant.RestaurantDetailActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class FeaturedFragment : Fragment(), MainActivity.Refreshable {

    private var _binding: FragmentFeaturedBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: GanfanRepository
    private lateinit var adapter: FeaturedRestaurantAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeaturedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyStatusBarTopPadding()
        repository = GanfanRepository.getInstance(requireContext())

        adapter = FeaturedRestaurantAdapter { restaurant ->
            startActivity(Intent(requireContext(), RestaurantDetailActivity::class.java).apply {
                putExtra("restaurant_id", restaurant.id)
            })
        }

        binding.gjFeaturedList.layoutManager = LinearLayoutManager(context)
        binding.gjFeaturedList.adapter = adapter
        binding.gjSwipeRefresh.setOnRefreshListener { onRefresh() }
        binding.gjSwipeRefresh.setColorSchemeResources(
            com.ganfan.junshi.R.color.gj_tabelog_accent
        )

        lifecycleScope.launch {
            repository.observeFeaturedRestaurants().collect { adapter.submitList(it) }
        }

        onRefresh()
    }

    override fun onRefresh() {
        lifecycleScope.launch {
            repository.refreshRestaurants()
            binding.gjSwipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
