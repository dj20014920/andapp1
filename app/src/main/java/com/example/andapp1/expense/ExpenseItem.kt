package com.example.andapp1.expense

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.NumberFormat
import java.util.*

@Entity(tableName = "expenses")
data class ExpenseItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: String = "",
    val userName: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val category: String = "기타",
    val createdAt: Date = Date(),
    val participants: List<String> = emptyList(),
    val isSettled: Boolean = false
) {
    fun getDisplayDescription(): String {
        return description.ifEmpty { "항목 없음" }
    }
    
    fun getFormattedAmount(): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.KOREA)
        return formatter.format(amount).replace("₩", "") + "원"
    }
    
    fun getCategoryEmoji(): String {
        return when (category) {
            "식비" -> "🍽️"
            "교통비" -> "🚗"
            "숙박비" -> "🏨"
            "관광비" -> "🎡"
            "쇼핑" -> "🛍️"
            "기타" -> "💼"
            else -> "��"
        }
    }
} 