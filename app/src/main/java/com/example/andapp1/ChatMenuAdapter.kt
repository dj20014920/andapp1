package com.example.andapp1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ChatMenuAdapter(
    private val menuItems: List<ChatMenuItem>,
    private val onItemClick: (ChatMenuItem) -> Unit
) : RecyclerView.Adapter<ChatMenuAdapter.MenuViewHolder>() {

    class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.menuCard)
        val icon: ImageView = view.findViewById(R.id.menuIcon)
        val title: TextView = view.findViewById(R.id.menuTitle)
        val subtitle: TextView = view.findViewById(R.id.menuSubtitle)
        val previewText: TextView = view.findViewById(R.id.previewText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_menu, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val menuItem = menuItems[position]
        
        holder.title.text = menuItem.title
        holder.subtitle.text = menuItem.subtitle
        holder.icon.setImageResource(menuItem.icon)
        
        // 배경색과 아이콘 색상 설정
        holder.card.setCardBackgroundColor(
            holder.itemView.context.getColor(menuItem.backgroundColor)
        )
        holder.icon.setColorFilter(
            holder.itemView.context.getColor(menuItem.iconTint)
        )
        
        // 미리보기 텍스트 설정
        if (menuItem.previewData != null) {
            holder.previewText.visibility = View.VISIBLE
            holder.previewText.text = menuItem.previewData
        } else {
            holder.previewText.visibility = View.GONE
        }
        
        holder.card.setOnClickListener {
            onItemClick(menuItem)
        }
    }

    override fun getItemCount() = menuItems.size
} 