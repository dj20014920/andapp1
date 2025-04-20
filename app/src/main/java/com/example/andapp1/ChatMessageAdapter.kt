/*package com.example.andapp1
import com.example.andapp1.ChatMessage
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.messages.MessageHolders
import com.stfalcon.chatkit.messages.MessagesListAdapter

class ChatMessageAdapter(senderId: String) : MessagesListAdapter<ChatMessage>(
    senderId,
    MessageHolders()
        .apply {
            // 텍스트
            setIncomingTextConfig(
                MessageHolders.IncomingTextMessageViewHolder::class.java,
                R.layout.item_incoming_text_message
            )
            setOutcomingTextConfig(
                MessageHolders.OutcomingTextMessageViewHolder::class.java,
                R.layout.item_outcoming_text_message
            )
            // 이미지
            setIncomingImageConfig(
                MessageHolders.IncomingImageMessageViewHolder::class.java,
                R.layout.item_incoming_image_message
            )
            setOutcomingImageConfig(
                MessageHolders.OutcomingImageMessageViewHolder::class.java,
                R.layout.item_outcoming_image_message
            )
        },
    ImageLoader { imageView, url, _ ->
        GlideApp.with(imageView.context)
            .load(url)
            .into(imageView)
    }
) */