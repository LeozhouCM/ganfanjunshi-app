package com.ganfan.junshi.ui.match

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ganfan.junshi.Constants
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.entity.BuddyEntity
import com.ganfan.junshi.data.local.entity.MatchPostEntity
import com.ganfan.junshi.databinding.ActivityMatchDetailBinding
import com.ganfan.junshi.ui.adapter.MatchApplicationAdapter
import com.ganfan.junshi.ui.chat.ChatActivity
import com.ganfan.junshi.ui.profile.UserProfileActivity
import com.ganfan.junshi.ui.restaurant.RestaurantDetailActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class MatchDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchDetailBinding
    private lateinit var repository: GanfanRepository
    private lateinit var appAdapter: MatchApplicationAdapter
    private var postId: Long = -1
    private var currentPost: MatchPostEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.gjToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.gjToolbar.applyStatusBarTopPadding()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = GanfanRepository.getInstance(this)
        postId = intent.getLongExtra(Constants.EXTRA_MATCH_POST_ID, -1)

        appAdapter = MatchApplicationAdapter(
            isPublisher = false,
            onApprove = { app ->
                lifecycleScope.launch {
                    repository.approveApplication(postId, app.id).onSuccess {
                        Toast.makeText(this@MatchDetailActivity, "已同意入桌", Toast.LENGTH_SHORT).show()
                        loadData()
                    }.onFailure { Toast.makeText(this@MatchDetailActivity, it.message, Toast.LENGTH_SHORT).show() }
                }
            },
            onReject = { app ->
                lifecycleScope.launch {
                    repository.rejectApplication(postId, app.id).onSuccess {
                        Toast.makeText(this@MatchDetailActivity, "已拒绝", Toast.LENGTH_SHORT).show()
                        loadData()
                    }
                }
            }
        )
        binding.gjAppList.layoutManager = LinearLayoutManager(this)
        binding.gjAppList.adapter = appAdapter

        binding.gjToolbar.setNavigationOnClickListener { finish() }
        binding.gjBtnApply.setOnClickListener { applyToMatch() }
        binding.gjBtnConfirmMeal.setOnClickListener { confirmMeal() }
        binding.gjBtnShareBuddies.setOnClickListener { shareToBuddies() }

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val post = repository.getMatchPost(postId) ?: run {
                Toast.makeText(this@MatchDetailActivity, "饭桌不存在", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            currentPost = post
            bindPost(post)

            val isPublisher = post.publisherId == repository.session.userId

            // 发帖人先从服务器拉取申请列表，同步到本地 Room DB
            if (isPublisher) {
                repository.syncApplicationsFromServer(postId)
            }
            // 然后再从本地 DB 读取（此时已包含服务器同步的数据）
            val apps = repository.getApplicationsForPost(postId)

            appAdapter = MatchApplicationAdapter(
                isPublisher = isPublisher,
                onApprove = { app ->
                    lifecycleScope.launch {
                        repository.approveApplication(postId, app.id).onSuccess {
                            Toast.makeText(this@MatchDetailActivity, "已同意", Toast.LENGTH_SHORT).show()
                            loadData()
                        }.onFailure { e -> Toast.makeText(this@MatchDetailActivity, e.message, Toast.LENGTH_SHORT).show() }
                    }
                },
                onReject = { app ->
                    lifecycleScope.launch {
                        repository.rejectApplication(postId, app.id).onSuccess { loadData() }
                    }
                }
            )
            binding.gjAppList.adapter = appAdapter
            appAdapter.submitList(apps)
            binding.gjNoApps.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE

            val me = repository.session.userId
            val myApproved = apps.any { it.applicantId == me && it.status == "approved" }
            val isMember = post.publisherId == me || myApproved

            binding.gjBtnApply.visibility = when {
                post.publisherId == me -> View.GONE
                post.status != "open" && post.status != "full" -> View.GONE
                apps.any { it.applicantId == me && it.status in listOf("pending", "approved") } -> View.GONE
                else -> View.VISIBLE
            }

            binding.gjBtnConfirmMeal.visibility = if (isMember && post.status in listOf("open", "full")) View.VISIBLE else View.GONE
            val confirmed = repository.hasConfirmedMeal(postId)
            binding.gjBtnConfirmMeal.isEnabled = !confirmed
            binding.gjBtnConfirmMeal.text = if (confirmed) "已确认赴约" else "确认赴约"

            binding.gjBtnShareBuddies.visibility =
                if (post.publisherId == me && repository.getBuddies().isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private suspend fun bindPost(post: MatchPostEntity) {
        binding.gjTitle.text = post.title
        binding.gjTime.text = "🕐 ${post.mealTime}"
        binding.gjPeople.text = "👥 ${post.currentPeople}/${post.maxPeople}"
        binding.gjBudget.text = "💰 ¥${post.budget}"
        binding.gjStatus.text = when (post.status) {
            "open" -> "招募中"
            "full" -> "已满员"
            "completed" -> "已完成"
            else -> post.status
        }

        binding.gjPublisherName.text = post.publisherName
        val publisher = repository.getUserById(post.publisherId)
        binding.gjPublisherBio.text = publisher?.bio ?: "点击查看主页"

        val me = repository.session.userId
        binding.gjUserCard.setOnClickListener {
            if (post.publisherId != me) {
                startActivity(Intent(this@MatchDetailActivity, UserProfileActivity::class.java).apply {
                    putExtra(Constants.EXTRA_USER_ID, post.publisherId)
                    putExtra(Constants.EXTRA_USER_NAME, post.publisherName)
                })
            }
        }

        val restaurant = post.restaurantId?.let { repository.getRestaurant(it) }
        binding.gjRestaurantName.text = restaurant?.name ?: post.restaurantName ?: "地点待定"
        binding.gjRestaurantMeta.text = if (restaurant != null) {
            "★ ${restaurant.rating}  ·  ${restaurant.cuisine}  ·  ¥${restaurant.avgPrice}/人"
        } else {
            "点击查看餐厅"
        }
        binding.gjRestaurantAddress.text = restaurant?.address ?: ""

        binding.gjRestaurantCard.setOnClickListener {
            post.restaurantId?.let { rid ->
                startActivity(Intent(this@MatchDetailActivity, RestaurantDetailActivity::class.java).apply {
                    putExtra("restaurant_id", rid)
                })
            }
        }
    }

    private fun applyToMatch() {
        lifecycleScope.launch {
            repository.applyToMatch(postId).onSuccess {
                Toast.makeText(this@MatchDetailActivity, "申请已发送，等待同意", Toast.LENGTH_SHORT).show()
                loadData()
            }.onFailure { e ->
                Toast.makeText(this@MatchDetailActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmMeal() {
        lifecycleScope.launch {
            repository.confirmMeal(postId).onSuccess { becameBuddy ->
                if (becameBuddy) {
                    Toast.makeText(this@MatchDetailActivity, "已成为密友 🔥", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MatchDetailActivity, "已确认，等待对方确认", Toast.LENGTH_SHORT).show()
                }
                loadData()
            }.onFailure { e ->
                Toast.makeText(this@MatchDetailActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareToBuddies() {
        lifecycleScope.launch {
            val buddies = repository.getBuddies()
            if (buddies.isEmpty()) {
                Toast.makeText(this@MatchDetailActivity, "暂无密友可分享", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = buddies.map { it.partnerName }.toTypedArray()
            val checked = BooleanArray(buddies.size)
            AlertDialog.Builder(this@MatchDetailActivity)
                .setTitle("分享给密友")
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton("发送") { _, _ ->
                    val ids = buddies.filterIndexed { i, _ -> checked[i] }.map(BuddyEntity::partnerId)
                    if (ids.isEmpty()) return@setPositiveButton
                    lifecycleScope.launch {
                        repository.shareMatchToBuddies(postId, ids).onSuccess {
                            Toast.makeText(this@MatchDetailActivity, "已分享给密友", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    fun openChatIfApproved(partnerId: Long, partnerName: String) {
        lifecycleScope.launch {
            val conv = repository.getOrCreateMatchConversation(postId, partnerId, partnerName)
            if (conv != null) {
                startActivity(Intent(this@MatchDetailActivity, ChatActivity::class.java).apply {
                    putExtra(Constants.EXTRA_CONVERSATION_ID, conv.id)
                    putExtra(Constants.EXTRA_PARTNER_NAME, partnerName)
                })
            } else {
                Toast.makeText(this@MatchDetailActivity, "需先约饭成功才能聊天", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
