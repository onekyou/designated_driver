package com.designated.driverapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.designated.driverapp.data.Constants
import com.designated.driverapp.data.repository.ChatRepository
import com.designated.driverapp.service.DriverForegroundService
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMsgService"

    @Inject lateinit var chatRepository: ChatRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            return
        }

        val sharedPreferences = applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val provinceId = sharedPreferences.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = sharedPreferences.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)

        if (userId.isNotBlank() && !provinceId.isNullOrBlank() && !cityId.isNullOrBlank() && !officeId.isNullOrBlank()) {
            // pending 상태로 토큰 저장 (실패 대비)
            sharedPreferences.edit()
                .putString(Constants.PREF_KEY_PENDING_FCM_TOKEN, token)
                .apply()

            val db = Firebase.firestore
            val driverRef = db.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                .collection(Constants.COLLECTION_CITIES).document(cityId)
                .collection(Constants.COLLECTION_OFFICES).document(officeId)
                .collection(Constants.COLLECTION_DRIVERS).document(userId)

            driverRef.update(mapOf(
                Constants.FIELD_FCM_TOKEN to token,
                Constants.FIELD_FCM_TOKEN_PLATFORM to Constants.PLATFORM_ANDROID,
                Constants.FIELD_PLATFORM to Constants.PLATFORM_ANDROID
            ))
                .addOnSuccessListener {
                    // 성공 시 pending 토큰 제거
                    sharedPreferences.edit()
                        .remove(Constants.PREF_KEY_PENDING_FCM_TOKEN)
                        .apply()
                    Log.d(TAG, "FCM 토큰 서버 저장 성공")
                }
                .addOnFailureListener { e ->
                    // 실패 시 pending 토큰 유지 (앱 시작 시 재시도)
                    Log.e(TAG, "FCM 토큰 서버 저장 실패 - 앱 재시작 시 재시도", e)
                }
        }
    }

    companion object {
        private const val TAG_STATIC = "MyFirebaseMsgService"

        /**
         * 앱 시작 시 pending FCM 토큰 재시도
         * MainActivity에서 호출
         */
        fun retryPendingFcmToken(context: Context) {
            val sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val pendingToken = sharedPreferences.getString(Constants.PREF_KEY_PENDING_FCM_TOKEN, null)

            if (!pendingToken.isNullOrBlank()) {
                Log.d(TAG_STATIC, "Pending FCM 토큰 발견 - 재시도: $pendingToken")

                val userId = Firebase.auth.currentUser?.uid ?: return
                val provinceId = sharedPreferences.getString(Constants.PREF_KEY_PROVINCE_ID, null)
                val cityId = sharedPreferences.getString(Constants.PREF_KEY_CITY_ID, null)
                val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)

                if (!provinceId.isNullOrBlank() && !cityId.isNullOrBlank() && !officeId.isNullOrBlank()) {
                    val db = Firebase.firestore
                    val driverRef = db.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                        .collection(Constants.COLLECTION_CITIES).document(cityId)
                        .collection(Constants.COLLECTION_OFFICES).document(officeId)
                        .collection(Constants.COLLECTION_DRIVERS).document(userId)

                    driverRef.update(mapOf(
                        Constants.FIELD_FCM_TOKEN to pendingToken,
                        Constants.FIELD_FCM_TOKEN_PLATFORM to Constants.PLATFORM_ANDROID,
                        Constants.FIELD_PLATFORM to Constants.PLATFORM_ANDROID
                    ))
                        .addOnSuccessListener {
                            sharedPreferences.edit()
                                .remove(Constants.PREF_KEY_PENDING_FCM_TOKEN)
                                .apply()
                            Log.d(TAG_STATIC, "Pending FCM 토큰 서버 저장 성공")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG_STATIC, "Pending FCM 토큰 재시도 실패", e)
                        }
                }
            }
        }

        /**
         * 로그아웃 시 pending FCM 토큰 제거
         */
        fun clearPendingFcmToken(context: Context) {
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(Constants.PREF_KEY_PENDING_FCM_TOKEN)
                .apply()
            Log.d(TAG_STATIC, "Pending FCM 토큰 제거됨")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM 메시지 수신: ${remoteMessage.data}")

        // ✅ 로그인 체크: SharedPreferences로 확인 (백그라운드에서도 안정적)
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val officeId = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)
        if (officeId.isNullOrBlank()) {
            Log.d(TAG, "로그인하지 않은 상태 (officeId 없음) - FCM 메시지 무시")
            return
        }

        // data 페이로드에서 먼저 확인, 없으면 notification에서, 그래도 없으면 기본값
        val title = remoteMessage.data["title"]
            ?: remoteMessage.notification?.title
            ?: "콜 배정 알림"
        val body = remoteMessage.data["body"]
            ?: remoteMessage.notification?.body
            ?: "새로운 콜이 배정되었습니다."
        val callId = remoteMessage.data["callId"]

        Log.d(TAG, "callId: $callId, title: $title, officeId: $officeId")

        // 메시지 타입 확인
        val messageType = remoteMessage.data["type"] ?: ""
        val notificationId = remoteMessage.data["notificationId"]

        if (!callId.isNullOrBlank() && messageType == "call_assigned") {
            // 도착 ACK 전송 (알림 수신 확인)
            if (!notificationId.isNullOrBlank()) {
                sendDeliveryAck(notificationId)
            }

            // DriverForegroundService에 콜 정보 전달
            Log.d(TAG, "DriverForegroundService에 콜 정보 전달: $callId")
            val serviceIntent = DriverForegroundService.newCallAssignedIntent(this, callId, title, body)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            if (isAppInForeground()) {
                // 포그라운드: LocalBroadcast로 앱 내 다이얼로그만 표시 (알림 X)
                Log.d(TAG, "앱이 포그라운드 - LocalBroadcast로 callId 전달 (알림 없음): $callId")
                val broadcastIntent = Intent(Constants.ACTION_SHOW_CALL_DIALOG).apply {
                    putExtra("callId", callId)
                    putExtra("title", title)
                    putExtra("body", body)
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
            } else {
                // 백그라운드: 시스템 알림 표시
                Log.d(TAG, "앱이 백그라운드 - 시스템 알림 표시: $callId")
                showNotification(title, body, callId)
            }
        } else if (!callId.isNullOrBlank() && messageType == "call_reserved") {
            // 신규콜 예약 (RESERVED) — 운행중 기사에게 다음 콜 약속.
            // 가벼운 알림 (FullScreenIntent X, DriverForegroundService 추가 시작 X). 포그라운드/백그라운드 모두 표시.
            Log.d(TAG, "예약 콜 FCM 수신: callId=$callId")

            // 1) LocalBroadcast — UI(HomeScreen) 가 reloadReservedCall 호출해 reservedCard 갱신
            val broadcastIntent = Intent(Constants.ACTION_RESERVATION_RECEIVED).apply {
                putExtra("callId", callId)
                putExtra("title", title)
                putExtra("body", body)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

            // 2) 시스템 알림 — 포그라운드/백그라운드 무관 (운행 중 기사가 시각·청각으로 인지 가능)
            //    showNotification 재사용 (기존 알림 채널, FullScreenIntent X)
            showNotification(
                title ?: "예약 콜",
                body ?: "운행 종료 후 처리할 콜이 예약되었습니다",
                callId
            )
        } else if (!callId.isNullOrBlank() && messageType == "call_cancelled") {
            Log.d(TAG, "콜 취소 FCM 수신: callId=$callId")

            // LocalBroadcast로 UI 업데이트 (포그라운드/백그라운드 무관)
            val broadcastIntent = Intent(Constants.ACTION_CALL_CANCELLED).apply {
                putExtra("callId", callId)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

            // 일반 알림 항상 표시
            val cancelReason = remoteMessage.data["cancelReason"] ?: "고객이 콜을 취소했습니다"
            showNotification("콜 취소", cancelReason, callId)
        } else if (messageType == "SETTLEMENT_FINALIZED") {
            // 업무 마감 알림 처리
            val sessionDate = remoteMessage.data["sessionDate"] ?: ""
            val totalCount = remoteMessage.data["totalCount"] ?: "0"
            val totalFare = remoteMessage.data["totalFare"] ?: "0"

            Log.d(TAG, "업무 마감 FCM 수신 - sessionDate: $sessionDate, totalCount: $totalCount, totalFare: $totalFare")

            // LocalBroadcast로 UI에 알림 (포그라운드/백그라운드 모두)
            val broadcastIntent = Intent(Constants.ACTION_SETTLEMENT_FINALIZED).apply {
                putExtra("sessionDate", sessionDate)
                putExtra("totalCount", totalCount)
                putExtra("totalFare", totalFare)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

            // 알림 표시
            showSettlementNotification(title, body, sessionDate)
        } else if (messageType == "SETTLEMENT_CONFIRMED") {
            // 매니저 정산 확인 완료
            Log.d(TAG, "정산 확인 FCM 수신")
            val broadcastIntent = Intent(Constants.ACTION_SETTLEMENT_CONFIRMED)
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
            showNotification(
                title ?: "정산 확인 완료",
                body ?: "매니저가 정산을 확인했습니다. 퇴근할 수 있습니다.",
                null,
                navigateTo = "settlement"
            )
        } else if (messageType == "SETTLEMENT_REJECTED") {
            // 매니저 정산 거절
            Log.d(TAG, "정산 거절 FCM 수신")
            val broadcastIntent = Intent(Constants.ACTION_SETTLEMENT_REJECTED)
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
            showNotification(
                title ?: "정산 거절",
                body ?: "매니저가 정산을 거절했습니다. 재제출해주세요.",
                null,
                navigateTo = "settlement"
            )
        } else if (messageType == "CARRYOVER_TRANSFERRED") {
            // 이체 알림
            Log.d(TAG, "이체 알림 FCM 수신")
            showNotification(
                title ?: "미수령금 이체 알림",
                body ?: "이체가 완료되었습니다.",
                null,
                navigateTo = "settlement"
            )
        } else if (messageType == "NEW_CHAT_MESSAGE") {
            // 사무실 단톡방 메시지
            handleChatMessage(remoteMessage)
        } else {
            // 기타 알림
            showNotification(title, body, callId)
        }
    }

    /**
     * 사무실 단톡방 메시지 처리 (NEW_CHAT_MESSAGE)
     * 1) Room INSERT (Repository에서 본인 senderId면 자동 skip)
     * 2) 본인 메시지면 알림 skip / 포그라운드면 BottomSheet UI가 처리(알림 skip) / 백그라운드만 알림
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
        val currentUid = Firebase.auth.currentUser?.uid
        if (currentUid != null && currentUid == senderId) {
            Log.d(TAG, "[handleChatMessage] 본인 메시지 - 알림 스킵")
            return
        }
        // 3) 포그라운드면 BottomSheet UI가 처리 — 시스템 알림 skip, sound만 재생
        if (isAppInForeground()) {
            Log.d(TAG, "[handleChatMessage] 포그라운드 - 알림 스킵 (UI가 처리). sound만 재생")
            playChatSound()
            return
        }

        // 4) 백그라운드 알림 (chat_messages_ptt 채널)
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

        val notification = NotificationCompat.Builder(this, DriverApplication.CHANNEL_CHAT_MESSAGES)
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

    /**
     * 포그라운드 시 chat 메시지 도착 효과음 재생.
     * 시스템 default 알림음 사용 — 사용자가 시스템 알림 채널 설정에서 사운드/음량 직접 컨트롤.
     */
    private fun playChatSound() {
        try {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            MediaPlayer.create(this, defaultUri)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "[playChatSound] default notification 재생 실패", e)
        }
    }

    private fun isAppInForeground(): Boolean {
        // ProcessLifecycleOwner 기반의 정확한 포그라운드 상태 확인
        // (Android 12+ 에서도 정확하게 동작)
        return DriverApplication.isInForeground
    }

    private fun showNotification(title: String, body: String, callId: String?, navigateTo: String? = null) {
        val channelId = "call_assignment_channel"
        val notificationId = if (callId != null) callId.hashCode() else System.currentTimeMillis().toInt()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 기본 알림 소리
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 채널이 없을 때만 생성 (매번 삭제/재생성하면 사용자 설정 초기화되고 알림 차단될 수 있음)
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val channel = NotificationChannel(
                    channelId,
                    "콜 배정 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "새로운 콜이 배정되었을 때 알림"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    setShowBadge(true)
                    setSound(defaultSoundUri, audioAttributes)
                    enableLights(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        // 알림 클릭 시 MainActivity로 이동
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (callId != null) {
                putExtra("callId", callId)
            }
            if (navigateTo != null) {
                putExtra("navigateTo", navigateTo)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Full-screen intent용: LockScreenActivity (잠금화면 위 전체화면 표시)
        val lockScreenIntent = Intent(this, LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LockScreenActivity.EXTRA_CALL_ID, callId ?: "")
            putExtra(LockScreenActivity.EXTRA_TITLE, title)
            putExtra(LockScreenActivity.EXTRA_BODY, body)
            putExtra(LockScreenActivity.EXTRA_NOTIFICATION_ID, notificationId)
        }

        val fullScreenIntent = PendingIntent.getActivity(
            this,
            notificationId + 1,
            lockScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)  // MAX로 상향
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setSound(defaultSoundUri)  // 소리 설정 추가
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)  // 기본 LED
            .setFullScreenIntent(fullScreenIntent, true) // 백그라운드에서 화면 띄우기

        notificationManager.notify(notificationId, builder.build())
        Log.d(TAG, "알림 표시 완료: notificationId=$notificationId, channelId=$channelId")
    }

    private fun showSettlementNotification(title: String, body: String, sessionDate: String) {
        val channelId = "settlement_channel"
        val notificationId = "settlement_$sessionDate".hashCode()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "업무 마감 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "업무 마감 및 정산 안내"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("settlementFinalized", true)
            putExtra("sessionDate", sessionDate)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 300, 200, 300))

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * 도착 ACK 전송 (Cloud Functions 호출)
     */
    private fun sendDeliveryAck(notificationId: String) {
        val functions = Firebase.functions("asia-northeast3")

        functions
            .getHttpsCallable("acknowledgeNotification")
            .call(hashMapOf("notificationId" to notificationId))
            .addOnSuccessListener {
                Log.d(TAG, "도착 ACK 전송 성공: $notificationId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "도착 ACK 전송 실패: $notificationId", e)
            }
    }
}
