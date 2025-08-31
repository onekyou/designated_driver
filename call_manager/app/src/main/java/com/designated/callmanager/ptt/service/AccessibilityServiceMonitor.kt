package com.designated.callmanager.ptt.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.designated.callmanager.R
import com.designated.callmanager.ptt.ui.PTTAccessibilityGuideActivity
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * 접근성 서비스 상태 모니터링 클래스
 * 서비스 비활성화 감지 및 재활성화 알림 제공
 */
class AccessibilityServiceMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "AccessibilityServiceMonitor"
        private const val CHANNEL_ID = "ptt_service_monitoring"
        private const val NOTIFICATION_ID = 3001
        private const val SERVICE_CHECK_INTERVAL = 30_000L // 30초
        
        // WorkManager 관련
        private const val WORK_NAME = "PTT_SERVICE_MONITOR_WORK"
        private const val WORK_TAG = "ptt_monitoring"
    }
    
    private var monitoringJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        createNotificationChannel()
    }
    
    /**
     * 모니터링 시작
     */
    fun startMonitoring() {
        Log.i(TAG, "Starting accessibility service monitoring")
        
        // 코루틴 기반 모니터링
        startCoroutineMonitoring()
        
        // WorkManager 기반 백그라운드 모니터링
        startWorkManagerMonitoring()
    }
    
    /**
     * 모니터링 중지
     */
    fun stopMonitoring() {
        Log.i(TAG, "Stopping accessibility service monitoring")
        
        // 코루틴 취소
        monitoringJob?.cancel()
        serviceScope.cancel()
        
        // WorkManager 취소
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
    
    /**
     * 코루틴 기반 실시간 모니터링
     */
    private fun startCoroutineMonitoring() {
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkAndNotify()
                    delay(SERVICE_CHECK_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop", e)
                    delay(SERVICE_CHECK_INTERVAL) // 에러 시에도 계속 모니터링
                }
            }
        }
    }
    
    /**
     * WorkManager 기반 백그라운드 모니터링
     */
    private fun startWorkManagerMonitoring() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()
        
        val monitoringWork = PeriodicWorkRequestBuilder<ServiceMonitorWorker>(
            15, TimeUnit.MINUTES // 최소 주기
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            monitoringWork
        )
        
        Log.d(TAG, "WorkManager monitoring started")
    }
    
    /**
     * 서비스 상태 체크 및 알림
     */
    private suspend fun checkAndNotify() {
        val isEnabled = isAccessibilityServiceEnabled()
        Log.v(TAG, "Service check - enabled: $isEnabled")
        
        if (!isEnabled) {
            Log.w(TAG, "Accessibility service is disabled, sending notification")
            showReactivationNotification()
            
            // Android 11+ 에서 자동 재활성화 시도
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tryAutoReactivation()
            }
        } else {
            // 서비스가 활성화되어 있으면 알림 제거
            hideReactivationNotification()
        }
    }
    
    /**
     * 접근성 서비스 활성화 상태 확인
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            val serviceName = "${context.packageName}/${PTTAccessibilityService::class.java.name}"
            val isEnabled = enabledServices.contains(serviceName, ignoreCase = true)
            
            Log.v(TAG, "Checking service: $serviceName, enabled: $isEnabled")
            isEnabled
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service status", e)
            false
        }
    }
    
    /**
     * 재활성화 알림 표시
     */
    fun showReactivationNotification() {
        val intent = Intent(context, PTTAccessibilityGuideActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        val settingsPendingIntent = PendingIntent.getActivity(
            context,
            1,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_assign)
            .setContentTitle("PTT 서비스 비활성화됨")
            .setContentText("볼륨키 PTT를 사용하려면 서비스를 다시 활성화하세요")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_assign, // 아이콘 리소스
                "가이드 보기",
                pendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_preferences,
                "설정 열기",
                settingsPendingIntent
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("PTT 접근성 서비스가 비활성화되었습니다. 볼륨키로 PTT를 사용하려면 서비스를 다시 활성화해주세요.")
            )
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        Log.i(TAG, "Reactivation notification shown")
    }
    
    /**
     * 재활성화 알림 숨기기
     */
    private fun hideReactivationNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
    
    /**
     * 자동 재활성화 시도 (Android 11+)
     */
    private suspend fun tryAutoReactivation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Log.d(TAG, "Attempting auto-reactivation for Android 11+")
                // Android 11+ 에서는 보안상 자동 활성화가 제한적
                // 사용자 액션이 필요함
            } catch (e: Exception) {
                Log.e(TAG, "Auto-reactivation failed", e)
            }
        }
    }
    
    /**
     * 알림 채널 생성
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PTT 서비스 모니터링",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "PTT 접근성 서비스 상태 알림"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
            
            Log.d(TAG, "Notification channel created")
        }
    }
    
    /**
     * 서비스 상태 정보 제공
     */
    fun getServiceStatus(): ServiceStatus {
        val isEnabled = isAccessibilityServiceEnabled()
        val lastCheckTime = System.currentTimeMillis()
        
        return ServiceStatus(
            isEnabled = isEnabled,
            lastCheckTime = lastCheckTime,
            isMonitoring = monitoringJob?.isActive ?: false
        )
    }
}

/**
 * WorkManager를 위한 Worker 클래스
 */
class ServiceMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val TAG = "ServiceMonitorWorker"
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "WorkManager monitoring check started")
            
            val monitor = AccessibilityServiceMonitor(applicationContext)
            val isEnabled = monitor.isAccessibilityServiceEnabled()
            
            if (!isEnabled) {
                Log.w(TAG, "Service disabled, showing notification")
                monitor.showReactivationNotification()
            }
            
            Log.d(TAG, "WorkManager monitoring check completed")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager monitoring failed", e)
            Result.retry()
        }
    }
}

/**
 * 서비스 상태 데이터 클래스
 */
data class ServiceStatus(
    val isEnabled: Boolean,
    val lastCheckTime: Long,
    val isMonitoring: Boolean
)