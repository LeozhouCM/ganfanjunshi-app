package com.ganfan.junshi.component.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.ganfan.junshi.Constants

class NetworkChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val connected = intent.getBooleanExtra("is_connected", true)
        val message = if (connected) "网络已恢复，军师重新上线！" else "军师断网了，部分功能暂不可用"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "饭点到了"
        Toast.makeText(context, "$title，该摇人吃饭了！", Toast.LENGTH_LONG).show()
        com.ganfan.junshi.util.NotificationHelper.showMealReminder(context, title)
    }
}

class MatchSuccessReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Constants.ACTION_MATCH_SUCCESS) {
            val postTitle = intent.getStringExtra(Constants.EXTRA_MATCH_TITLE) ?: "拼桌"
            Toast.makeText(context, "匹配成功：$postTitle", Toast.LENGTH_LONG).show()
        }
    }
}
