package com.designated.pickupapp.ptt.service

import android.content.Intent
import android.os.Build
import android.util.Log
import com.designated.pickupapp.ptt.core.UIDManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * 픽업 PTT FCM 시그널링 서비스
 * FCM을 통한 자동 채널 참여 및 PTT 신호 처리
 */
class PTTSignalingService : FirebaseMessagingService() {
    private val TAG = "PickupPTTSignaling"
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d(TAG, "Pickup FCM message received: ${message.data}")
        
        // FCM 데이터 처리
        when (message.data["type"]) {
            "PTT_START" -> handlePTTStart(message)
            "PTT_STOP" -> handlePTTStop(message) 
            "PTT_AUTO_JOIN" -> handleAutoJoin(message)
            "PTT_CHANNEL_INVITE" -> handleChannelInvite(message)
            "PTT_EMERGENCY" -> handleEmergencyCall(message)
            "PTT_DISPATCH" -> handleDispatchCall(message)
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
        val senderDescription = when {
            UIDManager.isCallManagerUID(senderUid) -> "관리자"
            UIDManager.isPickupAppUID(senderUid) -> "픽업기사"
            else -> "Unknown"
        }
        
        Log.d(TAG, "Sender: $senderDescription ($senderType)")
        
        // 관리자 신호인 경우 우선 처리
        if (UIDManager.isCallManagerUID(senderUid)) {
            Log.i(TAG, "Priority signal from call manager - auto joining")
            triggerAutoJoin(channel, senderUid, "관리자 호출: $senderName")
        } else {
            triggerAutoJoin(channel, senderUid, "PTT 시작: $senderName")
        }
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
        triggerAutoJoin(channel, inviterUid, "초대: $inviterName")
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
        triggerAutoJoin(channel, emergencyUid, "긴급: $emergencyType")
    }
    
    /**
     * 배차 호출 처리 (픽업 전용)
     */
    private fun handleDispatchCall(message: RemoteMessage) {
        val channel = message.data["channel"] ?: return
        val dispatcherUid = message.data["dispatcher_uid"]?.toIntOrNull() ?: return
        val pickupLocation = message.data["pickup_location"] ?: "Unknown"
        val customerInfo = message.data["customer_info"] ?: ""
        
        Log.i(TAG, "DISPATCH CALL: Location=$pickupLocation, Customer=$customerInfo")
        
        // 배차 호출 - 픽업 기사 전용 처리
        triggerAutoJoin(channel, dispatcherUid, "배차: $pickupLocation")
    }
    
    /**
     * 자동 채널 참여 트리거
     */
    private fun triggerAutoJoin(channel: String, senderUid: Int, reason: String) {
        try {
            Log.i(TAG, "Triggering pickup auto join - Channel: $channel, Reason: $reason")
            
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
            
            Log.i(TAG, "Pickup auto join triggered successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger pickup auto join", e)
        }
    }
    
    /**
     * 현재 사용자의 UID 가져오기 (픽업 기사)
     */
    private fun getCurrentUserUID(): Int? {
        return try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
            UIDManager.getExistingUID(this, "pickup_driver", userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current pickup driver UID", e)
            null
        }
    }
    
    /**
     * FCM 토큰 갱신 처리
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New pickup FCM token received: ${token.take(20)}...")
        
        // 서버에 토큰 업데이트 (향후 구현 예정)
        updateTokenOnServer(token)
    }
    
    /**
     * 서버에 FCM 토큰 업데이트
     */
    private fun updateTokenOnServer(token: String) {
        try {
            // Firebase Functions 호출하여 토큰 업데이트
            // 실제 구현에서는 Cloud Functions를 통해 픽업 기사의 FCM 토큰을 업데이트해야 함
            Log.d(TAG, "Should update pickup FCM token on server: ${token.take(20)}...")
            
            // TODO: Cloud Functions 호출 구현
            /*
            FirebaseFunctions.getInstance("asia-northeast3")
                .getHttpsCallable("updatePickupFCMToken")
                .call(mapOf(
                    "token" to token,
                    "userType" to "pickup_driver"
                ))
                .addOnSuccessListener { result ->
                    Log.i(TAG, "Pickup FCM token updated on server")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update pickup FCM token on server", e)
                }
            */
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update pickup token on server", e)
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
            "PTT_DISPATCH" -> listOf("channel", "dispatcher_uid", "pickup_location")
            else -> emptyList()
        }
        
        val isValid = requiredFields.all { field ->
            message.data.containsKey(field) && !message.data[field].isNullOrEmpty()
        }
        
        if (!isValid) {
            Log.e(TAG, "Invalid pickup FCM message format: missing required fields $requiredFields")
        }
        
        return isValid
    }
}