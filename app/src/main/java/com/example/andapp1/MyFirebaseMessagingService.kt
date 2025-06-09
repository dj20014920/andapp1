package com.example.andapp1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // 알림 채널 ID는 반드시 고유해야 함
    private val CHANNEL_ID = "chat_channel_id"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // FCM 콘솔 또는 서버에서 전송한 데이터/알림 메시지 처리
        val notification = remoteMessage.notification
        val data = remoteMessage.data

        // 1. notification 타입 메시지 처리 (기본)
        if (notification != null) {
            sendNotification(
                notification.title ?: "새 메시지",
                notification.body ?: ""
            )
        }

        // 2. data 타입 메시지에도 알림 보내고 싶으면 아래 사용
        // if (data.isNotEmpty()) {
        //     val title = data["title"] ?: "새 메시지"
        //     val message = data["body"] ?: ""
        //     sendNotification(title, message)
        // }
    }

    private fun sendNotification(title: String, message: String) {
        // 알림 클릭 시 채팅방 화면으로 이동 (예시: RoomActivity로 이동)
        val intent = Intent(this, RoomActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // 알림 생성
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // drawable에 알림 아이콘 추가 필요
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0+ NotificationChannel 필수
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "채팅 알림",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 표시
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
