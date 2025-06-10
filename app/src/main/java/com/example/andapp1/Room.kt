package com.example.andapp1

// ✅ Firebase 호환 Room 클래스
data class Room(
    var roomCode: String = "",
    var roomTitle: String = "",
    var lastActivityTime: String = "",
    var isFavorite: Boolean = false,
    var participants: Map<String, Any> = mapOf() // ✅ participants 필드 추가
) {
    // ✅ Firebase용 기본 생성자 (반드시 필요!)
    constructor() : this("", "", "", false, mapOf())

}