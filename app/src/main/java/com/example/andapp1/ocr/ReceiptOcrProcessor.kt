package com.example.andapp1.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

object ReceiptOcrProcessor {

    fun copyTrainedDataIfNeeded(context: Context) {
        val tessDir = File(context.filesDir, "tesseract/tessdata")
        if (!tessDir.exists()) {
            tessDir.mkdirs()
        }
        val file = File(tessDir, "eng.traineddata")
        if (!file.exists()) {
            val input = context.assets.open("tessdata/eng.traineddata")
            val output = FileOutputStream(file)
            input.copyTo(output)
            input.close()
            output.close()
        }
    }

    fun processReceipt(context: Context, bitmap: Bitmap): String {
        val dataPath = File(context.filesDir, "tesseract").absolutePath
        val tess = TessBaseAPI()
        tess.init(dataPath, "eng")
        tess.setImage(bitmap)
        val result = tess.utF8Text
        tess.end()
        return result
    }

    fun extractItemPricePairs(text: String): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val lines = text.lines()
        val priceRegex = Regex("\\d{2,5}") // 예: 3000, 12000 등

        for (line in lines) {
            val match = priceRegex.find(line)
            if (match != null) {
                val price = match.value.toInt()
                val item = line.substringBefore(match.value).trim()
                if (item.isNotBlank()) {
                    result.add(Pair(item, price))
                }
            }
        }
        return result
    }

    fun calculateTotalAmount(items: List<Pair<String, Int>>): Int {
        return items.sumOf { it.second }
    }

    fun formatAnalysisResult(items: List<Pair<String, Int>>, people: Int = 4): String {
        val builder = StringBuilder("[영수증 인식 결과]\n")
        for ((item, price) in items) {
            builder.append("$item: ${price}원\n")
        }
        val total = calculateTotalAmount(items)
        builder.append("→ 총합: ${total}원 / 인당: ${total / people}원")
        return builder.toString()
    }
}
