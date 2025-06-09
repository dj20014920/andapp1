package com.example.andapp1

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RoomAdapter(
    private val rooms: MutableList<Room>,
    private val onItemClick: (Room) -> Unit,
    private val onMenuChangeNameClick: (Room) -> Unit,
    private val onMenuParticipantsClick: (Room) -> Unit,
    private val onMenuInviteCodeClick: (Room) -> Unit,
    private val onMenuLeaveRoomClick: (Room) -> Unit,
    private val onFavoriteToggle: (Room, Boolean) -> Unit
) : RecyclerView.Adapter<RoomAdapter.RoomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.room_item, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(rooms[position])
    }

    override fun getItemCount(): Int = rooms.size

    fun updateRooms(newRooms: MutableList<Room>) {
        Log.d("RoomAdapter", "🔥 updateRooms 호출됨. 방 수: ${newRooms.size}")
        val sortedRooms = newRooms.sortedWith(
            compareByDescending<Room> { it.isFavorite }
                .thenByDescending { it.lastActivityTime }
        )

        rooms.clear()
        rooms.addAll(sortedRooms)
        notifyDataSetChanged()
    }

    inner class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val roomTitleTextView: TextView = itemView.findViewById(R.id.roomTitleTextView)
        private val lastMessageTimeTextView: TextView = itemView.findViewById(R.id.lastMessageTimeTextView)
        private val roomMenuButton: ImageButton = itemView.findViewById(R.id.roomMenuButton)
        private val favoriteButton: ImageView = itemView.findViewById(R.id.favoriteButton)

        fun bind(room: Room) {
            roomTitleTextView.text = room.roomTitle
            lastMessageTimeTextView.text = "마지막 활동 : ${room.lastActivityTime}"

            // 즐겨찾기 버튼 상태 초기화
            updateFavoriteIcon(room.isFavorite)

            // 채팅방 클릭
            itemView.setOnClickListener {
                onItemClick(room)
            }

            // 메뉴 버튼
            roomMenuButton.setOnClickListener {
                showRoomOptionsDialog(room)
            }

            // 즐겨찾기 토글
            favoriteButton.setOnClickListener {
                val newFavoriteStatus = !room.isFavorite
                room.isFavorite = newFavoriteStatus  // ✅ 상태 갱신
                updateFavoriteIcon(newFavoriteStatus)
                onFavoriteToggle(room, newFavoriteStatus)  // ✅ 뷰모델 + DB 반영까지 전달
            }
        }

        private fun updateFavoriteIcon(isFavorite: Boolean) {
            favoriteButton.setImageResource(
                if (isFavorite) R.drawable.ic_star_filled
                else R.drawable.ic_star_border
            )
        }

        private fun showRoomOptionsDialog(room: Room) {
            DialogHelper.showRoomOptionsDialog(
                context = itemView.context,
                anchorView = roomMenuButton,
                room = room,
                onChangeNameClick = { onMenuChangeNameClick(room) },
                onParticipantsClick = { onMenuParticipantsClick(room) },
                onInviteCodeClick = { onMenuInviteCodeClick(room) },
                onLeaveRoomClick = { onMenuLeaveRoomClick(room) }
            )
        }
    }
}