package com.designated.driverapp

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.designated.driverapp.data.Constants
import com.designated.driverapp.service.DriverForegroundService
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMsgService"

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
            val db = Firebase.firestore
            val driverRef = db.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                .collection(Constants.COLLECTION_CITIES).document(cityId)
                .collection(Constants.COLLECTION_OFFICES).document(officeId)
                .collection(Constants.COLLECTION_DRIVERS).document(userId)

            driverRef.update(Constants.FIELD_FCM_TOKEN, token)
                .addOnSuccessListener { }
                .addOnFailureListener { e -> }
        } else {
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

        if (!callId.isNullOrBlank() && messageType == "call_assigned") {
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
        } else {
            // 기타 알림
            showNotification(title, body, callId)
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    private fun showNotification(title: String, body: String, callId: String?) {
        val channelId = "call_assignment_channel"
        val notificationId = if (callId != null) callId.hashCode() else System.currentTimeMillis().toInt()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 기본 알림 소리
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 기존 채널 삭제 후 재생성 (채널 설정은 최초 생성 시에만 적용되므로)
            notificationManager.deleteNotificationChannel(channelId)

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
                setSound(defaultSoundUri, audioAttributes)  // 소리 설정 추가
                enableLights(true)  // LED 알림 (지원 기기)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC  // 잠금화면 표시
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (callId != null) {
                putExtra("callId", callId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Full-screen intent용 PendingIntent (백그라운드에서 화면 띄우기)
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            notificationId + 1,
            intent,
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
}
