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
import com.google.firebase.storage.FirebaseStorage
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


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
            Log.d("OCR_RECEIVER", "브로드캐스트 수신됨 - action: ${intent?.action}")
            
            if (intent?.action == "com.example.andapp1.SEND_CHAT_MESSAGE") {
                val message = intent.getStringExtra("message")
                val chatId = intent.getStringExtra("chatId")
                val source = intent.getStringExtra("source")
                val currentRoomCode = viewModel.roomCode
                
                Log.d("OCR_RECEIVER", "브로드캐스트 상세 정보:")
                Log.d("OCR_RECEIVER", "  - 수신 chatId: '$chatId'")
                Log.d("OCR_RECEIVER", "  - 현재 roomCode: '$currentRoomCode'")
                Log.d("OCR_RECEIVER", "  - source: '$source'")
                Log.d("OCR_RECEIVER", "  - message 길이: ${message?.length ?: 0}")
                
                // 채팅방 매칭 조건 (더 유연하게 처리)
                val isTargetChatRoom = when {
                    // 1. 정확히 일치하는 경우
                    chatId == currentRoomCode -> {
                        Log.d("OCR_RECEIVER", "✅ chatId와 roomCode 정확히 일치")
                        true
                    }
                    // 2. chatId가 null인 경우 (현재 활성화된 채팅방으로 간주)
                    chatId.isNullOrBlank() -> {
                        Log.d("OCR_RECEIVER", "✅ chatId가 null/빈값 - 현재 채팅방으로 처리")
                        true
                    }
                    // 3. 기타 경우
                    else -> {
                        Log.d("OCR_RECEIVER", "❌ 채팅방 불일치")
                        false
                    }
                }
                
                Log.d("OCR_RECEIVER", "  - chatId 비교 결과: $isTargetChatRoom")
                
                // 조건 확인 후 메시지 전송
                if (message != null && isTargetChatRoom && source == "ocr") {
                    Log.d("OCR_RECEIVER", "✅ 모든 조건 만족 - OCR 메시지 전송 시작")
                    sendChatMessage(message)
                    
                    Toast.makeText(this@ChatActivity, "💰 영수증 정산 결과가 전송되었습니다", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w("OCR_RECEIVER", "❌ 조건 불만족 - 메시지 전송 안함")
                    Log.w("OCR_RECEIVER", "  - message null? ${message == null}")
                    Log.w("OCR_RECEIVER", "  - target chat? $isTargetChatRoom")
                    Log.w("OCR_RECEIVER", "  - source ocr? ${source == "ocr"}")
                }
            } else {
                Log.d("OCR_RECEIVER", "다른 액션의 브로드캐스트: ${intent?.action}")
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

        when (requestCode) {
            1011 -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    openCamera() // 권한 허용되면 다시 openCamera 실행
                } else {
                    Toast.makeText(this, "사진 촬영을 위해 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            }
            1010 -> {
                Log.d("OCR_PERMISSIONS", "권한 요청 결과: ${grantResults.contentToString()}")
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d("OCR_PERMISSIONS", "모든 권한 허용됨 - 카메라 실행")
                    executeCameraCapture() // 권한 허용되면 바로 카메라 실행
                } else {
                    Log.d("OCR_PERMISSIONS", "권한 거부됨")
                    val deniedPermissions = permissions.filterIndexed { index, _ -> 
                        grantResults[index] != PackageManager.PERMISSION_GRANTED 
                    }
                    Log.d("OCR_PERMISSIONS", "거부된 권한들: ${deniedPermissions.joinToString(", ")}")
                    
                    // 설정으로 이동할 수 있는 다이얼로그 표시
                    showPermissionSettingsDialog()
                }
            }
        }
    }
    
    private fun openOcrCamera() {
        Log.d("OCR_PERMISSIONS", "=== 권한 체크 시작 ===")
        
        // 📌 먼저 카메라 권한 체크
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        Log.d("OCR_PERMISSIONS", "카메라 권한: ${if (cameraPermission == PackageManager.PERMISSION_GRANTED) "허용됨" else "거부됨"}")
        
        // 📌 Android 버전별 이미지 권한 체크 (더 강력한 검사)
        val hasImagePermission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ : READ_MEDIA_IMAGES 사용
                val mediaImagesPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                Log.d("OCR_PERMISSIONS", "READ_MEDIA_IMAGES 권한: ${if (mediaImagesPermission == PackageManager.PERMISSION_GRANTED) "허용됨" else "거부됨"}")
                
                // 추가: MediaStore에 실제 접근 가능한지 테스트
                val canAccessMediaStore = try {
                    val cursor = contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media._ID),
                        null,
                        null,
                        "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1"
                    )
                    val canAccess = cursor != null
                    cursor?.close()
                    Log.d("OCR_PERMISSIONS", "MediaStore 접근 테스트: ${if (canAccess) "성공" else "실패"}")
                    canAccess
                } catch (e: SecurityException) {
                    Log.d("OCR_PERMISSIONS", "MediaStore 접근 테스트: SecurityException - ${e.message}")
                    false
                } catch (e: Exception) {
                    Log.d("OCR_PERMISSIONS", "MediaStore 접근 테스트: Exception - ${e.message}")
                    false
                }
                
                mediaImagesPermission == PackageManager.PERMISSION_GRANTED && canAccessMediaStore
            }
            else -> {
                // Android 12 이하 : READ_EXTERNAL_STORAGE 사용
                val storagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                Log.d("OCR_PERMISSIONS", "READ_EXTERNAL_STORAGE 권한: ${if (storagePermission == PackageManager.PERMISSION_GRANTED) "허용됨" else "거부됨"}")
                storagePermission == PackageManager.PERMISSION_GRANTED
            }
        }
        
        // 📌 권한 체크 결과
        if (cameraPermission != PackageManager.PERMISSION_GRANTED || !hasImagePermission) {
            Log.d("OCR_PERMISSIONS", "권한 부족 - 요청 필요")
            Log.d("OCR_PERMISSIONS", "카메라: ${cameraPermission == PackageManager.PERMISSION_GRANTED}, 이미지: $hasImagePermission")
            
            // 🔧 임시 해결책: 사용자에게 강제 실행 옵션 제공
            AlertDialog.Builder(this)
                .setTitle("⚠️ 권한 문제 감지")
                .setMessage("권한이 허용되어 있음에도 불구하고 접근에 문제가 있습니다.\n\n" +
                        "• 설정에서 권한을 다시 확인하거나\n" +
                        "• 강제로 카메라를 실행해보세요.")
                .setPositiveButton("강제 실행") { _, _ ->
                    Log.d("OCR_PERMISSIONS", "사용자가 강제 실행 선택")
                    executeCameraCapture()
                }
                .setNegativeButton("권한 설정") { _, _ ->
                    requestOcrPermissions()
                }
                .setNeutralButton("앱 설정 열기") { _, _ ->
                    showPermissionSettingsDialog()
                }
                .show()
            return
        }
        
        Log.d("OCR_PERMISSIONS", "모든 권한 허용됨 - 카메라 실행")
        executeCameraCapture()
    }
    
    private fun requestOcrPermissions() {
        val permissions = mutableListOf<String>()
        
        // 카메라 권한
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        
        // Android 버전별 이미지 권한
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
            else -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
        
        Log.d("OCR_PERMISSIONS", "요청할 권한들: ${permissions.joinToString(", ")}")
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1010)
        } else {
            // 모든 권한이 허용된 상태
            executeCameraCapture()
        }
    }
    
    private fun executeCameraCapture() {
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
            return
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        cameraIntentLauncher.launch(intent)
    }
    
    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔒 권한 필요")
            .setMessage("영수증 인식을 위해 다음 권한이 필요합니다:\n\n" +
                    "• 📸 카메라: 영수증 촬영\n" +
                    "• 🖼️ 사진/미디어: 이미지 저장 및 읽기\n\n" +
                    "설정으로 이동하여 권한을 허용해주세요.")
            .setPositiveButton("설정 열기") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
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

        // 상단바 겹침 문제 해결을 위한 Window 설정
        window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimaryDark)
        
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
        
        // 동적 시스템 바 여백 조정 (각 기기마다 다른 상단바 높이 대응)
        setupDynamicSystemBarInsets()

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
    
    /**
     * 동적 시스템 바 여백 조정
     * 각 기기마다 다른 상단바 높이를 자동으로 감지해서 적절한 여백 적용
     */
    private fun setupDynamicSystemBarInsets() {
        Log.d("SystemBarInsets", "동적 시스템 바 여백 조정 시작")
        
        // 상태바를 투명하게 하고 콘텐츠가 상태바 아래로 확장되도록 설정 (최신 방식)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            // 시스템 바 인셋 정보 가져오기
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val statusBarHeight = systemBars.top
            val navigationBarHeight = systemBars.bottom
            
            Log.d("SystemBarInsets", "감지된 상단바 높이: ${statusBarHeight}px")
            Log.d("SystemBarInsets", "감지된 네비게이션바 높이: ${navigationBarHeight}px")
            
            // Toolbar에 동적 마진 적용
            val toolbarParams = binding.toolbar.layoutParams as ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = statusBarHeight
            binding.toolbar.layoutParams = toolbarParams
            
            // ⭐ 하단 입력창에 네비게이션 바 높이만큼 패딩 추가
            binding.inputBar.setPadding(
                binding.inputBar.paddingLeft,
                binding.inputBar.paddingTop,
                binding.inputBar.paddingRight,
                binding.inputBar.paddingBottom + navigationBarHeight
            )
            
            Log.d("SystemBarInsets", "Toolbar 마진 조정 완료 - 상단: ${statusBarHeight}px")
            Log.d("SystemBarInsets", "입력창 패딩 조정 완료 - 하단: ${navigationBarHeight}px")
            Log.d("SystemBarInsets", "동적 시스템 바 여백 조정 완료")
            
            // 원본 인셋 반환
            insets
        }
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
            Log.d("ChatActivity_UserLoad", "사용자 정보 로드 시작")
            
            // 1) DB에서 currentUser 불러오기
            val user = try {
                RoomDatabaseInstance
                    .getInstance(applicationContext)
                    .userDao()
                    .getUser()
            } catch (e: Exception) {
                Log.e("ChatActivity_UserLoad", "사용자 정보 DB 조회 실패", e)
                null
            }
            
            currentUser = user
            
            Log.d("ChatActivity_UserLoad", "DB 조회 결과 - user: ${user?.let { "ID: ${it.id}, Nickname: ${it.nickname}" } ?: "null"}")

            if (user == null) {
                Log.w("ChatActivity_UserLoad", "사용자 정보가 null입니다. 로그인 상태를 확인하세요.")
                Toast.makeText(this@ChatActivity, "⚠ 사용자 정보를 불러오지 못했습니다. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
                
                // 로그인 화면으로 돌아가기
                val intent = Intent(this@ChatActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return@launch
            }

            senderId = user.id
            Log.d("ChatActivity_UserLoad", "senderId 설정 완료: $senderId")

            // 2) Firebase 참여자 확인
            Log.d("ChatActivity_Participants", "Firebase 참여자 확인 시작 - User: ${user.id}, Room: ${viewModel.roomCode}")
            
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
                // 실패 시에도 일단 진행 (네트워크 문제일 수 있음)
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
                val options = arrayOf("📸 사진 촬영", "🖼️ 갤러리에서 선택")
                DialogHelper.showStyledChoiceDialog(
                    context = this@ChatActivity,
                    title = "사진 전송 방법 선택",
                    options = options
                ) { which ->
                    if (which == 0) openCamera() else openGallery()
                }
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
        // 메뉴 XML을 사용하지 않고 직접 메뉴 아이템 추가
        menu?.add(0, 1001, 0, "메뉴")?.apply {
            setIcon(R.drawable.ic_menu)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1001 -> {
                // 새로운 채팅방 메뉴 액티비티로 이동
                val intent = Intent(this, ChatRoomMenuActivity::class.java).apply {
                    putExtra("roomCode", viewModel.roomCode)
                    putExtra("roomName", supportActionBar?.title?.toString() ?: "채팅방")
                }
                startActivity(intent)
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showOcrChoiceDialog() {
        val options = arrayOf("📸 사진 촬영", "🖼️ 갤러리에서 선택")

        DialogHelper.showStyledChoiceDialog(
            context = this,
            title = "영수증 인식 방법 선택",
            options = options
        ) { which ->
            when (which) {
                0 -> {
                    Log.d("OCR_CAMERA", "📸 사진 촬영 선택됨")
                    openOcrCamera()
                }

                1 -> {
                    Log.d("OCR_CAMERA", "🖼️ 사진 선택 선택됨")
                    receiptImageLauncher.launch("image/*")
                }
            }
        }
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
        Log.d("ChatActivity", "📤 OCR 메시지 전송 시작: $message")
        viewModel.sendMessage(message)
        
        // 메시지 전송 후 자동 스크롤
        binding.messagesList.post {
            layoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
        }
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