package com.ganfan.junshi.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.databinding.ActivitySplashBinding
import com.ganfan.junshi.ui.login.LoginActivity
import com.ganfan.junshi.ui.main.MainActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - 检查登录状态")
        handler.postDelayed({
            val repo = GanfanRepository.getInstance(this)
            val intent = if (repo.session.isLoggedIn) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(intent)
            finish()
        }, 1500L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    companion object {
        private const val TAG = "SplashActivity"
    }
}
