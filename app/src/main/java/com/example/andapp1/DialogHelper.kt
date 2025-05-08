package com.example.andapp1 // ✅ 완료

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.example.andapp1.databinding.CreateRoomDialogBinding
import android.widget.PopupMenu
import com.google.firebase.database.FirebaseDatabase

object DialogHelper {

    fun showRoomOptionsDialog(
        context: Context,
        anchorView: View,
        room: Room,
        onChangeNameClick: () -> Unit,
        onParticipantsClick: () -> Unit,
        onLeaveRoomClick: () -> Unit
    ) {
        val popup = PopupMenu(context, anchorView)
        popup.menuInflater.inflate(R.menu.room_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_change_name -> {
                    onChangeNameClick()
                    true
                }
                R.id.menu_participants -> {
                    onParticipantsClick()
                    true
                }
                R.id.menu_leave_room -> {
                    onLeaveRoomClick()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }
    // 채팅방 생성 다이얼로그
    fun showCreateRoomDialog(context: Context, onCreate: (String, String) -> Unit) {
        val binding = CreateRoomDialogBinding.inflate(LayoutInflater.from(context))

        AlertDialog.Builder(context)
            .setTitle("채팅방 생성")
            .setView(binding.root)
            .setPositiveButton("생성") { _, _ ->
                val roomName = binding.editRoomName.text.toString()
                val currentTime = Util.getCurrentTime()
                onCreate(roomName, currentTime)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 채팅방 이름 변경 다이얼로그
    fun showChangeNameDialog(context: Context, room: Room, onRename: (String) -> Unit) {
        val editText = EditText(context).apply {
            setText(room.roomTitle)
        }

        AlertDialog.Builder(context)
            .setTitle("채팅방 이름 변경")
            .setView(editText)
            .setPositiveButton("변경") { _, _ ->
                val newName = editText.text.toString()
                onRename(newName)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 참여자 보기 다이얼로그
    fun showParticipantsDialog(context: Context, roomCode: String) {
        val participantsRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomCode)
            .child("participants")

        participantsRef.get().addOnSuccessListener { snapshot ->
            val userIds = snapshot.children.mapNotNull { it.key }

            if (userIds.isEmpty()) {
                showSimpleDialog(context, "참여자 목록", "참여자가 없습니다.")
                return@addOnSuccessListener
            }

            val usersRef = FirebaseDatabase.getInstance().getReference("users")
            val participantNames = mutableListOf<String>()

            var loadedCount = 0
            for (userId in userIds) {
                usersRef.child(userId).get().addOnSuccessListener { userSnapshot ->
                    val nickname = userSnapshot.child("nickname").getValue(String::class.java) ?: "알 수 없음"
                    participantNames.add(nickname)
                    loadedCount++

                    if (loadedCount == userIds.size) {
                        AlertDialog.Builder(context)
                            .setTitle("참여자 목록")
                            .setMessage(participantNames.joinToString("\n"))
                            .setPositiveButton("초대하기") { _, _ ->
                                val shareText = """
                                초대 코드 : $roomCode
                                초대 링크 : https://example.com/room?code=$roomCode
                            """.trimIndent()

                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "공유하기"))
                            }
                            .setNegativeButton("닫기", null)
                            .show()
                    }
                }
            }
        }.addOnFailureListener {
            showSimpleDialog(context, "오류", "참여자 목록을 불러오지 못했습니다.")
        }
    }

    fun showSimpleDialog(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }
    // 채팅방 나가기 확인 다이얼로그
    fun showLeaveRoomDialog(context: Context, onLeave: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("채팅방 나가기")
            .setMessage("정말로 채팅방을 나가시겠습니까?")
            .setPositiveButton("나가기") { _, _ -> onLeave() }
            .setNegativeButton("취소", null)
            .show()
    }

}