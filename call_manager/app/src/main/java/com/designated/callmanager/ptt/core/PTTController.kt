package com.designated.callmanager.ptt.core

import android.content.Context
import android.util.Log
import com.designated.callmanager.BuildConfig
import com.designated.callmanager.ptt.network.TokenManager
import com.designated.callmanager.ptt.state.TokenResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val TAG = "PTTController"
    
    private var currentChannel: String? = null
    private var currentUID: Int = 0
    private var isConnected = false
    
    // 기본 채널 설정 (region_office_ptt 형식)
    private var defaultRegionId: String = ""
    private var defaultOfficeId: String = ""
    
    /**
     * 기본 채널 정보 설정
     */
    fun setDefaultChannelInfo(regionId: String, officeId: String) {
        defaultRegionId = regionId
        defaultOfficeId = officeId
        Log.d(TAG, "Default channel info set: ${regionId}_${officeId}")
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
            // 1. UID 확인 또는 생성
            val finalUID = uid ?: run {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                    ?: return@withContext Result.failure(Exception("User not authenticated"))
                
                uidManager.getOrCreateUID(context, "call_manager", userId)
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
            
            // 3. 이미 같은 채널에 연결되어 있으면 전송만 시작
            if (isConnected && currentChannel == channelName && currentUID == finalUID) {
                Log.d(TAG, "Already connected to $channelName, starting transmission")
                return@withContext engine.startTransmit()
            }
            
            // 4. 토큰 획득
            Log.i(TAG, "Getting token for channel: $channelName, UID: $finalUID")
            val tokenResult = tokenManager.getToken(channelName, finalUID, defaultRegionId, defaultOfficeId)
            
            if (tokenResult !is TokenResult.Success) {
                val error = (tokenResult as? TokenResult.Failure)?.error
                return@withContext Result.failure(
                    error ?: Exception("Failed to get token")
                )
            }
            
            // 5. 채널 참여
            Log.i(TAG, "Joining channel: $channelName with UID: $finalUID")
            val joinResult = engine.joinChannel(channelName, tokenResult.token, finalUID)
            
            if (joinResult.isFailure) {
                return@withContext joinResult
            }
            
            // 6. 상태 업데이트
            currentChannel = channelName
            currentUID = finalUID
            isConnected = true
            
            // 7. 전송 시작
            engine.startTransmit()
            
            Log.i(TAG, "PTT started successfully on channel: $channelName")
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
            
            Log.d(TAG, "Stopping PTT transmission")
            engine.stopTransmit()
            
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
            // 1. UID 확인 또는 생성
            val finalUID = uid ?: run {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                    ?: return@withContext Result.failure(Exception("User not authenticated"))
                
                uidManager.getOrCreateUID(context, "call_manager", userId)
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
            val tokenResult = tokenManager.getToken(channelName, finalUID, defaultRegionId, defaultOfficeId)
            
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
            engine.destroy()
            currentChannel = null
            currentUID = 0
            isConnected = false
            Log.i(TAG, "PTT Controller destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error while destroying controller", e)
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