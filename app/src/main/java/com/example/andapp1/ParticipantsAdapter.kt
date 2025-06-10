package com.example.andapp1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ParticipantsAdapter(
    private var participants: MutableList<Participant>
) : RecyclerView.Adapter<ParticipantsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.avatar)
        val name: TextView = view.findViewById(R.id.name)
        val status: TextView = view.findViewById(R.id.status)
        val onlineIndicator: View = view.findViewById(R.id.online_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val participant = participants[position]
        
        holder.name.text = participant.nickname ?: "알 수 없음"
        holder.status.text = if (participant.isOnline) "온라인" else "오프라인"
        
        // 온라인 상태에 따른 표시
        holder.onlineIndicator.visibility = if (participant.isOnline) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    override fun getItemCount() = participants.size

    fun updateParticipants(newParticipants: List<Participant>) {
        participants.clear()
        participants.addAll(newParticipants)
        notifyDataSetChanged()
    }
}

data class Participant(
    val userId: String = "",
    val nickname: String? = null,
    val isOnline: Boolean = false
) 