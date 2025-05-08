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
import com.google.firebase.database.FirebaseDatabase

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
        viewModelScope.launch(Dispatchers.IO) {
            val user = RoomDatabaseInstance.getInstance(context).userDao().getUser()
            val userId = user?.id ?: return@launch

            FirebaseRoomManager.getRooms(userId) { roomsFromFirebase ->
                viewModelScope.launch(Dispatchers.IO) {
                    val favoriteCodes = roomRepository.getFavoriteRoomCodes()
                    val merged = roomsFromFirebase.map {
                        it.copy(isFavorite = favoriteCodes.contains(it.roomCode))
                    }
                    _rooms.postValue(merged)
                }
            }
        }
    }

    fun addRoom(room: Room) {
        viewModelScope.launch {
            val user = RoomDatabaseInstance.getInstance(context).userDao().getUser()
            val userId = user?.id ?: return@launch

            FirebaseRoomManager.createRoom(room, userId) // ✅ userId 함께 전달

            // RoomDB에 저장
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

                // ✅ 시스템 메시지 전송
                FirebaseRoomManager.sendLeaveMessage(roomCode, author)

                // ✅ Firebase에서 참여자 제거
                val participantsRef = FirebaseDatabase.getInstance()
                    .getReference("rooms")
                    .child(roomCode)
                    .child("participants")

                participantsRef.child(user.id).removeValue().addOnSuccessListener {
                    // ✅ 참여자 제거 후, 인원 수 체크
                    participantsRef.get().addOnSuccessListener { snapshot ->
                        if (!snapshot.hasChildren()) {
                            FirebaseRoomManager.deleteRoom(roomCode)
                            Log.d("RoomExit", "⚠️ 마지막 사용자가 나가 방 삭제됨: $roomCode")
                        }
                    }
                }

                // ✅ 로컬 DB에서 방 제거
                roomRepository.deleteRoomByCode(roomCode)

                // ✅ UI 갱신
                val updatedRooms = _rooms.value?.filterNot { it.roomCode == roomCode } ?: emptyList()
                _rooms.postValue(updatedRooms)

                Log.d("MainViewModel", "✅ ${author.name}님이 채팅방에서 나갔습니다.")
            } else {
                Log.e("MainViewModel", "❌ 사용자 정보를 불러오지 못했습니다. 나가기 실패.")
            }
        }
    }

    fun updateLastActivityTime(code: String, time: String) {
        FirebaseRoomManager.updateLastActivityTime(code, time)
    }

    fun removeRoom(code: String) {
        FirebaseRoomManager.deleteRoom(code)
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

    // Room - 즐겨찾기 상태 변경
    fun updateFavorite(roomCode: String, isFavorite: Boolean) {
        viewModelScope.launch {
            roomRepository.updateFavoriteStatus(roomCode, isFavorite)
        }
    }

    // Room - 채팅방 이름 변경
    fun updateRoomTitle(roomCode: String, newTitle: String) {
        viewModelScope.launch {
            roomRepository.updateRoomTitle(roomCode, newTitle)
        }
    }

    // Room - 마지막 채팅 시간 업데이트
    fun updateRoomLastActivityTime(roomCode: String, time: String) {
        viewModelScope.launch {
            roomRepository.updateLastActivityTime(roomCode, time)
        }
    }
}