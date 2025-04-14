package com.example.andapp1 // ✅ 완료

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.andapp1.databinding.ActivityMainBinding
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.util.Utility
import com.kakao.sdk.user.UserApiClient
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var roomAdapter: RoomAdapter

    private val MAX_ROOMS = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        val prefs = getSharedPreferences("login", MODE_PRIVATE)
        val editor = prefs.edit()
        val userId = prefs.getString("userId", null)
        val nickname = prefs.getString("nickname", null)
        val email = prefs.getString("email", null)
        val profileImage  = prefs.getString("profileImageUrl", null)
        userId?.let { editor.putString("userId", it) }
        nickname?.let { editor.putString("nickname", it) }
        email?.let { editor.putString("email", it) }
        profileImage?.let { editor.putString("profileImageUrl", it) }

        editor.apply()
        if (userId != null) {
            checkAndSaveUserInfo(userId, nickname, email)
        }
        // prefs.edit().clear().apply() // ✅ 로그인 정보 초기화
        Log.d("AutoLogin", "SharedPreferences에서 불러온 userId = $userId")
        if (userId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(binding.root)

        // ViewModel 설정
        val db = RoomDatabaseInstance.getInstance(applicationContext)
        val factory = MainViewModelFactory(applicationContext)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
        KakaoSdk.init(this, getString(R.string.kakao_native_app_key))
        //카카오 로그인
        // val loginButton = findViewById<Button>(R.id.kakao_login_button)
        val userDao = db.userDao()

        lifecycleScope.launch {
            val savedUser = withContext(Dispatchers.IO) {
                userDao.getUser() // Room에서 자동 로그인용 유저 가져오기
            }

            savedUser?.let { user ->
                Log.d("AutoLogin", "자동 로그인 성공: ${user.nickname}")
                // 자동 로그인 처리: 채팅방 화면 진입 등
                // startActivity(Intent(this@MainActivity, ChatListActivity::class.java)) 또는 처리 로직
            }
        }


        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
        setupRecyclerView()
        setupButtonClickListeners()
    }

    private fun setupRecyclerView() {
        roomAdapter = RoomAdapter(
            mutableListOf(),
            onItemClick = { room ->
                viewModel.updateLastActivityTime(room.roomCode, Util.getCurrentTime())

                FirebaseRoomManager.ensureRoomExists(room) {
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("roomName", room.roomTitle)
                    intent.putExtra("roomCode", room.roomCode)
                    startActivity(intent)
                }
            },
            onMenuChangeNameClick = { room ->
                DialogHelper.showChangeNameDialog(this, room) { newName ->
                    val oldName = room.roomTitle
                    viewModel.changeRoomName(room.roomCode, newName)

                    // 시스템 메시지 전송
                    val systemMessage = "⚙️ 채팅방 이름이 '$oldName'에서 '$newName'으로 변경되었습니다."
                    // ChatViewModel 인스턴스가 필요한데 현재 MainActivity에는 없으므로
                    // ChatActivity에서 이 메시지를 보내야 해
                }
            },
            onMenuParticipantsClick = { room ->
                DialogHelper.showParticipantsDialog(this, room.roomCode)
            },
            onMenuLeaveRoomClick = { room ->
                DialogHelper.showLeaveRoomDialog(this) {
                    viewModel.leaveRoom(room.roomCode)  // ✅ 새로운 함수로 변경 (deleteRoom ❌)
                } },
            onFavoriteToggle = { room, isFavorite ->
                // Room 객체 상태 변경
                room.isFavorite = isFavorite

                // 로컬 RoomDB에 저장
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

                // ✅ UI에 반영
                val msg = if (isFavorite) "즐겨찾기 등록!" else "즐겨찾기 해제!"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

                val currentRooms = viewModel.rooms.value?.toMutableList() ?: mutableListOf()
                val index = currentRooms.indexOfFirst { it.roomCode == room.roomCode }
                if (index != -1) {
                    currentRooms[index] = currentRooms[index].copy(isFavorite = isFavorite)
                    roomAdapter.updateRooms(currentRooms)
                }
            }
        )

        binding.roomsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.roomsRecyclerView.adapter = roomAdapter

        viewModel.rooms.observe(this) { updatedRooms ->
            Log.d("MainActivity", "받은 채팅방 목록: ${updatedRooms.size}")
            val sortedRooms = updatedRooms
                .sortedWith(compareByDescending<Room> { it.isFavorite }
                    .thenByDescending { it.lastActivityTime })

            roomAdapter.updateRooms(sortedRooms.toMutableList())
        }
    }
    private fun checkAndSaveUserInfo(userId: String, nickname: String?, email: String?) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // 아직 저장된 적이 없으면 저장
                val user = User(userId, nickname, email)
                userRef.setValue(user)
                Log.d("AutoLogin", "✅ Firebase에 사용자 정보 저장 완료")
            } else {
                Log.d("AutoLogin", "ℹ️ 사용자 정보가 이미 Firebase에 존재함")
            }
        }.addOnFailureListener {
            Log.e("AutoLogin", "❌ 사용자 정보 조회 실패", it)
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

                val newRoom = Room(
                    roomCode = roomCode,
                    roomTitle = roomName,
                    lastActivityTime = currentTime,
                    isFavorite = false
                )

                viewModel.addRoom(newRoom)

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

        // 입장하기 버튼 클릭 시
        binding.enterButton.setOnClickListener {
            val input = binding.enterCodeOrLinkEditText.text.toString().trim().uppercase()
            val currentRooms = viewModel.rooms.value ?: emptyList()
            if (currentRooms.size >= MAX_ROOMS) {
                Toast.makeText(this, "최대 채팅방 개수(5개)를 초과했습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val roomCode = when {
                viewModel.isRoomCode(input) -> input
                viewModel.isRoomLink(input) -> viewModel.extractRoomCodeFromLink(input)
                else -> null
            }

            if (roomCode == null) {
                Toast.makeText(this, "올바른 코드 또는 링크를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentRooms.any { it.roomCode == roomCode }) {
                Toast.makeText(this, "이미 참여한 채팅방입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newRoom = Room(
                roomCode = roomCode,
                roomTitle = "채팅방 : $roomCode",
                lastActivityTime = Util.getCurrentTime(),
                isFavorite = false
            )

            viewModel.addRoom(newRoom)

            Log.d("MainActivity", "방 생성 요청: ${newRoom.roomCode}")

            val data: Uri? = intent?.data
            data?.let {
                val roomCode = it.getQueryParameter("code") // URL의 ?code=XXX-XXX 추출
                if (!roomCode.isNullOrEmpty()) {
                    val newRoom = Room(
                        roomCode = roomCode,
                        roomTitle = "채팅방 : $roomCode",
                        lastActivityTime = Util.getCurrentTime(),
                        isFavorite = false
                    )

                    viewModel.addRoom(newRoom)

                    val intent = Intent(this, ChatActivity::class.java).apply {
                        putExtra("roomCode", roomCode)
                        putExtra("roomName", newRoom.roomTitle)
                    }
                    startActivity(intent)
                }
            }
        }
    }
}