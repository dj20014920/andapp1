package com.example.andapp1 // ✅ 완료

data class Room(
    var roomCode: String = "",
    var roomTitle: String = "",
    var lastActivityTime: String = "",
    var isFavorite: Boolean = false
)