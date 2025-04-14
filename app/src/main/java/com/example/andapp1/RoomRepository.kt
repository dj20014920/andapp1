//RoomRepository.kt
package com.example.andapp1

import androidx.lifecycle.LiveData
import androidx.room.Query

// ✅ 완료

import com.example.andapp1.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.andapp1.RoomDao
class RoomRepository(private val db: AppDatabase) {
    private val roomDao = db.roomDao()  // ✅ 여기가 문제면 수정 필요
    @Query("SELECT * FROM favorite_rooms ORDER BY lastActivityTime DESC")

    fun getAllFavoriteRooms(): LiveData<List<RoomEntity>> {
        return db.roomDao().getAllFavoriteRooms()
    }
    suspend fun getAllFavoriteRoomEntities(): List<RoomEntity> {
        return roomDao.getAllFavoriteRoomsRaw() // 아래 쿼리도 필요
    }
    suspend fun getFavoriteRoomCodes(): List<String> {
        return roomDao.getAllFavoriteRoomsRaw().map { it.roomCode }
    }
    suspend fun insertFavoriteRoom(room: RoomEntity) {
        withContext(Dispatchers.IO) {
            db.roomDao().insertFavoriteRoom(room)
        }
    }

    suspend fun deleteFavoriteRoom(room: RoomEntity) {
        withContext(Dispatchers.IO) {
            db.roomDao().deleteFavoriteRoom(room)
        }
    }

    suspend fun deleteRoomByCode(roomCode: String) {
        withContext(Dispatchers.IO) {
            db.roomDao().deleteRoomByCode(roomCode)
        }
    }

    suspend fun updateRoomTitle(roomCode: String, newTitle: String) {
        withContext(Dispatchers.IO) {
            db.roomDao().updateRoomTitle(roomCode, newTitle)
        }
    }

    suspend fun updateFavoriteStatus(roomCode: String, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            db.roomDao().updateFavoriteStatus(roomCode, isFavorite)
        }
    }

    suspend fun updateLastActivityTime(roomCode: String, newTime: String) {
        withContext(Dispatchers.IO) {
            db.roomDao().updateLastActivityTime(roomCode, newTime)
        }
    }
}