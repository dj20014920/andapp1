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
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.stfalcon.chatkit.commons.ImageLoader
import org.opencv.android.OpenCVLoader
import java.util.Date


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

        val roomCode = intent.getStringExtra("roomCode") ?: "default_room"
        val roomName = intent.getStringExtra("roomName") ?: "채팅방"
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = roomName

        viewModel = ViewModelProvider(this, ChatViewModelFactory(roomCode, applicationContext))[ChatViewModel::class.java]

        layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messagesList.layoutManager = layoutManager
        initializeAdapterAndListeners()
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
            val user = RoomDatabaseInstance.getInstance(applicationContext).userDao().getUser()
            currentUser = user // 저장!
            senderId = currentUser?.id ?: "unknown"
            Log.d("🔍 ChatDebug", "MessagesListAdapter senderId = $senderId")
            val holders = MessageHolders()
                .setOutcomingImageHolder(
                    OutcomingImageMessageViewHolder::class.java,
                    R.layout.item_outcoming_image_message
                )
                .setIncomingImageHolder(
                    IncomingImageMessageViewHolder::class.java,
                    R.layout.item_incoming_image_message
                )
            val imageLoader = ImageLoader { imageView, url, _ ->
                Glide.with(imageView.context).load(url).into(imageView)
            }
            adapter = MessagesListAdapter<ChatMessage>(senderId, holders, imageLoader)
            binding.messagesList.setAdapter(adapter)

            adapter.setOnMessageClickListener { message ->
                val imageUrl = message.imageUrlValue
                if (!imageUrl.isNullOrEmpty()) {
                    val photoList = viewModel.messages.value
                        ?.filter { !it.imageUrlValue.isNullOrEmpty() }
                        ?.map { it.imageUrlValue!! } ?: emptyList()

                    val clickedIndex = photoList.indexOf(imageUrl)

                    val intent = Intent(this@ChatActivity, PhotoViewerActivity::class.java).apply {
                        putStringArrayListExtra("photoList", ArrayList(photoList))
                        putExtra("startPosition", clickedIndex)
                    }
                    startActivity(intent)
                }
            }

            binding.customMessageInput.setInputListener { input ->
                viewModel.sendMessage(input.toString())
                Handler(Looper.getMainLooper()).postDelayed({ scrollToBottomSmooth() }, 300)
                true
            }

            binding.btnSendPhoto.setOnClickListener {
                val options = arrayOf("사진 촬영", "갤러리에서 선택")
                AlertDialog.Builder(this@ChatActivity)
                    .setTitle("사진 선택")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> openCamera()
                            1 -> openGallery()
                        }
                    }
                    .show()
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
            id = System.currentTimeMillis().toString(),
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
