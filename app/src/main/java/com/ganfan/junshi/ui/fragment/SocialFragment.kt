package com.ganfan.junshi.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ganfan.junshi.Constants
import com.ganfan.junshi.databinding.FragmentSocialBinding
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.entity.MatchPostEntity
import com.ganfan.junshi.ui.adapter.MatchPostAdapter
import com.ganfan.junshi.ui.main.MainActivity
import com.ganfan.junshi.ui.match.MatchDetailActivity
import com.ganfan.junshi.ui.search.SearchUserActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SocialFragment : Fragment(), MainActivity.Refreshable {

    private var _binding: FragmentSocialBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: GanfanRepository
    private lateinit var matchAdapter: MatchPostAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSocialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyStatusBarTopPadding()
        repository = GanfanRepository.getInstance(requireContext())

        matchAdapter = MatchPostAdapter(
            onItemClick = { post -> openMatchDetail(post) }
        )

        binding.gjMatchList.layoutManager = LinearLayoutManager(context)
        binding.gjMatchList.adapter = matchAdapter

        binding.gjSwipeRefresh.setOnRefreshListener { onRefresh() }
        binding.gjSwipeRefresh.setColorSchemeResources(com.ganfan.junshi.R.color.gj_primary)

        binding.gjBtnSearchUser.setOnClickListener {
            startActivity(Intent(requireContext(), SearchUserActivity::class.java))
        }

        lifecycleScope.launch {
            repository.observeMatchPosts().collectLatest { posts ->
                val open = posts.filter { it.status == "open" || it.status == "full" }
                val userMap = open.map { it.publisherId }
                    .distinct()
                    .mapNotNull { id -> repository.getUserById(id)?.let { id to it } }
                    .toMap()
                matchAdapter.setUserMap(userMap)
                matchAdapter.submitList(open)
                binding.gjMatchEmpty.visibility = if (open.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        onRefresh()
    }

    private fun openMatchDetail(post: MatchPostEntity) {
        startActivity(Intent(requireContext(), MatchDetailActivity::class.java).apply {
            putExtra(Constants.EXTRA_MATCH_POST_ID, post.id)
        })
    }

    override fun onRefresh() {
        lifecycleScope.launch {
            repository.refreshMatchPosts()
            binding.gjSwipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
