package com.example.andapp1

import com.google.firebase.database.Exclude
import com.stfalcon.chatkit.commons.models.IMessage
import java.util.Date
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
class ChatMessage(
    private var id: String = "",
    private var text: String = "",
    private var user: Author = Author(),
    @Exclude
    private var createdAt: Date = Date()) : IMessage {

    constructor() : this("", "", Author(), Date()) // Firebase용 빈 생성자

    override fun getId(): String = id
    override fun getText(): String = text
    override fun getUser(): Author = user
    override fun getCreatedAt(): Date = createdAt
}