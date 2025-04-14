//MessageRepository.kt
package com.example.andapp1

import android.R.attr.text
import android.R.id.message
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.*
import java.util.*

class MessageRepository(private val roomCode: String) {
    private val messagesRef: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("messages").child(roomCode)

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages

    private val messageListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val messageList = mutableListOf<ChatMessage>()
            for (child in snapshot.children) {
                child.getValue(ChatMessage::class.java)?.let { message ->
                    if (message.getText().isNotBlank()) {
                        messageList.add(message)
                    }
                }
            }
            _messages.postValue(messageList)
        }

        override fun onCancelled(error: DatabaseError) {
            // 오류 처리
        }
    }

    init {
        messagesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("MessageRepo", "방 존재 여부: ${snapshot.exists()}")
                if (!snapshot.exists()) {
                    // 메시지를 전혀 보내지 않도록 초기화 제거
                    Log.d("MessageRepo", "초기화 생략됨 (불필요한 빈 메시지 방지)")
                    // ⚠ 삭제: messagesRef.setValue(mapOf<String, Any>())
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("MessageRepo", "Firebase 오류: ${error.message}")
            }
        })

        messagesRef.addValueEventListener(messageListener)
    }

    fun sendMessage(message: ChatMessage) {
        val messageId = messagesRef.push().key ?: UUID.randomUUID().toString()
        messagesRef.child(messageId).setValue(message)
        Log.d("MessageRepository", "✅ Firebase 저장 완료: ${message.text}")
    }

    fun cleanup() {
        messagesRef.removeEventListener(messageListener)
    }
}