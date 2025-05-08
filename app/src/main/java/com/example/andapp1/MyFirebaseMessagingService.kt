package com.example.andapp1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Firebase 메시지 수신 처리
        remoteMessage.notification?.let {
            sendNotification(it.title ?: "알림", it.body ?: "")
        }
    }

    fun checkInactiveRooms(context: Context) {
        val roomsRef = FirebaseDatabase.getInstance().getReference("rooms")
        val now = System.currentTimeMillis()

        roomsRef.get().addOnSuccessListener { snapshot ->
            for (roomSnapshot in snapshot.children) {
                val roomCode = roomSnapshot.key ?: continue
                val lastTimeStr = roomSnapshot.child("lastActivityTime").getValue(String::class.java) ?: continue

                val lastTime = try {
                    Util.parseTimestampToMillis(lastTimeStr)
                } catch (e: Exception) {
                    continue
                }

                val daysInactive = (now - lastTime) / (1000 * 60 * 60 * 24)

                when {
                    daysInactive == 6L -> {
                        // 🔔 6일차 → 참여자에게 알림
                        val participants = roomSnapshot.child("participants").children
                        for (participant in participants) {
                            val userId = participant.key ?: continue
                            sendDeletionWarningFCM(userId, roomCode)
                        }
                    }
                    daysInactive >= 7L -> {
                        // 🗑️ 7일차 이상 → Firebase에서 삭제
                        FirebaseDatabase.getInstance().getReference("rooms").child(roomCode).removeValue()
                        FirebaseDatabase.getInstance().getReference("messages").child(roomCode).removeValue()
                        FirebaseDatabase.getInstance().getReference("scraps").child(roomCode).removeValue()
                        Log.d("RoomCleanup", "🔥 $roomCode → 7일 경과로 삭제됨")
                    }
                }
            }
        }
    }
    fun sendDeletionWarningFCM(userId: String, roomCode: String) {
        // 💡 userId를 통해 FCM 토큰 조회 후 푸시 전송
        // 이 예시는 Firebase Functions 또는 DB에서 토큰을 관리한다고 가정
        // 실서비스에서는 서버 or Functions에서 처리 필요

        val tokenRef = FirebaseDatabase.getInstance().getReference("userTokens").child(userId)
        tokenRef.get().addOnSuccessListener { snapshot ->
            val token = snapshot.getValue(String::class.java) ?: return@addOnSuccessListener

            val message = mapOf(
                "to" to token,
                "notification" to mapOf(
                    "title" to "채팅방 삭제 예정",
                    "body" to "방 [$roomCode]이 24시간 후 삭제됩니다. 활동이 필요해요!"
                )
            )

            // 이 아래는 FCM HTTP 호출 예시 - 앱에서는 직접 전송 불가 (서버 or Functions 필요)
            Log.d("FCM", "🔔 알림 전송 준비됨 (userId=$userId, token=$token)")
        }
    }
    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "chat_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8 이상은 채널 필수
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "채팅 알림",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}