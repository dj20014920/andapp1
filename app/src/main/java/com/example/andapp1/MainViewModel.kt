package com.example.andapp1

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MainViewModel.kt
class MainViewModel(
    private val repository: RoomRepository,
    private val context: Context
) : ViewModel() {

    private var currentUserId: String? = null

    private val _rooms = MutableLiveData<List<Room>>()
    val rooms: LiveData<List<Room>> = _rooms

    private var favoriteRoomCodes = setOf<String>()

    init {
        loadFavoriteRoomCodes()
    }

    fun setCurrentUserId(userId: String) {
        currentUserId = userId
    }

    private fun loadFavoriteRoomCodes() {
        viewModelScope.launch {
            favoriteRoomCodes = repository.getFavoriteRoomCodes().toSet()
        }
    }

    fun loadRooms(userId: String) {
        FirebaseRoomManager.getRooms(userId) { roomsList ->
            viewModelScope.launch {
                // 즐겨찾기 상태 동기화
                val roomsWithFavorites = roomsList.map { room ->
                    room.copy(isFavorite = favoriteRoomCodes.contains(room.roomCode))
                }
                _rooms.postValue(roomsWithFavorites)
            }
        }
    }

    fun updateRoomsList(roomsList: List<Room>) {
        viewModelScope.launch {
            // 즐겨찾기 상태 동기화
            val roomsWithFavorites = roomsList.map { room ->
                room.copy(isFavorite = favoriteRoomCodes.contains(room.roomCode))
            }
            _rooms.postValue(roomsWithFavorites)
        }
    }

    fun checkFavoriteStatus(room: Room) {
        room.isFavorite = favoriteRoomCodes.contains(room.roomCode)
    }

    fun createRoomWithParticipant(room: Room, userId: String) {
        // Firebase에 방 생성
        FirebaseRoomManager.createRoom(room)
        // 생성자를 참여자로 추가
        FirebaseRoomManager.addParticipant(room.roomCode, userId)
    }

    fun addRoom(room: Room) {
        currentUserId?.let { userId ->
            // Firebase에 참여자 추가
            FirebaseRoomManager.addParticipant(room.roomCode, userId)
        }
    }

    fun changeRoomName(roomCode: String, newName: String, author: Author) {
        FirebaseRoomManager.updateRoomName(roomCode, newName, author)

        // 로컬 DB도 업데이트
        viewModelScope.launch {
            repository.updateRoomTitle(roomCode, newName)
        }
    }

    fun leaveRoom(roomCode: String, userId: String) {
        viewModelScope.launch {
            try {
                // 1. 나가기 메시지 전송
                val prefs = context.getSharedPreferences("login", Context.MODE_PRIVATE)
                val nickname = prefs.getString("nickname", "Unknown") ?: "Unknown"
                val author = Author(userId, nickname)
                FirebaseRoomManager.sendLeaveMessage(roomCode, author)

                // 2. Firebase에서 참여자 제거
                FirebaseRoomManager.removeParticipant(roomCode, userId)

                // 3. 로컬 즐겨찾기에서도 제거
                repository.deleteRoomByCode(roomCode)

                // 4. 즐겨찾기 목록 갱신
                loadFavoriteRoomCodes()

                // 5. UI 즉시 업데이트
                val currentRooms = _rooms.value?.toMutableList() ?: mutableListOf()
                currentRooms.removeAll { it.roomCode == roomCode }
                _rooms.postValue(currentRooms)

                Log.d("MainViewModel", "✅ 채팅방 나가기 완료: $roomCode")
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ 채팅방 나가기 실패", e)
            }
        }
    }

    fun updateLastActivityTime(roomCode: String, newTime: String) {
        FirebaseRoomManager.updateLastActivityTime(roomCode, newTime)

        viewModelScope.launch {
            repository.updateLastActivityTime(roomCode, newTime)
        }
    }

    fun insertFavoriteRoom(room: RoomEntity) {
        viewModelScope.launch {
            repository.insertFavoriteRoom(room)
            favoriteRoomCodes = favoriteRoomCodes + room.roomCode

            // UI 즉시 업데이트
            val currentRooms = _rooms.value?.map {
                if (it.roomCode == room.roomCode) {
                    it.copy(isFavorite = true)
                } else it
            }
            currentRooms?.let { _rooms.postValue(it) }
        }
    }

    fun deleteFavoriteRoom(room: RoomEntity) {
        viewModelScope.launch {
            repository.deleteFavoriteRoom(room)
            favoriteRoomCodes = favoriteRoomCodes - room.roomCode

            // UI 즉시 업데이트
            val currentRooms = _rooms.value?.map {
                if (it.roomCode == room.roomCode) {
                    it.copy(isFavorite = false)
                } else it
            }
            currentRooms?.let { _rooms.postValue(it) }
        }
    }

    fun generateRoomLink(roomCode: String): String {
        return "https://andapp1.com/join?code=$roomCode"
    }

    fun isRoomCode(input: String): Boolean {
        return input.matches(Regex("[A-Z0-9]{3}-[A-Z0-9]{3}"))
    }

    fun isRoomLink(input: String): Boolean {
        return input.startsWith("https://andapp1.com/join?code=")
    }

    fun extractRoomCodeFromLink(link: String): String? {
        return try {
            val uri = android.net.Uri.parse(link)
            uri.getQueryParameter("code")?.uppercase()
        } catch (e: Exception) {
            null
        }
    }
}
