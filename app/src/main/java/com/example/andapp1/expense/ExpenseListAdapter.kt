package com.example.andapp1.expense

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.andapp1.R

/**
 * 경비 목록 어댑터
 */
class ExpenseListAdapter(
    private val onItemClick: (ExpenseItem) -> Unit
) : ListAdapter<ExpenseItem, ExpenseListAdapter.ExpenseViewHolder>(ExpenseDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ExpenseViewHolder(view, onItemClick)
    }
    
    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ExpenseViewHolder(
        itemView: View,
        private val onItemClick: (ExpenseItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val titleText: TextView = itemView.findViewById(android.R.id.text1)
        private val subtitleText: TextView = itemView.findViewById(android.R.id.text2)
        
        fun bind(expense: ExpenseItem) {
            titleText.text = expense.getFormattedAmount()
            subtitleText.text = expense.getDisplayDescription()
            
            itemView.setOnClickListener {
                onItemClick(expense)
            }
        }
    }
}

/**
 * DiffUtil Callback
 */
class ExpenseDiffCallback : DiffUtil.ItemCallback<ExpenseItem>() {
    override fun areItemsTheSame(oldItem: ExpenseItem, newItem: ExpenseItem): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: ExpenseItem, newItem: ExpenseItem): Boolean {
        return oldItem == newItem
    }
} 