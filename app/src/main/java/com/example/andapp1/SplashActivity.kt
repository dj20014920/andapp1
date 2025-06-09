package com.example.andapp1

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import android.os.Handler
import android.widget.LinearLayout
import android.widget.TextView
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.user.UserApiClient

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KakaoSdk.init(this, getString(R.string.kakao_native_app_key))
        setContentView(R.layout.splash_activity)

        val appTitle = findViewById<TextView>(R.id.tvAppTitle)
        val slogan = findViewById<TextView>(R.id.tvSlogan)
        appTitle.alpha = 0f
        slogan.alpha = 0f

        // 2. Fade-in 애니메이션 (2초동안 0→2)
        ObjectAnimator.ofFloat(appTitle, "alpha", 0f, 1f).apply {
            duration = 2000 // 2초
            start()
        }

        appTitle.postDelayed({
            ObjectAnimator.ofFloat(slogan, "alpha", 0f, 1f).apply {
                duration = 1000 // 1초
                start()
            }
        }, 1000) // 1초 후 실행

        // 화면 전체 터치 시 LoginActivity로 이동
        val rootView = findViewById<LinearLayout>(R.id.splashRoot)
        rootView.setOnClickListener {
            checkAutoLogin()
        }
    }
    private fun checkAutoLogin() {
        // 카카오톡 자동 로그인/토큰 체크
        UserApiClient.instance.accessTokenInfo { tokenInfo, error ->
            if (error != null) {
                // 토큰이 없거나 만료: 로그인 화면으로 이동
                startActivity(Intent(this, LoginActivity::class.java))
            } else if (tokenInfo != null) {
                // 토큰 유효: 바로 메인화면으로 이동
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }
}

