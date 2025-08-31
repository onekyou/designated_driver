package com.designated.pickupapp

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

/**
 * 픽업앱 애플리케이션 클래스
 */
@HiltAndroidApp
class PickupApplication : Application() {
    
    companion object {
        private const val TAG = "PickupApplication"
        
        @Volatile
        private var INSTANCE: PickupApplication? = null
        
        fun getInstance(): PickupApplication {
            return INSTANCE ?: throw IllegalStateException("Application not initialized")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        
        Log.i(TAG, "픽업앱 애플리케이션 초기화 시작")
        
        try {
            // Firebase 초기화
            FirebaseApp.initializeApp(this)
            Log.i(TAG, "✅ Firebase 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Firebase 초기화 실패", e)
        }
        
        Log.i(TAG, "✅ 픽업앱 애플리케이션 초기화 완료")
    }
}