package com.designated.pickupapp.service

import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // FCM 메시지 처리
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }

        // 데이터 페이로드 처리
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            
            // PTT 관련 메시지 처리
            val messageType = remoteMessage.data["type"]
            if (messageType == "PTT_AUTO_JOIN") {
                Log.d(TAG, "PTT auto join message received")
                
                val channel = remoteMessage.data["channel"] ?: return
                val senderUID = remoteMessage.data["uid"]?.toIntOrNull() ?: return
                
                // 마스터플랜 패턴: PTTForegroundService에 Intent로 전달
                val intent = Intent(this, com.designated.pickupapp.ptt.service.PTTForegroundService::class.java).apply {
                    action = "com.designated.pickupapp.action.AUTO_JOIN" 
                    putExtra("extra_channel", channel)
                    putExtra("extra_sender_uid", senderUID)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        // TODO: 필요시 서버에 토큰 업데이트
    }

    companion object {
        private const val TAG = "PickupFCMService"
    }
}