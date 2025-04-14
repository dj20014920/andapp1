package com.example.andapp1

import com.stfalcon.chatkit.commons.models.IUser

class Author(
    private var id: String = "",
    private var name: String = "",
    private var avatar: String? = null
) : IUser {

    constructor() : this("", "", null) // Firebase 역직렬화용 빈 생성자

    override fun getId(): String = id
    override fun getName(): String = name
    override fun getAvatar(): String? = avatar
}