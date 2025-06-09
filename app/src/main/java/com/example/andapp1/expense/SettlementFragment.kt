package com.example.andapp1.expense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.andapp1.databinding.FragmentSettlementBinding
import java.text.NumberFormat
import java.util.*

class SettlementFragment : Fragment() {
    
    companion object {
        private const val ARG_CHAT_ID = "chatId"
        
        fun newInstance(chatId: String): SettlementFragment {
            val fragment = SettlementFragment()
            val args = Bundle()
            args.putString(ARG_CHAT_ID, chatId)
            fragment.arguments = args
            return fragment
        }
    }
    
    private var _binding: FragmentSettlementBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: TravelExpenseViewModel
    private lateinit var settlementAdapter: SettlementAdapter
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
        _binding = FragmentSettlementBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViewModel()
        setupRecyclerView()
        observeData()
        setupClickListeners()
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
        settlementAdapter = SettlementAdapter()
        binding.settlementRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = settlementAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.calculateButton.setOnClickListener {
            calculateSettlement()
        }
    }
    
    private fun observeData() {
        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            calculateAndDisplaySettlement(expenses)
        }
    }
    
    private fun calculateAndDisplaySettlement(expenses: List<ExpenseItem>) {
        if (expenses.isEmpty()) {
            showEmptyState()
            return
        }
        
        // 총 경비 계산
        val totalAmount = expenses.sumOf { it.amount }
        val participantCount = 2 // TODO: 실제 참여자 수로 변경
        val amountPerPerson = totalAmount / participantCount
        
        // 사용자별 지출 계산 (임시로 현재 사용자만)
        val userExpenses = mapOf(
            "나" to expenses.sumOf { it.amount },
            "상대방" to 0 // TODO: 실제 데이터로 변경
        )
        
        // 정산 데이터 생성
        val settlementItems = calculateSettlementAmounts(userExpenses, amountPerPerson)
        
        // UI 업데이트
        binding.apply {
            totalAmountText.text = formatCurrency(totalAmount)
            participantCountText.text = "${participantCount}명"
            amountPerPersonText.text = formatCurrency(amountPerPerson)
            
            emptyStateLayout.visibility = View.GONE
            settlementContentLayout.visibility = View.VISIBLE
        }
        
        settlementAdapter.submitList(settlementItems)
    }
    
    private fun calculateSettlementAmounts(
        userExpenses: Map<String, Int>, 
        amountPerPerson: Int
    ): List<SettlementItem> {
        return userExpenses.map { (userName, userAmount) ->
            val balance = userAmount - amountPerPerson
            val settlementType = when {
                balance > 0 -> SettlementType.RECEIVE
                balance < 0 -> SettlementType.PAY
                else -> SettlementType.BALANCED
            }
            
            SettlementItem(
                userName = userName,
                paidAmount = userAmount,
                shouldPay = amountPerPerson,
                balance = kotlin.math.abs(balance),
                settlementType = settlementType
            )
        }
    }
    
    private fun calculateSettlement() {
        // 정산 계산 버튼 클릭 시 애니메이션 또는 상세 계산
        binding.calculateButton.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                binding.calculateButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }
    
    private fun showEmptyState() {
        binding.apply {
            emptyStateLayout.visibility = View.VISIBLE
            settlementContentLayout.visibility = View.GONE
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

// 정산 데이터 모델
data class SettlementItem(
    val userName: String,
    val paidAmount: Int,
    val shouldPay: Int,
    val balance: Int,
    val settlementType: SettlementType
)

enum class SettlementType {
    RECEIVE,    // 받을 돈이 있음
    PAY,        // 줄 돈이 있음
    BALANCED    // 정산 완료
} 