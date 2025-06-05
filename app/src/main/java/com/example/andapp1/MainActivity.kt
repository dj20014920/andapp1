package com.example.andapp1

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.andapp1.databinding.ActivityMainBinding
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import com.kakao.sdk.common.KakaoSdk
import android.util.Base64
import java.security.MessageDigest
import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var roomAdapter: RoomAdapter
    private var roomsListener: ValueEventListener? = null
    private var currentUserId: String? = null

    private val MAX_ROOMS = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        // 카카오 SDK 초기화
        KakaoSdk.init(this, getString(R.string.kakao_native_app_key))

        // 사용자 정보 확인
        val prefs = getSharedPreferences("login", MODE_PRIVATE)
        currentUserId = prefs.getString("userId", null)
        val nickname = prefs.getString("nickname", null)
        val email = prefs.getString("email", null)
        val profileImage = prefs.getString("profileImageUrl", null)

        // 로그인 확인
        if (currentUserId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(binding.root)

        // 🎨 버튼 색상 강제 설정
        setupButtonColors()

        // Firebase에 사용자 정보 저장
        currentUserId?.let {
            checkAndSaveUserInfo(it, nickname, email)
            // ViewModel에 userId 설정
            setupViewModel(it)
        }

        setupRecyclerView()
        setupButtonClickListeners()
        setupFirebaseListener()

        // 디버깅용 해시키 출력
        getHashKey(this)
    }

    private fun setupButtonColors() {
        // 입장하기 버튼 - 그라디언트 배경
        val primaryGradient = GradientDrawable().apply {
            colors = intArrayOf(
                Color.parseColor("#4facfe"),
                Color.parseColor("#00c9ff"),
                Color.parseColor("#0093E9")
            )
            orientation = GradientDrawable.Orientation.TL_BR
            cornerRadius = 12f * resources.displayMetrics.density
        }
        binding.enterButton.background = primaryGradient
        binding.enterButton.setTextColor(Color.WHITE)

        // 방 생성하기 버튼 - 입장하기 버튼과 완전히 동일하게
        val secondaryGradient = GradientDrawable().apply {
            colors = intArrayOf(
                Color.parseColor("#4facfe"),
                Color.parseColor("#00c9ff"),
                Color.parseColor("#0093E9")
            )
            orientation = GradientDrawable.Orientation.TL_BR
            cornerRadius = 12f * resources.displayMetrics.density
        }
        binding.createRoomButton.background = secondaryGradient
        binding.createRoomButton.setTextColor(Color.WHITE)

        Log.d("MainActivity", "✅ 버튼 색상 강제 설정 완료 - 두 버튼 동일한 색상")
    }

    private fun setupViewModel(userId: String) {
        val db = RoomDatabaseInstance.getInstance(applicationContext)
        val factory = MainViewModelFactory(applicationContext)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
        viewModel.setCurrentUserId(userId)
    }

    private fun setupFirebaseListener() {
        currentUserId?.let { userId ->
            roomsListener = FirebaseRoomManager.roomsRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("MainActivity", "✅ Firebase 데이터 변경 감지")

                    val rooms = mutableListOf<Room>()
                    for (roomSnapshot in snapshot.children) {
                        val participantsSnapshot = roomSnapshot.child("participants")
                        val isParticipant = participantsSnapshot.hasChild(userId)

                        if (isParticipant) {
                            val room = roomSnapshot.getValue(Room::class.java)
                            room?.let {
                                // 로컬 DB에서 즐겨찾기 상태 가져오기
                                viewModel.checkFavoriteStatus(it)
                                rooms.add(it)
                            }
                        }
                    }

                    Log.d("MainActivity", "참여 중인 방 개수: ${rooms.size}")
                    viewModel.updateRoomsList(rooms)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("MainActivity", "❌ Firebase 읽기 실패: ${error.message}")
                    Toast.makeText(this@MainActivity, "채팅방 목록을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun setupRecyclerView() {
        roomAdapter = RoomAdapter(
            mutableListOf(),
            onItemClick = { room ->
                viewModel.updateLastActivityTime(room.roomCode, Util.getCurrentTime())

                FirebaseRoomManager.ensureRoomExists(room) {
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("roomCode", room.roomCode)
                    intent.putExtra("roomName", room.roomTitle)
                    intent.putExtra("userId", currentUserId)
                    startActivity(intent)
                }
            },
            onMenuChangeNameClick = { room ->
                DialogHelper.showChangeNameDialog(this, room) { newName ->
                    currentUserId?.let { userId ->
                        val author = Author(userId, getSharedPreferences("login", MODE_PRIVATE).getString("nickname", "Unknown") ?: "Unknown")
                        viewModel.changeRoomName(room.roomCode, newName, author)
                    }
                }
            },
            onMenuParticipantsClick = { room ->
                DialogHelper.showParticipantsDialog(this, room.roomCode)
            },

            onMenuInviteCodeClick = { room ->  // ✅ 여기!
                // 초대코드 다이얼로그 호출!
                showInviteCodeDialog(room)
            },

            onMenuLeaveRoomClick = { room ->
                DialogHelper.showLeaveRoomDialog(this) {
                    currentUserId?.let { userId ->
                        viewModel.leaveRoom(room.roomCode, userId)
                    }
                }
            },
            onFavoriteToggle = { room, isFavorite ->
                room.isFavorite = isFavorite
                val roomEntity = RoomEntity(
                    roomCode = room.roomCode,
                    roomTitle = room.roomTitle,
                    lastActivityTime = room.lastActivityTime,
                    isFavorite = isFavorite
                )

                if (isFavorite) {
                    viewModel.insertFavoriteRoom(roomEntity)
                } else {
                    viewModel.deleteFavoriteRoom(roomEntity)
                }

                val msg = if (isFavorite) "즐겨찾기 등록!" else "즐겨찾기 해제!"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )

        binding.roomsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.roomsRecyclerView.adapter = roomAdapter

        viewModel.rooms.observe(this) { updatedRooms ->
            Log.d("MainActivity", "UI 업데이트 - 방 개수: ${updatedRooms.size}")
            val sortedRooms = updatedRooms
                .sortedWith(compareByDescending<Room> { it.isFavorite }
                    .thenByDescending { it.lastActivityTime })

            roomAdapter.updateRooms(sortedRooms.toMutableList())
        }
    }

    private fun setupButtonClickListeners() {
        // 방 생성하기
        binding.createRoomButton.setOnClickListener {
            val currentRooms = viewModel.rooms.value ?: emptyList()
            if (currentRooms.size >= MAX_ROOMS) {
                Toast.makeText(this, "최대 채팅방 개수(5개)를 초과했습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            DialogHelper.showCreateRoomDialog(this) { roomName, currentTime ->
                val roomCode = Util.generateRandomCode()
                val roomLink = viewModel.generateRoomLink(roomCode)

                currentUserId?.let { userId ->
                    val newRoom = Room(
                        roomCode = roomCode,
                        roomTitle = roomName,
                        lastActivityTime = currentTime,
                        isFavorite = false
                    )

                    // Firebase에 방 생성과 동시에 참여자 추가
                    viewModel.createRoomWithParticipant(newRoom, userId)

                    val shareText = """
                        친구랑 같이 여행을 떠나요!-!
                        초대 코드 : $roomCode
                        초대 링크 : $roomLink
                    """.trimIndent()

                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }

                    val shareIntent = Intent.createChooser(sendIntent, "공유하기")
                    startActivity(shareIntent)
                }
            }
        }

        // 입장하기 버튼
        binding.enterButton.setOnClickListener {
            val input = binding.enterCodeOrLinkEditText.text.toString().trim().uppercase()
            val currentRooms = viewModel.rooms.value ?: emptyList()

            // ✅ 디버깅 로그 추가
            Log.d("RoomJoin", "=== 채팅방 입장 디버깅 ===")
            Log.d("RoomJoin", "입력값: '$input'")
            Log.d("RoomJoin", "입력값 길이: ${input.length}")
            Log.d("RoomJoin", "isRoomCode 결과: ${viewModel.isRoomCode(input)}")
            Log.d("RoomJoin", "isRoomLink 결과: ${viewModel.isRoomLink(input)}")

            if (currentRooms.size >= MAX_ROOMS) {
                Toast.makeText(this, "최대 채팅방 개수(5개)를 초과했습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val roomCode = when {
                viewModel.isRoomCode(input) -> {
                    Log.d("RoomJoin", "✅ 코드로 인식됨: $input")
                    input
                }
                viewModel.isRoomLink(input) -> {
                    val extracted = viewModel.extractRoomCodeFromLink(input)
                    Log.d("RoomJoin", "✅ 링크에서 추출된 코드: $extracted")
                    extracted
                }
                else -> {
                    Log.d("RoomJoin", "❌ 코드/링크 인식 실패")
                    null
                }
            }

            Log.d("RoomJoin", "최종 roomCode: $roomCode")

            if (roomCode == null) {
                Toast.makeText(this, "올바른 코드 또는 링크를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentRooms.any { it.roomCode == roomCode }) {
                Toast.makeText(this, "이미 참여한 채팅방입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Firebase에서 방 정보 확인 후 입장
            FirebaseRoomManager.roomsRef.child(roomCode).get().addOnSuccessListener { snapshot ->
                Log.d("RoomJoin", "Firebase 조회 결과 - 방 존재: ${snapshot.exists()}")
                if (snapshot.exists()) {
                    val room = snapshot.getValue(Room::class.java)
                    room?.let {
                        currentUserId?.let { userId ->
                            // 참여자로 추가
                            FirebaseRoomManager.addParticipant(roomCode, userId)
                            Toast.makeText(this, "채팅방에 입장했습니다.", Toast.LENGTH_SHORT).show()
                            binding.enterCodeOrLinkEditText.text.clear()
                        }
                    }
                } else {
                    Toast.makeText(this, "존재하지 않는 채팅방입니다.", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { error ->
                Log.e("RoomJoin", "Firebase 오류: ${error.message}")
                Toast.makeText(this, "채팅방 확인에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndSaveUserInfo(userId: String, nickname: String?, email: String?) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val userMap = hashMapOf(
                    "id" to userId,
                    "nickname" to (nickname ?: "사용자"),
                    "email" to (email ?: "")
                )
                userRef.setValue(userMap)
                Log.d("MainActivity", "사용자 정보 저장 완료")
            }
        }.addOnFailureListener {
            Log.e("MainActivity", "사용자 정보 조회 실패", it)
        }
    }

    private fun getHashKey(context: Context) {
        try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            info.signatures?.forEach { signature ->
                val md = MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val hashKey = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                Log.d("HashKey", "keyhash: $hashKey")
            }
        } catch (e: Exception) {
            Log.e("HashKey", "Error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 메모리 누수 방지를 위해 리스너 제거
        roomsListener?.let {
            FirebaseRoomManager.roomsRef.removeEventListener(it)
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면 복귀 시 목록 새로고침
        currentUserId?.let { userId ->
            viewModel.loadRooms(userId)
        }

        // 🎨 화면 복귀 시에도 버튼 색상 재설정
        setupButtonColors()
    }

    private fun showInviteCodeDialog(room: Room) {
        val inviteCode = room.roomCode

        AlertDialog.Builder(this)
            .setTitle("초대 코드")
            .setMessage("\n$inviteCode")
            .setPositiveButton("복사") { dialog, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("roomCode", inviteCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "초대 코드가 복사되었습니다!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

}