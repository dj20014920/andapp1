package com.example.andapp1

import android.content.Intent
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.stfalcon.chatkit.commons.models.MessageContentType
import com.stfalcon.chatkit.messages.MessageHolders
import android.view.View


class IncomingImageMessageViewHolder(itemView: View) :
    MessageHolders.BaseIncomingMessageViewHolder<MessageContentType.Image>(itemView) {

    override fun onBind(message: MessageContentType.Image) {
        val imageView = itemView.findViewById<ImageView>(R.id.image)
        Glide.with(itemView.context)
            .load(message.imageUrl)
            .into(imageView)

        imageView.setOnClickListener {
            val context = itemView.context
            val intent = Intent(context, ImageViewerActivity::class.java).apply {
                putExtra("imageUrl", message.imageUrl)
            }
            context.startActivity(intent)
        }
    }
}
