package com.designated.callmanager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.designated.callmanager.MainActivity
import com.designated.callmanager.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "CallManager_FCM"
        
        // 알림 채널 ID들
        private const val NEW_CALL_CHANNEL_ID = "new_call_fcm_channel_v2"
        private const val STATUS_CHANGE_CHANNEL_ID = "status_change_fcm_channel"
        private const val DRIVER_UPDATE_CHANNEL_ID = "driver_update_fcm_channel"
        private const val SHARED_CALL_CHANNEL_ID = "shared_call_fcm_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "🔔 FCM 메시지 수신: ${remoteMessage.from}")
        Log.d(TAG, "데이터: ${remoteMessage.data}")

        val messageType = remoteMessage.data["type"] ?: return
        
        // SHARED_CALL_CANCELLED_POPUP은 포그라운드에서도 처리해야 함
        val shouldProcessInForeground = messageType == "SHARED_CALL_CANCELLED_POPUP"
        
        // 앱이 포그라운드라면 시스템 알림을 띄우지 않고 종료 (리스너가 처리)
        // 단, SHARED_CALL_CANCELLED_POPUP은 예외
        if (isAppInForeground() && !shouldProcessInForeground) {
            Log.d(TAG, "앱이 포그라운드 상태이므로 FCM 알림을 무시합니다.")
            return
        }
        
        // 공유콜의 경우 callId 대신 sharedCallId 사용
        val callId = remoteMessage.data["callId"] 
            ?: remoteMessage.data["sharedCallId"] 
            ?: return

        // 필요 없는 알림 타입 필터링
        val ignoredTypes = setOf("DRIVER_ACCEPT", "DRIVER_REJECT", "SETTLED", "AWAITING_SETTLEMENT")
        if (ignoredTypes.contains(messageType)) {
            Log.d(TAG, "무시되는 메시지 타입: $messageType")
            return
        }

        when (messageType) {
            "NEW_CALL" -> handleNewCall(remoteMessage, callId)
            "NEW_SHARED_CALL" -> handleNewSharedCall(remoteMessage, callId)
            "STATUS_CHANGE" -> handleStatusChange(remoteMessage, callId)  // 운행 시작(IN_PROGRESS)만 실 알림
            "DRIVER_STATUS_UPDATE" -> handleDriverStatusUpdate(remoteMessage, callId)
            "SHARED_CALL_CANCELLED_POPUP" -> handleSharedCallCancelled(remoteMessage, callId)
            else -> Log.w(TAG, "알 수 없는 메시지 타입: $messageType")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "새로운 FCM 토큰 발급: $token")
        
        // SharedPreferences에 저장
        getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
        
        // 🔥 Firestore에도 저장 (관리자 컬렉션)
        saveTokenToFirestore(token)
    }
    
    private fun saveTokenToFirestore(token: String) {
        Log.d(TAG, "🔥 saveTokenToFirestore 함수 호출됨")
        
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        
        Log.d(TAG, "  현재 사용자: ${currentUser?.email ?: "null"}")
        
        if (currentUser == null) {
            Log.w(TAG, "❌ 사용자가 로그인되지 않아 FCM 토큰을 Firestore에 저장할 수 없습니다.")
            return
        }
        
        // SharedPreferences에서 regionId, officeId 가져오기
        val sharedPreferences = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val regionId = sharedPreferences.getString("regionId", null)
        val officeId = sharedPreferences.getString("officeId", null)
        
        Log.d(TAG, "  SharedPreferences - regionId: '$regionId', officeId: '$officeId'")
        
        if (regionId.isNullOrBlank() || officeId.isNullOrBlank()) {
            Log.w(TAG, "❌ regionId 또는 officeId가 없어 FCM 토큰을 Firestore에 저장할 수 없습니다.")
            Log.w(TAG, "     regionId: '$regionId', officeId: '$officeId'")
            
            // SharedPreferences 전체 내용 로그 출력
            val allPrefs = sharedPreferences.all
            Log.w(TAG, "     SharedPreferences 전체 내용: $allPrefs")
            return
        }
        
        val adminId = currentUser.uid
        val firestore = FirebaseFirestore.getInstance()
        
        Log.d(TAG, "🚀 FCM 토큰 Firestore 저장 시도")
        Log.d(TAG, "     AdminId: $adminId")
        Log.d(TAG, "     RegionId: $regionId")
        Log.d(TAG, "     OfficeId: $officeId")
        Log.d(TAG, "     Token: ${token.take(20)}...")
        
        // set with merge=true로 변경 (문서가 없어도 생성됨)
        val tokenData = hashMapOf(
            "fcmToken" to token,
            "lastUpdated" to System.currentTimeMillis(),
            "associatedRegionId" to regionId,
            "associatedOfficeId" to officeId
        )
        
        firestore.collection("admins").document(adminId)
            .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { 
                Log.i(TAG, "✅ 관리자 FCM 토큰 Firestore 저장 성공!")
                Log.i(TAG, "     Admin: $adminId")
                Log.i(TAG, "     경로: admins/$adminId")
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "❌ 관리자 FCM 토큰 Firestore 저장 실패!")
                Log.e(TAG, "     Admin: $adminId")
                Log.e(TAG, "     경로: admins/$adminId")
                Log.e(TAG, "     실패 원인: ${e.message}")
                Log.e(TAG, "     예외 타입: ${e.javaClass.simpleName}")
                e.printStackTrace()
            }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 이전 버전 채널 삭제 (한 번만)
            try {
                val oldChannel = notificationManager.getNotificationChannel("new_call_fcm_channel")
                if (oldChannel != null) {
                    notificationManager.deleteNotificationChannel("new_call_fcm_channel")
                    Log.i(TAG, "이전 버전 채널 삭제 완료")
                }
            } catch (e: Exception) {
                Log.w(TAG, "이전 버전 채널 삭제 중 오류 (무시해도 됨): ${e.message}")
            }
            
            // 새로운 콜 채널 - 없을 때만 생성
            if (notificationManager.getNotificationChannel(NEW_CALL_CHANNEL_ID) == null) {
                val newCallChannel = NotificationChannel(
                    NEW_CALL_CHANNEL_ID,
                    "새로운 콜 알림 (긴급)",
                    NotificationManager.IMPORTANCE_MAX
                ).apply {
                    description = "새로운 콜 접수 긴급 알림"
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build())
                }
                notificationManager.createNotificationChannel(newCallChannel)
                Log.i(TAG, "새로운 콜 채널 생성 완료")
            }
            
            // 상태 변경 채널 - 없을 때만 생성
            if (notificationManager.getNotificationChannel(STATUS_CHANGE_CHANNEL_ID) == null) {
                val statusChangeChannel = NotificationChannel(
                    STATUS_CHANGE_CHANNEL_ID,
                    "운행 상태 변경 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "기사 운행 상태 변경 알림"
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 100, 300, 100, 300)
                    setShowBadge(true)
                    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build())
                }
                notificationManager.createNotificationChannel(statusChangeChannel)
                Log.i(TAG, "상태 변경 채널 생성 완료")
            }
            
            // 기사 업데이트 채널 - 없을 때만 생성
            if (notificationManager.getNotificationChannel(DRIVER_UPDATE_CHANNEL_ID) == null) {
                val driverUpdateChannel = NotificationChannel(
                    DRIVER_UPDATE_CHANNEL_ID,
                    "기사 응답 알림",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "기사 수락/거절 알림"
                    enableVibration(true)
                    setShowBadge(true)
                    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build())
                }
                notificationManager.createNotificationChannel(driverUpdateChannel)
                Log.i(TAG, "기사 업데이트 채널 생성 완료")
            }
            
            // 공유콜 채널 - 없을 때만 생성
            if (notificationManager.getNotificationChannel(SHARED_CALL_CHANNEL_ID) == null) {
                val sharedCallChannel = NotificationChannel(
                    SHARED_CALL_CHANNEL_ID,
                    "공유콜 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "새로운 공유콜 도착 알림"
                    enableLights(true)
                    lightColor = Color.YELLOW
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build())
                }
                notificationManager.createNotificationChannel(sharedCallChannel)
                Log.i(TAG, "공유콜 채널 생성 완료")
            }
            
            Log.i(TAG, "알림 채널 확인 완료")
        }
    }

    private fun handleNewCall(remoteMessage: RemoteMessage, callId: String) {
        Log.i(TAG, "🚨 새로운 콜 FCM 알림 처리: $callId")
        
        val customerName = remoteMessage.data["customerName"] ?: "신규 고객"
        val customerPhone = remoteMessage.data["customerPhone"] ?: "-"
        val pickupLocation = remoteMessage.data["pickupLocation"] ?: "위치 미확인"
        
        showNotification(
            channelId = NEW_CALL_CHANNEL_ID,
            notificationId = "new_call_$callId".hashCode(),
            title = "🚨 새로운 콜!",
            content = "$customerName ($customerPhone)",
            bigText = "고객: $customerName\n전화: $customerPhone\n위치: $pickupLocation",
            callId = callId,
            color = ContextCompat.getColor(this, android.R.color.holo_red_dark),
            autoCancel = true,
            isNewCall = true,
            timeoutAfter = 60000 // 1분
        )
    }

    private fun handleNewSharedCall(remoteMessage: RemoteMessage, sharedCallId: String) {
        Log.i(TAG, "🔄 새로운 공유콜 FCM 알림 처리: $sharedCallId")
        
        val departure = remoteMessage.data["departure"] ?: "출발지"
        val destination = remoteMessage.data["destination"] ?: "도착지"
        val fare = remoteMessage.data["fare"] ?: "0"
        val callType = remoteMessage.data["callType"] ?: ""
        
        // 마감콜인지 확인하여 다른 표시
        val (title, content, description) = when (callType) {
            "AFTER_HOURS", "MISSED_CALL", "AFTER_HOURS_QUICK" -> {
                Triple(
                    "🌙 마감콜 공유",
                    "마감 후 접수된 콜",
                    "마감 후 접수된 콜"
                )
            }
            else -> {
                Triple(
                    "🔄 새로운 공유콜!",
                    "$departure → $destination",
                    "출발지: $departure\n도착지: $destination\n요금: ${fare}원\n\n다른 사무실에서 공유한 콜입니다."
                )
            }
        }
        
        showNotification(
            channelId = SHARED_CALL_CHANNEL_ID,
            notificationId = "shared_call_$sharedCallId".hashCode(),
            title = title,
            content = content,
            bigText = description,
            callId = sharedCallId,
            color = ContextCompat.getColor(this, android.R.color.holo_orange_dark),
            autoCancel = true,
            isSharedCall = true,
            timeoutAfter = 120000 // 2분
        )
    }

    private fun handleStatusChange(remoteMessage: RemoteMessage, callId: String) {
        Log.i(TAG, "🚗 운행 상태 변경 FCM 알림 처리: $callId")
        
        val statusText = remoteMessage.data["statusText"] ?: "상태 변경"
        val customerName = remoteMessage.data["customerName"] ?: "고객"
        val customerPhone = remoteMessage.data["customerPhone"] ?: "-"
        val driverName = remoteMessage.data["driverName"] ?: "기사"
        
        val (emoji, color) = when (statusText) {
            "운행 시작" -> "🚗" to ContextCompat.getColor(this, android.R.color.holo_green_dark)
            "운행 완료" -> "✅" to ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            else -> "📢" to ContextCompat.getColor(this, android.R.color.holo_orange_dark)
        }
        
        showNotification(
            channelId = STATUS_CHANGE_CHANNEL_ID,
            notificationId = callId.hashCode(),
            title = "$emoji $statusText",
            content = "$customerName ($customerPhone) - $driverName",
            bigText = "고객: $customerName\n전화: $customerPhone\n기사: $driverName\n상태: $statusText",
            callId = callId,
            color = color,
            autoCancel = true,
            timeoutAfter = 30000 // 30초
        )
    }

    private fun handleDriverStatusUpdate(remoteMessage: RemoteMessage, callId: String) {
        Log.i(TAG, "📍 기사 상태 업데이트 FCM 알림 처리: $callId")
        
        val driverName = remoteMessage.data["driverName"] ?: "기사"
        val newStatus = remoteMessage.data["newStatus"] ?: "상태 변경"
        
        showNotification(
            channelId = DRIVER_UPDATE_CHANNEL_ID,
            notificationId = "driver_status_$callId".hashCode(),
            title = "📍 기사 상태 업데이트",
            content = "$driverName: $newStatus",
            bigText = "기사: $driverName\n새로운 상태: $newStatus",
            callId = callId,
            color = ContextCompat.getColor(this, android.R.color.holo_blue_light),
            autoCancel = true,
            timeoutAfter = 10000 // 10초
        )
    }

    private fun handleSharedCallCancelled(remoteMessage: RemoteMessage, callId: String) {
        Log.i(TAG, "🚫 공유콜 취소 FCM 알림 처리: $callId")
        
        val departure = remoteMessage.data["departure"] ?: "출발지"
        val destination = remoteMessage.data["destination"] ?: "도착지"
        val cancelReason = remoteMessage.data["cancelReason"] ?: "사유 없음"
        val phoneNumber = remoteMessage.data["phoneNumber"] ?: ""
        
        showNotification(
            channelId = SHARED_CALL_CHANNEL_ID,
            notificationId = "shared_call_cancelled_$callId".hashCode(),
            title = "🚫 공유콜이 취소되었습니다!",
            content = "$departure → $destination",
            bigText = "출발지: $departure\n도착지: $destination\n전화번호: $phoneNumber\n취소사유: $cancelReason\n\n콜이 대기상태로 복구되었습니다.",
            callId = callId,
            color = ContextCompat.getColor(this, android.R.color.holo_red_dark),
            autoCancel = true,
            isSharedCallCancelled = true,
            timeoutAfter = 60000 // 1분
        )
    }

    private fun showNotification(
        channelId: String,
        notificationId: Int,
        title: String,
        content: String,
        bigText: String,
        callId: String,
        color: Int,
        autoCancel: Boolean,
        isNewCall: Boolean = false,
        isSharedCall: Boolean = false,
        isSharedCallCancelled: Boolean = false,
        timeoutAfter: Long? = null
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ⭐️ 클릭 시 실행될 인텐트 생성
        val intent = Intent(this, MainActivity::class.java).apply {
            when {
                isNewCall -> {
                    action = "ACTION_SHOW_CALL_POPUP"
                    putExtra("callId", callId)
                }
                isSharedCall -> {
                    action = "ACTION_SHOW_SHARED_CALL"
                    putExtra("sharedCallId", callId)
                }
                isSharedCallCancelled -> {
                    action = "ACTION_SHOW_SHARED_CALL_CANCELLED"
                    putExtra("callId", callId)
                }
            }
            // ⭐️ 앱을 새로 시작하거나 기존의 것을 맨 위로 올림
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // ⭐️ 전체 화면 인텐트 (헤드업 알림용)
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            when {
                isNewCall -> {
                    action = "ACTION_SHOW_CALL_POPUP"
                    putExtra("callId", callId)
                }
                isSharedCall -> {
                    action = "ACTION_SHOW_SHARED_CALL"
                    putExtra("sharedCallId", callId)
                }
                isSharedCallCancelled -> {
                    action = "ACTION_SHOW_SHARED_CALL_CANCELLED"
                    putExtra("callId", callId)
                }
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            ("fullscreen_$callId").hashCode(),
            fullScreenIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX) // ⭐️ 최고 우선순위
            .setCategory(NotificationCompat.CATEGORY_CALL) // ⭐️ 통화 카테고리로 설정
            .setColor(color)
            .setAutoCancel(autoCancel)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOngoing(!autoCancel) // ⭐️ 새로운 콜은 지속적으로 표시

        // ⭐️ 새로운 콜이나 공유콜, 공유콜 취소인 경우 전체 화면 인텐트 및 사운드 추가
        if (isNewCall || isSharedCall || isSharedCallCancelled) {
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
            // ⭐️ 알림 소리 명시적 설정
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            notificationBuilder.setSound(soundUri)
        }

        if (timeoutAfter != null) {
            notificationBuilder.setTimeoutAfter(timeoutAfter)
        }

        try {
            notificationManager.notify(notificationId, notificationBuilder.build())
            Log.i(TAG, "알림 표시 완료: $title (ID: $notificationId, 채널: $channelId)")
        } catch (e: Exception) {
            Log.e(TAG, "알림 표시 실패: ${e.message}")
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }
} 