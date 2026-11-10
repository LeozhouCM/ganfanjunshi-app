package com.ganfan.junshi.ui.match

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.model.CreateMatchRequest
import com.ganfan.junshi.databinding.ActivityPublishMatchBinding
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PublishMatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPublishMatchBinding
    private lateinit var repository: GanfanRepository

    private var selectedCuisine: String = ""
    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var selectedDay: Int = 0
    private var selectedHour: Int = 18
    private var selectedMinute: Int = 0
    private var mealTimeSet: Boolean = false

    private val cuisines = listOf("川菜", "火锅", "日料", "烤肉", "面食", "其他")
    private val cuisineChips = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPublishMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopPadding()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "摇人吃饭"

        repository = GanfanRepository.getInstance(this)

        initCuisineChips()
        initMealTimePicker()
        binding.gjSubmit.setOnClickListener { submit() }
    }

    private fun initCuisineChips() {
        val chipGroup = binding.gjCuisineChipGroup
        cuisines.forEach { name ->
            val chip = TextView(this).apply {
                text = name
                setTextColor(getColor(R.color.gj_text_primary))
                textSize = 13f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                background = getDrawable(R.drawable.gj_chip_cuisine_bg)
                setOnClickListener { selectCuisine(name, this) }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) }
            chipGroup.addView(chip, params)
            cuisineChips.add(chip)
        }
    }

    private fun selectCuisine(name: String, chip: TextView) {
        selectedCuisine = name
        cuisineChips.forEach { c ->
            if (c == chip) {
                c.background = getDrawable(R.drawable.gj_chip_cuisine_selected_bg)
                c.setTextColor(getColor(android.R.color.white))
            } else {
                c.background = getDrawable(R.drawable.gj_chip_cuisine_bg)
                c.setTextColor(getColor(R.color.gj_text_primary))
            }
        }
    }

    private fun initMealTimePicker() {
        val now = Calendar.getInstance()
        selectedYear = now.get(Calendar.YEAR)
        selectedMonth = now.get(Calendar.MONTH)
        selectedDay = now.get(Calendar.DAY_OF_MONTH)
        selectedHour = 18
        selectedMinute = 0

        binding.gjMealTimeRow.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedYear = year
                    selectedMonth = month
                    selectedDay = day
                    showTimePicker()
                },
                selectedYear, selectedMonth, selectedDay
            ).show()
        }
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                mealTimeSet = true
                updateMealTimeDisplay()
            },
            selectedHour, selectedMinute, true
        ).show()
    }

    private fun updateMealTimeDisplay() {
        val cal = Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
        }
        val weekDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            Calendar.SUNDAY -> "周日"
            else -> ""
        }
        val display = "${selectedMonth + 1}月${selectedDay}日 $weekDay ${String.format("%02d:%02d", selectedHour, selectedMinute)}"
        binding.gjMealTimeDisplay.text = display
        binding.gjMealTimeDisplay.setTextColor(getColor(R.color.gj_text_primary))
    }

    private fun parseMaxPeople(): Int? {
        return binding.gjPeopleInput.text.toString().trim().toIntOrNull()?.takeIf { it in 2..20 }
    }

    private fun parseBudget(): Int? {
        return binding.gjBudgetInput.text.toString().trim().toIntOrNull()?.takeIf { it > 0 }
    }

    private fun buildMealTimeString(): String {
        return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(
            Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
            }.time
        )
    }

    private fun submit() {
        val title = binding.gjTitleInput.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "请输入饭局标题", Toast.LENGTH_SHORT).show()
            return
        }
        if (!mealTimeSet) {
            Toast.makeText(this, getString(R.string.gj_publish_time_required), Toast.LENGTH_SHORT).show()
            return
        }
        val maxPeople = parseMaxPeople()
        if (maxPeople == null) {
            Toast.makeText(this, getString(R.string.gj_publish_people_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        val budget = parseBudget()
        if (budget == null) {
            Toast.makeText(this, getString(R.string.gj_publish_budget_invalid), Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateMatchRequest(
            title = title,
            mealTime = buildMealTimeString(),
            maxPeople = maxPeople,
            budget = budget,
            lat = 39.9,
            lng = 116.4
        )

        binding.gjSubmit.isEnabled = false
        lifecycleScope.launch {
            val result = repository.createMatchPost(request)
            binding.gjSubmit.isEnabled = true
            result.onSuccess {
                Toast.makeText(this@PublishMatchActivity, "发布成功！", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }.onFailure { e ->
                Toast.makeText(this@PublishMatchActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
