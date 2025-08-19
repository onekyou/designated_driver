package com.designated.callmanager.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.content.Context
import android.view.WindowManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import com.designated.callmanager.R
import com.designated.callmanager.MainActivity

/**
 * Android 15 호환 PTT Accessibility Service
 * - Android 15에서 강화된 접근성 서비스 키 이벤트 처리
 * - 화면 꺼진 상태에서 PTT 송신 지원
 * - 향상된 로깅 및 디버깅
 */
class PTTAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PTTAccessibilityService"
        
        @Volatile
        private var isServiceRunning = false
        
        fun isRunning() = isServiceRunning
    }
    
    // 볼륨키 상태 추적
    private var isVolumeDownPressed = false
    
    // Wake Lock 관리
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private lateinit var powerManager: PowerManager

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "============================================")
        Log.i(TAG, "PTTAccessibilityService 생성됨")
        Log.i(TAG, "Android 버전: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        Log.i(TAG, "============================================")
        
        // PowerManager 초기화
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        // Android 15에서 서비스 유지를 위한 Foreground Service 시작
        if (Build.VERSION.SDK_INT >= 35) { // Android 15
            startForegroundServiceForAndroid15()
        }
        
        // Wake Lock 획득 (화면 꺼져도 키 이벤트 처리 가능)
        acquireWakeLocks()
        
        isServiceRunning = true
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "✅ PTTAccessibilityService 연결됨")
        
        // Android 15에서 서비스 정보 로깅
        try {
            val serviceInfo = serviceInfo
            Log.i(TAG, "서비스 정보:")
            Log.i(TAG, "  - flags: ${serviceInfo?.flags}")
            Log.i(TAG, "  - eventTypes: ${serviceInfo?.eventTypes}")
            Log.i(TAG, "  - feedbackType: ${serviceInfo?.feedbackType}")
            
            // Android 15 (API 35)에서 키 이벤트 필터링 강화
            if (Build.VERSION.SDK_INT >= 35) {
                Log.i(TAG, "Android 15+ 감지 - 키 이벤트 필터링 강화 모드")
                // 동적으로 키 이벤트 필터링 재요청
                try {
                    val currentInfo = serviceInfo
                    currentInfo?.let {
                        it.flags = it.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                                   AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                                   AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                        setServiceInfo(it)
                        Log.i(TAG, "Android 15 키 이벤트 필터링 재설정 완료")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "키 이벤트 필터링 재설정 실패: ${e.message}")
                }
                
                // Android 15에서 서비스 유지를 위한 주기적 체크
                startPeriodicServiceCheck()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "서비스 정보 확인 실패", e)
        }
        
        Log.i(TAG, "PTTAccessibilityService 초기화 완료 - 키 이벤트 대기 중...")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 현재 단계에서는 사용하지 않음 (키 이벤트만 처리)
        // Log.v(TAG, "AccessibilityEvent 수신: ${event?.eventType}")
    }

    override fun onInterrupt() {
        Log.i(TAG, "PTTAccessibilityService 중단됨")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "PTTAccessibilityService 종료됨")
        
        // Wake Lock 해제
        releaseWakeLocks()
        
        isServiceRunning = false
    }

    /**
     * 핵심: 키 이벤트 감지 - Android 15 호환성 개선
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.i(TAG, "========== 키 이벤트 수신 ==========")
        Log.i(TAG, "KeyCode: ${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
        Log.i(TAG, "Action: ${event.action} (${if(event.action == KeyEvent.ACTION_DOWN) "DOWN" else if(event.action == KeyEvent.ACTION_UP) "UP" else "OTHER"})")
        Log.i(TAG, "Repeat Count: ${event.repeatCount}")
        Log.i(TAG, "Device ID: ${event.deviceId}")
        Log.i(TAG, "Source: ${event.source}")
        Log.i(TAG, "Service Running: $isServiceRunning")
        Log.i(TAG, "Screen Interactive: ${powerManager.isInteractive}")
        
        // 볼륨 다운키만 차단
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            Log.i(TAG, "🎯 볼륨 다운키 감지!")
            
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!isVolumeDownPressed && event.repeatCount == 0) {
                        isVolumeDownPressed = true
                        Log.i(TAG, "🎯 볼륨 다운 키 눌림 - PTT 시작")
                        
                        // 화면이 꺼진 상태라면 화면 켜기
                        wakeUpScreen()
                        
                        // MediaSessionPTTService가 실행 중이라면 PTT 시작 요청
                        try {
                            Log.i(TAG, "📡 브로드캐스트 전송 시도...")
                            val intent = Intent("com.designated.callmanager.PTT_ACTION").apply {
                                putExtra("action", "start")
                                putExtra("source", "accessibility_service")
                                putExtra("timestamp", System.currentTimeMillis())
                                putExtra("screen_off", !powerManager.isInteractive)
                                // Android 15에서 명시적 패키지 지정
                                setPackage(packageName)
                            }
                            
                            // 여러 방법으로 브로드캐스트 전송 시도
                            sendBroadcast(intent)
                            Log.i(TAG, "✅ PTT 시작 브로드캐스트 전송 성공!")
                            Log.i(TAG, "  - Package: $packageName")
                            Log.i(TAG, "  - Screen: ${if(powerManager.isInteractive) "ON" else "OFF"}")
                            Log.i(TAG, "  - Time: ${System.currentTimeMillis()}")
                            
                            // MediaSessionPTTService 상태 확인
                            if (!MediaSessionPTTService.isRunning()) {
                                Log.w(TAG, "⚠️ MediaSessionPTTService가 실행 중이 아님!")
                            } else {
                                Log.i(TAG, "✅ MediaSessionPTTService 실행 중")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ PTT 시작 브로드캐스트 실패", e)
                            e.printStackTrace()
                        }
                    } else {
                        Log.d(TAG, "중복 키 이벤트 무시 (pressed: $isVolumeDownPressed, repeat: ${event.repeatCount})")
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (isVolumeDownPressed) {
                        isVolumeDownPressed = false
                        Log.i(TAG, "🎯 볼륨 다운 키 뗌 - PTT 중지")
                        
                        // MediaSessionPTTService가 실행 중이라면 PTT 중지 요청
                        try {
                            Log.i(TAG, "📡 브로드캐스트 전송 시도 (STOP)...")
                            val intent = Intent("com.designated.callmanager.PTT_ACTION").apply {
                                putExtra("action", "stop")
                                putExtra("source", "accessibility_service")
                                putExtra("timestamp", System.currentTimeMillis())
                                putExtra("screen_off", !powerManager.isInteractive)
                                // Android 15에서 명시적 패키지 지정
                                setPackage(packageName)
                            }
                            sendBroadcast(intent)
                            Log.i(TAG, "✅ PTT 중지 브로드캐스트 전송 성공!")
                            Log.i(TAG, "  - Package: $packageName")
                            Log.i(TAG, "  - Screen: ${if(powerManager.isInteractive) "ON" else "OFF"}")
                            Log.i(TAG, "  - Time: ${System.currentTimeMillis()}")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ PTT 중지 브로드캐스트 실패", e)
                            e.printStackTrace()
                        }
                    } else {
                        Log.d(TAG, "이미 해제된 상태에서 UP 이벤트")
                    }
                }
            }
            
            Log.i(TAG, "🔒 볼륨 다운키 시스템 처리 차단")
            // 볼륨 다운키는 항상 차단 (시스템 볼륨 변경 방지)
            return true
        }
        
        // 볼륨 업키도 로깅 (참고용)
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            Log.d(TAG, "볼륨 업키 감지 - 통과시킴")
        }
        
        // 다른 키는 통과
        return false
    }
    
    /**
     * Wake Lock 획득 - 화면이 꺼져도 키 이벤트 처리 가능
     */
    private fun acquireWakeLocks() {
        try {
            // CPU Wake Lock - 화면이 꺼져도 키 이벤트 처리 유지
            if (wakeLock?.isHeld != true) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CallManager:PTTAccessibilityWakeLock"
                )
                wakeLock?.acquire()
                Log.i(TAG, "✅ CPU Wake Lock 획득됨 - 화면 꺼짐 상태에서도 키 이벤트 처리 가능")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Wake Lock 획득 실패", e)
        }
    }
    
    /**
     * 화면 깨우기 - PTT 버튼 누를 때 화면 켜기
     */
    private fun wakeUpScreen() {
        try {
            if (!powerManager.isInteractive) {
                Log.i(TAG, "📱 화면이 꺼진 상태 감지 - 화면 깨우기 시도")
                
                // Screen Wake Lock 획득 (짧은 시간)
                screenWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "CallManager:PTTScreenWake"
                )
                screenWakeLock?.acquire(3000) // 3초간 화면 켜기
                Log.i(TAG, "✅ 화면 깨우기 완료")
                
                // 화면 깨우기 후 곧바로 해제 (불필요한 배터리 소모 방지)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (screenWakeLock?.isHeld == true) {
                            screenWakeLock?.release()
                            screenWakeLock = null
                            Log.i(TAG, "Screen Wake Lock 해제됨")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Screen Wake Lock 해제 중 오류", e)
                    }
                }, 1000)
                
            } else {
                Log.d(TAG, "화면이 이미 켜져 있음 - 깨우기 불필요")
            }
        } catch (e: Exception) {
            Log.e(TAG, "화면 깨우기 실패", e)
        }
    }
    
    /**
     * Wake Lock 해제
     */
    private fun releaseWakeLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "CPU Wake Lock 해제됨")
                }
            }
            wakeLock = null
            
            screenWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "Screen Wake Lock 해제됨")
                }
            }
            screenWakeLock = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Wake Lock 해제 실패", e)
        }
    }
    
    /**
     * Android 15를 위한 Foreground Service 시작
     */
    private fun startForegroundServiceForAndroid15() {
        try {
            Log.i(TAG, "Android 15 - Foreground Service로 접근성 서비스 실행")
            
            // 알림 채널 생성
            val channelId = "ptt_accessibility_channel"
            val channelName = "PTT 접근성 서비스"
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "PTT 볼륨키 감지를 위한 접근성 서비스"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
            
            // 알림 생성
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("PTT 접근성 서비스 실행 중")
                .setContentText("볼륨키 PTT 기능이 활성화되어 있습니다")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            // Foreground Service 시작
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    9999,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(9999, notification)
            }
            
            Log.i(TAG, "✅ Android 15 Foreground Service 시작 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "Android 15 Foreground Service 시작 실패", e)
        }
    }
    
    /**
     * Android 15에서 서비스 상태 주기적 체크
     */
    private fun startPeriodicServiceCheck() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val checkRunnable = object : Runnable {
            override fun run() {
                if (isServiceRunning) {
                    Log.d(TAG, "Android 15 - 접근성 서비스 상태 체크: 정상")
                    
                    // 서비스 정보 재설정 (유지를 위해)
                    try {
                        val currentInfo = serviceInfo
                        currentInfo?.let {
                            setServiceInfo(it)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "서비스 정보 재설정 실패: ${e.message}")
                    }
                    
                    // 15분 후 다시 체크
                    handler.postDelayed(this, 15 * 60 * 1000L)
                }
            }
        }
        
        // 첫 체크는 5분 후 시작
        handler.postDelayed(checkRunnable, 5 * 60 * 1000L)
        Log.i(TAG, "Android 15 - 주기적 서비스 체크 시작됨")
    }
}