package com.designated.pickupapp.ptt.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.designated.pickupapp.ptt.core.BeepSoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 픽업앱 PTT용 접근성 서비스
 * 볼륨 키를 이용한 PTT 기능 제공 + 비프음
 */
class PTTAccessibilityService : AccessibilityService() {
    
    private val TAG = "PTTAccessibilityService"
    
    // 비프음 매니저
    private var beepSoundManager: BeepSoundManager? = null
    
    // 코루틴 스코프
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PTT Accessibility Service connected")
        
        // 비프음 매니저 초기화
        try {
            beepSoundManager = BeepSoundManager(this)
            Log.d(TAG, "BeepSoundManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BeepSoundManager", e)
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 접근성 이벤트 처리 (필요시)
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "PTT Accessibility Service interrupted")
    }
    
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        
        val keyCode = event.keyCode
        val action = event.action
        
        // 볼륨 키 PTT 처리
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (action) {
                KeyEvent.ACTION_DOWN -> {
                    Log.d(TAG, "Volume key pressed - starting PTT with beep")
                    
                    // 시작 비프음 재생 (즉시)
                    try {
                        beepSoundManager?.playStartBeep()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to play start beep", e)
                    }
                    
                    // PTT 시작
                    sendPTTCommand(PTTForegroundService.ACTION_START_PTT)
                    return true // 이벤트 소비
                }
                KeyEvent.ACTION_UP -> {
                    Log.d(TAG, "Volume key released - stopping PTT with beep")
                    
                    // 종료 비프음 재생 (즉시)
                    try {
                        beepSoundManager?.playEndBeep()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to play end beep", e)
                    }
                    
                    // PTT 종료
                    sendPTTCommand(PTTForegroundService.ACTION_STOP_PTT)
                    return true // 이벤트 소비
                }
            }
        }
        
        return super.onKeyEvent(event)
    }
    
    /**
     * PTT 서비스에 명령 전송
     */
    private fun sendPTTCommand(action: String) {
        try {
            val intent = Intent(this, PTTForegroundService::class.java).apply {
                this.action = action
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send PTT command: $action", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "PTT Accessibility Service destroyed")
        
        // 비프음 매니저 정리
        try {
            beepSoundManager?.release()
            beepSoundManager = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release BeepSoundManager", e)
        }
    }
}