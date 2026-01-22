package com.designated.customer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.designated.customer.MainActivity
import com.designated.customer.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "CustomerFCM"
        private const val CHANNEL_ID_CALL_RECEIVED = "call_received_channel"
        private const val CHANNEL_ID_DRIVER_ASSIGNED = "driver_assigned_channel"
        private const val CHANNEL_ID_RIDE_COMPLETED = "ride_completed_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "메시지 수신: ${message.data}")

        when (message.data["type"]) {
            "call_assigned" -> {
                // 기사앱용 알림 (사용 안함)
                Log.d(TAG, "기사앱용 알림 - 무시")
            }

            "CALL_RECEIVED" -> {
                // 콜 접수 완료 알림
                val callId = message.data["callId"]
                val notificationMessage = message.data["message"] ?: "콜이 접수되었습니다."

                Log.d(TAG, "콜 접수 알림 수신 - callId: $callId")

                // 접수 완료 브로드캐스트 전송
                sendCallReceivedBroadcast(callId, notificationMessage)

                // 알림 표시
                showCallReceivedNotification(callId, notificationMessage)
            }

            "DRIVER_ASSIGNED" -> {
                // 고객앱용 기사 배정 알림
                val callId = message.data["callId"]
                val driverName = message.data["driverName"] ?: "기사"
                val driverPhone = message.data["driverPhone"] ?: ""
                val vehicleNumber = message.data["vehicleNumber"] ?: ""
                val driverId = message.data["driverId"] ?: ""

                Log.d(TAG, "기사 배정 알림 수신 - callId: $callId, driverName: $driverName, driverPhone: $driverPhone")

                // 기사 배정 팝업을 위한 브로드캐스트 전송
                sendDriverAssignedBroadcast(callId, driverName, driverPhone, vehicleNumber, driverId)

                // 알림도 표시
                showDriverAssignedNotification(callId, driverName, driverPhone, vehicleNumber, driverId)
            }

            "RIDE_COMPLETED" -> {
                // 운행 완료 알림
                val callId = message.data["callId"]
                val fare = message.data["fare"]?.toIntOrNull() ?: 0
                val pointsUsed = message.data["pointsUsed"]?.toIntOrNull() ?: 0

                Log.d(TAG, "운행 완료 알림 수신 - callId: $callId, fare: $fare, pointsUsed: $pointsUsed")

                // 포인트 적립 팝업을 위한 브로드캐스트 전송
                sendRideCompletedBroadcast(callId, fare, pointsUsed)

                // 알림도 표시
                showRideCompletedNotification(fare, pointsUsed)
            }

            "CALL_CANCELLED" -> {
                // 기사가 운행 취소
                val callId = message.data["callId"]
                val cancelReason = message.data["cancelReason"] ?: "운행취소"

                Log.d(TAG, "기사 취소 알림 수신 - callId: $callId, cancelReason: $cancelReason")

                // 취소 브로드캐스트 전송 (팝업 제거용)
                sendCallCancelledBroadcast(callId, cancelReason)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "새 FCM 토큰: $token")

        // FCM 토큰을 Firestore에 저장
        saveFcmTokenToFirestore(token)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 콜 접수 알림 채널
            val callReceivedChannel = NotificationChannel(
                CHANNEL_ID_CALL_RECEIVED,
                "콜 접수 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "콜이 접수되었을 때 알림"
                enableVibration(true)
            }

            // 기사 배정 알림 채널
            val driverChannel = NotificationChannel(
                CHANNEL_ID_DRIVER_ASSIGNED,
                "기사 배정 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "기사가 배정되었을 때 알림"
                enableVibration(true)
            }

            // 운행 완료 알림 채널
            val completedChannel = NotificationChannel(
                CHANNEL_ID_RIDE_COMPLETED,
                "운행 완료 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "운행이 완료되었을 때 알림"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(callReceivedChannel)
            notificationManager.createNotificationChannel(driverChannel)
            notificationManager.createNotificationChannel(completedChannel)
        }
    }

    private fun showDriverAssignedNotification(callId: String?, driverName: String, driverPhone: String, vehicleNumber: String, driverId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // ✅ 기사 배정 정보 전달
            putExtra("showDriverAssigned", true)
            putExtra("callId", callId)
            putExtra("driverName", driverName)
            putExtra("driverPhone", driverPhone)
            putExtra("vehicleNumber", vehicleNumber)
            putExtra("driverId", driverId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // ✅ 고유한 requestCode 사용
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            append(driverName)
            if (vehicleNumber.isNotEmpty()) {
                append(" (")
                append(vehicleNumber)
                append(")")
            }
            if (driverPhone.isNotEmpty()) {
                append(" - ")
                append(driverPhone)
            }
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_DRIVER_ASSIGNED)
            .setContentTitle("기사 배정 완료")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1001, notification)
    }

    private fun showRideCompletedNotification(fare: Int, pointsUsed: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // ✅ 포인트 정보 전달
            putExtra("showPointsDialog", true)
            putExtra("fare", fare)
            putExtra("pointsUsed", pointsUsed)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // ✅ 고유한 requestCode 사용
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_RIDE_COMPLETED)
            .setContentTitle("운행 완료")
            .setContentText("포인트가 적립되었습니다! 앱을 확인하세요.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1002, notification)
    }

    private fun sendDriverAssignedBroadcast(
        callId: String?,
        driverName: String,
        driverPhone: String,
        vehicleNumber: String,
        driverId: String
    ) {
        val intent = Intent("com.designated.customer.DRIVER_ASSIGNED").apply {
            putExtra("callId", callId)
            putExtra("driverName", driverName)
            putExtra("driverPhone", driverPhone)
            putExtra("vehicleNumber", vehicleNumber)
            putExtra("driverId", driverId)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "기사 배정 브로드캐스트 전송 완료 - callId: $callId, driverName: $driverName")
    }

    private fun sendRideCompletedBroadcast(callId: String?, fare: Int, pointsUsed: Int) {
        val intent = Intent("com.designated.customer.RIDE_COMPLETED").apply {
            putExtra("callId", callId)
            putExtra("fare", fare)
            putExtra("pointsUsed", pointsUsed)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "운행 완료 브로드캐스트 전송 완료 - callId: $callId")
    }

    private fun sendCallCancelledBroadcast(callId: String?, cancelReason: String) {
        val intent = Intent("com.designated.customer.CALL_CANCELLED").apply {
            putExtra("callId", callId)
            putExtra("cancelReason", cancelReason)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "취소 브로드캐스트 전송 완료 - callId: $callId, reason: $cancelReason")
    }

    private fun sendCallReceivedBroadcast(callId: String?, message: String) {
        val intent = Intent("com.designated.customer.CALL_RECEIVED").apply {
            putExtra("callId", callId)
            putExtra("message", message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "콜 접수 브로드캐스트 전송 완료 - callId: $callId")
    }

    private fun showCallReceivedNotification(callId: String?, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("showCallReceived", true)
            putExtra("callId", callId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CALL_RECEIVED)
            .setContentTitle("콜 접수 완료")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1000, notification)
    }

    private fun saveFcmTokenToFirestore(token: String) {
        // SharedPreferences에서 phoneNumber, provinceId, cityId, officeId 가져오기
        val prefs = getSharedPreferences("customer_app_prefs", Context.MODE_PRIVATE)
        val phoneNumber = prefs.getString("phone_number", null)
        val provinceId = prefs.getString("province_id", null) ?: prefs.getString("region_id", null)
        val cityId = prefs.getString("city_id", null)
        val officeId = prefs.getString("office_id", null)

        if (phoneNumber.isNullOrEmpty() || provinceId.isNullOrEmpty() || cityId.isNullOrEmpty() || officeId.isNullOrEmpty()) {
            Log.w(TAG, "고객 정보가 없어 FCM 토큰 저장 스킵 - phoneNumber: $phoneNumber, provinceId: $provinceId, cityId: $cityId, officeId: $officeId")
            return
        }

        FirebaseFirestore.getInstance()
            .collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("customerInfo")
            .document(phoneNumber)
            .set(
                hashMapOf(
                    "fcmToken" to token,
                    "phoneNumber" to phoneNumber,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnSuccessListener {
                Log.d(TAG, "FCM 토큰 저장 완료 - phoneNumber: $phoneNumber, token: $token")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "FCM 토큰 저장 실패", e)
            }
    }
}
