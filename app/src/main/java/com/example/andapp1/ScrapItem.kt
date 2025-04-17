package com.example.andapp1

data class ScrapItem(
    val name: String = "",     // 사용자 입력
    val url: String = "",      // 지도에서 가져온 링크
    val timestamp: Long = System.currentTimeMillis()
)