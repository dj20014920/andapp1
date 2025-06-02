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

    private val usersRef: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("users")

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages
    private var lastMessageSnapshot: List<ChatMessage> = emptyList()

    private val messageListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val messageList = mutableListOf<ChatMessage>()
            val userIds = mutableSetOf<String>()

            // 1) 메시지 수집 및 사용자 ID 추출
            for (child in snapshot.children) {
                child.getValue(ChatMessage::class.java)?.let { message ->
                    if (message.getText().isNotBlank() || message.imageUrl != null) {
                        messageList.add(message)
                        userIds.add(message.getUser().getId())
                    }
                }
            }

            // 2) 사용자별 프로필 이미지 로드
            loadUserProfiles(userIds) { userProfiles ->
                // 3) 메시지에 프로필 이미지 매핑
                val updatedMessages = messageList.map { message ->
                    val userId = message.getUser().getId()
                    val profileImageUrl = userProfiles[userId]

                    // Author에 프로필 이미지 설정
                    val updatedAuthor = Author(
                        id = message.getUser().getId(),
                        name = message.getUser().getName(),
                        avatar = profileImageUrl
                    )

                    // 새로운 ChatMessage 생성 (기존 데이터 유지 + 프로필 이미지 추가)
                    ChatMessage(
                        messageId = message.messageId,
                        text = message.getText(),
                        user = updatedAuthor,
                        imageUrlValue = message.imageUrlValue,
                        mapUrl = message.getMapUrl(),
                        createdAt = message.getCreatedAt()
                    )
                }

                val sortedMessages = updatedMessages.sortedBy { it.createdAt.time }

                if (!isSameMessageList(lastMessageSnapshot, sortedMessages)) {
                    lastMessageSnapshot = sortedMessages
                    _messages.postValue(sortedMessages)
                    Log.d("MessageRepository", "✅ LiveData 갱신됨 (프로필 이미지 포함) → size = ${sortedMessages.size}")
                } else {
                    Log.d("MessageRepository", "⚠ 중복 메시지 무시됨 (LiveData 발행 안 함)")
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("MessageRepository", "Firebase 오류: ${error.message}")
        }
    }

    // 사용자 프로필 이미지 일괄 로드
    private fun loadUserProfiles(userIds: Set<String>, callback: (Map<String, String?>) -> Unit) {
        if (userIds.isEmpty()) {
            callback(emptyMap())
            return
        }

        val userProfiles = mutableMapOf<String, String?>()
        var loadedCount = 0

        for (userId in userIds) {
            usersRef.child(userId).child("profileImageUrl").get()
                .addOnSuccessListener { snapshot ->
                    userProfiles[userId] = snapshot.getValue(String::class.java)
                    loadedCount++

                    if (loadedCount == userIds.size) {
                        callback(userProfiles)
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("MessageRepository", "사용자 프로필 로드 실패: $userId, ${error.message}")
                    userProfiles[userId] = null // 실패 시 null로 설정
                    loadedCount++

                    if (loadedCount == userIds.size) {
                        callback(userProfiles)
                    }
                }
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