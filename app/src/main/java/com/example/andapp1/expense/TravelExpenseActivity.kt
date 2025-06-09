package com.example.andapp1.expense

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.andapp1.databinding.ActivityTravelExpenseBinding
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 여행 경비 관리 액티비티 (ViewPager2 + Fragment 기반)
 */
class TravelExpenseActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "TravelExpenseActivity"
    }
    
    private lateinit var binding: ActivityTravelExpenseBinding
    private lateinit var pagerAdapter: ExpensePagerAdapter
    
    private var chatId: String = ""
    private var roomName: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTravelExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Intent 데이터 추출
        chatId = intent.getStringExtra("chatId") ?: ""
        roomName = intent.getStringExtra("roomName") ?: "여행 경비"
        
        if (chatId.isEmpty()) {
            finish()
            return
        }
        
        Log.d(TAG, "여행 경비 액티비티 시작 - chatId: $chatId, roomName: $roomName")
        
        setupHeader()
        setupViewPager()
        setupTabs()
        
        // 🎯 OCR에서 넘어온 경우 전체내역 탭으로 이동
        val openAddDialog = intent.getBooleanExtra("openAddDialog", false)
        if (openAddDialog) {
            binding.viewPager.setCurrentItem(0, false) // 전체내역 탭으로
        }
    }
    
    /**
     * 헤더 설정
     */
    private fun setupHeader() {
        binding.toolbarTitle.text = "$roomName"
        binding.backButton.setOnClickListener {
            finish()
        }
    }
    
    /**
     * ViewPager2 설정
     */
    private fun setupViewPager() {
        pagerAdapter = ExpensePagerAdapter(this, chatId)
        binding.viewPager.adapter = pagerAdapter
        
        // ViewPager2 스와이프 활성화
        binding.viewPager.isUserInputEnabled = true
        
        // 페이지 변경 시 부드러운 전환
        binding.viewPager.offscreenPageLimit = 3
    }
    
    /**
     * 탭 설정 (TabLayoutMediator 사용)
     */
    private fun setupTabs() {
        val tabTitles = arrayOf(
            "📋 전체 내역",
            "📊 카테고리별", 
            "💰 정산"
        )
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 