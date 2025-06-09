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
     * OCR 텍스트에서 '총액', '합계', '금액' 레이블 기반 숫자 추출.
     * 레이블 추출 실패 시 robust 방식으로 최대값 반환.
     */
    /*fun extractTotalAmount(text: String): Int {
        // ① 레이블 기반 추출 (합계/총액/금액 + 원)
        Regex("""(?:총\s*액|합\s*계|금\s*액)[^\d]{0,5}([\d,]+)""")
            .find(text)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toIntOrNull()
            ?.let { return it }

        // ② 숫자+원 패턴 중 마지막 매치
        Regex("""([\d,]+)\s*원""")
            .findAll(text)
            .mapNotNull { it.groupValues[1].replace(",", "").toIntOrNull() }
            .lastOrNull()
            ?.let { return it }

        // ③ 더 이상의 fallback 없이 0 리턴
        return 0
    }*/

    fun extractTotalAmount(text: String): Int? {
        val lines = text.lines()
        val keyWords = listOf("총", "합계", "총액", "금액", "결제", "합", "받은금액", "받음", "받은 금액", "총금액")
        val ignoreWords = listOf("승인", "카드", "사업자", "전화", "전화번호", "번호", "잔액", "포인트", "적립", "결제일", "VAT")
        val amountPattern = Regex("""(\d{3,7})\s*(원|₩)?""")

        data class Candidate(val amount: Int, val lineIdx: Int, val score: Int, val line: String)
        val candidates = mutableListOf<Candidate>()

        for ((idx, lineRaw) in lines.withIndex()) {
            val line = lineRaw.replace(",", "").replace(" ", "")
            if (ignoreWords.any { it in line }) continue

            val match = amountPattern.find(line)
            if (match != null) {
                val value = match.groupValues[1].toIntOrNull() ?: continue
                if (value < 1000) continue
                if (value > 2000000) continue

                var score = 0
                if (keyWords.any { it in line }) score += 5
                if (line.contains("원") || line.contains("₩")) score += 2
                score += ((lines.size - idx) / 2)

                candidates.add(Candidate(value, idx, score, lineRaw))
            }
        }

        val best = candidates.sortedWith(compareByDescending<Candidate> { it.score }
            .thenByDescending { it.amount }).firstOrNull()

        return best?.amount
    }

    /**
     * 총합 및 인당 금액만 반환 (기본 포맷)
     */
    fun formatAnalysisResult(items: List<Pair<String, Int>>, people: Int = 4): String {
        val total = calculateTotalAmount(items)
        return "→ 총합: ${total}원 / 인당: ${total / people}원"
    }
}
