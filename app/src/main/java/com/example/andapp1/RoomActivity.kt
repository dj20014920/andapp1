package com.example.andapp1 // ✅ 완료

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.andapp1.databinding.ActivityRoomBinding

class RoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoomBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // MainActivity에서 전달받은 코드/링크 가져오기
        val roomCode = intent.getStringExtra("ROOM_CODE") ?: ""
        val roomLink = intent.getStringExtra("ROOM_LINK") ?: ""

        // 화면에 표시
        binding.roomCodeTextView.text = "초대 코드 : $roomCode"
        binding.roomLinkTextView.text = "초대 링크 : $roomLink"

        // 공유 버튼 클릭 이벤트
        binding.shareButton.setOnClickListener {
            val shareText = """
                친구랑 같이 여행을 떠나요!-!
                초대 코드 : $roomCode
                초대 링크 : $roomLink
            """.trimIndent()

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, "공유하기")
            startActivity(shareIntent)
        }
    }
}