package com.example.andapp1.expense

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
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
        private val categoryIcon: TextView = itemView.findViewById(R.id.categoryIcon)
        private val categoryName: TextView = itemView.findViewById(R.id.categoryName)
        private val itemCount: TextView = itemView.findViewById(R.id.itemCount)
        private val totalAmount: TextView = itemView.findViewById(R.id.totalAmount)
        private val progressBar: View = itemView.findViewById(R.id.progressBar)
        
        fun bind(item: CategoryExpenseItem) {
            // 카테고리 아이콘 및 이름 설정
            val (icon, name) = getCategoryDisplayInfo(item.category)
            categoryIcon.text = icon
            categoryName.text = name
            
            // 항목 수 표시
            itemCount.text = "${item.itemCount}개 항목"
            
            // 금액 표시
            totalAmount.text = formatCurrency(item.totalAmount)
            
            // 프로그레스 바는 나중에 구현 (전체 대비 비율)
            // progressBar.layoutParams.width = calculateProgressWidth(item.totalAmount)
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