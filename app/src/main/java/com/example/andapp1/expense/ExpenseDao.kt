package com.example.andapp1.expense

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    
    @Query("SELECT * FROM expenses WHERE chatId = :chatId ORDER BY createdAt DESC")
    fun getExpensesForChat(chatId: String): Flow<List<ExpenseItem>>
    
    @Query("SELECT * FROM expenses WHERE chatId = :chatId AND category = :category ORDER BY createdAt DESC")
    fun getExpensesByCategory(chatId: String, category: String): Flow<List<ExpenseItem>>
    
    @Query("SELECT * FROM expenses WHERE chatId = :chatId AND isSettled = 0 ORDER BY createdAt DESC")
    fun getUnsettledExpenses(chatId: String): Flow<List<ExpenseItem>>
    
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): ExpenseItem?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseItem): Long
    
    @Update
    suspend fun updateExpense(expense: ExpenseItem)
    
    @Delete
    suspend fun deleteExpense(expense: ExpenseItem)
    
    @Query("DELETE FROM expenses WHERE chatId = :chatId")
    suspend fun deleteAllExpensesForChat(chatId: String)
    
    @Query("SELECT SUM(amount) FROM expenses WHERE chatId = :chatId AND isSettled = 0")
    suspend fun getTotalUnsettledAmount(chatId: String): Int
    
    @Query("SELECT SUM(amount) FROM expenses WHERE chatId = :chatId AND category = :category")
    suspend fun getTotalAmountByCategory(chatId: String, category: String): Int
    
    @Query("UPDATE expenses SET isSettled = 1 WHERE chatId = :chatId")
    suspend fun settleAllExpenses(chatId: String)
    
    @Query("SELECT DISTINCT category FROM expenses WHERE chatId = :chatId")
    suspend fun getCategoriesForChat(chatId: String): List<String>
} 