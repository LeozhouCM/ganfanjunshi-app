package com.ganfan.junshi.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.databinding.ActivityLoginBinding
import com.ganfan.junshi.ui.main.MainActivity
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var repository: GanfanRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopPadding()
        repository = GanfanRepository.getInstance(this)

        if (repository.session.isLoggedIn && !repository.session.userId2.isNullOrBlank()) {
            goMain()
            return
        }

        // Default to login tab
        selectTab(isLogin = true)

        binding.gjBtnTabLogin.setOnClickListener { selectTab(isLogin = true) }
        binding.gjBtnTabRegister.setOnClickListener { selectTab(isLogin = false) }
        binding.gjBtnLogin.setOnClickListener { doLogin() }
        binding.gjBtnRegister.setOnClickListener { doRegister() }
    }

    private fun selectTab(isLogin: Boolean) {
        val primary = ContextCompat.getColor(this, com.ganfan.junshi.R.color.gj_primary)
        val surface = ContextCompat.getColor(this, com.google.android.material.R.color.material_dynamic_neutral90)
        if (isLogin) {
            binding.gjBtnTabLogin.setBackgroundColor(primary)
            binding.gjBtnTabLogin.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            binding.gjBtnTabRegister.setBackgroundColor(surface)
            binding.gjBtnTabRegister.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            binding.gjLoginPanel.visibility = View.VISIBLE
            binding.gjRegisterPanel.visibility = View.GONE
        } else {
            binding.gjBtnTabRegister.setBackgroundColor(primary)
            binding.gjBtnTabRegister.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            binding.gjBtnTabLogin.setBackgroundColor(surface)
            binding.gjBtnTabLogin.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            binding.gjLoginPanel.visibility = View.GONE
            binding.gjRegisterPanel.visibility = View.VISIBLE
        }
    }

    private fun doLogin() {
        val phone = binding.gjEtPhone.text.toString().trim()
        val password = binding.gjEtPassword.text.toString().trim()
        if (phone.length < 11) {
            Toast.makeText(this, "请输入11位手机号", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, "请输入至少6位密码", Toast.LENGTH_SHORT).show()
            return
        }
        if (!binding.gjAgreeCheck.isChecked) {
            Toast.makeText(this, "请先同意用户协议", Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            val result = repository.login(phone, password)
            setLoading(false)
            if (result.isSuccess) {
                goMain()
            } else {
                Toast.makeText(
                    this@LoginActivity,
                    result.exceptionOrNull()?.message ?: "登录失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun doRegister() {
        val phone = binding.gjEtRegPhone.text.toString().trim()
        val password = binding.gjEtRegPassword.text.toString().trim()
        val userId = binding.gjEtRegUserId.text.toString().trim()
        val nickname = binding.gjEtRegNickname.text.toString().trim()
        if (phone.length < 11) {
            Toast.makeText(this, "请输入11位手机号", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, "请输入至少6位密码", Toast.LENGTH_SHORT).show()
            return
        }
        if (userId.isEmpty()) {
            Toast.makeText(this, "请输入你的专属ID", Toast.LENGTH_SHORT).show()
            return
        }
        if (userId.length < 3) {
            Toast.makeText(this, "ID至少3个字符", Toast.LENGTH_SHORT).show()
            return
        }
        if (nickname.isEmpty()) {
            Toast.makeText(this, "请输入昵称", Toast.LENGTH_SHORT).show()
            return
        }
        if (!binding.gjRegAgreeCheck.isChecked) {
            Toast.makeText(this, "请先同意用户协议", Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            val result = repository.register(phone, password, userId, nickname)
            setLoading(false)
            if (result.isSuccess) {
                goMain()
            } else {
                Toast.makeText(this@LoginActivity, "注册失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.gjBtnLogin.isEnabled = !loading
        binding.gjBtnRegister.isEnabled = !loading
        binding.gjProgress.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
