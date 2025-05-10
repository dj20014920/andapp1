package com.example.andapp1

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.andapp1.databinding.ActivityChatBinding
import com.example.andapp1.ocr.ReceiptOcrProcessor
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.FirebaseDatabase
import com.stfalcon.chatkit.messages.*
import kotlinx.coroutines.launch
import java.io.File
import android.Manifest
import android.graphics.BitmapFactory
import android.view.View
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var adapter: MessagesListAdapter<ChatMessage>
    private var lastMapUrl: String? = null
    private var cameraImageUri: Uri? = null

    private val receiptImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            ReceiptOcrProcessor.copyTrainedDataIfNeeded(this)
            val text = ReceiptOcrProcessor.processReceipt(this, bitmap)
            val items = ReceiptOcrProcessor.extractTotalAmount(text)
            val message = ReceiptOcrProcessor.formatTotalOnlyMessage(items, people = 4)
            sendChatMessage(message)
        }
    }

    private val cameraIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && cameraImageUri != null) {
            try {
                Handler(Looper.getMainLooper()).postDelayed({
                    // 📸 풀사이즈 비트맵 디코딩
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(contentResolver, cameraImageUri!!)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        MediaStore.Images.Media.getBitmap(contentResolver, cameraImageUri)
                    }
                    if (bitmap != null) {
                        processOcrWithPeopleInput(bitmap)
                    } else {
                        Toast.makeText(this, "사진 디코딩 실패", Toast.LENGTH_SHORT).show()
                    }
                }, 500)
            } catch (e: Exception) {
                Log.e("OCR_CAMERA", "❌ 이미지 디코딩 중 오류: ${e.message}")
            }
        } else {
            Toast.makeText(this, "사진 촬영이 취소되었거나 실패했습니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val roomName = intent.getStringExtra("roomName") ?: "채팅방"
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = roomName

        layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messagesList.layoutManager = layoutManager

        // ← 이 한 줄이 핵심: RecyclerView를 소프트웨어 레이어로 그립니다
        binding.messagesList.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        viewModel = ViewModelProvider(
            this,
            ChatViewModelFactory(intent.getStringExtra("roomCode") ?: "default_room", applicationContext)
        )[ChatViewModel::class.java]

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

            binding.customMessageInput.setInputListener { input ->
                viewModel.sendMessage(input.toString())
                Handler(Looper.getMainLooper()).postDelayed({ scrollToBottomSmooth() }, 300)
                true
            }

            observeMessages()
        }
    }

    private fun observeMessages() {
        viewModel.messages.observe(this) { messages ->
            adapter.clear()
            adapter.addToEnd(messages.sortedBy { it.createdAt.time }, true)
            binding.messagesList.post {
                binding.messagesList.scrollToPosition(adapter.itemCount)
            }
        }
    }

    private fun scrollToBottomSmooth() {
        binding.messagesList.postDelayed({
            if (adapter.itemCount > 0) binding.messagesList.scrollToPosition(adapter.itemCount)
        }, 300)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_receipt_ocr -> {
                showOcrChoiceDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showOcrChoiceDialog() {
        val options = arrayOf("📸 사진 촬영", "🖼️ 갤러리에서 선택")
        AlertDialog.Builder(this)
            .setTitle("영수증 분석 방법 선택")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // ⚠ 이 부분은 원래 코드 그대로
                        val permissions = mutableListOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.READ_MEDIA_IMAGES
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                        }
                        ActivityCompat.requestPermissions(
                            this,
                            permissions.toTypedArray(),
                            1010
                        )

                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "receipt_${System.currentTimeMillis()}.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Receipts")
                            }
                        }
                        cameraImageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        if (cameraImageUri == null) {
                            Log.e("OCR_CAMERA", "❌ URI 생성 실패")
                            return@setItems
                        }
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        cameraIntentLauncher.launch(intent)
                    }
                    1 -> receiptImageLauncher.launch("image/*")
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }





    private fun processOcrWithPeopleInput(bitmap: Bitmap) {
        val participantsRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(viewModel.roomCode)
            .child("participants")

        participantsRef.get().addOnSuccessListener { snapshot ->
            val defaultPeople = snapshot.childrenCount.toInt().coerceAtLeast(1)
            val editText = EditText(this).apply {
                setText(defaultPeople.toString())
                inputType = InputType.TYPE_CLASS_NUMBER
            }

            AlertDialog.Builder(this)
                .setTitle("정산 인원 수를 입력하세요")
                .setMessage("기본값은 ${defaultPeople}명입니다.")
                .setView(editText)
                .setPositiveButton("확인") { _, _ ->
                    val people = editText.text.toString().toIntOrNull() ?: defaultPeople
                    try {
                        /*
                        // 👇 OCR 처리
                        ReceiptOcrProcessor.copyTrainedDataIfNeeded(this)
                        val text = ReceiptOcrProcessor.processReceipt(this, bitmap)
                        val total = ReceiptOcrProcessor.extractTotalAmount(text)
                        val message = ReceiptOcrProcessor.formatTotalOnlyMessage(total, people)

                        // 👇 메시지 전송
                        sendChatMessage(message)

                         */
                        sendChatMessage("테스트 메시지: OCR 없이 바로 보냄")
                    } catch (e: Exception) {
                        Log.e("OCR_PROCESS", "❌ OCR 처리 중 오류: ${e.message}", e)
                        Toast.makeText(this, "OCR 처리 실패", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }.addOnFailureListener {
            Log.e("OCR_PROCESS", "❌ 참가자 수 로딩 실패: ${it.message}")
            Toast.makeText(this, "인원 수 불러오기 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendChatMessage(message: String) {
        if (viewModel.roomCode.isNullOrBlank()) {
            Log.e("OCR_PROCESS", "❌ roomCode 없음. 메시지 전송 불가.")
            return
        }
        viewModel.sendMessage(message)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }


}
