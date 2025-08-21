package com.designated.callmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * 백그라운드 볼륨 키 이벤트 핸들러
 * - 화면 꺼짐 상태에서도 볼륨 다운 버튼 감지
 * - PTT 버튼으로 활용
 */
class VolumeKeyHandler(
    private val context: Context,
    private val onVolumeDownPressed: () -> Unit,
    private val onVolumeDownReleased: () -> Unit
) {
    
    companion object {
        private const val TAG = "VolumeKeyHandler"
        private const val VOLUME_KEY_ACTION = "com.designated.callmanager.VOLUME_KEY_EVENT"
    }
    
    private var isRegistered = false
    private var lastVolumeDownTime = 0L
    private val debounceInterval = 100L // 100ms 디바운스
    
    private val volumeKeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_KEY_ACTION) {
                val keyCode = intent.getIntExtra("keyCode", -1)
                val action = intent.getIntExtra("action", -1)
                
                handleVolumeKey(keyCode, action)
            }
        }
    }
    
    /**
     * 볼륨 키 핸들러 등록
     */
    fun register() {
        if (!isRegistered) {
            try {
                val filter = IntentFilter(VOLUME_KEY_ACTION)
                context.registerReceiver(volumeKeyReceiver, filter)
                isRegistered = true
                Log.i(TAG, "Volume key handler registered for background PTT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register volume key handler", e)
            }
        }
    }
    
    /**
     * 볼륨 키 핸들러 해제
     */
    fun unregister() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(volumeKeyReceiver)
                isRegistered = false
                Log.i(TAG, "Volume key handler unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister volume key handler", e)
            }
        }
    }
    
    /**
     * 볼륨 키 이벤트 처리
     */
    private fun handleVolumeKey(keyCode: Int, action: Int) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val currentTime = System.currentTimeMillis()
            
            when (action) {
                KeyEvent.ACTION_DOWN -> {
                    // 디바운싱 처리
                    if (currentTime - lastVolumeDownTime > debounceInterval) {
                        lastVolumeDownTime = currentTime
                        
                        Log.d(TAG, "Volume down pressed (background)")
                        Handler(Looper.getMainLooper()).post {
                            onVolumeDownPressed()
                        }
                    }
                }
                
                KeyEvent.ACTION_UP -> {
                    Log.d(TAG, "Volume down released (background)")
                    Handler(Looper.getMainLooper()).post {
                        onVolumeDownReleased()
                    }
                }
            }
        }
    }
    
    /**
     * 시스템 볼륨 키 이벤트를 브로드캐스트로 전송하는 헬퍼
     * - 이 메소드는 액티비티에서 호출되어야 함 (onKeyDown/onKeyUp)
     */
    fun broadcastVolumeKeyEvent(context: Context, keyCode: Int, action: Int) {
        try {
            val intent = Intent(VOLUME_KEY_ACTION).apply {
                putExtra("keyCode", keyCode)
                putExtra("action", action)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "Volume key event broadcasted: keyCode=$keyCode, action=$action")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast volume key event", e)
        }
    }
}