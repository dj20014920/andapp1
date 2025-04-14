package com.example.andapp1

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Util {
    fun getCurrentTime(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = Date()
        return dateFormat.format(date)
    }
    fun generateRandomCode(length: Int = 6): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
    fun RoomEntity.toRoom(): Room {
        return Room(
            roomCode = this.roomCode,
            roomTitle = this.roomTitle,
            lastActivityTime = this.lastActivityTime,
            isFavorite = this.isFavorite
        )
    }

    fun Room.toRoomEntity(): RoomEntity {
        return RoomEntity(
            roomCode = this.roomCode,
            roomTitle = this.roomTitle,
            lastActivityTime = this.lastActivityTime,
            isFavorite = this.isFavorite
        )
    }
}