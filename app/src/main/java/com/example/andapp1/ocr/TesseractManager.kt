package com.example.andapp1.ocr

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Tesseract OCR 엔진을 관리하는 클래스
 * tessdata 파일 복사 및 OCR 텍스트 추출 담당
 */
class TesseractManager(private val context: Context) {
    
    private var tessApi: TessBaseAPI? = null
    private val tessDataPath = "${context.filesDir}/tesseract/"
    private val tessDataSubPath = "tessdata/"
    
    companion object {
        private const val TAG = "TesseractManager"
        private const val LANG_KOR = "kor"
        private const val LANG_ENG = "eng"
    }
    
    /**
     * Tesseract 초기화
     */
    fun initTesseract(): Boolean {
        return try {
            // tessdata 파일들을 assets에서 복사
            copyTessDataFiles()
            
            // TessBaseAPI 초기화
            tessApi = TessBaseAPI()
            val initResult = tessApi?.init(tessDataPath, "$LANG_KOR+$LANG_ENG") ?: false
            
            if (initResult) {
                Log.d(TAG, "✅ Tesseract 초기화 성공")
                // OCR 엔진 모드 설정 (기본값)
                tessApi?.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
                tessApi?.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789,.-원₩ ")
            } else {
                Log.e(TAG, "❌ Tesseract 초기화 실패")
            }
            
            initResult
        } catch (e: Exception) {
            Log.e(TAG, "❌ Tesseract 초기화 중 오류 발생", e)
            false
        }
    }
    
    /**
     * 이미지에서 텍스트 추출
     */
    fun extractText(bitmap: Bitmap): String {
        return try {
            if (tessApi == null) {
                Log.w(TAG, "⚠️ Tesseract가 초기화되지 않음. 재초기화 시도...")
                if (!initTesseract()) {
                    return ""
                }
            }
            
            tessApi?.setImage(bitmap)
            val extractedText = tessApi?.utF8Text ?: ""
            
            Log.d(TAG, "🔍 추출된 텍스트: $extractedText")
            extractedText.trim()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 텍스트 추출 중 오류 발생", e)
            ""
        }
    }
    
    /**
     * 숫자만 추출 (금액 인식용)
     */
    fun extractNumbers(bitmap: Bitmap): String {
        return try {
            tessApi?.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789,.-₩원 ")
            tessApi?.setImage(bitmap)
            val text = tessApi?.utF8Text ?: ""
            
            // 숫자와 관련 기호만 필터링
            val numbers = text.replace(Regex("[^0-9,.-₩원 ]"), "").trim()
            Log.d(TAG, "🔢 추출된 숫자: $numbers")
            
            numbers
        } catch (e: Exception) {
            Log.e(TAG, "❌ 숫자 추출 중 오류 발생", e)
            ""
        }
    }
    
    /**
     * assets에서 tessdata 파일들을 복사
     */
    private fun copyTessDataFiles() {
        val tessDataDir = File(tessDataPath + tessDataSubPath)
        if (!tessDataDir.exists()) {
            tessDataDir.mkdirs()
            Log.d(TAG, "📁 tessdata 디렉토리 생성: ${tessDataDir.absolutePath}")
        }
        
        try {
            val assetManager = context.assets
            val files = assetManager.list(tessDataSubPath) ?: arrayOf()
            
            for (filename in files) {
                val outFile = File(tessDataDir, filename)
                if (!outFile.exists()) {
                    Log.d(TAG, "📋 복사 중: $filename")
                    copyFile(assetManager, tessDataSubPath + filename, outFile)
                } else {
                    Log.d(TAG, "✅ 이미 존재: $filename")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ tessdata 파일 복사 실패", e)
        }
    }
    
    /**
     * 파일 복사 헬퍼 메서드
     */
    private fun copyFile(assetManager: AssetManager, assetPath: String, outFile: File) {
        try {
            val inputStream: InputStream = assetManager.open(assetPath)
            val outputStream = FileOutputStream(outFile)
            
            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            
            inputStream.close()
            outputStream.close()
            
            Log.d(TAG, "✅ 파일 복사 완료: ${outFile.name}")
        } catch (e: IOException) {
            Log.e(TAG, "❌ 파일 복사 실패: $assetPath", e)
        }
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        try {
            tessApi?.recycle()
            tessApi = null
            Log.d(TAG, "🧹 Tesseract 리소스 정리 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 리소스 정리 중 오류", e)
        }
    }
} 