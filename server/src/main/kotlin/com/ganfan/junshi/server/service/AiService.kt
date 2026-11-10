package com.ganfan.junshi.server.service

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties

/**
 * AI 军师服务 —— 核心对话引擎
 *
 * API Key 读取优先级：
 *   1. 环境变量 DEEPSEEK_API_KEY（生产/Docker）
 *   2. 项目根目录 local.properties 中的 DEEPSEEK_API_KEY（本地开发，自动加载）
 *
 * 未配置 Key 时自动降级为本地规则引擎。
 */
object AiService {

    private val logger = LoggerFactory.getLogger(AiService::class.java)
    private val gson = Gson()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private val apiKey: String? = run {
        System.getenv("DEEPSEEK_API_KEY")?.takeIf { it.isNotBlank() }
            ?: loadFromLocalProperties("DEEPSEEK_API_KEY")
    }

    private val baseUrl: String = System.getenv("DEEPSEEK_BASE_URL")
        ?: loadFromLocalProperties("DEEPSEEK_BASE_URL")
        ?: "https://api.deepseek.com/v1"

    private val model: String = System.getenv("DEEPSEEK_MODEL")
        ?: loadFromLocalProperties("DEEPSEEK_MODEL")
        ?: "deepseek-chat"

    val isApiConfigured: Boolean get() = !apiKey.isNullOrBlank()

    init {
        if (isApiConfigured) {
            logger.info("DeepSeek API 已配置: model=$model, baseUrl=$baseUrl")
        } else {
            logger.warn("DeepSeek API Key 未配置 —— 将使用本地规则引擎")
            logger.warn("请在项目根目录 local.properties 中添加: DEEPSEEK_API_KEY=sk-你的key")
        }
    }

    private fun loadFromLocalProperties(key: String): String? {
        return try {
            val file = File("local.properties")
            if (!file.exists()) {
                val alt = File("../local.properties")
                if (!alt.exists()) return null
                alt
            } else file
        } catch (_: Exception) {
            null
        }?.let { f ->
            Properties().apply { f.inputStream().use { load(it) } }.getProperty(key)
        }
    }

    // ==================== 公开接口 ====================

    fun reply(message: String): String = generateReply(message, null)

    fun replyWithContext(message: String, context: String?): String = generateReply(message, context)

    // ==================== 核心调度 ====================

    private fun generateReply(message: String, context: String?): String {
        if (isApiConfigured) {
            val apiReply = callDeepSeek(message, context)
            if (apiReply != null) return apiReply
            logger.warn("DeepSeek API 调用失败，降级到本地规则引擎")
        }

        if (!context.isNullOrBlank()) {
            val contextual = localContextualReply(message, context)
            if (contextual != null) return contextual
        }
        return localKeywordFallback(message)
    }

    // ==================== DeepSeek API 调用 ====================

    private fun callDeepSeek(userMessage: String, context: String?): String? {
        return try {
            val messages = buildMessages(userMessage, context)
            val requestBody = DeepSeekRequest(
                model = model,
                messages = messages,
                temperature = 0.7,
                maxTokens = 550
            )

            val jsonBody = gson.toJson(requestBody)
            logger.info("DeepSeek 请求: model=$model, messages=${messages.size}")

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/chat/completions"))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val result = gson.fromJson(response.body(), DeepSeekResponse::class.java)
                val reply = result.choices.firstOrNull()?.message?.content?.trim()
                if (!reply.isNullOrBlank()) {
                    logger.info("DeepSeek 回复: ${reply.take(100)}...")
                    return reply
                }
            }
            logger.error("DeepSeek API 返回 ${response.statusCode()}: ${response.body()}")
            null
        } catch (e: Exception) {
            logger.error("DeepSeek API 异常: ${e.message}")
            null
        }
    }

    private fun buildMessages(userMessage: String, context: String?): List<DeepSeekMessage> {
        val messages = mutableListOf<DeepSeekMessage>()

        val systemPrompt = buildSystemPrompt(context)
        messages.add(DeepSeekMessage(role = "system", content = systemPrompt))

        if (!context.isNullOrBlank()) {
            val historyPattern = Regex("""(用户|军师)[：:]\\s*(.+?)(?=\\n(?:用户|军师)[：:]|$)""",
                setOf(RegexOption.DOT_MATCHES_ALL))
            val historyMatches = historyPattern.findAll(context).toList()
            if (historyMatches.isNotEmpty()) {
                for (match in historyMatches) {
                    val role = match.groupValues[1]
                    val content = match.groupValues[2].trim()
                    if (role == "用户") {
                        messages.add(DeepSeekMessage(role = "user", content = content))
                    } else {
                        messages.add(DeepSeekMessage(role = "assistant", content = content))
                    }
                }
            }
        }

        messages.add(DeepSeekMessage(role = "user", content = userMessage))
        return messages
    }

    private fun buildSystemPrompt(context: String?): String {
        val sb = StringBuilder()
        sb.appendLine("你是「干饭军师」，一个热情幽默的美食推荐专家。你擅长和用户自然聊天，理解他们的口味偏好和用餐需求，从附近餐厅中挑出最合适的推荐。")
        sb.appendLine()
        sb.appendLine("## 核心规则")
        sb.appendLine("1. 用户会用自然语言和你聊天（比如"想吃辣的"、"有什么清淡的推荐"、"人均80左右适合约会的"、"上次那家太贵了换一家"），你要理解他们的真实需求，不要假设他们填了什么选择题。")
        sb.appendLine("2. 严格按照「附近可选餐厅」列表推荐，绝不编造餐厅名。如果用户想吃的菜系列表里没有，诚实告知并推荐最接近的选项。")
        sb.appendLine("3. 关注对话上下文：如果用户说"换一家"、"太贵了"、"有没有别的"，要根据上一轮推荐做出调整。")
        sb.appendLine("4. 不要每次推荐同一批餐厅，优先推荐之前没提过的，给出多样性。")
        sb.appendLine("5. 用户如果没有明确表达想吃/找餐厅的意图（比如只是打招呼、闲聊），自然回应即可，不要强行推荐。")
        sb.appendLine()
        sb.appendLine("## 推荐输出格式（仅当需要推荐餐厅时使用）")
        sb.appendLine("第一行：用亲切自然的语气一句话总结推荐理由（例如"天冷就该涮火锅！给你挑了三家"、"约会嘛，氛围很重要，这三家评分高环境好"）")
        sb.appendLine()
        sb.appendLine("【餐厅名】")
        sb.appendLine("匹配理由：菜系 | 人均¥XX | 评分X.X — 一句话说明为什么适合你")
        sb.appendLine()
        sb.appendLine("最后加一句温馨小提示（如"建议提前订座"、"人多可以组个局"、"不合口味告诉我，我再换一批"）")
        sb.appendLine()
        sb.appendLine("## 挑选逻辑")
        sb.appendLine("- 菜系映射：川菜/麻辣/火锅→川湘+火锅类，粤菜/点心/清淡→粤京类，日料/寿司→日料类，西餐/牛排/意面→西餐烤肉，面食/饺子→面馆京菜，韩式→韩餐烤肉，海鲜→海鲜类")
        sb.appendLine("- 口味映射：辣/麻辣→川湘，清淡/养生→粤日京，酸/糖醋→湘粤，甜→粤日京，重口→川湘京")
        sb.appendLine("- 预算敏感：用户提了"便宜"/"实惠"→优先低价，"高档"/"请客"/"约会"→优先高评分中高价")
        sb.appendLine("- 场景匹配：约会/商务→评分高+人均80以上，朋友聚餐→性价比+氛围，随便吃/省钱→低价实惠")
        sb.appendLine("- 若所有餐厅都不完全匹配用户需求，推荐评分最高的1-3家并说明"虽然不完全对口，但这几家评分最高"")
        sb.appendLine("- 输出1-3家，不要超过3家")
        sb.appendLine()

        if (!context.isNullOrBlank()) {
            val tasteSection = Regex("""用户(?:长期)?口味画像[：:].*?(?=\n\n|\n(?:附近|对话)|$)""",
                setOf(RegexOption.DOT_MATCHES_ALL)).find(context)?.value
            if (!tasteSection.isNullOrBlank()) {
                sb.appendLine("## 用户长期口味画像（参考，不是硬约束）")
                sb.appendLine(tasteSection.trim())
                sb.appendLine()
            }

            val restaurantSection = Regex("""附近可选餐厅[：:].*?(?=\n\n|\n(?:对话)|$)""",
                setOf(RegexOption.DOT_MATCHES_ALL)).find(context)?.value
            if (!restaurantSection.isNullOrBlank()) {
                sb.appendLine("## 附近可选餐厅（只能从这里选，不可编造）")
                sb.appendLine(restaurantSection.trim())
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    // ==================== 本地规则引擎 ====================

    private data class R(val name: String, val cuisine: String, val price: Int, val rating: Double)

    private fun localContextualReply(msg: String, context: String): String? {
        val chosenFoodType = Regex("""想吃(.+?)[，,]""").find(msg)?.groupValues?.get(1)?.trim()
        val chosenBudget = Regex("""预算(.+?)[，,]""").find(msg)?.groupValues?.get(1)?.trim()
        val chosenOccasion = Regex("""场景「(.+?)」""").find(msg)?.groupValues?.get(1)?.trim()
        val chosenTaste = Regex("""口味「(.+?)」""").find(msg)?.groupValues?.get(1)?.trim()
        val chosenPriority = Regex("""最看重「(.+?)」""").find(msg)?.groupValues?.get(1)?.trim()

        val restaurantPattern = Regex("""- (.+?) \| (.+?) \| 人均[$¥](\d+) \| 评分([\d.]+)""")
        val restaurants = restaurantPattern.findAll(context).map { match ->
            val (n, c, p, r) = match.destructured
            R(n, c, p.toIntOrNull() ?: 0, r.toDoubleOrNull() ?: 0.0)
        }.toList()
        if (restaurants.isEmpty()) return null

        val budgetRange = when {
            chosenBudget?.contains("30以下") == true -> 0..30
            chosenBudget?.contains("30-80") == true -> 30..80
            chosenBudget?.contains("80-150") == true -> 80..150
            chosenBudget?.contains("150以上") == true -> 150..Int.MAX_VALUE
            else -> 0..Int.MAX_VALUE
        }

        var filtered = restaurants.toList()

        if (!chosenFoodType.isNullOrBlank() && chosenFoodType != "不挑类型") {
            val keywords = foodTypeKeywords(chosenFoodType)
            val byType = filtered.filter { r -> keywords.any { r.cuisine.contains(it) || r.name.contains(it) } }
            if (byType.isNotEmpty()) filtered = byType
        }
        when (chosenTaste) {
            "嗜辣" -> { val s = filtered.filter { it.cuisine.contains("川") || it.cuisine.contains("湘") }; if (s.isNotEmpty()) filtered = s }
            "嗜甜" -> { val s = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("京") || it.cuisine.contains("日") }; if (s.isNotEmpty()) filtered = s }
            "偏咸" -> { val s = filtered.filter { it.cuisine.contains("京") || it.cuisine.contains("川") || it.cuisine.contains("烤") }; if (s.isNotEmpty()) filtered = s }
            "偏酸" -> { val s = filtered.filter { it.name.contains("酸") || it.cuisine.contains("湘") }; if (s.isNotEmpty()) filtered = s }
            "清淡" -> { val s = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("日") || it.cuisine.contains("京") }; if (s.isNotEmpty()) filtered = s }
        }
        val inB = filtered.filter { it.price in budgetRange }; if (inB.isNotEmpty()) filtered = inB

        if (filtered.isEmpty()) {
            filtered = restaurants.toList()
            when (chosenTaste) {
                "嗜辣" -> { val s = filtered.filter { it.cuisine.contains("川") || it.cuisine.contains("湘") }; if (s.isNotEmpty()) filtered = s }
                "嗜甜" -> { val s = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("日") || it.cuisine.contains("京") }; if (s.isNotEmpty()) filtered = s }
                "清淡" -> { val s = filtered.filter { it.cuisine.contains("粤") || it.cuisine.contains("日") || it.cuisine.contains("京") }; if (s.isNotEmpty()) filtered = s }
            }
            val ib2 = filtered.filter { it.price in budgetRange }; if (ib2.isNotEmpty()) filtered = ib2
        }
        if (filtered.isEmpty()) filtered = restaurants.toList()

        filtered = when (chosenPriority) {
            "评分口碑" -> filtered.sortedByDescending { it.rating }
            "性价比"   -> filtered.sortedBy { it.price }
            "环境氛围" -> filtered.sortedByDescending { it.rating * 0.7 + it.price / 200.0 * 0.3 }
            else -> when (chosenOccasion) {
                "情侣约会", "商务请客" -> filtered.sortedByDescending { it.rating }
                "随便吃点" -> filtered.sortedBy { it.price }
                else -> filtered.sortedByDescending { it.rating }
            }
        }

        val top = filtered.take(3)
        val summary = when {
            chosenTaste == "嗜辣" -> "根据你爱吃辣的偏好，为你精选："
            chosenTaste == "嗜甜" -> "根据你爱吃甜的偏好，为你精选："
            chosenTaste == "清淡" -> "根据你偏好清淡口味，为你精选："
            !chosenFoodType.isNullOrBlank() && chosenFoodType != "不挑类型" -> "根据你偏爱${chosenFoodType}，为你精选："
            else -> "军师为你精选以下菜馆："
        }
        val lines = top.joinToString("\n") { r ->
            "【${r.name}】\n匹配理由：${r.cuisine} | 人均¥${r.price} | 评分${r.rating}，符合你的偏好"
        }
        val tip = when (chosenOccasion) {
            "情侣约会", "商务请客" -> "💡 建议提前预约～"
            "朋友聚餐" -> "💡 人多可以看看「摇人吃饭」！"
            else -> "💡 不满意？点击「重新来过」换一组条件试试～"
        }
        return "$summary\n\n$lines\n\n$tip"
    }

    private fun foodTypeKeywords(foodType: String): List<String> = when (foodType) {
        "无肉不欢" -> listOf("烤", "肉", "川", "湘", "烧")
        "素食清淡" -> listOf("素", "粥", "粤", "清")
        "面食小吃" -> listOf("面", "京", "小")
        "日式料理" -> listOf("日", "寿司")
        "快餐速食" -> listOf("快", "面", "炸")
        "家常菜"   -> listOf("京", "粤", "川", "小")
        else       -> emptyList()
    }

    private fun localKeywordFallback(msg: String): String {
        val chosenFoodType = Regex("""想吃(.+?)[，,]""").find(msg)?.groupValues?.get(1)?.trim()
        val chosenBudget = Regex("""预算(.+?)[，,]""").find(msg)?.groupValues?.get(1)?.trim()
        val chosenOccasion = Regex("""场景「(.+?)」""").find(msg)?.groupValues?.get(1)?.trim()
        val chosenTaste = Regex("""口味「(.+?)」""").find(msg)?.groupValues?.get(1)?.trim()

        if (chosenFoodType != null || chosenBudget != null) {
            val foodType = chosenFoodType ?: "美食"
            return buildString {
                append("根据你的偏好（${foodType}·${chosenBudget ?: "适中预算"}·${chosenOccasion ?: "随便吃点"}·${chosenTaste ?: "不挑口味"}），")
                append("建议打开「觅食图」查看附近${foodType}餐厅，")
                append("或者去「摇人吃饭」看看有没有人正在凑局～\n\n")
                append("💡 提示：配置 DEEPSEEK_API_KEY 可启用 AI 大模型智能推荐！")
            }
        }

        return when {
            msg.contains("辣") -> "军师建议：今天来顿川菜怎么样？附近「蜀香小馆」评分 4.8，微辣友好～"
            msg.contains("清淡") -> "军师献策：养生局推荐「清粥小厨」，暖胃不负担，人均 ¥35。"
            msg.contains("火锅") || msg.contains("烤肉") -> "火锅烤肉一时爽！看看「摇人吃饭」有没有正在凑桌的局？"
            msg.contains("日料") || msg.contains("寿司") -> "「樱花寿司」评分 4.9，适合周末探店。"
            msg.contains("面") || msg.contains("快餐") -> "「老北京炸酱面」人均¥28，地道又管饱，附近就有！"
            msg.contains("约会") || msg.contains("请客") -> "排面局推荐「樱花寿司」—— 日料 | 人均¥120 | 评分 4.9，精致有面子！"
            else -> "军师已收到你的偏好！建议打开「觅食图」看看附近餐厅，或去「摇人吃饭」发个局～\n\n💡 提示：配置 DEEPSEEK_API_KEY 环境变量可启用 AI 大模型智能推荐！"
        }
    }
}

// ==================== DeepSeek API 数据类 ====================

data class DeepSeekRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 800
)

data class DeepSeekMessage(
    val role: String,
    val content: String
)

data class DeepSeekResponse(
    val choices: List<DeepSeekChoice>
)

data class DeepSeekChoice(
    val message: DeepSeekMessage,
    @SerializedName("finish_reason") val finishReason: String? = null
)
