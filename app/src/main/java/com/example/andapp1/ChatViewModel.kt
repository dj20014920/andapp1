//ChatViewModel.kt
package com.example.andapp1

import android.R.attr.text
import android.util.Log
import android.util.Property
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.andapp1.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.*
import android.content.Context
import com.google.firebase.database.FirebaseDatabase

class ChatViewModel(val roomCode: String,
                    val context: Context) : ViewModel() {

    private val messageRepository = MessageRepository(roomCode)
    val messages: LiveData<List<ChatMessage>> = messageRepository.messages

    private val userDao = RoomDatabaseInstance.getInstance(context).userDao()

    fun sendMessage(content: String) {
        val userDao = RoomDatabaseInstance.getInstance(context).userDao()

        // 코루틴으로 Room에서 사용자 불러오기
        viewModelScope.launch {
            val currentUser = withContext(Dispatchers.IO) {
                userDao.getUser()
            }

            if (currentUser != null) {
                // ✅ 디버깅 로그 추가
                Log.d("ProfileDebug", "=== 메시지 전송 시 프로필 디버깅 ===")
                Log.d("ProfileDebug", "사용자 ID: ${currentUser.id}")
                Log.d("ProfileDebug", "사용자 닉네임: ${currentUser.nickname}")
                Log.d("ProfileDebug", "프로필 이미지 URL: ${currentUser.profileImageUrl}")

                val user = Author(
                    id = currentUser.id,
                    name = currentUser.nickname ?: "알 수 없음",
                    avatar = currentUser.profileImageUrl // ✅ 프로필 이미지 URL 설정
                )

                Log.d("ProfileDebug", "Author avatar 설정됨: ${user.getAvatar()}")

                val message = ChatMessage(
                    messageId = "",
                    text = content,
                    user = user,
                    createdAt = Date()
                )
                Log.d("ChatViewModel", "📤 메시지 전송: ${message.text}")
                messageRepository.sendMessage(message)
            } else {
                Log.e("ChatViewModel", "❌ 사용자 정보가 없습니다. 메시지를 보낼 수 없습니다.")
            }
        }
    }

    fun sendSystemMessage(text: String) {
        viewModelScope.launch {
            val message = ChatMessage(
                messageId = "",
                text = text,
                user = Author(
                    id = "system",
                    name = "시스템",
                    avatar = null
                ),
                createdAt = Date()
            )
            Log.d("ChatViewModel", "⚙️ 시스템 메시지 전송: $text")
            messageRepository.sendMessage(message)
        }
    }

    fun sendMapUrlMessage(mapUrl: String) {
        viewModelScope.launch {
            val currentUser = withContext(Dispatchers.IO) {
                userDao.getUser()
            }

            if (currentUser != null) {
                val user = Author(
                    id = currentUser.id,
                    name = currentUser.nickname ?: "알 수 없음",
                    avatar = currentUser.profileImageUrl // ✅ 프로필 이미지 URL 설정
                )

                val message = ChatMessage(
                    messageId = "",
                    text = "🗺️ 장소를 공유했어요!\n$mapUrl", // ✅ URL도 텍스트에 포함
                    user = user,
                    imageUrlValue= null,
                    mapUrl = mapUrl,
                    createdAt = Date()
                )
                Log.d("ChatViewModel", "📤 지도 URL 전송: $mapUrl")
                messageRepository.sendMessage(message)
            } else {
                Log.e("ChatViewModel", "❌ 사용자 정보 없음")
            }
        }
    }

    fun sendMessage(message: ChatMessage) {
        viewModelScope.launch {
            messageRepository.sendMessage(message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        messageRepository.cleanup()
    }

}