package com.example.andapp1.expense

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ExpensePagerAdapter(
    fragmentActivity: FragmentActivity,
    private val chatId: String
) : FragmentStateAdapter(fragmentActivity) {
    
    override fun getItemCount(): Int = 3
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AllExpensesFragment.newInstance(chatId)
            1 -> CategoryExpensesFragment.newInstance(chatId)
            2 -> SettlementFragment.newInstance(chatId)
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
} 