package com.designated.pickupapp.ptt.manager

import android.content.Context
import android.util.Log
import com.designated.pickupapp.data.PTTStatus
import io.agora.rtm.*

/**
 * Agora Signaling SDK (RTM 2.x) Manager
 * 공식 문서 기반 정확한 구현 (픽업앱용)
 */
class SignalingManager(
    private val context: Context,
    private val appId: String,
    private val userId: String,
    private val onPTTStatusChanged: ((PTTStatus) -> Unit)? = null
) {
    
    private val TAG = "PickupSignalingManager"
    private var rtmClient: RtmClient? = null
    
    // RtmEventListener 구현 - 공식 문서 기반
    private val eventListener = object : RtmEventListener {
        override fun onMessageEvent(event: MessageEvent) {
            try {
                // 공식 API: getMessage() 사용
                val messageStr = event.message?.data?.toString() ?: ""
                val publisherId = event.publisherId?.toString() ?: "unknown"
                val channelName = event.channelName ?: "unknown"
                val channelType = event.channelType
                
                Log.d(TAG, "Message from $publisherId in $channelName: $messageStr")
                Log.d(TAG, "Channel type: $channelType")
                
                // PTT 메시지 처리 로직
                handlePttMessage(publisherId, messageStr)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message: $e")
            }
        }
        
        override fun onPresenceEvent(event: PresenceEvent) {
            try {
                val publisherId = event.publisherId
                val channelName = event.channelName
                val eventType = event.eventType
                
                Log.d(TAG, "Presence: $publisherId in $channelName - Type: $eventType")
                
                // 사용자 상태 변경 처리
                when (eventType) {
                    RtmConstants.RtmPresenceEventType.REMOTE_JOIN -> {
                        Log.d(TAG, "$publisherId joined channel")
                    }
                    RtmConstants.RtmPresenceEventType.REMOTE_LEAVE -> {
                        Log.d(TAG, "$publisherId left channel")
                    }
                    RtmConstants.RtmPresenceEventType.REMOTE_TIMEOUT -> {
                        Log.d(TAG, "$publisherId timeout")
                    }
                    else -> {
                        Log.d(TAG, "Other presence event: $eventType")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing presence: $e")
            }
        }
        
        override fun onTopicEvent(event: TopicEvent) {
            Log.d(TAG, "Topic event received")
        }
        
        override fun onStorageEvent(event: StorageEvent) {
            Log.d(TAG, "Storage event received")
        }
        
        override fun onLockEvent(event: LockEvent) {
            Log.d(TAG, "Lock event received")
        }
        
        override fun onLinkStateEvent(event: LinkStateEvent) {
            try {
                val currentState = event.currentState
                val previousState = event.previousState
                val reason = event.reason
                val affectedChannels = event.affectedChannels
                
                Log.d(TAG, "Link state changed: $previousState -> $currentState")
                Log.d(TAG, "Reason: $reason")
                
                when (currentState) {
                    RtmConstants.RtmLinkState.CONNECTED -> {
                        Log.d(TAG, "RTM Connected successfully")
                    }
                    RtmConstants.RtmLinkState.CONNECTING -> {
                        Log.d(TAG, "RTM Connecting...")
                    }
                    RtmConstants.RtmLinkState.DISCONNECTED -> {
                        Log.w(TAG, "RTM Disconnected")
                    }
                    RtmConstants.RtmLinkState.SUSPENDED -> {
                        Log.w(TAG, "RTM Suspended")
                    }
                    RtmConstants.RtmLinkState.FAILED -> {
                        Log.e(TAG, "RTM Connection failed")
                    }
                    else -> {
                        Log.d(TAG, "Unknown link state: $currentState")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing link state: $e")
            }
        }
        
        override fun onTokenPrivilegeWillExpire(channelName: String?) {
            Log.w(TAG, "Token will expire for channel: $channelName")
            // TODO: Token 갱신 로직 구현
        }
    }
    
    /**
     * RTM 클라이언트 초기화
     */
    fun initialize(callback: ((Boolean) -> Unit)? = null) {
        try {
            Log.d(TAG, "Initializing Pickup Signaling SDK...")
            
            val config = RtmConfig.Builder(appId, userId)
                .eventListener(eventListener)
                .build()
                
            // RtmClient.create()는 동기 메서드 - ResultCallback 없음
            rtmClient = RtmClient.create(config)
            Log.d(TAG, "Pickup RTM Client created successfully")
            callback?.invoke(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize: $e")
            callback?.invoke(false)
        }
    }
    
    /**
     * RTM 로그인
     */
    fun login(token: String? = null) {
        rtmClient?.let { client ->
            client.login(token, object : ResultCallback<Void?> {
                override fun onSuccess(responseInfo: Void?) {
                    Log.d(TAG, "Pickup login successful")
                }
                
                override fun onFailure(errorInfo: ErrorInfo?) {
                    Log.e(TAG, "Pickup login failed: ${errorInfo?.errorReason}")
                }
            })
        } ?: Log.e(TAG, "Pickup RTM Client is null")
    }
    
    /**
     * 채널 구독
     */
    fun subscribeChannel(channelName: String) {
        rtmClient?.let { client ->
            val options = SubscribeOptions()
            options.withMessage = true
            options.withPresence = true
            options.withMetadata = false
            options.withLock = false
            
            client.subscribe(channelName, options, object : ResultCallback<Void?> {
                override fun onSuccess(responseInfo: Void?) {
                    Log.d(TAG, "Pickup subscribed to channel: $channelName")
                }
                
                override fun onFailure(errorInfo: ErrorInfo?) {
                    Log.e(TAG, "Pickup subscribe failed: ${errorInfo?.errorReason}")
                }
            })
        } ?: Log.e(TAG, "Pickup RTM Client is null")
    }
    
    /**
     * 메시지 발행
     */
    fun publishMessage(channelName: String, message: String) {
        rtmClient?.let { client ->
            val options = PublishOptions()
            
            client.publish(channelName, message, options, object : ResultCallback<Void?> {
                override fun onSuccess(responseInfo: Void?) {
                    Log.d(TAG, "Pickup message published: $message")
                }
                
                override fun onFailure(errorInfo: ErrorInfo?) {
                    Log.e(TAG, "Pickup publish failed: ${errorInfo?.errorReason}")
                }
            })
        } ?: Log.e(TAG, "Pickup RTM Client is null")
    }
    
    /**
     * PTT 시작 신호 전송
     */
    fun sendPttStart(channelName: String) {
        val message = """{"type":"PTT_START","userId":"$userId","timestamp":${System.currentTimeMillis()},"source":"pickup"}"""
        publishMessage(channelName, message)
    }
    
    /**
     * PTT 종료 신호 전송
     */
    fun sendPttEnd(channelName: String) {
        val message = """{"type":"PTT_END","userId":"$userId","timestamp":${System.currentTimeMillis()},"source":"pickup"}"""
        publishMessage(channelName, message)
    }
    
    /**
     * 채널 구독 해제
     */
    fun unsubscribeChannel(channelName: String) {
        rtmClient?.let { client ->
            client.unsubscribe(channelName, object : ResultCallback<Void?> {
                override fun onSuccess(responseInfo: Void?) {
                    Log.d(TAG, "Pickup unsubscribed from channel: $channelName")
                }
                
                override fun onFailure(errorInfo: ErrorInfo?) {
                    Log.e(TAG, "Pickup unsubscribe failed: ${errorInfo?.errorReason}")
                }
            })
        } ?: Log.e(TAG, "Pickup RTM Client is null")
    }
    
    /**
     * 로그아웃
     */
    fun logout() {
        rtmClient?.let { client ->
            client.logout(object : ResultCallback<Void?> {
                override fun onSuccess(responseInfo: Void?) {
                    Log.d(TAG, "Pickup logout successful")
                }
                
                override fun onFailure(errorInfo: ErrorInfo?) {
                    Log.e(TAG, "Pickup logout failed: ${errorInfo?.errorReason}")
                }
            })
        } ?: Log.e(TAG, "Pickup RTM Client is null")
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        try {
            // RTM 클라이언트 정리
            rtmClient = null
            Log.d(TAG, "Pickup RTM Client released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing pickup RTM Client: $e")
        }
    }
    
    /**
     * PTT 메시지 처리
     */
    private fun handlePttMessage(publisherId: String, message: String) {
        try {
            // JSON 파싱하여 PTT 이벤트 처리
            if (message.contains("PTT_START")) {
                val source = if (message.contains("\"source\":\"pickup\"")) "pickup" else "call_manager"
                val sourceName = if (source == "pickup") "픽업" else "대리"
                val timestamp = extractTimestamp(message)
                
                Log.d(TAG, "PTT Started by $publisherId ($sourceName)")
                
                // UI로 PTT 상태 전달
                val pttStatus = PTTStatus(
                    userId = publisherId,
                    userName = "$sourceName 사용자", // 실제 구현 시 사용자명 매핑 필요
                    isTransmitting = true,
                    timestamp = timestamp,
                    source = source
                )
                onPTTStatusChanged?.invoke(pttStatus)
                
            } else if (message.contains("PTT_END")) {
                val source = if (message.contains("\"source\":\"pickup\"")) "pickup" else "call_manager"
                val sourceName = if (source == "pickup") "픽업" else "대리"
                val timestamp = extractTimestamp(message)
                
                Log.d(TAG, "PTT Ended by $publisherId ($sourceName)")
                
                // UI로 PTT 상태 전달
                val pttStatus = PTTStatus(
                    userId = publisherId,
                    userName = "$sourceName 사용자",
                    isTransmitting = false,
                    timestamp = timestamp,
                    source = source
                )
                onPTTStatusChanged?.invoke(pttStatus)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling PTT message: $e")
        }
    }
    
    /**
     * 메시지에서 타임스탬프 추출
     */
    private fun extractTimestamp(message: String): Long {
        return try {
            val timestampRegex = "\"timestamp\":(\\d+)".toRegex()
            val match = timestampRegex.find(message)
            match?.groups?.get(1)?.value?.toLong() ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}