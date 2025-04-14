package com.example.andapp1
data class User(
    val id: String = "",
    val nickname: String? = null,
    val email: String? = null,
    val profileImageUrl: String? = null // ✅ 새 필드 추가
)