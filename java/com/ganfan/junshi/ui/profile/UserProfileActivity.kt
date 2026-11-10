package com.ganfan.junshi.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ganfan.junshi.Constants
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.databinding.ActivityUserProfileBinding
import com.ganfan.junshi.ui.chat.ChatActivity
import com.ganfan.junshi.ui.match.PublishMatchActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class UserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileBinding
    private lateinit var repository: GanfanRepository
    private var targetUserId: Long = 0
    private var targetUserName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.gjToolbar.applyStatusBarTopPadding()
        repository = GanfanRepository.getInstance(this)

        targetUserId = intent.getLongExtra(Constants.EXTRA_USER_ID, -1)
        targetUserName = intent.getStringExtra(Constants.EXTRA_USER_NAME) ?: ""
        val isOwnProfile = targetUserId == repository.session.userId || targetUserId <= 0

        setSupportActionBar(binding.gjToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        if (isOwnProfile) {
            binding.gjToolbar.navigationIcon = null
        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            binding.gjToolbar.setNavigationOnClickListener { finish() }
        }

        binding.gjBtnFollow.setOnClickListener { toggleFollow() }
        binding.gjBtnChat.setOnClickListener { openChat() }
        binding.gjBtnInviteMatch.setOnClickListener { inviteMatch() }

        if (isOwnProfile || targetUserId <= 0) {
            loadOwnProfile()
        } else {
            loadOtherProfile()
        }
    }

    private fun loadOwnProfile() {
        lifecycleScope.launch {
            repository.getCurrentUser()?.let { displayUser(it, true) }
        }
    }

    private fun loadOtherProfile() {
        lifecycleScope.launch {
            val user = repository.getUserById(targetUserId)
            if (user != null) {
                displayUser(user, false)
                loadRelationStatus()
            } else {
                Toast.makeText(this@UserProfileActivity, "用户不存在", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private suspend fun loadRelationStatus() {
        val isFollowing = repository.isFollowing(targetUserId)
        val isMutual = repository.isMutualFollow(targetUserId)
        val canFollow = repository.canFollow(targetUserId)
        val isBuddy = repository.isBuddy(targetUserId)
        val spark = repository.getSparkLevel(targetUserId)
        val canChat = repository.canChatWith(targetUserId)

        updateFollowButton(isFollowing, isMutual, canFollow)
        binding.gjBtnChat.visibility = if (canChat) View.VISIBLE else View.GONE
        binding.gjBtnInviteMatch.visibility = if (isBuddy) View.VISIBLE else View.GONE

        if (isBuddy && spark > 0) {
            binding.gjTvBuddyBadge.visibility = View.VISIBLE
            binding.gjTvBuddyBadge.text = "密友 🔥$spark"
        } else {
            binding.gjTvBuddyBadge.visibility = View.GONE
        }

        val followerCount = repository.getFollowerCount(targetUserId)
        val followingCount = repository.getFollowingCount(targetUserId)
        binding.gjTvFollowerCount.text = "$followerCount 粉丝"
        binding.gjTvFollowingCount.text = "$followingCount 关注"
    }

    private fun updateFollowButton(isFollowing: Boolean, isMutual: Boolean, canFollow: Boolean) {
        binding.gjBtnFollow.visibility = if (canFollow) View.VISIBLE else View.GONE
        if (!canFollow) return
        binding.gjBtnFollow.text = when {
            isMutual -> "已互关"
            isFollowing -> "已关注"
            else -> "关注"
        }
    }

    private fun displayUser(user: com.ganfan.junshi.data.local.entity.UserEntity, isMe: Boolean) {
        binding.gjTvNickname.text = user.nickname
        binding.gjTvUserId2.text = if (!user.userId2.isNullOrBlank()) "ID: ${user.userId2}" else "ID: 未设置"
        binding.gjTvBio.text = user.bio ?: "这个人很懒，什么都没写"
        binding.gjTvCity.text = user.city ?: "未知城市"

        if (isMe) {
            binding.gjBtnFollow.visibility = View.GONE
            binding.gjBtnChat.visibility = View.GONE
            binding.gjBtnInviteMatch.visibility = View.GONE
            binding.gjTvBuddyBadge.visibility = View.GONE
            binding.gjTvTitle.text = "我的主页"
            lifecycleScope.launch {
                val fc = repository.getFollowerCount(repository.session.userId)
                val fgc = repository.getFollowingCount(repository.session.userId)
                binding.gjTvFollowerCount.text = "$fc 粉丝"
                binding.gjTvFollowingCount.text = "$fgc 关注"
            }
            binding.gjBtnEditProfile.visibility = View.VISIBLE
            binding.gjBtnEditProfile.setOnClickListener {
                startActivity(Intent(this, ProfileEditActivity::class.java))
            }
        } else {
            binding.gjBtnEditProfile.visibility = View.GONE
            binding.gjTvTitle.text = targetUserName
        }
    }

    private fun toggleFollow() {
        lifecycleScope.launch {
            val result = repository.followUser(targetUserId)
            if (result.isSuccess) {
                loadRelationStatus()
            } else {
                Toast.makeText(this@UserProfileActivity, result.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openChat() {
        lifecycleScope.launch {
            val conv = repository.getOrCreateConversation(targetUserId, targetUserName)
            if (conv != null) {
                startActivity(Intent(this@UserProfileActivity, ChatActivity::class.java).apply {
                    putExtra(Constants.EXTRA_CONVERSATION_ID, conv.id)
                    putExtra(Constants.EXTRA_PARTNER_NAME, targetUserName)
                })
            } else {
                Toast.makeText(this@UserProfileActivity, "需先约饭成功才能发消息", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun inviteMatch() {
        startActivity(Intent(this, PublishMatchActivity::class.java).apply {
            putExtra(Constants.EXTRA_USER_ID, targetUserId)
            putExtra(Constants.EXTRA_USER_NAME, targetUserName)
        })
    }
}
