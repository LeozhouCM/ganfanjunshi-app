package com.ganfan.junshi.ui.ai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.AppDatabase
import com.ganfan.junshi.data.local.entity.AiMessageEntity
import com.ganfan.junshi.data.local.entity.RestaurantEntity
import com.ganfan.junshi.data.local.entity.UserTasteEntity
import com.ganfan.junshi.databinding.ActivityAiDialogBinding
import com.ganfan.junshi.ui.restaurant.RestaurantDetailActivity
import com.ganfan.junshi.util.AmapRestaurantSearch
import kotlinx.coroutines.launch

class AiDialogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiDialogBinding
    private lateinit var repository: GanfanRepository
    private var conversationId: Long = -1
    private val messages = mutableListOf<AiMessageEntity>()
    private var allRestaurants: List<RestaurantEntity> = emptyList()
    private var taste: UserTasteEntity? = null
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.gjToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = GanfanRepository.getInstance(this)

        binding.gjChatRecycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.gjChatRecycler.adapter = ChatAdapter()

        binding.gjSendBtn.setOnClickListener { sendMessage() }

        lifecycleScope.launch {
            initConversation()
            loadRestaurants()
        }
    }

    private suspend fun initConversation() {
        val conversations = repository.getAiConversations()
        val existing = conversations.find { it.title == "命运转盘" || it.title == "AI聊吃" }

        conversationId = if (existing != null) {
            existing.id
        } else {
            repository.createAiConversation("命运转盘")
        }

        if (existing == null) {
            repository.saveAiMessage(
                AiMessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "老板好！我是你的AI干饭军师。告诉我你今天想吃什么类型、预算多少、什么场景、口味偏好，我帮你推荐附近的好餐厅~"
                )
            )
        }

        val msgs = repository.getAiMessages(conversationId)
        messages.clear()
        messages.addAll(msgs)
        binding.gjChatRecycler.adapter?.notifyDataSetChanged()
        if (messages.isNotEmpty()) {
            binding.gjChatRecycler.scrollToPosition(messages.size - 1)
        }
    }

    private suspend fun loadRestaurants() {
        repository.seedLocalDataIfNeeded()
        repository.refreshRestaurants()
        taste = repository.getUserTaste()

        val dbRestaurants = AppDatabase.getInstance(this).restaurantDao().getAll()
        val amapRestaurants = try {
            val location = AmapRestaurantSearch.getCurrentLocation(this)
            if (location != null) {
                AmapRestaurantSearch.searchNearby(
                    this, location.first, location.second, 3000, "美食"
                )
            } else emptyList()
        } catch (_: Exception) { emptyList() }

        val amapNames = amapRestaurants.map { it.name }.toSet()
        val uniqueDb = dbRestaurants.filter { it.name !in amapNames }
        allRestaurants = if (amapRestaurants.isNotEmpty()) amapRestaurants + uniqueDb else dbRestaurants
    }

    private fun sendMessage() {
        val input = binding.gjInput.text.toString().trim()
        if (input.isBlank() || isLoading) return

        binding.gjInput.text?.clear()
        hideKeyboard()

        isLoading = true
        binding.gjSendBtn.isEnabled = false

        lifecycleScope.launch {
            repository.saveAiMessage(
                AiMessageEntity(conversationId = conversationId, role = "user", content = input)
            )
            refreshMessages()

            val context = buildContext()
            val historySummary = buildHistorySummary()
            val fullMessage = if (historySummary.isNotBlank()) {
                "$historySummary\n\n用户最新输入: $input"
            } else {
                input
            }

            val result = repository.aiChatWithContext(fullMessage, context)
            val reply = result.getOrDefault("军师正在翻菜谱，稍等片刻～")

            repository.saveAiMessage(
                AiMessageEntity(conversationId = conversationId, role = "assistant", content = reply)
            )
            refreshMessages()

            isLoading = false
            runOnUiThread { binding.gjSendBtn.isEnabled = true }
        }
    }

    private suspend fun refreshMessages() {
        val msgs = repository.getAiMessages(conversationId)
        messages.clear()
        messages.addAll(msgs)
        runOnUiThread {
            binding.gjChatRecycler.adapter?.notifyDataSetChanged()
            if (messages.isNotEmpty()) {
                binding.gjChatRecycler.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun buildContext(): String {
        val sb = StringBuilder()
        taste?.let {
            sb.appendLine("用户长期口味画像：")
            sb.appendLine("- 辣度等级：${it.spiceLevel}/5")
            sb.appendLine("- 预算区间：${'$'}${it.budgetMin}-${it.budgetMax}")
            sb.appendLine("- 偏好菜系：${it.cuisines}")
            sb.appendLine("- 忌口禁忌：${it.taboos}")
            sb.appendLine()
        }
        if (allRestaurants.isNotEmpty()) {
            sb.appendLine("附近可选餐厅：")
            allRestaurants.forEach { r ->
                sb.appendLine("- ${r.name} | ${r.cuisine} | 人均${'$'}${r.avgPrice} | 评分${r.rating} | ${r.address}")
            }
        }
        return sb.toString()
    }

    private fun buildHistorySummary(): String {
        val recent = messages.takeLast(10)
        if (recent.isEmpty()) return ""
        return recent.joinToString("\n") { "${it.role}: ${it.content}" }
    }

    private fun parsePicks(reply: String): List<RestaurantEntity> {
        val names = Regex("""【(.+?)】""").findAll(reply).map { it.groupValues[1].trim() }.toList()
        return names.mapNotNull { name ->
            allRestaurants.find { r ->
                r.name == name || r.name.contains(name) || name.contains(r.name)
            }
        }
    }

    private fun inflateRestaurantCards(restaurants: List<RestaurantEntity>): List<View> {
        return restaurants.mapIndexed { idx, restaurant ->
            val card = layoutInflater.inflate(
                R.layout.item_restaurant_card, binding.gjChatRecycler, false
            ) as LinearLayout
            card.findViewById<TextView>(R.id.gj_card_name).text = restaurant.name
            card.findViewById<TextView>(R.id.gj_card_cuisine).text = restaurant.cuisine
            card.findViewById<TextView>(R.id.gj_card_price).text = "${'$'}${restaurant.avgPrice}/人"
            card.findViewById<TextView>(R.id.gj_card_rating).text = "\u2B50 ${restaurant.rating}"
            card.findViewById<TextView>(R.id.gj_card_address).text = restaurant.address
            card.findViewById<TextView>(R.id.gj_card_match).visibility =
                if (idx == 0) View.VISIBLE else View.GONE
            card.setOnClickListener {
                startActivity(Intent(this, RestaurantDetailActivity::class.java).apply {
                    putExtra("restaurant_id", restaurant.id)
                })
            }
            card
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.gjInput.windowToken, 0)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ==================== ChatAdapter ====================

    inner class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return if (messages[position].role == "user") 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_chat_message, parent, false)
            return if (viewType == 0) UserViewHolder(view) else AiViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val msg = messages[position]
            if (holder is UserViewHolder) holder.bind(msg)
            else if (holder is AiViewHolder) holder.bind(msg)
        }

        override fun getItemCount(): Int = messages.size
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubble: TextView = itemView.findViewById(R.id.gj_user_bubble)
        private val container: View = itemView.findViewById(R.id.gj_user_message)

        fun bind(msg: AiMessageEntity) {
            container.visibility = View.VISIBLE
            itemView.findViewById<View>(R.id.gj_ai_message).visibility = View.GONE
            bubble.text = msg.content
        }
    }

    inner class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubble: TextView = itemView.findViewById(R.id.gj_ai_bubble)
        private val container: View = itemView.findViewById(R.id.gj_ai_message)
        private val cardContainer: LinearLayout = itemView.findViewById(R.id.gj_card_container)

        fun bind(msg: AiMessageEntity) {
            container.visibility = View.VISIBLE
            itemView.findViewById<View>(R.id.gj_user_message).visibility = View.GONE
            bubble.text = msg.content

            cardContainer.removeAllViews()
            val picks = parsePicks(msg.content)
            if (picks.isNotEmpty()) {
                val cards = inflateRestaurantCards(picks)
                cards.forEach { cardContainer.addView(it) }
            }
        }
    }
}
