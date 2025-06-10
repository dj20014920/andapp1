package com.example.andapp1.ocr

import android.graphics.*
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * ROI(Region of Interest) 기반 이미지 처리 클래스
 * 선택된 영역만 추출하여 OCR 정확도 극대화
 */
object RoiImageProcessor {
    
    private const val TAG = "RoiImageProcessor"
    
    /**
     * ROI 영역만 추출하여 OCR 최적화 이미지로 변환
     * @param originalBitmap 원본 이미지
     * @param roiRatio ROI 영역 비율 (0.0 ~ 1.0)
     * @return OCR 최적화된 ROI 이미지
     */
    fun extractRoiForOcr(originalBitmap: Bitmap, roiRatio: RectF): Bitmap {
        Log.d(TAG, "ROI 기반 OCR 이미지 추출 시작")
        Log.d(TAG, "원본 이미지: ${originalBitmap.width}x${originalBitmap.height}")
        Log.d(TAG, "ROI 비율: ${roiRatio.toShortString()}")
        
        try {
            // 1. ROI 비율을 실제 픽셀 좌표로 변환
            val roiRect = Rect(
                (roiRatio.left * originalBitmap.width).toInt(),
                (roiRatio.top * originalBitmap.height).toInt(),
                (roiRatio.right * originalBitmap.width).toInt(),
                (roiRatio.bottom * originalBitmap.height).toInt()
            )
            
            // 2. 이미지 경계 내로 제한
            val constrainedRect = Rect(
                max(0, roiRect.left),
                max(0, roiRect.top),
                min(originalBitmap.width, roiRect.right),
                min(originalBitmap.height, roiRect.bottom)
            )
            
            Log.d(TAG, "ROI 영역: ${constrainedRect.toShortString()}")
            
            // 3. ROI 영역 추출
            val roiBitmap = Bitmap.createBitmap(
                originalBitmap,
                constrainedRect.left,
                constrainedRect.top,
                constrainedRect.width(),
                constrainedRect.height()
            )
            
            Log.d(TAG, "ROI 추출 완료: ${roiBitmap.width}x${roiBitmap.height}")
            
            // 4. 금액 인식 특화 전처리 적용
            val optimizedBitmap = optimizeForAmountRecognition(roiBitmap)
            
            Log.d(TAG, "ROI 기반 OCR 이미지 생성 완료")
            return optimizedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "ROI 추출 중 오류 발생", e)
            throw e
        }
    }
    
    /**
     * 금액 인식에 특화된 이미지 최적화
     * @param roiBitmap ROI 영역 이미지
     * @return 금액 인식 최적화 이미지
     */
    private fun optimizeForAmountRecognition(roiBitmap: Bitmap): Bitmap {
        Log.d(TAG, "금액 인식 특화 최적화 시작")
        
        // 1. 해상도 최적화 (Tesseract 권장 크기)
        val optimizedBitmap = optimizeResolution(roiBitmap)
        
        // 2. 기존 ImageUtils의 금액 특화 전처리 적용
        val preprocessedBitmap = ImageUtils.preprocessReceiptForAmount(optimizedBitmap)
        
        Log.d(TAG, "금액 인식 최적화 완료")
        return preprocessedBitmap
    }
    
    /**
     * 해상도 최적화 (Tesseract 4.0 LSTM 권장 크기)
     */
    private fun optimizeResolution(bitmap: Bitmap): Bitmap {
        val currentWidth = bitmap.width
        val currentHeight = bitmap.height
        
        // 금액 인식을 위한 최적 해상도 계산
        val targetWidth = when {
            currentWidth < 800 -> currentWidth * 2    // 저해상도: 2배 확대
            currentWidth < 1200 -> (currentWidth * 1.5).toInt()  // 중해상도: 1.5배 확대
            currentWidth > 2000 -> (currentWidth * 0.8).toInt()  // 고해상도: 축소
            else -> currentWidth  // 적정 해상도 유지
        }
        
        val aspectRatio = currentHeight.toFloat() / currentWidth.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        
        return if (targetWidth != currentWidth || targetHeight != currentHeight) {
            Log.d(TAG, "해상도 최적화: ${currentWidth}x${currentHeight} → ${targetWidth}x${targetHeight}")
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            Log.d(TAG, "해상도 최적화 불필요 (현재 해상도 적정)")
            bitmap
        }
    }
    
    /**
     * ROI 미리보기 이미지 생성 (사용자 확인용)
     * @param originalBitmap 원본 이미지
     * @param roiRatio ROI 영역 비율
     * @return 미리보기 이미지 (ROI 영역 하이라이트)
     */
    fun createRoiPreview(originalBitmap: Bitmap, roiRatio: RectF): Bitmap {
        Log.d(TAG, "ROI 미리보기 이미지 생성 시작")
        
        val previewBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(previewBitmap)
        
        // ROI 영역 계산
        val roiRect = RectF(
            roiRatio.left * originalBitmap.width,
            roiRatio.top * originalBitmap.height,
            roiRatio.right * originalBitmap.width,
            roiRatio.bottom * originalBitmap.height
        )
        
        // 1. 전체 영역을 어둡게
        val overlayPaint = Paint().apply {
            color = Color.parseColor("#80000000")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, previewBitmap.width.toFloat(), previewBitmap.height.toFloat(), overlayPaint)
        
        // 2. ROI 영역을 밝게 (원본 색상)
        val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawRect(roiRect, clearPaint)
        
        // 3. ROI 테두리 그리기
        val borderPaint = Paint().apply {
            color = Color.parseColor("#4CAF50")
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRect(roiRect, borderPaint)
        
        // 4. "OCR 영역" 라벨
        val textPaint = Paint().apply {
            color = Color.parseColor("#4CAF50")
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        
        val labelText = "OCR 영역"
        val textX = roiRect.left + 20f
        val textY = roiRect.top - 20f
        
        if (textY > textPaint.textSize) {
            canvas.drawText(labelText, textX, textY, textPaint)
        }
        
        Log.d(TAG, "ROI 미리보기 이미지 생성 완료")
        return previewBitmap
    }
    
    /**
     * 금액 영역 자동 감지 (향후 AI 기반 확장 가능)
     * @param bitmap 영수증 이미지
     * @return 추천 ROI 영역 비율
     */
    fun detectAmountRegion(bitmap: Bitmap): RectF {
        Log.d(TAG, "금액 영역 자동 감지 시작")
        
        // 현재는 휴리스틱 기반 (향후 AI 모델로 확장 가능)
        // 영수증의 일반적인 금액 위치: 하단 70-80% 지점
        val recommendedRoi = RectF(
            0.1f,   // 좌측 10% 여백
            0.65f,  // 상단 65% 지점부터
            0.9f,   // 우측 10% 여백
            0.85f   // 하단 85% 지점까지
        )
        
        Log.d(TAG, "추천 ROI 영역: ${recommendedRoi.toShortString()}")
        return recommendedRoi
    }
    
    /**
     * ROI 영역 검증 (너무 작거나 큰 영역 방지)
     */
    fun validateRoiRegion(roiRatio: RectF, imageWidth: Int, imageHeight: Int): Boolean {
        val roiWidth = roiRatio.width() * imageWidth
        val roiHeight = roiRatio.height() * imageHeight
        
        // ⭐ OCR 인식률 개선: 최소 크기를 대폭 증가
        val isValid = roiWidth >= 300 && roiHeight >= 150 &&  // 최소 크기 300x150 (기존 100x50)
                     roiRatio.width() <= 1.0f && roiRatio.height() <= 1.0f &&  // 최대 크기
                     roiRatio.left >= 0f && roiRatio.top >= 0f &&  // 경계 확인
                     roiRatio.right <= 1.0f && roiRatio.bottom <= 1.0f
        
        Log.d(TAG, "ROI 영역 검증 결과: $isValid (${roiWidth.toInt()}x${roiHeight.toInt()})")
        
        if (!isValid && (roiWidth < 300 || roiHeight < 150)) {
            Log.w(TAG, "ROI 영역이 너무 작습니다. OCR 인식을 위해 최소 300x150 필요")
        }
        
        return isValid
    }
} 