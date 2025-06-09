package com.example.andapp1.ocr

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/**
 * OpenCV를 사용한 이미지 전처리 유틸리티 클래스
 * 영수증 금액/합계 영역 검출 및 회전 보정에 특화
 */
object ImageUtils {
    
    private const val TAG = "ImageUtils"
    
    /**
     * 영수증 금액 영역 특화 전처리 파이프라인 (2024 웹서핑 개선 적용)
     * 59,500 → 163,292 오인식 문제 해결 특화
     * @param bitmap 원본 이미지
     * @return Tesseract 4.0 LSTM 최적화 전처리 이미지
     */
    fun preprocessReceiptForAmount(bitmap: Bitmap): Bitmap {
        Log.d(TAG, "영수증 전처리 시작 - 2024 숫자 인식 최적화: ${bitmap.width}x${bitmap.height}")
        
        // 1. Bitmap을 Mat으로 변환
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)
        Log.d(TAG, "Bitmap → Mat 변환 완료")
        
        // 2. 그레이스케일 변환
        val grayMat = convertToGrayscale(srcMat)
        Log.d(TAG, "그레이스케일 변환 완료")
        
        // ⭐ 핵심 개선 1: Tesseract 4.0 LSTM 최적 해상도 (웹서핑 권장)
        val resizedMat = optimizeForTesseract4LSTM(grayMat)
        Log.d(TAG, "Tesseract 4.0 해상도 최적화 완료: ${resizedMat.cols()}x${resizedMat.rows()}")
        
        // ⭐ 핵심 개선 2: 숫자 인식 특화 노이즈 제거 (웹서핑 연구 결과)
        val denoisedMat = applyNumberFocusedDenoising(resizedMat)
        Log.d(TAG, "숫자 특화 노이즈 제거 완료")
        
        // ⭐ 핵심 개선 3: Tesseract 4.0 특화 이진화 (웹서핑 권장)
        val binaryMat = tesseract4OptimizedBinarization(denoisedMat)
        Log.d(TAG, "Tesseract 4.0 특화 이진화 완료")
        
        // ⭐ 핵심 개선 4: 숫자 형태 최적화 (웹서핑 권장)
        val optimizedMat = optimizeNumberShapes(binaryMat)
        Log.d(TAG, "숫자 형태 최적화 완료")
        
        // ⭐ 핵심 개선 5: 경계 여백 추가 (웹서핑 권장)
        val finalMat = addOptimalBorders(optimizedMat)
        Log.d(TAG, "경계 여백 추가 완료")
        
        // 6. Mat을 Bitmap으로 변환
        val resultBitmap = Bitmap.createBitmap(
            finalMat.cols(),
            finalMat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(finalMat, resultBitmap)
        
        // 7. 메모리 해제
        srcMat.release()
        grayMat.release()
        resizedMat.release()
        denoisedMat.release()
        binaryMat.release()
        optimizedMat.release()
        finalMat.release()
        
        Log.d(TAG, "영수증 전처리 완료 - 59,500원 오인식 문제 해결 특화")
        return resultBitmap
    }
    
    /**
     * Tesseract 4.0 LSTM 엔진 최적 해상도 조정 (웹서핑 권장사항)
     * 텍스트 높이 20-40픽셀이 최적
     */
    private fun optimizeForTesseract4LSTM(src: Mat): Mat {
        val height = src.rows()
        val width = src.cols()
        
        // 웹서핑 권장: 텍스트 크기 기준 최적 해상도 계산
        val targetScale = when {
            width < 1500 -> 2.0    // 저해상도: 크게 확대
            width < 2500 -> 1.5    // 중해상도: 적당히 확대  
            width < 3500 -> 1.2    // 고해상도: 약간 확대
            else -> 1.0            // 초고해상도: 유지
        }
        
        val newWidth = (width * targetScale).toInt()
        val newHeight = (height * targetScale).toInt()
        
        val resized = Mat()
        // 웹서핑 권장: INTER_CUBIC (고품질 보간)
        Imgproc.resize(src, resized, Size(newWidth.toDouble(), newHeight.toDouble()),
            0.0, 0.0, Imgproc.INTER_CUBIC)
        
        Log.d(TAG, "Tesseract 4.0 해상도 최적화: ${width}x${height} → ${newWidth}x${newHeight}")
        return resized
    }
    
    /**
     * 숫자 인식 특화 노이즈 제거 (웹서핑 연구 결과)
     */
    private fun applyNumberFocusedDenoising(src: Mat): Mat {
        val denoised = Mat()
        
        // 웹서핑 권장: 메디안 필터 (숫자 형태 보존하며 노이즈 제거)
        Imgproc.medianBlur(src, denoised, 3)
        
        // 추가: 가우시안 블러 (미세 조정)
        val gaussian = Mat()
        Imgproc.GaussianBlur(denoised, gaussian, Size(3.0, 3.0), 0.8)
        
        Log.d(TAG, "숫자 특화 노이즈 제거 적용")
        return gaussian
    }
    
    /**
     * Tesseract 4.0 LSTM 엔진 특화 이진화 (웹서핑 권장)
     */
    private fun tesseract4OptimizedBinarization(src: Mat): Mat {
        val binary = Mat()
        
        // 웹서핑 권장: 적응형 가우시안 임계값 (LSTM 엔진에 최적)
        Imgproc.adaptiveThreshold(src, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
            11, 5.0)  // 웹서핑 권장 파라미터
        
        Log.d(TAG, "Tesseract 4.0 특화 이진화 적용")
        return binary
    }
    
    /**
     * 숫자 형태 최적화 (웹서핑 권장: 형태학적 변환)
     */
    private fun optimizeNumberShapes(src: Mat): Mat {
        val optimized = Mat()
        
        // 웹서핑 권장: Opening 연산 (잡음 제거하며 숫자 형태 보존)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(2.0, 2.0))
        Imgproc.morphologyEx(src, optimized, Imgproc.MORPH_OPEN, kernel)
        
        Log.d(TAG, "숫자 형태 최적화 적용")
        return optimized
    }
    
    /**
     * 경계 여백 추가 (웹서핑 권장: 10-20px 여백)
     */
    private fun addOptimalBorders(src: Mat): Mat {
        val bordered = Mat()
        
        // 웹서핑 권장: 15px 여백 (Tesseract 4.0 최적)
        Core.copyMakeBorder(src, bordered, 15, 15, 15, 15,
            Core.BORDER_CONSTANT, Scalar(255.0, 255.0, 255.0))
        
        Log.d(TAG, "최적 경계 여백 추가: 15px")
        return bordered
    }
    
    /**
     * 자동 회전 감지 및 보정
     * 영수증이 가로/세로/거꾸로 찍혀도 자동으로 올바른 방향으로 보정
     */
    private fun detectAndCorrectOrientation(src: Mat): Mat {
        try {
            Log.d(TAG, "회전 감지 시작")
            
            // 1. 그레이스케일 변환
            val gray = Mat()
            if (src.channels() > 1) {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
            } else {
                src.copyTo(gray)
            }
            
            // 2. 이진화
            val binary = Mat()
            Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            
            // 3. 텍스트 라인 검출을 위한 엣지 검출
            val edges = Mat()
            Imgproc.Canny(binary, edges, 50.0, 150.0, 3)
            
            // 4. 허프 라인 변환으로 주요 라인 검출
            val lines = Mat()
            Imgproc.HoughLines(edges, lines, 1.0, Math.PI/180, 80)
            
            // 5. 각도 분석
            val angles = mutableListOf<Double>()
            
            if (lines.rows() > 0) {
                for (i in 0 until min(lines.rows(), 30)) {
                    val line = lines.get(i, 0)
                    val theta = line[1]
                    val angleDegrees = theta * 180.0 / Math.PI
                    
                    // 0도, 90도, 180도, 270도 근처의 각도만 고려
                    val normalizedAngle = when {
                        angleDegrees < 45 -> angleDegrees
                        angleDegrees < 135 -> angleDegrees - 90
                        angleDegrees < 225 -> angleDegrees - 180
                        else -> angleDegrees - 270
                    }
                    
                    if (abs(normalizedAngle) < 45) {
                        angles.add(normalizedAngle)
                    }
                }
            }
            
            // 6. 최적 회전각 결정
            val rotationAngle = if (angles.isNotEmpty()) {
                val avgAngle = angles.average()
                Log.d(TAG, "감지된 평균 각도: ${String.format("%.2f", avgAngle)}도")
                
                // 15도 이상 기울어진 경우만 보정
                if (abs(avgAngle) > 15) {
                    -avgAngle // 반대 방향으로 회전
                } else {
                    0.0
                }
            } else {
                Log.d(TAG, "각도 감지 실패, 원본 유지")
                0.0
            }
            
            // 7. 추가 방향 검증 (텍스트 밀도 기반)
            val finalAngle = verifyOrientationByTextDensity(binary, rotationAngle)
            
            // 8. 회전 적용
            val result = if (abs(finalAngle) > 1.0) {
                Log.d(TAG, "회전 적용: ${String.format("%.2f", finalAngle)}도")
                rotateImage(src, finalAngle)
            } else {
                Log.d(TAG, "회전 불필요")
                val dst = Mat()
                src.copyTo(dst)
                dst
            }
            
            // 메모리 해제
            gray.release()
            binary.release()
            edges.release()
            lines.release()
            
            return result
            
        } catch (e: Exception) {
            Log.w(TAG, "회전 감지 중 오류 발생, 원본 사용: ${e.message}")
            val dst = Mat()
            src.copyTo(dst)
            return dst
        }
    }
    
    /**
     * 텍스트 밀도를 기반으로 올바른 방향 검증
     */
    private fun verifyOrientationByTextDensity(binary: Mat, currentAngle: Double): Double {
        val angles = listOf(0.0, 90.0, 180.0, 270.0)
        var bestAngle = currentAngle
        var maxDensity = 0.0
        
        for (angle in angles) {
            val rotated = if (angle != 0.0) rotateImage(binary, angle) else binary
            val density = calculateTextDensity(rotated)
            
            Log.d(TAG, "각도 ${angle}도에서 텍스트 밀도: $density")
            
            if (density > maxDensity) {
                maxDensity = density
                bestAngle = angle
            }
            
            if (angle != 0.0) rotated.release()
        }
        
        Log.d(TAG, "최적 방향: ${bestAngle}도 (밀도: $maxDensity)")
        return bestAngle
    }
    
    /**
     * 텍스트 밀도 계산 (가로 라인의 연속성 측정)
     */
    private fun calculateTextDensity(binary: Mat): Double {
        val height = binary.rows()
        val width = binary.cols()
        
        var horizontalLines = 0
        val sampleRows = min(height, 50) // 최대 50개 행만 샘플링
        
        for (y in 0 until height step (height / sampleRows)) {
            var whitePixels = 0
            var blackPixels = 0
            
            for (x in 0 until width) {
                val pixel = binary.get(y, x)[0]
                if (pixel > 128) whitePixels++ else blackPixels++
            }
            
            // 텍스트 라인으로 판단되는 조건: 흑백 픽셀이 적절히 섞여있음
            val ratio = blackPixels.toDouble() / (whitePixels + blackPixels)
            if (ratio in 0.1..0.6) {
                horizontalLines++
            }
        }
        
        return horizontalLines.toDouble() / sampleRows
    }
    
    /**
     * 영수증에서 금액/합계 영역 검출 및 추출
     */
    private fun extractAmountRegion(src: Mat): Mat {
        try {
            Log.d(TAG, "금액 영역 검출 시작")
            
            // 1. 그레이스케일 변환
            val gray = Mat()
            if (src.channels() > 1) {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
            } else {
                src.copyTo(gray)
            }
            
            // 2. 텍스트 영역 검출을 위한 전처리
            val binary = Mat()
            Imgproc.adaptiveThreshold(gray, binary, 255.0, 
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 4.0)
            
            // 3. 수직 라인 제거 (표 구조 제거)
            val horizontalKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(40.0, 1.0))
            val withoutVertical = Mat()
            Imgproc.morphologyEx(binary, withoutVertical, Imgproc.MORPH_OPEN, horizontalKernel)
            
            // 4. 텍스트 블록 확장
            val textKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(20.0, 3.0))
            val expandedText = Mat()
            Imgproc.morphologyEx(withoutVertical, expandedText, Imgproc.MORPH_CLOSE, textKernel)
            
            // 5. 컨투어 검출
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(expandedText, contours, hierarchy, 
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // 6. 금액 영역으로 추정되는 영역 필터링
            val candidates = contours.filter { contour ->
                val rect = Imgproc.boundingRect(contour)
                val area = Imgproc.contourArea(contour)
                
                // 조건: 적절한 크기, 가로로 긴 형태, 하단 부분에 위치
                area > 500 && 
                rect.width > rect.height && 
                rect.width > src.cols() * 0.3 && 
                rect.y > src.rows() * 0.4  // 영수증 하단 60% 영역
            }
            
            // 7. 최적 영역 선택 (가장 하단의 큰 영역)
            val bestRegion = candidates.maxByOrNull { contour ->
                val rect = Imgproc.boundingRect(contour)
                rect.y + rect.height * 2 // 하단 우선 + 크기 고려
            }
            
            val result = if (bestRegion != null) {
                val rect = Imgproc.boundingRect(bestRegion)
                
                // 영역 확장 (여백 추가)
                val expandedRect = Rect(
                    max(0, rect.x - 20),
                    max(0, rect.y - 10),
                    min(src.cols() - max(0, rect.x - 20), rect.width + 40),
                    min(src.rows() - max(0, rect.y - 10), rect.height + 20)
                )
                
                Log.d(TAG, "금액 영역 검출 성공: ${expandedRect.x}, ${expandedRect.y}, ${expandedRect.width}x${expandedRect.height}")
                
                val roi = Mat(src, expandedRect)
                val roiCopy = Mat()
                roi.copyTo(roiCopy)
                roiCopy
            } else {
                Log.d(TAG, "금액 영역 검출 실패, 하단 50% 영역 사용")
                
                // 검출 실패 시 영수증 하단 50% 영역 사용
                val fallbackRect = Rect(0, src.rows() / 2, src.cols(), src.rows() / 2)
                val roi = Mat(src, fallbackRect)
                val roiCopy = Mat()
                roi.copyTo(roiCopy)
                roiCopy
            }
            
            // 메모리 해제
            gray.release()
            binary.release()
            withoutVertical.release()
            expandedText.release()
            hierarchy.release()
            horizontalKernel.release()
            textKernel.release()
            contours.forEach { it.release() }
            
            return result
            
        } catch (e: Exception) {
            Log.w(TAG, "금액 영역 검출 중 오류, 원본 사용: ${e.message}")
            val dst = Mat()
            src.copyTo(dst)
            return dst
        }
    }
    
    /**
     * OCR에 최적화된 크기로 이미지 조정
     */
    private fun resizeForOcr(src: Mat): Mat {
        val dst = Mat()
        val originalWidth = src.cols()
        val originalHeight = src.rows()
        
        // OCR에 적합한 최소 폭: 800px, 최대 폭: 1600px (금액 영역은 더 작게)
        val targetWidth = when {
            originalWidth < 800 -> 800
            originalWidth > 1600 -> 1600
            else -> originalWidth
        }
        
        if (targetWidth != originalWidth) {
            val scale = targetWidth.toDouble() / originalWidth
            val targetHeight = (originalHeight * scale).toInt()
            
            Imgproc.resize(src, dst, 
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
                0.0, 0.0, Imgproc.INTER_CUBIC)
            
            Log.d(TAG, "이미지 리사이즈: ${originalWidth}x${originalHeight} → ${targetWidth}x${targetHeight}")
        } else {
            src.copyTo(dst)
        }
        
        return dst
    }
    
    /**
     * 그레이스케일 변환
     */
    private fun convertToGrayscale(src: Mat): Mat {
        val dst = Mat()
        if (src.channels() > 1) {
            Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGR2GRAY)
        } else {
            src.copyTo(dst)
        }
        return dst
    }
    
    /**
     * 숫자 인식을 위한 특화된 노이즈 제거
     */
    private fun removeNoiseForNumbers(src: Mat): Mat {
        val temp = Mat()
        val dst = Mat()
        
        // 1차: 작은 노이즈 제거 (숫자 보존)
        Imgproc.GaussianBlur(src, temp, Size(3.0, 3.0), 0.0)
        
        // 2차: 미디언 필터 (점 노이즈 제거, 숫자 형태 보존)
        Imgproc.medianBlur(temp, dst, 3)
        
        temp.release()
        Log.d(TAG, "숫자 특화 노이즈 제거 적용")
        return dst
    }
    
    /**
     * 숫자 인식을 위한 특화된 이진화
     */
    private fun applyAdaptiveThresholdForNumbers(src: Mat): Mat {
        val dst = Mat()
        
        // 숫자 인식에 최적화된 파라미터
        Imgproc.adaptiveThreshold(
            src, dst,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            9,    // 작은 블록 크기 (숫자 세부사항 보존)
            3.0   // 낮은 C값 (더 민감한 임계값)
        )
        
        Log.d(TAG, "숫자 특화 적응형 이진화 적용")
        return dst
    }
    
    /**
     * 숫자 품질 개선을 위한 모폴로지 연산
     */
    private fun applyMorphologyForNumbers(src: Mat): Mat {
        val temp = Mat()
        val dst = Mat()
        
        // 1. 작은 노이즈 제거 (숫자 형태 보존)
        val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(2.0, 2.0))
        Imgproc.morphologyEx(src, temp, Imgproc.MORPH_OPEN, openKernel)
        
        // 2. 숫자 내부 구멍 메우기
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        Imgproc.morphologyEx(temp, dst, Imgproc.MORPH_CLOSE, closeKernel)
        
        // 메모리 해제
        temp.release()
        openKernel.release()
        closeKernel.release()
        
        Log.d(TAG, "숫자 특화 모폴로지 연산 적용")
        return dst
    }
    
    /**
     * 이미지 회전
     */
    private fun rotateImage(src: Mat, angleDegrees: Double): Mat {
        val center = Point(src.cols() / 2.0, src.rows() / 2.0)
        val rotationMatrix = Imgproc.getRotationMatrix2D(center, angleDegrees, 1.0)
        
        val dst = Mat()
        Imgproc.warpAffine(src, dst, rotationMatrix, src.size())
        
        rotationMatrix.release()
        return dst
    }
    
    /**
     * 간단한 리사이즈 (OCR 최적화)
     */
    private fun simpleResizeForOcr(src: Mat): Mat {
        val dst = Mat()
        val originalWidth = src.cols()
        val originalHeight = src.rows()
        
        // OCR에 적합한 크기: 1500-3000px 범위 (더 큰 크기로)
        val targetWidth = when {
            originalWidth < 1500 -> 1500
            originalWidth > 3000 -> 3000
            else -> originalWidth
        }
        
        if (targetWidth != originalWidth) {
            val scale = targetWidth.toDouble() / originalWidth
            val targetHeight = (originalHeight * scale).toInt()
            
            Imgproc.resize(src, dst, 
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
                0.0, 0.0, Imgproc.INTER_CUBIC)
            
            Log.d(TAG, "이미지 리사이즈: ${originalWidth}x${originalHeight} → ${targetWidth}x${targetHeight}")
        } else {
            src.copyTo(dst)
        }
        
        return dst
    }
    
    /**
     * 효과적인 노이즈 제거 (영수증 텍스트 보존)
     */
    private fun lightDenoising(src: Mat): Mat {
        val temp = Mat()
        val dst = Mat()
        
        // 1단계: 가벼운 가우시안 블러
        Imgproc.GaussianBlur(src, temp, Size(3.0, 3.0), 0.0)
        
        // 2단계: 미디언 필터로 점 노이즈 제거 (텍스트 보존)
        Imgproc.medianBlur(temp, dst, 5)
        
        temp.release()
        Log.d(TAG, "효과적인 노이즈 제거 적용")
        return dst
    }
    
    /**
     * 부드러운 이진화 (영수증에 최적화)
     */
    private fun simpleThresholding(src: Mat): Mat {
        val dst = Mat()
        
        // 적응형 임계값 사용 (균형잡힌 설정)
        Imgproc.adaptiveThreshold(
            src, dst,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            13,    // 중간 블록 크기 (적절한 균형)
            7.0    // 중간 C 값 (적절한 민감도)
        )
        
        Log.d(TAG, "부드러운 적응형 이진화 적용")
        return dst
    }
    
    /**
     * 고해상도 이미지를 위한 해상도 최적화
     */
    private fun optimizeResolutionForOcr(src: Mat): Mat {
        val dst = Mat()
        val originalWidth = src.cols()
        val originalHeight = src.rows()
        
        // 고해상도 OCR에 적합한 크기: 2000-3000px 범위
        val targetWidth = when {
            originalWidth < 1000 -> 2000   // 너무 작으면 확대
            originalWidth > 4000 -> 3000   // 너무 크면 축소
            else -> originalWidth          // 적절한 크기면 유지
        }
        
        if (targetWidth != originalWidth) {
            val scale = targetWidth.toDouble() / originalWidth
            val targetHeight = (originalHeight * scale).toInt()
            
            Imgproc.resize(src, dst, 
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
                0.0, 0.0, Imgproc.INTER_CUBIC)
            
            Log.d(TAG, "해상도 최적화: ${originalWidth}x${originalHeight} → ${targetWidth}x${targetHeight}")
        } else {
            src.copyTo(dst)
            Log.d(TAG, "해상도 적절함: ${originalWidth}x${originalHeight}")
        }
        
        return dst
    }
    
    /**
     * 양방향 필터 (엣지 보존하며 노이즈 제거)
     */
    private fun bilateralFilter(src: Mat): Mat {
        val dst = Mat()
        
        // 양방향 필터: 엣지를 보존하면서 노이즈 제거
        Imgproc.bilateralFilter(src, dst, 9, 80.0, 80.0)
        
        Log.d(TAG, "양방향 필터 적용 완료")
        return dst
    }
    
    /**
     * 고해상도 이미지에 적합한 적응형 이진화
     */
    private fun adaptiveThresholding(src: Mat): Mat {
        val dst = Mat()
        
        // 고해상도 이미지에 최적화된 적응형 이진화
        Imgproc.adaptiveThreshold(
            src, dst,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            15,    // 고해상도에 적합한 블록 크기
            8.0    // 적절한 민감도
        )
        
        Log.d(TAG, "고해상도 적응형 이진화 적용")
        return dst
    }
    
    /**
     * 기존 전체 이미지 전처리 (호환성 유지)
     */
    fun preprocessImage(bitmap: Bitmap): Bitmap {
        return preprocessReceiptForAmount(bitmap)
    }
}

/**
 * 차세대 적응형 영수증 전처리 시스템
 * 자동 밝기 감지 + 동적 전처리 최적화
 */
object AdaptiveReceiptProcessor {
    private const val TAG = "AdaptiveProcessor"
    
    // 조명 상태 enum
    enum class LightingCondition {
        UNDEREXPOSED,    // 저조도
        NORMAL,          // 정상
        OVEREXPOSED      // 과노출
    }
    
    /**
     * 메인 적응형 전처리 함수
     * @param bitmap 원본 이미지
     * @return 적응형 전처리된 이미지
     */
    fun processReceiptAdaptively(bitmap: Bitmap): Bitmap {
        Log.d(TAG, "적응형 영수증 전처리 시작: ${bitmap.width}x${bitmap.height}")
        
        // 1. Bitmap을 Mat으로 변환
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)
        
        // 2. 자동 밝기 감지
        val lightingCondition = detectLightingCondition(srcMat)
        Log.d(TAG, "감지된 조명 상태: $lightingCondition")
        
        // 3. 해상도 최적화
        val resizedMat = intelligentResize(srcMat)
        Log.d(TAG, "해상도 최적화: ${resizedMat.cols()}x${resizedMat.rows()}")
        
        // 4. 조명 상태에 따른 적응형 전처리
        val processedMat = when (lightingCondition) {
            LightingCondition.OVEREXPOSED -> processOverexposed(resizedMat)
            LightingCondition.UNDEREXPOSED -> processUnderexposed(resizedMat)
            LightingCondition.NORMAL -> processNormal(resizedMat)
        }
        
        // 5. Mat을 Bitmap으로 변환
        val resultBitmap = Bitmap.createBitmap(
            processedMat.cols(), processedMat.rows(), Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(processedMat, resultBitmap)
        
        // 메모리 해제
        srcMat.release()
        resizedMat.release()
        processedMat.release()
        
        Log.d(TAG, "적응형 전처리 완료")
        return resultBitmap
    }
    
    /**
     * 자동 밝기 감지 (히스토그램 분석)
     */
    private fun detectLightingCondition(mat: Mat): LightingCondition {
        // 그레이스케일 변환
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // 히스토그램 계산
        val hist = Mat()
        val histSize = 256
        val ranges = MatOfFloat(0f, 256f)
        Imgproc.calcHist(
            listOf(grayMat), MatOfInt(0), Mat(), hist, 
            MatOfInt(histSize), ranges
        )
        
        // 통계 분석
        val totalPixels = (grayMat.rows() * grayMat.cols()).toFloat()
        var darkPixels = 0f
        var brightPixels = 0f
        
        for (i in 0 until histSize) {
            val count = hist.get(i, 0)[0].toFloat()
            when {
                i < 64 -> darkPixels += count          // 0-63: 어두운 픽셀
                i > 192 -> brightPixels += count       // 192-255: 밝은 픽셀
            }
        }
        
        val darkRatio = darkPixels / totalPixels
        val brightRatio = brightPixels / totalPixels
        
        Log.d(TAG, "어두운 픽셀 비율: ${(darkRatio * 100).toInt()}%, 밝은 픽셀 비율: ${(brightRatio * 100).toInt()}%")
        
        // 조명 상태 판단
        val condition = when {
            brightRatio > 0.4 -> LightingCondition.OVEREXPOSED    // 40% 이상 과노출
            darkRatio > 0.4 -> LightingCondition.UNDEREXPOSED     // 40% 이상 저조도  
            else -> LightingCondition.NORMAL
        }
        
        // 메모리 해제
        grayMat.release()
        hist.release()
        
        return condition
    }
    
    /**
     * 과노출 이미지 전처리 (밝은 곳)
     */
    private fun processOverexposed(src: Mat): Mat {
        Log.d(TAG, "과노출 이미지 전처리 적용")
        
        // 1. 그레이스케일 변환
        val grayMat = Mat()
        Imgproc.cvtColor(src, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // 2. 감마 보정으로 밝기 감소 (gamma > 1)
        val gammaCorrected = Mat()
        applyGammaCorrection(grayMat, gammaCorrected, 1.5)
        
        // 3. CLAHE로 국소 대비 향상
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val enhanced = Mat()
        clahe.apply(gammaCorrected, enhanced)
        
        // 4. 적응형 이진화 (강한 설정)
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            enhanced, binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            15,    // 큰 블록 크기
            10.0   // 높은 C 값
        )
        
        grayMat.release()
        gammaCorrected.release()
        enhanced.release()
        
        return binary
    }
    
    /**
     * 저조도 이미지 전처리 (어두운 곳)
     */
    private fun processUnderexposed(src: Mat): Mat {
        Log.d(TAG, "저조도 이미지 전처리 적용")
        
        // 1. 그레이스케일 변환
        val grayMat = Mat()
        Imgproc.cvtColor(src, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // 2. 감마 보정으로 밝기 증가 (gamma < 1)
        val gammaCorrected = Mat()
        applyGammaCorrection(grayMat, gammaCorrected, 0.7)
        
        // 3. 히스토그램 평활화
        val equalized = Mat()
        Imgproc.equalizeHist(gammaCorrected, equalized)
        
        // 4. 가우시안 블러로 노이즈 제거
        val denoised = Mat()
        Imgproc.GaussianBlur(equalized, denoised, Size(3.0, 3.0), 0.0)
        
        // 5. 적응형 이진화 (부드러운 설정)
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            denoised, binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            11,    // 중간 블록 크기
            6.0    // 낮은 C 값
        )
        
        grayMat.release()
        gammaCorrected.release()
        equalized.release()
        denoised.release()
        
        return binary
    }
    
    /**
     * 정상 조명 이미지 전처리
     */
    private fun processNormal(src: Mat): Mat {
        Log.d(TAG, "정상 조명 이미지 전처리 적용")
        
        // 1. 그레이스케일 변환
        val grayMat = Mat()
        Imgproc.cvtColor(src, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // 2. 미디언 필터로 노이즈 제거
        val denoised = Mat()
        Imgproc.medianBlur(grayMat, denoised, 3)
        
        // 3. 적응형 이진화 (균형잡힌 설정)
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            denoised, binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            13,    // 균형잡힌 블록 크기
            7.0    // 균형잡힌 C 값
        )
        
        grayMat.release()
        denoised.release()
        
        return binary
    }
    
    /**
     * 감마 보정 적용
     */
    private fun applyGammaCorrection(src: Mat, dst: Mat, gamma: Double) {
        // 룩업 테이블 생성
        val lookupTable = Mat(1, 256, CvType.CV_8U)
        val lookupData = ByteArray(256)
        
        for (i in 0 until 256) {
            val normalizedValue = i / 255.0
            val correctedValue = Math.pow(normalizedValue, gamma)
            lookupData[i] = (correctedValue * 255).toInt().toByte()
        }
        
        lookupTable.put(0, 0, lookupData)
        Core.LUT(src, lookupTable, dst)
        
        lookupTable.release()
    }
    
    /**
     * 지능적 해상도 조정
     */
    private fun intelligentResize(src: Mat): Mat {
        val originalWidth = src.cols()
        val originalHeight = src.rows()
        
        // 해상도에 따른 최적 크기 결정
        val targetSize = when {
            originalWidth < 1000 -> 1500    // 작은 이미지는 확대
            originalWidth > 4000 -> 2500    // 큰 이미지는 축소
            else -> originalWidth           // 적절한 크기는 유지
        }
        
        if (originalWidth == targetSize) {
            return src.clone()
        }
        
        val scale = targetSize.toDouble() / originalWidth
        val newHeight = (originalHeight * scale).toInt()
        
        val resized = Mat()
        Imgproc.resize(src, resized, Size(targetSize.toDouble(), newHeight.toDouble()))
        
        Log.d(TAG, "해상도 조정: ${originalWidth}x${originalHeight} → ${targetSize}x${newHeight}")
        return resized
    }
} 