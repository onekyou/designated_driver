package com.designated.callmanager.ptt.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.designated.callmanager.R
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
    private var isProcessing = false // 처리 중 플래그 추가
    
    // SoundPool for immediate beep
    private var soundPool: SoundPool? = null
    private var beepSoundId: Int = 0
    private var isSoundLoaded = false
    private var lastStreamId: Int = 0
    
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
        
        // SoundPool 초기화는 onServiceConnected에서 진행
    }
    
    private fun initializeSoundPool() {
        try {
            // AccessibilityService 전용 AudioAttributes
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)  // 접근성 서비스 전용
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            soundPool = SoundPool.Builder()
                .setMaxStreams(2)  // 약간 여유있게
                .setAudioAttributes(audioAttributes)
                .build()
            
            // 로딩 완료 리스너
            soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    isSoundLoaded = true
                    Log.d(TAG, "SoundPool loaded ptt_beep.m4a successfully (ASSISTANCE_SONIFICATION) - id: $sampleId")
                } else {
                    Log.e(TAG, "SoundPool loading failed: status=$status")
                }
            }
            
            // 사운드 로드
            beepSoundId = soundPool?.load(this, R.raw.ptt_beep, 1) ?: 0
            
            if (beepSoundId != 0) {
                Log.d(TAG, "SoundPool for ptt_beep.m4a initialized with ASSISTANCE_SONIFICATION (soundId: $beepSoundId)")
            } else {
                Log.e(TAG, "SoundPool.load() returned 0 - R.raw.ptt_beep not found?")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPool", e)
            soundPool = null
            beepSoundId = 0
            isSoundLoaded = false
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PTT Accessibility Service connected")
        
        // 서비스가 완전히 연결된 후 SoundPool 초기화
        initializeSoundPool()
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
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // repeatCount가 0일 때만 처리 (첫 번째 DOWN 이벤트)
                if (event.repeatCount == 0) {
                    // 이미 전송 중이면 무시
                    if (isTransmitting) {
                        Log.d(TAG, "Volume key DOWN ignored - already transmitting")
                        return true
                    }
                    
                    // 디바운싱 처리
                    if (!debouncer.shouldProcess()) {
                        Log.d(TAG, "Volume key DOWN event debounced")
                        return true
                    }
                    
                    Log.i(TAG, "Volume key pressed - starting PTT (repeatCount: 0)")
                    
                    // 즉시 비프음 재생 (무조건 재생)
                    playImmediateBeep()
                    
                    isProcessing = true
                    startPTT()
                    isTransmitting = true
                    isProcessing = false
                } else {
                    // repeat 이벤트는 무시 (키를 계속 누르고 있는 상태)
                    Log.v(TAG, "Volume key repeat ignored (repeatCount: ${event.repeatCount})")
                }
            }
            
            KeyEvent.ACTION_UP -> {
                // 전송 중이 아니면 무시
                if (!isTransmitting) {
                    Log.d(TAG, "Volume key UP ignored - not transmitting")
                    return true
                }
                
                // 처리 중이면 무시 (중복 UP 이벤트 방지)
                if (isProcessing) {
                    Log.d(TAG, "Volume key UP ignored - still processing")
                    return true
                }
                
                Log.i(TAG, "Volume key released - stopping PTT")
                isProcessing = true
                stopPTT()
                isTransmitting = false
                isProcessing = false
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
            
            // PTTForegroundService 직접 호출
            val intent = Intent(this, com.designated.callmanager.ptt.service.PTTForegroundService::class.java).apply {
                action = com.designated.callmanager.ptt.service.PTTForegroundService.ACTION_START_PTT
            }
            startService(intent)
            Log.i(TAG, "PTT start command sent to PTTForegroundService")
            
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
            
            // PTTForegroundService 직접 호출
            val intent = Intent(this, com.designated.callmanager.ptt.service.PTTForegroundService::class.java).apply {
                action = com.designated.callmanager.ptt.service.PTTForegroundService.ACTION_STOP_PTT
            }
            startService(intent)
            Log.i(TAG, "PTT stop command sent to PTTForegroundService")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop PTT", e)
        }
    }
    
    /**
     * 즉시 비프음 재생 (볼륨키 누름 즉시) - SoundPool 사용
     */
    private fun playImmediateBeep() {
        try {
            if (soundPool != null && beepSoundId != 0 && isSoundLoaded) {
                // 이전 스트림이 있으면 명시적으로 정리
                if (lastStreamId != 0) {
                    soundPool!!.stop(lastStreamId)
                    Log.v(TAG, "Stopped previous stream: $lastStreamId")
                }
                
                // 새 스트림 재생
                val streamId = soundPool!!.play(beepSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
                lastStreamId = streamId
                
                if (streamId != 0) {
                    Log.d(TAG, "Immediate beep played (ptt_beep.m4a) with ASSISTANCE_SONIFICATION - streamId: $streamId")
                } else {
                    Log.w(TAG, "SoundPool.play() returned 0 - no available streams or playback failed")
                    playSystemFallback()
                }
            } else {
                // SoundPool이 준비되지 않았으면 시스템 사운드로 폴백
                Log.w(TAG, "SoundPool not ready - pool: ${soundPool != null}, soundId: $beepSoundId, loaded: $isSoundLoaded")
                playSystemFallback()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play immediate beep", e)
            playSystemFallback()
        }
    }
    
    private fun playSystemFallback() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 1.0f)
            Log.d(TAG, "Immediate beep played (system fallback)")
        } catch (e: Exception) {
            Log.e(TAG, "Even system beep failed", e)
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
        
        // SoundPool 해제
        try {
            soundPool?.release()
            soundPool = null
            beepSoundId = 0
            isSoundLoaded = false
            lastStreamId = 0
            Log.d(TAG, "SoundPool released and cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release SoundPool", e)
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
    private val DEBOUNCE_DELAY = 200L // 200ms로 증가
    
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