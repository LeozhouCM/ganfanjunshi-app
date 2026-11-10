package com.ganfan.junshi.ui.chat

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ganfan.junshi.Constants
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.remote.ChatWebSocketClient
import com.ganfan.junshi.databinding.ActivityChatBinding
import com.ganfan.junshi.ui.adapter.ChatMessageAdapter
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var repository: GanfanRepository
    private lateinit var adapter: ChatMessageAdapter
    private var conversationId: Long = 1
    private var webSocketClient: ChatWebSocketClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopPadding()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = GanfanRepository.getInstance(this)
        conversationId = intent.getLongExtra(Constants.EXTRA_CONVERSATION_ID,
            intent.getLongExtra("conversation_id", 1))
        title = intent.getStringExtra(Constants.EXTRA_PARTNER_NAME)
            ?: intent.getStringExtra("title")
            ?: "私信"

        adapter = ChatMessageAdapter(repository.session.userId)
        binding.gjChatList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.gjChatList.adapter = adapter

        binding.gjSend.setOnClickListener { sendMessage() }
        binding.gjInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        lifecycleScope.launch {
            repository.observeChatMessages(conversationId).collect {
                adapter.submitList(it)
                if (it.isNotEmpty()) binding.gjChatList.scrollToPosition(it.size - 1)
            }
        }

        lifecycleScope.launch {
            repository.markConversationRead(conversationId)
        }

        connectWebSocket()
    }

    private fun connectWebSocket() {
        val token = repository.session.token ?: return
        webSocketClient = ChatWebSocketClient(
            conversationId = conversationId,
            token = token,
            onMessageReceived = { convId, senderId, senderName, content, sentAt ->
                lifecycleScope.launch {
                    repository.insertWebSocketMessage(convId, senderId, senderName, content, sentAt)
                    repository.markConversationRead(convId)
                }
            }
        )
        webSocketClient?.connect()
    }

    private fun sendMessage() {
        val text = binding.gjInput.text.toString().trim()
        if (text.isEmpty()) return

        lifecycleScope.launch {
            val result = repository.sendMessage(conversationId, text)
            result.onSuccess {
                binding.gjInput.text.clear()
            }.onFailure { e ->
                android.widget.Toast.makeText(this@ChatActivity, e.message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient?.disconnect()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
