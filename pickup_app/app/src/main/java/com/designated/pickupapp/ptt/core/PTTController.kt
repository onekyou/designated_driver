package com.designated.pickupapp.ptt.core

import android.content.Context
import android.util.Log
import com.designated.pickupapp.BuildConfig
import com.designated.pickupapp.data.PTTStatus
import com.designated.pickupapp.ptt.manager.SignalingManager
import com.designated.pickupapp.ptt.network.TokenManager
import com.designated.pickupapp.ptt.state.TokenResult
import com.designated.pickupapp.ptt.state.PTTLockManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PTT 시스템 컨트롤러
 * Engine, TokenManager, UIDManager를 조율하여 PTT 기능 제공
 */
class PTTController(
    private val context: Context,
    private val engine: SimplePTTEngine,
    private val tokenManager: TokenManager,
    private val uidManager: UIDManager = UIDManager
) {
    private val TAG = "PickupPTTController"
    
    private var currentChannel: String? = null
    private var currentUID: Int = 0
    private var isConnected = false
    
    // 기본 채널 설정 (region_office_ptt 형식)
    private var defaultRegionId: String = ""
    private var defaultOfficeId: String = ""
    
    // RTM 시그널링 매니저 (옵셔널)
    private var signalingManager: SignalingManager? = null
    private val rtmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 비프음 매니저 추가
    private val beepSoundManager: BeepSoundManager = BeepSoundManager(context)
    
    // PTT 상태 관리
    private val _pttStatusFlow = MutableStateFlow<PTTStatus?>(null)
    val pttStatusFlow: StateFlow<PTTStatus?> = _pttStatusFlow.asStateFlow()
    
    // PTT 충돌 방지 매니저
    private val pttLockManager = PTTLockManager()
    
    init {
        initializeRTMIfPossible()
    }
    
    /**
     * RTM 초기화 (실패해도 PTT 기능에는 영향 없음)
     */
    private fun initializeRTMIfPossible() {
        rtmScope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null && BuildConfig.AGORA_APP_ID.isNotEmpty()) {
                    Log.d(TAG, "Initializing Pickup RTM for user: $userId")
                    
                    signalingManager = SignalingManager(
                        context = context,
                        appId = BuildConfig.AGORA_APP_ID,
                        userId = userId,
                        onPTTStatusChanged = { pttStatus ->
                            _pttStatusFlow.value = pttStatus
                            // Lock Manager 업데이트
                            rtmScope.launch {
                                pttLockManager.updateRemoteUserStatus(
                                    userId = pttStatus.userId,
                                    isTransmitting = pttStatus.isTransmitting,
                                    timestamp = pttStatus.timestamp
                                )
                            }
                        }
                    )
                    
                    signalingManager?.initialize { success ->
                        if (success) {
                            signalingManager?.login()
                            Log.i(TAG, "Pickup RTM initialized successfully")
                        } else {
                            Log.w(TAG, "Pickup RTM initialization failed")
                            signalingManager = null
                        }
                    }
                } else {
                    Log.w(TAG, "Pickup RTM initialization skipped (no user or app ID)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pickup RTM initialization error: $e")
                signalingManager = null
            }
        }
    }
    
    /**
     * RTM 채널 자동 구독 (기본 채널 설정 후 호출)
     */
    private fun subscribeToRTMChannelIfReady() {
        rtmScope.launch {
            try {
                val channelName = getDefaultChannel()
                if (channelName.isNotEmpty() && signalingManager != null) {
                    signalingManager?.subscribeChannel(channelName)
                    Log.d(TAG, "Pickup RTM auto-subscribed to: $channelName")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pickup RTM auto-subscribe failed: $e")
            }
        }
    }
    
    /**
     * RTM PTT 시작 신호 전송 (옵셔널)
     */
    private fun sendRTMStartSignal(channel: String, uid: Int) {
        rtmScope.launch {
            try {
                signalingManager?.sendPttStart(channel)
                Log.d(TAG, "Pickup RTM PTT start signal sent")
            } catch (e: Exception) {
                Log.w(TAG, "Pickup RTM start signal failed: $e")
            }
        }
    }
    
    /**
     * RTM PTT 종료 신호 전송 (옵셔널)
     */
    private fun sendRTMStopSignal(channel: String, uid: Int) {
        rtmScope.launch {
            try {
                signalingManager?.sendPttEnd(channel)
                Log.d(TAG, "Pickup RTM PTT stop signal sent")
            } catch (e: Exception) {
                Log.w(TAG, "Pickup RTM stop signal failed: $e")
            }
        }
    }

    /**
     * 기본 채널 정보 설정
     */
    fun setDefaultChannelInfo(regionId: String, officeId: String) {
        defaultRegionId = regionId
        defaultOfficeId = officeId
        Log.d(TAG, "Pickup default channel info set: ${regionId}_${officeId}")
        
        // RTM 채널 자동 구독
        subscribeToRTMChannelIfReady()
    }
    
    /**
     * PTT 시작 (채널 참여 및 전송 시작)
     * @param uid 사용자 UID (null이면 자동 생성)
     * @param channel 채널명 (null이면 기본 채널)
     * @return 시작 결과
     */
    suspend fun startPTT(
        uid: Int? = null,
        channel: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. UID 확인 또는 생성 (픽업앱은 pickup_driver 타입)
            val finalUID = uid ?: run {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                    ?: return@withContext Result.failure(Exception("User not authenticated"))
                
                uidManager.getOrCreateUID(context, "pickup_driver", userId)
            }
            
            if (!uidManager.validateUID(finalUID)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid UID: $finalUID")
                )
            }
            
            // 2. 채널명 결정
            val channelName = channel ?: getDefaultChannel()
            if (channelName.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Channel name not provided and default not set")
                )
            }
            
            // 3. PTT 충돌 체크
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val canStart = pttLockManager.canStartPTT(userId)
            if (!canStart) {
                val currentUser = pttLockManager.getCurrentTransmitter()
                Log.w(TAG, "PTT 충돌 감지 - 현재 사용자: $currentUser")
                return@withContext Result.failure(
                    Exception("다른 사용자가 PTT를 사용 중입니다")
                )
            }
            
            // 4. PTT Lock 획득
            val lockAcquired = pttLockManager.acquirePTTLock(userId)
            if (!lockAcquired) {
                return@withContext Result.failure(
                    Exception("PTT 사용 권한을 얻지 못했습니다")
                )
            }
            
            // 5. 이미 같은 채널에 연결되어 있으면 전송만 시작
            if (isConnected && currentChannel == channelName && currentUID == finalUID) {
                Log.d(TAG, "Already connected to $channelName, starting transmission")
                val result = engine.startTransmit()
                // RTM 시작 신호 전송 (이미 연결된 경우)
                sendRTMStartSignal(channelName, finalUID)
                return@withContext result
            }
            
            // 6. 토큰 획득
            Log.i(TAG, "Getting token for channel: $channelName, UID: $finalUID")
            val tokenResult = tokenManager.getToken(channelName, finalUID, defaultRegionId, defaultOfficeId, "pickup_driver")
            
            if (tokenResult !is TokenResult.Success) {
                // Lock 해제
                pttLockManager.releasePTTLock(userId)
                val error = (tokenResult as? TokenResult.Failure)?.error
                return@withContext Result.failure(
                    error ?: Exception("Failed to get token")
                )
            }
            
            // 7. 채널 참여
            Log.i(TAG, "Joining channel: $channelName with UID: $finalUID")
            val joinResult = engine.joinChannel(channelName, tokenResult.token, finalUID)
            
            if (joinResult.isFailure) {
                // Lock 해제
                pttLockManager.releasePTTLock(userId)
                return@withContext joinResult
            }
            
            // 8. 상태 업데이트
            currentChannel = channelName
            currentUID = finalUID
            isConnected = true
            
            // 9. 전송 시작
            engine.startTransmit()
            
            // 10. RTM 시작 신호 전송 (실패해도 PTT는 정상 동작)
            sendRTMStartSignal(channelName, finalUID)
            
            Log.i(TAG, "Pickup PTT started successfully on channel: $channelName")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PTT", e)
            Result.failure(e)
        }
    }
    
    /**
     * PTT 중지 (전송만 중지, 채널은 유지)
     */
    suspend fun stopPTT(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isConnected) {
                Log.w(TAG, "Not connected to any channel")
                return@withContext Result.success(Unit)
            }
            
            Log.d(TAG, "Stopping Pickup PTT transmission")
            
            // RTM 종료 신호 먼저 전송
            sendRTMStopSignal(currentChannel!!, currentUID)
            
            // Lock 해제
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            pttLockManager.releasePTTLock(userId)
            
            engine.stopTransmit()
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop PTT", e)
            Result.failure(e)
        }
    }
    
    /**
     * 채널 참여 (전송하지 않고 듣기만)
     */
    suspend fun joinChannel(
        channel: String? = null,
        uid: Int? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. UID 확인 또는 생성 (픽업앱은 pickup_driver 타입)
            val finalUID = uid ?: run {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                    ?: return@withContext Result.failure(Exception("User not authenticated"))
                
                uidManager.getOrCreateUID(context, "pickup_driver", userId)
            }
            
            // 2. 채널명 결정
            val channelName = channel ?: getDefaultChannel()
            if (channelName.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Channel name not provided")
                )
            }
            
            // 3. 이미 연결되어 있으면 성공 반환
            if (isConnected && currentChannel == channelName && currentUID == finalUID) {
                Log.d(TAG, "Already connected to $channelName")
                return@withContext Result.success(Unit)
            }
            
            // 4. 토큰 획득
            val tokenResult = tokenManager.getToken(channelName, finalUID, defaultRegionId, defaultOfficeId, "pickup_driver")
            
            if (tokenResult !is TokenResult.Success) {
                val error = (tokenResult as? TokenResult.Failure)?.error
                return@withContext Result.failure(
                    error ?: Exception("Failed to get token")
                )
            }
            
            // 5. 채널 참여
            val joinResult = engine.joinChannel(channelName, tokenResult.token, finalUID)
            
            if (joinResult.isFailure) {
                return@withContext joinResult
            }
            
            // 6. 상태 업데이트
            currentChannel = channelName
            currentUID = finalUID
            isConnected = true
            
            Log.i(TAG, "Joined channel: $channelName as listener")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join channel", e)
            Result.failure(e)
        }
    }
    
    /**
     * 채널 나가기
     */
    suspend fun leaveChannel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isConnected) {
                Log.w(TAG, "Not connected to any channel")
                return@withContext Result.success(Unit)
            }
            
            Log.i(TAG, "Leaving channel: $currentChannel")
            
            val result = engine.leaveChannel()
            
            if (result.isSuccess) {
                currentChannel = null
                currentUID = 0
                isConnected = false
            }
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave channel", e)
            Result.failure(e)
        }
    }
    
    /**
     * 자동 채널 참여 (FCM 트리거)
     */
    suspend fun autoJoinChannel(
        channel: String,
        senderUID: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Auto-joining channel: $channel triggered by UID: $senderUID")
            
            // 송신자가 같은 타입의 사용자인지 확인
            val senderType = uidManager.getUserTypeFromUID(senderUID)
            if (senderType != "call_manager" && senderType != "pickup_driver") {
                Log.w(TAG, "Unknown sender type for UID: $senderUID")
            }
            
            // 채널 참여 (듣기 모드)
            joinChannel(channel)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-join channel", e)
            Result.failure(e)
        }
    }
    
    /**
     * 기본 채널명 생성
     */
    private fun getDefaultChannel(): String {
        return if (defaultRegionId.isNotEmpty() && defaultOfficeId.isNotEmpty()) {
            "${defaultRegionId}_${defaultOfficeId}_ptt"
        } else {
            ""
        }
    }
    
    /**
     * 현재 상태 가져오기
     */
    fun getStatus(): ControllerStatus {
        val engineStatus = engine.getStatus()
        return ControllerStatus(
            isConnected = isConnected,
            currentChannel = currentChannel,
            currentUID = currentUID,
            engineStatus = engineStatus
        )
    }
    
    /**
     * 정리
     */
    fun destroy() {
        try {
            // RTM 정리
            rtmScope.launch {
                try {
                    signalingManager?.logout()
                    signalingManager?.release()
                    signalingManager = null
                    Log.d(TAG, "Pickup RTM cleaned up")
                } catch (e: Exception) {
                    Log.w(TAG, "Pickup RTM cleanup error: $e")
                }
            }
            
            // RTC 정리
            engine.destroy()
            currentChannel = null
            currentUID = 0
            isConnected = false
            Log.i(TAG, "Pickup PTT Controller destroyed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error while destroying pickup controller", e)
        }
    }
    
    /**
     * 컨트롤러 상태 데이터 클래스
     */
    data class ControllerStatus(
        val isConnected: Boolean,
        val currentChannel: String?,
        val currentUID: Int,
        val engineStatus: SimplePTTEngine.EngineStatus
    )
}