package com.ganfan.junshi.util

import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.poisearch.PoiResultV2
import com.amap.api.services.poisearch.PoiSearchV2
import com.ganfan.junshi.data.local.entity.RestaurantEntity
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.suspendCancellableCoroutine

data class AmapLocationInfo(
    val lat: Double,
    val lng: Double,
    val city: String?
)

object AmapRestaurantSearch {

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val POI_PAGE_SIZE = 25
    const val POI_MAX_RESULTS = 50

    suspend fun getLocationInfo(context: Context): AmapLocationInfo? =
        suspendCancellableCoroutine { cont ->
            val client = AMapLocationClient(context.applicationContext)
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isOnceLocationLatest = true
                isNeedAddress = true
                httpTimeOut = 10_000
            }
            client.setLocationOption(option)
            client.setLocationListener { location: AMapLocation? ->
                client.stopLocation()
                client.onDestroy()
                if (location != null && location.errorCode == AMapLocation.LOCATION_SUCCESS) {
                    cont.resume(
                        AmapLocationInfo(
                            lat = location.latitude,
                            lng = location.longitude,
                            city = location.city?.takeIf { it.isNotBlank() }
                        )
                    )
                } else {
                    cont.resume(null)
                }
            }
            cont.invokeOnCancellation {
                client.stopLocation()
                client.onDestroy()
            }
            client.startLocation()
        }

    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? =
        getLocationInfo(context)?.let { it.lat to it.lng }

    fun buildSearchKeyword(userKeyword: String, cuisineKeyword: String?): String {
        val trimmed = userKeyword.trim()
        return when {
            trimmed.isNotEmpty() && !cuisineKeyword.isNullOrBlank() -> "$trimmed $cuisineKeyword"
            trimmed.isNotEmpty() -> trimmed
            !cuisineKeyword.isNullOrBlank() -> cuisineKeyword
            else -> ""
        }
    }

    suspend fun searchNearby(
        context: Context,
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        keyword: String = ""
    ): List<RestaurantEntity> {
        val pageOne = searchNearbyPage(context, lat, lng, radiusMeters, keyword, 1)
        if (pageOne.size < POI_PAGE_SIZE) return pageOne
        val pageTwo = searchNearbyPage(context, lat, lng, radiusMeters, keyword, 2)
        return (pageOne + pageTwo)
            .distinctBy { it.amapPoiId ?: it.id.toString() }
            .take(POI_MAX_RESULTS)
    }

    private suspend fun searchNearbyPage(
        context: Context,
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        keyword: String,
        pageNum: Int
    ): List<RestaurantEntity> = searchPage(
        context = context,
        keyword = keyword,
        pageNum = pageNum,
        refLat = lat,
        refLng = lng
    ) { search ->
        search.bound = PoiSearchV2.SearchBound(
            LatLonPoint(lat, lng),
            radiusMeters.coerceIn(1000, 50_000)
        )
    }

    suspend fun searchInCity(
        context: Context,
        city: String,
        lat: Double,
        lng: Double,
        keyword: String = ""
    ): List<RestaurantEntity> {
        val pageOne = searchInCityPage(context, city, lat, lng, keyword, 1)
        if (pageOne.size < POI_PAGE_SIZE) return pageOne
        val pageTwo = searchInCityPage(context, city, lat, lng, keyword, 2)
        return (pageOne + pageTwo)
            .distinctBy { it.amapPoiId ?: it.id.toString() }
            .take(POI_MAX_RESULTS)
    }

    private suspend fun searchInCityPage(
        context: Context,
        city: String,
        lat: Double,
        lng: Double,
        keyword: String,
        pageNum: Int
    ): List<RestaurantEntity> = searchPage(
        context = context,
        keyword = keyword,
        pageNum = pageNum,
        refLat = lat,
        refLng = lng,
        city = city
    ) { }

    /** 无城市信息时，用较大半径近似「全城」范围 */
    suspend fun searchWholeCityFallback(
        context: Context,
        lat: Double,
        lng: Double,
        keyword: String = ""
    ): List<RestaurantEntity> = searchNearby(context, lat, lng, 50_000, keyword)

    private suspend fun searchPage(
        context: Context,
        keyword: String,
        pageNum: Int,
        refLat: Double,
        refLng: Double,
        city: String = "",
        configureSearch: (PoiSearchV2) -> Unit
    ): List<RestaurantEntity> = suspendCancellableCoroutine { cont ->
        val queryKeyword = keyword.ifBlank { "美食" }
        val query = if (city.isBlank()) {
            PoiSearchV2.Query(queryKeyword, "050000", "")
        } else {
            PoiSearchV2.Query(queryKeyword, "050000", city)
        }
        query.pageSize = POI_PAGE_SIZE
        query.pageNum = pageNum
        query.setShowFields(PoiSearchV2.ShowFields(PoiSearchV2.ShowFields.BUSINESS))

        try {
            val search = PoiSearchV2(context.applicationContext, query)
            configureSearch(search)
            search.setOnPoiSearchListener(object : PoiSearchV2.OnPoiSearchListener {
                override fun onPoiSearched(result: PoiResultV2?, rCode: Int) {
                    if (cont.isCancelled) return
                    if (rCode == 1000 && result != null) {
                        val items = result.pois.orEmpty()
                        val baseIndex = (pageNum - 1) * POI_PAGE_SIZE
                        cont.resume(items.mapIndexed { index, poi ->
                            poi.toEntity(baseIndex + index, refLat, refLng)
                        })
                    } else {
                        cont.resume(emptyList())
                    }
                }

                override fun onPoiItemSearched(item: PoiItemV2?, rCode: Int) = Unit
            })
            cont.invokeOnCancellation { }
            search.searchPOIAsyn()
        } catch (_: Exception) {
            cont.resume(emptyList())
        }
    }

    private fun PoiItemV2.toEntity(index: Int, refLat: Double, refLng: Double): RestaurantEntity {
        val poiId = poiId?.takeIf { it.isNotBlank() } ?: "poi_$index"
        val stableId = poiId.hashCode().toLong().let { if (it < 0) -it else it }
            .let { if (it == 0L) index.toLong() + 1L else it }
        val point = latLonPoint
        val poiLat = point?.latitude ?: 0.0
        val poiLng = point?.longitude ?: 0.0
        val typeDesRaw = typeDes?.trim().orEmpty()
        val categories = parseCategories(typeDesRaw)
        val cuisine = categories.firstOrNull() ?: "餐饮"
        val address = buildString {
            provinceName?.takeIf { it.isNotBlank() }?.let { append(it) }
            cityName?.takeIf { it.isNotBlank() }?.let { append(it) }
            adName?.takeIf { it.isNotBlank() }?.let { append(it) }
            snippet?.takeIf { it.isNotBlank() }?.let { append(it) }
        }.ifBlank { "地址未知" }
        val distanceMeters = if (poiLat != 0.0 && poiLng != 0.0) {
            distanceMeters(refLat, refLng, poiLat, poiLng)
        } else {
            null
        }
        val avgPrice = parseAvgPrice(business?.cost)
        val rating = parseRating(business?.getmRating())

        return RestaurantEntity(
            id = stableId,
            amapPoiId = poiId,
            name = title?.takeIf { it.isNotBlank() } ?: "未知餐厅",
            lat = poiLat,
            lng = poiLng,
            address = address,
            avgPrice = avgPrice,
            cuisine = cuisine,
            rating = rating,
            tags = typeDesRaw.ifBlank { cuisine },
            distance = distanceMeters
        )
    }

    fun parseAvgPrice(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return Regex("\\d+").find(raw.replace(",", ""))?.value?.toIntOrNull() ?: 0
    }

    fun parseRating(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        raw.toDoubleOrNull()?.let { return (it * 10).roundToInt() / 10.0 }
        return Regex("\\d+\\.?\\d*").find(raw)?.value?.toDoubleOrNull()?.let {
            (it * 10).roundToInt() / 10.0
        } ?: 0.0
    }

    fun parseCategories(typeDes: String): List<String> {
        if (typeDes.isBlank()) return emptyList()
        return typeDes.split(";", "；", ",", "，")
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in GENERIC_CUISINE_LABELS }
    }

    fun summarizeCategories(restaurants: List<RestaurantEntity>): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        restaurants.forEach { restaurant ->
            val labels = buildList {
                addAll(parseCategories(restaurant.tags))
                addAll(parseCategories(restaurant.cuisine))
                restaurant.cuisine.takeIf { it.isNotBlank() }?.let { add(it) }
            }.distinct()
            labels.forEach { label ->
                counts[label] = (counts[label] ?: 0) + 1
            }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
    }

    private val GENERIC_CUISINE_LABELS = setOf(
        "餐饮服务", "餐饮", "美食", "中餐厅", "外国餐厅", "快餐厅", "休闲餐饮场所", "其他"
    )

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return EARTH_RADIUS_METERS * 2 * asin(sqrt(a))
    }
}
