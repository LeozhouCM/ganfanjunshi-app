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
import com.ganfan.junshi.databinding.FragmentMatchBinding
import com.ganfan.junshi.ui.adapter.MatchPostAdapter
import com.ganfan.junshi.ui.chat.ChatActivity
import com.ganfan.junshi.ui.main.MainActivity
import com.ganfan.junshi.ui.match.PublishMatchActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class MatchFragment : Fragment(), MainActivity.Refreshable {

    private var _binding: FragmentMatchBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: GanfanRepository
    private lateinit var adapter: MatchPostAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyStatusBarTopPadding()
        repository = GanfanRepository.getInstance(requireContext())

        adapter = MatchPostAdapter(
            onItemClick = { post ->
                startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("conversation_id", post.id)
                    putExtra("title", post.title)
                })
            }
        )

        binding.gjMatchList.layoutManager = LinearLayoutManager(context)
        binding.gjMatchList.adapter = adapter

        binding.gjFabPublish.setOnClickListener {
            startActivity(Intent(requireContext(), PublishMatchActivity::class.java))
        }

        lifecycleScope.launch {
            repository.observeMatchPosts().collect { adapter.submitList(it) }
        }

        onRefresh()
    }

    override fun onRefresh() {
        lifecycleScope.launch { repository.refreshMatchPosts() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
