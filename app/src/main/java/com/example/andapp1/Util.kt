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
    fun generateRandomCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val part1 = (1..3).map { chars.random() }.joinToString("")
        val part2 = (1..3).map { chars.random() }.joinToString("")
        return "$part1-$part2" // ABC-123 형식
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