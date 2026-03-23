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
import kotlinx.coroutines.cancel
import android.util.Log
import android.media.RingtoneManager
import com.google.firebase.auth.FirebaseAuth

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

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        isServiceRunning = true
        val notification = createForegroundServiceNotification("서비스 실행 중", "콜 데이터를 실시간으로 수신하고 있습니다.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            DashboardViewModel.ACTION_START_SERVICE -> {
                if (!isServiceRunning) {
                    startForeground(
                        FOREGROUND_NOTIFICATION_ID,
                        createForegroundServiceNotification()
                    )
                    isServiceRunning = true
                }
            }

            DashboardViewModel.ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isServiceRunning = false
            }

            else -> {
                if (!isServiceRunning) {
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

        // 코루틴 스코프 안전하게 해제 (경합 조건 방지)
        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling serviceScope: ${e.message}")
        }

        isServiceRunning = false
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

}