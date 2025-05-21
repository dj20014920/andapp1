package com.example.andapp1
import com.example.andapp1.Author
import com.google.firebase.database.IgnoreExtraProperties
import com.stfalcon.chatkit.commons.models.IMessage
import com.google.firebase.database.Exclude
import com.stfalcon.chatkit.commons.models.IUser
import com.stfalcon.chatkit.commons.models.MessageContentType
import java.util.Date
@IgnoreExtraProperties
class ChatMessage(
    private var id: String = "",
    private var text: String = "",
    private var user: Author = Author(),
    var _imageUrl: String? = null,     // ✅ 추가
    private var mapUrl: String? = null,       // ✅ 추가
    @Exclude
    private var createdAt: Date = Date()
) : IMessage, MessageContentType.Image{

    constructor() : this("", "", Author(), null, null, Date())

    override fun getId(): String = id
    override fun getText(): String = text
    override fun getUser(): IUser = user
    override fun getCreatedAt(): Date = createdAt

    override fun getImageUrl(): String? = _imageUrl

    fun getMapUrl(): String? = mapUrl
    fun setMapUrl(url: String) { this.mapUrl = url }
}