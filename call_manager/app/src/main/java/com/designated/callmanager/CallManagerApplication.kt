package com.designated.callmanager

import android.app.Application
import android.util.Log
import com.designated.callmanager.ptt.manager.PTTServiceManager

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
    
    // PTT 서비스 매니저
    lateinit var pttServiceManager: PTTServiceManager
        private set
    
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        
        Log.i(TAG, "콜매니저 애플리케이션 초기화 시작")
        
        // PTT 서비스 매니저 초기화
        initializePTTServiceManager()
        
        Log.i(TAG, "✅ 콜매니저 애플리케이션 초기화 완료")
    }
    
    /**
     * PTT 서비스 매니저 초기화
     */
    private fun initializePTTServiceManager() {
        try {
            Log.d(TAG, "Initializing PTT Service Manager...")
            pttServiceManager = PTTServiceManager.getInstance(this)
            
            // 주기적 상태 체크 시작
            pttServiceManager.startPeriodicStatusCheck()
            
            Log.i(TAG, "PTT Service Manager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PTT Service Manager", e)
        }
    }
}