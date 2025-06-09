package com.example.andapp1

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.ImageView
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
import android.content.Context
import android.R.attr.bitmap
import android.R.attr.data
import android.R.id.message
import android.content.ClipData
import android.graphics.BitmapFactory
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.andapp1.DialogHelper.showParticipantsDialog
import com.google.firebase.storage.FirebaseStorage
import com.stfalcon.chatkit.commons.ImageLoader
import org.opencv.android.OpenCVLoader
import java.util.Date
import androidx.lifecycle.Observer
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.Gravity
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var adapter: MessagesListAdapter<ChatMessage>
    private var lastMapUrl: String? = null
    private var cameraImageUri: Uri? = null
    private var photoSendUri: Uri? = null
    private var currentUser: UserEntity? = null
    private lateinit var photoUri: Uri
    private lateinit var senderId: String
    private val imageMessages = mutableListOf<String>()
    private var messagesObserver: Observer<List<ChatMessage>>? = null
    private var lastMessageId: String? = null
    private var shownMessageIds = mutableSetOf<String>()
    
    // OCR 결과 브로드캐스트 리시버
    private val ocrMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == "com.example.andapp1.SEND_CHAT_MESSAGE") {
                val message = intent.getStringExtra("message")
                val chatId = intent.getStringExtra("chatId")
                val source = intent.getStringExtra("source")
                
                Log.d("OCR_RECEIVER", "브로드캐스트 수신 - chatId: $chatId, source: $source")
                
                // 현재 채팅방과 일치하는 경우만 처리
                if (message != null && chatId == viewModel.roomCode && source == "ocr") {
                    Log.d("OCR_RECEIVER", "OCR 메시지 전송: $message")
                    sendChatMessage(message)
                    
                    Toast.makeText(this@ChatActivity, "💰 영수증 정산 결과가 전송되었습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openCamera() {
        // 📌 먼저 필요한 권한 목록
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // 권한 미허용 항목 추출
        val denied = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (denied.isNotEmpty()) {
            // 권한 요청
            ActivityCompat.requestPermissions(this, denied.toTypedArray(), 1011)
            return // ⚠️ 아직 권한 없으니까 여기서 중단
        }

        // 여기부터는 권한이 모두 허용된 상태
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "photo_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ChatPhotos")
            }
        }

        photoSendUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (photoSendUri == null) {
            Log.e("PHOTO", "❌ photoSendUri 생성 실패")
            return
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoSendUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        photoSendLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1011) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                openCamera() // 권한 허용되면 다시 openCamera 실행
            } else {
                Toast.makeText(this, "사진 촬영을 위해 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    private val receiptImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            // InputStream 방식이 더 안전함
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
            if (bitmap == null) {
                Toast.makeText(this, "이미지 파일을 읽을 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            // 📌 카메라에서 찍은 사진과 똑같은 로직 사용
            processOcrWithPeopleInput(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "이미지 분석에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
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

    private val photoSendLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && photoSendUri != null) {
            Log.d("PHOTO", "📷 촬영 성공 → 이미지 URI = $photoSendUri")
            uploadImageToFirebase(photoSendUri!!)
        } else {
            Log.e("PHOTO", "❌ 사진 촬영 실패 또는 URI 없음")
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

        handleSharedMapLink(intent)

        val roomCode = intent.getStringExtra("roomCode") ?: "default_room"
        val roomName = intent.getStringExtra("roomName") ?: "채팅방"
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = roomName

        viewModel = ViewModelProvider(this, ChatViewModelFactory(roomCode, applicationContext))[ChatViewModel::class.java]

        layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // 가장 아래부터 시작
            reverseLayout = false // 최신 메시지를 아래쪽에 표시
        }
        Log.d("정렬확인", "reverseLayout = ${layoutManager.reverseLayout}, stackFromEnd = ${layoutManager.stackFromEnd}")
        binding.messagesList.layoutManager = layoutManager
        
        // OCR 브로드캐스트 리시버 등록
        val filter = IntentFilter("com.example.andapp1.SEND_CHAT_MESSAGE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ocrMessageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(ocrMessageReceiver, filter)
        }
        
        initializeAdapterAndListeners()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("ChatActivity", "🌐 onNewIntent 호출됨")

        intent?.getStringExtra("mapUrl")?.let { url ->
            Log.d("ChatActivity", "🌐 받은 지도 URL: $url")
            lastMapUrl = url
            showMapRestoreButton()
        }
        intent?.getStringExtra("scrapText")?.let { sharedMapUrl ->
            // ✅ 중복 전송 방지를 위한 검사
            val alreadySent = intent.getBooleanExtra("alreadySent", false)
            if (!alreadySent) {
                Log.d("ChatActivity", "📩 공유 메시지 전송: $sharedMapUrl")
                viewModel.sendMapUrlMessage(sharedMapUrl)

                // ✅ 재진입 시 중복 방지 위해 플래그 추가
                intent.putExtra("alreadySent", true)
            } else {
                Log.d("ChatActivity", "⚠ 이미 전송된 메시지라 무시")
            }
        }
    }

    private fun showMapRestoreButton() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)

        // 중복 방지: 이미 있는 경우 추가 X
        val existing = rootView.findViewWithTag<FloatingActionButton>("map_restore_button")
        if (existing != null) {
            Log.d("ChatActivity", "🧭 이미 플로팅 버튼 존재 - 중복 생성 방지")
            return
        }

        val fab = FloatingActionButton(this).apply {
            tag = "map_restore_button" // ✅ 중복 방지용 태그

            setImageResource(R.drawable.ic_map)

            backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.white)
            imageTintList = ContextCompat.getColorStateList(context, android.R.color.black)

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                marginEnd = 32
                topMargin = 100
            }

            val dragKey = R.id.view_tag_drag_info

            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.setTag(dragKey, Triple(event.rawX, event.rawY, false))
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val (startX, startY, _) = view.getTag(dragKey) as Triple<Float, Float, Boolean>
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        val isDragged = dx * dx + dy * dy > 100
                        if (isDragged) {
                            view.x += dx
                            view.y += dy
                            view.setTag(dragKey, Triple(event.rawX, event.rawY, true))
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val (_, _, isDragged) = view.getTag(dragKey) as Triple<Float, Float, Boolean>
                        if (!isDragged) {
                            lastMapUrl?.let { url ->
                                val intent = Intent(this@ChatActivity, MapActivity::class.java).apply {
                                    putExtra("mapUrl", url)
                                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                }
                                startActivity(intent)
                            }

                        }
                        true
                    }
                    else -> false
                }
            }
        }
        rootView.addView(fab)
    }

    private fun handleSharedMapLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                Log.d("MapShare", "공유받은 지도 링크: $sharedText")
                sendChatMessage("📍 공유된 지도 링크: $sharedText")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CAMERA -> {
                    sendImageMessage(photoUri.toString())
                }
                REQUEST_GALLERY -> {
                    // 여러 장 선택했을 때
                    val clipData = data?.clipData
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            val imageUri = clipData.getItemAt(i).uri
                            uploadImageToFirebase(imageUri)
                        }
                    } else {
                        // 한 장만 선택한 경우
                        val selectedImageUri = data?.data
                        if (selectedImageUri != null) {
                            uploadImageToFirebase(selectedImageUri)
                        }
                    }
                }
            }
        }
    }

    private fun initializeAdapterAndListeners() {
        lifecycleScope.launch {
            // 1) DB에서 currentUser 불러오기
            val user = RoomDatabaseInstance
                .getInstance(applicationContext)
                .userDao()
                .getUser()
            currentUser = user

            if (user == null) {
                Toast.makeText(this@ChatActivity, "⚠ 사용자 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            senderId = user?.id ?: "unknown"

            if (user == null) {
                Toast.makeText(this@ChatActivity, "⚠ 사용자 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val participantsRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(viewModel.roomCode)
                .child("participants")
                .child(user.id)

            participantsRef.get().addOnSuccessListener { snapshot ->
                Log.d("ChatActivity_Participants", "참가자 스냅샷 수신. key: ${snapshot.key}, exists: ${snapshot.exists()}")
                if (!snapshot.exists()) {
                    Toast.makeText(this@ChatActivity, "⚠ 이미 나간 채팅방이거나 참여자 정보 없음.", Toast.LENGTH_SHORT).show()
                    Log.w("ChatActivity_Participants", "참가자가 아니므로 finish() 호출됨. User ID: ${user.id}, Room Code: ${viewModel.roomCode}")
                    finish() // 🚫 채팅방 입장 금지
                } else {
                    Log.d("ChatActivity_Participants", "✅ 참가자 확인 완료. User ID: ${user.id}, Room Code: ${viewModel.roomCode}")
                }
            }.addOnFailureListener { exception ->
                Log.e("ChatActivity_Participants", "참가자 정보 로드 실패: ${exception.message}", exception)
                Toast.makeText(this@ChatActivity, "⚠ 참가자 정보를 가져오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                // finish() // 실패 시에도 일단 종료 (기존 로직 유지 또는 다른 처리 고민 필요) -> 실패 시 바로 종료하지 않도록 일단 주석 처리
            }

            // ✅ 커스텀 ViewHolder 사용
            val holders = MessageHolders()
                .setIncomingTextHolder(
                    CustomIncomingTextViewHolder::class.java,
                    R.layout.item_incoming_text_message
                )
                .setIncomingImageHolder(
                    CustomIncomingImageViewHolder::class.java,
                    R.layout.item_incoming_image_message
                )
                // outcoming은 기본 사용 (프로필 이미지 없음)
                .setOutcomingTextHolder(
                    TextMessageViewHolder::class.java,
                    com.stfalcon.chatkit.R.layout.item_outcoming_text_message
                )
                .setOutcomingImageHolder(
                    OutcomingImageMessageViewHolder::class.java,
                    R.layout.item_outcoming_image_message
                )

            // 3) 어댑터 생성
            adapter = MessagesListAdapter<ChatMessage>(
                senderId,
                holders,
                ImageLoader { imageView, url, _ ->
                    // ✅ 디버깅 로그 추가
                    Log.d("ProfileDebug", "=== ImageLoader 호출됨 ===")
                    Log.d("ProfileDebug", "ImageView: $imageView")
                    Log.d("ProfileDebug", "URL: $url")

                    if (!url.isNullOrEmpty()) {
                        Log.d("ProfileDebug", "Glide로 이미지 로드 시작: $url")
                        Glide.with(imageView.context)
                            .load(url)
                            .error(R.drawable.ic_launcher_background) // 에러 시 기본 이미지 표시
                            .into(imageView)
                    } else {
                        Log.w("ProfileDebug", "URL이 비어있어서 기본 이미지 설정")
                        imageView.setImageResource(R.drawable.ic_launcher_background) // 기본 이미지
                    }
                }
            )

            binding.messagesList.setAdapter(adapter)

            // 메시지 클릭 (텍스트 메시지용)
            adapter.setOnMessageClickListener { message: ChatMessage ->
                val imageUrl = message.imageUrlValue
                Log.d("💥클릭된 메시지", "imageUrlValue = $imageUrl")

                // 📸 이미지 메시지만 처리 (텍스트 메시지는 TextMessageViewHolder에서 처리)
                if (!imageUrl.isNullOrEmpty()) {
                    val urls = imageMessages
                    val idx = urls.indexOf(imageUrl)

                    val photoListToSend = if (idx != -1) {
                        ArrayList(urls)
                    } else {
                        arrayListOf(imageUrl)
                    }

                    val position = if (idx != -1) idx else 0

                    Log.d("ChatActivity", "▶︎ 이미지 클릭 → photoList=$photoListToSend, index=$position")

                    val intent = Intent(this@ChatActivity, ImageViewerActivity::class.java)
                        .putStringArrayListExtra("photoList", photoListToSend)
                        .putExtra("startPosition", position)

                    startActivity(intent)
                }
            }

            // 텍스트 전송 버튼
            binding.customMessageInput.setInputListener { input ->
                viewModel.sendMessage(input.toString())

                // 🔽 메시지 전송 후 자동 스크롤 추가
                binding.messagesList.post {
                    layoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
                }
                true
            }

            // 사진 버튼
            binding.btnSendPhoto.setOnClickListener {
                val options = arrayOf("사진 촬영", "갤러리에서 선택")
                AlertDialog.Builder(this@ChatActivity)
                    .setTitle("사진 선택")
                    .setItems(options) { _, which ->
                        if (which == 0) openCamera() else openGallery()
                    }
                    .show()
            }
            // 메시지 옵저빙 시작
            observeMessages()
        }
    }

    private fun observeMessages() {
        viewModel.messages.observe(this) { messages ->
            Log.d("ProfileDebug", "=== observeMessages 호출됨 ===")
            Log.d("ProfileDebug", "받은 메시지 개수: ${messages.size}")

            // ✅ 각 메시지의 프로필 이미지 URL 확인
            messages.forEach { message ->
                Log.d("ProfileDebug", "메시지 ID: ${message.messageId}")
                Log.d("ProfileDebug", "사용자: ${message.getUser().getName()} (${message.getUser().getId()})")
                Log.d("ProfileDebug", "프로필 이미지: ${message.getUser().getAvatar()}")
                Log.d("ProfileDebug", "---")
            }

            val sorted = messages
                .filter { it.messageId.isNotBlank() }
                .distinctBy { it.messageId }
                .sortedBy { it.createdAt.time }
                .reversed() // ✅ 최신이 아래로 오도록 보장

            Log.d("ProfileDebug", "정렬된 메시지 개수: ${sorted.size}")

            adapter.setItems(sorted)

            imageMessages.clear()
            imageMessages.addAll(
                messages.filter { !it.imageUrlValue.isNullOrEmpty() }
                    .map { it.imageUrlValue!! }
            )
            ChatImageStore.imageMessages = imageMessages // 👈 전역 저장

            binding.messagesList.post {
                layoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
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
                // 새로운 OCR 액티비티로 이동 (채팅방 정보 전달)
                val intent = Intent(this, com.example.andapp1.ocr.OcrActivity::class.java).apply {
                    putExtra(com.example.andapp1.ocr.OcrActivity.EXTRA_CHAT_ID, viewModel.roomCode)
                    putExtra(com.example.andapp1.ocr.OcrActivity.EXTRA_AUTO_SEND, false)
                }
                startActivity(intent)
                true
            }
            R.id.menu_participants -> {
                // 참여자 목록 다이얼로그 띄우기
                DialogHelper.showParticipantsDialog(this, viewModel.roomCode)
                true
            }
            R.id.menu_open_map -> {
                // ✅ 지도 액티비티로 이동
                val intent = Intent(this, MapActivity::class.java)
                intent.putExtra("roomCode", viewModel.roomCode) // 채팅방 코드 넘기기 (필요 시)
                startActivity(intent)
                return true
            }
            R.id.menu_scrap_list -> {
                // 스크랩 목록 다이얼로그 띄우기 (또는 액티비티 이동)
                ScrapDialogHelper.showScrapListDialog(this, viewModel.roomCode)
                true
            }
            //  사진첩 메뉴
            R.id.menu_photo_gallery -> {
                val intent = Intent(this, PhotoGalleryActivity::class.java).apply {
                    putExtra("roomCode", viewModel.roomCode)
                    putExtra("roomName", supportActionBar?.title?.toString() ?: "채팅방")
                }
                startActivity(intent)
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
            showParticipantsInputDialog(bitmap, defaultPeople)
        }.addOnFailureListener { exception ->
            Log.e("FIREBASE_OCR", "참여자 수 로드 실패. 기본값 사용.", exception)
            showParticipantsInputDialog(bitmap, 4, "참여자 정보를 가져오지 못했습니다. 기본값(4명)을 사용합니다.")
        }
    }

    private fun showParticipantsInputDialog(bitmap: Bitmap, defaultPeopleCount: Int, messageHint: String? = null) {
        val editText = EditText(this@ChatActivity).apply {
            setText(defaultPeopleCount.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val dialogTitle = "정산 인원 수를 입력하세요"
        val dialogMessage = messageHint ?: "정산 인원을 입력해주세요. (현재 방 인원 자동 반영: ${defaultPeopleCount}명)"


        AlertDialog.Builder(this@ChatActivity)
            .setTitle(dialogTitle)
            .setMessage(dialogMessage)
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
                val enteredPeople = editText.text.toString().toIntOrNull()
                val finalValidPeople = enteredPeople?.takeIf { it > 0 } ?: defaultPeopleCount

                if (enteredPeople != null && enteredPeople <= 0) {
                    Toast.makeText(this@ChatActivity, "정산 인원은 1명 이상이어야 합니다. 기본값(${defaultPeopleCount}명)으로 설정됩니다.", Toast.LENGTH_LONG).show()
                }

                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        val ocrText = ReceiptOcrProcessor.processReceipt(this@ChatActivity, bitmap)
                        val totalAmount = ReceiptOcrProcessor.extractTotalAmount(ocrText)
                        
                        if (totalAmount != null && totalAmount > 0) {
                            val perPerson = totalAmount / finalValidPeople
                            val message = "📝 영수증 정산\n" +
                                        "→ 총액: ${totalAmount}원\n" +
                                        "→ 인원: ${finalValidPeople}명\n" +
                                        "→ 1인당: ${perPerson}원"
                            sendChatMessage(message) 
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ChatActivity, "영수증 총액을 인식할 수 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("OCR_PROCESS", "ChatActivity OCR 처리 중 예외", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ChatActivity, "영수증 인식에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun uploadImageToFirebase(uri: Uri) {
        val fileName = "images/${System.currentTimeMillis()}.jpg"
        val storageRef = FirebaseStorage.getInstance().reference.child(fileName)

        Log.d("PHOTO", "업로드 시도 URI: $uri")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                Log.d("PHOTO", "✅ 업로드 성공")
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    Log.d("PHOTO", "✅ 다운로드 URL: $downloadUrl")
                    sendImageMessage(downloadUrl.toString()) // 이때 imageUrlValue로 넣어야 함
                }
            }
            .addOnFailureListener { e ->
                Log.e("PHOTO", "❌ 업로드 실패: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this, "사진 업로드 실패", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendChatMessage(message: String) {
        viewModel.sendMessage(message)
    }

    private fun sendImageMessage(imageUrl: String) {
        Log.d("ChatActivity", "Sending image message: $imageUrl")

        val user = currentUser ?: return
        val author = Author(
            user.id,
            user.nickname ?: "알 수 없음",
            user.profileImageUrl // ✅ 프로필 이미지 URL 설정
        )

        val message = ChatMessage(
            messageId = "",
            text = "",
            user = author,
            imageUrlValue = imageUrl,
            createdAt = Date()
        )

        Log.d("🔍 ChatDebug", "adapter senderId = $senderId")
        Log.d("🔍 ChatDebug", "message sender id = ${message.getUser().getId()}")

        viewModel.sendMessage(message)
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // OCR 브로드캐스트 리시버 해제
        try {
            unregisterReceiver(ocrMessageReceiver)
            Log.d("ChatActivity_Lifecycle", "OCR 브로드캐스트 리시버 해제 완료")
        } catch (e: Exception) {
            Log.w("ChatActivity_Lifecycle", "OCR 브로드캐스트 리시버 해제 실패: ${e.message}")
        }
        
        Log.d("ChatActivity_Lifecycle", "onDestroy 호출됨", Exception("onDestroy Call Stack"))
    }

    companion object {
        const val REQUEST_CAMERA = 1001
        const val REQUEST_GALLERY = 1002
    }
}
class CustomIncomingTextViewHolder(itemView: View) : MessageHolders.IncomingTextMessageViewHolder<ChatMessage>(itemView) {

    override fun onBind(message: ChatMessage) {
        super.onBind(message)

        val messageTextView = itemView.findViewById<TextView>(R.id.messageText)
        val rawText = message.text
        val spannable = SpannableString(rawText)

        Linkify.addLinks(spannable, Linkify.WEB_URLS)
        processMapUrls(spannable, rawText)

        messageTextView.text = spannable
        messageTextView.movementMethod = LinkMovementMethod.getInstance()
        messageTextView.linksClickable = true

        messageTextView.setOnTouchListener { v, event ->
            val textView = v as TextView
            val s = textView.text as? Spannable ?: return@setOnTouchListener false

            val action = event.action
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt() - textView.totalPaddingLeft + textView.scrollX
                val y = event.y.toInt() - textView.totalPaddingTop + textView.scrollY

                val layout = textView.layout ?: return@setOnTouchListener false
                val line = layout.getLineForVertical(y)
                val off = layout.getOffsetForHorizontal(line, x.toFloat())

                val links = s.getSpans(off, off, ClickableSpan::class.java)
                if (links.isNotEmpty()) {
                    if (action == MotionEvent.ACTION_UP) {
                        links[0].onClick(textView)
                    }
                    return@setOnTouchListener true
                }
            }
            return@setOnTouchListener false
        }
        // 프로필 이미지 설정
        val avatarView = itemView.findViewById<ImageView>(R.id.messageUserAvatar)
        val avatarUrl = message.getUser().getAvatar()

        Log.d("CustomViewHolder", "텍스트 메시지 프로필 이미지 로드: $avatarUrl")

        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(itemView.context)
                .load(avatarUrl)
                .circleCrop() // 원형으로 표시
                .error(R.drawable.ic_launcher_background) // 에러 시 기본 이미지
                .into(avatarView)
        } else {
            avatarView.setImageResource(R.drawable.ic_launcher_background) // 기본 이미지
        }

        // ✅ 사용자 이름 설정
        val userNameView = itemView.findViewById<TextView>(R.id.messageUserName)
        val userName = message.getUser().getName()
        userNameView.text = if (userName.isNotEmpty()) userName else "알 수 없음"

        Log.d("CustomViewHolder", "사용자 이름 설정: $userName")

        // ✅ 프로필 이미지 클릭 이벤트 추가 (사용자 상세 보기)
        avatarView.setOnClickListener {
            showUserDetailDialog(itemView.context, message.getUser())
        }

        // ✅ 사용자 이름 클릭 이벤트 추가
        userNameView.setOnClickListener {
            showUserDetailDialog(itemView.context, message.getUser())
        }
    }

    private fun processMapUrls(spannable: Spannable, text: String) {
        // 지도 URL 패턴
        val mapPatterns = arrayOf(
            "https://m\\.map\\.naver\\.com[^\\s]*",
            "https://map\\.naver\\.com[^\\s]*",
            "https://map\\.kakao\\.com[^\\s]*",
            "https://maps\\.google\\.com[^\\s]*",
            "https://www\\.google\\.com/maps[^\\s]*"
        )

        for (patternStr in mapPatterns) {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(text)

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val mapUrl = text.substring(start, end)

                Log.d("TextMessageViewHolder", "🗺️ 지도 URL 발견: $mapUrl")

                // 기존 URL 링크 제거하고 커스텀 링크로 교체
                val existingSpans = spannable.getSpans(start, end, ClickableSpan::class.java)
                for (span in existingSpans) {
                    spannable.removeSpan(span)
                }

                // 커스텀 지도 링크 적용
                val mapClickSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        Log.d("TextMessageViewHolder", "🗺️ 지도 링크 클릭: $mapUrl")
                        try {
                            val intent = Intent(widget.context, MapActivity::class.java)
                            intent.putExtra("mapUrl", mapUrl)
                            widget.context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("TextMessageViewHolder", "지도 액티비티 실행 실패", e)
                        }
                    }
                }

                spannable.setSpan(
                    mapClickSpan,
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun showUserDetailDialog(context: Context, user: com.stfalcon.chatkit.commons.models.IUser) {
        // ✅ 커스텀 레이아웃 생성
        val dialogView = LayoutInflater.from(context).inflate(android.R.layout.select_dialog_item, null)

        // LinearLayout을 수동으로 생성하여 이미지와 텍스트를 표시
        val linearLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        // 프로필 이미지 추가
        val profileImageView = ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(300, 300).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 20
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            // 둥근 모서리 배경 설정
            background = ContextCompat.getDrawable(context, android.R.drawable.dialog_frame)
        }

        // 사용자 정보 텍스트 (ID 제거)
        val userInfoText = TextView(context).apply {
            text = "${user.getName()}"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // 레이아웃에 뷰들 추가
        linearLayout.addView(profileImageView)
        linearLayout.addView(userInfoText)

        // 이미지 로드
        if (!user.getAvatar().isNullOrEmpty()) {
            Log.d("UserDialog", "프로필 이미지 로드: ${user.getAvatar()}")
            Glide.with(context)
                .load(user.getAvatar())
                .centerCrop()
                .error(R.drawable.ic_launcher_background)
                .into(profileImageView)
        } else {
            profileImageView.setImageResource(R.drawable.ic_launcher_background)
        }

        // 다이얼로그 생성
        val dialog = AlertDialog.Builder(context)
            .setTitle("👤 사용자 정보")
            .setView(linearLayout)
            .setPositiveButton("확인", null)
            .setNeutralButton("프로필 크게 보기") { _, _ ->
                // 프로필 이미지를 전체화면으로 보기
                showFullScreenImage(context, user.getAvatar())
            }
            .create()

        dialog.show()
    }

    private fun showFullScreenImage(context: Context, imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) {
            Toast.makeText(context, "프로필 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 전체화면 이미지 뷰어를 위한 간단한 액티비티 호출
        // 또는 ImageViewerActivity 재활용
        val intent = Intent(context, ImageViewerActivity::class.java).apply {
            putStringArrayListExtra("photoList", arrayListOf(imageUrl))
            putExtra("startPosition", 0)
        }
        context.startActivity(intent)
    }
}

class CustomIncomingImageViewHolder(itemView: View) : MessageHolders.IncomingImageMessageViewHolder<ChatMessage>(itemView) {

    override fun onBind(message: ChatMessage) {
        super.onBind(message)

        // 프로필 이미지 설정
        val avatarView = itemView.findViewById<ImageView>(R.id.messageUserAvatar)
        val avatarUrl = message.getUser().getAvatar()

        Log.d("CustomViewHolder", "이미지 메시지 프로필 이미지 로드: $avatarUrl")

        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(itemView.context)
                .load(avatarUrl)
                .circleCrop() // 원형으로 표시
                .error(R.drawable.ic_launcher_background) // 에러 시 기본 이미지
                .into(avatarView)
        } else {
            avatarView.setImageResource(R.drawable.ic_launcher_background) // 기본 이미지
        }

        // ✅ 사용자 이름 설정
        val userNameView = itemView.findViewById<TextView>(R.id.messageUserName)
        val userName = message.getUser().getName()
        userNameView.text = if (userName.isNotEmpty()) userName else "알 수 없음"

        Log.d("CustomViewHolder", "사용자 이름 설정: $userName")

        // ✅ 프로필 이미지 클릭 이벤트 추가 (사용자 상세 보기)
        avatarView.setOnClickListener {
            showUserDetailDialog(itemView.context, message.getUser())
        }

        // ✅ 사용자 이름 클릭 이벤트 추가
        userNameView.setOnClickListener {
            showUserDetailDialog(itemView.context, message.getUser())
        }

        // ✅ 기존 이미지 클릭 기능 유지 (이미지 확대 보기)
        val imageView = itemView.findViewById<ImageView>(R.id.image)
        imageView.setOnClickListener {
            val url = message.imageUrlValue ?: return@setOnClickListener
            val allImages = ChatImageStore.imageMessages
            val idx = allImages.indexOf(url)
            val photoList = if (idx != -1) ArrayList(allImages) else arrayListOf(url)
            val position = if (idx != -1) idx else 0

            val intent = Intent(itemView.context, ImageViewerActivity::class.java).apply {
                putStringArrayListExtra("photoList", photoList)
                putExtra("startPosition", position)
            }

            itemView.context.startActivity(intent)
        }
    }

    private fun showUserDetailDialog(context: Context, user: com.stfalcon.chatkit.commons.models.IUser) {
        // ✅ 커스텀 레이아웃 생성
        val linearLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        // 프로필 이미지 추가
        val profileImageView = ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(300, 300).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 20
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            // 둥근 모서리 배경 설정
            background = ContextCompat.getDrawable(context, android.R.drawable.dialog_frame)
        }

        // 사용자 정보 텍스트 (ID 제거)
        val userInfoText = TextView(context).apply {
            text = "${user.getName()}"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // 레이아웃에 뷰들 추가
        linearLayout.addView(profileImageView)
        linearLayout.addView(userInfoText)

        // 이미지 로드
        if (!user.getAvatar().isNullOrEmpty()) {
            Log.d("UserDialog", "프로필 이미지 로드: ${user.getAvatar()}")
            Glide.with(context)
                .load(user.getAvatar())
                .centerCrop()
                .error(R.drawable.ic_launcher_background)
                .into(profileImageView)
        } else {
            profileImageView.setImageResource(R.drawable.ic_launcher_background)
        }

        // 다이얼로그 생성
        val dialog = AlertDialog.Builder(context)
            .setTitle("👤 사용자 정보")
            .setView(linearLayout)
            .setPositiveButton("확인", null)
            .setNeutralButton("프로필 크게 보기") { _, _ ->
                // 프로필 이미지를 전체화면으로 보기
                showFullScreenImage(context, user.getAvatar())
            }
            .create()

        dialog.show()
    }

    private fun showFullScreenImage(context: Context, imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) {
            Toast.makeText(context, "프로필 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 전체화면 이미지 뷰어를 위한 간단한 액티비티 호출
        // 또는 ImageViewerActivity 재활용
        val intent = Intent(context, ImageViewerActivity::class.java).apply {
            putStringArrayListExtra("photoList", arrayListOf(imageUrl))
            putExtra("startPosition", 0)
        }
        context.startActivity(intent)
    }
}