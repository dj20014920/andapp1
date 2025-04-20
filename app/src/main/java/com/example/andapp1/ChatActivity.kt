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
    private var lastMapUrl: String? = null

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
    }

    private fun showMapRestoreButton() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        if (rootView.findViewWithTag<FloatingActionButton>("map_restore_button") != null) return

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