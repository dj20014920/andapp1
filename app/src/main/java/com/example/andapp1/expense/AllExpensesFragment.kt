package com.example.andapp1.expense

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.andapp1.DialogHelper
import com.example.andapp1.R
import com.example.andapp1.databinding.FragmentAllExpensesBinding
import com.example.andapp1.ocr.OcrActivity
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class AllExpensesFragment : Fragment() {
    
    companion object {
        private const val TAG = "AllExpensesFragment"
        private const val REQUEST_OCR_RESULT = 1
        private const val ARG_CHAT_ID = "chatId"
        
        fun newInstance(chatId: String): AllExpensesFragment {
            val fragment = AllExpensesFragment()
            val args = Bundle()
            args.putString(ARG_CHAT_ID, chatId)
            fragment.arguments = args
            return fragment
        }
    }
    
    private var _binding: FragmentAllExpensesBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: TravelExpenseViewModel
    private lateinit var expenseAdapter: ExpenseListAdapter
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
        _binding = FragmentAllExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViewModel()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        // 데이터 로드
        if (chatId.isNotEmpty()) {
            viewModel.setChatId(chatId)
        }
    }
    
    private fun setupViewModel() {
        viewModel = ViewModelProvider(
            requireActivity(), 
            TravelExpenseViewModelFactory(requireActivity().application)
        )[TravelExpenseViewModel::class.java]
    }
    
    private fun setupRecyclerView() {
        expenseAdapter = ExpenseListAdapter { expense ->
            showExpenseDetailDialog(expense)
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = expenseAdapter
        }
    }
    
    private fun setupClickListeners() {
        // 경비 추가 버튼
        binding.fabAddExpense.setOnClickListener {
            showAddExpenseOptions()
        }
    }
    
    private fun observeViewModel() {
        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            Log.d(TAG, "경비 목록 업데이트: ${expenses.size}개")
            expenseAdapter.submitList(expenses)
            updateSummaryCard(expenses)
            updateEmptyState(expenses.isEmpty())
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateSummaryCard(expenses: List<ExpenseItem>) {
        val totalAmount = expenses.sumOf { it.amount }
        val itemCount = expenses.size
        
        binding.totalAmountText.text = formatCurrency(totalAmount)
        binding.itemCountText.text = "${itemCount}개 항목"
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun showAddExpenseOptions() {
        val options = arrayOf(
            "📸 영수증 촬영",
            "🖼️ 갤러리에서 선택", 
            "✏️ 직접 입력"
        )
        
        DialogHelper.showStyledChoiceDialog(
            context = requireContext(),
            title = "💸 경비 추가 방법",
            options = options
        ) { which ->
            when (which) {
                0, 1 -> {
                    val intent = Intent(requireContext(), OcrActivity::class.java).apply {
                        putExtra(OcrActivity.EXTRA_CHAT_ID, chatId)
                        putExtra(OcrActivity.EXTRA_AUTO_SEND, false)
                    }
                    startActivityForResult(intent, REQUEST_OCR_RESULT)
                }
                2 -> {
                    showManualInputDialog()
                }
            }
        }
    }
    
    private fun showManualInputDialog(prefilledAmount: Int = 0, prefilledDescription: String = "") {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null)
        val amountEditText = dialogView.findViewById<EditText>(R.id.editTextAmount)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.editTextDescription)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        
        // OCR 결과로 미리 채우기
        if (prefilledAmount > 0) {
            amountEditText.setText(prefilledAmount.toString())
        }
        if (prefilledDescription.isNotEmpty()) {
            descriptionEditText.setText(prefilledDescription)
        }
        
        // 카테고리 스피너 설정
        val categories = arrayOf(
            "🍽️ 식비", "☕ 카페", "🏨 숙박", "🚗 교통비", 
            "⛽ 주유", "🚙 렌트카", "🎢 관광/액티비티", "🛒 마트/편의점"
        )
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("💸 경비 추가")
            .setView(dialogView)
            .setPositiveButton("추가") { _, _ ->
                val amount = amountEditText.text.toString().toIntOrNull()
                val description = descriptionEditText.text.toString().trim()
                val category = categories[categorySpinner.selectedItemPosition]
                
                if (amount != null && amount > 0) {
                    val finalDescription = if (description.isBlank()) {
                        when (category) {
                            "🍽️ 식비" -> "식당"
                            "☕ 카페" -> "카페"
                            "🏨 숙박" -> "숙박비"
                            "🚗 교통비" -> "교통비"
                            "⛽ 주유" -> "주유비"
                            "🚙 렌트카" -> "렌트카"
                            "🎢 관광/액티비티" -> "관광"
                            "🛒 마트/편의점" -> "쇼핑"
                            else -> "기타 경비"
                        }
                    } else {
                        description
                    }
                    
                    addExpenseToDatabase(amount, finalDescription, category)
                } else {
                    Toast.makeText(requireContext(), "올바른 금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun addExpenseToDatabase(amount: Int, description: String, category: String) {
        lifecycleScope.launch {
            try {
                val expense = ExpenseItem(
                    chatId = chatId,
                    amount = amount,
                    description = description,
                    category = category,
                    userId = "current_user", // TODO: 실제 사용자 ID
                    createdAt = Date(System.currentTimeMillis())
                )
                
                viewModel.addExpense(expense)
                
                Toast.makeText(requireContext(), "✅ 경비가 추가되었습니다.", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "경비 추가 실패", e)
                Toast.makeText(requireContext(), "❌ 경비 추가에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showExpenseDetailDialog(expense: ExpenseItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_expense_detail, null)
        
        // 카테고리 아이콘 설정
        val categoryIcon = dialogView.findViewById<TextView>(R.id.categoryIcon)
        val categoryText = dialogView.findViewById<TextView>(R.id.expenseCategory)
        
        val (emoji, categoryName) = getCategoryInfo(expense.category)
        categoryIcon.text = emoji
        categoryText.text = "$emoji $categoryName"
        
        // 경비 정보 설정
        dialogView.findViewById<TextView>(R.id.expenseTitle).text = expense.getDisplayDescription()
        dialogView.findViewById<TextView>(R.id.expenseAmount).text = formatCurrency(expense.amount)
        dialogView.findViewById<TextView>(R.id.expenseDescription).text = 
            if (expense.description.isBlank()) "상세 내용이 없습니다." else expense.description
        
        // 날짜 정보 설정
        val dateFormatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        val formattedDate = dateFormatter.format(expense.createdAt)
        dialogView.findViewById<TextView>(R.id.expenseDate).text = formattedDate
        
        // 사용자 정보 설정
        dialogView.findViewById<TextView>(R.id.expenseUser).text = 
            if (expense.userName.isBlank()) "나" else expense.userName
        
        // 다이얼로그 생성
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // 투명한 배경 설정
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 버튼 클릭 리스너 설정
        dialogView.findViewById<View>(R.id.editButton).setOnClickListener {
            dialog.dismiss()
            showEditExpenseDialog(expense)
        }
        
        dialogView.findViewById<View>(R.id.deleteButton).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmDialog(expense)
        }
        
        dialogView.findViewById<View>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * 카테고리 정보 반환 (이모지, 이름)
     */
    private fun getCategoryInfo(category: String): Pair<String, String> {
        return when {
            category.contains("식비") -> "🍽️" to "식비"
            category.contains("카페") -> "☕" to "카페"
            category.contains("숙박") -> "🏨" to "숙박"
            category.contains("교통") -> "🚗" to "교통비"
            category.contains("주유") -> "⛽" to "주유"
            category.contains("렌트카") -> "🚙" to "렌트카"
            category.contains("관광") || category.contains("액티비티") -> "🎢" to "관광/액티비티"
            category.contains("마트") || category.contains("편의점") -> "🛒" to "마트/편의점"
            else -> "💰" to "기타"
        }
    }
    
    private fun showEditExpenseDialog(expense: ExpenseItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null)
        val amountEditText = dialogView.findViewById<EditText>(R.id.editTextAmount)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.editTextDescription)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        
        // 기존 값으로 미리 채우기
        amountEditText.setText(expense.amount.toString())
        descriptionEditText.setText(expense.description)
        
        // 카테고리 스피너 설정
        val categories = arrayOf(
            "🍽️ 식비", "☕ 카페", "🏨 숙박", "🚗 교통비", 
            "⛽ 주유", "🚙 렌트카", "🎢 관광/액티비티", "🛒 마트/편의점"
        )
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter
        
        // 기존 카테고리 선택
        val currentCategoryIndex = categories.indexOfFirst { it.contains(expense.category.replace("🍽️ ", "").replace("☕ ", "").replace("🏨 ", "").replace("🚗 ", "").replace("⛽ ", "").replace("🚙 ", "").replace("🎢 ", "").replace("🛒 ", "")) }
        if (currentCategoryIndex >= 0) {
            categorySpinner.setSelection(currentCategoryIndex)
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("✏️ 경비 수정")
            .setView(dialogView)
            .setPositiveButton("수정") { _, _ ->
                val amount = amountEditText.text.toString().toIntOrNull()
                val description = descriptionEditText.text.toString().trim()
                val category = categories[categorySpinner.selectedItemPosition]
                
                if (amount != null && amount > 0) {
                    val finalDescription = if (description.isBlank()) {
                        when (category) {
                            "🍽️ 식비" -> "식당"
                            "☕ 카페" -> "카페"
                            "🏨 숙박" -> "숙박비"
                            "🚗 교통비" -> "교통비"
                            "⛽ 주유" -> "주유비"
                            "🚙 렌트카" -> "렌트카"
                            "🎢 관광/액티비티" -> "관광"
                            "🛒 마트/편의점" -> "쇼핑"
                            else -> "기타 경비"
                        }
                    } else {
                        description
                    }
                    
                    updateExpenseInDatabase(expense, amount, finalDescription, category)
                } else {
                    Toast.makeText(requireContext(), "올바른 금액을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun updateExpenseInDatabase(expense: ExpenseItem, amount: Int, description: String, category: String) {
        lifecycleScope.launch {
            try {
                val updatedExpense = ExpenseItem(
                    chatId = expense.chatId,
                    amount = amount,
                    description = description,
                    category = category,
                    userId = expense.userId,
                    createdAt = expense.createdAt
                )
                
                viewModel.updateExpense(expense, updatedExpense)
                viewModel.refreshData(expense.chatId)
                
                Toast.makeText(requireContext(), "✅ 경비가 수정되었습니다.", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "경비 수정 실패", e)
                Toast.makeText(requireContext(), "❌ 경비 수정에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showDeleteConfirmDialog(expense: ExpenseItem) {
        val (emoji, categoryName) = getCategoryInfo(expense.category)
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🗑️ 경비 삭제")
            .setMessage("정말로 이 경비를 삭제하시겠습니까?\n\n" +
                    "$emoji $categoryName\n" +
                    "💰 ${formatCurrency(expense.amount)}\n" +
                    "📍 ${expense.description}\n\n" +
                    "⚠️ 삭제된 경비는 복구할 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                deleteExpense(expense)
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun deleteExpense(expense: ExpenseItem) {
        lifecycleScope.launch {
            try {
                viewModel.deleteExpense(expense)
                viewModel.refreshData(expense.chatId)
                
                Toast.makeText(requireContext(), "✅ 경비가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "경비 삭제 실패", e)
                Toast.makeText(requireContext(), "❌ 경비 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_OCR_RESULT && resultCode == Activity.RESULT_OK) {
            val amount = data?.getIntExtra("amount", 0) ?: 0
            val description = data?.getStringExtra("description") ?: ""
            
            if (amount > 0) {
                showManualInputDialog(amount, description)
            }
        }
    }
    
    private fun formatCurrency(amount: Int): String {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 