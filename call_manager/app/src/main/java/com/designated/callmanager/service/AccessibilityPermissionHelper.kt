package com.designated.callmanager.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.designated.callmanager.MainActivity

/**
 * 단계 2: Accessibility 권한 관리 헬퍼
 * - 권한 확인 및 설정 가이드 제공
 * - 단순한 구현으로 컴파일 오류 방지
 */
class AccessibilityPermissionHelper {

    companion object {
        private const val TAG = "AccessibilityPermissionHelper"
        private const val SERVICE_NAME = "com.designated.callmanager/.service.PTTAccessibilityService"

        /**
         * Accessibility 서비스 활성화 여부 확인
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            return try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                
                if (enabledServices.isNullOrEmpty()) {
                    Log.d(TAG, "활성화된 Accessibility 서비스 없음")
                    return false
                }
                
                val isEnabled = enabledServices.contains(SERVICE_NAME)
                Log.i(TAG, "PTT Accessibility 서비스 상태: $isEnabled")
                isEnabled
            } catch (e: Exception) {
                Log.e(TAG, "Accessibility 서비스 상태 확인 실패", e)
                false
            }
        }

        /**
         * Accessibility 설정 화면으로 이동
         */
        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.i(TAG, "Accessibility 설정 화면 열기")
            } catch (e: Exception) {
                Log.e(TAG, "Accessibility 설정 화면 열기 실패", e)
            }
        }

        /**
         * 권한 요청 가이드 메시지 생성
         */
        fun getPermissionGuideMessage(): String {
            return """
                PTT 백그라운드 기능을 위해 접근성 권한이 필요합니다.
                
                설정 방법:
                1. 설정 > 접근성 메뉴로 이동
                2. '콜 매니저' 찾기
                3. 서비스 활성화
                
                이 권한은 백그라운드에서 볼륨키를 PTT로 사용하기 위해서만 사용됩니다.
            """.trimIndent()
        }
    }
}