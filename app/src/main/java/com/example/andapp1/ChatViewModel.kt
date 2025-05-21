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
                val user = Author(
                    id = currentUser.id,
                    name = currentUser.nickname ?: "알 수 없음",
                    avatar = null
                )

                val message = ChatMessage(
                    id = System.currentTimeMillis().toString(),
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
                id = System.currentTimeMillis().toString(),
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
                    avatar = null
                )

                val message = ChatMessage(
                    id = System.currentTimeMillis().toString(),
                    text = "🗺️ 장소를 공유했어요!\n$mapUrl", // ✅ URL도 텍스트에 포함
                    user = user,
                    _imageUrl = null,
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