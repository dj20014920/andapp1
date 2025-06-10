package com.example.andapp1.ocr

import java.text.NumberFormat
import java.util.Locale

/**
 * 영수증에서 추출된 금액 정보를 담는 데이터 클래스
 */
data class ReceiptAmount(
    val rawText: String = "",
    val extractedAmount: Int = 0,
    val currency: String = "KRW",
    val confidence: Float = 0f,
    val position: Pair<Int, Int>? = null, // 텍스트 위치 좌표
    val category: AmountCategory = AmountCategory.UNKNOWN
) {
    
    /**
     * 포맷된 금액 문자열 반환
     */
    fun getFormattedAmount(): String {
        return if (extractedAmount > 0) {
            NumberFormat.getNumberInstance(Locale.KOREA).format(extractedAmount) + "원"
        } else {
            "0원"
        }
    }
    
    /**
     * 달러 표시 포맷
     */
    fun getFormattedAmountWithSymbol(): String {
        return when (currency) {
            "USD" -> "$${NumberFormat.getNumberInstance(Locale.US).format(extractedAmount)}"
            "KRW" -> "₩${NumberFormat.getNumberInstance(Locale.KOREA).format(extractedAmount)}"
            else -> getFormattedAmount()
        }
    }
    
    /**
     * 유효한 금액인지 확인
     */
    fun isValid(): Boolean {
        return extractedAmount > 0 && confidence > 0.3f
    }
    
    /**
     * 신뢰도 레벨 반환
     */
    fun getConfidenceLevel(): ConfidenceLevel {
        return when {
            confidence >= 0.8f -> ConfidenceLevel.HIGH
            confidence >= 0.5f -> ConfidenceLevel.MEDIUM
            confidence >= 0.3f -> ConfidenceLevel.LOW
            else -> ConfidenceLevel.VERY_LOW
        }
    }
}

/**
 * 금액 카테고리 열거형
 */
enum class AmountCategory {
    SUBTOTAL,    // 소계
    TAX,         // 세금
    TOTAL,       // 총계
    DISCOUNT,    // 할인
    TIP,         // 팁
    CHANGE,      // 거스름돈
    UNKNOWN      // 미분류
}

/**
 * 신뢰도 레벨 열거형
 */
enum class ConfidenceLevel {
    HIGH,        // 높음 (80% 이상)
    MEDIUM,      // 보통 (50-80%)
    LOW,         // 낮음 (30-50%)
    VERY_LOW     // 매우 낮음 (30% 미만)
}

/**
 * 여러 금액 정보를 관리하는 클래스
 */
data class ReceiptAmountList(
    val amounts: List<ReceiptAmount> = emptyList()
) {
    
    /**
     * 가장 신뢰도가 높은 총액 반환
     */
    fun getBestTotalAmount(): ReceiptAmount? {
        return amounts
            .filter { it.category == AmountCategory.TOTAL || it.category == AmountCategory.UNKNOWN }
            .maxByOrNull { it.confidence }
    }
    
    /**
     * 유효한 금액들만 필터링
     */
    fun getValidAmounts(): List<ReceiptAmount> {
        return amounts.filter { it.isValid() }
    }
    
    /**
     * 카테고리별 금액 반환
     */
    fun getAmountsByCategory(category: AmountCategory): List<ReceiptAmount> {
        return amounts.filter { it.category == category }
    }
    
    /**
     * 가장 큰 금액 반환 (보통 총액)
     */
    fun getLargestAmount(): ReceiptAmount? {
        return amounts.maxByOrNull { it.extractedAmount }
    }
} 