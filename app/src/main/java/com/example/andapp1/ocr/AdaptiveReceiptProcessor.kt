package com.example.andapp1.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

object AdaptiveReceiptProcessor {
    private const val TAG = "AdaptiveReceiptProcessor"
    
    fun processReceiptImage(context: Context, bitmap: Bitmap): Result<String> {
        return try {
            // 고급 적응형 전처리 적용
            val preprocessedBitmap = ImageUtils.processReceiptAdaptively(bitmap)
            Log.d(TAG, "적응형 전처리 완료")
            
            // OCR 처리
            val result = ReceiptOcrProcessor.processReceipt(context, preprocessedBitmap)
            Log.d(TAG, "OCR 처리 완료: $result")
            
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "영수증 처리 중 오류 발생", e)
            // 실패 시 기본 전처리로 재시도
            try {
                Log.d(TAG, "기본 전처리로 재시도")
                val basicProcessed = ImageUtils.processReceiptImage(bitmap)
                val fallbackResult = ReceiptOcrProcessor.processReceipt(context, basicProcessed)
                Result.success(fallbackResult)
            } catch (fallbackException: Exception) {
                Log.e(TAG, "기본 전처리도 실패", fallbackException)
                Result.failure(e)
            }
        }
    }
    
    fun extractAmount(text: String): Int? {
        return ReceiptOcrProcessor.extractTotalAmount(text)
    }
    
    /**
     * 영수증 품질 분석
     */
    fun analyzeReceiptQuality(bitmap: Bitmap): ReceiptQuality {
        return try {
            val imageQuality = ImageUtils.analyzeImageQuality(bitmap)
            
            // 밝기, 선명도, 해상도를 종합적으로 판단
            when {
                imageQuality.resolution < 800 -> ReceiptQuality.LOW_RESOLUTION
                imageQuality.sharpness < 0.1 -> ReceiptQuality.BLURRY
                imageQuality.brightness < 0.3 -> ReceiptQuality.TOO_DARK
                imageQuality.brightness > 0.8 -> ReceiptQuality.TOO_BRIGHT
                else -> ReceiptQuality.GOOD
            }
        } catch (e: Exception) {
            Log.e(TAG, "품질 분석 실패", e)
            ReceiptQuality.UNKNOWN
        }
    }
    
    enum class ReceiptQuality {
        GOOD,
        TOO_DARK,
        TOO_BRIGHT, 
        BLURRY,
        LOW_RESOLUTION,
        UNKNOWN
    }
} 