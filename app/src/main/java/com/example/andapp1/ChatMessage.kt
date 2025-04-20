package com.example.andapp1
import com.example.andapp1.Author
import com.google.firebase.database.IgnoreExtraProperties
import com.stfalcon.chatkit.commons.models.IMessage
import com.google.firebase.database.Exclude
import java.util.Date
@IgnoreExtraProperties
class ChatMessage(
    private var id: String = "",
    private var text: String = "",
    private var user: Author = Author(),
    private var imageUrl: String? = null,     // ✅ 추가
    private var mapUrl: String? = null,       // ✅ 추가
    @Exclude
    private var createdAt: Date = Date()
) : IMessage {

    constructor() : this("", "", Author(), null, null, Date())

    override fun getId(): String = id
    override fun getText(): String = text
    override fun getUser(): Author = user
    override fun getCreatedAt(): Date = createdAt

    fun getImageUrl(): String? = imageUrl      // ✅ 외부 접근용 getter
    fun getMapUrl(): String? = mapUrl

    fun setImageUrl(url: String) { this.imageUrl = url }  // 필요 시 setter도 가능
    fun setMapUrl(url: String) { this.mapUrl = url }
}