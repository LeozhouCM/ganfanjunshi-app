package com.ganfan.junshi.ui.profile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ganfan.junshi.R
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.databinding.ActivityProfileEditBinding
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch

class ProfileEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding
    private lateinit var repository: GanfanRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopPadding()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.gj_profile_archive)

        repository = GanfanRepository.getInstance(this)
        loadUser()

        binding.gjSave.setOnClickListener { saveProfile() }
    }

    private fun loadUser() {
        lifecycleScope.launch {
            val user = repository.getCurrentUser()
            user?.let {
                binding.gjNicknameInput.setText(it.nickname)
                binding.gjBioInput.setText(it.bio ?: "")
                binding.gjCityInput.setText(it.city ?: "")
                binding.gjRealNameInput.setText(it.realName ?: "")
                val realNameText = if (it.realNameVerified) {
                    getString(R.string.gj_real_name_verified)
                } else {
                    getString(R.string.gj_real_name_unverified)
                }
                binding.gjCreditInfo.text = buildString {
                    append(getString(R.string.gj_credit_title))
                    append("：")
                    append(it.creditScore)
                    append("\n")
                    append(getString(R.string.gj_credit_detail_format, it.attendanceRate, it.lateRate, it.blockCount))
                    append("\n")
                    append(realNameText)
                }
            }
        }
    }

    private fun saveProfile() {
        val nickname = binding.gjNicknameInput.text.toString().trim()
        if (nickname.isEmpty()) {
            Toast.makeText(this, "昵称不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = repository.updateUserProfile(
                nickname = nickname,
                bio = binding.gjBioInput.text.toString().trim().ifBlank { null },
                city = binding.gjCityInput.text.toString().trim().ifBlank { null },
                realName = binding.gjRealNameInput.text.toString().trim().ifBlank { null }
            )
            result.onSuccess {
                Toast.makeText(this@ProfileEditActivity, "干饭档案已保存", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }.onFailure {
                Toast.makeText(this@ProfileEditActivity, it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
