package com.example.andapp1.expense

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.andapp1.RoomDatabaseInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 여행 경비 ViewModel (Room DB 연동)
 */
class TravelExpenseViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "TravelExpenseViewModel"
    }
    
    private val database = RoomDatabaseInstance.getInstance(application)
    private val expenseDao = database.expenseDao()
    
    private val _expenses = MutableLiveData<List<ExpenseItem>>()
    val expenses: LiveData<List<ExpenseItem>> = _expenses
    
    private val _totalAmount = MutableLiveData<Int>()
    val totalAmount: LiveData<Int> = _totalAmount
    
    private val _expenseCount = MutableLiveData<Int>()
    val expenseCount: LiveData<Int> = _expenseCount
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    // LiveData for category expenses
    private val _categoryExpenses = MutableLiveData<Map<String, List<ExpenseItem>>>()
    val categoryExpenses: LiveData<Map<String, List<ExpenseItem>>> = _categoryExpenses
    
    // Current chatId
    private var currentChatId: String = ""
    
    /**
     * ChatId 설정
     */
    fun setChatId(chatId: String) {
        currentChatId = chatId
        loadExpenses(chatId)
        loadCategoryExpenses(chatId)
    }
    
    /**
     * 채팅방별 경비 데이터 로드
     */
    fun loadExpenses(chatId: String) {
        Log.d(TAG, "경비 데이터 로드 시작: chatId=$chatId")
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val expenses: List<ExpenseItem> = withContext(Dispatchers.IO) {
                    expenseDao.getExpensesByChatIdSync(chatId)
                }
                
                _expenses.value = expenses
                _expenseCount.value = expenses.size
                _totalAmount.value = expenses.sumOf { it.amount }
                
                Log.d(TAG, "✅ 경비 데이터 로드 완료: ${expenses.size}건, 총액: ${_totalAmount.value}원")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 경비 데이터 로드 실패", e)
                _errorMessage.value = "경비 데이터 로드에 실패했습니다: ${e.message}"
                _expenses.value = emptyList()
                _totalAmount.value = 0
                _expenseCount.value = 0
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 카테고리별 경비 데이터 로드
     */
    private fun loadCategoryExpenses(chatId: String) {
        viewModelScope.launch {
            try {
                val expenses: List<ExpenseItem> = withContext(Dispatchers.IO) {
                    expenseDao.getExpensesByChatIdSync(chatId)
                }
                
                val categoryMap = expenses.groupBy { it.category }
                _categoryExpenses.value = categoryMap
                
            } catch (e: Exception) {
                Log.e(TAG, "카테고리별 경비 로드 실패", e)
                _categoryExpenses.value = emptyMap()
            }
        }
    }
    
    /**
     * 채팅방별 총 경비 금액
     */
    suspend fun getTotalAmount(chatId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                expenseDao.getTotalAmountByChatId(chatId) ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "총 금액 조회 실패", e)
                0
            }
        }
    }
    
    /**
     * 채팅방별 경비 항목 수
     */
    suspend fun getExpenseCount(chatId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                expenseDao.getExpenseCountByChatId(chatId)
            } catch (e: Exception) {
                Log.e(TAG, "경비 항목 수 조회 실패", e)
                0
            }
        }
    }
    
    /**
     * 채팅방 참여자 수 (임시)
     */
    suspend fun getParticipantCount(chatId: String): Int {
        return try {
            // TODO: Firebase에서 실제 참여자 수 가져오기
            2 // 임시로 2명
        } catch (e: Exception) {
            Log.e(TAG, "참여자 수 로드 실패", e)
            2
        }
    }
    
    /**
     * 카테고리별 경비 합계
     */
    fun getExpensesByCategory(chatId: String): LiveData<Map<String, Int>> {
        val result = MutableLiveData<Map<String, Int>>()
        
        viewModelScope.launch {
            try {
                val expenses: List<ExpenseItem> = withContext(Dispatchers.IO) {
                    expenseDao.getExpensesByChatIdSync(chatId)
                }
                
                val categoryTotals: Map<String, Int> = expenses.groupBy { expense -> expense.category }
                    .mapValues { (_, expenseList) -> expenseList.sumOf { expense -> expense.amount } }
                
                result.value = categoryTotals
                
            } catch (e: Exception) {
                Log.e(TAG, "카테고리별 경비 조회 실패", e)
                result.value = emptyMap()
            }
        }
        
        return result
    }
    
    /**
     * 데이터 새로고침
     */
    fun refreshData(chatId: String) {
        loadExpenses(chatId)
    }
    
    /**
     * 경비 항목 추가
     */
    suspend fun addExpense(expense: ExpenseItem) {
        Log.d(TAG, "경비 추가: ${expense.getDisplayDescription()}, 금액: ${expense.amount}원")
        
        withContext(Dispatchers.IO) {
            try {
                expenseDao.insertExpense(expense)
                Log.d(TAG, "✅ 경비 추가 성공")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 경비 추가 실패", e)
                throw e
            }
        }
    }
    
    /**
     * 경비 수정
     */
    fun updateExpense(oldExpense: ExpenseItem, newExpense: ExpenseItem) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 기존 경비 삭제 후 새 경비 추가
                    expenseDao.deleteExpense(oldExpense)
                    expenseDao.insertExpense(newExpense.copy(id = oldExpense.id))
                }
                Log.d(TAG, "✅ 경비 수정 완료: ${newExpense.description}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 경비 수정 실패", e)
                _errorMessage.value = "경비 수정에 실패했습니다: ${e.message}"
            }
        }
    }
    
    /**
     * 경비 삭제
     */
    suspend fun deleteExpense(expense: ExpenseItem) {
        withContext(Dispatchers.IO) {
            expenseDao.deleteExpense(expense)
        }
    }
    
    /**
     * 카테고리별 경비 조회 (전체 목록)
     */
    suspend fun getAllExpensesForCategory(chatId: String): List<ExpenseItem> {
        return withContext(Dispatchers.IO) {
            expenseDao.getExpensesByChatIdSync(chatId)
        }
    }
}

/**
 * ViewModel Factory
 */
class TravelExpenseViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TravelExpenseViewModel::class.java)) {
            return TravelExpenseViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 