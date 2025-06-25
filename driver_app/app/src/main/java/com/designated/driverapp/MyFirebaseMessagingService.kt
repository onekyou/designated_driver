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
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            Log.w(TAG, "Cannot send token to server, user is not logged in.")
            return
        }

        // SharedPreferences에서 regionId와 officeId를 가져옵니다.
        // 이 방법은 서비스에서 Context를 사용할 수 있을 때 가능합니다.
        // getApplicationContext()를 사용하여 SharedPreferences 인스턴스를 가져옵니다.
        val sharedPreferences = applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val regionId = sharedPreferences.getString(Constants.PREF_KEY_REGION_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)

        if (userId.isNotBlank() && !regionId.isNullOrBlank() && !officeId.isNullOrBlank()) {
            val db = Firebase.firestore
            val driverRef = db.collection(Constants.COLLECTION_REGIONS).document(regionId)
                .collection(Constants.COLLECTION_OFFICES).document(officeId)
                .collection(Constants.COLLECTION_DRIVERS).document(userId)

            driverRef.update(Constants.FIELD_FCM_TOKEN, token)
                .addOnSuccessListener { Log.d(TAG, "FCM token updated successfully in onNewToken.") }
                .addOnFailureListener { e -> Log.e(TAG, "Error updating FCM token in onNewToken", e) }
        } else {
            Log.w(TAG, "Cannot update token in onNewToken: user, region, or office ID is missing.")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "콜 배정 알림"
        val body = remoteMessage.notification?.body ?: "새로운 콜이 배정되었습니다."

        // 데이터 페이로드에서 callId 추출
        val callId = remoteMessage.data["callId"]

        showNotification(title, body, callId)
    }

    private fun showNotification(title: String, body: String, callId: String?) {
        val channelId = "DriverServiceChannel"
        val notificationId = System.currentTimeMillis().toInt()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 채널 생성 (Android 8.0+)
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
            // callId가 있는 경우 Intent에 추가
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
