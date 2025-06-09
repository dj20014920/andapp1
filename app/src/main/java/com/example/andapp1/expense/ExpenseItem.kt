package com.example.andapp1.expense

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 여행 경비 항목 데이터 클래스
 */
@Entity(tableName = "expense_items")
data class ExpenseItem(
    @PrimaryKey
    val id: String = "",
    val chatId: String = "",
    val amount: Int = 0,
    val description: String = "",
    val category: String = "기타",
    val createdAt: Date = Date(),
    val userId: String = "",
    val userName: String = "",
    val ocrText: String = "",
    val imageUri: String? = null
) {
    /**
     * 포맷된 금액 문자열 반환
     */
    fun getFormattedAmount(): String {
        return "${String.format("%,d", amount)}원"
    }
    
    /**
     * 표시용 설명 반환 (빈 경우 "금액 정보"로 표시)
     */
    fun getDisplayDescription(): String {
        return if (description.isBlank()) "금액 정보" else description
    }
}

/**
 * 경비 카테고리 열거형
 */
enum class ExpenseCategory(val displayName: String, val emoji: String) {
    FOOD("식비", "🍽️"),
    TRANSPORT("교통비", "🚗"),
    ACCOMMODATION("숙박비", "🏨"),
    ACTIVITY("액티비티", "🎢"),
    SHOPPING("쇼핑", "🛍️"),
    OTHER("기타", "💼");
    
    companion object {
        fun fromDisplayName(displayName: String): ExpenseCategory {
            return values().find { it.displayName == displayName } ?: OTHER
        }
    }
} 