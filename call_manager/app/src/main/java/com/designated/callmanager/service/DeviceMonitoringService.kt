package com.designated.callmanager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.designated.callmanager.MainActivity
import com.designated.callmanager.R
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * 콜디텍터 디바이스 모니터링 서비스
 * Crashlytics 알림을 실시간으로 수신하여 처리
 */
class DeviceMonitoringService(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var emergencyAlertsListener: ListenerRegistration? = null
    private var deviceAlertsListener: ListenerRegistration? = null
    private var deviceStatusListener: ListenerRegistration? = null

    private val processedCrashes = mutableSetOf<String>()

    companion object {
        private const val CHANNEL_ID = "device_monitoring_channel"
        private const val EMERGENCY_CHANNEL_ID = "emergency_alert_channel"
        private const val NOTIFICATION_BASE_ID = 5000

        private const val CRASH_NOTIFICATION_OFFSET = 0
        private const val ANR_NOTIFICATION_OFFSET = 100
        private const val MEMORY_NOTIFICATION_OFFSET = 200
        private const val OFFLINE_NOTIFICATION_OFFSET = 300
    }

    init {
        createNotificationChannels()
    }

    /**
     * 알림 채널 생성
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitoringChannel = NotificationChannel(
                CHANNEL_ID,
                "디바이스 모니터링",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "콜디텍터 디바이스 상태 모니터링"
                enableLights(true)
                lightColor = Color.YELLOW
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val emergencyChannel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                "긴급 디바이스 알림",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "콜디텍터 강제종료 등 긴급 상황"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
            }

            notificationManager.createNotificationChannels(
                listOf(monitoringChannel, emergencyChannel)
            )
        }
    }

    /**
     * 모니터링 시작 (강화된 실시간 리스너)
     */
    fun startMonitoring(provinceId: String, cityId: String, officeId: String) {
        stopMonitoring()

        emergencyAlertsListener = firestore.collection("emergency_alerts")
            .whereEqualTo("provinceId", provinceId)
            .whereEqualTo("cityId", cityId)
            .whereEqualTo("officeId", officeId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            val data = change.document.data
                            val deviceId = data["deviceId"] as? String ?: "unknown"
                            val crashTime = data["crashTime"] as? Long ?: System.currentTimeMillis()

                            val crashKey = "${deviceId}_${crashTime}"

                            if (processedCrashes.contains(crashKey)) {
                                return@forEach
                            }

                            processedCrashes.add(crashKey)
                            handleEmergencyAlert(data)
                        }
                        DocumentChange.Type.MODIFIED -> {
                            }
                        else -> {
                            }
                    }
                }
            }

        }

    /**
     * 긴급 알림 처리 (강화된 로깅 및 알림)
     */
    private fun handleEmergencyAlert(data: Map<String, Any>) {
        val deviceId = data["deviceId"] as? String ?: "Unknown"
        val message = data["message"] as? String ?: "긴급 상황 발생"
        val type = data["type"] as? String ?: "UNKNOWN"

        when (type) {
            "EMERGENCY_CRASH_ALERT" -> {
                showCrashNotification(deviceId, message, true)
                updateDeviceStatusUI(deviceId, "CRASHED")
                }
            else -> {
                showCrashNotification(deviceId, "알 수 없는 긴급 상황: $message", true)
            }
        }
    }

    /**
     * 일반 디바이스 알림 처리
     */
    private fun handleDeviceAlert(data: Map<String, Any>) {
        val deviceId = data["deviceId"] as? String ?: "Unknown"
        val type = data["type"] as? String ?: "UNKNOWN"
        val status = data["status"] as? String

        when (type) {
            "CRASH" -> {
                val crashInfo = data["crashInfo"] as? Map<*, *>
                val message = crashInfo?.get("message") as? String ?: "알 수 없는 오류"
                showCrashNotification(deviceId, "크래시 발생: $message", false)
            }

            "PREVIOUS_CRASH_DETECTED" -> {
                showNotification(
                    deviceId,
                    "디바이스 복구됨",
                    "[$deviceId] 비정상 종료 후 재시작되었습니다",
                    CRASH_NOTIFICATION_OFFSET,
                    NotificationCompat.PRIORITY_HIGH
                )
            }

            "ANR" -> {
                val reason = data["reason"] as? String ?: "응답 없음"
                showNotification(
                    deviceId,
                    "ANR 발생",
                    "[$deviceId] $reason",
                    ANR_NOTIFICATION_OFFSET,
                    NotificationCompat.PRIORITY_HIGH
                )
            }

            "LOW_MEMORY" -> {
                val memoryUsage = data["memoryUsage"] as? Long ?: 0
                showNotification(
                    deviceId,
                    "메모리 부족 경고",
                    "[$deviceId] 메모리 사용률: $memoryUsage%",
                    MEMORY_NOTIFICATION_OFFSET,
                    NotificationCompat.PRIORITY_DEFAULT
                )
            }

            "SERVICE_RESTARTED_AFTER_CRASH" -> {
                updateDeviceStatusUI(deviceId, "RECOVERED")
            }
        }
    }

    /**
     * 크래시 알림 표시 (강화된 로깅)
     */
    private fun showCrashNotification(deviceId: String, message: String, isEmergency: Boolean) {
        val channelId = if (isEmergency) EMERGENCY_CHANNEL_ID else CHANNEL_ID
        val priority = if (isEmergency) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_SHOW_DEVICE_CRASH"
            putExtra("deviceId", deviceId)
            putExtra("timestamp", System.currentTimeMillis())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            deviceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ 콜디텍터 강제종료")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$message\n\n즉시 확인이 필요합니다.\n시간: ${getCurrentTime()}"
            ))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        if (isEmergency) {
            notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT
        }

        val notificationId = NOTIFICATION_BASE_ID + CRASH_NOTIFICATION_OFFSET + deviceId.hashCode()
        notificationManager.notify(notificationId, notification)
    }

    /**
     * 일반 알림 표시
     */
    private fun showNotification(
        deviceId: String,
        title: String,
        message: String,
        offset: Int,
        priority: Int
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        val notificationId = NOTIFICATION_BASE_ID + offset + deviceId.hashCode()
        notificationManager.notify(notificationId, notification)
    }

    /**
     * 디바이스 상태 주기적 모니터링
     */
    private fun startDeviceStatusMonitoring(provinceId: String, cityId: String, officeId: String) {
        scope.launch {
            while (true) {
                checkDeviceHealth(provinceId, cityId, officeId)
                kotlinx.coroutines.delay(60000)
            }
        }
    }

    /**
     * 디바이스 건강 상태 체크
     */
    private fun checkDeviceHealth(provinceId: String, cityId: String, officeId: String) {
        val cutoffTime = System.currentTimeMillis() - (5 * 60 * 1000)

        firestore.collection("device_status")
            .whereEqualTo("provinceId", provinceId)
            .whereEqualTo("cityId", cityId)
            .whereEqualTo("officeId", officeId)
            .get()
            .addOnSuccessListener { documents ->
                documents.forEach { doc ->
                    val lastUpdate = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0
                    val deviceId = doc.id

                    if (lastUpdate < cutoffTime) {
                        showNotification(
                            deviceId,
                            "디바이스 오프라인 의심",
                            "[$deviceId] 5분 이상 응답 없음",
                            OFFLINE_NOTIFICATION_OFFSET,
                            NotificationCompat.PRIORITY_DEFAULT
                        )
                    }
                }
            }
    }

    /**
     * UI 업데이트 (ViewModel과 연동)
     */
    private fun updateDeviceStatusUI(deviceId: String, status: String) {
    }

    /**
     * 현재 시간 포맷
     */
    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * 모니터링 중지
     */
    fun stopMonitoring() {
        emergencyAlertsListener?.remove()
        deviceAlertsListener?.remove()
        deviceStatusListener?.remove()

        // 코루틴 스코프 안전하게 해제
        try {
            scope.cancel()
        } catch (e: Exception) {
            Log.w("DeviceMonitoringService", "Error cancelling scope: ${e.message}")
        }

        processedCrashes.clear()
    }
}