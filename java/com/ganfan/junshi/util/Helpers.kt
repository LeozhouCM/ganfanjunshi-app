package com.ganfan.junshi.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ganfan.junshi.Constants
import com.ganfan.junshi.R
import com.ganfan.junshi.ui.main.MainActivity

object NotificationHelper {

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(Constants.CHANNEL_MATCH, "拼桌匹配", NotificationManager.IMPORTANCE_DEFAULT)
            )
            manager.createNotificationChannel(
                NotificationChannel(Constants.CHANNEL_MEAL, "饭点提醒", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun buildMatchNotification(context: Context, title: String) =
        baseBuilder(context, Constants.CHANNEL_MATCH)
            .setContentTitle("秒凑一桌")
            .setContentText("正在匹配：$title")
            .build()

    fun buildLocationNotification(context: Context) =
        baseBuilder(context, Constants.CHANNEL_MATCH)
            .setContentTitle("位置上报中")
            .setContentText("拼桌进行中，正在共享位置")
            .build()

    fun showMealReminder(context: Context, title: String) {
        val notification = baseBuilder(context, Constants.CHANNEL_MEAL)
            .setContentTitle("饭点闹钟")
            .setContentText(title)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(Constants.NOTIFICATION_MEAL_ID, notification)
    }

    private fun baseBuilder(context: Context, channelId: String): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true)
    }
}

object NetworkMonitor {
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
