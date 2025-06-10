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
import java.text.SimpleDateFormat
import java.util.*

class ExpenseDetailAdapter : ListAdapter<ExpenseItem, ExpenseDetailAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val expenseTitle: TextView = itemView.findViewById(R.id.expenseTitle)
        private val expenseDate: TextView = itemView.findViewById(R.id.expenseDate)
        private val expensePayers: TextView = itemView.findViewById(R.id.expensePayers)
        private val expenseAmount: TextView = itemView.findViewById(R.id.expenseAmount)

        fun bind(expense: ExpenseItem) {
            expenseTitle.text = expense.getDisplayDescription()
            expenseDate.text = formatDate(expense.createdAt)
            expensePayers.text = expense.userName.ifEmpty { "알 수 없음" }
            expenseAmount.text = expense.getFormattedAmount()
        }

        private fun formatDate(date: Date): String {
            val sdf = SimpleDateFormat("MM.dd", Locale.getDefault())
            return sdf.format(date)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ExpenseItem>() {
        override fun areItemsTheSame(oldItem: ExpenseItem, newItem: ExpenseItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ExpenseItem, newItem: ExpenseItem): Boolean {
            return oldItem == newItem
        }
    }
} 