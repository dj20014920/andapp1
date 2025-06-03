package com.example.andapp1

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import android.os.Handler
import android.widget.LinearLayout
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.user.UserApiClient

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KakaoSdk.init(this, getString(R.string.kakao_native_app_key))
        setContentView(R.layout.splash_activity)

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

