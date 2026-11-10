package com.ganfan.junshi.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ganfan.junshi.Constants
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.databinding.FragmentMessagesBinding
import com.ganfan.junshi.ui.adapter.ConversationAdapter
import com.ganfan.junshi.ui.adapter.MatchInviteAdapter
import com.ganfan.junshi.ui.chat.ChatActivity
import com.ganfan.junshi.ui.main.MainActivity
import com.ganfan.junshi.ui.match.MatchDetailActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MessagesFragment : Fragment(), MainActivity.Refreshable {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: GanfanRepository
    private lateinit var chatAdapter: ConversationAdapter
    private lateinit var inviteAdapter: MatchInviteAdapter
    private var showInvites = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyStatusBarTopPadding()
        repository = GanfanRepository.getInstance(requireContext())

        chatAdapter = ConversationAdapter(repository, lifecycleScope) { conversation ->
            startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra(Constants.EXTRA_CONVERSATION_ID, conversation.id)
                putExtra(Constants.EXTRA_PARTNER_NAME, conversation.partnerName)
            })
        }

        inviteAdapter = MatchInviteAdapter(
            onApprove = { item ->
                lifecycleScope.launch {
                    repository.approveApplication(item.postId, item.id).onSuccess {
                        Toast.makeText(context, "已同意入桌", Toast.LENGTH_SHORT).show()
                        refreshInvites()
                    }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
                }
            },
            onReject = { item ->
                lifecycleScope.launch {
                    when (item.type) {
                        "buddy_share" -> repository.declineBuddyInvite(item.id)
                        else -> repository.rejectApplication(item.postId, item.id)
                    }
                    refreshInvites()
                }
            },
            onAcceptBuddy = { item ->
                lifecycleScope.launch {
                    repository.acceptBuddyInvite(item.id).onSuccess {
                        Toast.makeText(context, "已加入饭桌", Toast.LENGTH_SHORT).show()
                        refreshInvites()
                    }
                }
            },
            onView = { item ->
                if (item.postId > 0) {
                    startActivity(Intent(requireContext(), MatchDetailActivity::class.java).apply {
                        putExtra(Constants.EXTRA_MATCH_POST_ID, item.postId)
                    })
                }
            }
        )

        binding.gjConversationList.layoutManager = LinearLayoutManager(context)
        binding.gjConversationList.adapter = chatAdapter
        binding.gjInviteList.layoutManager = LinearLayoutManager(context)
        binding.gjInviteList.adapter = inviteAdapter

        binding.gjMessageTabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showInvites = checkedId == R.id.gj_tab_invite
            updateTabVisibility()
        }
        binding.gjSwipeRefresh.setOnRefreshListener { onRefresh() }
        binding.gjSwipeRefresh.setColorSchemeResources(R.color.gj_primary)

        lifecycleScope.launch {
            repository.observeMatchConversations().collectLatest { list ->
                chatAdapter.submitList(list)
                if (!showInvites) {
                    binding.gjEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.gjEmpty.text = getString(R.string.gj_messages_empty)
                }
            }
        }

        onRefresh()
    }

    private fun updateTabVisibility() {
        binding.gjConversationList.visibility = if (showInvites) View.GONE else View.VISIBLE
        binding.gjInviteList.visibility = if (showInvites) View.VISIBLE else View.GONE
        if (showInvites) {
            refreshInvites()
        } else {
            binding.gjEmpty.visibility = View.GONE
        }
    }

    private fun refreshInvites() {
        lifecycleScope.launch {
            val items = repository.getInviteItems()
            inviteAdapter.submitList(items)
            if (showInvites) {
                binding.gjEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.gjEmpty.text = getString(R.string.gj_invites_empty)
            }
        }
    }

    override fun onRefresh() {
        lifecycleScope.launch {
            repository.getConversations()
            refreshInvites()
            binding.gjSwipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
