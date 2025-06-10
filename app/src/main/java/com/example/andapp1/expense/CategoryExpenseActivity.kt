package com.example.andapp1.expense

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import android.widget.ImageView
import com.example.andapp1.R
import java.text.NumberFormat
import java.util.*

class CategoryExpenseActivity : AppCompatActivity() {
    
    private lateinit var viewModel: TravelExpenseViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoryExpenseAdapter
    private lateinit var totalAmountText: TextView
    private lateinit var backButton: ImageView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_expense)
        
        setupViews()
        setupViewModel()
        setupRecyclerView()
        observeData()
    }
    
    private fun setupViews() {
        recyclerView = findViewById(R.id.categoryRecyclerView)
        totalAmountText = findViewById(R.id.totalAmountText)
        backButton = findViewById(R.id.backButton)
        
        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[TravelExpenseViewModel::class.java]
        
        // ChatId 받아오기
        val chatId = intent.getStringExtra("chatId") ?: ""
        if (chatId.isNotEmpty()) {
            viewModel.setChatId(chatId)
        }
    }
    
    private fun setupRecyclerView() {
        adapter = CategoryExpenseAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun observeData() {
        viewModel.categoryExpenses.observe(this) { categoryMap ->
            val categoryList = categoryMap.toList().map { (category, expenses) ->
                CategoryExpenseItem(
                    category = category,
                    totalAmount = expenses.sumOf { it.amount },
                    itemCount = expenses.size,
                    expenses = expenses
                )
            }.sortedByDescending { it.totalAmount }
            
            adapter.submitList(categoryList)
            
            // 총 금액 계산 및 표시
            val totalAmount = categoryList.sumOf { it.totalAmount }
            totalAmountText.text = formatCurrency(totalAmount)
        }
    }
    
    private fun formatCurrency(amount: Int): String {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"
    }
}

data class CategoryExpenseItem(
    val category: String,
    val totalAmount: Int,
    val itemCount: Int,
    val expenses: List<ExpenseItem>
) 