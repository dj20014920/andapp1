package com.example.andapp1

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.*
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.andapp1.databinding.ActivityMapBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.FirebaseDatabase
import kotlin.jvm.java
import android.widget.Button

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private var menuVisible = false
    private var menuView: View? = null
    private lateinit var fabToggle: FloatingActionButton
    private var lastLoadedUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 👉 지도 URL 복원 또는 초기화
        val restoreUrl = intent.getStringExtra("mapUrl")
        val targetUrl = restoreUrl ?: "https://m.map.naver.com/"
        Log.d("MapActivity", "🔥 로딩할 URL: $targetUrl")

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.setSupportZoom(true)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d("JSConsole", "${consoleMessage?.message()} (line: ${consoleMessage?.lineNumber()})")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    lastLoadedUrl = url
                    Log.d("MapActivity", "✅ 마지막 로딩된 URL: $url")
                }
            }
            loadUrl(targetUrl)
        }

        // 플로팅 토글 버튼
        fabToggle = binding.fabToggle
        fabToggle.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.tag = Triple(event.rawX, event.rawY, false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val (startX, startY, _) = view.tag as Triple<Float, Float, Boolean>
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    val isDragged = dx * dx + dy * dy > 100
                    if (isDragged) {
                        view.x += dx
                        view.y += dy
                        view.tag = Triple(event.rawX, event.rawY, true)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val (_, _, isDragged) = view.tag as Triple<Float, Float, Boolean>
                    if (!isDragged) showFloatingMenu()
                    true
                }
                else -> false
            }
        }

        // 웹뷰 터치 시 메뉴 닫기
        binding.webView.setOnTouchListener { _, event ->
            if (menuVisible && event.action == MotionEvent.ACTION_DOWN) {
                closeFloatingMenu()
            }
            false
        }
    }

    private fun showFloatingMenu() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        if (menuVisible) return

        val menu = layoutInflater.inflate(R.layout.fab_menu_vertical, root, false)
        menu.tag = "fab_menu"
        menu.x = fabToggle.x
        menu.y = fabToggle.y
        menuView = menu

        val fabShare = menu.findViewById<FloatingActionButton>(R.id.fab_share)
        val fabScrap = menu.findViewById<FloatingActionButton>(R.id.fab_scrap)
        val fabScrapList = menu.findViewById<FloatingActionButton>(R.id.fab_scrap_list)
        val fabBack = menu.findViewById<FloatingActionButton>(R.id.fab_back)

        fabShare.setOnClickListener { shareCurrentMapToChat() }
        fabScrap.setOnClickListener { promptScrapNameAndSave() }
        fabScrapList.setOnClickListener {
            val roomCode = intent.getStringExtra("roomCode") ?: return@setOnClickListener
            ScrapDialogHelper.showScrapListDialog(this, roomCode)
        }
        fabBack.setOnClickListener { returnToChat() }

        val fabList = listOf(fabShare, fabScrap, fabScrapList, fabBack)
        for (fab in fabList) {
            fab.alpha = 0f
            fab.translationY = -30f
        }

        root.addView(menu)
        fabToggle.visibility = View.GONE
        menuVisible = true

        for ((index, fab) in fabList.withIndex()) {
            fab.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 50).toLong())
                .setDuration(200)
                .start()
        }

        menu.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.tag = Triple(event.rawX, event.rawY, false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val (startX, startY, _) = view.tag as Triple<Float, Float, Boolean>
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (dx * dx + dy * dy > 100) {
                        view.x += dx
                        view.y += dy
                        view.tag = Triple(event.rawX, event.rawY, true)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun closeFloatingMenu() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val menu = menuView ?: return
        val fabList = listOf(
            menu.findViewById<FloatingActionButton>(R.id.fab_share),
            menu.findViewById<FloatingActionButton>(R.id.fab_scrap),
            menu.findViewById<FloatingActionButton>(R.id.fab_scrap_list),
            menu.findViewById<FloatingActionButton>(R.id.fab_back)
        )

        for ((index, fab) in fabList.withIndex()) {
            fab.animate()
                .alpha(0f)
                .translationY(-30f)
                .setDuration(150)
                .setStartDelay((index * 50).toLong())
                .withEndAction {
                    if (index == fabList.lastIndex) {
                        root.removeView(menu)
                        menuView = null
                        menuVisible = false
                        fabToggle.visibility = View.VISIBLE
                    }
                }.start()
        }
    }

    private fun returnToChat() {
        val url = binding.webView.url ?: return  // 마지막으로 본 지도 URL
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("mapUrl", url)  // 공유 버튼처럼 지도 URL 전달
        }
        startActivity(intent)
    }

    private fun shareCurrentMapToChat() {
        val url = lastLoadedUrl ?: return
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("mapUrl", url) // 지도 URL 전달
            putExtra("scrapText", url) // 추가: 메시지로 전송할 URL
        }
        startActivity(intent)
    }

    private fun promptScrapNameAndSave() {
        val roomCode = intent.getStringExtra("roomCode") ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_scrap_input, null)
        val editText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editScrapName)
        val saveButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveScrap)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        val dialog = AlertDialog.Builder(this, R.style.AppDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // 취소 버튼 클릭
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        // 저장 버튼 클릭
        saveButton.setOnClickListener {
            val name = editText.text.toString().trim()
            if (name.isEmpty()) {
                editText.error = "장소 이름을 입력해주세요"
                editText.requestFocus()
                return@setOnClickListener
            }

            // 버튼 비활성화 및 로딩 상태 표시
            saveButton.isEnabled = false
            saveButton.text = "저장 중..."

            val url = binding.webView.url ?: return@setOnClickListener
            val scrap = ScrapItem(
                name = name,
                url = url,
                thumbnailUrl = "https://example.com/default-thumbnail.png",
                description = ""
            )

            FirebaseDatabase.getInstance()
                .getReference("scraps")
                .child(roomCode)
                .push()
                .setValue(scrap)
                .addOnSuccessListener {
                    dialog.dismiss()
                    
                    // 성공 토스트 메시지
                    DialogHelper.showStyledConfirmDialog(
                        context = this,
                        title = "스크랩 완료",
                        message = "'$name'이(가) 스크랩 목록에 저장되었습니다.\n채팅방에 공유하시겠습니까?",
                        positiveText = "공유하기",
                        negativeText = "나중에",
                        onPositive = {
                            // 자동 메시지 전송
                            val messageIntent = Intent(this, ChatActivity::class.java).apply {
                                putExtra("roomCode", roomCode)
                                putExtra("mapUrl", url)
                                putExtra("scrapText", "📌 ${name}\n$url")
                                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            }
                            startActivity(messageIntent)
                        }
                    )
                }
                .addOnFailureListener { exception ->
                    // 버튼 상태 복원
                    saveButton.isEnabled = true
                    saveButton.text = "스크랩 저장"
                    
                    DialogHelper.showStyledConfirmDialog(
                        context = this,
                        title = "저장 실패",
                        message = "스크랩 저장 중 오류가 발생했습니다.\n다시 시도해주세요.",
                        positiveText = "확인"
                    )
                }
        }

        // 엔터키로 저장
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                saveButton.performClick()
                true
            } else {
                false
            }
        }

        dialog.show()
        
        // 키보드 자동 표시
        editText.requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
}