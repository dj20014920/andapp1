package com.example.andapp1 // ✅ 완료

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.andapp1.databinding.ActivityEnterLinkBinding
import com.google.firebase.database.FirebaseDatabase

class EnterLinkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnterLinkBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnterLinkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSubmitButton()
    }

    private fun setupSubmitButton() {
        binding.submitLinkButton.setOnClickListener {
            val link = binding.enterLinkEditText.text.toString().trim()

            if (link.isEmpty()) {
                Toast.makeText(this, "링크를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 예시: 링크에서 roomCode 추출
            val roomCode = extractRoomCodeFromLink(link)

            if (roomCode.isNullOrEmpty()) {
                Toast.makeText(this, "올바른 링크가 아닙니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(roomCode)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val roomTitle = snapshot.child("roomTitle").getValue(String::class.java) ?: "채팅방"

                        val intent = Intent(this, ChatActivity::class.java).apply {
                            putExtra("roomCode", roomCode)
                            putExtra("roomName", roomTitle)
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "존재하지 않는 링크입니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            Toast.makeText(this, "입장할 링크: $link", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, RoomActivity::class.java)
            intent.putExtra("roomCode", roomCode)
            startActivity(intent)
        }
    }

    // ✨ 링크 구조에 따라 룸 코드를 추출하는 함수 (예: https://example.com/join?code=ROOM123)
    private fun extractRoomCodeFromLink(link: String): String? {
        val uri = android.net.Uri.parse(link)
        return uri.getQueryParameter("code") // ?code=ROOM123 에서 ROOM123 추출
    }
}