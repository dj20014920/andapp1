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

    fun processReceipt(context: Context, originalBitmap: Bitmap?): String {
        if (originalBitmap == null) {
            Log.e("OCR", "processReceipt: originalBitmap is null!")
            return ""
        }
        if (originalBitmap.isRecycled) {
            Log.e("OCR", "processReceipt: originalBitmap is already recycled!")
            return ""
        }
        if (originalBitmap.width == 0 || originalBitmap.height == 0) {
            Log.e("OCR", "processReceipt: originalBitmap has zero dimensions! Width: ${originalBitmap.width}, Height: ${originalBitmap.height}")
            return ""
        }
        Log.d("OCR", "processReceipt: originalBitmap received - Width: ${originalBitmap.width}, Height: ${originalBitmap.height}, Config: ${originalBitmap.config}, Mutable: ${originalBitmap.isMutable}")

        val mutableBitmap: Bitmap
        try {
            mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            if (mutableBitmap == null) {
                Log.e("OCR", "processReceipt: originalBitmap.copy() returned null! Could be OutOfMemory.")
                return ""
            }
            Log.d("OCR", "processReceipt: mutableBitmap created - Width: ${mutableBitmap.width}, Height: ${mutableBitmap.height}, Config: ${mutableBitmap.config}, Mutable: ${mutableBitmap.isMutable}")
            if (mutableBitmap.isRecycled) {
                Log.e("OCR", "processReceipt: mutableBitmap became recycled immediately after copy!")
                return ""
            }
            if (mutableBitmap.width == 0 || mutableBitmap.height == 0) {
                Log.e("OCR", "processReceipt: mutableBitmap has zero dimensions! Width: ${mutableBitmap.width}, Height: ${mutableBitmap.height}")
                return ""
            }
        } catch (e: Exception) {
            Log.e("OCR", "processReceipt: Error during bitmap copy or initial checks.", e)
            return ""
        }

        copyTrainedDataIfNeeded(context)
        val baseDir = File(context.filesDir, "tesseract")
        val tessdataDir = File(baseDir, "tessdata")
        if (!tessdataDir.exists()) tessdataDir.mkdirs()
        Log.d("OCR", "tessdata 최종 내용 = ${tessdataDir.list()?.joinToString()}")

        val srcMat = Mat()
        try {
            Utils.bitmapToMat(mutableBitmap, srcMat)
            Log.d("OCR", "processReceipt: bitmapToMat successful.")
        } catch (cvException: org.opencv.core.CvException) {
            Log.e("OCR", "processReceipt: CvException during bitmapToMat. Mat details: ${srcMat.width()}x${srcMat.height()}", cvException)
            Log.e("OCR", "Details of mutableBitmap at crash: Width: ${mutableBitmap.width}, Height: ${mutableBitmap.height}, Config: ${mutableBitmap.config}, Mutable: ${mutableBitmap.isMutable}, Recycled: ${mutableBitmap.isRecycled}")
            return ""
        }

        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)
        val threshMat = Mat()
        Imgproc.adaptiveThreshold(
            grayMat, threshMat,
            255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, 21, 4.0
        )
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(2.0, 2.0))
        Imgproc.morphologyEx(threshMat, threshMat, Imgproc.MORPH_OPEN, kernel)
        val denoisedMat = Mat()
        Imgproc.medianBlur(threshMat, denoisedMat, 3)

        val processedBmp = Bitmap.createBitmap(
            denoisedMat.cols(),
            denoisedMat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(denoisedMat, processedBmp)

        srcMat.release()
        grayMat.release()
        threshMat.release()
        denoisedMat.release()

        val tess = TessBaseAPI()
        return try {
            tess.init(baseDir.absolutePath, "kor+eng")
            tess.setVariable(VAR_CHAR_WHITELIST, "0123456789,.원품명수량단가금액합계총 부가세과세공급가액면세황교익의 죽이는 김치찌개")
            tess.setPageSegMode(PageSegMode.PSM_AUTO_OSD)
            tess.setImage(processedBmp)
            val result = tess.utF8Text
            Log.d("OCR", "인식된 텍스트 = $result")
            result
        } catch (e: Exception) {
            Log.e("OCR", "processReceipt() 예외 발생", e)
            ""
        } finally {
            tess.end()
            if (!processedBmp.isRecycled) {
                processedBmp.recycle()
            }
            if (!mutableBitmap.isRecycled) {
                mutableBitmap.recycle()
            }
        }
    }

    fun extractItemPricePairs(text: String): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val lines = text.lines()
        val priceRegex = Regex("""([0-9,]{1,10})[ \t]*(?:원)?""")

        for (line in lines) {
            val priceMatches = priceRegex.findAll(line)
            var lastPriceEndIndex = 0

            priceMatches.forEach { matchResult ->
                val priceString = matchResult.groupValues[1].replace(",", "")
                priceString.toIntOrNull()?.let { price ->
                    var item = line.substring(lastPriceEndIndex, matchResult.range.first).trim()
                    if (item.isNotBlank() && !item.matches(Regex("""^[\d,\s]*${'$'}""")) &&
                        !item.startsWith("합계") && !item.startsWith("총액") && !item.startsWith("금액") &&
                        !item.startsWith("부가세") && !item.startsWith("VAT") && !item.contains("승인번호") &&
                        item.length > 1
                    ) {
                        priceRegex.find(item)?.value?.let {
                            item = item.substringBefore(it).trim()
                        }
                        if (item.isNotBlank()) {
                            result.add(item to price)
                        }
                    }
                    lastPriceEndIndex = matchResult.range.last + 1
                }
            }
        }
        Log.d("OCR_Parse", "extractItemPricePairs 결과: $result")
        return result
    }

    fun calculateTotalAmount(items: List<Pair<String, Int>>): Int {
        return items.sumOf { it.second }
    }

    fun extractTotalAmount(text: String): Int? {
        val lines = text.lines()
        val totalKeywords = listOf("총액", "합계", "총금액", "받을금액", "승인금액", "결제금액", "판매합계")
        val pureAmountPattern = """([0-9,]{1,10})[ \t]*(?:원)?"""
        val candidates = mutableListOf<Pair<Int, Int>>()

        lines.forEachIndexed { _, line ->
            for (keyword in totalKeywords) {
                val keywordAmountRegex = Regex("""${'$'}{Regex.escape(keyword)}[ \t]*($pureAmountPattern)""")
                keywordAmountRegex.findAll(line).forEach { matchResult ->
                    matchResult.groupValues[1].replace(",", "").toIntOrNull()?.let { amount ->
                        if (amount > 0) candidates.add(amount to 0)
                    }
                }
            }

            val standaloneAmountRegex = Regex("""^[ \t]*($pureAmountPattern)[ \t]*${'$'}""")
            standaloneAmountRegex.find(line)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()?.let { amount ->
                if (amount > 0) candidates.add(amount to 1)
            }
        }

        if (candidates.isEmpty()) {
            val generalAmountRegex = Regex(pureAmountPattern)
            generalAmountRegex.findAll(text).forEach { matchResult ->
                matchResult.groupValues[1].replace(",", "").toIntOrNull()?.let { amount ->
                    if (amount >= 100 && amount <= 100000000) {
                        candidates.add(amount to 2)
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null

        val sortedCandidates = candidates.sortedWith(compareBy({ it.second }, { -it.first }))
        val topPriority = sortedCandidates.first().second
        val result = sortedCandidates.filter { it.second == topPriority }.maxOfOrNull { it.first }
        Log.d("OCR_Parse", "extractTotalAmount 결과: $result")
        return result
    }

    /**
     * 입력된 텍스트 메시지에서 지출 내역(항목, 금액)을 추출합니다.
     * 주로 카드 사용 알림 SMS/텍스트 메시지 파싱을 목표로 합니다.
     * @param text 메시지 전체 내용
     * @return Pair(항목-금액 리스트, 총액 문자열). 금액 문자열에는 '원'이 포함됩니다.
     */
    fun processTextMessage(text: String): Pair<List<Pair<String, String>>, String?> {
        val items = mutableListOf<Pair<String, String>>()
        var totalAmountString: String? = null

        val amountRegex = Regex("""([0-9,]+)(원)""")
        val lines = text.lines()
        var amountFoundLineIndex = -1
        var parsedAmountValue = ""
        var parsedPlace: String? = null

        for (index in lines.indices) {
            val line = lines[index]
            val matchResult = amountRegex.find(line)
            if (matchResult != null) {
                parsedAmountValue = matchResult.value
                totalAmountString = parsedAmountValue
                amountFoundLineIndex = index
                break
            }
        }

        if (amountFoundLineIndex != -1) {
            if (amountFoundLineIndex + 1 < lines.size) {
                val potentialPlaceLine = lines[amountFoundLineIndex + 1].trim()
                if (potentialPlaceLine.isNotBlank() &&
                    !potentialPlaceLine.contains(Regex("""\d{2}/\d{2}""")) &&
                    !potentialPlaceLine.contains(Regex("""\d{1,2}:\d{2}""")) &&
                    !potentialPlaceLine.matches(Regex("""^[\d,\s]+원?${'$'}""")) &&
                    !potentialPlaceLine.contains(Regex("""(카드|체크|결제|승인|매입|취소|일시불|할부|잔액|누적|한도)""")) &&
                    potentialPlaceLine.length > 1 &&
                    !listOf("님", "고객님").any { potentialPlaceLine.endsWith(it) }
                ) {
                    parsedPlace = potentialPlaceLine.replace(Regex("""[\s]*(사용|이용|결제)$"""), "").trim()
                }
            }

            if (parsedPlace == null || parsedPlace.isBlank()) {
                val lineWithAmount = lines[amountFoundLineIndex]
                val placeCandidate = lineWithAmount.substringBefore(parsedAmountValue).trim()

                if (placeCandidate.isNotBlank() &&
                    !placeCandidate.matches(Regex("""^[\d,\s]+${'$'}""")) &&
                    !placeCandidate.contains(Regex("""\d{2}/\d{2}""")) &&
                    !placeCandidate.contains(Regex("""\d{1,2}:\d{2}""")) &&
                    placeCandidate.length > 1 &&
                    !listOf("님", "고객님", "[Web발신]", "(주)").any { placeCandidate.contains(it)} &&
                    !Regex("""(카드|체크|결제|승인|매입|취소|일시불|할부|잔액|누적|한도)""").containsMatchIn(placeCandidate)
                    ) {
                     parsedPlace = placeCandidate.replace(Regex("""[\s]*(사용|이용|결제)$"""), "").trim()
                }
            }
            
            if (parsedPlace == null || parsedPlace.isBlank()) {
                val usageKeywords = listOf("사용", "결제", "구매", "매입")
                for (i in lines.indices) {
                    if (i == amountFoundLineIndex) continue
                    val line = lines[i].trim()
                    if (usageKeywords.any { line.contains(it) } && 
                        !line.contains(Regex("""\d{2}/\d{2}""")) && 
                        !line.contains(Regex("""\d{1,2}:\d{2}""")) &&
                        !line.matches(Regex("""^[\d,\s]+원?${'$'}""")) &&
                        !line.contains(Regex("""(카드|체크|승인|취소|잔액|누적|한도)""")) &&
                        line.length > 2 ) {
                        
                        var tempPlace = line
                        usageKeywords.forEach { kw -> tempPlace = tempPlace.replace(kw, "") }
                        tempPlace = tempPlace.replace(Regex("""^\[[^\]]+\]\s*"""), "")
                        tempPlace = tempPlace.trim()

                        if (tempPlace.isNotBlank() && tempPlace.length > 1) {
                           parsedPlace = tempPlace
                           break 
                        }
                    }
                }
            }

            if (!parsedPlace.isNullOrBlank() && parsedAmountValue.isNotBlank()) {
                items.add(Pair(parsedPlace!!, parsedAmountValue))
            } else if (parsedAmountValue.isNotBlank()) {
                items.add(Pair("상세내역확인", parsedAmountValue))
                Log.d("processTextMessage", "금액(${parsedAmountValue})은 인식했으나, 사용처를 특정하지 못했습니다.")
            }
        }

        if (items.isEmpty() && totalAmountString == null) {
            val itemPricePattern = Regex("""([^\d,\s원][^\d,원]*?)\s*([0-9,]+)\s*(원)?""")
            itemPricePattern.findAll(text).forEach { matchResult ->
                val itemName = matchResult.groupValues[1].trim()
                val itemPriceNumber = matchResult.groupValues[2].replace(",", "")
                val currency = matchResult.groupValues[3].ifBlank { "원" }

                if (itemName.isNotBlank() && itemPriceNumber.isNotBlank()) {
                    val priceWithCurrency = "${'$'}{itemPriceNumber}${'$'}{currency}"
                    items.add(Pair(itemName, priceWithCurrency))
                    if (totalAmountString == null) {
                        totalAmountString = priceWithCurrency
                    }
                }
            }
        }

        if (items.isNotEmpty()) {
            Log.d("processTextMessage", "파싱 결과: 항목 = ${items.joinToString { it.first + ":" + it.second }}, 총액 = ${totalAmountString}")
        } else if (totalAmountString != null) {
             Log.d("processTextMessage", "파싱 결과: 금액만 인식 = ${totalAmountString}")
        } else {
            Log.d("processTextMessage", "지출 내역을 인식하지 못했습니다: 원본텍스트 = $text")
        }
        return Pair(items, totalAmountString)
    }

    /**
     * 총합 및 인당 금액만 반환 (기본 포맷)
     */
    fun formatAnalysisResult(items: List<Pair<String, Int>>, people: Int = 4): String {
        val total = calculateTotalAmount(items)
        return "→ 총합: ${total}원 / 인당: ${total / people}원"
    }
}
