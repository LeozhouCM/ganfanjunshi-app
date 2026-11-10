package com.ganfan.junshi.ui.restaurant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ganfan.junshi.Constants
import com.ganfan.junshi.R
import com.ganfan.junshi.component.service.LocationReportService
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.entity.RestaurantEntity
import com.ganfan.junshi.data.local.entity.ReviewEntity
import com.ganfan.junshi.databinding.ActivityRestaurantDetailBinding
import com.ganfan.junshi.databinding.LayoutReviewComposePanelBinding
import com.ganfan.junshi.ui.adapter.RestaurantMatchAdapter
import com.ganfan.junshi.ui.adapter.ReviewCardAdapter
import com.ganfan.junshi.ui.camera.CameraCheckInActivity
import com.ganfan.junshi.ui.match.MatchDetailActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class RestaurantDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRestaurantDetailBinding
    private lateinit var composePanel: LayoutReviewComposePanelBinding
    private lateinit var repository: GanfanRepository
    private lateinit var matchAdapter: RestaurantMatchAdapter
    private lateinit var reviewAdapter: ReviewCardAdapter

    private var restaurantId: Long = -1
    private var restaurantLat = 39.9
    private var restaurantLng = 116.4
    private var restaurantName = ""
    private var currentRestaurant: RestaurantEntity? = null
    private var isFavorite = false
    private var isLiked = false

    private var allReviews: List<ReviewEntity> = emptyList()
    private var showAllReviews = false
    private var replyTarget: ReviewEntity? = null
    private val previewReviewCount = 3
    private var composeRating = 5
    private lateinit var composeStars: List<ImageView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestaurantDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        composePanel = LayoutReviewComposePanelBinding.bind(binding.gjComposePanel.root)
        binding.gjToolbar.applyStatusBarTopPadding()
        setSupportActionBar(binding.gjToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = GanfanRepository.getInstance(this)
        restaurantId = intent.getLongExtra("restaurant_id", -1)

        matchAdapter = RestaurantMatchAdapter { post ->
            startActivity(Intent(this, MatchDetailActivity::class.java).apply {
                putExtra(Constants.EXTRA_MATCH_POST_ID, post.id)
            })
        }

        reviewAdapter = ReviewCardAdapter(
            scope = lifecycleScope,
            repository = repository,
            onRequestReply = { review -> openComposeForReply(review) },
            onDataChanged = { lifecycleScope.launch { refreshReviews() } }
        )

        binding.gjMatchList.layoutManager = LinearLayoutManager(this)
        binding.gjMatchList.adapter = matchAdapter
        binding.gjReviewList.layoutManager = LinearLayoutManager(this)
        binding.gjReviewList.adapter = reviewAdapter

        binding.gjCheckInButton.setOnClickListener {
            startActivity(Intent(this, CameraCheckInActivity::class.java).apply {
                putExtra("restaurant_id", restaurantId)
            })
        }
        binding.gjNavigateButton.setOnClickListener { openAmapNavigation() }
        binding.gjLikeButton.setOnClickListener { toggleLike() }
        binding.gjFavoriteButton.setOnClickListener { toggleFavorite() }
        binding.gjPublishMatch.setOnClickListener { showPublishSheet() }
        binding.gjFirstMatchButton.setOnClickListener { showPublishSheet() }
        binding.gjWriteReviewBtn.setOnClickListener { openComposeForNewReview() }

        setupPageBottomBar()
        setupComposePanel()

        onBackPressedDispatcher.addCallback(this) {
            if (composePanel.root.isVisible) {
                hideComposePanel()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.gjLoadMoreReviews.setOnClickListener {
            showAllReviews = true
            applyReviewList()
        }

        loadRestaurant()
        observeMatchPosts()
        lifecycleScope.launch { refreshReviews() }
    }

    private fun setupPageBottomBar() {
        binding.gjPageBottomBar.root.visibility = View.GONE
    }

    private fun setupComposePanel() {
        composeStars = listOf(
            composePanel.gjStar1,
            composePanel.gjStar2,
            composePanel.gjStar3,
            composePanel.gjStar4,
            composePanel.gjStar5
        )
        composeStars.forEachIndexed { index, star ->
            star.setOnClickListener { setComposeRating(index + 1, animate = true) }
        }
        setComposeRating(5, animate = false)

        composePanel.gjComposeSend.setOnClickListener { submitCompose() }
        composePanel.gjComposeCancel.setOnClickListener { hideComposePanel() }
        composePanel.gjComposeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitCompose()
                true
            } else false
        }
        composePanel.gjComposeInput.addTextChangedListener { text ->
            val content = text?.toString().orEmpty()
            if (content.isBlank()) {
                composePanel.gjComposePreview.visibility = View.GONE
            } else {
                composePanel.gjComposePreview.visibility = View.VISIBLE
                composePanel.gjComposePreview.text = "预览：$content"
            }
        }
    }

    private fun setComposeRating(rating: Int, animate: Boolean) {
        composeRating = rating.coerceIn(1, 5)
        composeStars.forEachIndexed { index, star ->
            val filled = index < composeRating
            star.setImageResource(
                if (filled) R.drawable.ic_star_favorite_yellow else R.drawable.ic_star_outline_gray
            )
            if (animate && filled) {
                star.animate()
                    .scaleX(1.25f)
                    .scaleY(1.25f)
                    .setDuration(90)
                    .withEndAction {
                        star.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    }
                    .start()
            }
        }
        composePanel.gjComposeRatingLabel.text = "${composeRating} 星"
    }

    private fun openComposeForNewReview() {
        if (restaurantId <= 0) {
            Toast.makeText(this, "餐厅信息加载中", Toast.LENGTH_SHORT).show()
            return
        }
        replyTarget = null
        composePanel.gjComposeHint.visibility = View.GONE
        composePanel.gjComposeRatingRow.visibility = View.VISIBLE
        setComposeRating(5, animate = false)
        showComposePanel()
    }

    private fun openComposeForReply(review: ReviewEntity) {
        replyTarget = review
        composePanel.gjComposeHint.visibility = View.VISIBLE
        composePanel.gjComposeHint.text = "回复 ${review.userName}"
        composePanel.gjComposeRatingRow.visibility = View.GONE
        showComposePanel()
    }

    private fun showComposePanel() {
        binding.gjPageBottomBar.root.visibility = View.GONE
        composePanel.root.visibility = View.VISIBLE
        composePanel.gjComposeInput.setText("")
        composePanel.gjComposePreview.visibility = View.GONE
        composePanel.gjComposeInput.requestFocus()
        composePanel.gjComposeInput.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(composePanel.gjComposeInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideComposePanel() {
        composePanel.root.visibility = View.GONE
        binding.gjPageBottomBar.root.visibility = View.GONE
        composePanel.gjComposeInput.clearFocus()
        composePanel.gjComposePreview.visibility = View.GONE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(composePanel.gjComposeInput.windowToken, 0)
    }

    private fun submitCompose() {
        val content = composePanel.gjComposeInput.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
            return
        }
        val target = replyTarget
        lifecycleScope.launch {
            if (target == null) {
                repository.createReview(
                    restaurantId = restaurantId,
                    restaurantName = restaurantName.ifBlank { currentRestaurant?.name ?: "" },
                    rating = composeRating,
                    content = content
                )
                Toast.makeText(this@RestaurantDetailActivity, "评价已发布", Toast.LENGTH_SHORT).show()
            } else {
                repository.createReview(
                    restaurantId = restaurantId,
                    restaurantName = restaurantName,
                    rating = 5,
                    content = content,
                    parentId = target.id
                )
                Toast.makeText(this@RestaurantDetailActivity, "回复已发送", Toast.LENGTH_SHORT).show()
            }
            hideComposePanel()
            refreshReviews()
            loadRestaurant()
        }
    }

    private fun loadRestaurant() {
        lifecycleScope.launch {
            val restaurant = repository.getRestaurant(restaurantId)
            restaurant?.let { bindRestaurant(it) }
        }
    }

    private suspend fun bindRestaurant(it: RestaurantEntity) {
        currentRestaurant = it
        restaurantLat = it.lat
        restaurantLng = it.lng
        restaurantName = it.name

        binding.gjHeroLabel.text = it.name
        binding.gjName.text = it.name
        binding.gjRating.text = "★ ${it.rating}"
        binding.gjReviewCount.text = getString(R.string.gj_review_count_format, allReviews.count { r -> r.parentId == 0L })
        binding.gjPrice.text = "¥${it.avgPrice}/人"
        binding.gjAddress.text = it.address
        binding.gjIntro.text = buildIntro(it)
        title = it.name
        supportActionBar?.title = it.name

        isFavorite = repository.isFavorite(it.id)
        isLiked = repository.isLiked(it.id)
        updateLikeFavoriteUi(it)

        val successRate = repository.getMatchSuccessRate(it.id)
        binding.gjSuccessRate.text = getString(R.string.gj_match_success_rate, successRate)

        val review = repository.getTopReview(it.id)
        if (review != null) {
            binding.gjReviewCard.visibility = View.VISIBLE
            binding.gjReviewTitle.text = getString(R.string.gj_featured_review) + " (By ${review.userName})"
            binding.gjReviewContent.text = "\"${review.content}\""
        } else {
            binding.gjReviewCard.visibility = View.GONE
        }
    }

    private suspend fun refreshReviews() {
        allReviews = repository.getRestaurantReviews(restaurantId)
        applyReviewList()
        currentRestaurant?.let { bindRestaurant(it) }
    }

    private fun applyReviewList() {
        val topLevel = allReviews.filter { it.parentId == 0L }.sortedByDescending { it.id }
        val display = if (showAllReviews) topLevel else topLevel.take(previewReviewCount)
        reviewAdapter.submitReviews(allReviews, display)
        val hasMore = topLevel.size > previewReviewCount && !showAllReviews
        binding.gjLoadMoreReviews.visibility = if (hasMore) View.VISIBLE else View.GONE
        if (hasMore) {
            binding.gjLoadMoreReviews.text = "查看更多评价（共 ${topLevel.size} 条）"
        }
    }

    private fun buildIntro(it: RestaurantEntity): String {
        val tagText = it.tags.replace(",", "、")
        return "本店主打${it.cuisine}，${tagText}。口碑评分 ${it.rating}，是附近食客常来的精选店谱之一。"
    }

    private fun updateLikeFavoriteUi(restaurant: RestaurantEntity) {
        val likeCount = repository.getDisplayLikeCount(restaurant.id, restaurant.rating)
        binding.gjLikeButton.text = if (isLiked) "👍 已赞 $likeCount" else "👍 点赞 $likeCount"
        binding.gjLikeButton.setTextColor(getColor(if (isLiked) R.color.gj_like_active else R.color.gj_text_primary))
        binding.gjFavoriteButton.text = if (isFavorite) "★ 已收藏" else "★ 收藏"
        binding.gjFavoriteButton.setTextColor(getColor(if (isFavorite) R.color.gj_star else R.color.gj_text_primary))
    }

    private fun toggleLike() {
        val restaurant = currentRestaurant ?: return
        isLiked = repository.toggleLike(restaurant.id)
        updateLikeFavoriteUi(restaurant)
        Toast.makeText(this, if (isLiked) "已点赞" else "已取消点赞", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFavorite() {
        val restaurant = currentRestaurant ?: return
        lifecycleScope.launch {
            isFavorite = repository.toggleFavorite(restaurant)
            updateLikeFavoriteUi(restaurant)
            Toast.makeText(this@RestaurantDetailActivity, if (isFavorite) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeMatchPosts() {
        lifecycleScope.launch {
            repository.observeRestaurantMatchPosts(restaurantId).collect { posts ->
                val hasPosts = posts.isNotEmpty()
                binding.gjMatchEmpty.visibility = if (hasPosts) View.GONE else View.VISIBLE
                binding.gjMatchList.visibility = if (hasPosts) View.VISIBLE else View.GONE
                matchAdapter.submitList(posts)
            }
        }
    }

    private fun showPublishSheet() {
        val restaurant = currentRestaurant ?: return
        PublishMatchBottomSheet.newInstance(restaurant.id, restaurant.lat, restaurant.lng, restaurant.avgPrice).apply {
            onPublished = { lifecycleScope.launch { repository.refreshMatchPosts() } }
        }.show(supportFragmentManager, "publish_match")
    }

    private fun openAmapNavigation() {
        val uri = Uri.parse(
            "androidamap://route?sourceApplication=ganfan_junshi" +
                "&dlat=$restaurantLat&dlon=$restaurantLng&dname=${Uri.encode(restaurantName)}&dev=0&t=0"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.autonavi.minimap") }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://uri.amap.com/navigation?to=$restaurantLng,$restaurantLat,${Uri.encode(restaurantName)}&mode=walk"
            )))
        }
        startForegroundService(Intent(this, LocationReportService::class.java))
    }

    override fun onSupportNavigateUp(): Boolean {
        if (composePanel.root.isVisible) {
            hideComposePanel()
            return true
        }
        finish()
        return true
    }
}
