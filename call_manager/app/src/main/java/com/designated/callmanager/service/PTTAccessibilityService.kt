package com.designated.callmanager.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.os.Build

/**
 * Android 15 호환 PTT Accessibility Service
 * - Android 15에서 강화된 접근성 서비스 키 이벤트 처리
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

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "============================================")
        Log.i(TAG, "PTTAccessibilityService 생성됨")
        Log.i(TAG, "Android 버전: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        Log.i(TAG, "============================================")
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
                        it.flags = it.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                        setServiceInfo(it)
                        Log.i(TAG, "키 이벤트 필터링 재설정 완료")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "키 이벤트 필터링 재설정 실패: ${e.message}")
                }
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
        isServiceRunning = false
    }

    /**
     * 핵심: 키 이벤트 감지 - Android 15 호환성 개선
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.v(TAG, "========== 키 이벤트 수신 ==========")
        Log.v(TAG, "KeyCode: ${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
        Log.v(TAG, "Action: ${event.action} (${if(event.action == KeyEvent.ACTION_DOWN) "DOWN" else if(event.action == KeyEvent.ACTION_UP) "UP" else "OTHER"})")
        Log.v(TAG, "Repeat Count: ${event.repeatCount}")
        Log.v(TAG, "Device ID: ${event.deviceId}")
        Log.v(TAG, "Source: ${event.source}")
        
        // 볼륨 다운키만 차단
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            Log.i(TAG, "🎯 볼륨 다운키 감지!")
            
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!isVolumeDownPressed && event.repeatCount == 0) {
                        isVolumeDownPressed = true
                        Log.i(TAG, "🎯 볼륨 다운 키 눌림 - PTT 시작")
                        
                        // MediaSessionPTTService가 실행 중이라면 PTT 시작 요청
                        try {
                            val intent = Intent("com.designated.callmanager.PTT_ACTION").apply {
                                putExtra("action", "start")
                                putExtra("source", "accessibility_service")
                                putExtra("timestamp", System.currentTimeMillis())
                                // Android 15에서 명시적 패키지 지정
                                setPackage(packageName)
                            }
                            sendBroadcast(intent)
                            Log.i(TAG, "PTT 시작 브로드캐스트 전송됨")
                        } catch (e: Exception) {
                            Log.e(TAG, "PTT 시작 브로드캐스트 실패", e)
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
                            val intent = Intent("com.designated.callmanager.PTT_ACTION").apply {
                                putExtra("action", "stop")
                                putExtra("source", "accessibility_service")
                                putExtra("timestamp", System.currentTimeMillis())
                                // Android 15에서 명시적 패키지 지정
                                setPackage(packageName)
                            }
                            sendBroadcast(intent)
                            Log.i(TAG, "PTT 중지 브로드캐스트 전송됨")
                        } catch (e: Exception) {
                            Log.e(TAG, "PTT 중지 브로드캐스트 실패", e)
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
}