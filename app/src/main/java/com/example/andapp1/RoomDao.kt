//RoomDao.kt
package com.example.andapp1
import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.andapp1.RoomEntity

@Dao
interface RoomDao {
    @Query("SELECT * FROM favorite_rooms")
    suspend fun getAllFavoriteRoomsRaw(): List<RoomEntity>

    @Query("SELECT * FROM favorite_rooms ORDER BY lastActivityTime DESC")
    fun getAllFavoriteRooms(): LiveData<List<RoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteRoom(room: RoomEntity)

    @Query("DELETE FROM favorite_rooms WHERE roomCode = :roomCode")
    suspend fun deleteRoomByCode(roomCode: String)

    @Delete
    suspend fun deleteFavoriteRoom(room: RoomEntity)

    @Query("UPDATE favorite_rooms SET roomTitle = :newTitle WHERE roomCode = :roomCode")
    suspend fun updateRoomTitle(roomCode: String, newTitle: String)

    @Query("UPDATE favorite_rooms SET isFavorite = :isFavorite WHERE roomCode = :roomCode")
    suspend fun updateFavoriteStatus(roomCode: String, isFavorite: Boolean)

    @Query("UPDATE favorite_rooms SET lastActivityTime = :newTime WHERE roomCode = :roomCode")
    suspend fun updateLastActivityTime(roomCode: String, newTime: String)

    @Query("SELECT COUNT(*) FROM favorite_rooms")
    suspend fun countAll(): Int
}