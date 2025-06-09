//MessageRepository.kt
package com.example.andapp1

import android.R.attr.text
import android.R.id.message
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.*
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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

            Log.d("ProfileDebug", "=== MessageRepository 메시지 로드 시작 ===")

            // 1) 메시지 수집 및 사용자 ID 추출
            for (child in snapshot.children) {
                child.getValue(ChatMessage::class.java)?.let { message ->
                    if (message.getText().isNotBlank() || message.imageUrl != null) {
                        messageList.add(message)
                        userIds.add(message.getUser().getId())
                        Log.d("ProfileDebug", "메시지 로드됨 - 사용자: ${message.getUser().getId()}, 기존 avatar: ${message.getUser().getAvatar()}")
                    }
                }
            }

            Log.d("ProfileDebug", "수집된 사용자 ID들: $userIds")

            // 2) 사용자별 프로필 이미지 로드
            loadUserProfiles(userIds) { userProfiles ->
                Log.d("ProfileDebug", "로드된 프로필 맵: $userProfiles")

                // 3) 메시지에 프로필 이미지 매핑
                val updatedMessages = messageList.map { message ->
                    val userId = message.getUser().getId()
                    val profileImageUrl = userProfiles[userId]

                    Log.d("ProfileDebug", "메시지 업데이트 - 사용자: $userId, 프로필URL: $profileImageUrl")

                    // Author에 프로필 이미지 설정
                    val updatedAuthor = Author(
                        id = message.getUser().getId(),
                        name = message.getUser().getName(),
                        avatar = profileImageUrl
                    )

                    Log.d("ProfileDebug", "업데이트된 Author avatar: ${updatedAuthor.getAvatar()}")

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
            Log.d("ProfileDebug", "사용자 ID가 없어서 프로필 로드 생략")
            callback(emptyMap())
            return
        }

        Log.d("ProfileDebug", "프로필 이미지 로드 시작 - 대상 사용자: $userIds")

        val userProfiles = mutableMapOf<String, String?>()
        var loadedCount = 0

        for (userId in userIds) {
            Log.d("ProfileDebug", "사용자 프로필 로드 중: $userId")

            usersRef.child(userId).child("profileImageUrl").get()
                .addOnSuccessListener { snapshot ->
                    val profileUrl = snapshot.getValue(String::class.java)
                    userProfiles[userId] = profileUrl
                    loadedCount++

                    Log.d("ProfileDebug", "프로필 로드 성공 - 사용자: $userId, URL: $profileUrl")

                    if (loadedCount == userIds.size) {
                        Log.d("ProfileDebug", "모든 프로필 로드 완료: $userProfiles")
                        callback(userProfiles)
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("MessageRepository", "사용자 프로필 로드 실패: $userId, ${error.message}")
                    userProfiles[userId] = null // 실패 시 null로 설정
                    loadedCount++

                    if (loadedCount == userIds.size) {
                        Log.d("ProfileDebug", "프로필 로드 완료 (일부 실패 포함): $userProfiles")
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

        // === 🔔 최신 OkHttp 방식 ===
        try {
            val json = org.json.JSONObject().apply {
                put("roomId", roomCode)
                put("senderId", message.user.id)
                put("senderName", message.user.name)
                put("messageText", message.text ?: "")
            }

            // 최신 확장 함수 사용!
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)

            val request = okhttp3.Request.Builder()
                .url("https://us-central1-andapp1-bcb40.cloudfunctions.net/sendChatNotification")
                .post(body)
                .build()

            okhttp3.OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e("푸시알림", "알림 함수 호출 실패", e)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val resBody = response.body?.string() // ✅ .body() → .body
                    Log.d("푸시알림", "알림 함수 응답: $resBody")
                }
            })
        } catch (e: Exception) {
            Log.e("푸시알림", "알림 함수 호출 중 예외", e)
        }
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