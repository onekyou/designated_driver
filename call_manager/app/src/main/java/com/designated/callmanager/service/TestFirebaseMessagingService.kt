package com.designated.callmanager.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TestFirebaseMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        Log.d("TEST_FCM", "🚨🚨🚨 TestFirebaseMessagingService onCreate 호출됨!!! 🚨🚨🚨")
        println("🚨🚨🚨 TestFirebaseMessagingService onCreate 호출됨!!! 🚨🚨🚨")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("TEST_FCM", "🚨🚨🚨 TestFirebaseMessagingService onNewToken 호출됨!!! 🚨🚨🚨")
        println("🚨🚨🚨 TestFirebaseMessagingService onNewToken 호출됨!!! 🚨🚨🚨")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("TEST_FCM", "🚨🚨🚨 TestFirebaseMessagingService onMessageReceived 호출됨!!! 🚨🚨🚨")
        println("🚨🚨🚨 TestFirebaseMessagingService onMessageReceived 호출됨!!! 🚨🚨🚨")
        println("메시지 from: ${remoteMessage.from}")
        println("메시지 data: ${remoteMessage.data}")
    }
}