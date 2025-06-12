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
import androidx.lifecycle.lifecycleScope
import com.example.andapp1.FirebaseRoomManager
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch

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
        // 경비가 없더라도 참여자 수는 항상 체크!
        FirebaseRoomManager.roomsRef.child(chatId).child("participants").get()
            .addOnSuccessListener { snapshot ->
                val participantCount = snapshot.childrenCount.toInt()
                android.util.Log.d("정산참여자수", "participants = $participantCount, chatId = $chatId")

                val totalAmount = expenses.sumOf { it.amount }
                val amountPerPerson = if (participantCount > 0) totalAmount / participantCount else 0

                binding.apply {
                    totalAmountText.text = formatCurrency(totalAmount)
                    participantCountText.text = "${participantCount}명"
                    amountPerPersonText.text = formatCurrency(amountPerPerson)

                    // empty/정산 레이아웃 분기
                    if (expenses.isEmpty()) {
                        emptyStateLayout.visibility = View.VISIBLE
                        settlementContentLayout.visibility = View.GONE
                    } else {
                        emptyStateLayout.visibility = View.GONE
                        settlementContentLayout.visibility = View.VISIBLE
                    }
                }

                if (expenses.isNotEmpty()) {
                    val userExpenses = mutableMapOf<String, Int>()
                    val userIdToNickname = mutableMapOf<String, String>()
                    val userIdList = mutableListOf<String>()

                    // 1. 먼저 userId 리스트 수집
                    snapshot.children.forEach { participantSnapshot ->
                        val userId = participantSnapshot.key ?: ""
                        userIdList.add(userId)
                        val nickname = participantSnapshot.child("nickname").getValue(String::class.java)
                        if (nickname != null) {
                            userIdToNickname[userId] = nickname
                        }
                    }

                    // 2. users 테이블에서 닉네임 fallback 조회 (필요한 경우에만)
                    if (userIdToNickname.size < userIdList.size) {
                        // 비동기 처리 필요. 모두 가져온 뒤에 settlement 처리!
                        val usersRef = FirebaseDatabase.getInstance().getReference("users")
                        val pending = userIdList.filter { !userIdToNickname.containsKey(it) }
                        var count = 0

                        if (pending.isEmpty()) {
                            // 바로 settlement 진행
                            applySettlementWithNicknames(userIdToNickname, expenses, amountPerPerson)
                        } else {
                            pending.forEach { userId ->
                                usersRef.child(userId).child("nickname").get().addOnSuccessListener { userSnap ->
                                    val name = userSnap.getValue(String::class.java) ?: userId
                                    userIdToNickname[userId] = name
                                    count++
                                    if (count == pending.size) {
                                        applySettlementWithNicknames(userIdToNickname, expenses, amountPerPerson)
                                    }
                                }.addOnFailureListener {
                                    userIdToNickname[userId] = userId
                                    count++
                                    if (count == pending.size) {
                                        applySettlementWithNicknames(userIdToNickname, expenses, amountPerPerson)
                                    }
                                }
                            }
                        }
                    } else {
                        applySettlementWithNicknames(userIdToNickname, expenses, amountPerPerson)
                    }
                }
            }
    }

    private fun applySettlementWithNicknames(
        userIdToNickname: Map<String, String>,
        expenses: List<ExpenseItem>,
        amountPerPerson: Int
    ) {
        val userExpenses = userIdToNickname.mapValues { (userId, nickname) ->
            expenses.filter { it.userId == userId }.sumOf { it.amount }
        }.mapKeys { (userId, _) ->
            userIdToNickname[userId] ?: userId // UI에는 닉네임을 key로
        }
        val settlementItems = calculateSettlementAmounts(userExpenses, amountPerPerson)
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