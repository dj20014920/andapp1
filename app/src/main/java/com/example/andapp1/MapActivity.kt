package com.example.andapp1

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.Animation
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
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat.startActivity

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
                    if (!isDragged) {
                        // 👉 여기에 메뉴 열기 추가!
                        showFloatingMenu()
                    }
                    true
                }
                else -> false
            }
        }
        // WebView 설정
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
                    restoreUrl?.let {
                        Regex("""\?c=([0-9.]+),([0-9.]+),([0-9.]+)""").find(it)?.let { match ->
                            val (lng, lat, zoom) = match.destructured
                            tryRestoreMapLocation(lat, lng, zoom)
                        }
                    }
                }
            }
            loadUrl(targetUrl)
        }

        // 웹뷰 외부 터치 시 메뉴 닫기
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

        // 메뉴 버튼 동작 연결
        val fabShare = menu.findViewById<FloatingActionButton>(R.id.fab_share)
        val fabScrap = menu.findViewById<FloatingActionButton>(R.id.fab_scrap)
        val fabBack = menu.findViewById<FloatingActionButton>(R.id.fab_back)

        fabShare.setOnClickListener { shareCurrentMapToChat() }
        fabScrap.setOnClickListener { promptScrapNameAndSave() }
        fabBack.setOnClickListener { returnToChat() }

        // 초기 상태를 살짝 위에 & 투명하게
        val fabList = listOf(fabShare, fabScrap, fabBack)
        for (fab in fabList) {
            fab.alpha = 0f
            fab.translationY = -30f
        }

        root.addView(menu)
        fabToggle.visibility = View.GONE
        menuVisible = true

        // 부드럽게 위에서 아래로 등장
        for ((index, fab) in fabList.withIndex()) {
            fab.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 50).toLong()) // 순차적으로 나타남
                .setDuration(200)
                .start()
        }

        // 드래그 기능도 유지
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
                    val isDragged = dx * dx + dy * dy > 100
                    if (isDragged) {
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
            menu.findViewById<FloatingActionButton>(R.id.fab_back)
        )

        // 아래에서 위로 사라지도록 딜레이와 함께 애니메이션
        for ((index, fab) in fabList.withIndex()) {
            fab.animate()
                .alpha(0f)
                .translationY(-30f)
                .setDuration(150)
                .setStartDelay((index * 50).toLong())
                .withEndAction {
                    if (index == fabList.lastIndex) {
                        // 마지막 FAB까지 끝나면 메뉴 제거
                        root.removeView(menu)
                        menuView = null
                        menuVisible = false
                        fabToggle.visibility = View.VISIBLE
                    }
                }.start()
        }
    }

    private fun shareCurrentMapToChat() {
        val url = binding.webView.url ?: return
        val bitmap = captureWebView()
        Toast.makeText(this, "🖼️ 이미지 & 링크 전송 준비 중: $url", Toast.LENGTH_SHORT).show()
        // TODO: FirebaseStorage 업로드 및 채팅 전송 로직 작성
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

    private fun returnToChat() {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("mapUrl", binding.webView.url)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun captureWebView(): Bitmap {
        val width = binding.webView.width
        val height = binding.webView.contentHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        binding.webView.draw(canvas)
        return bitmap
    }

    private fun tryRestoreMapLocation(lat: String, lng: String, zoom: String, retry: Int = 0) {
        if (retry >= 10) return
        val js = """
            (function() {
                if (typeof map !== 'undefined' && map.setCenter) {
                    map.setCenter(new naver.maps.LatLng($lat, $lng));
                    map.setZoom($zoom);
                    return "ok";
                } else {
                    return "retry";
                }
            })();
        """.trimIndent()

        binding.webView.evaluateJavascript(js) { result ->
            if (result.contains("retry")) {
                binding.webView.postDelayed({
                    tryRestoreMapLocation(lat, lng, zoom, retry + 1)
                }, 800)
            }
        }
    }
}