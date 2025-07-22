package com.designated.callmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.designated.callmanager.MainActivity
import com.designated.callmanager.R
import com.designated.callmanager.data.Constants
import com.designated.callmanager.ui.dashboard.DashboardViewModel
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.content.ContextCompat
import android.content.pm.ServiceInfo
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import android.media.RingtoneManager

class CallManagerService : Service() {
    companion object {
        private const val TAG = "CallManagerService"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val SERVICE_CHANNEL_ID = "CallManagerServiceChannel"
        const val ACTION_CALL_UPDATED = "com.designated.callmanager.ACTION_CALL_UPDATED"
        const val EXTRA_CALL_ID = "com.designated.callmanager.EXTRA_CALL_ID"
        const val EXTRA_CALL_STATUS = "com.designated.callmanager.EXTRA_CALL_STATUS"
        const val EXTRA_CALL_SUMMARY = "com.designated.callmanager.EXTRA_CALL_SUMMARY"

        var isServiceRunning = false
    }

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var sharedPreferences: SharedPreferences

    // ⚠️ FCM 토큰 방식 전환으로 리스너 관련 변수들 제거됨
    // private var callsListener, connectionListener, isListenerAttached 등
    // 모든 알림은 MyFirebaseMessagingService를 통해 처리됨

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "서비스 생성됨")
        sharedPreferences = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        isServiceRunning = true
        val notification = createForegroundServiceNotification("서비스 실행 중", "콜 데이터를 실시간으로 수신하고 있습니다.")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
        // FCM 토큰 방식 사용으로 리스너 비활성화
        // setupCallListener() // 제거됨 - FCM 토큰 방식으로 대체
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand 호출됨, action: ${intent?.action}")
        when (intent?.action) {
            DashboardViewModel.ACTION_START_SERVICE -> {
                Log.d(TAG, "서비스 시작 액션 수신")
                if (!isServiceRunning) {
                    startForeground(
                        FOREGROUND_NOTIFICATION_ID,
                        createForegroundServiceNotification()
                    )
                    isServiceRunning = true
                    Log.d(TAG, "Foreground 서비스 시작됨 (from intent)")
                }
            }

            DashboardViewModel.ACTION_STOP_SERVICE -> {
                Log.d(TAG, "서비스 중지 액션 수신")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isServiceRunning = false
                Log.d(TAG, "Foreground 서비스 중지 및 자체 종료됨")
            }

            else -> {
                if (!isServiceRunning) {
                    Log.d(TAG, "서비스가 시스템에 의해 재시작됨")
                    startForeground(
                        FOREGROUND_NOTIFICATION_ID,
                        createForegroundServiceNotification()
                    )
                    isServiceRunning = true
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "서비스 종료됨 - FCM 토큰 방식 사용 중")
        isServiceRunning = false
        // stopFirebaseListeners() 제거됨 - FCM 토큰 방식에서는 불필요
    }

    private fun createForegroundServiceNotification(title: String = "대리운전 콜 관리", text: String = "서비스 실행 중"): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "콜 매니저 서비스",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d(TAG, "Foreground 서비스 알림 채널 생성됨: $SERVICE_CHANNEL_ID")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    // ⚠️ 리스너 방식 제거 - FCM 토큰 방식으로 완전 대체됨
    // setupCallListener() 함수는 더 이상 사용하지 않음
    // 모든 알림은 Firebase Functions + FCM을 통해 처리됨

    // ⚠️ FCM 토큰 방식 전환으로 stopFirebaseListeners() 함수 제거됨
    // Firebase 리스너가 없으므로 정리할 것이 없음

    private fun broadcastCallUpdate(callId: String, status: String, summary: String? = null) {
        val intent = Intent(ACTION_CALL_UPDATED).apply {
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALL_STATUS, status)
            summary?.let { putExtra(EXTRA_CALL_SUMMARY, it) }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun updateNotification(title: String, text: String) {
        val notification = createForegroundServiceNotification(title, text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }
    
    // ⚠️ 로컬 알림 함수들 제거 - FCM을 통해 서버에서 처리됨
    // showStatusChangeNotification() 및 showNewCallNotification() 함수는
    // MyFirebaseMessagingService.kt에서 FCM 메시지로 처리됨
}