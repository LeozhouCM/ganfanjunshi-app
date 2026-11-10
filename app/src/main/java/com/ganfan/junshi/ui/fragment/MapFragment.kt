package com.ganfan.junshi.ui.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.entity.RestaurantEntity
import com.ganfan.junshi.data.model.RestaurantMatchStats
import com.ganfan.junshi.databinding.FragmentMapBinding
import com.ganfan.junshi.ui.adapter.RestaurantAdapter
import com.ganfan.junshi.ui.main.MainActivity
import com.ganfan.junshi.ui.restaurant.RestaurantDetailActivity
import com.ganfan.junshi.util.AmapRestaurantSearch
import com.ganfan.junshi.util.applyStatusBarTopPadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import kotlin.math.abs

class MapFragment : Fragment(), MainActivity.Refreshable {

    companion object {
        private const val TAG = "MapFragment"
        private const val MAP_VIEW_STATE_KEY = "gj_map_view_state"
        private val DEFAULT_CENTER = LatLng(39.9042, 116.4074)
    }

    private enum class PriceFilter { ALL, UNDER50, MID, OVER100 }
    private enum class MatchFilter { ALL, HAS_MATCH, NEED_PEOPLE, TWO_PLUS }
    private enum class SortMode { DISTANCE, RATING, PRICE_LOW }
    private enum class DistanceFilter(val meters: Int?, val labelRes: Int) {
        KM1(1000, R.string.gj_filter_km_1),
        KM3(3000, R.string.gj_filter_km_3),
        KM5(5000, R.string.gj_filter_km_5),
        WHOLE_CITY(null, R.string.gj_filter_whole_city)
    }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: GanfanRepository
    private lateinit var adapter: RestaurantAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var aMap: AMap? = null
    private val markerMap = mutableMapOf<Marker, RestaurantEntity>()
    private var allRestaurants = listOf<RestaurantEntity>()
    private var restaurants = listOf<RestaurantEntity>()
    private var matchStats = mapOf<Long, RestaurantMatchStats>()
    private var favoriteIds = setOf<Long>()
    private var mapSavedState: Bundle? = null
    private var mapViewCreated = false
    private var mapLoaded = false
    private var pendingMarkers: List<RestaurantEntity>? = null

    private var maxDistanceMeters: Int? = DistanceFilter.KM3.meters
    private var distanceFilter = DistanceFilter.KM3
    private var priceFilter = PriceFilter.ALL
    private var matchFilter = MatchFilter.ALL
    private var sortMode = SortMode.DISTANCE
    private var cuisineFilter: String? = null
    private var featuredOnly = false
    private var searchCenterLat = DEFAULT_CENTER.latitude
    private var searchCenterLng = DEFAULT_CENTER.longitude
    private var currentCity: String? = null
    private var locationInitialized = false
    private var nearbyLoadTriggered = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            enableMyLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { mapSavedState = it.getBundle(MAP_VIEW_STATE_KEY) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = GanfanRepository.getInstance(requireContext())
        applyStatusBarPadding()

        binding.gjMapView.onCreate(mapSavedState)
        mapViewCreated = true
        initMap()
        initBottomSheet()
        setupFilters()
        binding.gjFilterNearby.text = getString(distanceFilter.labelRes)
        refreshChipStates()
        binding.gjLocateFab.setOnClickListener { moveToMyLocation() }

        adapter = RestaurantAdapter(
            onItemClick = { restaurant ->
                expandResultsSheet()
                moveCameraTo(restaurant.lat, restaurant.lng)
                startActivity(Intent(requireContext(), RestaurantDetailActivity::class.java).apply {
                    putExtra("restaurant_id", restaurant.id)
                })
            },
            onFavoriteClick = { restaurant ->
                lifecycleScope.launch {
                    val added = repository.toggleFavorite(restaurant)
                    favoriteIds = if (added) {
                        favoriteIds + restaurant.id
                    } else {
                        favoriteIds - restaurant.id
                    }
                    adapter.updateFavoriteIds(favoriteIds)
                    Toast.makeText(
                        context,
                        if (added) "已加入种草清单" else "已取消收藏",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        adapter.updateMatchStats(matchStats)
        lifecycleScope.launch {
            favoriteIds = repository.getFavorites().map { it.restaurantId }.toSet()
            adapter.updateFavoriteIds(favoriteIds)
        }

        binding.gjRestaurantList.layoutManager = LinearLayoutManager(context)
        binding.gjRestaurantList.adapter = adapter
        binding.gjSwipeRefresh.setOnRefreshListener { onRefresh() }
        binding.gjSwipeRefresh.setColorSchemeResources(R.color.gj_primary)

        binding.gjSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadNearbyRestaurants()
                expandResultsSheet()
                true
            } else false
        }

        lifecycleScope.launch {
            repository.observeRestaurants().collect { list ->
                allRestaurants = list
                applyFilters()
            }
        }

        requestLocationPermissionIfNeeded()
        scheduleLocationFallback()

        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            binding.gjMapView.onResume()
        }
    }

    private fun applyStatusBarPadding() {
        binding.gjMapTopBar.applyStatusBarTopPadding()
    }

    private suspend fun resolveLocationContext() {
        if (currentCity.isNullOrBlank() || !locationInitialized) {
            AmapRestaurantSearch.getLocationInfo(requireContext())?.let { info ->
                searchCenterLat = info.lat
                searchCenterLng = info.lng
                currentCity = info.city ?: currentCity
                locationInitialized = true
            }
        }
    }

    private fun buildPoiKeyword(): String =
        binding.gjSearchInput.text?.toString()?.trim().orEmpty()

    private fun scheduleLocationFallback() {
        view?.postDelayed({
            if (_binding != null) {
                requestNearbyLoadOnce()
            }
        }, 2500L)
    }

    private fun requestNearbyLoadOnce() {
        if (nearbyLoadTriggered) return
        nearbyLoadTriggered = true
        if (!locationInitialized) {
            lifecycleScope.launch {
                AmapRestaurantSearch.getLocationInfo(requireContext())?.let { info ->
                    searchCenterLat = info.lat
                    searchCenterLng = info.lng
                    currentCity = info.city
                    locationInitialized = true
                    moveCameraTo(info.lat, info.lng)
                }
                loadNearbyRestaurants()
            }
        } else {
            loadNearbyRestaurants()
        }
    }

    private fun loadNearbyRestaurants() {
        if (_binding == null) return
        lifecycleScope.launch {
            binding.gjSwipeRefresh.isRefreshing = true
            resolveLocationContext()
            val poiKeyword = buildPoiKeyword()
            val wholeCity = distanceFilter == DistanceFilter.WHOLE_CITY
            val radius = distanceFilter.meters ?: 5000

            repository.refreshRestaurants(
                lat = searchCenterLat,
                lng = searchCenterLng,
                radiusMeters = radius,
                keyword = poiKeyword,
                wholeCity = wholeCity,
                city = currentCity
            ).getOrNull()?.let { list ->
                allRestaurants = list
                syncCuisineFilterAfterReload()
            }
            repository.refreshMatchPosts()
            matchStats = repository.getOpenMatchStatsByRestaurant()
            favoriteIds = repository.getFavorites().map { it.restaurantId }.toSet()
            adapter.updateFavoriteIds(favoriteIds)
            applyFilters()
            binding.gjSwipeRefresh.isRefreshing = false
            if (restaurants.isNotEmpty()) {
                showResultsSheetPeek()
            }
        }
    }

    private fun initMap() {
        aMap = binding.gjMapView.map
        if (aMap == null) {
            Log.e(TAG, "AMap 实例为 null，请检查 Key 与模拟器 ABI")
            return
        }
        aMap?.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isScaleControlsEnabled = true
            moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 14f))
            setOnMapLoadedListener {
                mapLoaded = true
                Log.i(TAG, "地图瓦片加载完成")
                pendingMarkers?.let {
                    updateMapMarkers(it)
                    pendingMarkers = null
                }
            }
            setOnMarkerClickListener { marker ->
                markerMap[marker]?.let { restaurant ->
                    expandResultsSheet()
                    val index = restaurants.indexOfFirst { it.id == restaurant.id }
                    if (index >= 0) binding.gjRestaurantList.smoothScrollToPosition(index)
                    moveCameraTo(restaurant.lat, restaurant.lng)
                }
                true
            }
        }
    }

    private fun initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.gjResultsSheet)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.isDraggable = false
        bottomSheetBehavior.skipCollapsed = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        setupSheetHeaderGestures()

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (_binding == null) return
                binding.gjSheetHint.text = when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> getString(R.string.gj_sheet_list_scroll_hint)
                    BottomSheetBehavior.STATE_COLLAPSED -> getString(R.string.gj_sheet_swipe_up_hint)
                    BottomSheetBehavior.STATE_HIDDEN -> getString(R.string.gj_sheet_swipe_hint)
                    else -> getString(R.string.gj_sheet_swipe_hint)
                }
                updateExpandFabVisibility(newState)
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        binding.gjSheetExpandFab.setOnClickListener {
            if (restaurants.isNotEmpty()) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    private fun setupSheetHeaderGestures() {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        val swipeThreshold = 72 * resources.displayMetrics.density
        var dragStartY = 0f

        binding.gjSheetHeader.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaY = event.rawY - dragStartY
                    when {
                        abs(deltaY) < touchSlop -> handleSheetHeaderTap()
                        deltaY <= -swipeThreshold -> handleSheetHeaderSwipeUp()
                        deltaY >= swipeThreshold -> handleSheetHeaderSwipeDown()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun handleSheetHeaderTap() {
        when (bottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_HIDDEN,
            BottomSheetBehavior.STATE_COLLAPSED -> expandResultsSheet()
            BottomSheetBehavior.STATE_EXPANDED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun handleSheetHeaderSwipeUp() {
        when (bottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_HIDDEN -> {
                if (restaurants.isNotEmpty()) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
            BottomSheetBehavior.STATE_COLLAPSED -> expandResultsSheet()
            BottomSheetBehavior.STATE_EXPANDED -> Unit
        }
    }

    private fun handleSheetHeaderSwipeDown() {
        when (bottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_EXPANDED ->
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            BottomSheetBehavior.STATE_COLLAPSED ->
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            BottomSheetBehavior.STATE_HIDDEN -> Unit
        }
    }

    private fun setupFilters() {
        binding.gjFilterNearby.setOnClickListener { anchor ->
            showPopup(anchor, listOf(
                FilterOption(getString(R.string.gj_filter_km_1)) {
                    distanceFilter = DistanceFilter.KM1
                    maxDistanceMeters = DistanceFilter.KM1.meters
                    binding.gjFilterNearby.text = getString(R.string.gj_filter_km_1)
                },
                FilterOption(getString(R.string.gj_filter_km_3)) {
                    distanceFilter = DistanceFilter.KM3
                    maxDistanceMeters = DistanceFilter.KM3.meters
                    binding.gjFilterNearby.text = getString(R.string.gj_filter_km_3)
                },
                FilterOption(getString(R.string.gj_filter_km_5)) {
                    distanceFilter = DistanceFilter.KM5
                    maxDistanceMeters = DistanceFilter.KM5.meters
                    binding.gjFilterNearby.text = getString(R.string.gj_filter_km_5)
                },
                FilterOption(getString(R.string.gj_filter_whole_city)) {
                    distanceFilter = DistanceFilter.WHOLE_CITY
                    maxDistanceMeters = null
                    binding.gjFilterNearby.text = getString(R.string.gj_filter_whole_city)
                }
            ), reloadSearch = true)
        }

        binding.gjFilterCategory.setOnClickListener { anchor -> showCategoryPopup(anchor) }

        binding.gjFilterSort.setOnClickListener { anchor ->
            showPopup(anchor, listOf(
                FilterOption(getString(R.string.gj_sort_distance)) {
                    sortMode = SortMode.DISTANCE
                    priceFilter = PriceFilter.ALL
                    matchFilter = MatchFilter.ALL
                    binding.gjFilterSort.text = getString(R.string.gj_sort_distance)
                },
                FilterOption(getString(R.string.gj_sort_rating)) {
                    sortMode = SortMode.RATING
                    priceFilter = PriceFilter.ALL
                    matchFilter = MatchFilter.ALL
                    binding.gjFilterSort.text = getString(R.string.gj_sort_rating)
                },
                FilterOption(getString(R.string.gj_sort_price_low)) {
                    sortMode = SortMode.PRICE_LOW
                    priceFilter = PriceFilter.ALL
                    matchFilter = MatchFilter.ALL
                    binding.gjFilterSort.text = getString(R.string.gj_sort_price_low)
                },
                FilterOption(getString(R.string.gj_filter_price_under50)) {
                    priceFilter = PriceFilter.UNDER50
                    matchFilter = MatchFilter.ALL
                    binding.gjFilterSort.text = getString(R.string.gj_filter_price_under50)
                },
                FilterOption(getString(R.string.gj_filter_price_mid)) {
                    priceFilter = PriceFilter.MID
                    matchFilter = MatchFilter.ALL
                    binding.gjFilterSort.text = getString(R.string.gj_filter_price_mid)
                },
                FilterOption(getString(R.string.gj_filter_price_over100)) {
                    priceFilter = PriceFilter.OVER100
                    matchFilter = MatchFilter.ALL
                    binding.gjFilterSort.text = getString(R.string.gj_filter_price_over100)
                },
                FilterOption(getString(R.string.gj_filter_has_match)) {
                    matchFilter = MatchFilter.HAS_MATCH
                    binding.gjFilterSort.text = getString(R.string.gj_filter_has_match)
                },
                FilterOption(getString(R.string.gj_filter_need_people)) {
                    matchFilter = MatchFilter.NEED_PEOPLE
                    binding.gjFilterSort.text = getString(R.string.gj_filter_need_people)
                },
                FilterOption(getString(R.string.gj_filter_match_two_plus)) {
                    matchFilter = MatchFilter.TWO_PLUS
                    binding.gjFilterSort.text = getString(R.string.gj_filter_match_two_plus)
                }
            ), reloadSearch = false)
        }

        binding.gjFilterReset.setOnClickListener { resetFilters() }
    }

    private data class FilterOption(val label: String, val apply: () -> Unit)

    private fun showCategoryPopup(anchor: View) {
        if (allRestaurants.isEmpty()) {
            Toast.makeText(context, getString(R.string.gj_filter_category_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val options = mutableListOf(
            FilterOption(getString(R.string.gj_filter_all)) {
                cuisineFilter = null
                featuredOnly = false
                binding.gjFilterCategory.text = getString(R.string.gj_filter_category)
            },
            FilterOption(getString(R.string.gj_category_featured)) {
                cuisineFilter = null
                featuredOnly = true
                binding.gjFilterCategory.text = getString(R.string.gj_category_featured)
            }
        )
        AmapRestaurantSearch.summarizeCategories(allRestaurants).forEach { (name, count) ->
            options.add(
                FilterOption(getString(R.string.gj_filter_category_count, name, count)) {
                    cuisineFilter = name
                    featuredOnly = false
                    binding.gjFilterCategory.text = name
                }
            )
        }
        showPopup(anchor, options, reloadSearch = false)
    }

    private fun syncCuisineFilterAfterReload() {
        val selected = cuisineFilter ?: return
        val available = AmapRestaurantSearch.summarizeCategories(allRestaurants)
            .map { it.first }
            .toSet()
        if (selected !in available) {
            cuisineFilter = null
            featuredOnly = false
            binding.gjFilterCategory.text = getString(R.string.gj_filter_category)
        }
    }

    private fun restaurantMatchesCuisine(restaurant: RestaurantEntity, cuisine: String): Boolean {
        val labels = buildSet {
            addAll(AmapRestaurantSearch.parseCategories(restaurant.tags))
            addAll(AmapRestaurantSearch.parseCategories(restaurant.cuisine))
            restaurant.cuisine.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        return labels.any { it.equals(cuisine, ignoreCase = true) }
    }

    private fun showPopup(anchor: View, options: List<FilterOption>, reloadSearch: Boolean) {
        PopupMenu(requireContext(), anchor).apply {
            options.forEachIndexed { index, option -> menu.add(0, index, index, option.label) }
            setOnMenuItemClickListener { item ->
                options.getOrNull(item.itemId)?.apply?.invoke()
                refreshChipStates()
                if (reloadSearch) loadNearbyRestaurants() else applyFilters()
                true
            }
        }.show()
    }

    private fun resetFilters() {
        distanceFilter = DistanceFilter.KM3
        maxDistanceMeters = DistanceFilter.KM3.meters
        priceFilter = PriceFilter.ALL
        matchFilter = MatchFilter.ALL
        sortMode = SortMode.DISTANCE
        cuisineFilter = null
        featuredOnly = false
        binding.gjFilterNearby.text = getString(R.string.gj_filter_km_3)
        binding.gjFilterCategory.text = getString(R.string.gj_filter_category)
        binding.gjFilterSort.text = getString(R.string.gj_filter_sort)
        binding.gjSearchInput.text?.clear()
        refreshChipStates()
        nearbyLoadTriggered = false
        requestNearbyLoadOnce()
        Toast.makeText(context, getString(R.string.gj_filter_reset), Toast.LENGTH_SHORT).show()
    }

    private fun refreshChipStates() {
        val ctx = context ?: return
        val defaultBg = ContextCompat.getColor(ctx, R.color.gj_chip_unselected_color)
        val selectedBg = ContextCompat.getColor(ctx, R.color.gj_chip_selected)
        val defaultTextColor = ContextCompat.getColor(ctx, R.color.gj_text_primary)
        val selectedTextColor = ContextCompat.getColor(ctx, R.color.gj_nav_active)

        fun applyChipStyle(chip: com.google.android.material.chip.Chip, isActive: Boolean) {
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                if (isActive) selectedBg else defaultBg
            )
            chip.setTextColor(if (isActive) selectedTextColor else defaultTextColor)
            chip.chipStrokeWidth = 0f
        }

        val isDistanceActive = distanceFilter != DistanceFilter.KM3
        val isCategoryActive = cuisineFilter != null || featuredOnly
        val isSortActive = sortMode != SortMode.DISTANCE || priceFilter != PriceFilter.ALL || matchFilter != MatchFilter.ALL

        applyChipStyle(binding.gjFilterNearby, isDistanceActive)
        applyChipStyle(binding.gjFilterCategory, isCategoryActive)
        applyChipStyle(binding.gjFilterSort, isSortActive)
    }

    private fun expandResultsSheet() {
        if (restaurants.isEmpty()) return
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun showResultsSheetPeek() {
        if (restaurants.isEmpty()) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            return
        }
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun applyFilters() {
        if (_binding == null || !::adapter.isInitialized) return

        var list = allRestaurants

        cuisineFilter?.let { cuisine ->
            list = list.filter { restaurantMatchesCuisine(it, cuisine) }
        }
        if (featuredOnly) list = list.filter { it.rating >= 4.6 }
        if (distanceFilter != DistanceFilter.WHOLE_CITY) {
            maxDistanceMeters?.let { maxDist ->
                list = list.filter { (it.distance ?: Double.MAX_VALUE) <= maxDist }
            }
        }

        list = when (priceFilter) {
            PriceFilter.UNDER50 -> list.filter { it.avgPrice <= 50 }
            PriceFilter.MID -> list.filter { it.avgPrice in 51..100 }
            PriceFilter.OVER100 -> list.filter { it.avgPrice > 100 }
            PriceFilter.ALL -> list
        }

        list = when (matchFilter) {
            MatchFilter.HAS_MATCH -> list.filter { (matchStats[it.id]?.openPostCount ?: 0) > 0 }
            MatchFilter.NEED_PEOPLE -> list.filter { (matchStats[it.id]?.totalSeekingPeople ?: 0) > 0 }
            MatchFilter.TWO_PLUS -> list.filter { (matchStats[it.id]?.maxTableSize ?: 0) >= 2 }
            MatchFilter.ALL -> list
        }

        list = when {
            sortMode == SortMode.RATING || featuredOnly -> list.sortedByDescending { it.rating }
            sortMode == SortMode.PRICE_LOW -> list.sortedBy { it.avgPrice }
            else -> list.sortedWith(compareBy({ it.distance ?: Double.MAX_VALUE }, { -it.rating }))
        }

        restaurants = list
        adapter.updateMatchStats(matchStats)
        adapter.submitList(restaurants)
        updateResultCount()
        updateMapMarkers(restaurants)
        if (restaurants.isNotEmpty()) {
            showResultsSheetPeek()
        }
    }

    private fun updateResultCount() {
        binding.gjResultCount.text = getString(R.string.gj_result_count_format, restaurants.size)
        binding.gjResultCount.visibility = if (restaurants.isEmpty()) View.GONE else View.VISIBLE
        updateExpandFabVisibility(bottomSheetBehavior.state)
    }

    private fun updateExpandFabVisibility(sheetState: Int) {
        val showFab = sheetState == BottomSheetBehavior.STATE_HIDDEN && restaurants.isNotEmpty()
        binding.gjSheetExpandFab.visibility = if (showFab) View.VISIBLE else View.GONE
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun moveToMyLocation() {
        if (!hasLocationPermission()) {
            Toast.makeText(context, getString(R.string.gj_map_location_permission), Toast.LENGTH_SHORT).show()
            requestLocationPermissionIfNeeded()
            return
        }
        lifecycleScope.launch {
            val mapLocation = aMap?.myLocation
            val target = if (mapLocation != null) {
                mapLocation.latitude to mapLocation.longitude
            } else {
                AmapRestaurantSearch.getLocationInfo(requireContext())?.let { info ->
                    currentCity = info.city ?: currentCity
                    info.lat to info.lng
                }
            }
            if (target == null) {
                if (locationInitialized) {
                    moveCameraTo(searchCenterLat, searchCenterLng)
                } else {
                    Toast.makeText(context, getString(R.string.gj_map_location_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            searchCenterLat = target.first
            searchCenterLng = target.second
            locationInitialized = true
            moveCameraTo(target.first, target.second)
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            enableMyLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun enableMyLocation() {
        aMap?.apply {
            myLocationStyle = MyLocationStyle().apply {
                myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
                interval(2000)
                showMyLocation(true)
            }
            isMyLocationEnabled = true
            setOnMyLocationChangeListener { location ->
                if (location == null) return@setOnMyLocationChangeListener
                searchCenterLat = location.latitude
                searchCenterLng = location.longitude
                if (!locationInitialized) {
                    locationInitialized = true
                    moveCameraTo(searchCenterLat, searchCenterLng)
                    requestNearbyLoadOnce()
                }
            }
        }
    }

    private fun updateMapMarkers(list: List<RestaurantEntity>) {
        if (!mapLoaded) {
            pendingMarkers = list
            return
        }
        val map = aMap ?: return
        map.clear()
        markerMap.clear()
        list.forEach { restaurant ->
            if (restaurant.lat == 0.0 && restaurant.lng == 0.0) return@forEach
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(restaurant.lat, restaurant.lng))
                    .title(restaurant.name)
                    .snippet("${restaurant.cuisine} · 人均¥${restaurant.avgPrice}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )
            if (marker != null) markerMap[marker] = restaurant
        }
    }

    private fun moveCameraTo(lat: Double, lng: Double) {
        aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f))
    }

    override fun onRefresh() {
        nearbyLoadTriggered = false
        loadNearbyRestaurants()
    }

    override fun onResume() {
        super.onResume()
        if (mapViewCreated) {
            _binding?.gjMapView?.onResume()
        }
    }

    override fun onPause() {
        if (mapViewCreated) {
            _binding?.gjMapView?.onPause()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mapViewCreated) {
            val mapState = Bundle()
            _binding?.gjMapView?.onSaveInstanceState(mapState)
            outState.putBundle(MAP_VIEW_STATE_KEY, mapState)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _binding?.gjMapView?.onLowMemory()
    }

    override fun onDestroyView() {
        if (mapViewCreated) {
            _binding?.gjMapView?.onDestroy()
            mapViewCreated = false
        }
        mapLoaded = false
        pendingMarkers = null
        aMap = null
        markerMap.clear()
        _binding = null
        super.onDestroyView()
    }
}
