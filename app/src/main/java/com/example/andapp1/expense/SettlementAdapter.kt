package com.example.andapp1.expense

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.andapp1.R
import java.text.NumberFormat
import java.util.*

class SettlementAdapter : ListAdapter<SettlementItem, SettlementAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_settlement, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userNameText: TextView = itemView.findViewById(R.id.userNameText)
        private val paidAmountText: TextView = itemView.findViewById(R.id.paidAmountText)
        private val shouldPayText: TextView = itemView.findViewById(R.id.shouldPayText)
        private val balanceText: TextView = itemView.findViewById(R.id.balanceText)
        private val statusIcon: TextView = itemView.findViewById(R.id.statusIcon)
        
        fun bind(item: SettlementItem) {
            userNameText.text = item.userName
            paidAmountText.text = "지출: ${formatCurrency(item.paidAmount)}"
            shouldPayText.text = "분담: ${formatCurrency(item.shouldPay)}"
            
            when (item.settlementType) {
                SettlementType.RECEIVE -> {
                    balanceText.text = "사용 금액: ${formatCurrency(item.balance)}"
                    balanceText.setTextColor(ContextCompat.getColor(itemView.context, R.color.success))
                    statusIcon.text = "💰"
                }
                SettlementType.PAY -> {
                    balanceText.text = "사용 금액: ${formatCurrency(item.balance)}"
                    balanceText.setTextColor(ContextCompat.getColor(itemView.context, R.color.error_color))
                    statusIcon.text = "💸"
                }
                SettlementType.BALANCED -> {
                    balanceText.text = "정산 완료"
                    balanceText.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                    statusIcon.text = "✅"
                }
            }
        }
        
        private fun formatCurrency(amount: Int): String {
            return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<SettlementItem>() {
        override fun areItemsTheSame(oldItem: SettlementItem, newItem: SettlementItem): Boolean {
            return oldItem.userName == newItem.userName
        }
        
        override fun areContentsTheSame(oldItem: SettlementItem, newItem: SettlementItem): Boolean {
            return oldItem == newItem
        }
    }
} 