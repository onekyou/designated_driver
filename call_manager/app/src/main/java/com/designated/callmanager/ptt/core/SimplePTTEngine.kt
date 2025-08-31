package com.designated.callmanager.ptt.core

import android.content.Context
import android.util.Log
import io.agora.rtc2.*

/**
 * 단순하고 안정적인 PTT 엔진
 * Agora RTC SDK를 래핑하여 PTT 기능 제공
 */
class SimplePTTEngine {
    private val TAG = "SimplePTTEngine"
    
    private var rtcEngine: RtcEngine? = null
    private var currentChannel: String? = null
    private var currentUID: Int = 0
    private var isInitialized = false
    private var isInChannel = false
    private var isTransmitting = false
    
    /**
     * 엔진 초기화
     * @param context Android Context
     * @param appId Agora App ID
     * @param eventHandler RTC 이벤트 핸들러
     * @return 초기화 결과
     */
    fun initialize(
        context: Context,
        appId: String,
        eventHandler: IRtcEngineEventHandler
    ): Result<Unit> {
        return try {
            if (isInitialized) {
                Log.w(TAG, "Engine already initialized")
                return Result.success(Unit)
            }
            
            Log.i(TAG, "Initializing PTT Engine with App ID: ${appId.take(8)}...")
            
            val config = RtcEngineConfig().apply {
                mContext = context.applicationContext
                mAppId = appId
                mEventHandler = eventHandler
                mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                mAudioScenario = Constants.AUDIO_SCENARIO_DEFAULT
            }
            
            rtcEngine = RtcEngine.create(config)
            configureAudio()
            
            isInitialized = true
            Log.i(TAG, "PTT Engine initialized successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PTT Engine", e)
            Result.failure(e)
        }
    }
    
    /**
     * 오디오 설정 구성
     */
    private fun configureAudio() {
        rtcEngine?.apply {
            // 오디오 프로파일 설정
            setAudioProfile(
                Constants.AUDIO_PROFILE_SPEECH_STANDARD,
                Constants.AUDIO_SCENARIO_DEFAULT
            )
            
            // 오디오 라우팅 설정 (스피커폰)
            setDefaultAudioRoutetoSpeakerphone(true)
            
            // 볼륨 인디케이터 활성화 (말하는 사람 감지용)
            enableAudioVolumeIndication(200, 3, true)
            
            // 노이즈 억제 활성화
            setParameters("{\"che.audio.ns.mode\":2}")
            
            // 에코 캔슬레이션 활성화
            setParameters("{\"che.audio.aec.enable\":true}")
            
            Log.d(TAG, "Audio configured for PTT")
        }
    }
    
    /**
     * 채널 참여
     * @param channelName 채널 이름
     * @param token Agora 토큰
     * @param uid 사용자 UID
     * @return 참여 결과
     */
    fun joinChannel(
        channelName: String,
        token: String,
        uid: Int
    ): Result<Unit> {
        return try {
            if (!isInitialized) {
                return Result.failure(IllegalStateException("Engine not initialized"))
            }
            
            if (isInChannel && currentChannel == channelName && currentUID == uid) {
                Log.w(TAG, "Already in channel: $channelName with UID: $uid")
                return Result.success(Unit)
            }
            
            // 기존 채널에서 나가기
            if (isInChannel) {
                leaveChannel()
            }
            
            Log.i(TAG, "Joining channel: $channelName with UID: $uid")
            
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = false // 초기에는 마이크 OFF
                autoSubscribeAudio = true // 다른 사용자 오디오 자동 구독
            }
            
            val result = rtcEngine?.joinChannel(token, channelName, uid, options)
            
            if (result == 0) {
                currentChannel = channelName
                currentUID = uid
                isInChannel = true
                Log.i(TAG, "Channel join initiated successfully")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to join channel, error code: $result")
                Result.failure(Exception("Join channel failed with code: $result"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception while joining channel", e)
            Result.failure(e)
        }
    }
    
    /**
     * 채널 나가기
     * @return 나가기 결과
     */
    fun leaveChannel(): Result<Unit> {
        return try {
            if (!isInChannel) {
                Log.w(TAG, "Not in any channel")
                return Result.success(Unit)
            }
            
            // 전송 중이면 중지
            if (isTransmitting) {
                stopTransmit()
            }
            
            Log.i(TAG, "Leaving channel: $currentChannel")
            
            val result = rtcEngine?.leaveChannel()
            
            if (result == 0) {
                currentChannel = null
                currentUID = 0
                isInChannel = false
                Log.i(TAG, "Left channel successfully")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to leave channel, error code: $result")
                Result.failure(Exception("Leave channel failed with code: $result"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception while leaving channel", e)
            Result.failure(e)
        }
    }
    
    /**
     * PTT 전송 시작
     * @return 전송 시작 결과
     */
    fun startTransmit(): Result<Unit> {
        return try {
            if (!isInChannel) {
                return Result.failure(IllegalStateException("Not in channel"))
            }
            
            if (isTransmitting) {
                Log.w(TAG, "Already transmitting")
                return Result.success(Unit)
            }
            
            Log.d(TAG, "Starting PTT transmission")
            
            // 마이크 음소거 해제 및 발행
            rtcEngine?.muteLocalAudioStream(false)
            rtcEngine?.updateChannelMediaOptions(
                ChannelMediaOptions().apply {
                    publishMicrophoneTrack = true
                }
            )
            
            isTransmitting = true
            Log.i(TAG, "PTT transmission started")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start transmission", e)
            Result.failure(e)
        }
    }
    
    /**
     * PTT 전송 중지
     * @return 전송 중지 결과
     */
    fun stopTransmit(): Result<Unit> {
        return try {
            if (!isTransmitting) {
                Log.w(TAG, "Not transmitting")
                return Result.success(Unit)
            }
            
            Log.d(TAG, "Stopping PTT transmission")
            
            // 마이크 음소거 및 발행 중지
            rtcEngine?.muteLocalAudioStream(true)
            rtcEngine?.updateChannelMediaOptions(
                ChannelMediaOptions().apply {
                    publishMicrophoneTrack = false
                }
            )
            
            isTransmitting = false
            Log.i(TAG, "PTT transmission stopped")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop transmission", e)
            Result.failure(e)
        }
    }
    
    /**
     * 엔진 정리
     */
    fun destroy() {
        try {
            Log.i(TAG, "Destroying PTT Engine")
            
            if (isInChannel) {
                leaveChannel()
            }
            
            rtcEngine?.let {
                RtcEngine.destroy()
                rtcEngine = null
            }
            
            isInitialized = false
            isInChannel = false
            isTransmitting = false
            currentChannel = null
            currentUID = 0
            
            Log.i(TAG, "PTT Engine destroyed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error while destroying engine", e)
        }
    }
    
    /**
     * 스피커폰 활성화/비활성화
     */
    fun setSpeakerphone(enabled: Boolean): Result<Unit> {
        return try {
            rtcEngine?.setEnableSpeakerphone(enabled)
            Log.d(TAG, "Speakerphone ${if (enabled) "enabled" else "disabled"}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speakerphone", e)
            Result.failure(e)
        }
    }
    
    /**
     * 볼륨 조절
     * @param volume 0-100
     */
    fun setVolume(volume: Int): Result<Unit> {
        return try {
            val clampedVolume = volume.coerceIn(0, 100)
            rtcEngine?.adjustPlaybackSignalVolume(clampedVolume)
            Log.d(TAG, "Volume set to $clampedVolume")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume", e)
            Result.failure(e)
        }
    }
    
    /**
     * 현재 상태 가져오기
     */
    fun getStatus(): EngineStatus {
        return EngineStatus(
            isInitialized = isInitialized,
            isInChannel = isInChannel,
            isTransmitting = isTransmitting,
            currentChannel = currentChannel,
            currentUID = currentUID
        )
    }
    
    /**
     * 엔진 상태 데이터 클래스
     */
    data class EngineStatus(
        val isInitialized: Boolean,
        val isInChannel: Boolean,
        val isTransmitting: Boolean,
        val currentChannel: String?,
        val currentUID: Int
    )
}