package com.example.andapp1

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.andapp1.databinding.ActivityMapBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw()
        }

        val roomCode = intent.getStringExtra("roomCode")
        // ✅ 키 값을 "mapUrl"로 맞춰줌
        val restoreUrl = intent.getStringExtra("mapUrl")
        val targetUrl = restoreUrl ?: "https://m.map.naver.com/"
        Log.d("MapActivity", "🔥 로딩할 URL: $targetUrl")

        binding.webView.apply {
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d("JSConsole", "${consoleMessage?.message()} (line: ${consoleMessage?.lineNumber()})")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d("MapActivity", "✅ WebView 최종 로딩 완료: $url")

                    restoreUrl?.let {
                        Regex("""\?c=([0-9.]+),([0-9.]+),([0-9.]+)""").find(it)?.let { match ->
                            val (lng, lat, zoom) = match.destructured
                            tryRestoreMapLocation(lat, lng, zoom) // 💡 이미 아래에 정의한 함수
                        }
                    }
                }
            }

            settings.javaScriptEnabled = true
            settings.setSupportZoom(true)
            loadUrl(targetUrl)
        }
        //플로트 버튼 구현 드래그,클릭 구분완료
        binding.btnReturnToApp.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.tag = Triple(event.rawX, event.rawY, false) // (x, y, isDragged=false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val (startX, startY, _) = view.tag as Triple<Float, Float, Boolean>
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY

                    // 일정 거리 이상 움직이면 드래그로 판단
                    val isDragged = dx * dx + dy * dy > 100 // 10px 이상 움직임

                    if (isDragged) {
                        view.x += dx
                        view.y += dy
                        view.tag = Triple(event.rawX, event.rawY, true) // isDragged = true
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val (_, _, isDragged) = view.tag as Triple<Float, Float, Boolean>
                    if (!isDragged) {
                        Log.d("MapActivity", "🧭 앱으로 돌아가기 버튼 클릭됨")

                        // JavaScript 실행해서 중심 좌표와 줌 레벨 가져오기
                        binding.webView.evaluateJavascript(
                            """
            (function() {
                if (typeof map !== 'undefined') {
                    var center = map.getCenter();
                    var zoom = map.getZoom();
                    return center.lat() + "," + center.lng() + "," + zoom;
                } else {
                    return "";
                }
            })();
            """
                        ) { result ->
                            val clean = result.replace("\"", "") // 예: "37.56,126.97,18"
                            Log.d("MapActivity", "📍 지도 좌표 반환: $clean")

                            val parts = clean.split(",")
                            val mapUrl = if (parts.size == 3) {
                                val lat = parts[0]
                                val lng = parts[1]
                                val zoom = parts[2]
                                // ✅ 중심좌표 기반 네이버맵 URL 생성
                                "https://m.map.naver.com/?c=${lng},${lat},${zoom},0,0,0,0"
                            } else {
                                "https://m.map.naver.com/"
                            }

                            Log.d("MapActivity", "📍 전달할 URL: $mapUrl")

                            val intent = Intent(this, ChatActivity::class.java).apply {
                                putExtra("mapUrl", mapUrl)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun getMapCenterUrl(callback: (String) -> Unit) {
        binding.webView.evaluateJavascript(
            """
        (function() {
            try {
                var map = window.__naverMap__; 
                if (!map) return null;
                var center = map.getCenter();
                var zoom = map.getZoom();
                return center.lat + "," + center.lng + "," + zoom;
            } catch(e) {
                return null;
            }
        })();
        """.trimIndent()
        ) { result ->
            Log.d("MapActivity", "📍 JS 반환값: $result")
            val cleaned = result.replace("\"", "")
            val parts = cleaned.split(",")
            if (parts.size == 3) {
                val lat = parts[0]
                val lng = parts[1]
                val zoom = parts[2]
                val newUrl = "https://m.map.naver.com/?c=${lng},${lat},${zoom},0,0,0,0"
                callback(newUrl)
            } else {
                callback("https://m.map.naver.com/")
            }
        }
    }
    // TODO: 추후 BottomSheetDialog로 메뉴 구성
    private fun showMapMenuDialog() {
        // 임시: 토스트로 확인
        Toast.makeText(this, "메뉴 버튼 클릭됨", Toast.LENGTH_SHORT).show()
    }

    // WebView 전체 캡처 함수 (3단계에서 사용)
    fun captureWebView(): Bitmap {
        val width = binding.webView.width
        val height = binding.webView.contentHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        binding.webView.draw(canvas)
        return bitmap
    }
    // 클래스 내부 가장 아래쪽에 추가해줘 (MapActivity 클래스의 마지막 부분)
    private fun tryRestoreMapLocation(lat: String, lng: String, zoom: String, retry: Int = 0) {
        if (retry >= 10) {
            Log.w("MapActivity", "❌ 복원 실패: map 객체 없음")
            return
        }

        val js = """
        (function() {
            try {
                if (typeof map !== 'undefined' && map.setCenter) {
                    var center = new naver.maps.LatLng($lat, $lng);
                    map.setCenter(center);
                    map.setZoom($zoom);
                    console.log("✅ map 중심 복원 완료");
                    return "ok";
                } else {
                    console.log("⚠️ map 객체 아직 없음 또는 setCenter 없음");
                    return "retry";
                }
            } catch (e) {
                console.log("❌ JS 오류: " + e.message);
                return "retry";
            }
        })();
    """.trimIndent()

        binding.webView.evaluateJavascript(js) { result ->
            Log.d("MapActivity", "📍 JS 복원 결과: $result")
            if (result.contains("retry")) {
                binding.webView.postDelayed({
                    tryRestoreMapLocation(lat, lng, zoom, retry + 1)
                }, 800)
            }
        }
    }
}