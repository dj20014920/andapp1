package com.example.andapp1.ocr

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OCR 처리를 위한 ViewModel
 */
class OcrViewModel : ViewModel() {
    
    private val _ocrResult = MutableLiveData<OcrResult>()
    val ocrResult: LiveData<OcrResult> = _ocrResult
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    companion object {
        private const val TAG = "OcrViewModel"
    }
    
    /**
     * 이미지 처리 및 OCR 수행
     */
    fun processImage(bitmap: Bitmap, tesseractManager: TesseractManager) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            
            try {
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "🔍 OCR 처리 시작...")
                    
                    // 이미지 전처리
                    val processedBitmap = ImageUtils.enhanceImageForOCR(bitmap)
                    
                    // 텍스트 추출
                    val extractedText = tesseractManager.extractText(processedBitmap)
                    Log.d(TAG, "📄 추출된 텍스트: $extractedText")
                    
                    // 숫자/금액 추출
                    val numbers = tesseractManager.extractNumbers(processedBitmap)
                    val extractedAmount = extractAmountFromText(numbers + extractedText)
                    Log.d(TAG, "💰 추출된 금액: $extractedAmount")
                    
                    // 결과 설정
                    val result = OcrResult(
                        extractedText = extractedText,
                        extractedAmount = extractedAmount,
                        confidence = calculateConfidence(extractedText)
                    )
                    
                    withContext(Dispatchers.Main) {
                        _ocrResult.value = result
                        Log.d(TAG, "✅ OCR 처리 완료")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ OCR 처리 중 오류 발생", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "텍스트 인식에 실패했습니다: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }
    
    /**
     * 텍스트에서 금액 추출
     */
    private fun extractAmountFromText(text: String): String {
        try {
            // 금액 패턴 찾기 (원, 숫자 조합)
            val patterns = listOf(
                Regex("""(\d{1,3}(?:,\d{3})*)\s*원"""),
                Regex("""(\d{1,3}(?:,\d{3})*)\s*₩"""),
                Regex("""₩\s*(\d{1,3}(?:,\d{3})*)"""),
                Regex("""(\d{1,3}(?:,\d{3})*)\s*[원₩]"""),
                Regex("""(\d+,\d+)"""),
                Regex("""(\d{3,})""") // 최소 3자리 이상의 숫자
            )
            
            for (pattern in patterns) {
                val matches = pattern.findAll(text)
                val amounts = matches.map { it.groupValues[1] }.toList()
                
                if (amounts.isNotEmpty()) {
                    // 가장 큰 금액을 선택 (일반적으로 총액이 가장 큼)
                    val maxAmount = amounts.maxByOrNull { 
                        it.replace(",", "").toIntOrNull() ?: 0 
                    }
                    if (maxAmount != null) {
                        Log.d(TAG, "💰 패턴 '$pattern'에서 금액 추출: $maxAmount")
                        return maxAmount + "원"
                    }
                }
            }
            
            return ""
        } catch (e: Exception) {
            Log.e(TAG, "❌ 금액 추출 중 오류", e)
            return ""
        }
    }
    
    /**
     * OCR 신뢰도 계산
     */
    private fun calculateConfidence(text: String): Float {
        return when {
            text.length > 50 -> 0.9f
            text.length > 20 -> 0.7f
            text.length > 10 -> 0.5f
            else -> 0.3f
        }
    }
    
    /**
     * 결과 초기화
     */
    fun clearResult() {
        _ocrResult.value = OcrResult("", "", 0f)
        _errorMessage.value = ""
    }
}

/**
 * OCR 결과 데이터 클래스
 */
data class OcrResult(
    val extractedText: String,
    val extractedAmount: String,
    val confidence: Float
) 