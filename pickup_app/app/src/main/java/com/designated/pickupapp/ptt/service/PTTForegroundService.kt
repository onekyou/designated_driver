package com.designated.pickupapp.ptt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.VolumeProviderCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.designated.pickupapp.BuildConfig
import com.designated.pickupapp.MainActivity
import com.designated.pickupapp.R
import com.designated.pickupapp.ptt.core.PTTController
import com.designated.pickupapp.ptt.core.SimplePTTEngine
import com.designated.pickupapp.ptt.core.UIDManager
import com.designated.pickupapp.ptt.network.TokenManager
import com.designated.pickupapp.ptt.state.PTTState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.IRtcEngineEventHandler.AudioVolumeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PTT Foreground Service for Pickup App
 * 백그라운드에서도 안정적으로 PTT 기능을 제공하는 서비스
 */
class PTTForegroundService : Service() {
    private val TAG = "PTTForegroundService"
    
    // Service의 생명주기를 따르는 CoroutineScope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Service가 모든 주요 컴포넌트 소유
    private lateinit var pttEngine: SimplePTTEngine
    private lateinit var pttController: PTTController
    private lateinit var tokenManager: TokenManager
    
    // MediaSession 관련
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var volumeProvider: VolumeProviderCompat
    private var isTransmitting = false
    private var isPTTModeEnabled = true // PTT 모드 활성화 여부
    
    // Notification 관련
    private val NOTIFICATION_ID = 2001 // 픽업앱은 2001 사용
    private val CHANNEL_ID = "ptt_service_channel_pickup"
    
    // 서비스 상태
    private var isServiceRunning = false
    private var currentRegionId: String = ""
    private var currentOfficeId: String = ""
    
    companion object {
        // Service의 상태를 외부에 알리기 위한 StateFlow
        private val _pttState = MutableStateFlow<PTTState>(PTTState.Disconnected)
        val pttState: StateFlow<PTTState> = _pttState.asStateFlow()
        
        // PTT 모드 상태를 외부에 알리기 위한 StateFlow
        private val _isPTTModeEnabled = MutableStateFlow(true)
        val isPTTModeEnabled: StateFlow<Boolean> = _isPTTModeEnabled.asStateFlow()
        
        // Service 명령 Actions
        const val ACTION_INITIALIZE = "com.designated.pickupapp.action.INITIALIZE"
        const val ACTION_START_PTT = "com.designated.pickupapp.action.START_PTT"
        const val ACTION_STOP_PTT = "com.designated.pickupapp.action.STOP_PTT"
        const val ACTION_JOIN_CHANNEL = "com.designated.pickupapp.action.JOIN_CHANNEL"
        const val ACTION_LEAVE_CHANNEL = "com.designated.pickupapp.action.LEAVE_CHANNEL"
        const val ACTION_AUTO_JOIN = "com.designated.pickupapp.action.AUTO_JOIN"
        const val ACTION_TOGGLE_PTT_MODE = "com.designated.pickupapp.action.TOGGLE_PTT_MODE"
        const val ACTION_SHUTDOWN = "com.designated.pickupapp.action.SHUTDOWN"
        
        // Intent extras
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_SENDER_UID = "extra_sender_uid"
        const val EXTRA_REGION_ID = "extra_region_id"
        const val EXTRA_OFFICE_ID = "extra_office_id"
        
        // Service 시작 헬퍼 메서드
        fun startService(context: Context, regionId: String, officeId: String) {
            val intent = Intent(context, PTTForegroundService::class.java).apply {
                action = ACTION_INITIALIZE
                putExtra(EXTRA_REGION_ID, regionId)
                putExtra(EXTRA_OFFICE_ID, officeId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        // Service 중지 헬퍼 메서드
        fun stopService(context: Context) {
            val intent = Intent(context, PTTForegroundService::class.java).apply {
                action = ACTION_SHUTDOWN
            }
            context.startService(intent)
        }
        
        // PTT 모드 토글 헬퍼 메서드
        fun togglePTTMode(context: Context) {
            val intent = Intent(context, PTTForegroundService::class.java).apply {
                action = ACTION_TOGGLE_PTT_MODE
            }
            context.startService(intent)
        }
    }
    
    // Agora Event Handler
    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.i(TAG, "Joined channel: $channel with UID: $uid (elapsed: ${elapsed}ms)")
            _pttState.value = PTTState.Connected(channel ?: "", uid)
            updateNotification("연결됨: $channel")
        }
        
        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.i(TAG, "User joined with UID: $uid")
            val userType = UIDManager.getUserTypeFromUID(uid)
            Log.d(TAG, "User type: $userType")
            
            // 콜매니저 사용자 참여 알림
            if (UIDManager.isCallManagerUID(uid)) {
                updateNotification("관리자 참여: UID $uid")
            }
        }
        
        override fun onUserOffline(uid: Int, reason: Int) {
            Log.i(TAG, "User offline: $uid, reason: $reason")
        }
        
        override fun onError(err: Int) {
            Log.e(TAG, "Agora error: $err")
            val errorMessage = getAgoraErrorMessage(err)
            _pttState.value = PTTState.Error(errorMessage, err)
            handleAgoraError(err)
        }
        
        override fun onAudioVolumeIndication(
            speakers: Array<AudioVolumeInfo>?,
            totalVolume: Int
        ) {
            speakers?.forEach { speaker ->
                if (speaker.volume > 0) {
                    _pttState.value = PTTState.UserSpeaking(speaker.uid, speaker.volume)
                }
            }
        }
        
        override fun onConnectionStateChanged(state: Int, reason: Int) {
            Log.i(TAG, "Connection state changed: state=$state, reason=$reason")
            when (state) {
                Constants.CONNECTION_STATE_DISCONNECTED -> {
                    _pttState.value = PTTState.Disconnected
                    updateNotification("연결 해제됨")
                }
                Constants.CONNECTION_STATE_CONNECTING -> {
                    _pttState.value = PTTState.Connecting
                    updateNotification("연결 중...")
                }
                Constants.CONNECTION_STATE_CONNECTED -> {
                    // Connected state는 onJoinChannelSuccess에서 처리
                }
                Constants.CONNECTION_STATE_RECONNECTING -> {
                    _pttState.value = PTTState.Connecting
                    updateNotification("재연결 중...")
                }
                Constants.CONNECTION_STATE_FAILED -> {
                    _pttState.value = PTTState.Error("연결 실패", reason)
                    updateNotification("연결 실패")
                }
            }
        }
        
        override fun onTokenPrivilegeWillExpire(token: String?) {
            Log.w(TAG, "Token will expire soon, refreshing...")
            serviceScope.launch {
                refreshToken()
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        
        // 1. Notification Channel 생성
        createNotificationChannel()
        
        // 2. Foreground 시작
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 3. 컴포넌트 초기화 (Service가 소유)
        initializeComponents()
        
        // 4. MediaSession 초기화
        initializeMediaSession()
        
        isServiceRunning = true
    }
    
    private fun initializeComponents() {
        try {
            Log.d(TAG, "Initializing components...")
            
            // TokenManager 초기화
            tokenManager = TokenManager(FirebaseFunctions.getInstance("asia-northeast3"))
            
            // PTT Engine 초기화
            pttEngine = SimplePTTEngine()
            val appId = BuildConfig.AGORA_APP_ID
            val initResult = pttEngine.initialize(this, appId, rtcEventHandler)
            
            if (initResult.isFailure) {
                Log.e(TAG, "Failed to initialize PTT Engine", initResult.exceptionOrNull())
                _pttState.value = PTTState.Error("엔진 초기화 실패")
                return
            }
            
            // PTT Controller 초기화
            pttController = PTTController(
                context = this,
                engine = pttEngine,
                tokenManager = tokenManager,
                uidManager = UIDManager
            )
            
            Log.i(TAG, "Components initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize components", e)
            _pttState.value = PTTState.Error("초기화 실패: ${e.message}")
        }
    }
    
    /**
     * MediaSession 초기화 - 백그라운드에서 볼륨키 이벤트를 받기 위함
     */
    private fun initializeMediaSession() {
        try {
            Log.d(TAG, "Initializing MediaSession for volume key control...")
            
            // MediaSession 생성
            mediaSession = MediaSessionCompat(this, "PickupPTTServiceMediaSession")
            
            // 볼륨키 이벤트를 받기 위한 VolumeProvider 설정
            volumeProvider = object : VolumeProviderCompat(
                VolumeProviderCompat.VOLUME_CONTROL_RELATIVE,
                100, // maxVolume
                50   // currentVolume
            ) {
                override fun onAdjustVolume(direction: Int) {
                    Log.d(TAG, "Volume key detected: direction=$direction")
                    handleVolumeKeyEvent(direction)
                }
            }
            
            // MediaSession 설정
            mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            
            // PTT 모드일 때만 VolumeProvider를 MediaSession에 연결
            updateMediaSessionVolumeControl()
            
            // PlaybackState 설정 (필수)
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE
                )
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
            mediaSession.setPlaybackState(stateBuilder.build())
            
            // MediaButton 이벤트 콜백 설정
            mediaSession.setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null) {
                        Log.d(TAG, "MediaButton event: keyCode=${keyEvent.keyCode}, action=${keyEvent.action}")
                        
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_VOLUME_UP,
                            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                handleMediaVolumeKeyEvent(keyEvent)
                                return true // 이벤트 소비
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            
            // MediaSession 활성화 (매우 중요!)
            mediaSession.isActive = true
            
            Log.i(TAG, "MediaSession initialized and activated for volume key control")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaSession", e)
        }
    }
    
    /**
     * VolumeProvider를 통한 볼륨키 이벤트 처리
     * @param direction 음수(볼륨 다운), 양수(볼륨 업)
     */
    private fun handleVolumeKeyEvent(direction: Int) {
        Log.d(TAG, "Volume key event: direction=$direction, isPTTMode=$isPTTModeEnabled, isTransmitting=$isTransmitting")
        
        if (!isPTTModeEnabled) {
            Log.d(TAG, "PTT mode disabled, allowing normal volume control")
            return // PTT 모드가 비활성화되면 일반 볼륨 조절 허용
        }
        
        if (direction < 0) { // 볼륨 다운
            if (!isTransmitting) {
                Log.i(TAG, "Volume DOWN pressed - Starting PTT")
                isTransmitting = true
                handleStartPTT()
            }
        } else if (direction > 0) { // 볼륨 업 
            if (!isTransmitting) {
                Log.i(TAG, "Volume UP pressed - Starting PTT")
                isTransmitting = true
                handleStartPTT()
            }
        }
    }
    
    /**
     * MediaButton을 통한 볼륨키 이벤트 처리 (ACTION_DOWN/ACTION_UP)
     */
    private fun handleMediaVolumeKeyEvent(keyEvent: KeyEvent) {
        Log.d(TAG, "Media volume key: keyCode=${keyEvent.keyCode}, action=${keyEvent.action}, isPTTMode=$isPTTModeEnabled")
        
        if (!isPTTModeEnabled) {
            Log.d(TAG, "PTT mode disabled, passing volume key to system")
            return // PTT 모드가 비활성화되면 시스템이 처리하도록 함
        }
        
        when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!isTransmitting) {
                    Log.i(TAG, "Volume key DOWN - Starting PTT")
                    isTransmitting = true
                    handleStartPTT()
                }
            }
            
            KeyEvent.ACTION_UP -> {
                if (isTransmitting) {
                    Log.i(TAG, "Volume key UP - Stopping PTT")
                    isTransmitting = false
                    handleStopPTT()
                }
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        intent?.let {
            when (it.action) {
                ACTION_INITIALIZE -> handleInitialize(it)
                ACTION_START_PTT -> handleStartPTT()
                ACTION_STOP_PTT -> handleStopPTT()
                ACTION_JOIN_CHANNEL -> handleJoinChannel(it)
                ACTION_LEAVE_CHANNEL -> handleLeaveChannel()
                ACTION_AUTO_JOIN -> handleAutoJoin(it)
                ACTION_TOGGLE_PTT_MODE -> handleTogglePTTMode()
                ACTION_SHUTDOWN -> handleShutdown()
            }
        }
        
        return START_STICKY // 시스템이 종료해도 재시작
    }
    
    private fun handleInitialize(intent: Intent) {
        val regionId = intent.getStringExtra(EXTRA_REGION_ID) ?: ""
        val officeId = intent.getStringExtra(EXTRA_OFFICE_ID) ?: ""
        
        Log.i(TAG, "Initializing with region: $regionId, office: $officeId")
        
        if (regionId.isNotEmpty() && officeId.isNotEmpty()) {
            currentRegionId = regionId
            currentOfficeId = officeId
            pttController.setDefaultChannelInfo(regionId, officeId)
        }
        
        updateNotification("픽업 PTT 준비됨")
    }
    
    private fun handleStartPTT() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting PTT...")
                _pttState.value = PTTState.Connecting
                
                val uid = getOrCreateUID()
                val result = pttController.startPTT(uid)
                
                if (result.isSuccess) {
                    _pttState.value = PTTState.Transmitting(true)
                    updateNotification("송신 중...")
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "PTT start failed", error)
                    _pttState.value = PTTState.Error("PTT 시작 실패: ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "PTT start failed", e)
                _pttState.value = PTTState.Error("PTT 시작 실패: ${e.message}")
            }
        }
    }
    
    private fun handleStopPTT() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Stopping PTT...")
                val result = pttController.stopPTT()
                
                if (result.isSuccess) {
                    _pttState.value = PTTState.Transmitting(false)
                    // 채널 정보 유지
                    val status = pttController.getStatus()
                    if (status.isConnected) {
                        _pttState.value = PTTState.Connected(
                            status.currentChannel ?: "",
                            status.currentUID
                        )
                        updateNotification("연결됨: ${status.currentChannel}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "PTT stop failed", e)
            }
        }
    }
    
    private fun handleJoinChannel(intent: Intent) {
        val channel = intent.getStringExtra(EXTRA_CHANNEL)
        
        serviceScope.launch {
            try {
                Log.d(TAG, "Joining channel: $channel")
                _pttState.value = PTTState.Connecting
                
                val uid = getOrCreateUID()
                val result = pttController.joinChannel(channel, uid)
                
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Join channel failed", error)
                    _pttState.value = PTTState.Error("채널 참여 실패: ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Join channel failed", e)
                _pttState.value = PTTState.Error("채널 참여 실패: ${e.message}")
            }
        }
    }
    
    private fun handleLeaveChannel() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Leaving channel...")
                val result = pttController.leaveChannel()
                
                if (result.isSuccess) {
                    _pttState.value = PTTState.Disconnected
                    updateNotification("픽업 PTT 준비됨")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Leave channel failed", e)
            }
        }
    }
    
    private fun handleAutoJoin(intent: Intent) {
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: return
        val senderUid = intent.getIntExtra(EXTRA_SENDER_UID, 0)
        
        serviceScope.launch {
            try {
                Log.i(TAG, "Auto-joining channel: $channel from UID: $senderUid")
                _pttState.value = PTTState.Connecting
                
                val result = pttController.autoJoinChannel(channel, senderUid)
                
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Auto-join failed", error)
                    _pttState.value = PTTState.Error("자동 참여 실패: ${error?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-join failed", e)
                _pttState.value = PTTState.Error("자동 참여 실패: ${e.message}")
            }
        }
    }
    
    private fun handleShutdown() {
        Log.i(TAG, "Shutting down service...")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private suspend fun getOrCreateUID(): Int {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")
        
        // 픽업앱은 pickup_driver 타입 사용
        return UIDManager.getOrCreateUID(this, "pickup_driver", userId)
    }
    
    private suspend fun refreshToken() {
        try {
            val status = pttController.getStatus()
            if (status.isConnected && status.currentChannel != null) {
                Log.d(TAG, "Refreshing token for channel: ${status.currentChannel}")
                tokenManager.getToken(
                    status.currentChannel,
                    status.currentUID,
                    currentRegionId,
                    currentOfficeId,
                    "pickup_driver",
                    forceRefresh = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
        }
    }
    
    private fun handleAgoraError(errorCode: Int) {
        when (errorCode) {
            Constants.ERR_TOKEN_EXPIRED -> {
                Log.e(TAG, "Token expired, attempting to refresh...")
                serviceScope.launch {
                    refreshToken()
                }
            }
            Constants.ERR_INVALID_TOKEN -> {
                Log.e(TAG, "Invalid token")
                updateNotification("토큰 오류")
            }
            else -> {
                Log.e(TAG, "Agora error: $errorCode")
            }
        }
    }
    
    private fun getAgoraErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            Constants.ERR_TOKEN_EXPIRED -> "토큰 만료됨"
            Constants.ERR_INVALID_TOKEN -> "잘못된 토큰"
            Constants.ERR_INVALID_CHANNEL_NAME -> "잘못된 채널명"
            Constants.ERR_NOT_READY -> "준비되지 않음"
            Constants.ERR_CONNECTION_INTERRUPTED -> "연결 중단됨"
            else -> "오류 코드: $errorCode"
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "픽업 PTT Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "픽업 PTT 서비스 실행 중"
                setShowBadge(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String = "픽업 PTT 서비스 실행 중"): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("픽업 PTT")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_call) // TODO: 커스텀 아이콘
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service onDestroy")
        
        isServiceRunning = false
        
        // Coroutine 취소
        serviceScope.cancel()
        
        // 컴포넌트 정리
        try {
            pttController.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying components", e)
        }
        
        // 상태 초기화
        _pttState.value = PTTState.Disconnected
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // Bound service 아님
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed")
        // 서비스를 계속 실행하려면 아래 주석 해제
        // if (isServiceRunning) {
        //     val restartIntent = Intent(this, PTTForegroundService::class.java)
        //     startService(restartIntent)
        // }
        super.onTaskRemoved(rootIntent)
    }
}