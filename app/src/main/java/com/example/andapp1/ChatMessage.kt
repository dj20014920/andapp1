package com.example.andapp1
import com.example.andapp1.Author
import com.google.firebase.database.IgnoreExtraProperties
import com.stfalcon.chatkit.commons.models.IMessage
import com.google.firebase.database.Exclude
import com.stfalcon.chatkit.commons.models.IUser
import com.stfalcon.chatkit.commons.models.MessageContentType
import java.util.Date
@IgnoreExtraProperties
data class ChatMessage(
    var messageId: String = "",
    private var text: String = "",
    private var user: Author = Author(),
    var imageUrlValue: String? = null,     // ✅ 추가
    private var mapUrl: String? = null,       // ✅ 추가
    private var createdAt: Date = Date()
) : IMessage, MessageContentType.Image{

    constructor() : this("", "", Author(), null, null, Date())
    @Exclude
    override fun getId(): String = messageId

    override fun getText(): String = text
    override fun getUser(): Author = user
    override fun getCreatedAt(): Date = createdAt
    override fun getImageUrl(): String? = imageUrlValue

    fun getMapUrl(): String? = mapUrl
    fun setMapUrl(url: String) { this.mapUrl = url }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatMessage) return false

        return messageId == other.messageId
                || (text == other.text && user.getId() == other.user.getId()
                && createdAt.time == other.createdAt.time)
    }

    override fun hashCode(): Int {
        return messageId.hashCode() + text.hashCode() + createdAt.hashCode()
    }

}