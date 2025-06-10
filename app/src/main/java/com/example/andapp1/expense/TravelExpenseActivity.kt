package com.example.andapp1.expense

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.andapp1.R
import com.example.andapp1.databinding.ActivityTravelExpenseBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class TravelExpenseActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTravelExpenseBinding
    private lateinit var chatId: String
    private lateinit var roomName: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTravelExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Intent에서 데이터 받기
        chatId = intent.getStringExtra("chatId") ?: ""
        roomName = intent.getStringExtra("roomName") ?: "여행 경비"
        
        setupUI()
        setupViewPager()
    }
    
    private fun setupUI() {
        // 액션바 설정
        supportActionBar?.apply {
            title = roomName
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        
        // 상단 그라디언트 헤더 색상 설정
        binding.headerLayout.setBackgroundResource(R.drawable.gradient_ocean_header)
        binding.headerTitle.text = "$roomName 경비 관리"
        binding.headerSubtitle.text = "공동 경비를 스마트하게 관리하세요"
    }
    
    private fun setupViewPager() {
        val adapter = ExpensePagerAdapter(this, chatId)
        binding.viewPager.adapter = adapter
        
        // TabLayout과 ViewPager2 연결
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "전체 내역"
                1 -> "카테고리별"
                2 -> "정산하기"
                else -> "탭 $position"
            }
            
            // 탭 아이콘 설정 (기존 아이콘 활용)
            tab.icon = when (position) {
                0 -> getDrawable(R.drawable.receipt)
                1 -> getDrawable(R.drawable.ic_bookmark)
                2 -> getDrawable(R.drawable.ic_account_circle)
                else -> null
            }
        }.attach()
        
        // 탭 스타일링
        binding.tabLayout.apply {
            setSelectedTabIndicatorColor(getColor(R.color.white))
            setTabTextColors(
                getColor(R.color.white_60),
                getColor(R.color.white)
            )
            tabMode = TabLayout.MODE_FIXED
        }
        
        // ViewPager 설정
        binding.viewPager.apply {
            offscreenPageLimit = 3
            isUserInputEnabled = true // 스와이프 가능
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

/**
 * ViewPager2용 FragmentStateAdapter
 */
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
            else -> AllExpensesFragment.newInstance(chatId)
        }
    }
} 