package com.example.andapp1

data class ScrapItem(
    val name: String = "",           // 장소 이름
    val url: String = "",            // 지도 URL
    val thumbnailUrl: String = "",   // (옵션) 썸네일 이미지
    val description: String = ""     // (옵션) 장소 설명
)