package com.example.andapp1

// ✅ Firebase 호환 Room 클래스 (participants의 혼재된 구조 처리)
data class Room(
    var roomCode: String = "",
    var roomTitle: String = "",
    var lastActivityTime: String = "",
    var isFavorite: Boolean = false,
    var participants: Map<String, Any> = emptyMap()  // Boolean과 Object 혼재 구조 처리
) {
    // ✅ Firebase용 기본 생성자 (반드시 필요!)
    constructor() : this("", "", "", false, emptyMap())
    
    /**
     * 참여자 수 반환
     */
    fun getParticipantCount(): Int {
        return participants.size
    }
    
    /**
     * 참여자 목록을 List<String>으로 반환
     */
    fun getParticipantIds(): List<String> {
        return participants.keys.toList()
    }
    
    /**
     * 특정 사용자가 참여 중인지 확인
     * Firebase에서 participants[userId]가 true 또는 객체(nickname 포함)인 경우 모두 참여로 간주
     */
    fun isUserParticipating(userId: String): Boolean {
        return when (val participant = participants[userId]) {
            is Boolean -> participant  // true인 경우만 참여
            is Map<*, *> -> true       // 객체가 있으면 참여 (nickname 정보 포함)
            else -> false              // null이거나 다른 타입이면 미참여
        }
    }
    
    /**
     * 참여자의 닉네임 반환 (있는 경우)
     */
    fun getParticipantNickname(userId: String): String? {
        return when (val participant = participants[userId]) {
            is Map<*, *> -> participant["nickname"] as? String
            else -> null
        }
    }
}