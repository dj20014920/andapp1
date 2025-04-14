package com.example.andapp1
//완료

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_rooms")
data class RoomEntity(
    @PrimaryKey val roomCode: String, // 채팅방 고유 코드
    val roomTitle: String,           // 채팅방 이름
    val lastActivityTime: String,    // 마지막 채팅 시간
    val isFavorite: Boolean          // 즐겨찾기 여부
)