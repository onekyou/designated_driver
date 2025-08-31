package com.designated.callmanager.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.designated.callmanager.BuildConfig
import io.agora.rtc2.*
import kotlinx.coroutines.*

/**
 * PTT Manager Service - Agora SDK 최적화 버전
 * 백그라운드 오디오 처리 및 오디오 포커스 관리
 */
class PTTManagerService : Service() {
    
    companion object {
        private const val TAG = "PTTManagerService"
        private var instance: PTTManagerService? = null
        
        fun getInstance(): PTTManagerService? = instance
    }
    
    private var rtcEngine: RtcEngine? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 현재 상태
    private var isTransmitting = false
    private var isConnected = false
    private var currentChannel: String? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "PTTManagerService created")
        
        initializeAudioManager()
        initializeWakeLock()
        initializeAgoraEngine()
    }
    
    /**
     * 오디오 매니저 초기화
     */
    private fun initializeAudioManager() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Android O 이상에서 AudioFocusRequest 생성
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
        }
        
        Log.d(TAG, "Audio manager initialized")
    }
    
    /**
     * WakeLock 초기화
     */
    private fun initializeWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CallManager:PTTWakeLock"
        )
        Log.d(TAG, "WakeLock initialized")
    }
    
    /**
     * Agora 엔진 초기화 - 백그라운드 최적화
     */
    private fun initializeAgoraEngine() {
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = BuildConfig.AGORA_APP_ID
                mEventHandler = rtcEventHandler
                
                // 백그라운드 모드 최적화 설정
                mAudioScenario = Constants.AUDIO_SCENARIO_DEFAULT
                mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
            }
            
            rtcEngine = RtcEngine.create(config).apply {
                // 오디오 활성화
                enableAudio()
                
                // 백그라운드 오디오 처리 활성화
                enableLocalAudio(true)
                
                // 스피커폰 비활성화 (이어폰/헤드셋 우선)
                setDefaultAudioRoutetoSpeakerphone(false)
                setEnableSpeakerphone(false)
                
                // 오디오 프로파일 설정 (고품질, 저지연)
                setAudioProfile(
                    Constants.AUDIO_PROFILE_SPEECH_STANDARD,
                    Constants.AUDIO_SCENARIO_DEFAULT
                )
                
                // 에코 캔슬레이션 활성화
                setParameters("{\"che.audio.enable.aec\":true}")
                // 노이즈 억제 활성화
                setParameters("{\"che.audio.enable.ns\":true}")
                // 자동 게인 제어 활성화
                setParameters("{\"che.audio.enable.agc\":true}")
                
                // 오디오 볼륨 인디케이터 활성화
                enableAudioVolumeIndication(200, 3, true)
                
                // 클라이언트 역할 설정 (BROADCASTER로 설정)
                setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
                
                // 기본적으로 로컬 오디오 음소거
                muteLocalAudioStream(true)
                
                Log.i(TAG, "Agora engine initialized with background optimization")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Agora engine", e)
        }
    }
    
    /**
     * Agora 이벤트 핸들러
     */
    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.i(TAG, "Joined channel: $channel with UID: $uid")
            isConnected = true
            currentChannel = channel
        }
        
        override fun onLeaveChannel(stats: RtcStats?) {
            Log.i(TAG, "Left channel")
            isConnected = false
            currentChannel = null
            releaseAudioFocus()
        }
        
        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.i(TAG, "User joined: $uid")
        }
        
        override fun onUserOffline(uid: Int, reason: Int) {
            Log.i(TAG, "User offline: $uid")
        }
        
        override fun onError(err: Int) {
            Log.e(TAG, "Agora error: $err")
            handleAgoraError(err)
        }
        
        override fun onConnectionStateChanged(state: Int, reason: Int) {
            Log.i(TAG, "Connection state changed: state=$state, reason=$reason")
            when (state) {
                Constants.CONNECTION_STATE_DISCONNECTED -> {
                    isConnected = false
                    releaseAudioFocus()
                    releaseWakeLock()
                }
                Constants.CONNECTION_STATE_CONNECTED -> {
                    isConnected = true
                }
                Constants.CONNECTION_STATE_FAILED -> {
                    isConnected = false
                    releaseAudioFocus()
                    releaseWakeLock()
                }
            }
        }
        
        override fun onAudioVolumeIndication(
            speakers: Array<out AudioVolumeInfo>?,
            totalVolume: Int
        ) {
            // 볼륨 레벨 모니터링 (필요시 UI 업데이트)
            speakers?.forEach { speaker ->
                if (speaker.volume > 0) {
                    Log.v(TAG, "Speaker UID: ${speaker.uid}, Volume: ${speaker.volume}")
                }
            }
        }
    }
    
    /**
     * PTT 송신 시작
     */
    fun startTransmission() {
        if (!isConnected) {
            Log.w(TAG, "Not connected to channel")
            return
        }
        
        serviceScope.launch {
            try {
                // 오디오 포커스 요청
                requestAudioFocus()
                
                // 웨이크락 획득
                acquireWakeLock()
                
                // 로컬 오디오 스트림 활성화
                rtcEngine?.muteLocalAudioStream(false)
                
                // 오디오 라우팅 설정
                setupAudioRouting()
                
                isTransmitting = true
                Log.i(TAG, "PTT transmission started")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start transmission", e)
                stopTransmission()
            }
        }
    }
    
    /**
     * PTT 송신 중지
     */
    fun stopTransmission() {
        serviceScope.launch {
            try {
                // 로컬 오디오 스트림 음소거
                rtcEngine?.muteLocalAudioStream(true)
                
                // 오디오 포커스 해제
                releaseAudioFocus()
                
                // 웨이크락 해제
                releaseWakeLock()
                
                isTransmitting = false
                Log.i(TAG, "PTT transmission stopped")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop transmission", e)
            }
        }
    }
    
    /**
     * 채널 참여
     */
    fun joinChannel(channel: String, uid: Int, token: String) {
        serviceScope.launch {
            try {
                val options = ChannelMediaOptions().apply {
                    channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                    autoSubscribeAudio = true
                    publishMicrophoneTrack = false // 처음에는 음소거 상태
                }
                
                val result = rtcEngine?.joinChannel(token, channel, uid, options)
                if (result == 0) {
                    Log.i(TAG, "Join channel request sent: $channel")
                } else {
                    Log.e(TAG, "Failed to join channel: $result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error joining channel", e)
            }
        }
    }
    
    /**
     * 채널 나가기
     */
    fun leaveChannel() {
        serviceScope.launch {
            try {
                rtcEngine?.leaveChannel()
                isConnected = false
                currentChannel = null
                releaseAudioFocus()
                releaseWakeLock()
                Log.i(TAG, "Left channel")
            } catch (e: Exception) {
                Log.e(TAG, "Error leaving channel", e)
            }
        }
    }
    
    /**
     * 오디오 포커스 요청
     */
    private fun requestAudioFocus() {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.requestAudioFocus(it)
            } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
        
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.i(TAG, "Audio focus granted")
        } else {
            Log.w(TAG, "Audio focus request failed")
        }
    }
    
    /**
     * 오디오 포커스 해제
     */
    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
        Log.i(TAG, "Audio focus released")
    }
    
    /**
     * 오디오 포커스 변경 처리
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus gained")
                // 볼륨 복원
                rtcEngine?.adjustRecordingSignalVolume(100)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus lost")
                // PTT 중지
                if (isTransmitting) {
                    stopTransmission()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus lost transient")
                // 일시적 손실, PTT 일시 중지
                rtcEngine?.muteLocalAudioStream(true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.w(TAG, "Audio focus loss can duck")
                // 볼륨 줄이기
                rtcEngine?.adjustRecordingSignalVolume(50)
            }
        }
    }
    
    /**
     * 오디오 라우팅 설정
     */
    private fun setupAudioRouting() {
        // 블루투스 헤드셋 연결 확인
        if (audioManager.isBluetoothScoAvailableOffCall) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            Log.i(TAG, "Bluetooth audio routing enabled")
        } else if (audioManager.isWiredHeadsetOn) {
            // 유선 헤드셋 사용
            rtcEngine?.setEnableSpeakerphone(false)
            Log.i(TAG, "Wired headset audio routing")
        } else {
            // 스피커폰 사용
            rtcEngine?.setEnableSpeakerphone(true)
            Log.i(TAG, "Speakerphone audio routing")
        }
        
        // 오디오 모드 설정
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }
    
    /**
     * 웨이크락 획득
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10 * 60 * 1000L) // 최대 10분
            Log.d(TAG, "WakeLock acquired")
        }
    }
    
    /**
     * 웨이크락 해제
     */
    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released")
        }
    }
    
    /**
     * Agora 에러 처리
     */
    private fun handleAgoraError(errorCode: Int) {
        when (errorCode) {
            Constants.ERR_TOKEN_EXPIRED -> {
                Log.e(TAG, "Token expired, need refresh")
                // TODO: 토큰 갱신 로직
            }
            Constants.ERR_INVALID_TOKEN -> {
                Log.e(TAG, "Invalid token")
            }
            Constants.ERR_CONNECTION_INTERRUPTED -> {
                Log.e(TAG, "Connection interrupted")
                releaseAudioFocus()
                releaseWakeLock()
            }
            else -> {
                Log.e(TAG, "Agora error code: $errorCode")
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        
        // 리소스 정리
        serviceScope.cancel()
        rtcEngine?.leaveChannel()
        RtcEngine.destroy()
        releaseAudioFocus()
        releaseWakeLock()
        
        Log.i(TAG, "PTTManagerService destroyed")
    }
}