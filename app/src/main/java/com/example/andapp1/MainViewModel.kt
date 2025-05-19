//MainViewModel.kt
package com.example.andapp1 // ✅ 완료

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.andapp1.FirebaseRoomManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
class MainViewModel(
    val roomRepository: RoomRepository,
    private val context: Context
) : ViewModel() {
    private val _rooms = MutableLiveData<List<Room>>()
    val rooms: LiveData<List<Room>> get() = _rooms
    private val userDao = RoomDatabaseInstance.getInstance(context).userDao()
    fun generateRoomLink(code: String): String {
        return "https://example.com/room?code=$code"
    }

    fun isRoomCode(input: String): Boolean {
        val regex = Regex("^[A-Z0-9]{3}-[A-Z0-9]{3}$")
        return regex.matches(input.trim().uppercase())
    }

    fun isRoomLink(input: String): Boolean {
        return input.startsWith("http") && input.contains("code=")
    }

    fun extractRoomCodeFromLink(link: String): String? {
        return Regex("code=([A-Z0-9]{3}-[A-Z0-9]{3})")
            .find(link)
            ?.groupValues
            ?.getOrNull(1)
    }

    init {
        FirebaseRoomManager.getRooms { roomsFromFirebase ->
            viewModelScope.launch(Dispatchers.IO) {
                Log.d("MainViewModel", "✅ getRooms 호출됨")
                val favoriteCodes = roomRepository.getFavoriteRoomCodes()
                val merged = roomsFromFirebase.map {
                    it.copy(isFavorite = favoriteCodes.contains(it.roomCode))
                }
                _rooms.postValue(merged)
            }
        }
    }

    fun addRoom(room: Room) {
        // Firebase에 먼저 방 저장
        FirebaseRoomManager.createRoom(room)

        // 로컬 RoomDB에 즐겨찾기 여부 저장
        viewModelScope.launch {
            val roomEntity = RoomEntity(
                roomCode = room.roomCode,
                roomTitle = room.roomTitle,
                lastActivityTime = room.lastActivityTime,
                isFavorite = room.isFavorite
            )
            roomRepository.insertFavoriteRoom(roomEntity)
        }
    }

    fun changeRoomName(roomCode: String, newName: String) {
        viewModelScope.launch {
            val user = withContext(Dispatchers.IO) {
                RoomDatabaseInstance.getInstance(context).userDao().getUser()
            }

            if (user != null) {
                val author = Author(user.id, user.nickname ?: "알 수 없음")
                FirebaseRoomManager.updateRoomName(roomCode, newName, author) // ✅ 함수명 주의!
            } else {
                Log.e("MainViewModel", "❌ 로컬에 저장된 사용자 정보가 없습니다. 이름 변경 불가.")
            }
        }
    }
    fun leaveRoom(roomCode: String) {
        viewModelScope.launch {
            val user = withContext(Dispatchers.IO) {
                RoomDatabaseInstance.getInstance(context).userDao().getUser()
            }

            if (user != null) {
                val author = Author(user.id, user.nickname ?: "알 수 없음")

                // ❌ Firebase 참여자 제거는 생략 (기록 유지 목적)
                // ✅ 시스템 메시지 전송
                FirebaseRoomManager.sendLeaveMessage(roomCode, author)

                // ✅ RoomDB에서만 제거 (로컬 기록 삭제)
                roomRepository.deleteRoomByCode(roomCode)

                // ✅ UI에서 제거
                val updatedRooms = _rooms.value?.filterNot { it.roomCode == roomCode }
                _rooms.postValue(updatedRooms)

                Log.d("MainViewModel", "✅ ${author.name}님이 채팅방에서 나갔습니다. (로컬 기준)")
            } else {
                Log.e("MainViewModel", "❌ 사용자 정보를 불러오지 못했습니다. 나가기 실패.")
            }
        }
    }
    fun updateLastActivityTime(code: String, time: String) {
        FirebaseRoomManager.updateLastActivityTime(code, time)
    }

    // Room - 즐겨찾기 저장
    fun insertFavoriteRoom(roomEntity: RoomEntity) {
        viewModelScope.launch {
            roomRepository.insertFavoriteRoom(roomEntity)
        }
    }

    // Room - 즐겨찾기 제거
    fun deleteFavoriteRoom(roomEntity: RoomEntity) {
        viewModelScope.launch {
            roomRepository.deleteFavoriteRoom(roomEntity)
        }
    }
}