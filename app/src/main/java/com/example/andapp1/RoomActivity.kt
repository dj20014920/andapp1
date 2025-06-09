package com.example.andapp1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.andapp1.databinding.ActivityRoomBinding
import com.google.firebase.database.*
import kotlinx.coroutines.launch

class RoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoomBinding
    private lateinit var adapter: RoomAdapter
    private lateinit var currentUserId: String
    private val rooms = mutableListOf<Room>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 현재 로그인한 사용자 ID 가져오기
        lifecycleScope.launch {
            val user = RoomDatabaseInstance.getInstance(applicationContext).userDao().getUser()
            currentUserId = user?.id ?: return@launch
            // 2. 어댑터 초기화
            adapter = RoomAdapter(
                rooms,
                onItemClick = { room ->
                    val roomCode = room.roomCode
                    val participantsRef = FirebaseDatabase.getInstance()
                        .getReference("rooms")
                        .child(roomCode)
                        .child("participants")
                        .child(currentUserId)

                    participantsRef.get().addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            // ✅ 참가자인 경우에만 입장 허용
                            val intent = Intent(this@RoomActivity, ChatActivity::class.java).apply {
                                putExtra("roomCode", room.roomCode)
                                putExtra("roomName", room.roomTitle)
                            }
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@RoomActivity, "이미 나간 채팅방입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onMenuChangeNameClick = { room -> /* 이름 변경 구현 */ },
                onMenuParticipantsClick = { room -> /* 참여자 목록 */ },
                onMenuLeaveRoomClick = { room -> leaveRoom(room) },
                onMenuInviteCodeClick = { room ->
                    showInviteCodeDialog(room)
                },
                onFavoriteToggle = { room, isFavorite -> /* 즐겨찾기 처리 */ }
            )

            binding.recyclerViewRooms.layoutManager = LinearLayoutManager(this@RoomActivity)
            binding.recyclerViewRooms.adapter = adapter

            // 3. 참여 중인 방만 가져오기
            loadJoinedRooms()
        }
    }

    private fun loadJoinedRooms() {
        val dbRef = FirebaseDatabase.getInstance().getReference("rooms")

        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val joinedRooms = mutableListOf<Room>()
                for (roomSnapshot in snapshot.children) {
                    val participants = roomSnapshot.child("participants")
                    if (participants.hasChild(currentUserId)) {
                        val room = roomSnapshot.getValue(Room::class.java)
                        if (room != null) joinedRooms.add(room)
                    }
                }
                rooms.clear()
                rooms.addAll(joinedRooms)
                adapter.updateRooms(rooms)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("RoomActivity", "❌ Firebase 읽기 실패: ${error.message}")
            }
        })
    }

    private fun leaveRoom(room: Room) {
        val ref = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(room.roomCode)
            .child("participants")
            .child(currentUserId)

        ref.removeValue().addOnSuccessListener {
            Toast.makeText(this, "채팅방에서 나갔습니다.", Toast.LENGTH_SHORT).show()

            // ✅ API 23 호환 방식으로 수정
            val iterator = rooms.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().roomCode == room.roomCode) {
                    iterator.remove()
                }
            }
            adapter.updateRooms(rooms)
        }
    }
    private fun showInviteCodeDialog(room: Room) {
        val inviteCode = room.roomCode

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("초대 코드")
            .setMessage("이 채팅방의 초대 코드는\n$inviteCode 입니다.")
            .setPositiveButton("복사") { dialog, _ ->
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("roomCode", inviteCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "초대 코드가 복사되었습니다!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

}
