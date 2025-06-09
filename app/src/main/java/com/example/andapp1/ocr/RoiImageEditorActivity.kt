package com.example.andapp1.ocr

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.andapp1.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 갤러리 이미지 ROI 편집 Activity
 * 확대/축소/드래그 + ROI 선택 기능
 */
class RoiImageEditorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RoiImageEditorActivity"
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_OCR_RESULT = "ocr_result"
        const val EXTRA_ROI_USED = "roi_used"
        const val EXTRA_ROI_IMAGE_PATH = "roi_image_path"
    }

    // UI 컴포넌트
    private lateinit var zoomableImageView: ZoomableImageView
    private lateinit var overlayView: CameraOverlayView
    private lateinit var resetButton: Button
    private lateinit var confirmButton: Button
    private lateinit var guideText: TextView

    // 데이터
    private var imageUri: Uri? = null
    private var originalBitmap: Bitmap? = null
    private var roiBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roi_image_editor)

        Log.d(TAG, "ROI 이미지 편집기 시작")

        initViews()
        setupButtons()
        
        // Intent에서 이미지 URI 받기
        imageUri = intent.getParcelableExtra(EXTRA_IMAGE_URI)
        if (imageUri != null) {
            loadImage(imageUri!!)
        } else {
            Log.e(TAG, "이미지 URI가 전달되지 않음")
            showError("이미지를 불러올 수 없습니다.")
            finish()
        }
    }

    /**
     * UI 초기화
     */
    private fun initViews() {
        zoomableImageView = findViewById(R.id.zoomable_image_view)
        overlayView = findViewById(R.id.overlay_view)
        resetButton = findViewById(R.id.reset_button)
        confirmButton = findViewById(R.id.confirm_button)
        guideText = findViewById(R.id.guide_text)

        // 가이드 텍스트 설정
        guideText.text = "영수증의 금액 부분을 선택하세요\n• 핀치로 확대/축소\n• 드래그로 이동\n• 터치로 영역 선택"
    }

    /**
     * 버튼 설정
     */
    private fun setupButtons() {
        resetButton.setOnClickListener {
            resetRoi()
        }

        confirmButton.setOnClickListener {
            processRoi()
        }

        // 초기에는 확인 버튼 비활성화
        confirmButton.isEnabled = false
        
        // ROI 변경 감지
        overlayView.setOnRoiChangeListener { isValid ->
            confirmButton.isEnabled = isValid
            Log.d(TAG, "ROI 변경됨 - 유효성: $isValid")
        }
    }

    /**
     * 이미지 로드
     */
    private fun loadImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "이미지 로딩 시작: $uri")

                // URI에서 비트맵 로드
                val bitmap = when (uri.scheme) {
                    "content" -> {
                        // 갤러리에서 선택한 이미지
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                    "file" -> {
                        // 파일 경로에서 직접 로드
                        BitmapFactory.decodeFile(uri.path)
                    }
                    else -> {
                        throw IOException("지원하지 않는 URI 스키마: ${uri.scheme}")
                    }
                } ?: throw IOException("비트맵 로딩 실패")

                // 이미지 크기 최적화 (메모리 절약)
                val optimizedBitmap = optimizeBitmap(bitmap)
                
                withContext(Dispatchers.Main) {
                    originalBitmap = optimizedBitmap
                    zoomableImageView.setImageBitmap(optimizedBitmap)
                    
                    Log.d(TAG, "이미지 로딩 완료 - 크기: ${optimizedBitmap.width} x ${optimizedBitmap.height}")
                    Toast.makeText(this@RoiImageEditorActivity, "이미지를 로딩했습니다", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "이미지 로딩 실패", e)
                withContext(Dispatchers.Main) {
                    showError("이미지를 불러올 수 없습니다: ${e.message}")
                    finish()
                }
            }
        }
    }

    /**
     * 이미지 최적화 (대용량 이미지 처리)
     */
    private fun optimizeBitmap(original: Bitmap): Bitmap {
        val maxSize = 2048 // 최대 너비/높이
        
        if (original.width <= maxSize && original.height <= maxSize) {
            return original
        }
        
        val ratio = minOf(
            maxSize.toFloat() / original.width,
            maxSize.toFloat() / original.height
        )
        
        val scaledWidth = (original.width * ratio).toInt()
        val scaledHeight = (original.height * ratio).toInt()
        
        Log.d(TAG, "이미지 최적화: ${original.width}x${original.height} -> ${scaledWidth}x${scaledHeight}")
        
        return Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true).also {
            if (it != original) {
                original.recycle()
            }
        }
    }

    /**
     * ROI 리셋
     */
    private fun resetRoi() {
        overlayView.resetRoi()
        zoomableImageView.resetZoom()
        confirmButton.isEnabled = false
        Log.d(TAG, "ROI 및 줌 리셋")
    }

    /**
     * ROI 처리 및 OCR 실행
     */
    private fun processRoi() {
        val bitmap = originalBitmap ?: return
        
        Log.d(TAG, "ROI 처리 시작")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. ROI 비율 가져오기
                val roiRatio = overlayView.getRoiRatio()
                Log.d(TAG, "ROI 비율: $roiRatio")

                // 2. 이미지 변환 매트릭스 고려
                val adjustedRoiRatio = adjustRoiForImageTransform(roiRatio)
                
                // 3. ROI 영역 유효성 검증
                if (!RoiImageProcessor.validateRoiRegion(adjustedRoiRatio, bitmap.width, bitmap.height)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RoiImageEditorActivity, "선택 영역이 너무 작습니다", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 4. ROI 이미지 추출
                val roiImage = RoiImageProcessor.extractRoiForOcr(bitmap, adjustedRoiRatio)
                roiBitmap = roiImage

                // 5. OCR 처리
                val tesseractManager = TesseractManager.getInstance(this@RoiImageEditorActivity)
                val ocrResult = tesseractManager.performOcr(roiImage)

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "ROI OCR 완료: $ocrResult")

                    // ROI 이미지를 임시 파일로 저장
                    val tempFile = File(cacheDir, "roi_temp_${System.currentTimeMillis()}.jpg")
                    try {
                        tempFile.outputStream().use { out ->
                            roiImage.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        Log.d(TAG, "ROI 이미지 임시 저장: ${tempFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "ROI 이미지 저장 실패", e)
                    }

                    // 결과를 이전 Activity로 전달
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_OCR_RESULT, ocrResult)
                        putExtra(EXTRA_ROI_USED, true)
                        putExtra(EXTRA_ROI_IMAGE_PATH, tempFile.absolutePath)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }

            } catch (e: Exception) {
                Log.e(TAG, "ROI 처리 중 오류", e)
                withContext(Dispatchers.Main) {
                    showError("ROI 처리에 실패했습니다: ${e.message}")
                }
            }
        }
    }

    /**
     * 이미지 변환을 고려한 ROI 비율 조정
     * ZoomableImageView의 확대/이동 상태를 고려
     */
    private fun adjustRoiForImageTransform(roiRatio: RectF): RectF {
        val imageBounds = zoomableImageView.getImageBounds()
        val viewWidth = zoomableImageView.width.toFloat()
        val viewHeight = zoomableImageView.height.toFloat()
        
        // 뷰 좌표에서 이미지 좌표로 변환
        val roiLeft = ((roiRatio.left * viewWidth) - imageBounds.left) / imageBounds.width()
        val roiTop = ((roiRatio.top * viewHeight) - imageBounds.top) / imageBounds.height()
        val roiRight = ((roiRatio.right * viewWidth) - imageBounds.left) / imageBounds.width()
        val roiBottom = ((roiRatio.bottom * viewHeight) - imageBounds.top) / imageBounds.height()
        
        // 0~1 범위로 클램핑
        return RectF(
            roiLeft.coerceIn(0f, 1f),
            roiTop.coerceIn(0f, 1f),
            roiRight.coerceIn(0f, 1f),
            roiBottom.coerceIn(0f, 1f)
        ).also {
            Log.d(TAG, "조정된 ROI: $it")
        }
    }

    /**
     * 오류 메시지 표시
     */
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }

    override fun onDestroy() {
        super.onDestroy()
        originalBitmap?.recycle()
        roiBitmap?.recycle()
        Log.d(TAG, "ROI 이미지 편집기 종료")
    }
} 