package com.designated.driverapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.designated.driverapp.data.Constants
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

        // 앱이 포그라운드에 있을 때도 MainActivity로 callId 전달하여 팝업 표시
        if (!callId.isNullOrBlank()) {
            Log.d(TAG, "MainActivity로 callId 전달: $callId")
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("callId", callId)
            }
            startActivity(intent)
        }

        showNotification(title, body, callId)
    }

    private fun showNotification(title: String, body: String, callId: String?) {
        val channelId = "DriverServiceChannel"
        val notificationId = System.currentTimeMillis().toInt()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "콜 알림",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (callId != null) {
                putExtra("callId", callId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(notificationId, builder.build())
    }
}
