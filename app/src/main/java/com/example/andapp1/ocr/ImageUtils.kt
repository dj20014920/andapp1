package com.example.andapp1.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object ImageUtils {
    
    fun enhanceImageForOCR(bitmap: Bitmap): Bitmap {
        try {
            // OpenCV Mat으로 변환
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)
            
            // 그레이스케일 변환
            val grayMat = Mat()
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)
            
            // 가우시안 블러로 노이즈 제거
            val blurredMat = Mat()
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)
            
            // 적응형 임계값 적용
            val thresholdMat = Mat()
            Imgproc.adaptiveThreshold(
                blurredMat, thresholdMat,
                255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 11, 2.0
            )
            
            // 결과를 Bitmap으로 변환
            val resultBitmap = Bitmap.createBitmap(
                thresholdMat.cols(),
                thresholdMat.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(thresholdMat, resultBitmap)
            
            // 메모리 해제
            srcMat.release()
            grayMat.release()
            blurredMat.release()
            thresholdMat.release()
            
            return resultBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return bitmap // 실패시 원본 반환
        }
    }
    
    fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val scaleFactor = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }
        
        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    // AdaptiveReceiptProcessor에서 필요한 메서드들
    fun processReceiptAdaptively(bitmap: Bitmap): Bitmap {
        return enhanceImageForOCR(bitmap)
    }
    
    fun processReceiptImage(bitmap: Bitmap): Bitmap {
        return enhanceImageForOCR(bitmap)
    }
    
    fun analyzeImageQuality(bitmap: Bitmap): Float {
        // 간단한 이미지 품질 분석 (0.0 ~ 1.0)
        val width = bitmap.width
        val height = bitmap.height
        val pixels = width * height
        
        // 해상도 기반 품질 점수
        return when {
            pixels > 2000000 -> 1.0f // 고해상도
            pixels > 1000000 -> 0.8f // 중해상도
            pixels > 500000 -> 0.6f  // 저해상도
            else -> 0.4f             // 매우 낮은 해상도
        }
    }
} 