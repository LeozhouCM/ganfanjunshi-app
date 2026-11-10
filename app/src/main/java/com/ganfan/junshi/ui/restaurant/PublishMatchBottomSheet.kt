package com.ganfan.junshi.ui.restaurant

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.model.CreateMatchRequest
import com.ganfan.junshi.databinding.BottomSheetPublishMatchBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PublishMatchBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPublishMatchBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: GanfanRepository

    private var restaurantId: Long = -1
    private var restaurantLat = 39.9
    private var restaurantLng = 116.4
    private var defaultBudget = 80

    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var selectedDay: Int = 0
    private var selectedHour: Int = 18
    private var selectedMinute: Int = 0
    private var mealTimeSet: Boolean = false

    var onPublished: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restaurantId = arguments?.getLong(ARG_RESTAURANT_ID) ?: -1
        restaurantLat = arguments?.getDouble(ARG_LAT) ?: 39.9
        restaurantLng = arguments?.getDouble(ARG_LNG) ?: 116.4
        defaultBudget = arguments?.getInt(ARG_BUDGET) ?: 80
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetPublishMatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = GanfanRepository.getInstance(requireContext())

        binding.gjBudgetInput.setText(defaultBudget.toString())
        initMealTimePicker()
        binding.gjSubmit.setOnClickListener { submit() }
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
                requireContext(),
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
            requireContext(),
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
        binding.gjMealTimeDisplay.setTextColor(requireContext().getColor(R.color.gj_text_primary))
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
            Toast.makeText(context, "请输入饭局标题", Toast.LENGTH_SHORT).show()
            return
        }
        if (!mealTimeSet) {
            Toast.makeText(context, getString(R.string.gj_publish_time_required), Toast.LENGTH_SHORT).show()
            return
        }
        val maxPeople = parseMaxPeople()
        if (maxPeople == null) {
            Toast.makeText(context, getString(R.string.gj_publish_people_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        val budget = parseBudget()
        if (budget == null) {
            Toast.makeText(context, getString(R.string.gj_publish_budget_invalid), Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateMatchRequest(
            title = title,
            mealTime = buildMealTimeString(),
            maxPeople = maxPeople,
            budget = budget,
            restaurantId = restaurantId,
            lat = restaurantLat,
            lng = restaurantLng
        )

        binding.gjSubmit.isEnabled = false
        lifecycleScope.launch {
            val result = repository.createMatchPost(request)
            binding.gjSubmit.isEnabled = true
            result.onSuccess {
                Toast.makeText(context, "拼桌发布成功！", Toast.LENGTH_SHORT).show()
                onPublished?.invoke()
                dismiss()
            }.onFailure { e ->
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_RESTAURANT_ID = "restaurant_id"
        private const val ARG_LAT = "lat"
        private const val ARG_LNG = "lng"
        private const val ARG_BUDGET = "budget"

        fun newInstance(restaurantId: Long, lat: Double, lng: Double, avgPrice: Int): PublishMatchBottomSheet {
            return PublishMatchBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_RESTAURANT_ID, restaurantId)
                    putDouble(ARG_LAT, lat)
                    putDouble(ARG_LNG, lng)
                    putInt(ARG_BUDGET, avgPrice)
                }
            }
        }
    }
}
