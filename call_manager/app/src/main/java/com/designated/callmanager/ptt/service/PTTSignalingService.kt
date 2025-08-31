package com.designated.callmanager.ptt.service

import android.content.Intent
import android.os.Build
import android.util.Log
import com.designated.callmanager.ptt.core.UIDManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * PTT FCM 시그널링 서비스
 * FCM을 통한 자동 채널 참여 및 PTT 신호 처리
 */
class PTTSignalingService : FirebaseMessagingService() {
    private val TAG = "PTTSignalingService"
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d(TAG, "FCM message received: ${message.data}")
        
        // FCM 데이터 처리
        when (message.data["type"]) {
            "PTT_START" -> handlePTTStart(message)
            "PTT_STOP" -> handlePTTStop(message) 
            "PTT_AUTO_JOIN" -> handleAutoJoin(message)
            "PTT_CHANNEL_INVITE" -> handleChannelInvite(message)
            "PTT_EMERGENCY" -> handleEmergencyCall(message)
            else -> {
                Log.w(TAG, "Unknown message type: ${message.data["type"]}")
            }
        }
    }
    
    /**
     * PTT 시작 신호 처리
     */
    private fun handlePTTStart(message: RemoteMessage) {
        val channel = message.data["channel"] ?: return
        val senderUid = message.data["uid"]?.toIntOrNull() ?: return
        val senderName = message.data["sender_name"] ?: "Unknown"
        
        Log.i(TAG, "PTT start signal from UID: $senderUid in channel: $channel")
        
        // 자신이 보낸 신호는 무시
        val currentUID = getCurrentUserUID()
        if (currentUID == senderUid) {
            Log.d(TAG, "Ignoring own PTT signal")
            return
        }
        
        // 사용자 타입 확인
        val senderType = UIDManager.getUserTypeFromUID(senderUid)
        Log.d(TAG, "Sender type: $senderType")
        
        // 자동 채널 참여 트리거
        triggerAutoJoin(channel, senderUid, "PTT_START from $senderName")
    }
    
    /**
     * PTT 중지 신호 처리
     */
    private fun handlePTTStop(message: RemoteMessage) {
        val channel = message.data["channel"] ?: return
        val senderUid = message.data["uid"]?.toIntOrNull() ?: return
        
        Log.i(TAG, "PTT stop signal from UID: $senderUid in channel: $channel")
        
        // 필요한 경우 UI 업데이트 등의 처리
        // 현재는 로깅만 수행
    }
    
    /**
     * 자동 채널 참여 처리
     */
    private fun handleAutoJoin(message: RemoteMessage) {
        val channel = message.data["channel"] ?: return
        val senderUid = message.data["uid"]?.toIntOrNull() ?: return
        val reason = message.data["reason"] ?: "Auto join request"
        
        Log.i(TAG, "Auto join request: $reason")
        
        triggerAutoJoin(channel, senderUid, reason)
    }
    
    /**
     * 채널 초대 처리
     */
    private fun handleChannelInvite(message: RemoteMessage) {
        val channel = message.data["channel"] ?: return
        val inviterUid = message.data["inviter_uid"]?.toIntOrNull() ?: return
        val inviterName = message.data["inviter_name"] ?: "Unknown"
        
        Log.i(TAG, "Channel invite from $inviterName (UID: $inviterUid) to channel: $channel")
        
        // 초대 알림 표시 및 자동 참여
        triggerAutoJoin(channel, inviterUid, "Invited by $inviterName")
    }
    
    /**
     * 긴급 호출 처리
     */
    private fun handleEmergencyCall(message: RemoteMessage) {
        val channel = message.data["channel"] ?: return
        val emergencyUid = message.data["uid"]?.toIntOrNull() ?: return
        val emergencyType = message.data["emergency_type"] ?: "UNKNOWN"
        val location = message.data["location"]
        
        Log.w(TAG, "EMERGENCY CALL: Type=$emergencyType, UID=$emergencyUid, Location=$location")
        
        // 긴급 상황 - 즉시 채널 참여
        triggerAutoJoin(channel, emergencyUid, "EMERGENCY: $emergencyType")
    }
    
    /**
     * 자동 채널 참여 트리거
     */
    private fun triggerAutoJoin(channel: String, senderUid: Int, reason: String) {
        try {
            Log.i(TAG, "Triggering auto join - Channel: $channel, Reason: $reason")
            
            val intent = Intent(this, PTTForegroundService::class.java).apply {
                action = PTTForegroundService.ACTION_AUTO_JOIN
                putExtra(PTTForegroundService.EXTRA_CHANNEL, channel)
                putExtra(PTTForegroundService.EXTRA_SENDER_UID, senderUid)
            }
            
            // Foreground Service 시작
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            Log.i(TAG, "Auto join triggered successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger auto join", e)
        }
    }
    
    /**
     * 현재 사용자의 UID 가져오기
     */
    private fun getCurrentUserUID(): Int? {
        return try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
            UIDManager.getExistingUID(this, "call_manager", userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current user UID", e)
            null
        }
    }
    
    /**
     * FCM 토큰 갱신 처리
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM token received: ${token.take(20)}...")
        
        // 서버에 토큰 업데이트 (향후 구현 예정)
        updateTokenOnServer(token)
    }
    
    /**
     * 서버에 FCM 토큰 업데이트
     */
    private fun updateTokenOnServer(token: String) {
        try {
            // Firebase Functions 호출하여 토큰 업데이트
            // 실제 구현에서는 Cloud Functions를 통해 사용자의 FCM 토큰을 업데이트해야 함
            Log.d(TAG, "Should update FCM token on server: ${token.take(20)}...")
            
            // TODO: Cloud Functions 호출 구현
            /*
            FirebaseFunctions.getInstance("asia-northeast3")
                .getHttpsCallable("updateFCMToken")
                .call(mapOf("token" to token))
                .addOnSuccessListener { result ->
                    Log.i(TAG, "FCM token updated on server")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update FCM token on server", e)
                }
            */
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update token on server", e)
        }
    }
    
    /**
     * FCM 메시지 유효성 검사
     */
    private fun validateMessage(message: RemoteMessage): Boolean {
        val requiredFields = when (message.data["type"]) {
            "PTT_START", "PTT_STOP" -> listOf("channel", "uid")
            "PTT_AUTO_JOIN" -> listOf("channel", "uid")
            "PTT_CHANNEL_INVITE" -> listOf("channel", "inviter_uid")
            "PTT_EMERGENCY" -> listOf("channel", "uid", "emergency_type")
            else -> emptyList()
        }
        
        val isValid = requiredFields.all { field ->
            message.data.containsKey(field) && !message.data[field].isNullOrEmpty()
        }
        
        if (!isValid) {
            Log.e(TAG, "Invalid FCM message format: missing required fields $requiredFields")
        }
        
        return isValid
    }
}