package com.example.andapp1 // ✅ 완료

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.andapp1.databinding.ActivityEnterCodeBinding
import com.google.firebase.database.FirebaseDatabase

class EnterCodeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnterCodeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnterCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.submitCodeButton.setOnClickListener {
            val code = binding.enterCodeEditText.text.toString().trim()

            if (code.isNotEmpty()) {
                FirebaseDatabase.getInstance()
                    .getReference("rooms")
                    .child(code)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val roomTitle = snapshot.child("roomTitle").getValue(String::class.java) ?: "채팅방"

                            val intent = Intent(this, ChatActivity::class.java).apply {
                                putExtra("roomCode", code)
                                putExtra("roomName", roomTitle)
                            }
                            startActivity(intent)
                        } else {
                            Toast.makeText(this, "존재하지 않는 코드입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "코드를 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }
}