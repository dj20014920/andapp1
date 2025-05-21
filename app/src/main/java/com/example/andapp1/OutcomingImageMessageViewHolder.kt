package com.example.andapp1

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.stfalcon.chatkit.commons.models.MessageContentType
import com.stfalcon.chatkit.messages.MessageHolders
import android.view.View


class OutcomingImageMessageViewHolder(itemView: View) :
    MessageHolders.BaseOutcomingMessageViewHolder<MessageContentType.Image>(itemView) {

    override fun onBind(message: MessageContentType.Image) {
        val imageView = itemView.findViewById<ImageView>(R.id.image)
        Glide.with(imageView.context).load(message.imageUrl).into(imageView)

    }
}
