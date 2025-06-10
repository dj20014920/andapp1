package com.example.andapp1.expense

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.andapp1.RoomDatabaseInstance
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class TravelExpenseViewModel(application: Application) : AndroidViewModel(application) {
    
    // ExpenseDao는 일단 주석 처리하고 기본 기능만 유지
    // private val database = RoomDatabaseInstance.getInstance(application)
    // private val expenseDao = database.expenseDao()
    
    private val _chatId = MutableLiveData<String>()
    val chatId: LiveData<String> = _chatId
    
    private val _totalAmount = MutableLiveData<Int>()
    val totalAmount: LiveData<Int> = _totalAmount
    
    private val _unsettledAmount = MutableLiveData<Int>()
    val unsettledAmount: LiveData<Int> = _unsettledAmount
    
    private val _categories = MutableLiveData<List<String>>()
    val categories: LiveData<List<String>> = _categories
    
    fun setChatId(chatId: String) {
        _chatId.value = chatId
        loadCategories()
        calculateTotals()
    }
    
    fun getExpensesForChat(chatId: String): LiveData<List<ExpenseItem>> {
        // return expenseDao.getExpensesForChat(chatId).asLiveData()
        return MutableLiveData(emptyList())
    }
    
    fun getExpensesByCategory(chatId: String, category: String): LiveData<List<ExpenseItem>> {
        // return expenseDao.getExpensesByCategory(chatId, category).asLiveData()
        return MutableLiveData(emptyList())
    }
    
    fun getUnsettledExpenses(chatId: String): LiveData<List<ExpenseItem>> {
        // return expenseDao.getUnsettledExpenses(chatId).asLiveData()
        return MutableLiveData(emptyList())
    }
    
    fun addExpense(expense: ExpenseItem) {
        viewModelScope.launch {
            // expenseDao.insertExpense(expense)
            calculateTotals()
        }
    }
    
    fun updateExpense(expense: ExpenseItem) {
        viewModelScope.launch {
            // expenseDao.updateExpense(expense)
            calculateTotals()
        }
    }
    
    fun deleteExpense(expense: ExpenseItem) {
        viewModelScope.launch {
            // expenseDao.deleteExpense(expense)
            calculateTotals()
        }
    }
    
    fun settleAllExpenses() {
        val currentChatId = _chatId.value ?: return
        viewModelScope.launch {
            // expenseDao.settleAllExpenses(currentChatId)
            calculateTotals()
        }
    }
    
    private fun loadCategories() {
        val currentChatId = _chatId.value ?: return
        viewModelScope.launch {
            // val categoryList = expenseDao.getCategoriesForChat(currentChatId)
            _categories.value = emptyList()
        }
    }
    
    private fun calculateTotals() {
        val currentChatId = _chatId.value ?: return
        viewModelScope.launch {
            // val total = expenseDao.getTotalUnsettledAmount(currentChatId) ?: 0
            // val unsettled = expenseDao.getTotalUnsettledAmount(currentChatId) ?: 0
            
            _totalAmount.value = 0
            _unsettledAmount.value = 0
        }
    }
    
    fun getFormattedAmount(amount: Int): String {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"
    }
    
    fun getTotalAmountByCategory(chatId: String, category: String, callback: (Int) -> Unit) {
        viewModelScope.launch {
            // val amount = expenseDao.getTotalAmountByCategory(chatId, category)
            callback(0)
        }
    }
} 