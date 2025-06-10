package com.example.andapp1 // ✅ 완료

import android.content.Context
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.example.andapp1.databinding.CreateRoomDialogBinding
import android.widget.PopupMenu
import com.google.firebase.database.FirebaseDatabase
import android.util.Log
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView

object DialogHelper {

    /**
     * 일관된 스타일의 선택 다이얼로그 생성
     */
    fun showStyledChoiceDialog(
        context: Context,
        title: String,
        options: Array<String>,
        onItemSelected: (Int) -> Unit
    ) {
        AlertDialog.Builder(context, R.style.AppDialog)
            .setTitle(title)
            .setItems(options) { _, which ->
                onItemSelected(which)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 일관된 스타일의 확인 다이얼로그
     */
    fun showStyledConfirmDialog(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "확인",
        negativeText: String = "취소",
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context, R.style.AppDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onPositive?.invoke() }
            .setNegativeButton(negativeText) { _, _ -> onNegative?.invoke() }
            .show()
    }

    /**
     * 일관된 스타일의 입력 다이얼로그
     */
    fun showStyledInputDialog(
        context: Context,
        title: String,
        hint: String,
        initialText: String = "",
        onResult: (String) -> Unit
    ) {
        val editText = EditText(context).apply {
            setHint(hint)
            setText(initialText)
        }
        
        AlertDialog.Builder(context, R.style.AppDialog)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
                val result = editText.text.toString().trim()
                if (result.isNotEmpty()) {
                    onResult(result)
                } else {
                    editText.error = "값을 입력해주세요"
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 새로운 스타일의 채팅방 옵션 다이얼로그
     */
    fun showStyledRoomOptionsDialog(
        context: Context,
        room: Room,
        onChangeNameClick: () -> Unit,
        onParticipantsClick: () -> Unit,
        onInviteCodeClick: () -> Unit,
        onLeaveRoomClick: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_room_options, null)
        
        val changeNameCard = dialogView.findViewById<MaterialCardView>(R.id.item_change_name)
        val participantsCard = dialogView.findViewById<MaterialCardView>(R.id.item_participants)
        val inviteCodeCard = dialogView.findViewById<MaterialCardView>(R.id.item_invite_code)
        val leaveRoomCard = dialogView.findViewById<MaterialCardView>(R.id.item_leave_room)
        
        val dialog = AlertDialog.Builder(context, R.style.AppDialog)
            .setView(dialogView)
            .create()
            
        changeNameCard.setOnClickListener {
            dialog.dismiss()
            onChangeNameClick()
        }
        
        participantsCard.setOnClickListener {
            dialog.dismiss()
            onParticipantsClick()
        }
        
        inviteCodeCard.setOnClickListener {
            dialog.dismiss()
            onInviteCodeClick()
        }
        
        leaveRoomCard.setOnClickListener {
            dialog.dismiss()
            onLeaveRoomClick()
        }
        
        dialog.show()
    }

    /**
     * 새로운 스타일의 참여자 목록 다이얼로그
     */
    fun showStyledParticipantsDialog(context: Context, roomCode: String) {
        Log.d("DialogHelper", "참여자 다이얼로그 표시 - 방 코드: $roomCode")
        
        val participantsRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomCode)
            .child("participants")

        participantsRef.get().addOnSuccessListener { snapshot ->
            val userIds = snapshot.children.mapNotNull { it.key }

            if (userIds.isEmpty()) {
                showStyledConfirmDialog(
                    context = context,
                    title = "참여자 목록",
                    message = "참여자가 없습니다.",
                    positiveText = "확인"
                )
                return@addOnSuccessListener
            }

            val usersRef = FirebaseDatabase.getInstance().getReference("users")
            val participants = mutableListOf<Participant>()

            var loadedCount = 0
            for (userId in userIds) {
                usersRef.child(userId).get().addOnSuccessListener { userSnapshot ->
                    val nickname = userSnapshot.child("nickname").getValue(String::class.java) ?: "알 수 없음"
                    participants.add(Participant(userId, nickname, true)) // 임시로 모두 온라인으로 설정
                    loadedCount++

                    if (loadedCount == userIds.size) {
                        showParticipantsListDialog(context, participants)
                    }
                }
            }
        }.addOnFailureListener {
            showStyledConfirmDialog(
                context = context,
                title = "오류",
                message = "참여자 목록을 불러오지 못했습니다.",
                positiveText = "확인"
            )
        }
    }

    private fun showParticipantsListDialog(context: Context, participants: List<Participant>) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_participants, null)
        
        val titleView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerView)
        val closeButton = dialogView.findViewById<MaterialButton>(R.id.btn_close)
        
        titleView.text = "참여자 목록 (${participants.size}명)"
        
        val adapter = ParticipantsAdapter(participants.toMutableList())
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        val dialog = AlertDialog.Builder(context, R.style.AppDialog)
            .setView(dialogView)
            .create()
            
        closeButton.setOnClickListener { dialog.dismiss() }
        
        dialog.show()
    }

    fun showRoomOptionsDialog(
        context: Context,
        anchorView: View,
        room: Room,
        onChangeNameClick: () -> Unit,
        onParticipantsClick: () -> Unit,
        onInviteCodeClick: () -> Unit,
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
                R.id.menu_invite_code -> {
                    onInviteCodeClick();
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
        val roomNameEditText = EditText(context).apply {
            hint = "채팅방 이름 입력"
        }

        AlertDialog.Builder(context)
            .setTitle("새 채팅방 만들기")
            .setView(roomNameEditText)
            .setPositiveButton("만들기") { _, _ ->
                val roomName = roomNameEditText.text.toString().trim()
                if (roomName.isNotEmpty()) {
                    // 간단한 코드 생성 (실제로는 서버에서 생성하는 것이 좋음)
                    val roomCode = "ROOM${System.currentTimeMillis() % 10000}"
                    onCreate(roomName, roomCode)
                } else {
                    Toast.makeText(context, "채팅방 이름을 입력해주세요", Toast.LENGTH_SHORT).show()
                }
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

    fun showParticipantsDialog(context: Context, roomCode: String) {
        Log.d("DialogHelper", "참여자 다이얼로그 표시 - 방 코드: $roomCode")
        
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
                        showSimpleDialog(context, "참여자 목록", participantNames.joinToString("\n"))
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
            .setMessage("정말로 이 채팅방을 나가시겠습니까?")
            .setPositiveButton("나가기") { _, _ -> onLeave() }
            .setNegativeButton("취소", null)
            .show()
    }
    
    // 스타일이 적용된 참여자 다이얼로그
    fun showStyledParticipantsDialog(context: Context, roomCode: String) {
        val participantsRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomCode)
            .child("participants")

        participantsRef.get().addOnSuccessListener { snapshot ->
            val userIds = snapshot.children.mapNotNull { it.key }

            if (userIds.isEmpty()) {
                showStyledDialog(context, "참여자 목록", "참여자가 없습니다.")
                return@addOnSuccessListener
            }

            val usersRef = FirebaseDatabase.getInstance().getReference("users")
            val participantNames = mutableListOf<String>()

            var loadedCount = 0
            for (userId in userIds) {
                usersRef.child(userId).get().addOnSuccessListener { userSnapshot ->
                    val nickname = userSnapshot.child("nickname").getValue(String::class.java) ?: "알 수 없음"
                    participantNames.add("👤 $nickname")
                    loadedCount++

                    if (loadedCount == userIds.size) {
                        showStyledDialog(context, "💬 참여자 목록 (${participantNames.size}명)", participantNames.joinToString("\n"))
                    }
                }
            }
        }.addOnFailureListener {
            showStyledDialog(context, "⚠️ 오류", "참여자 목록을 불러오지 못했습니다.")
        }
    }
    
    // 스타일이 적용된 확인 다이얼로그
    fun showStyledConfirmDialog(
        context: Context, 
        title: String, 
        message: String, 
        positiveText: String = "확인",
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onConfirm() }
            .setNegativeButton("취소", null)
            .setCancelable(true)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        setTextColor(context.getColor(R.color.primary_color))
                        textSize = 16f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                        setTextColor(context.getColor(R.color.on_surface_variant))
                        textSize = 16f
                    }
                }
                show()
            }
    }
    
    // 스타일이 적용된 일반 다이얼로그
    fun showStyledDialog(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인", null)
            .setCancelable(true)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        setTextColor(context.getColor(R.color.primary_color))
                        textSize = 16f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                }
                show()
            }
    }
    
    // 스타일이 적용된 채팅방 옵션 다이얼로그
    fun showStyledRoomOptionsDialog(
        context: Context,
        room: Room,
        onEdit: () -> Unit,
        onParticipants: () -> Unit,
        onLeave: () -> Unit
    ) {
        val options = arrayOf("✏️ 채팅방 이름 변경", "👥 참여자 보기", "🚪 채팅방 나가기")
        
        AlertDialog.Builder(context)
            .setTitle("⚙️ ${room.roomTitle} 설정")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onEdit()
                    1 -> onParticipants()
                    2 -> onLeave()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

}
