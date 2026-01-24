package com.designated.driverapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.designated.driverapp.MainActivity
import com.designated.driverapp.R
import com.designated.driverapp.data.Constants
import com.designated.driverapp.model.CallInfo
import com.designated.driverapp.model.DriverStatus
import android.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "DriverForegroundService"
private const val SERVICE_STATUS_CHANNEL_ID = "DriverServiceStatusChannel"
private const val CALL_CHANNEL_ID = "DriverCallChannel"
private const val NOTIFICATION_ID = 1
private const val SERVICE_STATUS_NOTIFICATION_TITLE = "대리운전 기사앱"
private const val SERVICE_STATUS_NOTIFICATION_TEXT = "서비스 실행 중"

/**
 * FCM 기반 포그라운드 서비스
 * - Firestore 리스너 제거됨
 * - FCM 알림을 통해 콜 정보 수신
 * - 포그라운드 상태 유지 및 알림 관리만 담당
 */
class DriverForegroundService : Service() {

    private val _driverStatus = MutableStateFlow<DriverStatus>(DriverStatus.OFFLINE)
    val driverStatus: StateFlow<DriverStatus> = _driverStatus

    private val _assignedCall = MutableStateFlow<CallInfo?>(null)
    val assignedCall: StateFlow<CallInfo?> = _assignedCall

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DriverForegroundService onCreate")
        createNotificationChannel()

        // 기본 포그라운드 알림 시작
        val notification = createStatusNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        // FCM에서 전달받은 콜 정보 처리
        intent?.let { handleIntent(it) }

        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_NEW_CALL_ASSIGNED -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                Log.d(TAG, "새 콜 배정: callId=$callId")
                // 알림은 MyFirebaseMessagingService에서 생성하므로 여기서는 처리하지 않음
            }
            ACTION_CLEAR_CALL -> {
                Log.d(TAG, "콜 상태 클리어")
                clearAssignedCallState()
                // 기본 상태 알림으로 복원
                val notification = createStatusNotification()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE_STATUS -> {
                val status = intent.getStringExtra(EXTRA_DRIVER_STATUS)
                Log.d(TAG, "기사 상태 업데이트: $status")
                _driverStatus.value = DriverStatus.fromString(status)
            }
        }
    }

    fun clearAssignedCallState() {
        _assignedCall.value = null
    }

    fun updateDriverStatus(status: DriverStatus) {
        _driverStatus.value = status
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DriverForegroundService onDestroy")
    }

    private val binder = LocalBinder()

    inner class LocalBinder : android.os.Binder() {
        fun getService(): DriverForegroundService = this@DriverForegroundService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 긴급 콜 알림 채널
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "콜 배정 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "새로운 대리운전 호출이 배정되었을 때 알립니다."
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)

            // 서비스 상태 채널
            val statusChannel = NotificationChannel(
                SERVICE_STATUS_CHANNEL_ID,
                "서비스 실행 상태",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "앱 백그라운드 서비스 실행 상태를 표시합니다."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            notificationManager.createNotificationChannel(statusChannel)
        }
    }

    private fun createStatusNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SERVICE_STATUS_CHANNEL_ID)
            .setContentTitle(SERVICE_STATUS_NOTIFICATION_TITLE)
            .setContentText(SERVICE_STATUS_NOTIFICATION_TEXT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_NEW_CALL_ASSIGNED = "com.designated.driverapp.ACTION_NEW_CALL_ASSIGNED"
        const val ACTION_CLEAR_CALL = "com.designated.driverapp.ACTION_CLEAR_CALL"
        const val ACTION_UPDATE_STATUS = "com.designated.driverapp.ACTION_UPDATE_STATUS"

        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_DRIVER_STATUS = "driverStatus"

        fun newCallAssignedIntent(context: Context, callId: String, title: String, body: String): Intent {
            return Intent(context, DriverForegroundService::class.java).apply {
                action = ACTION_NEW_CALL_ASSIGNED
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
            }
        }

        fun clearCallIntent(context: Context): Intent {
            return Intent(context, DriverForegroundService::class.java).apply {
                action = ACTION_CLEAR_CALL
            }
        }
    }
}
