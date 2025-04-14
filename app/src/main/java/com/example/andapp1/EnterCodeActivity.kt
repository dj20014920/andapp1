package com.example.andapp1 // ✅ 완료

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.andapp1.databinding.ActivityEnterCodeBinding

class EnterCodeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnterCodeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnterCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.submitCodeButton.setOnClickListener {
            val code = binding.enterCodeEditText.text.toString().trim()

            if (code.isNotEmpty()) {
                Toast.makeText(this, "입력한 코드: $code", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("roomCode", code)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "코드를 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }
}