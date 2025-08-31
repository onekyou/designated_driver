package com.designated.pickupapp.ptt.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * WakeLock 대신 오디오 세션을 활용한 백그라운드 유지 관리자
 * Agora SDK의 오디오 세션과 결합하여 디바이스를 깨어있게 유지
 */
class AudioSessionManager(private val context: Context) {
    private val TAG = "AudioSessionManager"
    
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    
    // 오디오 포커스 변경 리스너
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Audio focus changed: $focusChange")
        
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus gained")
                hasFocus = true
                // 볼륨 복원 등의 작업 수행
                restoreAudioSettings()
            }
            
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus lost permanently")
                hasFocus = false
                // PTT 중지 등의 작업 수행
                onFocusLost()
            }
            
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus lost temporarily")
                hasFocus = false
                // 일시적 중지
                onFocusLostTransient()
            }
            
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost - can duck")
                // 볼륨 낮추기
                onFocusLostCanDuck()
            }
            
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                Log.e(TAG, "Audio focus request failed")
                hasFocus = false
            }
        }
    }
    
    /**
     * PTT용 오디오 포커스 획득
     * 음성 통신용 배타적 포커스를 요청하여 디바이스를 활성 상태로 유지
     */
    fun acquireAudioFocus(): Boolean {
        return try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0 이상
                audioFocusRequest = AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                ).apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setAcceptsDelayedFocusGain(false)
                    setWillPauseWhenDucked(false)
                    setOnAudioFocusChangeListener(audioFocusChangeListener)
                }.build()
                
                val result = audioManager?.requestAudioFocus(audioFocusRequest!!)
                hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                
                if (hasFocus) {
                    Log.i(TAG, "Audio focus acquired successfully (API 26+)")
                    configureAudioForPTT()
                } else {
                    Log.e(TAG, "Failed to acquire audio focus (API 26+)")
                }
                
            } else {
                // Android 7.1 이하
                @Suppress("DEPRECATION")
                val result = audioManager?.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                
                hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                
                if (hasFocus) {
                    Log.i(TAG, "Audio focus acquired successfully (Legacy API)")
                    configureAudioForPTT()
                } else {
                    Log.e(TAG, "Failed to acquire audio focus (Legacy API)")
                }
            }
            
            hasFocus
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception while acquiring audio focus", e)
            false
        }
    }
    
    /**
     * 오디오 포커스 해제
     */
    fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { request ->
                    audioManager?.abandonAudioFocusRequest(request)
                    audioFocusRequest = null
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(audioFocusChangeListener)
            }
            
            hasFocus = false
            audioManager = null
            
            Log.i(TAG, "Audio focus released")
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception while releasing audio focus", e)
        }
    }
    
    /**
     * PTT용 오디오 설정
     */
    private fun configureAudioForPTT() {
        try {
            audioManager?.let { manager ->
                // 스피커폰 활성화
                manager.isSpeakerphoneOn = true
                
                // 음성 통화 모드로 설정
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    manager.mode = AudioManager.MODE_IN_COMMUNICATION
                }
                
                // 마이크 음소거 해제 (필요한 경우)
                manager.isMicrophoneMute = false
                
                Log.d(TAG, "Audio configured for PTT - Speakerphone: ON, Mic mute: OFF")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure audio for PTT", e)
        }
    }
    
    /**
     * 오디오 설정 복원
     */
    private fun restoreAudioSettings() {
        try {
            Log.d(TAG, "Restoring audio settings")
            // 필요한 경우 오디오 설정 복원
            configureAudioForPTT()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore audio settings", e)
        }
    }
    
    /**
     * 영구적 포커스 손실 처리
     */
    private fun onFocusLost() {
        Log.w(TAG, "Permanent audio focus lost - PTT should be stopped")
        // PTT 중지 신호를 보내거나 콜백 호출
        // 실제 구현에서는 PTTController에 알림을 보내야 함
    }
    
    /**
     * 일시적 포커스 손실 처리
     */
    private fun onFocusLostTransient() {
        Log.w(TAG, "Transient audio focus lost - temporarily pause PTT")
        // 일시적으로 PTT 중단
    }
    
    /**
     * Duck 가능한 포커스 손실 처리
     */
    private fun onFocusLostCanDuck() {
        Log.d(TAG, "Audio focus lost but can duck - lower volume")
        // 볼륨을 낮추되 계속 동작
    }
    
    /**
     * 현재 오디오 포커스 상태 확인
     */
    fun hasFocus(): Boolean = hasFocus
    
    /**
     * 오디오 라우팅 상태 확인
     */
    fun getAudioRouting(): AudioRouting {
        return try {
            audioManager?.let { manager ->
                when {
                    manager.isSpeakerphoneOn -> AudioRouting.SPEAKERPHONE
                    manager.isWiredHeadsetOn -> AudioRouting.WIRED_HEADSET
                    manager.isBluetoothA2dpOn || manager.isBluetoothScoOn -> AudioRouting.BLUETOOTH
                    else -> AudioRouting.EARPIECE
                }
            } ?: AudioRouting.UNKNOWN
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get audio routing", e)
            AudioRouting.UNKNOWN
        }
    }
    
    /**
     * 스피커폰 설정
     */
    fun setSpeakerphone(enabled: Boolean) {
        try {
            audioManager?.isSpeakerphoneOn = enabled
            Log.d(TAG, "Speakerphone ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speakerphone", e)
        }
    }
    
    /**
     * 마이크 음소거 설정
     */
    fun setMicrophoneMute(muted: Boolean) {
        try {
            audioManager?.isMicrophoneMute = muted
            Log.d(TAG, "Microphone ${if (muted) "muted" else "unmuted"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set microphone mute", e)
        }
    }
    
    /**
     * 볼륨 조절
     */
    fun setVolume(streamType: Int, volume: Int, flags: Int = 0) {
        try {
            audioManager?.setStreamVolume(streamType, volume, flags)
            Log.d(TAG, "Volume set for stream $streamType to $volume")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume", e)
        }
    }
    
    /**
     * 현재 볼륨 가져오기
     */
    fun getVolume(streamType: Int): Int {
        return try {
            audioManager?.getStreamVolume(streamType) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get volume", e)
            0
        }
    }
    
    /**
     * 최대 볼륨 가져오기
     */
    fun getMaxVolume(streamType: Int): Int {
        return try {
            audioManager?.getStreamMaxVolume(streamType) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get max volume", e)
            0
        }
    }
    
    /**
     * 디버그 정보 출력
     */
    fun debugPrintAudioState() {
        try {
            audioManager?.let { manager ->
                Log.d(TAG, "=== Audio State Debug ===")
                Log.d(TAG, "Has focus: $hasFocus")
                Log.d(TAG, "Speakerphone: ${manager.isSpeakerphoneOn}")
                Log.d(TAG, "Microphone muted: ${manager.isMicrophoneMute}")
                Log.d(TAG, "Wired headset: ${manager.isWiredHeadsetOn}")
                Log.d(TAG, "Bluetooth SCO: ${manager.isBluetoothScoOn}")
                Log.d(TAG, "Audio routing: ${getAudioRouting()}")
                
                val voiceCallVolume = getVolume(AudioManager.STREAM_VOICE_CALL)
                val maxVoiceCallVolume = getMaxVolume(AudioManager.STREAM_VOICE_CALL)
                Log.d(TAG, "Voice call volume: $voiceCallVolume/$maxVoiceCallVolume")
                
                Log.d(TAG, "========================")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to print debug info", e)
        }
    }
    
    /**
     * 오디오 라우팅 타입
     */
    enum class AudioRouting {
        SPEAKERPHONE,
        EARPIECE,
        WIRED_HEADSET,
        BLUETOOTH,
        UNKNOWN
    }
}