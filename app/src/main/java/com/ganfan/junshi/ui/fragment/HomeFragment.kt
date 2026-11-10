package com.ganfan.junshi.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.databinding.FragmentHomeBinding
import com.ganfan.junshi.ui.ai.AiChatActivity
import com.ganfan.junshi.ui.ai.AiDialogActivity
import com.ganfan.junshi.ui.main.MainActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class HomeFragment : Fragment(), MainActivity.Refreshable {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: GanfanRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyStatusBarTopPadding()
        repository = GanfanRepository.getInstance(requireContext())
        binding.gjWelcome.text = "你好，${repository.session.nickname ?: "干饭人"}！"

        // 军师献策 → 跳转5题问卷
        binding.gjAiButton.setOnClickListener {
            startActivity(Intent(requireContext(), AiChatActivity::class.java))
        }
        // AI 聊吃 → 对话推荐
        binding.gjSpinButton.setOnClickListener {
            startActivity(Intent(requireContext(), AiDialogActivity::class.java))
        }
        binding.gjRefresh.setOnClickListener { onRefresh() }

        lifecycleScope.launch {
            val taste = repository.getUserTaste()
            val hint = taste?.let {
                "今日军师已就位，根据你的口味（${it.cuisines}）准备了推荐方案"
            } ?: "去「我的→胃的身份证」填写口味，推荐更精准"
            binding.gjAiReply.text = hint
        }

        onRefresh()
    }

    override fun onRefresh() {
        lifecycleScope.launch {
            repository.refreshRestaurants()
            repository.refreshMatchPosts()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
