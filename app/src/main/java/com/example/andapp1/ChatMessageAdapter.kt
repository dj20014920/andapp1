//ChatMessageAdapter.kt
package com.example.andapp1

import com.example.andapp1.ChatMessage
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.messages.MessageHolders
import com.stfalcon.chatkit.messages.MessagesListAdapter

class ChatMessageAdapter(senderId: String)
    : MessagesListAdapter<ChatMessage>(
    senderId,
    MessageHolders()
        .apply {
            setIncomingTextConfig(
                MessageHolders.IncomingTextMessageViewHolder::class.java,
                R.layout.item_incoming_text_message
            )
            setOutcomingTextConfig(
                MessageHolders.OutcomingTextMessageViewHolder::class.java,
                R.layout.item_outcoming_text_message
            )
        },
    ImageLoader { _, _, _ -> } // 빈 이미지 로더
)