package com.designated.callmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.designated.callmanager.MainActivity
import com.designated.callmanager.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * 백그라운드 PTT 통신 서비스
 * - 화면 꺼짐/백그라운드 상태에서도 PTT 통신 가능
 * - Wake Lock 및 배터리 최적화 예외 처리
 * - 백그라운드 오디오 세션 관리
 */
class BackgroundPTTService : Service() {
    
    companion object {
        private const val TAG = "BackgroundPTTService"
        private const val CHANNEL_ID = "background_ptt_channel"
        private const val NOTIFICATION_ID = 1002
        
        const val ACTION_START_BACKGROUND_PTT = "start_background_ptt"
        const val ACTION_STOP_BACKGROUND_PTT = "stop_background_ptt"
        const val ACTION_PTT_PRESSED = "ptt_pressed"
        const val ACTION_PTT_RELEASED = "ptt_released"
        
        @Volatile
        private var isServiceRunning = false
        
        fun isRunning() = isServiceRunning
    }
    
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var powerManager: PowerManager
    private lateinit var audioManager: AudioManager
    private lateinit var database: FirebaseDatabase
    
    // Wake Lock for screen-off operation
    private var wakeLock: PowerManager.WakeLock? = null
    private var pttManager: PTTManager? = null
    
    // PTT 세션 감지
    private var pttSessionRef: DatabaseReference? = null
    private var backgroundSessionListener: ValueEventListener? = null
    
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "BackgroundPTTService 생성됨")
        
        sharedPreferences = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        setupFirebase()
        createNotificationChannel()
        acquireWakeLock()
        
        isServiceRunning = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand 호출됨, action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_BACKGROUND_PTT -> {
                startBackgroundPTTService()
            }
            ACTION_STOP_BACKGROUND_PTT -> {
                stopBackgroundPTTService()
            }
            ACTION_PTT_PRESSED -> {
                handleBackgroundPTTPressed()
            }
            ACTION_PTT_RELEASED -> {
                handleBackgroundPTTReleased()
            }
            else -> {
                // 기본 시작
                startBackgroundPTTService()
            }
        }
        
        return START_STICKY // 시스템에 의해 종료되면 재시작
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "BackgroundPTTService 종료됨")
        
        cleanupBackgroundPTT()
        releaseWakeLock()
        isServiceRunning = false
    }
    
    /**
     * 백그라운드 PTT 서비스 시작
     */
    private fun startBackgroundPTTService() {
        Log.i(TAG, "백그라운드 PTT 서비스 시작")
        
        val notification = createPTTNotification(
            "PTT 백그라운드 통신 활성화",
            "화면 꺼짐 상태에서도 PTT 통신이 가능합니다"
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        // PTTManager 초기화 및 백그라운드 세션 감지 시작
        initializeBackgroundPTTManager()
        setupBackgroundPTTSessionListener()
        
    }
    
    /**
     * 백그라운드 PTT 서비스 중지
     */
    private fun stopBackgroundPTTService() {
        Log.i(TAG, "백그라운드 PTT 서비스 중지")
        cleanupBackgroundPTT()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /**
     * Firebase 설정
     */
    private fun setupFirebase() {
        try {
            database = FirebaseDatabase.getInstance("https://calldetector-5d61e-default-rtdb.firebaseio.com/")
            Log.i(TAG, "Firebase Database 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase 설정 실패", e)
        }
    }
    
    /**
     * 백그라운드 PTTManager 초기화
     */
    private fun initializeBackgroundPTTManager() {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.w(TAG, "백그라운드 PTTManager 초기화 실패: 로그인되지 않음")
                return
            }
            
            val region = sharedPreferences.getString("regionId", null)
            val office = sharedPreferences.getString("officeId", null)
            
            if (region.isNullOrEmpty() || office.isNullOrEmpty()) {
                Log.w(TAG, "백그라운드 PTTManager 초기화 실패: region 또는 office 정보 없음")
                return
            }
            
            Log.i(TAG, "백그라운드 PTTManager 초기화 - region: $region, office: $office")
            
            pttManager = PTTManager.getInstance(
                context = applicationContext,
                userType = "call_manager_background",
                regionId = region,
                officeId = office
            )
            
            // 백그라운드 오디오 세션 설정
            pttManager?.setBackgroundAudioMode(true)
            
            pttManager?.initialize(object : PTTManager.PTTCallback {
                override fun onStatusChanged(status: String) {
                    Log.d(TAG, "백그라운드 PTT 상태: $status")
                    updateNotification("PTT 백그라운드 활성", status)
                }
                
                override fun onConnectionStateChanged(isConnected: Boolean) {
                    Log.i(TAG, "백그라운드 PTT 연결: $isConnected")
                    if (isConnected) {
                        updateNotification("PTT 백그라운드 연결됨", "언제든지 PTT 통신 가능")
                    }
                }
                
                override fun onSpeakingStateChanged(isSpeaking: Boolean) {
                    Log.i(TAG, "백그라운드 PTT 송신: $isSpeaking")
                    if (isSpeaking) {
                        // 화면 켜기 (선택적)
                        turnScreenOnForPTT()
                        updateNotification("PTT 송신 중", "백그라운드에서 송신하고 있습니다")
                    }
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "백그라운드 PTT 오류: $error")
                    updateNotification("PTT 오류", error)
                }
            })
            
            Log.i(TAG, "✅ 백그라운드 PTTManager 초기화 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "백그라운드 PTTManager 초기화 중 오류", e)
        }
    }
    
    /**
     * 백그라운드 PTT 세션 리스너 설정
     * - 다른 앱에서 PTT 시작 시 자동으로 채널 참여
     */
    private fun setupBackgroundPTTSessionListener() {
        try {
            val region = sharedPreferences.getString("regionId", null)
            val office = sharedPreferences.getString("officeId", null)
            
            if (region.isNullOrEmpty() || office.isNullOrEmpty()) {
                Log.w(TAG, "PTT 세션 리스너 설정 실패: region/office 정보 없음")
                return
            }
            
            pttSessionRef = database.getReference("ptt_sessions/${region}_${office}")
            
            backgroundSessionListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val status = snapshot.child("status").getValue(String::class.java)
                        val initiator = snapshot.child("initiator").getValue(String::class.java)
                        
                        Log.i(TAG, "백그라운드 PTT 세션 변화 감지 - status: $status, initiator: $initiator")
                        
                        when (status) {
                            "active" -> {
                                if (initiator != FirebaseAuth.getInstance().currentUser?.uid) {
                                    Log.i(TAG, "🎯 백그라운드에서 PTT 자동 참여 시작")
                                    Handler(Looper.getMainLooper()).post {
                                        pttManager?.handleVolumeDownPress()
                                    }
                                }
                            }
                            "inactive" -> {
                                Log.i(TAG, "백그라운드 PTT 세션 종료 감지")
                                Handler(Looper.getMainLooper()).post {
                                    pttManager?.handleVolumeDownRelease()
                                }
                            }
                        }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "백그라운드 PTT 세션 리스너 오류", error.toException())
                }
            }
            
            pttSessionRef?.addValueEventListener(backgroundSessionListener!!)
            Log.i(TAG, "백그라운드 PTT 세션 리스너 설정 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "백그라운드 PTT 세션 리스너 설정 실패", e)
        }
    }
    
    /**
     * 백그라운드에서 PTT 버튼 눌림 처리
     */
    private fun handleBackgroundPTTPressed() {
        Log.i(TAG, "백그라운드에서 PTT 버튼 눌림 처리")
        
        // Wake Lock 확보 (화면이 꺼져있어도 처리)
        acquireWakeLock()
        
        // PTTManager로 전달
        pttManager?.handleVolumeDownPress()
        
        // 필요시 화면 켜기
        turnScreenOnForPTT()
    }
    
    /**
     * 백그라운드에서 PTT 버튼 뗌 처리
     */
    private fun handleBackgroundPTTReleased() {
        Log.i(TAG, "백그라운드에서 PTT 버튼 뗌 처리")
        
        // PTTManager로 전달
        pttManager?.handleVolumeDownRelease()
    }
    
    /**
     * Wake Lock 획득 (화면 꺼져도 서비스 동작)
     */
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CallManager:BackgroundPTTWakeLock"
                )
                wakeLock?.acquire(10 * 60 * 1000L) // 10분
                Log.i(TAG, "Wake Lock 획득됨")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wake Lock 획득 실패", e)
        }
    }
    
    /**
     * Wake Lock 해제
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "Wake Lock 해제됨")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Wake Lock 해제 실패", e)
        }
    }
    
    /**
     * PTT 송신 시 화면 켜기 (선택적 기능)
     */
    private fun turnScreenOnForPTT() {
        try {
            if (!powerManager.isInteractive) {
                Log.i(TAG, "PTT 송신을 위해 화면 켜기")
                
                val screenWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "CallManager:PTTScreenWakeLock"
                )
                screenWakeLock.acquire(3000) // 3초간만 화면 켜기
                
                // 3초 후 자동 해제
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (screenWakeLock.isHeld) {
                            screenWakeLock.release()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Screen Wake Lock 해제 실패", e)
                    }
                }, 3000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "화면 켜기 실패", e)
        }
    }
    
    
    /**
     * 백그라운드 PTT 정리
     */
    private fun cleanupBackgroundPTT() {
        try {
            // PTT 세션 리스너 제거
            backgroundSessionListener?.let { listener ->
                pttSessionRef?.removeEventListener(listener)
            }
            backgroundSessionListener = null
            pttSessionRef = null
            
            // PTTManager 정리
            pttManager?.destroy()
            pttManager = null
            
            Log.i(TAG, "백그라운드 PTT 정리 완료")
        } catch (e: Exception) {
            Log.e(TAG, "백그라운드 PTT 정리 실패", e)
        }
    }
    
    /**
     * 알림 채널 생성
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "백그라운드 PTT 통신",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "화면 꺼짐 상태에서도 PTT 통신을 유지합니다"
                setShowBadge(false)
                setSound(null, null)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    /**
     * PTT 알림 생성
     */
    private fun createPTTNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * 알림 업데이트
     */
    private fun updateNotification(title: String, content: String) {
        try {
            val notification = createPTTNotification(title, content)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "알림 업데이트 실패", e)
        }
    }
}