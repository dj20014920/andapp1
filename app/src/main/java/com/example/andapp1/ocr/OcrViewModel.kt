// OcrViewModel.kt
package com.example.andapp1.ocr

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OCR 상태를 관리하는 Sealed Class
 */
sealed class OcrUiState {
    object Idle : OcrUiState()
    object Loading : OcrUiState()
    data class Success(val text: String) : OcrUiState()
    data class Error(val message: String) : OcrUiState()
}

/**
 * OCR 기능의 비즈니스 로직을 관리하는 ViewModel
 * 영수증 금액 추출 및 채팅방 연동 기능 포함
 */
class OcrViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "OcrViewModel"
    }
    
    // OCR 상태 관리
    private val _ocrState = MutableLiveData<OcrState>(OcrState.Idle)
    val ocrState: LiveData<OcrState> = _ocrState
    
    // 처리된 이미지
    private val _processedImage = MutableLiveData<Bitmap?>()
    val processedImage: LiveData<Bitmap?> = _processedImage
    
    // OCR 결과 (금액 정보)
    private val _receiptAmount = MutableLiveData<ReceiptAmount?>()
    val receiptAmount: LiveData<ReceiptAmount?> = _receiptAmount
    
    // 채팅방 전송 결과
    private val _chatSendResult = MutableLiveData<String?>()
    val chatSendResult: LiveData<String?> = _chatSendResult
    
    private val tesseractManager = TesseractManager.getInstance(application)
    
    init {
        initializeOcr()
    }
    
    /**
     * OCR 엔진 초기화
     */
    private fun initializeOcr() {
        viewModelScope.launch {
            try {
                _ocrState.value = OcrState.Loading("OCR 엔진 초기화 중...")
                
                val success: Boolean = withContext(Dispatchers.IO) {
                    tesseractManager.initialize()
                }
                
                if (success) {
                    Log.d(TAG, "OCR 엔진 초기화 성공")
                    _ocrState.value = OcrState.Idle
                } else {
                    Log.e(TAG, "OCR 엔진 초기화 실패")
                    _ocrState.value = OcrState.Error("OCR 엔진 초기화에 실패했습니다.")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "OCR 초기화 중 오류: ${e.message}", e)
                _ocrState.value = OcrState.Error("OCR 초기화 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }
    
    /**
     * 영수증 이미지에서 금액 정보 추출
     * @param bitmap 원본 이미지
     */
    fun processReceiptImage(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "영수증 처리 시작")
                _ocrState.value = OcrState.Loading("이미지 전처리 중...")
                
                // 1. 기존 ImageUtils를 사용한 이미지 전처리
                val processedBitmap: Bitmap = withContext(Dispatchers.IO) {
                    ImageUtils.preprocessReceiptForAmount(bitmap)
                }
                
                _processedImage.value = processedBitmap
                Log.d(TAG, "이미지 전처리 완료")
                
                // 2. OCR 실행 (금액 정보 추출)
                _ocrState.value = OcrState.Loading("금액 정보 분석 중...")
                
                val ocrText: String = withContext(Dispatchers.IO) {
                    tesseractManager.performOcr(processedBitmap)
                }
                
                // OCR 결과를 ReceiptAmount로 변환
                val amountResult: ReceiptAmount? = parseOcrToReceiptAmount(ocrText)
                
                if (amountResult != null && amountResult.getMainAmount() != null) {
                    Log.d(TAG, "금액 추출 성공: ${amountResult.getFormattedAmount()}")
                    _receiptAmount.value = amountResult
                    _ocrState.value = OcrState.Success(amountResult)
                } else {
                    Log.w(TAG, "금액 정보를 찾을 수 없음")
                    _ocrState.value = OcrState.Error(
                        "영수증에서 금액 정보를 찾을 수 없습니다.\n" +
                        "• 영수증 하단의 합계/총액 부분이 선명하게 보이는지 확인해주세요\n" +
                        "• 조명이 충분한 곳에서 다시 촬영해주세요"
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "영수증 처리 중 오류: ${e.message}", e)
                _ocrState.value = OcrState.Error("영수증 처리 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }
    
    /**
     * 채팅방에 정산 결과 전송
     * @param chatId 채팅방 ID (옵션)
     * @param includeDetails 상세 내역 포함 여부
     *
    fun sendToChat(chatId: String?, includeDetails: Boolean = false) {
        // 메시지 상세 로깅
        val formattedAmount = receiptAmount.value?.getMainAmount()?.let { amount ->
            String.format("%,d", amount)
        } ?: "0"
        
        Log.d(TAG, "📤 채팅방 메시지 전송 시작")
        Log.d(TAG, "    📍 chatId: '$chatId'")
        Log.d(TAG, "    💰 금액: ${formattedAmount}원")
        
        // 기본 메시지 구성
        val receiptText = receiptAmount.value?.rawText ?: ""
        val amount = receiptAmount.value?.getMainAmount() ?: 0
        val items = receiptAmount.value?.items ?: emptyList()
        
        val message = if (includeDetails) {
            buildDetailedMessage(amount, items, receiptText)
        } else {
            buildBasicMessage(amount, receiptText)
        }
        
        Log.d(TAG, "    📝 message 길이: ${message.length}")
        Log.d(TAG, "    📝 message 내용: $message")
        
        // 브로드캐스트 인텐트 생성 (개선된 버전)
        val intent = Intent("com.example.andapp1.SEND_CHAT_MESSAGE").apply {
            putExtra("message", message)
            putExtra("chatId", chatId)
            putExtra("source", "ocr")
            putExtra("timestamp", System.currentTimeMillis())
            putExtra("amount", amount)
            
            // 💥 핵심 개선: 더 명시적인 브로드캐스트 설정
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(getApplication<Application>().packageName)  // 같은 패키지 내로 제한
        }
        
        Log.d(TAG, "📡 브로드캐스트 전송 중...")
        
        try {
            // 브로드캐스트 전송 (한 번만)
            getApplication<Application>().sendBroadcast(intent)
            
            Log.d(TAG, "✅ 브로드캐스트 전송 완료")
            
            // 결과 설정
            _chatSendResult.postValue("메시지 전송 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 브로드캐스트 전송 실패", e)
            _chatSendResult.postValue("전송 실패: ${e.message}")
        }
    }
    
    /**
     * 커스텀 메시지를 채팅방에 전송
     */
    fun sendCustomMessage(chatId: String?, customMessage: String) {
        Log.d(TAG, "📤 커스텀 메시지 전송 시작")
        Log.d(TAG, "    📍 chatId: '$chatId'")
        Log.d(TAG, "    📝 메시지: $customMessage")
        
        // 브로드캐스트 인텐트 생성
        val intent = Intent("com.example.andapp1.SEND_CHAT_MESSAGE").apply {
            putExtra("message", customMessage)
            putExtra("chatId", chatId)
            putExtra("source", "expense")
            putExtra("timestamp", System.currentTimeMillis())
            
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(getApplication<Application>().packageName)
        }
        
        try {
            getApplication<Application>().sendBroadcast(intent)
            Log.d(TAG, "✅ 커스텀 메시지 전송 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 커스텀 메시지 전송 실패", e)
        }
    }
    */
    /**
     * OCR 결과 직접 설정 (ROI 카메라용)
     */
    fun setOcrResult(receiptAmount: ReceiptAmount) {
        Log.d(TAG, "OCR 결과 직접 설정: ${receiptAmount.getFormattedAmount()}")
        _receiptAmount.value = receiptAmount
        _ocrState.value = OcrState.Success(receiptAmount)
    }
    
    /**
     * OCR 텍스트를 ReceiptAmount로 변환
     */
    private fun parseOcrToReceiptAmount(ocrText: String): ReceiptAmount? {
        if (ocrText.isBlank()) return null
        
        Log.d(TAG, "OCR 결과 파싱: $ocrText")
        
        // 금액 패턴 찾기 (한국어 통화 형식)
        val amountPattern = """(\d{1,3}(?:,\d{3})*)\s*원""".toRegex()
        val matches = amountPattern.findAll(ocrText)
        
        val amounts = mutableListOf<Pair<String, Int>>()
        var maxAmount = 0
        var totalAmountText = "0원"
        
        for (match in matches) {
            val amountText = match.value
            val numberText = match.groupValues[1].replace(",", "")
            val amount = numberText.toIntOrNull() ?: 0
            
            if (amount > 0) {
                amounts.add(amountText to amount)
                if (amount > maxAmount) {
                    maxAmount = amount
                    totalAmountText = amountText
                }
            }
        }
        
        return if (amounts.isNotEmpty()) {
            ReceiptAmount().apply {
                setMainAmount(maxAmount)
                rawText = ocrText
                items.addAll(amounts)
            }
        } else {
            null
        }
    }
    
    /**
     * 상태 초기화
     */
    fun resetState() {
        _ocrState.value = OcrState.Idle
        _processedImage.value = null
        _receiptAmount.value = null
        _chatSendResult.value = null
    }
    
    /**
     * 채팅 전송 결과 초기화
     */
    fun clearChatSendResult() {
        _chatSendResult.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel 정리 완료")
    }
    
    /**
     * 상세 메시지 구성
     */
    private fun buildDetailedMessage(amount: Int, items: List<Pair<String, Int>>, receiptText: String): String {
        val message = StringBuilder()
        
        // 헤더
        message.append("🧾 여행 경비 분석 결과\n")
        message.append("━━━━━━━━━━━━━━━\n")
        
        // 주요 금액
        message.append("💰 총 금액: ${String.format("%,d", amount)}원\n")
        
        // 상세 내역
        if (items.isNotEmpty()) {
            message.append("\n📝 항목별 내역:\n")
            items.take(5).forEachIndexed { index, (name, itemAmount) ->
                message.append("${index + 1}. $name: ${String.format("%,d", itemAmount)}원\n")
            }
            
            if (items.size > 5) {
                message.append("... 외 ${items.size - 5}개 항목\n")
            }
        }
        
        // 푸터
        message.append("\n📱 영수증 OCR로 자동 분석됨")
        
        return message.toString()
    }
    
    /**
     * 기본 메시지 구성
     */
    private fun buildBasicMessage(amount: Int, receiptText: String): String {
        return "🧾 여행 경비 분석 결과\n" +
                "━━━━━━━━━━━━━━━\n" +
                "💰 총 금액: ${String.format("%,d", amount)}원\n\n" +
                "📝 항목별 내역:\n" +
                "1. 금액 정보: ${String.format("%,d", amount)}원\n\n" +
                "📱 영수증 OCR로 자동 분석됨"
    }
}

/**
 * OCR 처리 상태
 */
sealed class OcrState {
    object Idle : OcrState()
    data class Loading(val message: String) : OcrState()
    data class Success(val result: ReceiptAmount) : OcrState()
    data class Error(val message: String) : OcrState()
}

/**
 * 채팅 전송 결과
 */
sealed class ChatSendResult {
    object Loading : ChatSendResult()
    data class Success(val message: String) : ChatSendResult()
    data class Error(val message: String) : ChatSendResult()
} 