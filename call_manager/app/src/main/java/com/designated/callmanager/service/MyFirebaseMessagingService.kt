package com.designated.callmanager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.designated.callmanager.MainActivity
import com.designated.callmanager.R
import com.designated.callmanager.ui.SharedCallAcceptActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "CallManager_FCM"

        private const val NEW_CALL_CHANNEL_ID = "new_call_fcm_channel_v2"
        private const val STATUS_CHANGE_CHANNEL_ID = "status_change_fcm_channel_v2"
        private const val DRIVER_UPDATE_CHANNEL_ID = "driver_update_fcm_channel_v3"
        private const val SHARED_CALL_CHANNEL_ID = "shared_call_fcm_channel_v3"  // v3로 변경하여 새 채널 생성
        private const val CHAT_MESSAGE_CHANNEL_ID = "chat_messages_ptt"  // 사무실 단톡방 (스펙 §10) — ptt 효과음 적용 (v2 ID)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("TEST_ORIGINAL", "🚨🚨🚨 MyFirebaseMessagingService onCreate 호출됨!!! 🚨🚨🚨")
        println("🚨🚨🚨 MyFirebaseMessagingService onCreate 호출됨!!! 🚨🚨🚨")
        createNotificationChannels()

        // FCM 토큰 즉시 확인
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "FCM 토큰 가져오기 실패", task.exception)
                return@addOnCompleteListener
            }
        }

        checkAndSyncExistingToken()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val messageType = remoteMessage.data["type"] ?: run {
            Log.w(TAG, "messageType이 null입니다 - 메시지 처리 중단")
            return
        }

        // 포그라운드에서 처리할 메시지 타입들
        val alwaysProcessTypes = setOf(
            "NEW_CALL",              // 앱호출 새 콜은 항상 처리
            "NEW_SHARED_CALL",
            "SHARED_CALL_CANCELLED_POPUP",
            "SHARED_CALL_CLAIMED",
            "DRIVER_STATUS_UPDATE",  // 기사 상태 변경은 항상 처리
            "CALL_STATUS_UPDATE",    // 콜 상태 업데이트는 항상 처리
            "STATUS_CHANGE",         // 운행 시작/완료 알림은 항상 처리
            "NOTIFICATION_FAILURE",  // 알림 전달 실패 경고는 항상 처리
            "SETTLEMENT_SUBMITTED",  // 기사 업무마감 제출 알림
            "NEW_CHAT_MESSAGE"       // 사무실 단톡방 메시지 (포그라운드도 Room INSERT 필요)
        )
        val shouldProcessInForeground = alwaysProcessTypes.contains(messageType)

        val isInForeground = isAppInForeground()
        Log.d(TAG, "앱 포그라운드 상태: $isInForeground, shouldProcessInForeground: $shouldProcessInForeground")

        if (isInForeground && !shouldProcessInForeground) {
            Log.d(TAG, "포그라운드에서 처리하지 않음 (공유콜/기사상태 외) - return")
            return
        }

        // DRIVER_STATUS_UPDATE는 callId 대신 driverId 사용
        if (messageType == "DRIVER_STATUS_UPDATE") {
            val driverId = remoteMessage.data["driverId"] ?: return
            Log.d(TAG, "🔔 [DEBUG] DRIVER_STATUS_UPDATE 처리 시작 - driverId: $driverId")
            handleDriverStatusUpdate(remoteMessage, driverId)
            Log.d(TAG, "🔔 [DEBUG] DRIVER_STATUS_UPDATE 처리 완료")
            return
        }

        // SETTLEMENT_SUBMITTED는 callId 불필요
        if (messageType == "SETTLEMENT_SUBMITTED") {
            val driverId = remoteMessage.data["driverId"] ?: ""
            val driverName = remoteMessage.data["driverName"] ?: "기사"
            val tripCount = remoteMessage.data["tripCount"] ?: "0"
            val realDeposit = remoteMessage.data["realDeposit"] ?: "0"
            Log.d(TAG, "🔔 SETTLEMENT_SUBMITTED - $driverName, ${tripCount}건, 실납입: ${realDeposit}원")
            showNotification(
                channelId = STATUS_CHANGE_CHANNEL_ID,
                notificationId = "settlement_$driverId".hashCode(),
                title = "업무마감 제출",
                content = "${driverName}님이 업무마감을 제출했습니다.",
                bigText = "기사: $driverName\n운행: ${tripCount}건\n실납입: ${realDeposit}원",
                callId = driverId,
                color = ContextCompat.getColor(this, android.R.color.holo_orange_dark),
                autoCancel = true,
                isSettlement = true,
                timeoutAfter = 0
            )
            return
        }

        // NEW_CHAT_MESSAGE는 callId 없으므로 별도 처리 (callId 체크 전에 분기)
        if (messageType == "NEW_CHAT_MESSAGE") {
            handleChatMessage(remoteMessage)
            return
        }

        val callId = remoteMessage.data["callId"]
            ?: remoteMessage.data["sharedCallId"]
            ?: return

        val ignoredTypes = setOf("DRIVER_ACCEPT", "DRIVER_REJECT", "SETTLED", "AWAITING_SETTLEMENT")
        if (ignoredTypes.contains(messageType)) {
            return
        }

        Log.d(TAG, "🔔 [DEBUG] 메시지 타입에 따른 처리 시작: $messageType")

        when (messageType) {
            "NEW_CALL" -> {
                Log.d(TAG, "🔔 [DEBUG] NEW_CALL 처리 시작")
                handleNewCall(remoteMessage, callId)
                Log.d(TAG, "🔔 [DEBUG] NEW_CALL 처리 완료")
            }
            "call_assigned" -> {
                Log.d(TAG, "🔔 [DEBUG] call_assigned (일반 콜 배정) 처리 시작")
                handleNewCall(remoteMessage, callId)
                Log.d(TAG, "🔔 [DEBUG] call_assigned 처리 완료")
            }
            "DRIVER_APPROVAL_REQUEST" -> {
                Log.d(TAG, "🔔 [DEBUG] DRIVER_APPROVAL_REQUEST 처리 시작")
                handleDriverApprovalRequest(remoteMessage)
                Log.d(TAG, "🔔 [DEBUG] DRIVER_APPROVAL_REQUEST 처리 완료")
            }
            "NEW_SHARED_CALL" -> {
                Log.d(TAG, "🔔 [DATA_ONLY] NEW_SHARED_CALL 처리 시작")
                // 사무실 마감 상태 확인
                if (isOfficeClosed()) {
                    Log.d(TAG, "🔔 [DATA_ONLY] 사무실 마감 상태 - 공유콜 알림 차단")
                    return
                }
                // Data-only 메시지이므로 항상 커스텀 알림 생성
                showCustomSharedCallNotification(remoteMessage)
                Log.d(TAG, "🔔 [DATA_ONLY] NEW_SHARED_CALL 처리 완료")
            }
            "CALL_STATUS_UPDATE" -> {
                Log.d(TAG, "🔔 [DEBUG] CALL_STATUS_UPDATE 처리 시작")
                handleCallStatusUpdate(remoteMessage)
                Log.d(TAG, "🔔 [DEBUG] CALL_STATUS_UPDATE 처리 완료")
            }
            "STATUS_CHANGE" -> {
                Log.d(TAG, "🔔 [DEBUG] STATUS_CHANGE 처리 시작")
                handleStatusChange(remoteMessage, callId)
                Log.d(TAG, "🔔 [DEBUG] STATUS_CHANGE 처리 완료")
            }
            // DRIVER_STATUS_UPDATE는 위에서 별도 처리됨 (driverId 사용)
            "SHARED_CALL_CANCELLED_POPUP" -> {
                Log.d(TAG, "🔔 [DEBUG] SHARED_CALL_CANCELLED_POPUP 처리 시작")
                handleSharedCallCancelled(remoteMessage, callId)
                Log.d(TAG, "🔔 [DEBUG] SHARED_CALL_CANCELLED_POPUP 처리 완료")
            }
            "new_customer" -> {
                Log.d(TAG, "🔔 [DEBUG] new_customer 처리 시작")
                handleNewCustomer(remoteMessage)
                Log.d(TAG, "🔔 [DEBUG] new_customer 처리 완료")
            }
            "NOTIFICATION_FAILURE" -> {
                Log.d(TAG, "🔔 [DEBUG] NOTIFICATION_FAILURE 처리 시작")
                handleNotificationFailure(remoteMessage)
                Log.d(TAG, "🔔 [DEBUG] NOTIFICATION_FAILURE 처리 완료")
            }
            else -> {
                Log.w(TAG, "⚠️ [DEBUG] 알 수 없는 메시지 타입: $messageType")
            }
        }

        Log.d(TAG, "🔔 [DEBUG] onMessageReceived 완전 종료")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "========== onNewToken 호출됨 ==========")
        Log.d(TAG, "[새로운 FCM 토큰]: $token")

        getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
        Log.d(TAG, "[onNewToken] SharedPreferences에 새 토큰 저장 완료")

        saveTokenToFirestore(token)
    }

    /**
     * FCM 토큰을 Firestore에 저장 (Two-Phase Commit 방식)
     * Phase 1: admins 컬렉션에 기본 토큰 저장 (항상 실행)
     * Phase 2: managerTokens 컬렉션에 사무실별 토큰 저장 (로그인 후에만)
     */
    private fun saveTokenToFirestore(token: String) {
        Log.d(TAG, "[saveTokenToFirestore] 시작 - 토큰: ${token.take(20)}...")

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Log.w(TAG, "[saveTokenToFirestore] 현재 사용자 null - 저장 취소")
            return
        }

        val adminId = currentUser.uid
        Log.d(TAG, "[saveTokenToFirestore] 현재 사용자 UID: $adminId")

        // Phase 1: admins 컬렉션에 기본 토큰 저장 (항상 실행)
        saveTokenToAdminsCollection(adminId, token)

        // Phase 2: managerTokens 컬렉션에 사무실별 토큰 저장 (조건부)
        val sharedPreferences = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val provinceId = sharedPreferences.getString("provinceId", null)
        val cityId = sharedPreferences.getString("cityId", null)
        val officeId = sharedPreferences.getString("officeId", null)

        Log.d(TAG, "[saveTokenToFirestore] provinceId: $provinceId, cityId: $cityId, officeId: $officeId")

        if (!provinceId.isNullOrBlank() && !cityId.isNullOrBlank() && !officeId.isNullOrBlank()) {
            saveTokenToManagerTokensCollection(adminId, provinceId, cityId, officeId, token)
        } else {
            Log.w(TAG, "[saveTokenToFirestore] provinceId/cityId/officeId 없음 - managerTokens 저장 스킵 (로그인 후 재시도 필요)")
        }
    }

    /**
     * Phase 1: admins 컬렉션에 기본 토큰 저장
     */
    private fun saveTokenToAdminsCollection(adminId: String, token: String) {
        val firestore = FirebaseFirestore.getInstance()

        // 기본 토큰 데이터 (regionId/officeId 없이도 저장)
        val tokenData = hashMapOf(
            "fcmToken" to token,
            "lastUpdated" to System.currentTimeMillis()
        )

        Log.d(TAG, "[saveTokenToAdmins] admins 컬렉션에 토큰 저장 시도...")

        firestore.collection("admins").document(adminId)
            .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "[saveTokenToAdmins] ✅ admins 컬렉션에 토큰 저장 성공")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "[saveTokenToAdmins] ❌ admins 컬렉션에 토큰 저장 실패: ${e.message}")
                e.printStackTrace()
            }
    }

    /**
     * Phase 2: managerTokens 컬렉션에 사무실별 토큰 저장
     * 로그인 완료 후 호출되어야 함
     */
    private fun saveTokenToManagerTokensCollection(
        adminId: String,
        provinceId: String,
        cityId: String,
        officeId: String,
        token: String
    ) {
        val firestore = FirebaseFirestore.getInstance()

        val managerTokenData = hashMapOf(
            "fcmToken" to token,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        Log.d(TAG, "[saveTokenToManagerTokens] managerTokens 저장 시도 - provinceId: $provinceId, cityId: $cityId, officeId: $officeId")

        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("managerTokens").document(adminId)
            .set(managerTokenData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "[saveTokenToManagerTokens] ✅ managerTokens 컬렉션에 토큰 저장 성공")

                // admins 컬렉션에도 provinceId/cityId/officeId 업데이트
                val adminUpdateData = hashMapOf(
                    "associatedProvinceId" to provinceId,
                    "associatedCityId" to cityId,
                    "associatedOfficeId" to officeId
                )
                firestore.collection("admins").document(adminId)
                    .set(adminUpdateData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "[saveTokenToManagerTokens] ✅ admins에 provinceId/cityId/officeId 업데이트 완료")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "[saveTokenToManagerTokens] ❌ managerTokens 컬렉션에 토큰 저장 실패: ${e.message}")
                e.printStackTrace()
            }
    }

    /**
     * 로그인 완료 후 managerTokens 재동기화
     * LoginViewModel에서 호출됨
     */
    fun retryManagerTokensSync(provinceId: String, cityId: String, officeId: String) {
        Log.d(TAG, "[retryManagerTokensSync] managerTokens 재동기화 시작")

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Log.w(TAG, "[retryManagerTokensSync] 현재 사용자 null - 재동기화 불가")
            return
        }

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "[retryManagerTokensSync] 토큰 가져오기 실패: ${task.exception?.message}")
                    return@addOnCompleteListener
                }

                val token = task.result
                Log.d(TAG, "[retryManagerTokensSync] 토큰 획득 성공 - managerTokens 저장 시도")
                saveTokenToManagerTokensCollection(currentUser.uid, provinceId, cityId, officeId, token)
            }
    }

    // 기존 토큰 확인 및 Firestore 동기화 (정석적 방법)
    private fun checkAndSyncExistingToken() {
        Log.d(TAG, "[checkAndSyncExistingToken] 시작")

        // 현재 유효한 토큰 가져오기 (캐시된 토큰 사용)
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "[checkAndSyncExistingToken] 토큰 가져오기 실패: ${task.exception?.message}")
                    return@addOnCompleteListener
                }

                val currentToken = task.result
                Log.d(TAG, "[checkAndSyncExistingToken] 현재 FCM 토큰: $currentToken")

                // SharedPreferences의 토큰과 비교
                val sharedPrefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                val savedToken = sharedPrefs.getString("fcm_token", null)
                Log.d(TAG, "[checkAndSyncExistingToken] 저장된 토큰: $savedToken")

                when {
                    savedToken == null -> {
                        Log.d(TAG, "[checkAndSyncExistingToken] SharedPreferences에 토큰 없음 - 새로 저장")
                        sharedPrefs.edit().putString("fcm_token", currentToken).apply()
                        saveTokenToFirestore(currentToken)
                    }
                    savedToken != currentToken -> {
                        Log.d(TAG, "[checkAndSyncExistingToken] 토큰 변경됨 - 업데이트 필요")
                        Log.d(TAG, "[checkAndSyncExistingToken] 이전 토큰: $savedToken")
                        Log.d(TAG, "[checkAndSyncExistingToken] 새 토큰: $currentToken")
                        sharedPrefs.edit().putString("fcm_token", currentToken).apply()
                        saveTokenToFirestore(currentToken)
                    }
                    else -> {
                        Log.d(TAG, "[checkAndSyncExistingToken] 토큰 동일 - Firestore 확인 필요")
                        verifyTokenInFirestore(currentToken)
                    }
                }
            }
    }

    // Firestore에 토큰이 저장되어 있는지 확인
    private fun verifyTokenInFirestore(token: String) {
        Log.d(TAG, "[verifyTokenInFirestore] 시작 - 토큰: $token")

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Log.w(TAG, "[verifyTokenInFirestore] 현재 사용자 null - 로그인 필요")
            return
        }
        Log.d(TAG, "[verifyTokenInFirestore] 현재 사용자 UID: ${currentUser.uid}")

        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("admins").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                val firestoreToken = document.getString("fcmToken")
                Log.d(TAG, "[verifyTokenInFirestore] Firestore 토큰: $firestoreToken")

                if (firestoreToken != token) {
                    Log.d(TAG, "[verifyTokenInFirestore] Firestore 토큰 불일치 - 업데이트 필요")
                    saveTokenToFirestore(token)
                } else {
                    Log.d(TAG, "[verifyTokenInFirestore] 토큰 일치 - 업데이트 불필요")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "[verifyTokenInFirestore] Firestore 토큰 확인 실패: ${e.message}")
                saveTokenToFirestore(token)
            }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 구 채널 삭제 (사운드 변경을 위해 채널 ID 버전업 시 필요)
            listOf("new_call_fcm_channel", "status_change_fcm_channel", "driver_update_fcm_channel", "driver_update_fcm_channel_v2").forEach { oldId ->
                try {
                    notificationManager.getNotificationChannel(oldId)?.let {
                        notificationManager.deleteNotificationChannel(oldId)
                    }
                } catch (e: Exception) { }
            }

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
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build())
                }
                notificationManager.createNotificationChannel(newCallChannel)
            }

            if (notificationManager.getNotificationChannel(STATUS_CHANGE_CHANNEL_ID) == null) {
                val customSoundUri = Uri.parse("android.resource://${packageName}/${R.raw.status_alert}")
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
                    setSound(customSoundUri, AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build())
                }
                notificationManager.createNotificationChannel(statusChangeChannel)
            }

            if (notificationManager.getNotificationChannel(DRIVER_UPDATE_CHANNEL_ID) == null) {
                val customSoundUri2 = Uri.parse("android.resource://${packageName}/${R.raw.status_alert}")
                val driverUpdateChannel = NotificationChannel(
                    DRIVER_UPDATE_CHANNEL_ID,
                    "기사 응답 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "기사 수락/거절 알림"
                    enableVibration(true)
                    setShowBadge(true)
                    setSound(customSoundUri2, AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build())
                }
                notificationManager.createNotificationChannel(driverUpdateChannel)
            }

            if (notificationManager.getNotificationChannel(SHARED_CALL_CHANNEL_ID) == null) {
                Log.d(TAG, "🔧 [CHANNEL] SHARED_CALL_CHANNEL 새로 생성 시작 - ID: $SHARED_CALL_CHANNEL_ID")

                val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                Log.d(TAG, "🔧 [CHANNEL] 알람 소리 URI: $alarmSoundUri")

                val sharedCallChannel = NotificationChannel(
                    SHARED_CALL_CHANNEL_ID,
                    "공유콜 알림 (긴급)",
                    NotificationManager.IMPORTANCE_MAX
                ).apply {
                    description = "새로운 공유콜 도착 알림"
                    enableLights(true)
                    lightColor = Color.YELLOW
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                    setSound(alarmSoundUri, AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build())
                }

                notificationManager.createNotificationChannel(sharedCallChannel)
                Log.d(TAG, "🔧✅ [CHANNEL] SHARED_CALL_CHANNEL 생성 완료")

                // 생성된 채널 정보 확인
                val createdChannel = notificationManager.getNotificationChannel(SHARED_CALL_CHANNEL_ID)
                createdChannel?.let { channel ->
                    Log.d(TAG, "🔧 [CHANNEL] 생성된 채널 정보:")
                    Log.d(TAG, "🔧 [CHANNEL] - ID: ${channel.id}")
                    Log.d(TAG, "🔧 [CHANNEL] - Name: ${channel.name}")
                    Log.d(TAG, "🔧 [CHANNEL] - Importance: ${channel.importance}")
                    Log.d(TAG, "🔧 [CHANNEL] - Sound: ${channel.sound}")
                    Log.d(TAG, "🔧 [CHANNEL] - VibrationEnabled: ${channel.shouldVibrate()}")
                    Log.d(TAG, "🔧 [CHANNEL] - CanBypassDnd: ${channel.canBypassDnd()}")
                }
            } else {
                Log.d(TAG, "🔧 [CHANNEL] SHARED_CALL_CHANNEL 이미 존재함 - 기존 설정 사용")
                val existingChannel = notificationManager.getNotificationChannel(SHARED_CALL_CHANNEL_ID)
                existingChannel?.let { channel ->
                    Log.d(TAG, "🔧 [CHANNEL] 기존 채널 정보:")
                    Log.d(TAG, "🔧 [CHANNEL] - ID: ${channel.id}")
                    Log.d(TAG, "🔧 [CHANNEL] - Name: ${channel.name}")
                    Log.d(TAG, "🔧 [CHANNEL] - Importance: ${channel.importance}")
                    Log.d(TAG, "🔧 [CHANNEL] - Sound: ${channel.sound}")
                    Log.d(TAG, "🔧 [CHANNEL] - VibrationEnabled: ${channel.shouldVibrate()}")
                    Log.d(TAG, "🔧 [CHANNEL] - CanBypassDnd: ${channel.canBypassDnd()}")
                }
            }

            // 사무실 단톡방 채널 (스펙 §10) — 콜 채널과 차별화: HIGH/일반알림음/DND 우회 X
            if (notificationManager.getNotificationChannel(CHAT_MESSAGE_CHANNEL_ID) == null) {
                val chatChannel = NotificationChannel(
                    CHAT_MESSAGE_CHANNEL_ID,
                    "단톡방 메시지",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "사무실 단톡방 메시지 알림"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250)  // 단일 진동 (콜은 1초 반복)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setBypassDnd(false)  // 콜과 차별 (콜은 true)
                    setSound(
                        android.net.Uri.parse("android.resource://$packageName/${com.designated.callmanager.R.raw.ptt_start}"),
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                }
                notificationManager.createNotificationChannel(chatChannel)
                Log.d(TAG, "🔧✅ [CHANNEL] CHAT_MESSAGE_CHANNEL 생성 완료 (ptt_start)")
            }

        }
    }

    private fun handleDriverApprovalRequest(remoteMessage: RemoteMessage) {
        val driverId = remoteMessage.data["driverId"] ?: return
        val driverName = remoteMessage.data["driverName"] ?: "기사"
        val driverPhone = remoteMessage.data["driverPhone"] ?: ""

        Log.d(TAG, "[handleDriverApprovalRequest] 기사 승인 요청 알림 생성: $driverName")

        // 알림 생성
        showNotification(
            channelId = DRIVER_UPDATE_CHANNEL_ID,
            notificationId = "driver_approval_$driverId".hashCode(),
            title = "🚗 새 기사 가입 신청",
            content = "$driverName 님이 가입 승인을 기다리고 있습니다.",
            bigText = "이름: $driverName\n전화: $driverPhone\n승인 대기 중",
            callId = driverId,
            color = ContextCompat.getColor(this, android.R.color.holo_blue_dark),
            autoCancel = true,
            isNewCall = false,
            isDriverApproval = true,
            timeoutAfter = 0
        )

        // MainActivity에서 승인 다이얼로그 표시하도록 브로드캐스트 전송
        val intent = Intent("com.designated.callmanager.DRIVER_APPROVAL_REQUEST")
        intent.putExtra("driverId", driverId)
        intent.putExtra("driverName", driverName)
        intent.putExtra("driverPhone", driverPhone)
        sendBroadcast(intent)
    }

    private fun handleNewCustomer(remoteMessage: RemoteMessage) {
        val customerId = remoteMessage.data["customerId"] ?: return
        val customerName = remoteMessage.data["customerName"] ?: "신규 회원"
        val customerPhone = remoteMessage.data["customerPhone"] ?: ""
        val referralDriverName = remoteMessage.data["referralDriverName"] ?: ""

        Log.d(TAG, "[handleNewCustomer] 신규 회원 가입 알림 생성: $customerName")

        val contentText = if (referralDriverName.isNotEmpty()) {
            "$customerName 님 (추천: $referralDriverName)"
        } else {
            "$customerName 님이 가입했습니다"
        }

        val bigText = if (referralDriverName.isNotEmpty()) {
            "이름: $customerName\n전화: $customerPhone\n추천: $referralDriverName"
        } else {
            "이름: $customerName\n전화: $customerPhone"
        }

        // 알림 생성
        showNotification(
            channelId = DRIVER_UPDATE_CHANNEL_ID,
            notificationId = "new_customer_$customerId".hashCode(),
            title = "🎉 새 회원 가입",
            content = contentText,
            bigText = bigText,
            callId = customerId,
            color = ContextCompat.getColor(this, android.R.color.holo_green_dark),
            autoCancel = true,
            isNewCall = false,
            timeoutAfter = 0
        )
    }

    /**
     * 사무실 단톡방 메시지 처리 (NEW_CHAT_MESSAGE)
     * 1) Room INSERT — Repository에서 본인 senderId면 내부적으로 skip
     * 2) 알림 표시 — 본인 메시지 + 포그라운드 시 skip (포그라운드는 BottomSheet UI가 처리)
     * 알림 채널: CHAT_MESSAGE_CHANNEL_ID (콜 채널과 차별, CATEGORY_MESSAGE)
     */
    private fun handleChatMessage(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val messageId = data["messageId"] ?: run {
            Log.w(TAG, "[handleChatMessage] messageId 없음 - 스킵")
            return
        }
        val senderId = data["senderId"] ?: return
        val senderName = data["senderName"] ?: "알 수 없음"
        val senderRole = data["senderRole"] ?: "MANAGER"
        val text = data["text"] ?: return

        Log.d(TAG, "[handleChatMessage] 수신 - id=$messageId, from=$senderName ($senderRole)")

        // 1) Room INSERT (Repository가 본인 메시지면 자동 skip)
        val app = applicationContext as? com.designated.callmanager.CallManagerApplication
        if (app == null) {
            Log.e(TAG, "[handleChatMessage] CallManagerApplication 가져올 수 없음")
            return
        }
        app.chatRepository.onRemoteMessageReceived(data)

        // 2) 알림 표시 조건 — 본인 메시지면 skip + 포그라운드면 skip
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid != null && currentUid == senderId) {
            Log.d(TAG, "[handleChatMessage] 본인 메시지 - 알림 스킵")
            return
        }
        if (isAppInForeground()) {
            Log.d(TAG, "[handleChatMessage] 포그라운드 - 알림 스킵 (UI가 처리). sound만 재생")
            playChatSound()
            return
        }

        // 3) 백그라운드 알림 (chat_messages 채널, CATEGORY_MESSAGE)
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
     * 시스템 알림은 포그라운드 skip이라 channel setSound가 트리거 X — 직접 재생.
     * chat 분기 안에서만 호출 (다른 알림 영향 0).
     */
    private fun playChatSound() {
        try {
            val uri = android.net.Uri.parse(
                "android.resource://$packageName/${R.raw.ptt_start}"
            )
            val ringtone = android.media.RingtoneManager.getRingtone(this, uri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.w(TAG, "[playChatSound] ptt_start 재생 실패", e)
        }
    }

    private fun handleNewCall(remoteMessage: RemoteMessage, callId: String) {
        val data = remoteMessage.data
        val customerName = data["customerName"] ?: "신규 고객"
        val customerPhone = data["customerPhone"] ?: "-"
        val pickupLocation = data["pickupLocation"] ?: "위치 미확인"
        val customerAddress = data["customerAddress"]
        val status = data["status"] ?: "WAITING"
        val callType = data["callType"]
        val fromCallDetector = data["fromCallDetector"]?.toBooleanStrictOrNull()
        val fromCallManager = data["fromCallManager"]?.toBooleanStrictOrNull()
        val assignedDriverId = data["assignedDriverId"]
        val assignedDriverName = data["assignedDriverName"]
        val assignedDriverPhone = data["assignedDriverPhone"]

        // 로컬 DB에 콜 저장
        val app = applicationContext as? com.designated.callmanager.CallManagerApplication
        if (app != null) {
            val sharedPreferences = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            // FCM 데이터에서 먼저 가져오고, 없으면 SharedPreferences에서 가져옴
            val provinceId = data["provinceId"] ?: sharedPreferences.getString("provinceId", null)
            val cityId = data["cityId"] ?: sharedPreferences.getString("cityId", null)
            val officeId = data["officeId"] ?: sharedPreferences.getString("officeId", null)

            if (!provinceId.isNullOrBlank() && !cityId.isNullOrBlank() && !officeId.isNullOrBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        app.callRepository.insertCallFromFCM(
                            callId = callId,
                            phoneNumber = customerPhone,
                            customerName = customerName,
                            customerAddress = customerAddress ?: pickupLocation,
                            status = status,
                            provinceId = provinceId,
                            officeId = officeId,
                            callType = callType,
                            fromCallDetector = fromCallDetector,
                            fromCallManager = fromCallManager,
                            assignedDriverId = assignedDriverId,
                            assignedDriverName = assignedDriverName,
                            assignedDriverPhone = assignedDriverPhone
                        )
                        Log.d(TAG, "[handleNewCall] 로컬 DB에 새 콜 저장 완료: $callId")
                    } catch (e: Exception) {
                        Log.e(TAG, "[handleNewCall] 로컬 DB 저장 실패: $callId", e)
                    }
                }
            } else {
                Log.w(TAG, "[handleNewCall] provinceId/cityId/officeId 없음 - 로컬 DB 저장 스킵")
            }
        }

        // 앱이 포그라운드일 때는 알림 생성하지 않음 (UI가 실시간 업데이트됨)
        // 콜디텍터에서 온 콜은 이미 콜디텍터에서 배차하므로 알림 불필요
        // 콜매니저에서 생성한 콜도 이미 포그라운드이므로 알림 불필요
        if (!isAppInForeground() && fromCallDetector != true && fromCallManager != true) {
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
                timeoutAfter = 60000
            )
        } else {
            val reason = when {
                isAppInForeground() -> "앱 포그라운드"
                fromCallDetector == true -> "콜디텍터에서 온 콜"
                fromCallManager == true -> "콜매니저에서 생성한 콜"
                else -> "기타"
            }
            Log.d(TAG, "[handleNewCall] 알림 생략 - $reason")
        }
    }

    private fun handleNewSharedCall(remoteMessage: RemoteMessage, sharedCallId: String) {
        // 사무실 마감 상태 확인
        if (isOfficeClosed()) {
            Log.d(TAG, "사무실 마감 상태 - 공유콜 알림 차단")
            return
        }

        val departure = remoteMessage.data["departure"] ?: "출발지"
        val destination = remoteMessage.data["destination"] ?: "도착지"
        val fare = remoteMessage.data["fare"] ?: "0"
        val callType = remoteMessage.data["callType"] ?: ""

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
            timeoutAfter = 120000
        )
    }

    /**
     * CALL_STATUS_UPDATE FCM 메시지 처리
     * Local-First 아키텍처: 로컬 DB 업데이트
     */
    private fun handleCallStatusUpdate(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val callId = data["callId"] ?: run {
            Log.e(TAG, "[CALL_STATUS_UPDATE] callId 없음")
            return
        }
        val status = data["status"] ?: run {
            Log.e(TAG, "[CALL_STATUS_UPDATE] status 없음")
            return
        }

        Log.d(TAG, "[CALL_STATUS_UPDATE] callId=$callId, status=$status")

        // CallManagerApplication에서 Repository 가져오기
        val app = applicationContext as? com.designated.callmanager.CallManagerApplication
        if (app == null) {
            Log.e(TAG, "[CALL_STATUS_UPDATE] CallManagerApplication을 가져올 수 없습니다")
            return
        }

        // 출발지/목적지/요금/경유지/기사전화번호 데이터 추출
        val departure = data["departure"]
        val destination = data["destination"]
        val fare = data["fare"]?.toLongOrNull()
        val waypoints = data["waypoints"]
        val driverPhone = data["assignedDriverPhone"]
        val driverName = data["assignedDriverName"] ?: "기사"
        val customerName = data["customerName"] ?: "고객"

        // 취소 상태일 때 일반 알림 표시
        if (status == "CANCELLED_BY_CUSTOMER" || status == "CANCELLED_BY_DRIVER" || status == "CANCELED") {
            val cancelledBy = when (status) {
                "CANCELLED_BY_CUSTOMER" -> "고객"
                "CANCELLED_BY_DRIVER" -> "기사"
                else -> "관리자"
            }
            val message = data["message"] ?: "${cancelledBy} 취소"
            showNotification(
                channelId = STATUS_CHANGE_CHANNEL_ID,
                notificationId = "call_cancelled_$callId".hashCode(),
                title = "콜 취소",
                content = message,
                bigText = message,
                callId = callId,
                color = android.graphics.Color.RED,
                autoCancel = true
            )
            Log.d(TAG, "[CALL_STATUS_UPDATE] 취소 알림 표시: $callId, $message")
        }

        // COMPLETED 상태일 때 바로 팝업 표시 (Flow 필터링 전에)
        if (status == "COMPLETED") {
            Log.d(TAG, "[CALL_STATUS_UPDATE] 운행완료 - 팝업 브로드캐스트 전송: callId=$callId, driverName=$driverName, customerName=$customerName")
            val broadcastIntent = Intent(com.designated.callmanager.data.Constants.ACTION_TRIP_COMPLETED).apply {
                putExtra("callId", callId)
                putExtra("driverName", driverName)
                putExtra("customerName", customerName)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
        }

        // Repository를 통한 로컬 DB 업데이트
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "[CALL_STATUS_UPDATE] 로컬 DB 업데이트: departure=$departure, destination=$destination, fare=$fare, waypoints=$waypoints, driverPhone=$driverPhone")

                // Repository를 통해 로컬 DB 업데이트
                app.callRepository.updateCallStatusFromFCM(
                    callId = callId,
                    newStatus = status,
                    departure = departure,
                    destination = destination,
                    fare = fare,
                    waypoints = waypoints,
                    driverPhone = driverPhone
                )

                Log.d(TAG, "[CALL_STATUS_UPDATE] 로컬 DB 업데이트 완료: $callId")
            } catch (e: Exception) {
                Log.e(TAG, "[CALL_STATUS_UPDATE] 로컬 DB 업데이트 실패: $callId", e)
            }
        }
    }

    private fun handleStatusChange(remoteMessage: RemoteMessage, callId: String) {
        val data = remoteMessage.data
        val statusText = data["statusText"] ?: "상태 변경"
        val customerName = data["customerName"] ?: "고객"
        val customerPhone = data["customerPhone"] ?: "-"
        val driverName = data["driverName"] ?: "기사"
        val driverPhone = data["driverPhone"]
        val departure = data["departure"]
        val destination = data["destination"]
        val waypoints = data["waypoints"]
        val fare = data["fare"]?.toLongOrNull()

        // statusText를 실제 status 값으로 변환
        val status = when (statusText) {
            "운행 시작" -> "IN_PROGRESS"
            "운행 완료" -> "COMPLETED"
            "기사 수락" -> "ACCEPTED"
            "정산 대기" -> "AWAITING_SETTLEMENT"
            else -> null
        }

        Log.d(TAG, "[STATUS_CHANGE] callId=$callId, statusText=$statusText, status=$status, driverPhone=$driverPhone, waypoints=$waypoints")

        // 로컬 DB 업데이트
        if (status != null) {
            val app = applicationContext as? com.designated.callmanager.CallManagerApplication
            if (app != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        app.callRepository.updateCallStatusFromFCM(
                            callId = callId,
                            newStatus = status,
                            departure = departure,
                            destination = destination,
                            fare = fare
                        )
                        Log.d(TAG, "[STATUS_CHANGE] 로컬 DB 업데이트 완료: $callId -> $status")
                    } catch (e: Exception) {
                        Log.e(TAG, "[STATUS_CHANGE] 로컬 DB 업데이트 실패: $callId", e)
                    }
                }
            } else {
                Log.e(TAG, "[STATUS_CHANGE] CallManagerApplication을 가져올 수 없습니다")
            }
        }

        val (emoji, color) = when (statusText) {
            "운행 시작" -> "🚗" to ContextCompat.getColor(this, android.R.color.holo_green_dark)
            "운행 완료" -> "✅" to ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            else -> "📢" to ContextCompat.getColor(this, android.R.color.holo_orange_dark)
        }

        // 운행 시작/완료 시 앱 내 팝업 표시를 위한 브로드캐스트 전송
        if (statusText == "운행 시작" || statusText == "운행 완료") {
            val popupIntent = Intent("com.designated.callmanager.TRIP_STATUS_POPUP")
            popupIntent.putExtra("statusText", statusText)
            popupIntent.putExtra("driverName", driverName)
            popupIntent.putExtra("driverPhone", driverPhone ?: "")
            popupIntent.putExtra("customerName", customerName)
            popupIntent.putExtra("departure", departure ?: "정보없음")
            popupIntent.putExtra("destination", destination ?: "정보없음")
            popupIntent.putExtra("waypoints", waypoints ?: "")
            popupIntent.putExtra("fare", fare ?: 0L)
            sendBroadcast(popupIntent)
            Log.d(TAG, "[STATUS_CHANGE] 팝업 브로드캐스트 전송: $statusText")
        }

        // 알림 bigText에 출발지/도착지/요금 포함
        val bigTextContent = buildString {
            append("기사: $driverName\n")
            append("고객: $customerName ($customerPhone)\n")
            if (!departure.isNullOrBlank()) append("출발: $departure\n")
            if (!destination.isNullOrBlank()) append("도착: $destination\n")
            if (fare != null && fare > 0) append("요금: ${fare}원\n")
            append("상태: $statusText")
        }

        showNotification(
            channelId = STATUS_CHANGE_CHANNEL_ID,
            notificationId = callId.hashCode(),
            title = "$emoji $statusText",
            content = "$customerName ($customerPhone) - $driverName",
            bigText = bigTextContent,
            callId = callId,
            color = color,
            autoCancel = true,
            timeoutAfter = 30000
        )
    }

    private fun handleDriverStatusUpdate(remoteMessage: RemoteMessage, driverId: String) {
        val driverName = remoteMessage.data["driverName"] ?: "기사"
        val newStatus = remoteMessage.data["newStatus"] ?: "상태 변경"
        val statusMessage = remoteMessage.data["statusMessage"] ?: newStatus
        val lastLoginTimeStr = remoteMessage.data["lastLoginTime"]

        Log.d(TAG, "🔔 [DRIVER_STATUS] 기사 상태 업데이트 - driverId: $driverId, name: $driverName, status: $newStatus, lastLoginTime: $lastLoginTimeStr")

        // 로컬 DB 직접 업데이트 (Room Flow가 자동으로 UI 업데이트)
        val app = applicationContext as? com.designated.callmanager.CallManagerApplication
        if (app != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val lastLoginTime = lastLoginTimeStr?.toLongOrNull()
                    if (lastLoginTime != null && lastLoginTime > 0) {
                        app.driverRepository.updateDriverStatusWithLoginTimeFromFCM(driverId, newStatus, lastLoginTime)
                        Log.d(TAG, "🔔 [DRIVER_STATUS] 로컬 DB 업데이트 완료 (with loginTime): $driverId -> $newStatus")
                    } else {
                        app.driverRepository.updateDriverStatusFromFCM(driverId, newStatus)
                        Log.d(TAG, "🔔 [DRIVER_STATUS] 로컬 DB 업데이트 완료: $driverId -> $newStatus")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "🔔 [DRIVER_STATUS] 로컬 DB 업데이트 실패: $driverId", e)
                }
            }
        } else {
            Log.e(TAG, "🔔 [DRIVER_STATUS] CallManagerApplication을 가져올 수 없습니다")
        }

        // 브로드캐스트 유지 (로그 용도)
        val intent = Intent("com.designated.callmanager.DRIVER_STATUS_UPDATE")
        intent.putExtra("driverId", driverId)
        intent.putExtra("driverName", driverName)
        intent.putExtra("newStatus", newStatus)
        intent.putExtra("statusMessage", statusMessage)
        sendBroadcast(intent)

        showNotification(
            channelId = DRIVER_UPDATE_CHANNEL_ID,
            notificationId = "driver_status_$driverId".hashCode(),
            title = "📍 기사 상태 업데이트",
            content = "$driverName: $statusMessage",
            bigText = "기사: $driverName\n상태: $statusMessage",
            callId = driverId,
            color = ContextCompat.getColor(this, android.R.color.holo_blue_light),
            autoCancel = true,
            timeoutAfter = 10000
        )
    }

    private fun handleSharedCallCancelled(remoteMessage: RemoteMessage, callId: String) {

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
            timeoutAfter = 60000
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
        isDriverApproval: Boolean = false,
        isSettlement: Boolean = false,
        timeoutAfter: Long? = null
    ) {
        Log.d(TAG, "🔔🔔🔔 [NOTIFICATION] showNotification 호출 시작 🔔🔔🔔")
        Log.d(TAG, "🔔 [NOTIFICATION] channelId: $channelId")
        Log.d(TAG, "🔔 [NOTIFICATION] notificationId: $notificationId")
        Log.d(TAG, "🔔 [NOTIFICATION] title: $title")
        Log.d(TAG, "🔔 [NOTIFICATION] callId: $callId")
        Log.d(TAG, "🔔 [NOTIFICATION] isNewCall: $isNewCall, isSharedCall: $isSharedCall")

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
                isDriverApproval -> {
                    action = "ACTION_SHOW_PENDING_DRIVERS"
                }
                isSettlement -> {
                    action = "ACTION_SHOW_SETTLEMENT"
                }
            }
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
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setColor(color)
            .setAutoCancel(autoCancel)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOngoing(!autoCancel)

        if (isNewCall || isSharedCall || isSharedCallCancelled) {
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)

            if (channelId == SHARED_CALL_CHANNEL_ID) {
                Log.d(TAG, "🔔 [BUILDER] 공유콜 알림 Builder 설정 시작")
                val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                Log.d(TAG, "🔔 [BUILDER] 알람 소리 URI: $alarmSoundUri")
                Log.d(TAG, "🔔 [BUILDER] 기본 알림 소리 URI: $notificationSoundUri")
                Log.d(TAG, "🔔 [BUILDER] 채널 ID: $channelId")

                notificationBuilder.setSound(alarmSoundUri)
                notificationBuilder.setVibrate(longArrayOf(0, 1000))  // 1회 진동으로 변경
                notificationBuilder.setPriority(NotificationCompat.PRIORITY_MAX)
                notificationBuilder.setDefaults(0)

                Log.d(TAG, "🔔 [BUILDER] 공유콜 알림 Builder 설정 완료 - 알람음 적용")
            } else {
                Log.d(TAG, "🔔 [BUILDER] 일반 알림 Builder 설정")
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                Log.d(TAG, "🔔 [BUILDER] 일반 알림 소리 URI: $soundUri")
                notificationBuilder.setSound(soundUri)
                Log.d(TAG, "🔔 [BUILDER] 일반 알림 Builder 설정 완료")
            }
        }

        if (timeoutAfter != null) {
            notificationBuilder.setTimeoutAfter(timeoutAfter)
        }

        try {
            val notification = notificationBuilder.build()
            Log.d(TAG, "🔔 [NOTIFICATION] 실제 알림 생성 완료 - notificationId: $notificationId")
            Log.d(TAG, "🔔 [NOTIFICATION] 알림 내용: ${notification.tickerText}")
            Log.d(TAG, "🔔 [NOTIFICATION] Android SDK: ${android.os.Build.VERSION.SDK_INT}")
            Log.d(TAG, "🔔 [NOTIFICATION] Android 버전: ${android.os.Build.VERSION.RELEASE}")

            // 실제 생성된 알림의 정보 확인
            Log.d(TAG, "🔔 [NOTIFICATION] 생성된 알림 정보:")
            Log.d(TAG, "🔔 [NOTIFICATION] - Channel ID: ${notification.channelId}")
            Log.d(TAG, "🔔 [NOTIFICATION] - Sound URI: ${notification.sound}")
            Log.d(TAG, "🔔 [NOTIFICATION] - Priority: ${notification.priority}")
            Log.d(TAG, "🔔 [NOTIFICATION] - Defaults: ${notification.defaults}")
            Log.d(TAG, "🔔 [NOTIFICATION] - Vibrate Pattern: ${notification.vibrate?.joinToString()}")

            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "🔔✅ [NOTIFICATION] notificationManager.notify() 호출 완료")
        } catch (e: Exception) {
            Log.e(TAG, "🔔❌ [NOTIFICATION] 알림 생성 실패: ${e.message}")
            e.printStackTrace()
        }

        Log.d(TAG, "🔔🔔🔔 [NOTIFICATION] showNotification 완전 종료 🔔🔔🔔")
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

    private fun isScreenOff(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            !powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            !powerManager.isScreenOn
        }
    }

    /**
     * 사무실 마감 상태 확인
     * SharedPreferences에 캐시된 사무실 상태를 확인
     */
    private fun isOfficeClosed(): Boolean {
        try {
            val prefs = getSharedPreferences("office_status_cache", Context.MODE_PRIVATE)
            val status = prefs.getString("current_office_status", "OPEN") ?: "OPEN"
            Log.d(TAG, "캐시된 사무실 상태: $status")

            val isClosed = status == "CLOSED" || status == "AUTO_SHARING"
            Log.d(TAG, "사무실 마감 상태: $isClosed")
            return isClosed
        } catch (e: Exception) {
            Log.e(TAG, "사무실 상태 확인 중 오류: ${e.message}")
            return false
        }
    }

    /**
     * Data-only FCM 메시지로부터 커스텀 공유콜 알림 생성
     * 전문가 권장: Ongoing Notification + Full-Screen Intent (향후 추가)
     */
    private fun showCustomSharedCallNotification(remoteMessage: RemoteMessage) {
        Log.d(TAG, "🔔 [CUSTOM] 커스텀 공유콜 알림 생성 시작")

        // 사무실 마감 상태 확인
        if (isOfficeClosed()) {
            Log.d(TAG, "🔔 [CUSTOM] 사무실 마감 상태 - 커스텀 공유콜 알림 차단")
            return
        }

        val data = remoteMessage.data
        val sharedCallId = data["sharedCallId"] ?: return
        val title = data["title"] ?: "🔄 새로운 공유콜!"
        val body = data["body"] ?: "공유콜이 도착했습니다"
        val customMessage = data["customMessage"] ?: body

        Log.d(TAG, "🔔 [CUSTOM] sharedCallId: $sharedCallId")
        Log.d(TAG, "🔔 [CUSTOM] title: $title")
        Log.d(TAG, "🔔 [CUSTOM] body: $body")

        // PendingIntent 생성 - SharedCallAcceptActivity로 직접 이동
        val intent = Intent(this, SharedCallAcceptActivity::class.java).apply {
            putExtra(SharedCallAcceptActivity.EXTRA_SHARED_CALL_ID, sharedCallId)
            putExtra(SharedCallAcceptActivity.EXTRA_TITLE, title)
            putExtra(SharedCallAcceptActivity.EXTRA_BODY, body)
            putExtra(SharedCallAcceptActivity.EXTRA_CUSTOM_MESSAGE, customMessage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            sharedCallId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full-Screen Intent 알림 생성 (전문가 권장 - 전화 수신처럼 즉시 전체화면)
        val notificationBuilder = NotificationCompat.Builder(this, "shared_call_fcm_channel_v3")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(customMessage))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL) // 전화 카테고리
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true) // ⭐ Full-Screen Intent 핵심
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = sharedCallId.hashCode()

        Log.d(TAG, "🔔 [CUSTOM] 알림 표시 - ID: $notificationId")
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "🔔 [CUSTOM] 커스텀 공유콜 알림 생성 완료")
    }

    /**
     * 알림 전달 실패 처리 (ACK 시스템)
     * 기사에게 알림 전달이 2회 실패 시 콜매니저에 경고
     */
    private fun handleNotificationFailure(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val callId = data["callId"] ?: return
        val driverName = data["driverName"] ?: "기사"
        val driverId = data["driverId"] ?: ""
        val presenceStatus = data["presenceStatus"] ?: "unknown"
        val message = data["message"] ?: "알림 전달 실패"
        val title = data["title"] ?: "⚠️ 알림 전달 실패"

        Log.d(TAG, "[NOTIFICATION_FAILURE] callId=$callId, driverName=$driverName, presenceStatus=$presenceStatus")

        // 브로드캐스트로 UI에 알림 (팝업 표시)
        val broadcastIntent = Intent("com.designated.callmanager.NOTIFICATION_FAILURE").apply {
            putExtra("callId", callId)
            putExtra("driverName", driverName)
            putExtra("driverId", driverId)
            putExtra("presenceStatus", presenceStatus)
            putExtra("message", message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

        // 시스템 알림도 표시
        val statusText = when (presenceStatus) {
            "offline" -> "앱 꺼짐 또는 네트워크 연결 끊김"
            "background" -> "앱이 백그라운드 상태"
            else -> "알림 전달 실패"
        }

        showNotification(
            channelId = STATUS_CHANGE_CHANNEL_ID,
            notificationId = "notification_failure_$callId".hashCode(),
            title = title,
            content = "$driverName 기사에게 알림 전달 실패",
            bigText = "$driverName 기사에게 알림 전달 실패\n상태: $statusText\n\n전화로 연락하거나 다른 기사를 배차해주세요.",
            callId = callId,
            color = ContextCompat.getColor(this, android.R.color.holo_red_dark),
            autoCancel = true,
            timeoutAfter = 60000  // 1분 후 자동 사라짐
        )
    }
}