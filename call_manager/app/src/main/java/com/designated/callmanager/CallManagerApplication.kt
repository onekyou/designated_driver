package com.designated.callmanager

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * CallManager 애플리케이션 클래스
 */
class CallManagerApplication : Application() {

    companion object {
        private const val TAG = "CallManagerApplication"

        @Volatile
        private var INSTANCE: CallManagerApplication? = null

        fun getInstance(): CallManagerApplication {
            return INSTANCE ?: throw IllegalStateException("Application not initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this

        Log.d(TAG, "========== CallManagerApplication onCreate 시작 ==========")
        Log.d(TAG, "🔍 [DEBUG] 애플리케이션 초기화 시각: ${System.currentTimeMillis()}")

        // Firebase 초기화 상태 확인
        try {
            val firebaseApps = FirebaseApp.getApps(this)
            Log.d(TAG, "🔍 [DEBUG] Firebase Apps 개수: ${firebaseApps.size}")
            firebaseApps.forEachIndexed { index, app ->
                Log.d(TAG, "🔍 [DEBUG] Firebase App [$index]: ${app.name}")
            }

            // Firebase Messaging 토큰 상태 확인
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "✅ [DEBUG] FCM 토큰 조회 성공: ${token?.take(20)}...")
                } else {
                    Log.e(TAG, "❌ [DEBUG] FCM 토큰 조회 실패: ${task.exception?.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ [DEBUG] Firebase 상태 확인 중 오류: ${e.message}")
            e.printStackTrace()
        }

        Log.d(TAG, "========== CallManagerApplication onCreate 완료 ==========")
    }
}