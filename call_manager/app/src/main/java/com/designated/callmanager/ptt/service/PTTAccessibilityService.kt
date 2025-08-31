package com.designated.callmanager.ptt.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
// PTTDebouncer는 이 파일 하단에 정의됨

/**
 * PTT Accessibility Service
 * 볼륨키를 통한 PTT 제어를 위한 접근성 서비스
 */
class PTTAccessibilityService : AccessibilityService() {
    private val TAG = "PTTAccessibilityService"
    
    private val debouncer = PTTDebouncer()
    private lateinit var sharedPrefs: SharedPreferences
    
    // PTT 활성화 상태
    private var isPTTEnabled = true
    private var isTransmitting = false
    
    companion object {
        private const val PREF_NAME = "ptt_accessibility_prefs"
        private const val KEY_PTT_ENABLED = "ptt_enabled"
        
        /**
         * PTT 기능 활성화/비활성화
         */
        fun setPTTEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_PTT_ENABLED, enabled).apply()
        }
        
        /**
         * PTT 기능 활성화 상태 확인
         */
        fun isPTTEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PTT_ENABLED, true)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PTT Accessibility Service created")
        
        sharedPrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isPTTEnabled = sharedPrefs.getBoolean(KEY_PTT_ENABLED, true)
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PTT Accessibility Service connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // AccessibilityEvent는 사용하지 않음 (볼륨키 이벤트만 처리)
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }
    
    override fun onKeyEvent(event: KeyEvent): Boolean {
        // PTT가 비활성화된 경우 이벤트 패스
        if (!isPTTEnabled()) {
            return super.onKeyEvent(event)
        }
        
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                handleVolumeKeyEvent(event)
            }
            else -> super.onKeyEvent(event)
        }
    }
    
    /**
     * 볼륨키 이벤트 처리
     */
    private fun handleVolumeKeyEvent(event: KeyEvent): Boolean {
        // 디바운싱 처리
        if (!debouncer.shouldProcess()) {
            Log.d(TAG, "Volume key event debounced")
            return true
        }
        
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!isTransmitting) {
                    Log.d(TAG, "Volume key pressed - starting PTT")
                    startPTT()
                    isTransmitting = true
                }
            }
            
            KeyEvent.ACTION_UP -> {
                if (isTransmitting) {
                    Log.d(TAG, "Volume key released - stopping PTT")
                    stopPTT()
                    isTransmitting = false
                }
            }
        }
        
        // 이벤트를 소비하여 시스템 볼륨 조절 방지
        return true
    }
    
    /**
     * PTT 시작
     */
    private fun startPTT() {
        try {
            Log.i(TAG, "Starting PTT via AccessibilityService")
            
            // PTTManagerService 사용
            val pttManager = com.designated.callmanager.service.PTTManagerService.getInstance()
            if (pttManager != null) {
                pttManager.startTransmission()
                Log.i(TAG, "PTT transmission started via PTTManagerService")
            } else {
                // PTTManagerService가 없으면 시작
                val intent = Intent(this, com.designated.callmanager.service.PTTManagerService::class.java)
                startService(intent)
                Log.w(TAG, "PTTManagerService not found, starting service")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PTT", e)
        }
    }
    
    /**
     * PTT 중지
     */
    private fun stopPTT() {
        try {
            Log.i(TAG, "Stopping PTT via AccessibilityService")
            
            // PTTManagerService 사용
            val pttManager = com.designated.callmanager.service.PTTManagerService.getInstance()
            if (pttManager != null) {
                pttManager.stopTransmission()
                Log.i(TAG, "PTT transmission stopped via PTTManagerService")
            } else {
                Log.w(TAG, "PTTManagerService not available for stopping")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop PTT", e)
        }
    }
    
    /**
     * 현재 PTT 활성화 상태 확인
     */
    private fun isPTTEnabled(): Boolean {
        // SharedPreferences에서 실시간으로 확인
        isPTTEnabled = sharedPrefs.getBoolean(KEY_PTT_ENABLED, true)
        return isPTTEnabled
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "PTT Accessibility Service destroyed")
        
        // 서비스 종료 시 전송 중이면 중지
        if (isTransmitting) {
            stopPTT()
            isTransmitting = false
        }
    }
}

/**
 * PTT 디바운서
 * 빠른 연속 키 입력을 방지
 */
class PTTDebouncer {
    private val TAG = "PTTDebouncer"
    private var lastEventTime = 0L
    private val DEBOUNCE_DELAY = 100L // 100ms
    
    /**
     * 이벤트 처리 여부 결정
     */
    fun shouldProcess(): Boolean {
        val currentTime = System.currentTimeMillis()
        val shouldProcess = currentTime - lastEventTime > DEBOUNCE_DELAY
        
        if (shouldProcess) {
            lastEventTime = currentTime
            Log.d(TAG, "Event allowed")
        } else {
            Log.d(TAG, "Event debounced (${currentTime - lastEventTime}ms)")
        }
        
        return shouldProcess
    }
    
    /**
     * 디바운스 딜레이 재설정
     */
    fun reset() {
        lastEventTime = 0L
        Log.d(TAG, "Debouncer reset")
    }
}