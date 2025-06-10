package com.example.andapp1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScrapAdapter(
    private val scrapList: List<ScrapItem>,
    private val onItemClick: (ScrapItem) -> Unit
) : RecyclerView.Adapter<ScrapAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.scrap_name)
        val url: TextView = view.findViewById(R.id.scrap_url)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scrap, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val scrapItem = scrapList[position]
        
        holder.name.text = scrapItem.name
        holder.url.text = scrapItem.url
        
        holder.itemView.setOnClickListener {
            onItemClick(scrapItem)
        }
    }

    override fun getItemCount() = scrapList.size
} 