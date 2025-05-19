package com.example.andapp1

import com.stfalcon.chatkit.commons.models.IUser

class Author(
    private var id: String = "",
    private var name: String = "",
    private var avatar: String? = null
) : IUser {

    constructor() : this("", "", null)

    override fun getId(): String = id
    override fun getName(): String = name
    override fun getAvatar(): String? = avatar
}