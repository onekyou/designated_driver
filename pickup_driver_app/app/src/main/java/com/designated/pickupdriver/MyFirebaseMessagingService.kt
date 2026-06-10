package com.designated.pickupdriver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.designated.pickupdriver.data.Constants
import com.designated.pickupdriver.data.repository.CallRepository
import com.designated.pickupdriver.data.repository.ChatRepository
import com.designated.pickupdriver.service.PttReceiverService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * pickup_driver_app FCM 서비스
 *
 * 처리 타입:
 *  - NEW_CHAT_MESSAGE: 사무실 단톡방
 *  - NEW_CALL: 신규 콜 → Room INSERT + 시스템 알림
 *  - CALL_STATUS_UPDATE: 콜 상태 변경 → Room UPDATE + 시스템 알림
 *  - 그 외(call_assigned 등): 무시 (else 분기)
 */
@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var callRepository: CallRepository
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var firestore: FirebaseFirestore

    companion object {
        private const val TAG = "PickupApp_FCM"
        const val CHAT_MESSAGE_CHANNEL_ID = "chat_messages_ptt"
    }

    /**
     * FCM 토큰 갱신 시 pickup_drivers/{uid}.fcmToken 동기화.
     * 로그인된 상태에서만 동작 — 로그인 전이면 LoginViewModel이 로그인 직후 fetch+저장.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = auth.currentUser?.uid ?: run {
            Log.d(TAG, "[onNewToken] 미로그인 상태 - 토큰 저장 스킵")
            return
        }
        Log.d(TAG, "[onNewToken] uid=$uid 토큰 동기화 시작")
        firestore.collectionGroup(Constants.COLLECTION_GROUP_PICKUP_DRIVERS)
            .whereEqualTo(Constants.FIELD_AUTH_UID, uid)
            .limit(1)
            .get()
            .addOnSuccessListener { qs ->
                val doc = qs.documents.firstOrNull() ?: run {
                    Log.w(TAG, "[onNewToken] pickup_drivers 문서 못 찾음 (uid=$uid)")
                    return@addOnSuccessListener
                }
                doc.reference.update(Constants.FIELD_FCM_TOKEN, token)
                    .addOnSuccessListener { Log.d(TAG, "[onNewToken] fcmToken 업데이트 성공") }
                    .addOnFailureListener { e -> Log.e(TAG, "[onNewToken] fcmToken 업데이트 실패", e) }
            }
            .addOnFailureListener { e -> Log.e(TAG, "[onNewToken] pickup_drivers 조회 실패", e) }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val messageType = remoteMessage.data["type"] ?: run {
            Log.w(TAG, "messageType null - 처리 중단")
            return
        }

        Log.d(TAG, "수신: type=$messageType")

        when (messageType) {
            Constants.MSG_TYPE_NEW_CHAT_MESSAGE -> handleChatMessage(remoteMessage)
            Constants.MSG_TYPE_NEW_CALL -> handleNewCall(remoteMessage)
            Constants.MSG_TYPE_CALL_STATUS_UPDATE -> handleCallStatusUpdate(remoteMessage)
            "ptt_dispatch" -> {
                // PTT 라이브 수신 wake — 수신 FGS로 위임(화면 off에서도 join).
                val channelName = remoteMessage.data["channelName"] ?: ""
                val senderName = remoteMessage.data["senderName"] ?: "매니저"
                ContextCompat.startForegroundService(
                    this, PttReceiverService.newPttDispatchIntent(this, channelName, senderName)
                )
            }
            "ptt_prewake" -> Log.d(TAG, "PTT pre-wake 수신 (no-op)")
            else -> Log.d(TAG, "처리 안 함: $messageType")
        }
    }

    /**
     * 신규 콜 (NEW_CALL) 처리
     *  1) WAITING 30분 컷오프: stuck 콜은 Room/알림 둘 다 skip (대시보드 일관성)
     *  2) Room upsert (CallRepository)
     *  3) 시스템 알림 (PickupDriverApplication.CHANNEL_CALL_CHANGES)
     *
     * fromCallDetector / fromCallManager 플래그 무시 — 픽업앱은 콜을 만든 게 아니라 모든 NEW_CALL 알림 발사.
     */
    private fun handleNewCall(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val callId = data["callId"] ?: run {
            Log.w(TAG, "[handleNewCall] callId 없음")
            return
        }
        val status = data["status"] ?: Constants.STATUS_WAITING
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

        // WAITING 30분 컷오프 (FCM 측)
        val age = System.currentTimeMillis() - timestamp
        if (status == Constants.STATUS_WAITING && age > Constants.WAITING_CUTOFF_MS) {
            Log.d(TAG, "[handleNewCall] WAITING 30분+ 경과 → skip: $callId (age=${age}ms)")
            return
        }

        Log.d(TAG, "[handleNewCall] callId=$callId status=$status")

        // 1) Room upsert
        callRepository.insertCallFromFCM(data)

        // 2) 시스템 알림
        val title = "🔔 새 콜 접수"
        val body = buildCallBody(data)
        notifyCallChange(callId, status, title, body)
    }

    /**
     * 콜 상태 변경 (CALL_STATUS_UPDATE) 처리
     */
    private fun handleCallStatusUpdate(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val callId = data["callId"] ?: run {
            Log.w(TAG, "[handleCallStatusUpdate] callId 없음")
            return
        }
        val status = data["status"] ?: run {
            Log.w(TAG, "[handleCallStatusUpdate] status 없음")
            return
        }

        Log.d(TAG, "[handleCallStatusUpdate] callId=$callId status=$status")

        // 1) Room UPDATE
        callRepository.updateCallStatusFromFCM(data)

        // 2) 시스템 알림 (status 라벨)
        val title = statusLabel(status)
        val body = buildCallBody(data)
        notifyCallChange(callId, status, title, body)
    }

    private fun statusLabel(status: String): String = when (status) {
        Constants.STATUS_WAITING -> "대기"
        Constants.STATUS_ASSIGNED -> "배차됨"
        Constants.STATUS_ACCEPTED -> "수락"
        Constants.STATUS_IN_PROGRESS -> "운행중"
        Constants.STATUS_AWAITING_SETTLEMENT -> "정산대기"
        else -> status
    }

    private fun buildCallBody(data: Map<String, String>): String {
        val departure = data["departure"]?.takeIf { it.isNotBlank() }
        val destination = data["destination"]?.takeIf { it.isNotBlank() }
        val customerAddress = data["customerAddress"]?.takeIf { it.isNotBlank() }
            ?: data["pickupLocation"]?.takeIf { it.isNotBlank() }
        return when {
            departure != null && destination != null -> "$departure → $destination"
            departure != null -> departure
            destination != null -> "→ $destination"
            customerAddress != null -> customerAddress
            else -> "출발지 정보 없음"
        }
    }

    private fun notifyCallChange(callId: String, status: String, title: String, body: String) {
        // 알림 ID를 callId+status 조합으로 분리 — 매 상태 전이마다 새 알림 + sound 재생
        // (callId.hashCode() 단독이면 같은 ID로 update 처리 → 두 번째부터 sound 무음)
        val notifId = "$callId-$status".hashCode()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notifId,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = NotificationCompat.Builder(
            this, PickupDriverApplication.CHANNEL_CALL_CHANGES
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notifId, notification)
            Log.d(TAG, "[notifyCallChange] $callId / $status / $title")
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS 권한 없음 — 알림 skip", e)
        }
    }

    /**
     * 사무실 단톡방 메시지 처리
     * 1) Room INSERT — Repository에서 본인 senderId면 자동 skip
     * 2) 본인 메시지면 알림 skip
     * 3) 포그라운드면 시스템 알림 skip + ptt_start 사운드만 재생 (BottomSheet UI가 처리)
     * 4) 백그라운드면 시스템 알림 표시 (chat_messages_ptt 채널)
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
        val text = data["text"] ?: ""
        val audioUrl = data["audioUrl"]?.takeIf { it.isNotEmpty() }
        val audioAutoplay = data["audioAutoplay"] == "true"
        val displayText = if (audioUrl != null) "[음성]" else text

        Log.d(TAG, "[handleChatMessage] 수신 - id=$messageId, from=$senderName ($senderRole)" +
            if (audioUrl != null) " (audio)" else "")

        // 1) Room INSERT
        chatRepository.onRemoteMessageReceived(data)

        // 블랙박스 9-B: 시스템 이벤트는 무음 — Room INSERT만 하고 소리·알림 없이 종료
        if ((data["chatType"] ?: "") == "system") {
            Log.d(TAG, "[handleChatMessage] 시스템 이벤트 - 무음 INSERT (id=$messageId)")
            return
        }

        // 2) 본인 메시지면 알림 skip
        val currentUid = auth.currentUser?.uid
        if (currentUid != null && currentUid == senderId) {
            Log.d(TAG, "[handleChatMessage] 본인 메시지 - 알림 스킵")
            return
        }

        // 2.5) PTT 콜드 음성 메모 자동재생 (운전 중이라 포/백 무관). 자동재생이 곧 알림.
        if (audioUrl != null && audioAutoplay) {
            Log.d(TAG, "[handleChatMessage] 음성 메모 자동재생 트리거")
            PttReceiverService.playVoiceMemo(this, audioUrl)
        }

        // 3) 포그라운드면 시스템 알림 skip, sound만 재생 (BottomSheet UI가 처리)
        if (isAppInForeground()) {
            Log.d(TAG, "[handleChatMessage] 포그라운드 - 알림 skip")
            if (audioUrl == null) playChatSound() // 음성은 자동재생이 알림 역할
            return
        }

        // 4) 백그라운드 알림 표시 (chat_messages_ptt 채널)
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
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
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

    private fun isAppInForeground(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val appProcesses = activityManager.runningAppProcesses ?: return false
            val pkg = packageName
            appProcesses.any {
                it.processName == pkg &&
                    it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        } catch (e: Exception) {
            Log.w(TAG, "[isAppInForeground] 체크 실패 — 백그라운드로 간주", e)
            false
        }
    }

    private fun playChatSound() {
        try {
            val uri = android.net.Uri.parse("android.resource://$packageName/${R.raw.ptt_start}")
            val ringtone = android.media.RingtoneManager.getRingtone(this, uri)
            // 채널 sound와 동일 stream/처리로 통일 — 콜 알림과 같은 음감
            ringtone?.audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .build()
            ringtone?.play()
        } catch (e: Exception) {
            Log.w(TAG, "[playChatSound] ptt_start 재생 실패", e)
        }
    }
}
