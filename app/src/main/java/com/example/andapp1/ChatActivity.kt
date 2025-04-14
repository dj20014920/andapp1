//ChatActivity.kt
package com.example.andapp1

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.andapp1.databinding.ActivityChatBinding
import com.example.andapp1.ChatMessage
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatMessageAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var lastMapUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val roomCode = intent.getStringExtra("roomCode") ?: "default_room"
        val roomName = intent.getStringExtra("roomName") ?: "채팅방"
        setSupportActionBar(binding.toolbar) // -> activity_chat.xml에 Toolbar가 있어야 함

        supportActionBar?.title = roomName

        setSupportActionBar(binding.toolbar)

        // ✅ ViewModel은 한 번만 생성
        val factory = ChatViewModelFactory(roomCode, applicationContext)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]

        // ✅ 코루틴에서 userId 가져오기
        lifecycleScope.launch {
            val user = RoomDatabaseInstance.getInstance(applicationContext).userDao().getUser()
            if (user != null) {
                val userId = user.id
                addParticipantToRoom(roomCode, userId)
            }
        }

        setupRecyclerView()
        observeMessages()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("ChatActivity", "🌐 onNewIntent 호출됨") // 추가
        intent?.getStringExtra("mapUrl")?.let { url ->
            Log.d("ChatActivity", "🌐 받은 지도 URL: $url") // 추가
            lastMapUrl = url
            showMapRestoreButton()
        }
    }

    private fun showMapRestoreButton() {
        val fab = FloatingActionButton(this).apply {
            setImageResource(R.drawable.ic_map)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END // ✅ 오른쪽 위로 변경
                marginEnd = 32
                topMargin = 100
            }

            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.tag = Triple(event.rawX, event.rawY, false)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val (startX, startY, _) = view.tag as Triple<Float, Float, Boolean>
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        val isDragged = dx * dx + dy * dy > 100
                        if (isDragged) {
                            view.x += dx
                            view.y += dy
                            view.tag = Triple(event.rawX, event.rawY, true)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val (_, _, isDragged) = view.tag as Triple<Float, Float, Boolean>
                        if (!isDragged) {
                            lastMapUrl?.let { url ->
                                val intent = Intent(this@ChatActivity, MapActivity::class.java)
                                intent.putExtra("mapUrl", url)
                                startActivity(intent)
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        val rootView = findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(fab)
    }

    private fun setupRecyclerView() {
        val senderId = "user1"
        adapter = ChatMessageAdapter(senderId)
        layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // ✅ 최신 채팅이 아래 정렬되게 함
        }

        // ✅ MessagesList는 내부적으로 RecyclerView이므로 명확히 설정
        binding.messagesList.setLayoutManager(layoutManager)
        binding.messagesList.setAdapter(adapter)

        binding.customMessageInput.setInputListener { input ->
            val text = input.toString()
            viewModel.sendMessage(text)

            Handler(Looper.getMainLooper()).postDelayed({
                scrollToBottomSmooth()
            }, 150)

            true
        }
    }

    fun addParticipantToRoom(roomCode: String, userId: String) {
        val participantsRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomCode)
            .child("participants")

        participantsRef.child(userId).setValue(true)
    }

    private fun observeMessages() {
        viewModel.messages.observe(this) { messages ->
            Log.d("ChatActivity", "📨 메시지 수: ${messages.size}")
            val sorted = messages.sortedBy { it.createdAt.time }

            adapter.clear()
            adapter.addToEnd(sorted, true)

            // RecyclerView가 렌더링 완료된 후 스크롤
            // observeMessages 내부에서 변경
            binding.messagesList.postDelayed({
                val lastIndex = adapter.itemCount
                if (lastIndex >= 0) {
                    binding.messagesList.scrollToPosition(lastIndex+1)
                }
            }, 300)
        }
    }

    private fun scrollToBottomSmooth() {
        binding.messagesList.postDelayed({
            if (adapter.itemCount > 0) {
                val lastIndex = adapter.itemCount
                binding.messagesList.scrollToPosition(lastIndex+1)
            }
        }, 300) // ✅ 시간 여유 줘서 확실히 반영되게
    }
    //메뉴버튼
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_open_map -> {
                // ✅ 지도 액티비티로 이동
                val intent = Intent(this, MapActivity::class.java)
                intent.putExtra("roomCode", viewModel.roomCode) // 채팅방 코드 넘기기 (필요 시)
                startActivity(intent)
                return true
            }

            R.id.menu_scrap_list -> {
                // ✅ 스크랩 목록 다이얼로그 띄우기 (또는 액티비티 이동)
                ScrapDialogHelper.showScrapListDialog(this, viewModel.roomCode)
                return true
            }

            R.id.menu_participants -> {
                // ✅ 참여자 목록 다이얼로그 띄우기
                DialogHelper.showParticipantsDialog(this, viewModel.roomCode)
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }
}
