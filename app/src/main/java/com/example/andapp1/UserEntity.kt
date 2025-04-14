package com.example.andapp1

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val nickname: String?,
    val email: String?,
    val profileImageUrl: String? // 프로필 이미지 URL
)