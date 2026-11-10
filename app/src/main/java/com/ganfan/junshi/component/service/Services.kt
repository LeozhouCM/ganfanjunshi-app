package com.ganfan.junshi.component.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.ganfan.junshi.Constants
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.util.NotificationHelper
import kotlinx.coroutines.*

class MatchForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var matchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val postId = intent?.getLongExtra("post_id", -1L) ?: -1L
        val title = intent?.getStringExtra("title") ?: "秒凑一桌"

        startForeground(Constants.NOTIFICATION_MATCH_ID, NotificationHelper.buildMatchNotification(this, title))

        matchJob?.cancel()
        matchJob = scope.launch {
            for (attempt in 0 until 5) {
                delay(2000L)
                Log.d(TAG, "匹配轮询 #$attempt postId=$postId")
                if (attempt >= 2) {
                    val broadcast = Intent(Constants.ACTION_MATCH_SUCCESS).apply {
                        putExtra(Constants.EXTRA_MATCH_POST_ID, postId)
                        putExtra(Constants.EXTRA_MATCH_TITLE, title)
                        setPackage(packageName)
                    }
                    sendBroadcast(broadcast)
                    break
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        matchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MatchForegroundService"
    }
}

class LocationReportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reportJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_MATCH_ID + 1,
            NotificationHelper.buildLocationNotification(this))

        reportJob?.cancel()
        reportJob = scope.launch {
            repeat(3) {
                Log.d(TAG, "上报位置 lat=39.9 lng=116.4")
                delay(5000L)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        reportJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocationReportService"
    }
}

class AiReplyFetchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra("message") ?: ""
        val callbackAction = intent?.getStringExtra("callback_action")

        scope.launch {
            val repo = GanfanRepository.getInstance(applicationContext)
            val reply = repo.aiChat(message).getOrDefault("军师暂时离线")
            val broadcast = Intent(callbackAction ?: Constants.ACTION_MATCH_SUCCESS).apply {
                putExtra("ai_reply", reply)
                setPackage(packageName)
            }
            sendBroadcast(broadcast)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
