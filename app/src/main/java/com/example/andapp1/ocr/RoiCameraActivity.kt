package com.example.andapp1.ocr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.andapp1.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ROI(Region of Interest) 기반 카메라 Activity
 * 2025년 최신 CameraX + 오버레이 통합 구현
 */
class RoiCameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RoiCameraActivity"
    }

    // UI 컴포넌트
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: CameraOverlayView
    private lateinit var captureButton: Button
    private lateinit var confirmButton: Button
    private lateinit var resetButton: Button
    private lateinit var previewImageView: ImageView

    // CameraX 컴포넌트
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null

    // 상태 관리
    private var currentRoi: RectF = RectF()
    private var capturedBitmap: Bitmap? = null
    private var isPreviewMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roi_camera)
        
        Log.d(TAG, "ROI 카메라 Activity 시작")
        
        initializeViews()
        setupOverlayListener()
        startCamera()
    }

    /**
     * View 초기화
     */
    private fun initializeViews() {
        previewView = findViewById(R.id.preview_view)
        overlayView = findViewById(R.id.overlay_view)
        captureButton = findViewById(R.id.btn_capture)
        confirmButton = findViewById(R.id.btn_confirm)
        resetButton = findViewById(R.id.btn_reset)
        previewImageView = findViewById(R.id.preview_image)

        // 버튼 리스너 설정
        captureButton.setOnClickListener { capturePhoto() }
        confirmButton.setOnClickListener { processOcr() }
        resetButton.setOnClickListener { resetCapture() }

        // 초기 상태 설정
        confirmButton.isEnabled = false
        previewImageView.visibility = ImageView.GONE
        
        Log.d(TAG, "View 초기화 완료")
    }

    /**
     * 오버레이 리스너 설정
     */
    private fun setupOverlayListener() {
        overlayView.setOnRoiChangedListener { roi ->
            currentRoi = roi
            Log.d(TAG, "ROI 영역 변경됨: ${roi.toShortString()}")
        }
    }

    /**
     * CameraX 카메라 시작
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
                Log.d(TAG, "카메라 시작 성공")
            } catch (e: Exception) {
                Log.e(TAG, "카메라 시작 실패", e)
                Toast.makeText(this, "카메라 시작에 실패했습니다", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * 카메라 Use Case 바인딩
     */
    private fun bindCameraUseCases() {
        val cameraProvider = this.cameraProvider ?: return

        // Preview 설정
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // ImageCapture 설정 (ROI 기반 최적화)
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()

        // 카메라 선택 (후면 카메라 우선)
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // 기존 바인딩 해제
            cameraProvider.unbindAll()

            // 카메라 바인딩
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
            )

            Log.d(TAG, "카메라 Use Case 바인딩 완료")
        } catch (e: Exception) {
            Log.e(TAG, "카메라 바인딩 실패", e)
            Toast.makeText(this, "카메라 설정에 실패했습니다", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 사진 촬영
     */
    private fun capturePhoto() {
        val imageCapture = this.imageCapture ?: return

        Log.d(TAG, "사진 촬영 시작")
        
        // 임시 파일 생성
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File(externalCacheDir, "roi_capture_$timestamp.jpg")
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "사진 저장 성공: ${imageFile.absolutePath}")
                    lifecycleScope.launch {
                        processRoiImage(imageFile)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "사진 촬영 실패", exception)
                    Toast.makeText(
                        this@RoiCameraActivity,
                        "사진 촬영에 실패했습니다",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    /**
     * ROI 이미지 처리
     */
    private suspend fun processRoiImage(imageFile: File) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "ROI 이미지 처리 시작")

                // 1. 이미지 로드
                val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    ?: throw Exception("이미지 로딩 실패")

                // 2. ROI 비율 가져오기
                val roiRatio = overlayView.getRoiRatio()

                // 3. ROI 영역 유효성 검증
                if (!RoiImageProcessor.validateRoiRegion(roiRatio, originalBitmap.width, originalBitmap.height)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RoiCameraActivity, "선택 영역이 너무 작습니다. 다시 선택해주세요.", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }

                // 4. ROI 기반 OCR 이미지 추출
                val roiImage = RoiImageProcessor.extractRoiForOcr(originalBitmap, roiRatio)

                // 5. 미리보기 이미지 생성
                val previewImage = RoiImageProcessor.createRoiPreview(originalBitmap, roiRatio)

                withContext(Dispatchers.Main) {
                    // UI 업데이트
                    capturedBitmap = roiImage
                    previewImageView.setImageBitmap(previewImage)
                    previewImageView.visibility = ImageView.VISIBLE
                    
                    // 버튼 상태 변경
                    confirmButton.isEnabled = true
                    captureButton.text = "다시 촬영"
                    isPreviewMode = true

                    Toast.makeText(this@RoiCameraActivity, "ROI 영역이 추출되었습니다", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "ROI 이미지 처리 완료")
                }

            } catch (e: Exception) {
                Log.e(TAG, "ROI 이미지 처리 중 오류", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RoiCameraActivity, "이미지 처리에 실패했습니다", Toast.LENGTH_SHORT).show()
                }
            } finally {
                // 임시 파일 삭제
                if (imageFile.exists()) {
                    imageFile.delete()
                }
            }
        }
    }

    /**
     * OCR 처리 및 결과 전달
     */
    private fun processOcr() {
        val bitmap = capturedBitmap ?: return

        Log.d(TAG, "ROI 기반 OCR 처리 시작")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // TesseractManager를 통한 OCR 수행
                val tesseractManager = TesseractManager.getInstance(this@RoiCameraActivity)
                val ocrResult = tesseractManager.performOcr(bitmap)

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "ROI OCR 결과: $ocrResult")

                    // 결과를 이전 Activity로 전달
                    val resultIntent = Intent().apply {
                        putExtra("ocr_result", ocrResult)
                        putExtra("roi_used", true)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }

            } catch (e: Exception) {
                Log.e(TAG, "OCR 처리 중 오류", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RoiCameraActivity, "OCR 처리에 실패했습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 촬영 리셋
     */
    private fun resetCapture() {
        capturedBitmap = null
        previewImageView.visibility = ImageView.GONE
        confirmButton.isEnabled = false
        captureButton.text = "촬영"
        isPreviewMode = false
        
        // ROI 영역 리셋
        overlayView.resetRoi()
        
        Log.d(TAG, "촬영 상태 리셋 완료")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        capturedBitmap?.recycle()
        Log.d(TAG, "ROI 카메라 Activity 종료")
    }
} 