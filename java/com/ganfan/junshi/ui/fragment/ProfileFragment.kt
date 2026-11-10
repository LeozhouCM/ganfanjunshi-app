package com.ganfan.junshi.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.entity.RestaurantEntity
import com.ganfan.junshi.databinding.FragmentProfileBinding
import com.ganfan.junshi.ui.adapter.FavoriteDisplayItem
import com.ganfan.junshi.ui.adapter.FavoriteRestaurantAdapter
import com.ganfan.junshi.ui.main.MainActivity
import com.ganfan.junshi.ui.profile.ProfileEditActivity
import com.ganfan.junshi.ui.profile.TasteProfileActivity
import com.ganfan.junshi.ui.restaurant.RestaurantDetailActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(), MainActivity.Refreshable {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: GanfanRepository
    private lateinit var favAdapter: FavoriteRestaurantAdapter

    private val profileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) loadProfile()
    }

    private val tasteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) loadProfile()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyStatusBarTopPadding()
        repository = GanfanRepository.getInstance(requireContext())

        setupTabs()
        setupFavRecycler()
        loadProfile()

        binding.gjEditProfileBtn.setOnClickListener {
            profileLauncher.launch(Intent(requireContext(), ProfileEditActivity::class.java))
        }

        binding.gjTasteIdBtn.setOnClickListener {
            tasteLauncher.launch(Intent(requireContext(), TasteProfileActivity::class.java))
        }

        binding.gjLogout.setOnClickListener {
            repository.session.clear()
            Toast.makeText(context, "已退出", Toast.LENGTH_SHORT).show()
            activity?.finish()
        }
    }

    private fun setupTabs() {
        binding.gjProfileTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        binding.gjFavRecycler.visibility = View.VISIBLE
                        binding.gjFootprintEmpty.visibility = View.GONE
                        lifecycleScope.launch { loadFavorites() }
                    }
                    1 -> {
                        binding.gjFavRecycler.visibility = View.GONE
                        binding.gjFavEmpty.visibility = View.GONE
                        binding.gjFootprintEmpty.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.gjProfileTabs.getTabAt(0)?.select()
    }

    private fun setupFavRecycler() {
        favAdapter = FavoriteRestaurantAdapter(
            onItemClick = { item ->
                startActivity(Intent(requireContext(), RestaurantDetailActivity::class.java).apply {
                    putExtra("restaurant_id", item.restaurantId)
                })
            },
            onUnfavorite = { item ->
                lifecycleScope.launch {
                    val restaurant = RestaurantEntity(
                        id = item.restaurantId,
                        amapPoiId = null,
                        name = item.restaurantName,
                        lat = 0.0,
                        lng = 0.0,
                        address = "",
                        avgPrice = 0,
                        cuisine = item.cuisine,
                        rating = item.rating,
                        tags = "",
                        distance = null
                    )
                    repository.toggleFavorite(restaurant)
                    Toast.makeText(context, "已取消种草", Toast.LENGTH_SHORT).show()
                    loadFavorites()
                }
            }
        )
        binding.gjFavRecycler.layoutManager = LinearLayoutManager(context)
        binding.gjFavRecycler.adapter = favAdapter
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            val user = repository.getCurrentUser()
            val nickname = user?.nickname ?: repository.session.nickname ?: "干饭人"
            binding.gjNickname.text = nickname
            binding.gjBio.text = user?.bio ?: ""
            binding.gjCity.text = user?.city ?: ""

            val fanCount = repository.getFollowerCount()
            val followCount = repository.getFollowingCount()
            binding.gjFanCount.text = fanCount.toString()
            binding.gjFollowCount.text = followCount.toString()
            binding.gjLikeCount.text = "0"

            loadFavorites()
        }
    }

    private suspend fun loadFavorites() {
        val favorites = repository.getFavorites()
        if (favorites.isEmpty()) {
            binding.gjFavEmpty.visibility = View.VISIBLE
            favAdapter.submitList(emptyList())
            return
        }
        binding.gjFavEmpty.visibility = View.GONE

        val items = favorites.mapNotNull { fav ->
            val restaurant = repository.getRestaurant(fav.restaurantId)
            if (restaurant != null) {
                FavoriteDisplayItem(
                    restaurantId = fav.restaurantId,
                    restaurantName = fav.restaurantName,
                    cuisine = restaurant.cuisine.ifBlank { "未知菜系" },
                    rating = restaurant.rating
                )
            } else {
                FavoriteDisplayItem(
                    restaurantId = fav.restaurantId,
                    restaurantName = fav.restaurantName,
                    cuisine = "",
                    rating = 0.0
                )
            }
        }
        favAdapter.submitList(items)
    }

    override fun onRefresh() = loadProfile()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
