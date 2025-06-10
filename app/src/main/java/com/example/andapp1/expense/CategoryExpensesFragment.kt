package com.example.andapp1.expense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.andapp1.databinding.FragmentCategoryExpensesBinding
import java.text.NumberFormat
import java.util.*

class CategoryExpensesFragment : Fragment() {
    
    companion object {
        private const val ARG_CHAT_ID = "chatId"
        
        fun newInstance(chatId: String): CategoryExpensesFragment {
            val fragment = CategoryExpensesFragment()
            val args = Bundle()
            args.putString(ARG_CHAT_ID, chatId)
            fragment.arguments = args
            return fragment
        }
    }
    
    private var _binding: FragmentCategoryExpensesBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: TravelExpenseViewModel
    private lateinit var adapter: CategoryExpenseAdapter
    private var chatId: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = arguments?.getString(ARG_CHAT_ID) ?: ""
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViewModel()
        setupRecyclerView()
        observeData()
    }
    
    private fun setupViewModel() {
        viewModel = ViewModelProvider(
            requireActivity(), 
            TravelExpenseViewModelFactory(requireActivity().application)
        )[TravelExpenseViewModel::class.java]
        
        if (chatId.isNotEmpty()) {
            viewModel.setChatId(chatId)
        }
    }
    
    private fun setupRecyclerView() {
        adapter = CategoryExpenseAdapter()
        binding.categoryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.categoryRecyclerView.adapter = adapter
    }
    
    private fun observeData() {
        viewModel.categoryExpenses.observe(viewLifecycleOwner) { categoryMap ->
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
            binding.totalAmountText.text = formatCurrency(totalAmount)
            
            // 빈 상태 처리
            updateEmptyState(categoryList.isEmpty())
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.categoryRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun formatCurrency(amount: Int): String {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 