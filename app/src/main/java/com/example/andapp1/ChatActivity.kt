package com.example.andapp1

import android.content.Intent
import android.os.*
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.andapp1.databinding.ActivityChatBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.FirebaseDatabase
import com.stfalcon.chatkit.messages.*
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var adapter: MessagesListAdapter<ChatMessage>

    override fun onResume() {
        super.onResume()

        // 만약 버튼이 안 떠 있는 상태라면 강제로 띄워줌
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val existing = rootView.findViewWithTag<FloatingActionButton>("map_restore_button")
        if (existing == null) {
            showMapRestoreButton()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val roomCode = intent.getStringExtra("roomCode") ?: "default_room"
        val roomName = intent.getStringExtra("roomName") ?: "채팅방"
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = roomName

        viewModel = ViewModelProvider(this, ChatViewModelFactory(roomCode, applicationContext))[ChatViewModel::class.java]

        layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.messagesList.layoutManager = layoutManager
        // 어댑터 초기화 및 UI 연결
        initializeAdapterAndListeners()
    }

    private fun initializeAdapterAndListeners() {
        lifecycleScope.launch {
            val user = RoomDatabaseInstance.getInstance(applicationContext).userDao().getUser()
            val senderId = user?.id ?: "unknown"

            val holders = MessageHolders()
                .setIncomingTextConfig(TextMessageViewHolder::class.java, R.layout.item_incoming_text_message)
                .setOutcomingTextConfig(TextMessageViewHolder::class.java, R.layout.item_outcoming_text_message)

            adapter = MessagesListAdapter(senderId, holders, null)
            binding.messagesList.setAdapter(adapter)

            // 메시지 전송 처리
            binding.customMessageInput.setInputListener { input ->
                val text = input.toString()
                viewModel.sendMessage(text)

                Handler(Looper.getMainLooper()).postDelayed({
                    scrollToBottomSmooth()
                }, 300)

                true
            }

            observeMessages()
        }
    }

    private fun observeMessages() {
        viewModel.messages.observe(this) { messages ->
            adapter.clear()
            adapter.addToEnd(messages.sortedBy { it.createdAt.time }, true)

            // 안전하게 스크롤
            binding.messagesList.post {
                val lastIndex = adapter.itemCount
                binding.messagesList.scrollToPosition(lastIndex)

                Handler(Looper.getMainLooper()).postDelayed({
                    binding.messagesList.scrollToPosition(lastIndex)
                    Log.d("스크롤 디버그", "✅ 재확인 스크롤: $lastIndex")
                }, 150)
            }
        }
    }

    private fun scrollToBottomSmooth() {
        binding.messagesList.postDelayed({
            if (adapter.itemCount > 0) {
                binding.messagesList.scrollToPosition(adapter.itemCount)
                Log.d("스크롤 디버그", "Adapter size = ${adapter.itemCount}")
            }
        }, 300)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.getStringExtra("mapUrl")?.let { url ->
            viewModel.sendMapUrlMessage(url)
            showMapRestoreButton()
        }

        intent?.getStringExtra("scrapText")?.let { scrapText ->
            viewModel.sendMessage(scrapText)
        }
    }

    private fun showMapRestoreButton() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        if (rootView.findViewWithTag<View>("map_restore_button") != null) return

        val fab = FloatingActionButton(this).apply {
            tag = "map_restore_button"
            setImageResource(R.drawable.ic_map)
            setBackgroundTintList(ContextCompat.getColorStateList(context, android.R.color.white))
            setColorFilter(ContextCompat.getColor(context, android.R.color.black))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                marginEnd = 32
                topMargin = 100
            }

            setOnClickListener {
                val intent = Intent(this@ChatActivity, MapActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                startActivity(intent)
            }
        }

        // ✅ 드래그 기능 추가
        fab.setOnTouchListener(object : View.OnTouchListener {
            private var downRawX = 0f
            private var downRawY = 0f
            private var dX = 0f
            private var dY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        dX = view.x - downRawX
                        dY = view.y - downRawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val newX = event.rawX + dX
                        val newY = event.rawY + dY
                        view.animate()
                            .x(newX)
                            .y(newY)
                            .setDuration(0)
                            .start()
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val upRawX = event.rawX
                        val upRawY = event.rawY
                        val dx = upRawX - downRawX
                        val dy = upRawY - downRawY

                        val distanceSquared = dx * dx + dy * dy

                        if (distanceSquared < 100) {
                            // ✅ 클릭으로 간주 (드래그 거리 작음)
                            view.performClick()
                        }
                        return true
                    }
                    else -> return false
                }
            }
        })

        rootView.addView(fab)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_open_map -> {
                startActivity(Intent(this, MapActivity::class.java).apply {
                    putExtra("roomCode", viewModel.roomCode)
                })
                true
            }
            R.id.menu_scrap_list -> {
                ScrapDialogHelper.showScrapListDialog(this, viewModel.roomCode)
                true
            }
            R.id.menu_participants -> {
                DialogHelper.showParticipantsDialog(this, viewModel.roomCode)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addParticipantToRoom(roomCode: String, userId: String) {
        val ref = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomCode)
            .child("participants")
        ref.child(userId).setValue(true)
    }
}