package com.designated.callmanager.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * 백그라운드 오디오 관리 클래스
 * - 화면 꺼짐 상태에서도 오디오 포커스 유지
 * - PTT 통신을 위한 오디오 세션 관리
 */
class BackgroundAudioManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BackgroundAudioManager"
    }
    
    private val audioManager: AudioManager = 
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    
    /**
     * 백그라운드 오디오 설정
     */
    fun setupBackgroundAudio() {
        Log.i(TAG, "Setting up background audio for PTT")
        
        requestAudioFocus()
        configureAudioSettings()
    }
    
    /**
     * 오디오 포커스 요청
     */
    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                
                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                
                Log.i(TAG, "Audio focus request result (API 26+): $result, hasAudioFocus: $hasAudioFocus")
                
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                
                Log.i(TAG, "Audio focus request result (Legacy): $result, hasAudioFocus: $hasAudioFocus")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus", e)
        }
    }
    
    /**
     * 오디오 설정 구성
     */
    private fun configureAudioSettings() {
        try {
            // 스피커폰 모드로 설정
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            
            // 마이크 음소거 해제
            audioManager.isMicrophoneMute = false
            
            // 볼륨 설정 (적절한 레벨로)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val targetVolume = (maxVolume * 0.8).toInt() // 80% 볼륨
            audioManager.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL, 
                targetVolume, 
                0
            )
            
            Log.i(TAG, "Audio settings configured - Mode: ${audioManager.mode}, Speaker: ${audioManager.isSpeakerphoneOn}, Volume: $targetVolume/$maxVolume")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure audio settings", e)
        }
    }
    
    /**
     * 오디오 포커스 변화 리스너
     */
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus gained")
                hasAudioFocus = true
                configureAudioSettings()
            }
            
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus lost permanently")
                hasAudioFocus = false
            }
            
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus lost temporarily")
                hasAudioFocus = false
            }
            
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(TAG, "Audio focus lost - can duck")
                // PTT에서는 덕킹하지 않고 유지
            }
        }
    }
    
    /**
     * 오디오 포커스 상태 확인
     */
    fun hasAudioFocus(): Boolean = hasAudioFocus
    
    /**
     * 정리
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up background audio manager")
        
        try {
            // 오디오 포커스 해제
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { request ->
                    audioManager.abandonAudioFocusRequest(request)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
            
            // 오디오 설정 복원
            audioManager.mode = AudioManager.MODE_NORMAL
            
            hasAudioFocus = false
            audioFocusRequest = null
            
            Log.i(TAG, "Background audio manager cleaned up")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup background audio manager", e)
        }
    }
}