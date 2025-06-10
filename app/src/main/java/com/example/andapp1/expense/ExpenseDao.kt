package com.example.andapp1.expense

import androidx.lifecycle.LiveData
import androidx.room.*
import java.util.Date

/**
 * 경비 항목 DAO
 */
@Dao
interface ExpenseDao {
    
    @Query("SELECT * FROM expense_items WHERE chatId = :chatId ORDER BY createdAt DESC")
    fun getExpensesByChatId(chatId: String): LiveData<List<ExpenseItem>>
    
    @Query("SELECT * FROM expense_items WHERE chatId = :chatId ORDER BY createdAt DESC")
    suspend fun getExpensesByChatIdSync(chatId: String): List<ExpenseItem>
    
    @Query("SELECT SUM(amount) FROM expense_items WHERE chatId = :chatId")
    suspend fun getTotalAmountByChatId(chatId: String): Int?
    
    @Query("SELECT COUNT(*) FROM expense_items WHERE chatId = :chatId")
    suspend fun getExpenseCountByChatId(chatId: String): Int
    
    @Insert
    suspend fun insertExpense(expense: ExpenseItem)
    
    @Update
    suspend fun updateExpense(expense: ExpenseItem)
    
    @Delete
    suspend fun deleteExpense(expense: ExpenseItem)
    
    @Query("DELETE FROM expense_items WHERE chatId = :chatId")
    suspend fun deleteAllExpensesByChatId(chatId: String)
    
    @Query("SELECT * FROM expense_items WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: String): ExpenseItem?
    
    @Query("SELECT DISTINCT category FROM expense_items WHERE chatId = :chatId")
    suspend fun getCategoriesByChatId(chatId: String): List<String>
    
    @Query("SELECT COUNT(*) FROM expense_items WHERE chatId = :chatId")
    suspend fun getExpenseCount(chatId: String): Int
    
    @Query("SELECT * FROM expense_items WHERE chatId = :chatId AND createdAt >= :sinceTime ORDER BY createdAt DESC")
    suspend fun getRecentExpenses(chatId: String, sinceTime: java.util.Date): List<ExpenseItem>
}

/**
 * 날짜 타입 컨버터
 */
class DateConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
} 