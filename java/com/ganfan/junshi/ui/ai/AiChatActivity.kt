package com.ganfan.junshi.ui.ai

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.AppDatabase
import com.ganfan.junshi.data.local.entity.RestaurantEntity
import com.ganfan.junshi.data.local.entity.UserTasteEntity
import com.ganfan.junshi.databinding.ActivityAiChatBinding
import com.ganfan.junshi.ui.restaurant.RestaurantDetailActivity
import com.ganfan.junshi.util.AmapRestaurantSearch
import kotlinx.coroutines.launch

class AiChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiChatBinding
    private lateinit var repository: GanfanRepository
    private var taste: UserTasteEntity? = null
    private var currentQuestion = 0
    private val answers = mutableMapOf<String, String>()
    private var allRestaurants: List<RestaurantEntity> = emptyList()

    // ==================== 问卷（5 题） ====================

    private val questions = listOf(
        Question("今天想吃什么类型？", listOf(
            Option("🥩 无肉不欢",   "foodType", "meat"),
            Option("🥦 素食清淡",   "foodType", "veggie"),
            Option("🍜 面食小吃",   "foodType", "noodle"),
            Option("🍣 日式料理",   "foodType", "japanese"),
            Option("🍔 快餐速食",   "foodType", "fastfood"),
            Option("🏠 家常菜",     "foodType", "home"),
            Option("🎲 都行，不挑", "foodType", "any")
        )),
        Question("人均预算多少？", listOf(
            Option("💰 ¥30以下",  "budget", "low"),
            Option("💵 ¥30-80",   "budget", "mid"),
            Option("💳 ¥80-150",  "budget", "high"),
            Option("👑 ¥150以上", "budget", "luxury")
        )),
        Question("什么用餐场景？", listOf(
            Option("🧑‍🍳 自己随便吃", "occasion", "casual"),
            Option("👥 朋友聚餐",     "occasion", "group"),
            Option("💕 情侣约会",     "occasion", "date"),
            Option("💼 商务请客",     "occasion", "business")
        )),
        Question("口味偏好？", listOf(
            Option("🔥 嗜辣",       "taste", "spicy"),
            Option("🍬 嗜甜",       "taste", "sweet"),
            Option("🧂 偏咸",       "taste", "salty"),
            Option("🍋 偏酸",       "taste", "sour"),
            Option("🌿 清淡",       "taste", "light"),
            Option("😶 不挑口味",   "taste", "any")
        )),
        Question("最看重什么？", listOf(
            Option("⭐ 评分口碑", "priority", "rating"),
            Option("💰 性价比",   "priority", "value"),
            Option("🎭 环境氛围", "priority", "ambience"),
            Option("😎 无所谓",   "priority", "any")
        ))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.gjToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = GanfanRepository.getInstance(this)

        binding.gjAgainBtn.setOnClickListener { resetQuestions() }

        lifecycleScope.launch {
            // 1. 预加载种子数据（离线兜底）
            repository.seedLocalDataIfNeeded()
            repository.refreshRestaurants()
            taste = repository.getUserTaste()

            // 2. 从 Room 获取已缓存的餐厅
            val dbRestaurants = AppDatabase.getInstance(this@AiChatActivity).restaurantDao().getAll()

            // 3. 尝试从高德 POI 实时搜索周边餐厅（更丰富、更实时）
            val amapRestaurants = try {
                val location = AmapRestaurantSearch.getCurrentLocation(this@AiChatActivity)
                if (location != null) {
                    AmapRestaurantSearch.searchNearby(
                        this@AiChatActivity,
                        location.first,
                        location.second,
                        3000,  // 3km 半径
                        "美食"
                    )
                } else {
                    emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }

            // 4. 合并：高德 POI 优先，DB 数据去重补充
            val amapNames = amapRestaurants.map { it.name }.toSet()
            val uniqueDb = dbRestaurants.filter { it.name !in amapNames }
            allRestaurants = if (amapRestaurants.isNotEmpty()) {
                amapRestaurants + uniqueDb
            } else {
                dbRestaurants  // 高德不可用时回退到 DB/种子数据
            }

            showQuestion(0)
        }
    }

    // ==================== 题目显示 ====================

    private fun showQuestion(index: Int) {
        currentQuestion = index
        val q = questions[index]

        binding.gjQuestionStep.text = "第 ${index + 1}/${questions.size} 问"
        binding.gjQuestionText.text = q.text
        binding.gjOptionsContainer.removeAllViews()

        binding.gjQuestionCard.visibility = View.VISIBLE
        binding.gjLoadingArea.visibility = View.GONE
        binding.gjResultsArea.visibility = View.GONE
        binding.gjAgainBtn.visibility = View.GONE

        q.options.forEach { option ->
            val tv = TextView(this).apply {
                text = option.label
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 18
                lp.gravity = Gravity.CENTER
                lp.marginStart = 16
                lp.marginEnd = 16
                layoutParams = lp
                setPadding(32, 20, 32, 20)
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.gj_primary))
                background = ContextCompat.getDrawable(context, R.drawable.gj_option_bg)
                setOnClickListener { onOptionSelected(option, this) }
            }
            binding.gjOptionsContainer.addView(tv)
        }
    }

    private fun onOptionSelected(option: Option, view: TextView) {
        view.apply {
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 56f
                setColor(ContextCompat.getColor(context, R.color.gj_primary))
            }
        }
        for (i in 0 until binding.gjOptionsContainer.childCount) {
            binding.gjOptionsContainer.getChildAt(i).isEnabled = false
        }
        answers[option.key] = option.value

        view.postDelayed({
            if (currentQuestion < questions.size - 1) showQuestion(currentQuestion + 1)
            else submitAnswers()
        }, 250)
    }

    // ==================== 提交：prompt 驱动 ====================

    private fun submitAnswers() {
        binding.gjQuestionCard.visibility = View.GONE
        binding.gjLoadingArea.visibility = View.VISIBLE
        binding.gjResultsArea.visibility = View.GONE

        lifecycleScope.launch {
            val context = buildContext()
            val message = buildQueryMessage()

            val aiReply = repository.aiChatWithContext(message, context).getOrDefault("")

            val picks = parseAiPicks(aiReply)

            val logicText = picks.firstOrNull()?.takeIf { it.name == "_HEADER_" }?.reason ?: ""
            val restaurantPicks = picks.filter { it.name != "_HEADER_" }

            if (restaurantPicks.isNotEmpty()) {
                showResults(logicText, restaurantPicks, emptyList())
            } else {
                val fallback = localFallback()
                val logic = buildString {
                    append("📋 根据你的选择（")
                    append(foodTypeLabel(answers["foodType"] ?: "any"))
                    append(" · ${occasionLabel(answers["occasion"] ?: "casual")}")
                    append(" · ${tasteLabel(answers["taste"] ?: "any")}），")
                    append("附近暂无完全匹配的餐厅。")
                }
                showResults(logic, emptyList(), fallback)
            }
        }
    }

    // ==================== AI 回复解析 ====================

    private fun parseAiPicks(reply: String): List<AiPick> {
        val results = mutableListOf<AiPick>()

        val headerEnd = reply.indexOf("【")
        if (headerEnd > 0) {
            val header = reply.substring(0, headerEnd).trim()
            if (header.isNotBlank()) results.add(AiPick("_HEADER_", header))
        }

        val pattern = Regex("""【(.+?)】\s*\n?\s*匹配理由[：:]\s*(.+?)(?=\n【|\n\n【|\n💡|\n\n💡|$)""",
            setOf(RegexOption.DOT_MATCHES_ALL))
        pattern.findAll(reply).forEach { match ->
            val name = match.groupValues[1].trim()
            val reason = match.groupValues[2].trim().replace("\n", " ").take(80)
            results.add(AiPick(name, reason))
        }

        if (results.all { it.name == "_HEADER_" }) {
            Regex("""【(.+?)】""").findAll(reply).map { it.groupValues[1].trim() }.forEach { name ->
                results.add(AiPick(name, ""))
            }
        }

        return results
    }

    private fun fuzzyMatch(name: String): RestaurantEntity? {
        return allRestaurants.find { r ->
            r.name == name || r.name.contains(name) || name.contains(r.name)
        }
    }

    // ==================== 本地兜底 ====================

    private fun localFallback(): List<RestaurantEntity> {
        if (allRestaurants.isEmpty()) return emptyList()

        val foodType = answers["foodType"] ?: "any"
        val budgetKey = answers["budget"] ?: "mid"
        val tasteKey = answers["taste"] ?: "any"

        val budgetRange = when (budgetKey) {
            "low" -> 0..30; "mid" -> 30..80; "high" -> 80..150; "luxury" -> 150..Int.MAX_VALUE
            else -> 0..Int.MAX_VALUE
        }

        var filtered = allRestaurants.toList()

        if (foodType != "any") {
            val kw = foodTypeKeywords(foodType)
            val byType = filtered.filter { r -> kw.any { r.cuisine.contains(it) || r.name.contains(it) || r.tags.contains(it) } }
            if (byType.isNotEmpty()) filtered = byType
        }
        when (tasteKey) {
            "spicy" -> { val s = filtered.filter { it.cuisine.contains("川") || it.cuisine.contains("湘") || it.tags.contains("辣") }; if (s.isNotEmpty()) filtered = s }
            "sweet" -> { val s = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("京") || it.cuisine.contains("日") }; if (s.isNotEmpty()) filtered = s }
            "salty" -> { val s = filtered.filter { it.cuisine.contains("京") || it.cuisine.contains("川") || it.cuisine.contains("烤") }; if (s.isNotEmpty()) filtered = s }
            "sour"  -> { val s = filtered.filter { it.name.contains("酸") || it.cuisine.contains("湘") }; if (s.isNotEmpty()) filtered = s }
            "light" -> { val s = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("日") || it.cuisine.contains("京") }; if (s.isNotEmpty()) filtered = s }
        }
        val inB = filtered.filter { it.avgPrice in budgetRange }; if (inB.isNotEmpty()) filtered = inB

        if (filtered.isEmpty()) {
            filtered = allRestaurants.toList()
            when (tasteKey) {
                "spicy" -> { val s = filtered.filter { it.cuisine.contains("川") || it.cuisine.contains("湘") }; if (s.isNotEmpty()) filtered = s }
                "sweet" -> { val s = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("日") || it.cuisine.contains("京") }; if (s.isNotEmpty()) filtered = s }
                "light" -> { val s = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("日") || it.cuisine.contains("京") }; if (s.isNotEmpty()) filtered = s }
            }
        }
        if (filtered.isEmpty()) filtered = allRestaurants.toList()

        val profile = taste
        val scored = filtered.map { r ->
            var profileScore = 0.0
            if (profile != null) {
                val prefs = profile.cuisines.split(",", "，").map { it.trim() }.filter { it.isNotBlank() && it != "不限" }
                if (prefs.any { r.cuisine.contains(it) || it.contains(r.cuisine) }) profileScore += 0.40
                if (r.avgPrice in profile.budgetMin..profile.budgetMax) profileScore += 0.30
                else if (r.avgPrice in (profile.budgetMin - 20)..(profile.budgetMax + 30)) profileScore += 0.10
                if (profile.spiceLevel >= 4 && (r.cuisine.contains("川") || r.cuisine.contains("湘"))) profileScore += 0.20
                if (profile.spiceLevel <= 1 && !r.cuisine.contains("川") && !r.cuisine.contains("湘")) profileScore += 0.10
            }
            val ratingNorm = r.rating / 5.0 * 0.10
            Triple(r, profileScore, r.rating)
        }
        return scored
            .sortedWith(compareByDescending<Triple<RestaurantEntity, Double, Double>> { it.second }
                .thenByDescending { it.third })
            .map { it.first }
    }

    // ==================== 结果展示 ====================

    private fun showResults(
        headerText: String,
        aiPicks: List<AiPick>,
        fallback: List<RestaurantEntity>
    ) {
        binding.gjLoadingArea.visibility = View.GONE
        binding.gjResultsArea.visibility = View.VISIBLE
        binding.gjAgainBtn.visibility = View.VISIBLE

        if (headerText.isNotBlank()) {
            binding.gjAiCommentary.text = headerText
            binding.gjAiCommentary.visibility = View.VISIBLE
        } else {
            binding.gjAiCommentary.visibility = View.GONE
        }

        binding.gjResultsContainer.removeAllViews()
        binding.gjFallbackContainer.removeAllViews()

        if (aiPicks.isNotEmpty()) {
            binding.gjSectionTitle.visibility = View.VISIBLE
            binding.gjSectionTitle.text = "🎯 军师为你精选"
            aiPicks.forEachIndexed { idx, pick ->
                val restaurant = fuzzyMatch(pick.name)
                if (restaurant != null) {
                    val card = inflateCard(restaurant, idx == 0)
                    binding.gjResultsContainer.addView(card)

                    if (pick.reason.isNotBlank()) {
                        val reasonTv = TextView(this).apply {
                            text = "💬 ${pick.reason}"
                            setPadding(16, 4, 16, 12)
                            textSize = 13f
                            setTextColor(ContextCompat.getColor(context, R.color.gj_text_secondary))
                        }
                        binding.gjResultsContainer.addView(reasonTv)
                    }
                }
            }
        } else {
            binding.gjSectionTitle.visibility = View.GONE
        }

        if (fallback.isNotEmpty()) {
            binding.gjFallbackDivider.visibility = View.VISIBLE
            binding.gjFallbackTitle.visibility = View.VISIBLE
            binding.gjFallbackTitle.text = "📋 根据你的口味画像推荐以下备选："
            fallback.forEachIndexed { idx, r ->
                binding.gjFallbackContainer.addView(inflateCard(r, idx == 0))
            }
        } else {
            binding.gjFallbackDivider.visibility = View.GONE
            binding.gjFallbackTitle.visibility = View.GONE
        }

        if (aiPicks.isEmpty() && fallback.isEmpty()) {
            binding.gjSectionTitle.visibility = View.VISIBLE
            binding.gjSectionTitle.text = "😕 附近暂无餐厅数据"
        }
    }

    private fun inflateCard(restaurant: RestaurantEntity, best: Boolean): View {
        val card = layoutInflater.inflate(R.layout.item_restaurant_card, binding.gjResultsContainer, false)
        card.findViewById<TextView>(R.id.gj_card_name).text = restaurant.name
        card.findViewById<TextView>(R.id.gj_card_cuisine).text = restaurant.cuisine
        card.findViewById<TextView>(R.id.gj_card_price).text = "¥${restaurant.avgPrice}/人"
        card.findViewById<TextView>(R.id.gj_card_rating).text = "⭐ ${restaurant.rating}"
        card.findViewById<TextView>(R.id.gj_card_address).text = restaurant.address
        card.findViewById<TextView>(R.id.gj_card_match).visibility = if (best) View.VISIBLE else View.GONE

        card.setOnClickListener {
            startActivity(Intent(this, RestaurantDetailActivity::class.java).apply {
                putExtra("restaurant_id", restaurant.id)
            })
        }

        return card
    }

    // ==================== 上下文构建 ====================

    private fun buildContext(): String {
        val sb = StringBuilder()
        taste?.let {
            sb.appendLine("用户长期口味画像：")
            sb.appendLine("- 辣度等级：${it.spiceLevel}/5")
            sb.appendLine("- 预算区间：¥${it.budgetMin}-${it.budgetMax}")
            sb.appendLine("- 偏好菜系：${it.cuisines}")
            sb.appendLine("- 忌口禁忌：${it.taboos}")
            sb.appendLine()
        }
        if (allRestaurants.isNotEmpty()) {
            sb.appendLine("附近可选餐厅：")
            allRestaurants.forEach { r ->
                sb.appendLine("- ${r.name} | ${r.cuisine} | 人均¥${r.avgPrice} | 评分${r.rating} | ${r.address}")
            }
        }
        return sb.toString()
    }

    private fun buildQueryMessage(): String = buildString {
        append("用户当前选择：")
        append("想吃${foodTypeLabel(answers["foodType"] ?: "any")}，")
        append("预算${budgetLabel(answers["budget"] ?: "mid")}，")
        append("场景「${occasionLabel(answers["occasion"] ?: "casual")}」，")
        append("口味「${tasteLabel(answers["taste"] ?: "any")}」，")
        append("最看重「${priorityLabel(answers["priority"] ?: "any")}」。")
        append("请从附近餐厅列表中挑选最合适的推荐，用【餐厅名】标记。")
    }

    private fun resetQuestions() {
        answers.clear()
        currentQuestion = 0
        showQuestion(0)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ==================== 工具方法 ====================

    private fun foodTypeKeywords(ft: String) = when (ft) {
        "meat" -> listOf("烤","肉","川","湘","烧")
        "veggie" -> listOf("素","粥","粤","清")
        "noodle" -> listOf("面","京","小")
        "japanese" -> listOf("日","寿司")
        "fastfood" -> listOf("快","面","炸")
        "home" -> listOf("京","粤","川","小")
        else -> emptyList()
    }
    private fun foodTypeLabel(k: String) = when(k) {
        "meat"->"无肉不欢"; "veggie"->"素食清淡"; "noodle"->"面食小吃"
        "japanese"->"日式料理"; "fastfood"->"快餐速食"; "home"->"家常菜"; else->"不挑类型"
    }
    private fun budgetLabel(k: String) = when(k) {
        "low"->"¥30以下"; "mid"->"¥30-80"; "high"->"¥80-150"; "luxury"->"¥150以上"; else->"适中"
    }
    private fun occasionLabel(k: String) = when(k) {
        "casual"->"随便吃点"; "group"->"朋友聚餐"; "date"->"情侣约会"; "business"->"商务请客"; else->"随便吃点"
    }
    private fun tasteLabel(k: String) = when(k) {
        "spicy"->"嗜辣"; "sweet"->"嗜甜"; "salty"->"偏咸"; "sour"->"偏酸"; "light"->"清淡"; else->"不挑口味"
    }
    private fun priorityLabel(k: String) = when(k) {
        "rating"->"评分口碑"; "value"->"性价比"; "ambience"->"环境氛围"; else->"随缘推荐"
    }
}

private data class Question(val text: String, val options: List<Option>)
private data class Option(val label: String, val key: String, val value: String)
private data class AiPick(val name: String, val reason: String)
