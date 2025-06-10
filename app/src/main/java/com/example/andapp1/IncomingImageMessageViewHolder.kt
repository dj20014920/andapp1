package com.example.andapp1

import android.content.Intent
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.andapp1.ChatImageStore
import com.example.andapp1.ChatMessage
import com.example.andapp1.ImageViewerActivity
import com.example.andapp1.R
import com.stfalcon.chatkit.messages.MessageHolders

class IncomingImageMessageViewHolder(itemView: View) :
    MessageHolders.BaseIncomingMessageViewHolder<ChatMessage>(itemView) {

    override fun onBind(message: ChatMessage) {
        val imageView = itemView.findViewById<ImageView>(R.id.image)
        Glide.with(itemView.context).load(message.imageUrlValue).into(imageView)

        imageView.setOnClickListener {
            val url = message.imageUrlValue ?: return@setOnClickListener
            val allImages = ChatImageStore.imageMessages
            val idx = allImages.indexOf(url)
            val photoList = if (idx != -1) ArrayList(allImages) else arrayListOf(url)
            val position = if (idx != -1) idx else 0

            val intent = Intent(itemView.context, ImageViewerActivity::class.java).apply {
                putStringArrayListExtra("photoList", photoList)
                putExtra("startPosition", position)
            }

            itemView.context.startActivity(intent)
        }
    }
}
