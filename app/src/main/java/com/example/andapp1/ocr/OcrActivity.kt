package com.example.andapp1.ocr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.andapp1.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.example.andapp1.RoomDatabaseInstance
import com.example.andapp1.expense.ExpenseItem
import android.os.Handler
import android.os.Looper
import android.app.Activity

/**
 * OCR 기능을 제공하는 Activity
 * 영수증 금액 추출 및 채팅방 연동 특화
 */
class OcrActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "OcrActivity"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_AUTO_SEND = "auto_send"
    }
    
    // UI 컴포넌트
    private lateinit var imageView: ImageView
    private lateinit var processedImageView: ImageView
    private lateinit var loadingView: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var resultScrollView: ScrollView
    private lateinit var resultText: TextView
    private lateinit var amountSummaryCard: MaterialCardView
    private lateinit var totalAmountText: TextView
    private lateinit var itemCountText: TextView
    private lateinit var cameraButton: MaterialButton
    private lateinit var galleryButton: MaterialButton
    private lateinit var sendToChatButton: MaterialButton
    private lateinit var retryButton: MaterialButton
    
    // ViewModel 및 상태
    private lateinit var viewModel: OcrViewModel
    private var chatId: String? = null
    private var autoSend: Boolean = false
    
    // 카메라 촬영을 위한 임시 파일 URI
    private var photoUri: Uri? = null
    
    // ActivityResult Launchers
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var roiCameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<String>
    private lateinit var roiImageEditorLauncher: ActivityResultLauncher<Intent>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate 시작!")
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_ocr)
            Log.d(TAG, "setContentView 완료")
            
            // Intent에서 파라미터 추출
            chatId = intent.getStringExtra(EXTRA_CHAT_ID)
            autoSend = intent.getBooleanExtra(EXTRA_AUTO_SEND, false)
            
            Log.d(TAG, "OcrActivity 시작 - chatId: $chatId, autoSend: $autoSend")
            
            initializeViews()
            initializeViewModel()
            initializeActivityLaunchers()
            setupClickListeners()
            observeViewModel()
            
            Log.d(TAG, "onCreate 완료!")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate 에러: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * View 초기화
     */
    private fun initializeViews() {
        Log.d(TAG, "View 초기화 시작")
        
        try {
            imageView = findViewById(R.id.imageView)
            processedImageView = findViewById(R.id.processedImageView)
            loadingView = findViewById(R.id.loadingView)
            statusText = findViewById(R.id.statusText)
            resultScrollView = findViewById(R.id.resultScrollView)
            resultText = findViewById(R.id.resultText)
            amountSummaryCard = findViewById(R.id.amountSummaryCard)
            totalAmountText = findViewById(R.id.totalAmountText)
            itemCountText = findViewById(R.id.itemCountText)
            
            Log.d(TAG, "기본 View들 찾기 완료")
            
            // 버튼들 찾기
            cameraButton = findViewById(R.id.cameraButton)
            galleryButton = findViewById(R.id.galleryButton)
            sendToChatButton = findViewById(R.id.sendToChatButton)
            retryButton = findViewById(R.id.retryButton)
            
            Log.d(TAG, "버튼들 찾기 완료 - cameraButton: ${cameraButton != null}, galleryButton: ${galleryButton != null}")
            
            // 버튼 컨테이너 확인
            val buttonContainer = findViewById<LinearLayout>(R.id.buttonContainer)
            if (buttonContainer != null) {
                Log.d(TAG, "버튼 컨테이너 찾음")
                buttonContainer.setOnTouchListener { v, event ->
                    Log.d(TAG, "버튼 컨테이너 터치 이벤트: ${event.action}")
                    false
                }
            } else {
                Log.w(TAG, "버튼 컨테이너를 찾을 수 없음")
            }
            
            // 초기 상태 설정
            amountSummaryCard.visibility = View.GONE
            processedImageView.visibility = View.GONE
            resultScrollView.visibility = View.GONE
            sendToChatButton.visibility = View.GONE
            retryButton.visibility = View.GONE
            
            // 버튼들을 명시적으로 활성화
            cameraButton.isEnabled = true
            galleryButton.isEnabled = true
            
            Log.d(TAG, "View 초기화 완료 - 카메라 버튼 상태: clickable=${cameraButton.isClickable}, enabled=${cameraButton.isEnabled}, focusable=${cameraButton.isFocusable}")
            Log.d(TAG, "갤러리 버튼 상태: clickable=${galleryButton.isClickable}, enabled=${galleryButton.isEnabled}, focusable=${galleryButton.isFocusable}")
        } catch (e: Exception) {
            Log.e(TAG, "View 초기화 에러: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * ViewModel 초기화
     */
    private fun initializeViewModel() {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return OcrViewModel(application) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[OcrViewModel::class.java]
    }
    
    /**
     * ActivityResult Launcher 초기화
     */
    private fun initializeActivityLaunchers() {
        // 카메라 권한 요청
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "카메라 권한 승인됨")
                launchCamera()
            } else {
                Log.w(TAG, "카메라 권한 거부됨")
                showPermissionDeniedDialog("카메라")
            }
        }
        
        // 저장소 권한 요청
        storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "저장소 권한 승인됨")
                launchGallery()
            } else {
                Log.w(TAG, "저장소 권한 거부됨")
                showPermissionDeniedDialog("저장소")
            }
        }
        
        // 카메라 실행 (고해상도 이미지)
        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                // photoUri에서 고해상도 이미지를 불러옴
                photoUri?.let { uri ->
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        Log.d(TAG, "카메라에서 고해상도 이미지 획득: ${bitmap.width}x${bitmap.height}")
                        processImage(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "카메라 이미지 로드 실패: ${e.message}", e)
                        showError("이미지를 불러올 수 없습니다: ${e.message}")
                    }
                } ?: run {
                    Log.e(TAG, "카메라 촬영 후 photoUri가 null입니다.")
                    showError("이미지를 가져오는 데 실패했습니다.")
                }
            } else {
                Log.d(TAG, "카메라 촬영 취소됨")
            }
        }
        
        // 갤러리 실행
        galleryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageUri = result.data?.data
                if (imageUri != null) {
                    try {
                        Log.d(TAG, "갤러리에서 이미지 선택됨: $imageUri")
                        // ROI 이미지 편집기로 이동
                        launchRoiImageEditor(imageUri)
                    } catch (e: Exception) {
                        Log.e(TAG, "갤러리 이미지 처리 실패: ${e.message}", e)
                        showError("이미지를 처리할 수 없습니다: ${e.message}")
                    }
                } else {
                    Log.e(TAG, "갤러리에서 이미지 URI를 가져올 수 없음")
                    showError("이미지를 선택해주세요.")
                }
            } else {
                Log.d(TAG, "갤러리 선택 취소됨")
            }
        }
        
        // ROI 카메라 실행
        roiCameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val ocrResult = result.data?.getStringExtra("ocr_result")
                val roiUsed = result.data?.getBooleanExtra("roi_used", false) ?: false
                
                if (!ocrResult.isNullOrEmpty()) {
                    Log.d(TAG, "ROI 카메라에서 OCR 결과 받음: $ocrResult")
                    // OCR 결과를 직접 처리
                    handleRoiOcrResult(ocrResult)
                } else {
                    Log.w(TAG, "ROI 카메라에서 빈 OCR 결과")
                    showError("OCR 처리 결과가 비어있습니다.")
                }
            } else {
                Log.d(TAG, "ROI 카메라 취소됨")
            }
        }
        
        // ROI 이미지 편집기 실행
        roiImageEditorLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val ocrResult = result.data?.getStringExtra(RoiImageEditorActivity.EXTRA_OCR_RESULT)
                val roiUsed = result.data?.getBooleanExtra(RoiImageEditorActivity.EXTRA_ROI_USED, false) ?: false
                val roiImagePath = result.data?.getStringExtra(RoiImageEditorActivity.EXTRA_ROI_IMAGE_PATH)
                
                if (!ocrResult.isNullOrEmpty()) {
                    Log.d(TAG, "ROI 이미지 편집기에서 OCR 결과 받음: $ocrResult")
                    
                    // ROI 이미지가 있으면 로드해서 표시
                    if (!roiImagePath.isNullOrEmpty()) {
                        try {
                            val roiBitmap = BitmapFactory.decodeFile(roiImagePath)
                            if (roiBitmap != null) {
                                processedImageView.setImageBitmap(roiBitmap)
                                processedImageView.visibility = View.VISIBLE
                                Log.d(TAG, "ROI 이미지 표시됨: $roiImagePath")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "ROI 이미지 로드 실패", e)
                        }
                    }
                    
                    // OCR 결과를 직접 처리
                    handleRoiOcrResult(ocrResult)
                } else {
                    Log.w(TAG, "ROI 이미지 편집기에서 빈 OCR 결과")
                    showError("OCR 처리 결과가 비어있습니다.")
                }
            } else {
                Log.d(TAG, "ROI 이미지 편집기 취소됨")
            }
        }
    }
    
    /**
     * 클릭 리스너 설정
     */
    private fun setupClickListeners() {
        Log.d(TAG, "클릭 리스너 설정 시작")
        
        // 버튼 배경색 설정 (디버깅용)
        cameraButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        galleryButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        
        // OnTouchListener로 터치 이벤트 추적
        cameraButton.setOnTouchListener { v, event ->
            Log.d(TAG, "카메라 버튼 터치 이벤트: ${event.action}")
            false // 이벤트를 계속 전달
        }
        
        galleryButton.setOnTouchListener { v, event ->
            Log.d(TAG, "갤러리 버튼 터치 이벤트: ${event.action}")
            false // 이벤트를 계속 전달
        }
        
        cameraButton.setOnClickListener {
            Log.d(TAG, "카메라 버튼 클릭됨!")
            // ROI 기반 카메라 실행
            launchRoiCamera()
        }
        
        galleryButton.setOnClickListener {
            Log.d(TAG, "갤러리 버튼 클릭됨!")
            requestStoragePermissionAndLaunch()
        }
        
        sendToChatButton.setOnClickListener {
            Log.d(TAG, "채팅방 전송 버튼 클릭됨!")
            viewModel.sendToChat(chatId, includeDetails = true)
        }
        
        retryButton.setOnClickListener {
            Log.d(TAG, "재시도 버튼 클릭됨!")
            showIdleState()
        }
        
        Log.d(TAG, "클릭 리스너 설정 완료 - 카메라 버튼: ${cameraButton.isEnabled}, 갤러리 버튼: ${galleryButton.isEnabled}")
    }
    
    /**
     * ViewModel 상태 관찰
     */
    private fun observeViewModel() {
        // OCR 상태 관찰
        viewModel.ocrState.observe(this) { state ->
            when (state) {
                is OcrState.Idle -> {
                    Log.d(TAG, "OCR 상태: 대기")
                    showIdleState()
                }
                is OcrState.Loading -> {
                    Log.d(TAG, "OCR 상태: 로딩 - ${state.message}")
                    showLoadingState(state.message)
                }
                is OcrState.Success -> {
                    Log.d(TAG, "OCR 상태: 성공 - ${state.result.getFormattedAmount()}")
                    showSuccessState(state.result)
                    
                    // 자동 전송 옵션이 켜져있으면 바로 전송
                    if (autoSend) {
                        viewModel.sendToChat(chatId, includeDetails = true)
                    }
                }
                is OcrState.Error -> {
                    Log.d(TAG, "OCR 상태: 오류 - ${state.message}")
                    showErrorState(state.message)
                }
            }
        }
        
        // 처리된 이미지 관찰
        viewModel.processedImage.observe(this) { bitmap ->
            if (bitmap != null) {
                processedImageView.setImageBitmap(bitmap)
                processedImageView.visibility = View.VISIBLE
                Log.d(TAG, "처리된 이미지 표시됨")
            }
        }
        
        // 채팅 전송 결과 관찰
        viewModel.chatSendResult.observe(this) { result ->
            when (result) {
                is ChatSendResult.Loading -> {
                    Log.d(TAG, "채팅 전송: 로딩")
                    sendToChatButton.isEnabled = false
                    sendToChatButton.text = "전송 중..."
                }
                is ChatSendResult.Success -> {
                    Log.d(TAG, "채팅 전송: 성공")
                    showChatSendSuccess(result.message)
                }
                is ChatSendResult.Error -> {
                    Log.d(TAG, "채팅 전송: 오류 - ${result.message}")
                    showChatSendError(result.message)
                }
                null -> {
                    // 초기 상태
                    sendToChatButton.isEnabled = true
                    sendToChatButton.text = "채팅방에 전송"
                }
            }
        }
    }
    
    /**
     * 이미지 처리 시작
     */
    private fun processImage(bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)
        viewModel.processReceiptImage(bitmap)
    }
    
    /**
     * 대기 상태 UI
     */
    private fun showIdleState() {
        loadingView.visibility = View.GONE
        statusText.text = "영수증을 촬영하거나 갤러리에서 선택해주세요"
        amountSummaryCard.visibility = View.GONE
        resultScrollView.visibility = View.GONE
        sendToChatButton.visibility = View.GONE
        retryButton.visibility = View.GONE
        
        // 버튼 활성화 (핵심 수정!)
        cameraButton.isEnabled = true
        galleryButton.isEnabled = true
        
        Log.d(TAG, "대기 상태로 전환 - 버튼 활성화됨")
    }
    
    /**
     * 로딩 상태 UI
     */
    private fun showLoadingState(message: String) {
        loadingView.visibility = View.VISIBLE
        statusText.text = message
        cameraButton.isEnabled = false
        galleryButton.isEnabled = false
        sendToChatButton.visibility = View.GONE
        retryButton.visibility = View.GONE
    }
    
    /**
     * 성공 상태 UI
     */
    private fun showSuccessState(result: ReceiptAmount) {
        loadingView.visibility = View.GONE
        cameraButton.isEnabled = true
        galleryButton.isEnabled = true
        
        // 금액 요약 카드 표시
        val mainAmount = result.getMainAmount()
        if (mainAmount != null) {
            totalAmountText.text = String.format("%,d원", mainAmount)
            itemCountText.text = if (result.items.isNotEmpty()) {
                "${result.items.size}개 항목"
            } else {
                "금액 정보"
            }
            amountSummaryCard.visibility = View.VISIBLE
            statusText.text = "✅ 여행 경비 분석 완료!"
            
            // TravelExpenseActivity에서 호출된 경우 결과 반환
            if (!autoSend) {
                returnResultToParent(mainAmount, result.toString())
            }
        }
        
        // 상세 결과 표시
        resultText.text = result.getDetailedInfo()
        resultScrollView.visibility = View.VISIBLE
        
        // 채팅방 전송 버튼 표시
        sendToChatButton.visibility = View.VISIBLE
        retryButton.visibility = View.VISIBLE
    }
    
    /**
     * TravelExpenseActivity로 결과 반환
     */
    private fun returnResultToParent(amount: Int, description: String) {
        val resultIntent = Intent().apply {
            putExtra("recognized_amount", amount)
            putExtra("recognized_text", description)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        
        Log.d(TAG, "TravelExpenseActivity로 결과 반환 - 금액: $amount, 설명: $description")
        
        // 사용자에게 결과 반환 안내
        Toast.makeText(this, "인식된 금액: ${String.format("%,d", amount)}원", Toast.LENGTH_SHORT).show()
        
        // 잠시 후 종료
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 1500)
    }
    
    /**
     * 오류 상태 UI
     */
    private fun showErrorState(message: String) {
        loadingView.visibility = View.GONE
        cameraButton.isEnabled = true
        galleryButton.isEnabled = true
        statusText.text = "❌ $message"
        amountSummaryCard.visibility = View.GONE
        
        // 오류 메시지 표시
        resultText.text = message
        resultScrollView.visibility = View.VISIBLE
        
        sendToChatButton.visibility = View.GONE
        retryButton.visibility = View.VISIBLE
    }
    
    /**
     * 채팅 전송 성공 처리
     */
    private fun showChatSendSuccess(message: String) {
        sendToChatButton.isEnabled = true
        sendToChatButton.text = "✅ 전송 완료"
        
        Toast.makeText(this, "채팅방에 정산 결과가 전송되었습니다", Toast.LENGTH_SHORT).show()
        
        // 3초 후 액티비티 종료
        sendToChatButton.postDelayed({
            finish()
        }, 3000)
    }
    
    /**
     * 채팅 전송 오류 처리
     */
    private fun showChatSendError(message: String) {
        sendToChatButton.isEnabled = true
        sendToChatButton.text = "채팅방에 전송"
        
        AlertDialog.Builder(this)
            .setTitle("전송 실패")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }
    
    /**
     * 카메라 권한 요청 및 실행
     */
    private fun requestCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == 
                PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "카메라 권한 이미 승인됨")
                launchCamera()
            }
            else -> {
                Log.d(TAG, "카메라 권한 요청")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    /**
     * 저장소 권한 요청 및 실행 (Android 13+ 대응)
     */
    private fun requestStoragePermissionAndLaunch() {
        // 📌 Android 버전별 권한 확인
        val hasStoragePermission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ : READ_MEDIA_IMAGES 사용
                val mediaImagesPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                Log.d(TAG, "READ_MEDIA_IMAGES 권한: ${if (mediaImagesPermission == PackageManager.PERMISSION_GRANTED) "허용됨" else "거부됨"}")
                mediaImagesPermission == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                // Android 12 이하 : READ_EXTERNAL_STORAGE 사용
                val storagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                Log.d(TAG, "READ_EXTERNAL_STORAGE 권한: ${if (storagePermission == PackageManager.PERMISSION_GRANTED) "허용됨" else "거부됨"}")
                storagePermission == PackageManager.PERMISSION_GRANTED
            }
        }

        if (hasStoragePermission) {
            Log.d(TAG, "저장소 권한 이미 승인됨")
            launchGallery()
        } else {
            Log.d(TAG, "저장소 권한 요청")
            val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            storagePermissionLauncher.launch(permissionToRequest)
        }
    }
    
    /**
     * 카메라 실행 (고해상도 이미지)
     */
    private fun launchCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        
        // 임시 파일을 생성하고 FileProvider를 통해 URI를 얻어옴
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Log.e(TAG, "이미지 파일 생성 실패", ex)
            showError("이미지 파일을 생성할 수 없습니다.")
            null
        }
        
        photoFile?.also {
            photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                it
            )
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            
            if (cameraIntent.resolveActivity(packageManager) != null) {
                Log.d(TAG, "고해상도 카메라 실행 - 파일: ${it.absolutePath}")
                cameraLauncher.launch(cameraIntent)
            } else {
                showError("카메라 앱을 찾을 수 없습니다.")
            }
        }
    }
    
    /**
     * 카메라 촬영용 임시 이미지 파일 생성
     */
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // 타임스탬프를 사용하여 고유한 파일 이름 생성
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "RECEIPT_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            Log.d(TAG, "임시 이미지 파일 생성: $absolutePath")
        }
    }
    
    /**
     * 갤러리 실행
     */
    private fun launchGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(galleryIntent)
    }
    
    /**
     * 권한 거부 다이얼로그
     */
    private fun showPermissionDeniedDialog(permissionType: String) {
        AlertDialog.Builder(this)
            .setTitle("권한 필요")
            .setMessage("${permissionType} 기능을 사용하려면 권한이 필요합니다.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(settingsIntent)
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    /**
     * 오류 메시지 표시
     */
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    

    
    /**
     * ROI 카메라 실행
     */
    private fun launchRoiCamera() {
        Log.d(TAG, "ROI 카메라 실행")
        
        try {
            val intent = Intent(this, RoiCameraActivity::class.java)
            roiCameraLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "ROI 카메라 실행 실패", e)
            showError("ROI 카메라를 실행할 수 없습니다: ${e.message}")
        }
    }
    
    /**
     * ROI 이미지 편집기 실행 (갤러리용)
     */
    private fun launchRoiImageEditor(imageUri: Uri) {
        Log.d(TAG, "ROI 이미지 편집기 실행: $imageUri")
        
        try {
            val intent = Intent(this, RoiImageEditorActivity::class.java).apply {
                putExtra(RoiImageEditorActivity.EXTRA_IMAGE_URI, imageUri)
            }
            roiImageEditorLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "ROI 이미지 편집기 실행 실패", e)
            showError("ROI 이미지 편집기를 실행할 수 없습니다: ${e.message}")
        }
    }
    
    /**
     * ROI OCR 결과 처리
     */
    private fun handleRoiOcrResult(ocrResult: String) {
        Log.d(TAG, "ROI OCR 결과 처리 시작: $ocrResult")
        
        try {
            // OCR 결과를 ReceiptAmount 객체로 변환
            val receiptAmount = parseOcrResultToReceiptAmount(ocrResult)
            
            // 사용자에게 금액 확인 요청
            showAmountConfirmationDialog(receiptAmount, ocrResult)
            
            Log.d(TAG, "ROI OCR 결과 처리 완료")
        } catch (e: Exception) {
            Log.e(TAG, "ROI OCR 결과 처리 중 오류", e)
            showError("OCR 결과 처리에 실패했습니다: ${e.message}")
        }
    }
    
    /**
     * 금액 확인 다이얼로그 표시
     */
    private fun showAmountConfirmationDialog(receiptAmount: ReceiptAmount, originalOcrText: String) {
        val detectedAmount = receiptAmount.getMainAmount() ?: 0
        val formattedAmount = if (detectedAmount > 0) {
            String.format("%,d", detectedAmount)
        } else {
            ""
        }
        
        Log.d(TAG, "금액 확인 다이얼로그 표시: ${formattedAmount}원")
        
        // 다이얼로그 레이아웃 생성
        val dialogView = layoutInflater.inflate(R.layout.dialog_amount_confirmation, null)
        val amountEditText = dialogView.findViewById<EditText>(R.id.amount_edit_text)
        val ocrResultText = dialogView.findViewById<TextView>(R.id.ocr_result_text)
        
        // 초기값 설정
        amountEditText.setText(formattedAmount)
        ocrResultText.text = "OCR 인식 결과:\n$originalOcrText"
        
        // 다이얼로그 생성
        val dialog = AlertDialog.Builder(this)
            .setTitle("💰 금액 확인")
            .setMessage("인식된 금액이 정확한가요?")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                val userAmount = amountEditText.text.toString()
                    .replace(",", "")
                    .replace("원", "")
                    .trim()
                
                val finalAmount = userAmount.toIntOrNull() ?: detectedAmount
                
                Log.d(TAG, "사용자 확정 금액: ${finalAmount}원")
                
                if (finalAmount > 0) {
                    // 사용처 이름 입력 다이얼로그 표시
                    showExpenseNameDialog(finalAmount, originalOcrText)
                } else {
                    android.widget.Toast.makeText(this@OcrActivity, "올바른 금액을 입력해주세요.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("다시 시도") { _, _ ->
                // 다시 시도 - UI 초기화
                showIdleState()
            }
            .setNeutralButton("원본 보기") { _, _ ->
                // 원본 OCR 텍스트 전체 보기
                showOcrRawTextDialog(originalOcrText)
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // EditText에 포커스 주고 텍스트 선택
        amountEditText.requestFocus()
        amountEditText.selectAll()
    }
    
    /**
     * 원본 OCR 텍스트 표시 다이얼로그
     */
    private fun showOcrRawTextDialog(ocrText: String) {
        AlertDialog.Builder(this)
            .setTitle("📄 OCR 원본 결과")
            .setMessage(ocrText)
            .setPositiveButton("확인", null)
            .show()
    }
    
    /**
     * 사용처 이름 입력 다이얼로그
     */
    private fun showExpenseNameDialog(amount: Int, originalOcrText: String) {
        val nameEditText = EditText(this).apply {
            hint = "사용처 입력 (예: 스타벅스, 롯데리아, GS25...)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
            setPadding(20, 20, 20, 20)
        }
        
        val message = """
            💰 금액: ${String.format("%,d", amount)}원
            📝 어디서 사용하셨나요?
            
            카테고리별로 정리하여 여행 경비를 관리해드릴게요!
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("💳 사용처 입력")
            .setMessage(message)
            .setView(nameEditText)
            .setPositiveButton("다음") { _, _ ->
                val expenseName = nameEditText.text.toString().trim()
                val finalName = if (expenseName.isBlank()) "영수증 항목" else expenseName
                
                Log.d(TAG, "사용처 확정: $finalName, 금액: ${amount}원")
                
                // 🎯 OCR Activity 내에서 바로 경비 추가 다이얼로그 처리
                showExpenseAddDialog(amount, finalName, originalOcrText)
            }
            .setNegativeButton("건너뛰기") { _, _ ->
                // 🎯 이름 없이도 바로 경비 추가 다이얼로그 처리
                showExpenseAddDialog(amount, "영수증 항목", originalOcrText)
            }
            .setCancelable(false)
            .show()
        
        // EditText에 포커스
        nameEditText.requestFocus()
    }
    
    /**
     * 경비 추가 다이얼로그 (OCR Activity 내에서 직접 처리)
     */
    private fun showExpenseAddDialog(amount: Int, description: String, ocrText: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null)
        val amountEditText = dialogView.findViewById<EditText>(R.id.editTextAmount)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.editTextDescription)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        
        // 💡 값 미리 채우기
        amountEditText.setText(amount.toString())
        descriptionEditText.setText(description)
        descriptionEditText.hint = "사용처를 입력하세요 (예: 스타벅스, 맛집, 주유소)"
        
        // 카테고리 스피너 설정
        val categories = arrayOf(
            "🍽️ 식비", "☕ 카페", "🏨 숙박", "🚗 교통비", 
            "⛽ 주유", "🚙 렌트카", "🎢 관광/액티비티", "🛒 마트/편의점"
        )
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter
        
        // 🎯 자동 카테고리 선택
        val autoCategory = categorizeExpense(description)
        val categoryIndex = when (autoCategory) {
            "식비" -> 0
            "카페" -> 1  
            "숙박" -> 2
            "교통비" -> 3
            "주유" -> 4
            "렌트카" -> 5
            "관광/액티비티" -> 6
            "마트/편의점" -> 7
            else -> 0
        }
        categorySpinner.setSelection(categoryIndex)
        
        AlertDialog.Builder(this)
            .setTitle("💸 경비 추가")
            .setView(dialogView)
            .setPositiveButton("저장 후 채팅 전송") { _, _ ->
                val finalAmount = amountEditText.text.toString().toIntOrNull() ?: amount
                val finalDescription = descriptionEditText.text.toString().trim().let { desc ->
                    if (desc.isBlank()) {
                        when (categories[categorySpinner.selectedItemPosition]) {
                            "🍽️ 식비" -> "식당"
                            "☕ 카페" -> "카페"
                            "🏨 숙박" -> "숙박비"
                            "🚗 교통비" -> "교통비"
                            "⛽ 주유" -> "주유비"
                            "🚙 렌트카" -> "렌트카"
                            "🎢 관광/액티비티" -> "관광"
                            "🛒 마트/편의점" -> "쇼핑"
                            else -> "기타 경비"
                        }
                    } else desc
                }
                val finalCategory = categories[categorySpinner.selectedItemPosition]
                
                // 🎯 중복 방지하면서 저장 + 채팅 전송
                saveExpenseAndSendToChat(finalAmount, finalDescription, finalCategory, ocrText)
            }
            .setNegativeButton("저장만") { _, _ ->
                val finalAmount = amountEditText.text.toString().toIntOrNull() ?: amount
                val finalDescription = descriptionEditText.text.toString().trim().let { desc ->
                    if (desc.isBlank()) "영수증 항목" else desc
                }
                val finalCategory = categories[categorySpinner.selectedItemPosition]
                
                // 🎯 저장만 (채팅 전송 없이)
                saveExpenseOnly(finalAmount, finalDescription, finalCategory, ocrText)
            }
            .setNeutralButton("취소", null)
            .show()
    }
    
    /**
     * 경비 저장 + 채팅 전송
     */
    private fun saveExpenseAndSendToChat(amount: Int, description: String, category: String, ocrText: String) {
        Log.d(TAG, "경비 저장 + 채팅 전송 시작")
        
        lifecycleScope.launch {
            try {
                // 🚫 중복 방지: 같은 금액+설명+시간(1분 이내) 체크
                val isDuplicate = checkForDuplicate(amount, description)
                if (isDuplicate) {
                    runOnUiThread {
                        Toast.makeText(this@OcrActivity, "⚠️ 동일한 경비가 이미 저장되어 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Room DB에 저장
                val success = saveToDatabase(amount, description, category, ocrText)
                
                if (success) {
                    runOnUiThread {
                        // ✅ 성공 상태로 UI 변경
                        showSuccessStateWithChatOption(amount, description, category)
                        
                        Toast.makeText(this@OcrActivity, 
                            "✅ 경비 저장 완료! 채팅방에 전송됩니다.", 
                            Toast.LENGTH_SHORT).show()
                    }
                    
                    // 🎯 채팅 전송
                    sendExpenseMessageToChat(amount, description, category)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@OcrActivity, "❌ 경비 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "경비 저장 + 채팅 전송 중 오류", e)
                runOnUiThread {
                    Toast.makeText(this@OcrActivity, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 경비 저장만 (채팅 전송 없이)
     */
    private fun saveExpenseOnly(amount: Int, description: String, category: String, ocrText: String) {
        Log.d(TAG, "경비 저장만 실행")
        
        lifecycleScope.launch {
            try {
                // 🚫 중복 방지
                val isDuplicate = checkForDuplicate(amount, description)
                if (isDuplicate) {
                    runOnUiThread {
                        Toast.makeText(this@OcrActivity, "⚠️ 동일한 경비가 이미 저장되어 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val success = saveToDatabase(amount, description, category, ocrText)
                
                runOnUiThread {
                    if (success) {
                        showSuccessStateWithBackOption(amount, description)
                        Toast.makeText(this@OcrActivity, "✅ 경비가 저장되었습니다!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@OcrActivity, "❌ 경비 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "경비 저장 중 오류", e)
                runOnUiThread {
                    Toast.makeText(this@OcrActivity, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * OCR 결과 텍스트를 ReceiptAmount 객체로 변환 (한국 영수증 구조 기반 v3)
     */
    private fun parseOcrResultToReceiptAmount(ocrText: String): ReceiptAmount {
        Log.d(TAG, "OCR 결과 파싱 시작: $ocrText")
        
        var maxAmount = 0
        
        try {
            // 1. 영수증 구조 기반 우선순위 파싱
            val structuredAmount = parseByReceiptStructure(ocrText)
            if (structuredAmount > 0) {
                maxAmount = structuredAmount
                Log.d(TAG, "영수증 구조 기반 파싱 성공: ${structuredAmount}원")
                return ReceiptAmount().apply {
                    setMainAmount(maxAmount)
                    rawText = ocrText
                    items.clear()
                    items.add("총 금액" to maxAmount)
                }
            }
            
            // 2. "원" 근처 금액 우선 탐색 (수정된 패턴)
            // "59, 500원", "59,500원", "59 500원" 모두 인식
            val amountWithWonPatterns = listOf(
                """(\d{1,3}[,\s]\d{3})\s*원""".toRegex(),  // "59, 500원" 또는 "59,500원"
                """(\d{1,3}(?:[,\s]\d{3})*)\s*원""".toRegex(),  // 기존 패턴 유지
                """(\d{4,})\s*원""".toRegex()  // "59500원" 직접 패턴
            )
            
            val foundAmounts = mutableSetOf<Int>()
            
            for (pattern in amountWithWonPatterns) {
                val wonMatches = pattern.findAll(ocrText)
                for (match in wonMatches) {
                    val numberText = match.groupValues[1].replace("""[,\s]""".toRegex(), "")
                    val amount = numberText.toIntOrNull()
                    
                    if (amount != null && amount >= 100 && amount < 100_000_000) {
                        foundAmounts.add(amount)
                        Log.d(TAG, "원 단위 발견: ${amount}원 (패턴: ${match.value})")
                    }
                }
            }
            
            if (foundAmounts.isNotEmpty()) {
                maxAmount = foundAmounts.maxOrNull() ?: 0
                Log.d(TAG, "원 단위 최대금액: ${maxAmount}원")
                return ReceiptAmount().apply {
                    setMainAmount(maxAmount)
                    rawText = ocrText
                    items.clear()
                    items.add("총 금액" to maxAmount)
                }
            }
            
            // 3. 분리된 숫자 재구성 (개선된 로직)
            val reconstructedAmount = reconstructSeparatedNumbersV3(ocrText)
            if (reconstructedAmount > 0) {
                maxAmount = reconstructedAmount
                Log.d(TAG, "분리된 숫자 재구성 성공: ${reconstructedAmount}원")
                return ReceiptAmount().apply {
                    setMainAmount(maxAmount)
                    rawText = ocrText
                    items.clear()
                    items.add("인식된 금액" to maxAmount)
                }
            }
            
            // 4. 일반 숫자 패턴 (기존 유지)
            val cleanedText = ocrText.replace("""[^\d,\s]""".toRegex(), " ")
            val numberPattern = """(\d{1,3}(?:,\d{3})+)""".toRegex()
            val numberMatches = numberPattern.findAll(cleanedText)
            val numberAmounts = mutableSetOf<Int>()
            
            for (match in numberMatches) {
                val numberText = match.value.replace(",", "")
                val amount = numberText.toIntOrNull()
                
                if (amount != null && amount >= 1000 && amount < 100_000_000) {
                    numberAmounts.add(amount)
                    Log.d(TAG, "숫자 패턴 발견: ${amount}원")
                }
            }
            
            maxAmount = numberAmounts.maxOrNull() ?: 0
            Log.d(TAG, "최종 파싱 결과 - 최대금액: ${maxAmount}원")
            
        } catch (e: Exception) {
            Log.e(TAG, "OCR 결과 파싱 중 오류", e)
        }
        
        return ReceiptAmount().apply {
            setMainAmount(maxAmount)
            rawText = ocrText
            items.clear()
            if (maxAmount > 0) {
                items.add("인식된 금액" to maxAmount)
            }
        }
    }
    
    /**
     * 한국 영수증 구조 기반 금액 탐색
     * 총액/합계 키워드 근처에서 금액을 우선 탐색
     */
    private fun parseByReceiptStructure(ocrText: String): Int {
        Log.d(TAG, "영수증 구조 기반 파싱 시작")
        
        try {
            val lines = ocrText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            
            // 총액/합계 관련 키워드가 포함된 라인 탐색
            val totalKeywords = listOf("총", "합계", "금액", "total", "amount", "계")
            val wonKeywords = listOf("원")
            
            for (i in lines.indices) {
                val line = lines[i]
                
                // 총액 키워드가 포함된 라인인지 확인
                val hasTotalKeyword = totalKeywords.any { keyword ->
                    line.contains(keyword, ignoreCase = true)
                }
                
                if (hasTotalKeyword || line.contains("원")) {
                    Log.d(TAG, "총액 관련 라인 발견: $line")
                    
                    // 해당 라인에서 금액 추출
                    val amount = extractAmountFromLine(line)
                    if (amount > 0) {
                        Log.d(TAG, "구조 기반 금액 추출 성공: ${amount}원")
                        return amount
                    }
                    
                    // 인접 라인도 확인 (±1줄)
                    for (offset in -1..1) {
                        val adjIndex = i + offset
                        if (adjIndex in lines.indices && adjIndex != i) {
                            val adjAmount = extractAmountFromLine(lines[adjIndex])
                            if (adjAmount > 0) {
                                Log.d(TAG, "인접 라인에서 금액 발견: ${adjAmount}원")
                                return adjAmount
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "영수증 구조 기반 탐색 실패")
            return 0
            
        } catch (e: Exception) {
            Log.e(TAG, "영수증 구조 파싱 중 오류", e)
            return 0
        }
    }
    
    /**
     * 한 줄에서 금액 추출 (개선된 버전)
     */
    private fun extractAmountFromLine(line: String): Int {
        try {
            Log.d(TAG, "라인 분석: '$line'")
            
            // 💥 패턴 0: 쉼표+공백으로 분리된 숫자 우선 처리 "59, 500" 또는 "59, 5003"
            val separatedPattern = """(\d{1,3})\s*,\s*(\d{3,4})""".toRegex()
            val separatedMatch = separatedPattern.find(line)
            if (separatedMatch != null) {
                val thousands = separatedMatch.groupValues[1].toIntOrNull() ?: 0
                val hundredsText = separatedMatch.groupValues[2]
                
                // 4자리인 경우 처리: 5003 → 500 (마지막 자리는 OCR 오류로 간주)
                val hundreds = if (hundredsText.length == 4) {
                    // 마지막 자리가 0-3이면 3자리로 보정, 아니면 전체를 3자리로 축약
                    val lastDigit = hundredsText.last().digitToInt()
                    if (lastDigit <= 3) {
                        hundredsText.substring(0, 3).toIntOrNull() ?: 0
                    } else {
                        // 5003 같은 경우 → 500 (앞 3자리만)
                        hundredsText.substring(0, 3).toIntOrNull() ?: 0
                    }
                } else {
                    hundredsText.toIntOrNull() ?: 0
                }
                
                val amount = thousands * 1000 + hundreds
                if (amount >= 100 && amount < 100_000_000) {
                    Log.d(TAG, "🎯 분리된 숫자 패턴 추출: ${thousands},${hundredsText} → ${thousands},${hundreds} → ${amount}원")
                    return amount
                }
            }
            
            // 패턴 1: 쉼표가 포함된 금액 "59,500"
            val commaPattern = """(\d{1,3}(?:,\d{3})+)""".toRegex()
            val commaMatch = commaPattern.find(line)
            if (commaMatch != null) {
                val amount = commaMatch.value.replace(",", "").toIntOrNull()
                if (amount != null && amount >= 100 && amount < 100_000_000) {
                    Log.d(TAG, "쉼표 패턴 추출: ${amount}원")
                    return amount
                }
            }
            
            // 패턴 2: 공백으로 분리된 숫자 "59 500"
            val spacePattern = """(\d{1,3})\s+(\d{3})""".toRegex()
            val spaceMatch = spacePattern.find(line)
            if (spaceMatch != null) {
                val thousands = spaceMatch.groupValues[1].toIntOrNull() ?: 0
                val hundreds = spaceMatch.groupValues[2].toIntOrNull() ?: 0
                val amount = thousands * 1000 + hundreds
                if (amount >= 100 && amount <= 999999) {
                    Log.d(TAG, "공백 분리 패턴 추출: ${amount}원")
                    return amount
                }
            }
            
            // 패턴 3: 단순 숫자 (5자리 이상만) - 마지막에 처리
            val numberPattern = """(\d{5,})""".toRegex()
            val numberMatches = numberPattern.findAll(line)
            for (match in numberMatches) {
                val amount = match.value.toIntOrNull()
                // 🎯 5자리 이상만 허용하여 "5003" 같은 4자리 오인식 제외
                if (amount != null && amount >= 10000 && amount < 100_000_000) {
                    Log.d(TAG, "단순 숫자 패턴 추출: ${amount}원 (5자리 이상)")
                    return amount
                }
            }
            
            return 0
            
        } catch (e: Exception) {
            Log.e(TAG, "라인 금액 추출 중 오류", e)
            return 0
        }
    }
    
    /**
     * 분리된 숫자들을 재구성하여 올바른 금액 추출 (개선된 v3)
     * 예: "59, 5003" → "59, 500"으로 보정 (4자리를 3자리로)
     */
    private fun reconstructSeparatedNumbersV3(ocrText: String): Int {
        Log.d(TAG, "분리된 숫자 재구성 v3 시작: $ocrText")
        
        try {
            // 패턴 1: "59, 500원" 또는 "59,500원" 형태 (정확한 3자리 매칭)
            val pattern1 = """(\d{1,3})\s*,\s*(\d{3})(?:\s*원)?""".toRegex()
            val match1 = pattern1.find(ocrText)
            if (match1 != null) {
                val thousands = match1.groupValues[1].toIntOrNull() ?: 0
                val hundreds = match1.groupValues[2].toIntOrNull() ?: 0
                val result = thousands * 1000 + hundreds
                
                // 합리적인 금액 범위만 허용
                if (result >= 1000 && result <= 999999) {
                    Log.d(TAG, "패턴1 매치: ${thousands},${hundreds} → $result")
                    return result
                }
            }
            
            // 패턴 2: "59, 5003" → "59, 500"으로 보정 (4자리를 3자리로)
            val pattern2 = """(\d{1,3})\s*,\s*(\d{4})""".toRegex()
            val match2 = pattern2.find(ocrText)
            if (match2 != null) {
                val thousands = match2.groupValues[1].toIntOrNull() ?: 0
                val fullHundreds = match2.groupValues[2]
                
                // 4자리를 3자리로 보정 (마지막 자리 제거)
                if (fullHundreds.length == 4) {
                    val correctedHundreds = fullHundreds.substring(0, 3).toIntOrNull() ?: 0
                    val result = thousands * 1000 + correctedHundreds
                    
                    if (result >= 1000 && result <= 999999) {
                        Log.d(TAG, "패턴2 보정 매치: ${thousands},${fullHundreds} → ${thousands},${correctedHundreds} → $result")
                        return result
                    }
                }
            }
            
            // 패턴 3: 줄바꿈으로 분리된 경우 (엄격한 조건)
            val lines = ocrText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size >= 2) {
                for (i in 0 until lines.size - 1) {
                    val line1 = lines[i].replace("""[^\d]""".toRegex(), "")
                    val line2 = lines[i + 1].replace("""[^\d]""".toRegex(), "")
                    
                    // 첫 번째는 1-3자리, 두 번째는 정확히 3자리여야 함
                    if (line1.length in 1..3 && line2.length == 3) {
                        val thousands = line1.toIntOrNull() ?: continue
                        val hundreds = line2.toIntOrNull() ?: continue
                        val result = thousands * 1000 + hundreds
                        
                        if (result >= 1000 && result <= 999999) {
                            Log.d(TAG, "패턴3 매치: $line1 + $line2 → $result")
                            return result
                        }
                    }
                }
            }
            
            Log.d(TAG, "분리된 숫자 재구성 v3 실패")
            return 0
            
        } catch (e: Exception) {
            Log.e(TAG, "분리된 숫자 재구성 v3 중 오류", e)
            return 0
        }
    }
    
    /**
     * 사용처 이름으로 카테고리 자동 분류
     */
    private fun categorizeExpense(name: String): String {
        val nameUpper = name.uppercase()
        
        return when {
            nameUpper.contains("카페") || nameUpper.contains("커피") || nameUpper.contains("스타벅스") -> "카페"
            nameUpper.contains("식당") || nameUpper.contains("맛집") || nameUpper.contains("음식") || nameUpper.contains("치킨") -> "식비"
            nameUpper.contains("주유") || nameUpper.contains("기름") || nameUpper.contains("GS") || nameUpper.contains("SK") -> "주유"
            nameUpper.contains("렌트") || nameUpper.contains("렌탈") || nameUpper.contains("차량") -> "렌트카"
            nameUpper.contains("숙소") || nameUpper.contains("호텔") || nameUpper.contains("펜션") || nameUpper.contains("모텔") -> "숙박"
            nameUpper.contains("마트") || nameUpper.contains("편의점") || nameUpper.contains("쇼핑") -> "마트/편의점"
            nameUpper.contains("관광") || nameUpper.contains("입장") || nameUpper.contains("티켓") -> "관광/액티비티"
            nameUpper.contains("교통") || nameUpper.contains("버스") || nameUpper.contains("지하철") || nameUpper.contains("택시") -> "교통비"
            else -> "기타"
        }
    }
    
    /**
     * 중복 경비 체크 (1분 이내 같은 금액+설명)
     */
    private suspend fun checkForDuplicate(amount: Int, description: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val database = RoomDatabaseInstance.getInstance(this@OcrActivity)
                val expenseDao = database.expenseDao()
                
                // 1분 이내의 같은 금액+설명 확인
                val oneMinuteAgo = java.util.Date(System.currentTimeMillis() - 60000)
                val recentExpenses = expenseDao.getRecentExpenses(chatId ?: "", oneMinuteAgo)
                
                recentExpenses.any { 
                    it.amount == amount && it.description.equals(description, ignoreCase = true)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "중복 체크 중 오류", e)
                false
            }
        }
    }
    
    /**
     * 데이터베이스에 저장
     */
    private suspend fun saveToDatabase(amount: Int, description: String, category: String, ocrText: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val database = RoomDatabaseInstance.getInstance(this@OcrActivity)
                val expenseDao = database.expenseDao()
                val userDao = database.userDao()
                
                // 현재 사용자 정보 가져오기
                val currentUser = userDao.getUser()
                
                if (currentUser != null) {
                    // ExpenseItem 생성
                    val expenseItem = ExpenseItem(
                        id = java.util.UUID.randomUUID().toString(),
                        chatId = chatId ?: "",
                        amount = amount,
                        description = description,
                        category = category,
                        createdAt = java.util.Date(),
                        userId = currentUser.id,
                        userName = currentUser.nickname ?: "알 수 없음",
                        ocrText = ocrText
                    )
                    
                    // Room DB에 저장
                    expenseDao.insertExpense(expenseItem)
                    
                    Log.d(TAG, "✅ 경비 데이터 저장 완료: ${expenseItem.id}")
                    true
                } else {
                    Log.e(TAG, "❌ 사용자 정보가 없어서 경비 저장 실패")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 데이터베이스 저장 중 오류", e)
                false
            }
        }
    }
    
    /**
     * 채팅에 경비 메시지 전송
     */
    private fun sendExpenseMessageToChat(amount: Int, description: String, category: String) {
        Log.d(TAG, "채팅에 경비 메시지 전송")
        
        val emoji = when {
            category.contains("카페") -> "☕"
            category.contains("식비") -> "🍽️"
            category.contains("주유") -> "⛽"
            category.contains("렌트카") -> "🚙"
            category.contains("숙박") -> "🏨"
            category.contains("마트") || category.contains("편의점") -> "🛒"
            category.contains("관광") || category.contains("액티비티") -> "🎢"
            category.contains("교통비") -> "🚗"
            else -> "💰"
        }
        
        val currentTime = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.KOREA)
            .format(java.util.Date())
        
        val expenseMessage = """
            💰 여행 경비 등록
            ━━━━━━━━━━━━━━━
            💰 금액: ${String.format("%,d", amount)}원
            📍 사용처: $description
            🏷️ 카테고리: $category
            🕒 시간: $currentTime
            
            📱 영수증 OCR로 자동 분석됨
        """.trimIndent()
        
        // 기존 채팅 전송 로직 활용
        viewModel.sendCustomMessage(chatId, expenseMessage)
    }
    
    /**
     * 성공 상태 (채팅 전송 옵션 포함)
     */
    private fun showSuccessStateWithChatOption(amount: Int, description: String, category: String) {
        loadingView.visibility = View.GONE
        cameraButton.isEnabled = true
        galleryButton.isEnabled = true
        
        // 금액 요약 카드 표시
        totalAmountText.text = String.format("%,d원", amount)
        itemCountText.text = "경비 등록 완료"
        amountSummaryCard.visibility = View.VISIBLE
        statusText.text = "✅ 여행 경비 저장 및 채팅 전송 완료!"
        
        // 결과 표시
        resultText.text = """
            💸 저장된 경비 정보
            ━━━━━━━━━━━━━━━
            💰 금액: ${String.format("%,d", amount)}원
            📍 사용처: $description
            🏷️ 카테고리: $category
            
            ✅ 데이터베이스 저장 완료
            📤 채팅방 전송 완료
        """.trimIndent()
        resultScrollView.visibility = View.VISIBLE
        
        // 버튼 설정
        sendToChatButton.text = "경비 관리 보기"
        sendToChatButton.visibility = View.VISIBLE
        sendToChatButton.setOnClickListener {
            // TravelExpenseActivity로 이동
            val intent = Intent(this, com.example.andapp1.expense.TravelExpenseActivity::class.java)
            intent.putExtra("chatId", chatId)
            startActivity(intent)
        }
        
        retryButton.visibility = View.VISIBLE
    }
    
    /**
     * 성공 상태 (저장만, 뒤로가기 옵션)
     */
    private fun showSuccessStateWithBackOption(amount: Int, description: String) {
        loadingView.visibility = View.GONE
        cameraButton.isEnabled = true
        galleryButton.isEnabled = true
        
        // 금액 요약 카드 표시
        totalAmountText.text = String.format("%,d원", amount)
        itemCountText.text = "경비 저장 완료"
        amountSummaryCard.visibility = View.VISIBLE
        statusText.text = "✅ 여행 경비 저장 완료!"
        
        // 결과 표시
        resultText.text = """
            💸 저장된 경비 정보
            ━━━━━━━━━━━━━━━
            💰 금액: ${String.format("%,d", amount)}원
            📍 사용처: $description
            
            ✅ 데이터베이스 저장 완료
        """.trimIndent()
        resultScrollView.visibility = View.VISIBLE
        
        // 버튼 설정
        sendToChatButton.text = "경비 관리 보기"
        sendToChatButton.visibility = View.VISIBLE
        sendToChatButton.setOnClickListener {
            val intent = Intent(this, com.example.andapp1.expense.TravelExpenseActivity::class.java)
            intent.putExtra("chatId", chatId)
            startActivity(intent)
        }
        
        retryButton.visibility = View.VISIBLE
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OcrActivity 종료")
    }
} 