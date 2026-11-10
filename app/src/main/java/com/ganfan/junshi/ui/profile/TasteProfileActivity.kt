package com.ganfan.junshi.ui.profile

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.data.local.entity.UserTasteEntity
import com.ganfan.junshi.databinding.ActivityTasteProfileBinding
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class TasteProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTasteProfileBinding
    private lateinit var repository: GanfanRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasteProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopPadding()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.gj_taste_id)

        repository = GanfanRepository.getInstance(this)
        setupSeekBars()
        loadTaste()

        binding.gjSave.setOnClickListener { saveTaste() }
    }

    private fun setupSeekBars() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateLabels()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.gjSpiceSeek.setOnSeekBarChangeListener(listener)
        binding.gjBudgetMinSeek.setOnSeekBarChangeListener(listener)
        binding.gjBudgetMaxSeek.setOnSeekBarChangeListener(listener)
    }

    private fun updateLabels() {
        binding.gjSpiceLabel.text = getString(R.string.gj_spice_level, binding.gjSpiceSeek.progress)
        val min = binding.gjBudgetMinSeek.progress * 5 + 20
        var max = binding.gjBudgetMaxSeek.progress * 10 + 50
        if (max < min) max = min
        binding.gjBudgetLabel.text = getString(R.string.gj_budget_range, min, max)
    }

    private fun loadTaste() {
        lifecycleScope.launch {
            val taste = repository.getUserTaste()
            taste?.let {
                binding.gjSpiceSeek.progress = it.spiceLevel.coerceIn(0, 5)
                binding.gjBudgetMinSeek.progress = ((it.budgetMin - 20) / 5).coerceIn(0, 20)
                binding.gjBudgetMaxSeek.progress = ((it.budgetMax - 50) / 10).coerceIn(0, 30)
                binding.gjCuisinesInput.setText(it.cuisines)
                binding.gjTaboosInput.setText(it.taboos)
            }
            updateLabels()
        }
    }

    private fun saveTaste() {
        val min = binding.gjBudgetMinSeek.progress * 5 + 20
        var max = binding.gjBudgetMaxSeek.progress * 10 + 50
        if (max < min) max = min

        val taste = UserTasteEntity(
            userId = repository.session.userId,
            spiceLevel = binding.gjSpiceSeek.progress,
            budgetMin = min,
            budgetMax = max,
            cuisines = binding.gjCuisinesInput.text.toString().trim().ifBlank { "不限" },
            taboos = binding.gjTaboosInput.text.toString().trim().ifBlank { "无" }
        )

        lifecycleScope.launch {
            repository.saveUserTaste(taste)
            Toast.makeText(this@TasteProfileActivity, "胃的身份证已保存", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
