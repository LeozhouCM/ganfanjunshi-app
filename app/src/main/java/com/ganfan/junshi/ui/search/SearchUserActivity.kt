package com.ganfan.junshi.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ganfan.junshi.Constants
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.entity.UserEntity
import com.ganfan.junshi.databinding.ActivitySearchUserBinding
import com.ganfan.junshi.ui.adapter.UserSearchAdapter
import com.ganfan.junshi.ui.profile.UserProfileActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class SearchUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchUserBinding
    private lateinit var repository: GanfanRepository
    private val adapter = UserSearchAdapter { user -> onUserClicked(user) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchUserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.gjToolbar.applyStatusBarTopPadding()
        repository = GanfanRepository.getInstance(this)

        binding.gjSearchList.layoutManager = LinearLayoutManager(this)
        binding.gjSearchList.adapter = adapter

        binding.gjBtnSearch.setOnClickListener { doSearch() }
        binding.gjToolbar.setNavigationOnClickListener { finish() }
    }

    private fun doSearch() {
        val keyword = binding.gjEtSearch.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(this, "请输入用户ID或昵称", Toast.LENGTH_SHORT).show()
            return
        }
        binding.gjProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val users = repository.searchUsers(keyword)
            binding.gjProgress.visibility = View.GONE
            adapter.submitList(users)
            if (users.isEmpty()) {
                binding.gjEmptyHint.visibility = View.VISIBLE
                binding.gjEmptyHint.text = "未找到匹配的用户"
            } else {
                binding.gjEmptyHint.visibility = View.GONE
            }
        }
    }

    private fun onUserClicked(user: UserEntity) {
        startActivity(Intent(this, UserProfileActivity::class.java).apply {
            putExtra(Constants.EXTRA_USER_ID, user.id)
            putExtra(Constants.EXTRA_USER_NAME, user.nickname)
        })
    }
}
