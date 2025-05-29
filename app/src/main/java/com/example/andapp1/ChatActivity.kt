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
import android.R.attr.bitmap
import android.R.attr.data
import android.R.id.message
import android.graphics.BitmapFactory
import com.bumptech.glide.Glide
import com.example.andapp1.DialogHelper.showParticipantsDialog
import com.google.firebase.storage.FirebaseStorage
import com.stfalcon.chatkit.commons.ImageLoader
import org.opencv.android.OpenCVLoader
import java.util.Date
import androidx.lifecycle.Observer

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
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY)
    }

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
                    val selectedImageUri = data?.data
                    if (selectedImageUri != null) {
                        uploadImageToFirebase(selectedImageUri)
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
                if (!snapshot.exists()) {
                    Toast.makeText(this@ChatActivity, "⚠ 이미 나간 채팅방입니다.", Toast.LENGTH_SHORT).show()
                    finish() // 🚫 채팅방 입장 금지
                } else {
                    Log.d("ChatActivity", "✅ 참가자 확인 완료")
                }
            }

            // 2) holders 만들기 (제네릭 없이)
            val holders = MessageHolders()
                .setOutcomingImageHolder(
                    OutcomingImageMessageViewHolder::class.java,
                    R.layout.item_outcoming_image_message
                )
                .setIncomingImageHolder(
                    IncomingImageMessageViewHolder::class.java,
                    R.layout.item_incoming_image_message
                )
            // 3) 어댑터 생성
            adapter = MessagesListAdapter<ChatMessage>(
                senderId,
                holders,
                ImageLoader { imageView, url, _ ->
                    Glide.with(imageView.context).load(url).into(imageView)
                }
            )

            binding.messagesList.setAdapter(adapter)

            // 메시지 클릭 (텍스트 메시지용)
            adapter.setOnMessageClickListener { message: ChatMessage ->
                val imageUrl = message.imageUrlValue
                Log.d("💥클릭된 메시지", "imageUrlValue = $imageUrl")

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
            val sorted = messages
                .filter { it.messageId.isNotBlank() }
                .distinctBy { it.messageId }
                .sortedBy { it.createdAt.time }
                .reversed() // ✅ 최신이 아래로 오도록 보장
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
                showOcrChoiceDialog()
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
        val author = Author(user.id, user.nickname ?: "알 수 없음", null)

        val message = ChatMessage(
            messageId = "",
            text = "",
            user = author,
            imageUrlValue = imageUrl,
            createdAt = Date()
        )

        Log.d("🔍 ChatDebug", "adapter senderId = $senderId") // 직접 senderId를 저장해두었다면
        Log.d("🔍 ChatDebug", "message sender id = ${message.getUser().getId()}")

        viewModel.sendMessage(message)
    }

    companion object {
        const val REQUEST_CAMERA = 1001
        const val REQUEST_GALLERY = 1002
    }
}
