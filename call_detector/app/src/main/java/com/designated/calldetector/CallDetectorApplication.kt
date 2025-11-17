package com.designated.calldetector

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.sentry.android.core.SentryAndroid
import io.sentry.SentryLevel

/**
 * CallDetector Application 클래스
 * 콜매니저 방식 적용: 단순 Firebase 초기화만 수행
 * 익명 인증 제거, 정식 로그인 상태 유지
 */
class CallDetectorApplication : Application() {
    
    companion object {
        var crashReportService: CrashReportService? = null
            private set
        
        @Volatile
        private var INSTANCE: CallDetectorApplication? = null
        
        fun getInstance(): CallDetectorApplication {
            return INSTANCE ?: throw IllegalStateException("Application not initialized")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this

        // Sentry 초기화 (가장 먼저 초기화하여 모든 에러 캡처)
        initializeSentry()

        // Firebase 초기화 및 확인
        val app = FirebaseApp.initializeApp(this)
        android.util.Log.d("CallDetectorApp", "Firebase initialized: ${app?.name}")

        // Firebase 프로젝트 정보 확인
        android.util.Log.d("CallDetectorApp", "Project ID: ${app?.options?.projectId}")
        android.util.Log.d("CallDetectorApp", "App ID: ${app?.options?.applicationId}")

        // Crashlytics 설정
        setupCrashlytics()
        
        // CrashReportService 초기화
        crashReportService = CrashReportService(this)
        crashReportService?.initialize()
        
        // 메모리 부족 콜백 등록
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {}
            
            override fun onLowMemory() {
                crashReportService?.reportLowMemory()
            }
            
            override fun onTrimMemory(level: Int) {
                when (level) {
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                        crashReportService?.reportLowMemory()
                    }
                }
            }
        })
        
        android.util.Log.i("CallDetectorApp", "✅ 콜디텍터 애플리케이션 초기화 완료")
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

    private fun initializeSentry() {
        SentryAndroid.init(this) { options ->
            // DSN 설정
            options.dsn = "https://2b898c264c535b3fdbbff7201d1a2d27@o4510356858535936.ingest.us.sentry.io/4510356866203648"

            // 환경 설정 (debug/release)
            options.environment = if (BuildConfig.DEBUG) "debug" else "production"

            // 릴리스 버전 설정
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                options.release = "${packageInfo.versionName} (${packageInfo.versionCode})"
            } catch (e: Exception) {
                options.release = "1.0.0"
            }

            // 앱 타입 태그 추가
            options.setTag("app_type", "CALL_DETECTOR")

            // 디버그 로그 활성화 (개발 중에만)
            options.isDebug = BuildConfig.DEBUG

            // 샘플링 비율 설정
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2

            // ANR (Application Not Responding) 감지
            options.isEnableAutoSessionTracking = true
            options.isAnrEnabled = true

            // 첨부 파일 및 스크린샷
            options.isAttachScreenshot = true
            options.isAttachViewHierarchy = true

            android.util.Log.i("CallDetectorApp", "✅ Sentry 초기화 완료")
        }
    }
}