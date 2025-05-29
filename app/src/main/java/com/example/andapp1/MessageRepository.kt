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
    private var lastMessageSnapshot: List<ChatMessage> = emptyList()

    private val messageListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val messageList = mutableListOf<ChatMessage>()
            for (child in snapshot.children) {
                child.getValue(ChatMessage::class.java)?.let { message ->
                    if (message.getText().isNotBlank() || message.imageUrl != null) {
                        messageList.add(message)
                    }
                }
            }

            val sortedMessages = messageList.sortedBy { it.createdAt.time }  // ✅ 정렬 핵심

            if (!isSameMessageList(lastMessageSnapshot, sortedMessages)) {
                lastMessageSnapshot = sortedMessages
                _messages.postValue(sortedMessages)
                Log.d("MessageRepository", "✅ LiveData 갱신됨 → size = ${sortedMessages.size}")
            } else {
                Log.d("MessageRepository", "⚠ 중복 메시지 무시됨 (LiveData 발행 안 함)")
            }
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
                    Log.d("MessageRepo", "초기화 생략됨 (불필요한 빈 메시지 방지)")
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
        message.messageId = messageId
        messagesRef.child(messageId).setValue(message)
        Log.d("MessageRepository", "✅ Firebase 저장 완료: ${message.text}")
    }

    fun cleanup() {
        messagesRef.removeEventListener(messageListener)
    }
    private fun isSameMessageList(
        list1: List<ChatMessage>,
        list2: List<ChatMessage>
    ): Boolean {
        if (list1.size != list2.size) return false
        return list1.zip(list2).all { (a, b) -> a.id == b.id }
    }
}