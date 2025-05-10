package com.example.andapp1.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

object ReceiptOcrProcessor {

    fun copyTrainedDataIfNeeded(context: Context) {
        val tessDataDir = File(context.filesDir, "tesseract/tessdata")
        if (!tessDataDir.exists()) {
            tessDataDir.mkdirs()
        }

        val requiredLanguages = listOf("eng", "kor")
        for (lang in requiredLanguages) {
            val file = File(tessDataDir, "$lang.traineddata")
            if (!file.exists()) {
                try {
                    context.assets.open("tessdata/$lang.traineddata").use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                            Log.d("OCR", "$lang.traineddata 파일 복사 완료")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OCR", "❌ $lang.traineddata 복사 실패: ${e.message}")
                }
            }
        }
    }


    fun processReceipt(context: Context, bitmap: Bitmap): String {
        val dataPath = File(context.filesDir, "tesseract").absolutePath
        val tess = TessBaseAPI()
        val success = tess.init(dataPath, "kor+eng")
        if (!success) {
            Log.e("OCR_ENGINE", "❌ Tesseract 초기화 실패")
            return ""
        }

        tess.setImage(bitmap)
        val result = tess.utF8Text
        Log.d("OCR_ENGINE", "🔍 OCR 결과 텍스트:\n$result")
        tess.end()
        return result
    }


    fun extractTotalAmount(text: String): Int {
        Log.d("OCR_RESULT_CLEAN", text)  // ✅ 인식 결과 텍스트 확인
        val totalRegex = Regex("""(총\s*액|총\s*합|합\s*계|총\s*금액|결제\s*금액)[^\d]*([0-9,]+)""")
        val match = totalRegex.find(text)
        return match?.groupValues?.getOrNull(2)
            ?.replace(",", "")
            ?.replace("원", "")
            ?.toIntOrNull() ?: 0
    }

    fun formatTotalOnlyMessage(total: Int, people: Int): String {
        return if (total > 0) {
            "총 합계: ${total}원\n→ 인당: ${total / people}원"
        } else {
            "총액을 인식할 수 없습니다."
        }
    }
}
