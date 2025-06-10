package com.example.andapp1

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.database.FirebaseDatabase

class ChatRoomMenuActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var menuRecyclerView: RecyclerView
    private lateinit var menuAdapter: ChatMenuAdapter
    private lateinit var viewModel: ChatViewModel
    private lateinit var roomNameText: TextView
    private lateinit var participantCountText: TextView
    private var roomCode: String = ""
    private var roomName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room_menu)

        // Intent에서 데이터 받기
        roomCode = intent.getStringExtra("roomCode") ?: ""
        roomName = intent.getStringExtra("roomName") ?: "채팅방"

        setupViews()
        setupToolbar()
        setupMenuItems()
        setupViewModel()
        loadRoomInfo()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        menuRecyclerView = findViewById(R.id.menuRecyclerView)
        roomNameText = findViewById(R.id.roomNameText)
        participantCountText = findViewById(R.id.participantCountText)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = roomName
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(
            this,
            ChatViewModelFactory(roomCode, applicationContext)
        )[ChatViewModel::class.java]
    }

    private fun setupMenuItems() {
        val menuItems = listOf(
            ChatMenuItem(
                id = "photos",
                title = "사진/동영상",
                subtitle = "공유된 미디어 보기",
                icon = R.drawable.ic_camera,
                backgroundColor = R.color.pastel_blue_100,
                iconTint = R.color.primary_color
            ),
            ChatMenuItem(
                id = "map",
                title = "지도",
                subtitle = "위치 공유 및 장소 확인",
                icon = R.drawable.ic_map,
                backgroundColor = R.color.pastel_green_100,
                iconTint = R.color.pastel_green_200
            ),
            ChatMenuItem(
                id = "scrap",
                title = "스크랩 모음",
                subtitle = "저장된 장소 및 링크",
                icon = R.drawable.ic_bookmark,
                backgroundColor = R.color.pastel_yellow_100,
                iconTint = R.color.pastel_yellow_200
            ),
            ChatMenuItem(
                id = "participants",
                title = "참여자 목록",
                subtitle = "대화 참여자 관리",
                icon = R.drawable.ic_group,
                backgroundColor = R.color.pastel_purple_100,
                iconTint = R.color.pastel_purple_200
            ),
            ChatMenuItem(
                id = "expense",
                title = "여행 경비",
                subtitle = "공동 경비 관리",
                icon = R.drawable.receipt,
                backgroundColor = R.color.pastel_orange_100,
                iconTint = R.color.pastel_orange_200
            ),
            ChatMenuItem(
                id = "settings",
                title = "채팅방 설정",
                subtitle = "알림, 이름 변경 등",
                icon = R.drawable.ic_settings,
                backgroundColor = R.color.gray_100,
                iconTint = R.color.gray_600
            )
        )

        menuAdapter = ChatMenuAdapter(menuItems) { menuItem ->
            handleMenuItemClick(menuItem)
        }

        menuRecyclerView.layoutManager = GridLayoutManager(this, 2)
        menuRecyclerView.adapter = menuAdapter
    }

    private fun handleMenuItemClick(menuItem: ChatMenuItem) {
        when (menuItem.id) {
            "photos" -> {
                val intent = Intent(this, PhotoGalleryActivity::class.java).apply {
                    putExtra("roomCode", roomCode)
                    putExtra("roomName", roomName)
                }
                startActivity(intent)
            }
            "map" -> {
                val intent = Intent(this, MapActivity::class.java).apply {
                    putExtra("roomCode", roomCode)
                }
                startActivity(intent)
            }
            "scrap" -> {
                ScrapDialogHelper.showScrapListDialog(this, roomCode)
            }
            "participants" -> {
                DialogHelper.showStyledParticipantsDialog(this, roomCode)
            }
            "expense" -> {
                val intent = Intent(this, com.example.andapp1.expense.TravelExpenseActivity::class.java).apply {
                    putExtra("chatId", roomCode)
                    putExtra("roomName", roomName)
                }
                startActivity(intent)
            }
            "settings" -> {
                showRoomSettingsDialog()
            }
        }
    }

    private fun showRoomSettingsDialog() {
        // 임시 Room 객체 생성 (실제로는 뷰모델에서 가져와야 함)
        val room = Room(
            roomCode = roomCode,
            roomTitle = roomName,
            lastActivityTime = System.currentTimeMillis().toString(),
            isFavorite = false,
            participants = emptyMap()
        )

        DialogHelper.showStyledRoomOptionsDialog(
            context = this,
            room = room,
            onChangeNameClick = {
                DialogHelper.showChangeNameDialog(this, room) { newName ->
                    updateRoomName(newName)
                }
            },
            onParticipantsClick = {
                DialogHelper.showStyledParticipantsDialog(this, roomCode)
            },
            onInviteCodeClick = {
                DialogHelper.showStyledConfirmDialog(
                    context = this,
                    title = "초대 코드",
                    message = "채팅방 코드: $roomCode\n\n이 코드를 친구들과 공유하여 채팅방에 초대하세요.",
                    positiveText = "복사하기",
                    onPositive = {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("roomCode", roomCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "초대 코드가 복사되었습니다", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onLeaveRoomClick = {
                DialogHelper.showStyledConfirmDialog(
                    context = this,
                    title = "채팅방 나가기",
                    message = "정말로 이 채팅방을 나가시겠습니까?\n나간 후에는 대화 내용을 볼 수 없습니다.",
                    positiveText = "나가기",
                    onPositive = {
                        leaveRoom()
                    }
                )
            }
        )
    }

    private fun updateRoomName(newName: String) {
        val roomRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomCode)

        roomRef.child("roomTitle").setValue(newName)
            .addOnSuccessListener {
                supportActionBar?.title = newName
                Toast.makeText(this, "채팅방 이름이 변경되었습니다", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "채팅방 이름 변경에 실패했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun leaveRoom() {
        // 실제 구현에서는 사용자를 방에서 제거하는 로직이 필요
        Toast.makeText(this, "채팅방을 나갔습니다", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun loadRoomInfo() {
        roomNameText.text = roomName
        
        // Firebase에서 참여자 수 로드
        val participantsRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomCode)
            .child("participants")
            
        participantsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val participantCount = snapshot.childrenCount.toInt()
                participantCountText.text = "참여자 ${participantCount}명"
            }
            
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                participantCountText.text = "참여자 수 로드 실패"
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

data class ChatMenuItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: Int,
    val backgroundColor: Int,
    val iconTint: Int,
    val previewData: String? = null
) 