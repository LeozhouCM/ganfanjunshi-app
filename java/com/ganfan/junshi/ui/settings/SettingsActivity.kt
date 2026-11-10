package com.ganfan.junshi.ui.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ganfan.junshi.component.receiver.MealReminderReceiver
import com.ganfan.junshi.databinding.ActivitySettingsBinding
import com.ganfan.junshi.util.applyStatusBarTopPadding
import java.util.Calendar

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopPadding()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "防尬开关"

        binding.gjLocationSwitch.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, if (isChecked) "位置对饭友可见" else "位置已隐藏", Toast.LENGTH_SHORT).show()
        }

        binding.gjReminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) scheduleMealReminder() else Toast.makeText(this, "饭点提醒已关闭", Toast.LENGTH_SHORT).show()
        }

        binding.gjSave.setOnClickListener {
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun scheduleMealReminder() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, MealReminderReceiver::class.java).apply {
            putExtra("title", "午饭时间到")
        }
        val pending = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pending
        )
        Toast.makeText(this, "饭点闹钟已设置（1分钟后提醒）", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
