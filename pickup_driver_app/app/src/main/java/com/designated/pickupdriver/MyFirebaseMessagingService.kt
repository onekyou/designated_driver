package com.designated.pickupdriver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.designated.pickupdriver.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * pickup_driver_app FCM 서비스
 *
 * Phase 1: 사무실 단톡방 (NEW_CHAT_MESSAGE)만 처리.
 * 다른 FCM 타입(call_assigned 등)은 추후 진입 시 추가.
 */
@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var auth: FirebaseAuth

    companion object {
        private const val TAG = "PickupApp_FCM"
        const val CHAT_MESSAGE_CHANNEL_ID = "chat_messages_ptt"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val messageType = remoteMessage.data["type"] ?: run {
            Log.w(TAG, "messageType null - 처리 중단")
            return
        }

        Log.d(TAG, "수신: type=$messageType")

        when (messageType) {
            "NEW_CHAT_MESSAGE" -> handleChatMessage(remoteMessage)
            else -> Log.d(TAG, "처리 안 함: $messageType")
        }
    }

    /**
     * 사무실 단톡방 메시지 처리
     * 1) Room INSERT — Repository에서 본인 senderId면 자동 skip
     * 2) 알림 표시 — 본인 메시지면 skip (포그라운드 여부와 무관하게 항상 알림 — 픽업앱은 BottomSheet UI가 항상 보이는 게 아니므로)
     */
    private fun handleChatMessage(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val messageId = data["messageId"] ?: run {
            Log.w(TAG, "[handleChatMessage] messageId 없음")
            return
        }
        val senderId = data["senderId"] ?: return
        val senderName = data["senderName"] ?: "알 수 없음"
        val senderRole = data["senderRole"] ?: "MANAGER"
        val text = data["text"] ?: return

        Log.d(TAG, "[handleChatMessage] 수신 - id=$messageId, from=$senderName ($senderRole)")

        // 1) Room INSERT
        chatRepository.onRemoteMessageReceived(data)

        // 2) 본인 메시지면 알림 skip
        val currentUid = auth.currentUser?.uid
        if (currentUid != null && currentUid == senderId) {
            Log.d(TAG, "[handleChatMessage] 본인 메시지 - 알림 스킵")
            return
        }

        // 3) 알림 표시 (chat_messages_ptt 채널)
        val roleKorean = when (senderRole) {
            "MANAGER" -> "매니저"
            "DESIGNATED_DRIVER" -> "대리기사"
            "PICKUP_DRIVER" -> "픽업기사"
            else -> ""
        }
        val title = if (roleKorean.isNotEmpty()) "$senderName ($roleKorean)" else senderName

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            messageId.hashCode(),
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = NotificationCompat.Builder(this, CHAT_MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(messageId.hashCode(), notification)
        Log.d(TAG, "[handleChatMessage] 알림 표시 완료")
    }
}
