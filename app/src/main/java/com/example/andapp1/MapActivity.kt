package com.example.andapp1

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private var menuVisible = false
    private var menuView: View? = null
    private lateinit var fabToggle: FloatingActionButton

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
            webViewClient = WebViewClient()
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
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) // 웹뷰 상태 유지
        }
        startActivity(intent)
    }

    // MapActivity.kt
    private fun shareCurrentMapToChat() {
        val url = binding.webView.url ?: return
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("mapUrl", url) // ✅ 지도 URL 전달
        }
        startActivity(intent)
    }

    private fun promptScrapNameAndSave() {
        val editText = EditText(this)
        editText.hint = "장소 이름 입력"
        val roomCode = intent.getStringExtra("roomCode") ?: return

        AlertDialog.Builder(this)
            .setTitle("스크랩 이름")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val name = editText.text.toString().trim()
                val url = binding.webView.url ?: return@setPositiveButton
                val scrap = ScrapItem(name, url)

                FirebaseDatabase.getInstance()
                    .getReference("scraps")
                    .child(roomCode)
                    .push()
                    .setValue(scrap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "✅ 스크랩 저장됨", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "❌ 저장 실패", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}