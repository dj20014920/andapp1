package com.example.andapp1.ocr

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.andapp1.R
import com.example.andapp1.databinding.ActivityOcrBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OCR (광학 문자 인식) 기능을 제공하는 액티비티
 * 카메라 촬영 또는 갤러리에서 이미지를 선택하여 텍스트를 추출
 */
class OcrActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityOcrBinding
    private lateinit var viewModel: OcrViewModel
    private lateinit var tesseractManager: TesseractManager
    
    companion object {
        private const val TAG = "OcrActivity"
        const val EXTRA_ROOM_CODE = "room_code"
        const val EXTRA_EXTRACTED_TEXT = "extracted_text"
        const val EXTRA_EXTRACTED_AMOUNT = "extracted_amount"
    }
    
    // 카메라 촬영 결과 처리
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let { processImage(it) }
        }
    }
    
    // 갤러리 선택 결과 처리
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    processImage(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 갤러리 이미지 로드 실패", e)
                    Toast.makeText(this, "이미지를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOcrBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // ViewModel 초기화
        viewModel = ViewModelProvider(this)[OcrViewModel::class.java]
        
        // Tesseract 매니저 초기화
        tesseractManager = TesseractManager(this)
        
        setupViews()
        observeViewModel()
        initializeTesseract()
    }
    
    private fun setupViews() {
        // 제목 설정
        supportActionBar?.title = "영수증 인식"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 카메라 버튼
        binding.btnCamera.setOnClickListener {
            openCamera()
        }
        
        // 갤러리 버튼
        binding.btnGallery.setOnClickListener {
            openGallery()
        }
        
        // 결과 확인 버튼
        binding.btnConfirm.setOnClickListener {
            confirmResult()
        }
        
        // 재시도 버튼
        binding.btnRetry.setOnClickListener {
            resetForRetry()
        }
    }
    
    private fun observeViewModel() {
        // OCR 결과 관찰
        viewModel.ocrResult.observe(this) { result ->
            binding.tvExtractedText.text = result.extractedText
            binding.tvExtractedAmount.text = "추출된 금액: ${result.extractedAmount}"
            
            // 결과가 있으면 확인 버튼 표시
            if (result.extractedText.isNotEmpty() || result.extractedAmount.isNotEmpty()) {
                binding.layoutResult.visibility = View.VISIBLE
                binding.btnConfirm.visibility = View.VISIBLE
                binding.btnRetry.visibility = View.VISIBLE
            }
        }
        
        // 로딩 상태 관찰
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnCamera.isEnabled = !isLoading
            binding.btnGallery.isEnabled = !isLoading
        }
        
        // 에러 메시지 관찰
        viewModel.errorMessage.observe(this) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun initializeTesseract() {
        lifecycleScope.launch(Dispatchers.IO) {
            val success = tesseractManager.initTesseract()
            
            withContext(Dispatchers.Main) {
                if (success) {
                    Log.d(TAG, "✅ Tesseract 초기화 완료")
                    binding.tvStatus.text = "카메라 또는 갤러리에서 영수증을 선택하세요"
                } else {
                    Log.e(TAG, "❌ Tesseract 초기화 실패")
                    binding.tvStatus.text = "OCR 엔진 초기화에 실패했습니다"
                    Toast.makeText(this@OcrActivity, "OCR 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            cameraLauncher.launch(intent)
        } else {
            Toast.makeText(this, "카메라를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        galleryLauncher.launch(intent)
    }
    
    private fun processImage(bitmap: Bitmap) {
        binding.ivPreview.setImageBitmap(bitmap)
        binding.ivPreview.visibility = View.VISIBLE
        binding.tvStatus.text = "이미지를 분석 중입니다..."
        
        // ViewModel을 통해 OCR 처리
        viewModel.processImage(bitmap, tesseractManager)
    }
    
    private fun confirmResult() {
        val result = viewModel.ocrResult.value
        if (result != null) {
            val intent = Intent().apply {
                putExtra(EXTRA_EXTRACTED_TEXT, result.extractedText)
                putExtra(EXTRA_EXTRACTED_AMOUNT, result.extractedAmount)
            }
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }
    
    private fun resetForRetry() {
        binding.ivPreview.visibility = View.GONE
        binding.layoutResult.visibility = View.GONE
        binding.btnConfirm.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE
        binding.tvStatus.text = "카메라 또는 갤러리에서 영수증을 선택하세요"
        
        viewModel.clearResult()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        tesseractManager.cleanup()
    }
} 