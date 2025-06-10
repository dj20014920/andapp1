package com.example.andapp1.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object ReceiptOcrProcessor {

    private const val TAG = "OCR_T4A_Lifecycle"

    fun copyTrainedDataIfNeeded(context: Context) {
        val tessDir = File(context.filesDir, "tesseract/tessdata").apply { 
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Tesseract data directory 생성: ${this.absolutePath}")
            }
        }
        listOf("eng", "kor").forEach { lang ->
            val outFile = File(tessDir, "$lang.traineddata")
            if (!outFile.exists() || outFile.length() == 0L) {
                try {
                    context.assets.open("tessdata/$lang.traineddata").use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "$lang.traineddata 복사 성공 (${outFile.length()} bytes) to ${outFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "$lang.traineddata 복사 중 오류 발생", e)
                }
            } else {
                Log.d(TAG, "$lang.traineddata 이미 존재 (${outFile.length()} bytes) at ${outFile.absolutePath}")
            }
        }
    }

    fun processReceipt(context: Context, bitmap: Bitmap): String {
        Log.d(TAG, "processReceipt 시작")

        copyTrainedDataIfNeeded(context)
        val baseDir = File(context.filesDir, "tesseract")
        val tessdataDir = File(baseDir, "tessdata")
        if (!tessdataDir.exists()) tessdataDir.mkdirs()
        Log.d(TAG, "tessdata 최종 내용 = ${tessdataDir.list()?.joinToString()}")

        // OpenCV 전처리
        Log.d(TAG, "OpenCV 전처리 시작")
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)
        Log.d(TAG, "bitmapToMat 완료")

        // 3) 그레이스케일
        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)
        Log.d(TAG, "cvtColor 완료")

        // 4) Adaptive Threshold 이진화
        val threshMat = Mat()
        Imgproc.adaptiveThreshold(
            grayMat, threshMat,
            255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, 21,4.0
        )
        Log.d(TAG, "adaptiveThreshold 완료")

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(2.0, 2.0))
        Imgproc.morphologyEx(threshMat, threshMat, Imgproc.MORPH_OPEN, kernel)
        Log.d(TAG, "morphologyEx 완료")

        val denoisedMat = Mat()
        Imgproc.medianBlur(threshMat, denoisedMat, 3)
        Log.d(TAG, "medianBlur 완료")

        // 6) Mat → Bitmap (Tesseract 입력용)
        val processedBmp = Bitmap.createBitmap(
            denoisedMat.cols(),
            denoisedMat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(denoisedMat, processedBmp)
        Log.d(TAG, "matToBitmap 완료")

        // 7) Mat 메모리 해제
        srcMat.release()
        grayMat.release()
        threshMat.release()
        denoisedMat.release()
        Log.d(TAG, "Mat 메모리 해제 완료")

        val tess = TessBaseAPI()
        return try {
            Log.d(TAG, "Tesseract baseDir path: ${baseDir.absolutePath}")
            Log.d(TAG, "Tesseract tessdata directory exists: ${File(baseDir, "tessdata").exists()}")
            Log.d(TAG, "Tesseract tessdata files: ${File(baseDir, "tessdata").listFiles()?.joinToString { it.name }}")

            var initialized = false
            var lastError: Throwable? = null

            // 1. 한국어 시도
            var langToTry = "kor"
            Log.d(TAG, "Tesseract 초기화 시도 (init) - 언어: $langToTry")
            try {
                tess.init(baseDir.absolutePath, langToTry)
                Log.d(TAG, "tess.init($langToTry) 호출 반환됨")
                initialized = true
                Log.d(TAG, "Tesseract 초기화 성공으로 간주 ($langToTry)")
            } catch (t: Throwable) {
                Log.e(TAG, "Tesseract 초기화 실패 ($langToTry)", t)
                lastError = t
            }

            // 2. 한국어 실패 시 영어 시도
            if (!initialized) {
                langToTry = "eng"
                Log.d(TAG, "Tesseract 초기화 시도 (init) - 언어: $langToTry (fallback)")
                try {
                    tess.init(baseDir.absolutePath, langToTry)
                    Log.d(TAG, "tess.init($langToTry) 호출 반환됨")
                    initialized = true
                    Log.d(TAG, "Tesseract 초기화 성공으로 간주 ($langToTry)")
                } catch (t: Throwable) {
                    Log.e(TAG, "Tesseract 초기화 실패 ($langToTry)", t)
                    if (lastError == null) lastError = t else lastError.addSuppressed(t)
                }
            }

            if (!initialized) {
                val errorMessage = "Tesseract API가 두 언어(kor, eng) 모두에 대해 성공적으로 초기화되지 않았습니다."
                Log.e(TAG, errorMessage)
                throw RuntimeException(errorMessage, lastError)
            }

            Log.d(TAG, "Tesseract setVariable 및 setPageSegMode 설정 시도")
            // 한국 영수증 핵심 금액 인식을 위한 최적화된 whitelist
            // Main 브랜치 방식: 숫자, 쉼표, 핵심 금액 관련 한글만 허용
            tess.setVariable("tessedit_char_whitelist", "0123456789,원합계총액금액")
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN
            Log.d(TAG, "Tesseract 이미지 설정 시도")
            tess.setImage(processedBmp)
            Log.d(TAG, "Tesseract 이미지 설정 완료")

            Log.d(TAG, "Tesseract 텍스트 인식 시도")
            val result = tess.getUTF8Text()
            Log.d(TAG, "Tesseract 텍스트 인식 완료")
            Log.d(TAG, "인식된 텍스트 = $result")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "processReceipt() 내에서 예외 발생 또는 Tesseract 초기화 실패", t)
            throw t
        } finally {
            Log.d(TAG, "Tesseract end 시도")
            tess.recycle()
            Log.d(TAG, "Tesseract end 완료")
            Log.d(TAG, "processReceipt 종료")
        }
    }


    fun extractItemPricePairs(text: String): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val lines = text.lines()
        val priceRegex = Regex("\\d{2,5}")
        for (line in lines) {
            val match = priceRegex.find(line)
            if (match != null) {
                val price = match.value.toInt()
                val item = line.substringBefore(match.value).trim()
                if (item.isNotBlank()) result.add(item to price)
            }
        }
        return result
    }

    fun calculateTotalAmount(items: List<Pair<String, Int>>): Int {
        return items.sumOf { it.second }
    }

    /**
     * 한국 영수증의 핵심 금액 부분 추출 최적화
     * 영수증 하단의 총액/합계 중심으로 인식
     */
    fun extractTotalAmount(text: String): Int? {
        val lines = text.lines().filter { it.isNotBlank() }
        
        // 한국 영수증의 핵심 키워드 (우선순위별)
        val primaryKeywords = listOf("총액", "합계", "총금액", "금액")
        val secondaryKeywords = listOf("총", "합", "결제")
        
        // 영수증 하단부터 검색 (총액은 보통 하단에 위치)
        val reversedLines = lines.reversed()
        
        // 1차: 핵심 키워드와 함께 나오는 금액 검색
        for (line in reversedLines) {
            val cleanLine = line.replace(",", "").replace(" ", "")
            
            // 주요 키워드가 포함된 라인에서 금액 추출
            if (primaryKeywords.any { cleanLine.contains(it) }) {
                val amountMatch = Regex("""(\d{3,7})""").find(cleanLine)
                amountMatch?.let { match ->
                    val amount = match.value.toIntOrNull()
                    if (amount != null && amount >= 1000 && amount <= 1000000) {
                        Log.d(TAG, "핵심 키워드로 금액 발견: ${amount}원 (라인: $line)")
                        return amount
                    }
                }
            }
        }
        
        // 2차: 보조 키워드 검색
        for (line in reversedLines) {
            val cleanLine = line.replace(",", "").replace(" ", "")
            
            if (secondaryKeywords.any { cleanLine.contains(it) }) {
                val amountMatch = Regex("""(\d{3,7})""").find(cleanLine)
                amountMatch?.let { match ->
                    val amount = match.value.toIntOrNull()
                    if (amount != null && amount >= 1000 && amount <= 500000) {
                        Log.d(TAG, "보조 키워드로 금액 발견: ${amount}원 (라인: $line)")
                        return amount
                    }
                }
            }
        }
        
        // 3차: 가장 큰 금액 반환 (fallback)
        val allAmounts = lines.mapNotNull { line ->
            val cleanLine = line.replace(",", "").replace(" ", "")
            Regex("""(\d{3,7})""").find(cleanLine)?.value?.toIntOrNull()
        }.filter { it >= 1000 && it <= 500000 }
        
        val maxAmount = allAmounts.maxOrNull()
        if (maxAmount != null) {
            Log.d(TAG, "최대 금액으로 선택: ${maxAmount}원")
        }
        
        return maxAmount
    }

    /**
     * 총합 및 인당 금액만 반환 (기본 포맷)
     */
    fun formatAnalysisResult(items: List<Pair<String, Int>>, people: Int = 4): String {
        val total = calculateTotalAmount(items)
        return "→ 총합: ${total}원 / 인당: ${total / people}원"
    }
}
