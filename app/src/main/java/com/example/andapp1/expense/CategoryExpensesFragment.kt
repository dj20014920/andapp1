package com.example.andapp1.expense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.andapp1.R

class CategoryExpensesFragment : Fragment() {

    private var chatId: String = ""

    companion object {
        fun newInstance(chatId: String): CategoryExpensesFragment {
            val fragment = CategoryExpensesFragment()
            val args = Bundle()
            args.putString("chatId", chatId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            chatId = it.getString("chatId") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_category_expenses, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: 카테고리별 경비 로직 구현
    }
} 