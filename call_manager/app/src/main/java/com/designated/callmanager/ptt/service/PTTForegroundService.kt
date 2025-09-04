package com.designated.callmanager.ptt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.designated.callmanager.BuildConfig
import com.designated.callmanager.MainActivity
import com.designated.callmanager.R
import com.designated.callmanager.ptt.core.BeepSoundManager
import com.designated.callmanager.ptt.core.PTTController
import com.designated.callmanager.ptt.core.SimplePTTEngine
import com.designated.callmanager.ptt.core.UIDManager
import com.designated.callmanager.ptt.network.TokenManager
import com.designated.callmanager.ptt.state.PTTState
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
 * PTT Foreground Service
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
    private lateinit var beepSoundManager: BeepSoundManager
    
    // Notification 관련
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "ptt_service_channel"
    
    // WakeLock 관련
    private var wakeLock: PowerManager.WakeLock? = null
    private var powerManager: PowerManager? = null
    
    // 서비스 상태
    private var isServiceRunning = false
    private var currentRegionId: String = ""
    private var currentOfficeId: String = ""
    
    companion object {
        // Service의 상태를 외부에 알리기 위한 StateFlow
        private val _pttState = MutableStateFlow<PTTState>(PTTState.Disconnected)
        val pttState: StateFlow<PTTState> = _pttState.asStateFlow()
        
        // Service 명령 Actions
        const val ACTION_INITIALIZE = "com.designated.callmanager.action.INITIALIZE"
        const val ACTION_START_PTT = "com.designated.callmanager.action.START_PTT"
        const val ACTION_STOP_PTT = "com.designated.callmanager.action.STOP_PTT"
        const val ACTION_JOIN_CHANNEL = "com.designated.callmanager.action.JOIN_CHANNEL"
        const val ACTION_LEAVE_CHANNEL = "com.designated.callmanager.action.LEAVE_CHANNEL"
        const val ACTION_AUTO_JOIN = "com.designated.callmanager.action.AUTO_JOIN"
        const val ACTION_SHUTDOWN = "com.designated.callmanager.action.SHUTDOWN"
        
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
    }
    
    // Agora Event Handler
    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.i(TAG, "Joined channel: $channel with UID: $uid (elapsed: ${elapsed}ms)")
            // handleStartPTT에서 이미 Transmitting 상태로 설정했으므로 여기서는 로그만
            // _pttState.value = PTTState.Connected(channel ?: "", uid)  // 제거
            // updateNotification("연결됨: $channel")  // 제거
        }
        
        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.i(TAG, "User joined with UID: $uid")
            val userType = UIDManager.getUserTypeFromUID(uid)
            Log.d(TAG, "User type: $userType")
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
            // 볼륨 표시는 로그만 남기고 상태 변경 제거 (UI 깜빡임 방지)
            speakers?.forEach { speaker ->
                if (speaker.volume > 0) {
                    Log.v(TAG, "User ${speaker.uid} speaking with volume ${speaker.volume}")
                    // _pttState.value = PTTState.UserSpeaking(speaker.uid, speaker.volume)  // 제거
                }
            }
        }
        
        override fun onConnectionStateChanged(state: Int, reason: Int) {
            Log.i(TAG, "Connection state changed: state=$state, reason=$reason")
            // 상태 변경은 handleStartPTT/handleStopPTT에서만 처리
            // 여기서는 에러 상황만 처리
            when (state) {
                Constants.CONNECTION_STATE_DISCONNECTED -> {
                    // stopPTT에 의한 정상 종료가 아닌 경우만 처리
                    if (_pttState.value !is PTTState.Disconnected) {
                        Log.w(TAG, "Unexpected disconnection")
                    }
                }
                Constants.CONNECTION_STATE_CONNECTING -> {
                    // 로그만
                }
                Constants.CONNECTION_STATE_CONNECTED -> {
                    // 로그만
                }
                Constants.CONNECTION_STATE_RECONNECTING -> {
                    Log.w(TAG, "Reconnecting...")
                    updateNotification("재연결 중...")
                }
                Constants.CONNECTION_STATE_FAILED -> {
                    _pttState.value = PTTState.Error("연결 실패", reason)
                    updateNotification("연결 실패")
                    beepSoundManager.playErrorSound()  // 에러음 추가
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
        
        // 3. PowerManager 및 WakeLock 초기화
        initializePowerManagement()
        
        // 4. 컴포넌트 초기화 (Service가 소유)
        initializeComponents()
        
        // 5. 배터리 최적화 예외 체크
        checkBatteryOptimization()
        
        isServiceRunning = true
    }
    
    private fun initializePowerManagement() {
        try {
            powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            // Partial WakeLock 생성 - 화면이 꺼져도 CPU는 계속 작동
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "CallManager:PTTWakeLock"
            )?.apply {
                setReferenceCounted(false) // 참조 카운트 사용 안함
            }
            
            Log.i(TAG, "PowerManagement initialized - WakeLock ready")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PowerManagement", e)
        }
    }
    
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = powerManager ?: return
            val packageName = packageName
            
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Log.w(TAG, "App is NOT exempted from battery optimization")
                // 나중에 UI에서 사용자에게 요청할 수 있도록 상태 저장
                // SharedPreferences나 다른 방법으로 저장
            } else {
                Log.i(TAG, "App is exempted from battery optimization ✓")
            }
        }
    }
    
    private fun acquireWakeLock(duration: Long = 10 * 60 * 1000L) {
        try {
            wakeLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire(duration) // 기본 10분, 최대 시간 제한
                    Log.i(TAG, "WakeLock acquired for ${duration/1000} seconds")
                } else {
                    Log.d(TAG, "WakeLock already held")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Log.i(TAG, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }
    
    private fun initializeComponents() {
        try {
            Log.d(TAG, "Initializing components...")
            
            // TokenManager 초기화
            tokenManager = TokenManager(FirebaseFunctions.getInstance("asia-northeast3"))
            
            // BeepSoundManager 초기화
            beepSoundManager = BeepSoundManager(this)
            
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
        
        updateNotification("PTT 준비됨")
    }
    
    private fun handleStartPTT() {
        // 시작 비프음은 AccessibilityService에서 이미 재생했으므로 제거
        
        // PTT 시작 시 WakeLock 획득 (30분 제한)
        acquireWakeLock(30 * 60 * 1000L)
        
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting PTT...")
                _pttState.value = PTTState.Connecting
                updateNotification("연결 중...")
                
                val uid = getOrCreateUID()
                val result = pttController.startPTT(uid)
                
                if (result.isSuccess) {
                    // 연결 성공 - 상태만 업데이트
                    _pttState.value = PTTState.Transmitting(true)
                    updateNotification("송신 중...")
                    Log.i(TAG, "PTT started successfully")
                } else {
                    // 연결 실패 - 에러음 & WakeLock 해제
                    beepSoundManager.playErrorSound()
                    releaseWakeLock()
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "PTT start failed", error)
                    _pttState.value = PTTState.Error("PTT 시작 실패: ${error?.message}")
                    updateNotification("연결 실패")
                }
            } catch (e: Exception) {
                // 예외 발생 - 에러음 & WakeLock 해제
                beepSoundManager.playErrorSound()
                releaseWakeLock()
                Log.e(TAG, "PTT start failed", e)
                _pttState.value = PTTState.Error("PTT 시작 실패: ${e.message}")
                updateNotification("연결 실패")
            }
        }
    }
    
    private fun handleStopPTT() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Stopping PTT...")
                val result = pttController.stopPTT()
                
                if (result.isSuccess) {
                    beepSoundManager.playEndBeep() // 종료 비프음 재생
                    releaseWakeLock() // PTT 종료 시 WakeLock 해제
                    _pttState.value = PTTState.Transmitting(false)
                    // 채널에서 완전히 나갔으므로 Disconnected 상태로 변경
                    _pttState.value = PTTState.Disconnected
                    updateNotification("PTT 준비됨")
                }
            } catch (e: Exception) {
                Log.e(TAG, "PTT stop failed", e)
                releaseWakeLock() // 에러 시에도 WakeLock 해제
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
                    updateNotification("PTT 준비됨")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Leave channel failed", e)
            }
        }
    }
    
    private fun handleAutoJoin(intent: Intent) {
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: return
        val senderUid = intent.getIntExtra(EXTRA_SENDER_UID, 0)
        
        // 자동 참여 시에도 WakeLock 획득 (화면 꺼진 상태에서도 수신 보장)
        acquireWakeLock(30 * 60 * 1000L)
        
        serviceScope.launch {
            try {
                Log.i(TAG, "Auto-joining channel: $channel from UID: $senderUid")
                _pttState.value = PTTState.Connecting
                
                val result = pttController.autoJoinChannel(channel, senderUid)
                
                if (result.isFailure) {
                    releaseWakeLock() // 실패 시 WakeLock 해제
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Auto-join failed", error)
                    _pttState.value = PTTState.Error("자동 참여 실패: ${error?.message}")
                } else {
                    Log.i(TAG, "Auto-join successful - WakeLock held for RTM reception")
                    updateNotification("수신 중...")
                }
            } catch (e: Exception) {
                releaseWakeLock() // 예외 시 WakeLock 해제
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
        
        return UIDManager.getOrCreateUID(this, "call_manager", userId)
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
                    "call_manager",
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
                "PTT Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "PTT 서비스 실행 중"
                setShowBadge(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String = "PTT 서비스 실행 중"): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PTT 서비스")
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
        
        // WakeLock 해제
        releaseWakeLock()
        
        // Coroutine 취소
        serviceScope.cancel()
        
        // 컴포넌트 정리
        try {
            pttController.destroy()
            beepSoundManager.release()
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