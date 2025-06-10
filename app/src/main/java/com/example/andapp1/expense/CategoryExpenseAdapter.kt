package com.example.andapp1.expense

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.andapp1.R
import java.text.NumberFormat
import java.util.*

class CategoryExpenseAdapter : ListAdapter<CategoryExpenseItem, CategoryExpenseAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_expense, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryHeader: View = itemView.findViewById(R.id.categoryHeader)
        private val categoryIcon: TextView = itemView.findViewById(R.id.categoryIcon)
        private val categoryName: TextView = itemView.findViewById(R.id.categoryName)
        private val itemCount: TextView = itemView.findViewById(R.id.itemCount)
        private val totalAmount: TextView = itemView.findViewById(R.id.totalAmount)
        private val expandArrow: ImageView = itemView.findViewById(R.id.expandArrow)
        private val progressBar: View = itemView.findViewById(R.id.progressBar)
        private val expenseDetailsRecyclerView: RecyclerView = itemView.findViewById(R.id.expenseDetailsRecyclerView)
        
        private var isExpanded = false
        private val expenseDetailAdapter = ExpenseDetailAdapter()
        
        init {
            // 세부 항목 RecyclerView 설정
            expenseDetailsRecyclerView.apply {
                layoutManager = LinearLayoutManager(itemView.context)
                adapter = expenseDetailAdapter
                isNestedScrollingEnabled = false
            }
        }
        
        fun bind(item: CategoryExpenseItem) {
            // 카테고리 아이콘 및 이름 설정
            val (icon, name) = getCategoryDisplayInfo(item.category)
            categoryIcon.text = icon
            categoryName.text = name
            
            // 항목 수 표시
            itemCount.text = "${item.itemCount}개 항목"
            
            // 금액 표시
            totalAmount.text = formatCurrency(item.totalAmount)
            
            // 세부 항목 데이터 설정
            expenseDetailAdapter.submitList(item.expenses)
            
            // 헤더 클릭 리스너
            categoryHeader.setOnClickListener {
                toggleExpansion()
            }
            
            // 초기 상태 설정
            updateExpandedState(false)
        }
        
        private fun toggleExpansion() {
            isExpanded = !isExpanded
            updateExpandedState(true)
        }
        
        private fun updateExpandedState(animate: Boolean) {
            if (animate) {
                // 화살표 회전 애니메이션
                val rotation = if (isExpanded) 180f else 0f
                ObjectAnimator.ofFloat(expandArrow, "rotation", rotation).apply {
                    duration = 200
                    start()
                }
                
                // 세부 항목 표시/숨김 애니메이션
                if (isExpanded) {
                    expenseDetailsRecyclerView.visibility = View.VISIBLE
                    expenseDetailsRecyclerView.alpha = 0f
                    expenseDetailsRecyclerView.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                } else {
                    expenseDetailsRecyclerView.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            expenseDetailsRecyclerView.visibility = View.GONE
                        }
                        .start()
                }
            } else {
                // 초기 상태 설정 (애니메이션 없음)
                expandArrow.rotation = if (isExpanded) 180f else 0f
                expenseDetailsRecyclerView.visibility = if (isExpanded) View.VISIBLE else View.GONE
                expenseDetailsRecyclerView.alpha = if (isExpanded) 1f else 0f
            }
        }
        
        private fun getCategoryDisplayInfo(category: String): Pair<String, String> {
            return when (category) {
                "🍽️ 식비" -> "🍽️" to "식비"
                "☕ 카페" -> "☕" to "카페"
                "🏨 숙박" -> "🏨" to "숙박"
                "🚗 교통비" -> "🚗" to "교통비"
                "⛽ 주유" -> "⛽" to "주유"
                "🚙 렌트카" -> "🚙" to "렌트카"
                "🎢 관광/액티비티" -> "🎢" to "관광/액티비티"
                "🛒 마트/편의점" -> "🛒" to "마트/편의점"
                else -> "💰" to category
            }
        }
        
        private fun formatCurrency(amount: Int): String {
            return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<CategoryExpenseItem>() {
        override fun areItemsTheSame(oldItem: CategoryExpenseItem, newItem: CategoryExpenseItem): Boolean {
            return oldItem.category == newItem.category
        }
        
        override fun areContentsTheSame(oldItem: CategoryExpenseItem, newItem: CategoryExpenseItem): Boolean {
            return oldItem == newItem
        }
    }
} 