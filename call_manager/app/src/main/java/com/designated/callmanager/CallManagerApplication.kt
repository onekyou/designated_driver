package com.designated.callmanager

import android.app.Application
import android.util.Log

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
        
        Log.i(TAG, "콜매니저 애플리케이션 초기화 시작")
        
        Log.i(TAG, "✅ 콜매니저 애플리케이션 초기화 완료")
    }
}