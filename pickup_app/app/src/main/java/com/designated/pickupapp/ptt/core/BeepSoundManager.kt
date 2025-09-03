package com.designated.pickupapp.ptt.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import com.designated.pickupapp.R

/**
 * PTT 비프음 관리 클래스 (픽업앱용)
 */
class BeepSoundManager(private val context: Context) {
    
    private val TAG = "BeepSoundManager"
    
    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0  // 즉각적인 클릭음
    private var readySoundId: Int = 0  // 준비 완료 비프음
    private var endSoundId: Int = 0    // 종료 비프음
    private var errorSoundId: Int = 0  // 에러음
    private var isInitialized = false
    private val loadedSounds = mutableSetOf<Int>()
    
    init {
        initialize()
    }
    
    /**
     * 사운드 풀 초기화
     */
    private fun initialize() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            soundPool = SoundPool.Builder()
                .setMaxStreams(4)  // 여러 사운드 동시 재생 가능
                .setAudioAttributes(audioAttributes)
                .build()
            
            // 로딩 완료 리스너 설정
            soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    loadedSounds.add(sampleId)
                    Log.d(TAG, "Sound loaded successfully: $sampleId (ptt_beep.m4a)")
                    
                    // 모든 사운드가 로드되었는지 확인
                    if (loadedSounds.containsAll(listOf(clickSoundId, endSoundId))) {
                        Log.i(TAG, "All PTT sounds loaded successfully")
                    }
                } else {
                    Log.e(TAG, "Sound loading failed: $sampleId, status: $status")
                }
            }
            
            // ptt_beep.m4a 파일 로드
            // 같은 ID를 재사용하여 메모리 절약
            val soundId = soundPool?.load(context, R.raw.ptt_beep, 1) ?: 0
            clickSoundId = soundId
            readySoundId = soundId  
            endSoundId = soundId
            errorSoundId = soundId
            
            isInitialized = true
            Log.d(TAG, "BeepSoundManager initialized, loading ptt_beep.m4a (id: $soundId)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BeepSoundManager", e)
        }
    }
    
    /**
     * PTT 시작 비프음 재생 (버튼 클릭 시 무조건 재생)
     */
    fun playStartBeep() {
        try {
            // SoundPool이 준비되어 있고 사운드가 로드되었으면 사용
            if (isInitialized && soundPool != null && clickSoundId != 0) {
                val streamId = soundPool?.play(clickSoundId, 1.0f, 1.0f, 1, 0, 1.0f) ?: 0
                if (streamId != 0) {
                    Log.d(TAG, "Start beep played (ptt_beep.m4a) - streamId: $streamId")
                } else {
                    // play가 실패하면 시스템 사운드 사용
                    playSystemBeep()
                    Log.d(TAG, "Start beep played (system fallback - play failed)")
                }
            } else {
                // SoundPool이 준비되지 않았으면 시스템 사운드 사용
                playSystemBeep()
                Log.d(TAG, "Start beep played (system fallback) - init: $isInitialized, pool: ${soundPool != null}, id: $clickSoundId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play start beep", e)
            playSystemBeep()
        }
    }
    
    /**
     * 에러음 재생 (연결 실패 시 고음의 일정한 에러음)
     */
    fun playErrorSound() {
        try {
            // 시스템 에러음 사용 (고음의 일정한 사운드)
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // FX_KEYPRESS_DELETE는 높은 톤의 에러음
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE, 1.0f)
            Thread.sleep(150)
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE, 1.0f)
            Log.d(TAG, "Error sound played (high-pitched beep)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play error sound", e)
            playSystemError()
        }
    }
    
    /**
     * PTT 종료 비프음 재생
     */
    fun playEndBeep() {
        try {
            // SoundPool이 준비되어 있고 사운드가 로드되었으면 사용
            if (isInitialized && soundPool != null && endSoundId != 0) {
                val streamId = soundPool?.play(endSoundId, 0.8f, 0.8f, 1, 0, 0.9f) ?: 0  // 약간 작고 낮은 톤
                if (streamId != 0) {
                    Log.d(TAG, "End beep played (ptt_beep.m4a) - streamId: $streamId")
                } else {
                    // play가 실패하면 시스템 사운드 사용
                    playSystemBeep()
                    Log.d(TAG, "End beep played (system fallback - play failed)")
                }
            } else {
                // SoundPool이 준비되지 않았으면 시스템 사운드 사용
                playSystemBeep()
                Log.d(TAG, "End beep played (system fallback) - init: $isInitialized, pool: ${soundPool != null}, id: $endSoundId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play end beep", e)
            playSystemBeep()
        }
    }
    
    /**
     * 시스템 비프음 재생 (대체용)
     */
    private fun playSystemBeep() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play system beep: $e")
        }
    }
    
    /**
     * 시스템 클릭음 재생 (즉각적인 피드백)
     */
    private fun playSystemClick() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.5f)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play system click: $e")
        }
    }
    
    /**
     * 시스템 에러음 재생
     */
    private fun playSystemError() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_INVALID, 0.8f)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play system error: $e")
        }
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            isInitialized = false
            loadedSounds.clear()  // 로드된 사운드 ID 클리어
            clickSoundId = 0
            readySoundId = 0
            endSoundId = 0
            errorSoundId = 0
            Log.d(TAG, "BeepSoundManager released and cleared")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release BeepSoundManager", e)
        }
    }
    
    /**
     * 재초기화 (필요시 사용)
     */
    fun reinitialize() {
        release()
        initialize()
    }
}