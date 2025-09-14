package com.example.calldetector

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * CallDetector Application 클래스
 * Crashlytics 초기화 및 전역 크래시 핸들링
 */
class CallDetectorApplication : Application() {
    
    companion object {
        var crashReportService: CrashReportService? = null
            private set
        
        /**
         * 로그아웃 상태 설정
         */
        fun setLogoutState(context: Context, isLoggedOut: Boolean) {
            val prefs = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_logged_out", isLoggedOut).apply()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Firebase 초기화 및 확인
        FirebaseApp.initializeApp(this)
        
        // Firebase 프로젝트 정보 확인
        
        // Crashlytics 설정
        setupCrashlytics()
        
        // 🔥 핵심 해결책: Firebase 익명 인증 설정
        setupFirebaseAuth()
        
        // CrashReportService 즉시 초기화
        crashReportService = CrashReportService(this)
        crashReportService?.initialize()
        
        // 메모리 부족 콜백 등록
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {}
            
            override fun onLowMemory() {
                // null 체크로 안전하게 호출
                crashReportService?.reportLowMemory()
            }
            
            override fun onTrimMemory(level: Int) {
                when (level) {
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                        // null 체크로 안전하게 호출
                        crashReportService?.reportLowMemory()
                    }
                }
            }
        })
    }
    
    private fun setupCrashlytics() {
        val crashlytics = FirebaseCrashlytics.getInstance()
        
        // Crashlytics 활성화
        crashlytics.setCrashlyticsCollectionEnabled(true)
        
        // 개발/프로덕션 환경 구분
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            crashlytics.setCustomKey("app_version", packageInfo.versionName ?: "1.0")
            crashlytics.setCustomKey("version_code", packageInfo.versionCode.toString())
        } catch (e: Exception) {
            crashlytics.setCustomKey("app_version", "1.0")
        }
        crashlytics.setCustomKey("app_type", "CALL_DETECTOR")
    }
    
    /**
     * Firebase 익명 인증 설정
     * Firestore 쓰기 권한을 위해 필요
     */
    private fun setupFirebaseAuth() {
        val auth = FirebaseAuth.getInstance()
        
        // 로그아웃 상태 확인
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val isLoggedOut = prefs.getBoolean("is_logged_out", false)
        
        
        // 사용자가 명시적으로 로그아웃한 경우 자동 재로그인 하지 않음
        if (auth.currentUser == null && !isLoggedOut) {
            auth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        
                        // Crashlytics에 사용자 ID 설정
                        FirebaseCrashlytics.getInstance().setUserId(user?.uid ?: "anonymous")
                    }
                }
        }
    }
    
}