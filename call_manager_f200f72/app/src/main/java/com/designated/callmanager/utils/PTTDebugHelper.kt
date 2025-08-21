package com.designated.callmanager.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.designated.callmanager.service.MediaSessionPTTService
import com.designated.callmanager.service.PTTAccessibilityService

/**
 * PTT 디버그 헬퍼
 * 접근성 서비스와 MediaSession 서비스 상태를 점검하는 유틸리티
 */
object PTTDebugHelper {
    private const val TAG = "PTTDebugHelper"
    
    /**
     * 전체 PTT 시스템 상태 점검
     */
    fun checkPTTSystemStatus(context: Context): PTTSystemStatus {
        val accessibilityEnabled = isAccessibilityServiceEnabled(context)
        val mediaSessionRunning = MediaSessionPTTService.isRunning()
        val accessibilityRunning = PTTAccessibilityService.isRunning()
        
        Log.i(TAG, "========== PTT 시스템 상태 점검 ==========")
        Log.i(TAG, "1. 접근성 서비스 설정 활성화: $accessibilityEnabled")
        Log.i(TAG, "2. PTTAccessibilityService 실행 중: $accessibilityRunning")
        Log.i(TAG, "3. MediaSessionPTTService 실행 중: $mediaSessionRunning")
        Log.i(TAG, "==========================================")
        
        return PTTSystemStatus(
            isAccessibilityEnabled = accessibilityEnabled,
            isAccessibilityServiceRunning = accessibilityRunning,
            isMediaSessionServiceRunning = mediaSessionRunning,
            androidVersion = android.os.Build.VERSION.SDK_INT,
            androidVersionName = android.os.Build.VERSION.RELEASE
        )
    }
    
    /**
     * 접근성 서비스 활성화 여부 확인
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        val packageName = context.packageName
        val serviceName = PTTAccessibilityService::class.java.canonicalName
        
        for (service in enabledServices) {
            val serviceId = service.id
            Log.d(TAG, "활성화된 접근성 서비스: $serviceId")
            
            if (serviceId.contains(packageName) && serviceId.contains("PTTAccessibilityService")) {
                Log.i(TAG, "✅ PTTAccessibilityService가 활성화되어 있습니다!")
                return true
            }
        }
        
        Log.w(TAG, "⚠️ PTTAccessibilityService가 활성화되어 있지 않습니다!")
        return false
    }
    
    /**
     * 접근성 서비스 설정 화면 열기
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "접근성 설정 화면 열기 성공")
        } catch (e: Exception) {
            Log.e(TAG, "접근성 설정 화면 열기 실패", e)
        }
    }
    
    /**
     * PTT 테스트 브로드캐스트 전송
     */
    fun sendTestPTTBroadcast(context: Context, action: String) {
        try {
            val intent = Intent("com.designated.callmanager.PTT_ACTION").apply {
                putExtra("action", action)
                putExtra("source", "debug_test")
                putExtra("timestamp", System.currentTimeMillis())
                putExtra("screen_off", false)
                setPackage(context.packageName)
            }
            
            context.sendBroadcast(intent)
            Log.i(TAG, "🚀 테스트 PTT 브로드캐스트 전송: $action")
        } catch (e: Exception) {
            Log.e(TAG, "테스트 브로드캐스트 전송 실패", e)
        }
    }
    
    /**
     * 상세 로그 출력
     */
    fun printDetailedStatus(context: Context) {
        val status = checkPTTSystemStatus(context)
        
        Log.i(TAG, "┌────────────────────────────────────────")
        Log.i(TAG, "│ PTT 시스템 상세 상태")
        Log.i(TAG, "├────────────────────────────────────────")
        Log.i(TAG, "│ Android 버전: ${status.androidVersion} (${status.androidVersionName})")
        Log.i(TAG, "│")
        Log.i(TAG, "│ 접근성 서비스:")
        Log.i(TAG, "│  - 설정 활성화: ${if (status.isAccessibilityEnabled) "✅" else "❌"}")
        Log.i(TAG, "│  - 서비스 실행: ${if (status.isAccessibilityServiceRunning) "✅" else "❌"}")
        Log.i(TAG, "│")
        Log.i(TAG, "│ MediaSession 서비스:")
        Log.i(TAG, "│  - 실행 중: ${if (status.isMediaSessionServiceRunning) "✅" else "❌"}")
        Log.i(TAG, "│")
        Log.i(TAG, "│ 전체 상태: ${status.getOverallStatus()}")
        Log.i(TAG, "└────────────────────────────────────────")
        
        if (!status.isFullyOperational()) {
            Log.w(TAG, "")
            Log.w(TAG, "⚠️ 문제 해결 방법:")
            
            if (!status.isAccessibilityEnabled) {
                Log.w(TAG, "1. 설정 > 접근성 > PTT 접근성 서비스 활성화")
            }
            
            if (!status.isMediaSessionServiceRunning) {
                Log.w(TAG, "2. 앱을 재시작하여 MediaSession 서비스 시작")
            }
            
            if (status.androidVersion >= 35) {
                Log.w(TAG, "3. Android 15 이상: 앱 권한 설정에서 '제한 없음' 선택")
            }
        }
    }
    
    /**
     * PTT 시스템 상태 데이터 클래스
     */
    data class PTTSystemStatus(
        val isAccessibilityEnabled: Boolean,
        val isAccessibilityServiceRunning: Boolean,
        val isMediaSessionServiceRunning: Boolean,
        val androidVersion: Int,
        val androidVersionName: String
    ) {
        fun isFullyOperational(): Boolean {
            return isAccessibilityEnabled && 
                   isAccessibilityServiceRunning && 
                   isMediaSessionServiceRunning
        }
        
        fun getOverallStatus(): String {
            return when {
                isFullyOperational() -> "✅ 정상 작동"
                isAccessibilityEnabled && !isAccessibilityServiceRunning -> "⚠️ 접근성 서비스 재시작 필요"
                !isAccessibilityEnabled -> "❌ 접근성 서비스 설정 필요"
                !isMediaSessionServiceRunning -> "❌ MediaSession 서비스 시작 필요"
                else -> "⚠️ 부분적 작동"
            }
        }
    }
}