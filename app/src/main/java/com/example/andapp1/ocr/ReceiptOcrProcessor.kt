package com.example.andapp1.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageSegMode
import com.googlecode.tesseract.android.TessBaseAPI.VAR_CHAR_WHITELIST
import java.io.File
import java.io.FileOutputStream
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object ReceiptOcrProcessor {

    fun copyTrainedDataIfNeeded(context: Context) {
        val tessDir = File(context.filesDir, "tesseract/tessdata").apply { if (!exists()) mkdirs() }
        listOf("eng", "kor").forEach { lang ->
            val outFile = File(tessDir, "$lang.traineddata")
            if (!outFile.exists()) {
                context.assets.open("tessdata/$lang.traineddata").use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("OCR", "$lang.traineddata 복사 성공 → ${outFile.length()} bytes")
            } else {
                Log.d("OCR", "$lang.traineddata 이미 존재 → ${outFile.length()} bytes")
            }
        }
    }

    fun processReceipt(context: Context, bitmap: Bitmap): String {

        copyTrainedDataIfNeeded(context)
        val baseDir = File(context.filesDir, "tesseract")
        val tessdataDir = File(baseDir, "tessdata")
        if (!tessdataDir.exists()) tessdataDir.mkdirs()
        Log.d("OCR", "tessdata 최종 내용 = ${tessdataDir.list()?.joinToString()}")

        // OpenCV 전처리
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        // 3) 그레이스케일
        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)

        // 4) Adaptive Threshold 이진화
        val threshMat = Mat()
        Imgproc.adaptiveThreshold(
            grayMat, threshMat,
            255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, 21,4.0
        )

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(2.0, 2.0))
        Imgproc.morphologyEx(threshMat, threshMat, Imgproc.MORPH_OPEN, kernel)

        val denoisedMat = Mat()
        Imgproc.medianBlur(threshMat, denoisedMat, 3)

        // 6) Mat → Bitmap (Tesseract 입력용)
        val processedBmp = Bitmap.createBitmap(
            denoisedMat.cols(),
            denoisedMat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(denoisedMat, processedBmp)

        // 7) Mat 메모리 해제
        srcMat.release()
        grayMat.release()
        threshMat.release()
        denoisedMat.release()

        val tess = TessBaseAPI()
        return try {
            tess.init(baseDir.absolutePath, "kor+eng")
            tess.setVariable(VAR_CHAR_WHITELIST, "0123456789,원합계총액금액")
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            tess.setImage(processedBmp)

            val result = tess.utF8Text
            Log.d("OCR", "인식된 텍스트 = $result")
            result
        } catch (e: Exception) {
            Log.e("OCR", "processReceipt() 예외 발생", e)
            ""
        } finally {
            tess.end()
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

        val amountRegex = Regex("""((합\s*계|총\s*액|총\s*합|결제금액|총\s*금액)[^\d]{0,3})?(\d{2,6})\s*(원)?""")

        val candidates = mutableListOf<Int>()

        for (line in lines) {
            val match = amountRegex.find(line)
            if (match != null) {
                val amount = match.groupValues[3].toIntOrNull()
                if (amount != null) candidates.add(amount)
            }
        }

        // 숫자 중 가장 큰 값을 총합으로 가정
        return candidates.maxOrNull()
    }
    /**
     * 총합 및 인당 금액만 반환 (기본 포맷)
     */
    fun formatAnalysisResult(items: List<Pair<String, Int>>, people: Int = 4): String {
        val total = calculateTotalAmount(items)
        return "→ 총합: ${total}원 / 인당: ${total / people}원"
    }
}
