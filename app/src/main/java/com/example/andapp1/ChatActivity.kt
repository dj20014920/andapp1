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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.stfalcon.chatkit.messages.*
import kotlinx.coroutines.launch
import java.io.File
import android.Manifest
import android.R.attr.bitmap
import android.R.attr.data
import android.R.id.message
import android.graphics.BitmapFactory
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.andapp1.DialogHelper.showParticipantsDialog
import com.google.firebase.database.FirebaseDatabase
import com.stfalcon.chatkit.commons.ImageLoader
import org.opencv.android.OpenCVLoader
import java.util.Date
import androidx.lifecycle.Observer
import android.content.Context
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
import com.google.firebase.storage.FirebaseStorage

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

    private fun openCameraForPhoto() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val denied = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (denied.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, denied.toTypedArray(), REQUEST_CAMERA_PERMISSION_PHOTO)
            return
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            photoSendLauncher.launch(intent)
        } else {
            Toast.makeText(this, "카메라 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CAMERA_PERMISSION_PHOTO) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                openCameraForPhoto()
            } else {
                Toast.makeText(this, "사진 촬영을 위해 카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openGalleryForPhoto() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, REQUEST_GALLERY_PHOTO)
    }

    private val photoSendLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d("PHOTO_SEND", "Photo selected from gallery or some camera apps: $uri")
                uploadImageToFirebase(uri)
            } ?: result.data?.extras?.get("data")?.let { bitmapData ->
                Log.d("PHOTO_SEND", "Photo captured as thumbnail bitmap")
                Toast.makeText(this, "사진 URI를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
            } ?: photoSendUri?.let {
                Log.d("PHOTO_SEND", "Photo captured to predefined URI: $it")
                uploadImageToFirebase(it)
                this.photoSendUri = null
            } ?: Toast.makeText(this, "사진 데이터를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("PHOTO_SEND", "Photo selection/capture failed or cancelled.")
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
            stackFromEnd = true
            reverseLayout = false
        }
        binding.messagesList.layoutManager = layoutManager
        initializeAdapterAndListeners()

        currentUser = UserEntity(id = roomCode, nickname = roomName, email = null, profileImageUrl = null)

        addBotMessage("안녕하세요! 지출 내역을 알려주시면 기록해드릴게요. 결제 문자 전체를 입력하거나 '항목 금액' 형식으로 입력해주세요.")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("ChatActivity", "onNewIntent 호출됨")

        intent?.getStringExtra("mapUrl")?.let { url ->
            Log.d("ChatActivity", "받은 지도 URL: $url")
            lastMapUrl = url
            showMapRestoreButton()
        }
        intent?.getStringExtra("scrapText")?.let { sharedMapUrl ->
            val alreadySent = intent.getBooleanExtra("alreadySent", false)
            if (!alreadySent) {
                Log.d("ChatActivity", "공유 메시지 전송: $sharedMapUrl")
                viewModel.sendMapUrlMessage(sharedMapUrl)
                intent.putExtra("alreadySent", true)
            } else {
                Log.d("ChatActivity", "이미 전송된 메시지라 무시")
            }
        }
    }

    private fun showMapRestoreButton() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val existing = rootView.findViewWithTag<FloatingActionButton>("map_restore_button")
        if (existing != null) {
            Log.d("ChatActivity", "이미 플로팅 버튼 존재 - 중복 생성 방지")
            return
        }

        val fab = FloatingActionButton(this).apply {
            tag = "map_restore_button"
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_GALLERY_PHOTO -> {
                    val clipData = data?.clipData
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            val imageUri = clipData.getItemAt(i).uri
                            uploadImageToFirebase(imageUri)
                        }
                    } else {
                        data?.data?.let { selectedImageUri ->
                            uploadImageToFirebase(selectedImageUri)
                        }
                    }
                }
            }
        }
    }

    private fun initializeAdapterAndListeners() {
        lifecycleScope.launch {
            val user = RoomDatabaseInstance
                .getInstance(applicationContext)
                .userDao()
                .getUser()
            currentUser = user

            if (user == null) {
                Toast.makeText(this@ChatActivity, "사용자 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            senderId = user.id

            val participantsRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(viewModel.roomCode)
                .child("participants")
                .child(user.id)

            participantsRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    Toast.makeText(this@ChatActivity, "이미 나간 채팅방입니다.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Log.d("ChatActivity", "참가자 확인 완료")
                }
            }

            val holders = MessageHolders()
                .setIncomingTextHolder(
                    CustomIncomingTextViewHolder::class.java,
                    R.layout.item_incoming_text_message
                )
                .setIncomingImageHolder(
                    CustomIncomingImageViewHolder::class.java,
                    R.layout.item_incoming_image_message
                )
                .setOutcomingTextHolder(
                    TextMessageViewHolder::class.java,
                    com.stfalcon.chatkit.R.layout.item_outcoming_text_message
                )
                .setOutcomingImageHolder(
                    OutcomingImageMessageViewHolder::class.java,
                    R.layout.item_outcoming_image_message
                )

            adapter = MessagesListAdapter<ChatMessage>(
                senderId,
                holders,
                ImageLoader { imageView, url, _ ->
                    if (!url.isNullOrEmpty()) {
                        Glide.with(imageView.context)
                            .load(url)
                            .error(R.drawable.ic_launcher_background)
                            .into(imageView)
                    } else {
                        imageView.setImageResource(R.drawable.ic_launcher_background)
                    }
                }
            )

            binding.messagesList.setAdapter(adapter)

            adapter.setOnMessageClickListener { message: ChatMessage ->
                val imageUrl = message.imageUrlValue
                if (!imageUrl.isNullOrEmpty()) {
                    val urls = imageMessages
                    val idx = urls.indexOf(imageUrl)
                    val photoListToSend = if (idx != -1) ArrayList(urls) else arrayListOf(imageUrl)
                    val position = if (idx != -1) idx else 0
                    val intent = Intent(this@ChatActivity, ImageViewerActivity::class.java)
                        .putStringArrayListExtra("photoList", photoListToSend)
                        .putExtra("startPosition", position)
                    startActivity(intent)
                }
            }

            binding.customMessageInput.setInputListener { input ->
                onSubmit(input)
            }

            binding.btnSendPhoto.setOnClickListener {
                val options = arrayOf("사진 촬영", "갤러리에서 선택")
                AlertDialog.Builder(this@ChatActivity)
                    .setTitle("사진 전송 방법 선택")
                    .setItems(options) { _, which ->
                        if (which == 0) openCameraForPhoto() else openGalleryForPhoto()
                    }
                    .show()
            }
            observeMessages()
        }
    }

    private fun observeMessages() {
        viewModel.messages.observe(this) { messages ->
            val sorted = messages
                .filter { it.messageId.isNotBlank() }
                .distinctBy { it.messageId }
                .sortedBy { it.createdAt.time }
                .reversed()

            adapter.setItems(sorted)

            imageMessages.clear()
            imageMessages.addAll(
                messages.filter { !it.imageUrlValue.isNullOrEmpty() }
                    .map { it.imageUrlValue!! }
            )
            ChatImageStore.imageMessages = imageMessages

            binding.messagesList.post {
                layoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_receipt_ocr -> {
                Toast.makeText(this, "텍스트로 지출 내역을 입력해주세요. 예: GS25 삼각김밥 1500원", Toast.LENGTH_LONG).show()
                true
            }
            R.id.menu_participants -> {
                DialogHelper.showParticipantsDialog(this, viewModel.roomCode)
                true
            }
            R.id.menu_open_map -> {
                val intent = Intent(this, MapActivity::class.java)
                intent.putExtra("roomCode", viewModel.roomCode)
                startActivity(intent)
                return true
            }
            R.id.menu_scrap_list -> {
                ScrapDialogHelper.showScrapListDialog(this, viewModel.roomCode)
                true
            }
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

    private fun uploadImageToFirebase(uri: Uri) {
        val fileName = "images/${System.currentTimeMillis()}.jpg"
        val storageRef = FirebaseStorage.getInstance().reference.child(fileName)

        storageRef.putFile(uri)
            .addOnSuccessListener { taskSnapshot ->
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    sendImageMessage(downloadUri.toString())
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PHOTO_UPLOAD", "Firebase 업로드 실패: ${exception.message}", exception)
                Toast.makeText(this, "사진 업로드 실패: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendChatMessage(message: String) {
        viewModel.sendMessage(message)
    }

    private fun sendImageMessage(imageUrl: String) {
        val user = currentUser ?: return
        val author = Author(
            user.id,
            user.nickname ?: "알 수 없음",
            user.profileImageUrl
        )
        val message = ChatMessage(
            messageId = "",
            text = "",
            user = author,
            imageUrlValue = imageUrl,
            createdAt = Date()
        )
        viewModel.sendMessage(message)
    }

    private fun setupMessageInput() {
        binding.customMessageInput.setInputListener { input ->
            onSubmit(input)
        }
    }

    fun onSubmit(input: CharSequence): Boolean {
        val messageText = input.toString()
        if (messageText.isBlank()) {
            return false
        }

        val user = currentUser ?: return false
        val author = Author(
            user.id,
            user.nickname ?: "알 수 없음",
            user.profileImageUrl
        )
        val userTextMessage = ChatMessage(
            messageId = "",
            text = messageText,
            user = author,
            createdAt = Date()
        )
        viewModel.sendMessage(userTextMessage)

        lifecycleScope.launch {
            val (items, total) = withContext(Dispatchers.IO) {
                com.example.andapp1.ocr.ReceiptOcrProcessor.processTextMessage(messageText)
            }

            if (items.isEmpty() && total == null) {
                addBotMessage("Could not parse expense. Use 'Item Amount' format or paste full text.")
            } else {
                val resultText = StringBuilder("Parsed Expenses:\n")
                items.forEach { (item, price) ->
                    resultText.append("- Item: $item, Price: $price\n")
                }
                total?.let {
                    resultText.append("Total: $it\n")
                }
                addBotMessage(resultText.toString().trim())
            }
        }
        return true
    }

    private fun addBotMessage(text: String) {
        val botUser = Author("BOT_ID", "지출 관리 봇", null)
        val botChatMessage = ChatMessage(
            messageId = "",
            text = text,
            user = botUser,
            createdAt = Date()
        )
        viewModel.sendMessage(botChatMessage)
    }

    companion object {
        const val REQUEST_CAMERA_PERMISSION_PHOTO = 1012
        const val REQUEST_GALLERY_PHOTO = 1003
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
        val avatarView = itemView.findViewById<ImageView>(R.id.messageUserAvatar)
        val avatarUrl = message.getUser().getAvatar()

        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(itemView.context)
                .load(avatarUrl)
                .circleCrop()
                .error(R.drawable.ic_launcher_background)
                .into(avatarView)
        } else {
            avatarView.setImageResource(R.drawable.ic_launcher_background)
        }

        val userNameView = itemView.findViewById<TextView>(R.id.messageUserName)
        val userName = message.getUser().getName()
        userNameView.text = if (userName.isNotEmpty()) userName else "알 수 없음"

        avatarView.setOnClickListener {
            showUserDetailDialog(itemView.context, message.getUser())
        }
        userNameView.setOnClickListener {
            showUserDetailDialog(itemView.context, message.getUser())
        }
    }

    private fun processMapUrls(spannable: Spannable, text: String) {
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
                val existingSpans = spannable.getSpans(start, end, ClickableSpan::class.java)
                for (span in existingSpans) {
                    spannable.removeSpan(span)
                }
                val mapClickSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        try {
                            val intent = Intent(widget.context, MapActivity::class.java)
                            intent.putExtra("mapUrl", mapUrl)
                            widget.context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("TextMessageViewHolder", "지도 액티비티 실행 실패", e)
                        }
                    }
                }
                spannable.setSpan(mapClickSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun showUserDetailDialog(context: Context, user: com.stfalcon.chatkit.commons.models.IUser) {
        val linearLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        val profileImageView = ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(300, 300).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 20
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(context, android.R.drawable.dialog_frame)
        }
        val userInfoText = TextView(context).apply {
            text = user.getName()
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        linearLayout.addView(profileImageView)
        linearLayout.addView(userInfoText)
        if (!user.getAvatar().isNullOrEmpty()) {
            Glide.with(context)
                .load(user.getAvatar())
                .centerCrop()
                .error(R.drawable.ic_launcher_background)
                .into(profileImageView)
        } else {
            profileImageView.setImageResource(R.drawable.ic_launcher_background)
        }
        AlertDialog.Builder(context)
            .setTitle("사용자 정보")
            .setView(linearLayout)
            .setPositiveButton("확인", null)
            .setNeutralButton("프로필 크게 보기") { _, _ ->
                showFullScreenImage(context, user.getAvatar())
            }
            .create()
            .show()
    }

    private fun showFullScreenImage(context: Context, imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) {
            Toast.makeText(context, "프로필 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
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
        val avatarView = itemView.findViewById<ImageView>(R.id.messageUserAvatar)
        val avatarUrl = message.getUser().getAvatar()

        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(itemView.context)
                .load(avatarUrl)
                .circleCrop()
                .error(R.drawable.ic_launcher_background)
                .into(avatarView)
        } else {
            avatarView.setImageResource(R.drawable.ic_launcher_background)
        }

        val userNameView = itemView.findViewById<TextView>(R.id.messageUserName)
        val userName = message.getUser().getName()
        userNameView.text = if (userName.isNotEmpty()) userName else "알 수 없음"

        avatarView.setOnClickListener {
            showUserDetailDialog(itemView.context, message.getUser())
        }
        userNameView.setOnClickListener {
            showUserDetailDialog(itemView.context, message.getUser())
        }

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
        val linearLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        val profileImageView = ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(300, 300).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 20
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(context, android.R.drawable.dialog_frame)
        }
        val userInfoText = TextView(context).apply {
            text = user.getName()
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        linearLayout.addView(profileImageView)
        linearLayout.addView(userInfoText)
        if (!user.getAvatar().isNullOrEmpty()) {
            Glide.with(context)
                .load(user.getAvatar())
                .centerCrop()
                .error(R.drawable.ic_launcher_background)
                .into(profileImageView)
        } else {
            profileImageView.setImageResource(R.drawable.ic_launcher_background)
        }
        AlertDialog.Builder(context)
            .setTitle("사용자 정보")
            .setView(linearLayout)
            .setPositiveButton("확인", null)
            .setNeutralButton("프로필 크게 보기") { _, _ ->
                showFullScreenImage(context, user.getAvatar())
            }
            .create()
            .show()
    }

    private fun showFullScreenImage(context: Context, imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) {
            Toast.makeText(context, "프로필 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(context, ImageViewerActivity::class.java).apply {
            putStringArrayListExtra("photoList", arrayListOf(imageUrl))
            putExtra("startPosition", 0)
        }
        context.startActivity(intent)
    }
}