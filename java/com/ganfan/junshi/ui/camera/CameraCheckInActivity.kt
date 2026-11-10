package com.ganfan.junshi.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.databinding.ActivityCameraCheckinBinding
import com.ganfan.junshi.util.applyStatusBarTopPadding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraCheckInActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraCheckinBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var repository: GanfanRepository
    private var restaurantId: Long = 1

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else Toast.makeText(this, "需要相机权限", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraCheckinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopPadding()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "到此一吃"

        repository = GanfanRepository.getInstance(this)
        restaurantId = intent.getLongExtra("restaurant_id", 1)

        binding.gjCapture.setOnClickListener { takePhoto() }
        binding.gjPickGallery.setOnClickListener { pickFromGallery() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            binding.gjPreview.visibility = android.view.View.VISIBLE
            binding.gjPreview.setImageURI(it)
            saveCheckIn(it.toString())
        }
    }

    private fun pickFromGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.gjPreviewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder().build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "相机启动失败", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val photoFile = File(cacheDir, "checkin_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    binding.gjPreview.visibility = android.view.View.VISIBLE
                    binding.gjPreview.setImageURI(android.net.Uri.fromFile(photoFile))
                    saveCheckIn(photoFile.absolutePath)
                }
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraCheckInActivity, "拍照失败", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun saveCheckIn(imagePath: String) {
        lifecycleScope.launch {
            repository.saveReviewDraft(restaurantId, "到此一吃打卡！", 5, imagePath)
            Toast.makeText(this@CameraCheckInActivity, "打卡成功！", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
