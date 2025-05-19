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
import org.opencv.android.OpenCVLoader

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
            // 총액만 추출하여 메시지 생성
            val total = ReceiptOcrProcessor.extractTotalAmount(text)
            val message = "→ 총합: ${'$'}total원 / 인당: ${'$'}{total / 4}원"
            sendChatMessage(message)
        }
    }
    //카메라 촬영 후 처리
    private val cameraIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && cameraImageUri != null) {
            try {
                // delay를 줘서 이미지 저장이 완료된 후 읽도록
                Handler(Looper.getMainLooper()).postDelayed({
                    val inputStream = contentResolver.openInputStream(cameraImageUri!!)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        processOcrWithPeopleInput(bitmap)
                    } else {
                        Log.e("OCR_CAMERA", "❌ 비트맵 디코딩 실패: bitmap == null")
                    }
                }, 500) // 0.5초 후 시도 (필요 시 늘릴 것)
            } catch (e: Exception) {
                Log.e("OCR_CAMERA", "❌ 이미지 디코딩 중 오류 발생: ${e.message}")
            }
        } else {
            Log.e("OCR_CAMERA", "❌ 사진 촬영 실패 또는 취소됨")
        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV initialization failed")
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully")
        }
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val roomCode = intent.getStringExtra("roomCode") ?: "default_room"
        val roomName = intent.getStringExtra("roomName") ?: "채팅방"
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = roomName

        viewModel = ViewModelProvider(this, ChatViewModelFactory(roomCode, applicationContext))[ChatViewModel::class.java]

        layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messagesList.layoutManager = layoutManager
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
                        Log.d("OCR_CAMERA", "📸 사진 촬영 선택됨")

                        // ✅ 권한 요청 추가 (Android 13 이상 대응)
                        val permissions: MutableList<String> = mutableListOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.READ_MEDIA_IMAGES
                        )


                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                        }
                        ActivityCompat.requestPermissions(
                            this,
                            permissions.toTypedArray(),
                            1010 // 예시: 요청 코드 상수 (원하는 번호 사용 가능)
                        )

                        // 아래는 기존 코드
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

                    1 -> {
                        Log.d("OCR_CAMERA", "🖼️ 사진 선택 선택됨")
                        receiptImageLauncher.launch("image/*")
                    }
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
                // processOcrWithPeopleInput() 안에서…
                .setPositiveButton("확인") { _, _ ->
                    val people = editText.text.toString().toIntOrNull() ?: defaultPeople
                    try {
                        val text = ReceiptOcrProcessor.processReceipt(this, bitmap)
                        Log.d("OCR", "ChatActivity → OCR 결과 텍스트 = $text")
                        val total = ReceiptOcrProcessor.extractTotalAmount(text)
                        Log.d("OCR", "ChatActivity → total = $total, people = $people")
                        val message = "→ 총합: ${total ?: 0}원 / 인당: ${(total ?: 0) / people}원"
                        sendChatMessage(message)

                    } catch (e: Exception) {
                        Log.e("OCR_PROCESS", "ChatActivity OCR 처리 중 예외", e)
                        Toast.makeText(this, "영수증 인식에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                .setNegativeButton("취소", null)
                .show()
        }
    }


    private fun sendChatMessage(message: String) {
        viewModel.sendMessage(message)
    }

}
