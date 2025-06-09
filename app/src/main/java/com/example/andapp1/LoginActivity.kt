package com.example.andapp1

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.lifecycleScope
import com.example.andapp1.MainActivity
import com.example.andapp1.databinding.ActivityLoginBinding
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

// LoginActivity.kt
class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 카카오 로그인 초기화
        KakaoSdk.init(this, getString(R.string.kakao_native_app_key))
        printKeyHash(this) // 👈 여기서 호출

        binding.kakaoLoginButton.setOnClickListener {
            Log.d("Login", "카카오 로그인 버튼 클릭됨")
            if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
                UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                    if (token != null) {
                        Log.d("Login", "토큰 받음 ✅: ${token.accessToken}")
                        fetchKakaoUserInfo()
                    } else {
                        Log.e("Login", "카카오톡 로그인 실패: ${error?.message}")
                    }
                }
            } else {
                UserApiClient.instance.loginWithKakaoAccount(this) { token, error ->
                    if (error != null) {
                        Log.e("Login", "카카오 계정 로그인 실패 ❌", error)
                        Toast.makeText(this, "로그인 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                    } else if (token != null) {
                        Log.d("Login", "카카오 계정 로그인 성공 ✅: ${token.accessToken}")
                        fetchKakaoUserInfo()
                    } else {
                        Log.e("Login", "로그인 실패: token도 error도 null임 ❗")
                        Toast.makeText(this, "알 수 없는 로그인 오류 발생", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    fun printKeyHash(context: Context) {
        try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )

            info.signatures?.let { signatures ->
                for (signature in signatures) {
                    val md = MessageDigest.getInstance("SHA")
                    md.update(signature.toByteArray())
                    val hashKey = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                    Log.d("HashKey", "keyhash: $hashKey")
                }
            }
        } catch (e: Exception) {
            Log.e("HashKey", "Error printing KeyHash: ${e.message}")
        }
    }
    private fun fetchKakaoUserInfo() {
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.e("Login", "사용자 정보 불러오기 실패 ❌: ${error.message}")
            }
            if (user != null) {
                Log.d("Login", "사용자 정보 불러오기 성공 ✅: ${user.kakaoAccount?.profile?.nickname}")
                val userId = user.id.toString()
                val nickname = user.kakaoAccount?.profile?.nickname ?: ""
                val email = user.kakaoAccount?.email ?: ""
                val profileImageUrl = user.kakaoAccount?.profile?.profileImageUrl
                saveUserToFirebaseAndRoom(userId, nickname, email, profileImageUrl)

                // 자동 로그인용 사용자 ID를 저장
                val prefs = getSharedPreferences("login", MODE_PRIVATE)
                prefs.edit().putString("userId", userId).apply()

                // MainActivity로 이동
                Log.d("Login", "MainActivity로 이동합니다.")
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                Log.e("Login", "user가 null임 ❗")
                Toast.makeText(this, "로그인에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun saveUserToFirebaseAndRoom(
        userId: String,
        nickname: String?,
        email: String?,
        profileImageUrl: String?)
    {
        // 1. Firebase 저장
        val user = User(userId, nickname, email, profileImageUrl)
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        userRef.setValue(user)
            .addOnSuccessListener {
                Log.d("FirebaseUser", "Firebase에 사용자 저장 완료 ✅")
                Log.d("Login", "nickname = $nickname")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseUser", "Firebase 저장 실패 ❌", e)
            }

        // 2. Room 저장
        val userEntity = UserEntity(userId, nickname, email, profileImageUrl)
        val userDao = RoomDatabaseInstance.getInstance(applicationContext).userDao()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                userDao.insertUser(userEntity)
            }
            Log.d("RoomUser", "Room에 사용자 저장 완료 ✅")
        }
    }
}