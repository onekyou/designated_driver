package com.designated.callmanager.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.util.Log
import android.net.Uri
import android.content.ComponentName

/**
 * 접근성 서비스 설정을 위한 원클릭 가이드 헬퍼
 * 사용자가 최소한의 클릭으로 접근성 서비스를 활성화할 수 있도록 도움
 */
object AccessibilityGuideHelper {
    private const val TAG = "AccessibilityGuideHelper"
    
    /**
     * 원클릭 접근성 설정 가이드 표시
     * 사용자에게 간단한 설정 방법을 안내하고 직접 설정 화면으로 이동
     */
    fun showOneClickGuide(context: Context, onComplete: (() -> Unit)? = null) {
        try {
            val dialog = AlertDialog.Builder(context)
            .setTitle("📱 PTT 백그라운드 사용 설정")
            .setMessage("""
                화면이 꺼진 상태에서도 볼륨키 PTT를 사용하려면:
                
                🔸 다음 화면에서 "콜매니저 PTT 접근성 서비스" 찾기
                🔸 토글 버튼 ON으로 변경
                🔸 "사용" 버튼 클릭
                
                ⏱️ 약 10초면 완료됩니다!
            """.trimIndent())
            .setPositiveButton("📋 설정 화면 열기") { _, _ ->
                openAccessibilitySettingsDirectly(context)
                onComplete?.invoke()
            }
            .setNegativeButton("나중에") { _, _ ->
                Toast.makeText(context, "설정 > 접근성에서 언제든 설정할 수 있습니다", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("❓ 상세 가이드") { _, _ ->
                showDetailedGuide(context)
            }
            .create()
            
        dialog.show()
        } catch (e: Exception) {
            Log.w(TAG, "AlertDialog 생성 실패, Toast로 대체: ${e.message}")
            Toast.makeText(context, "PTT 접근성 서비스를 활성화하려면 설정 > 접근성으로 이동하세요", Toast.LENGTH_LONG).show()
            openAccessibilitySettingsDirectly(context)
            onComplete?.invoke()
        }
    }
    
    /**
     * 접근성 설정 화면으로 직접 이동
     */
    private fun openAccessibilitySettingsDirectly(context: Context) {
        try {
            // 방법 1: 특정 접근성 서비스 설정으로 직접 이동 시도
            val serviceIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                // Android 10+ 에서는 특정 서비스로 직접 이동 가능
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    putExtra(
                        Intent.EXTRA_COMPONENT_NAME,
                        ComponentName(context, "com.designated.callmanager.service.PTTAccessibilityService").flattenToString()
                    )
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(serviceIntent)
            Log.i(TAG, "접근성 설정 화면 열기 성공")
            
            // 3초 후 안내 토스트
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Toast.makeText(context, "👆 '콜매니저 PTT 접근성 서비스'를 찾아 활성화해주세요", Toast.LENGTH_LONG).show()
            }, 3000)
            
        } catch (e: Exception) {
            Log.e(TAG, "접근성 설정 화면 열기 실패", e)
            // 기본 설정 화면으로 이동
            fallbackToGeneralSettings(context)
        }
    }
    
    /**
     * 일반 설정 화면으로 폴백
     */
    private fun fallbackToGeneralSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "설정 > 접근성 > 콜매니저 PTT 접근성 서비스", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "일반 설정 화면 열기도 실패", e)
            Toast.makeText(context, "설정 앱을 수동으로 열어주세요", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 상세 설정 가이드 표시
     */
    private fun showDetailedGuide(context: Context) {
        try {
            val detailDialog = AlertDialog.Builder(context)
            .setTitle("🔧 상세 설정 가이드")
            .setMessage("""
                📋 단계별 설정 방법:
                
                1️⃣ 휴대폰 '설정' 앱 열기
                2️⃣ '접근성' 또는 '유용한 기능' 선택  
                3️⃣ '설치된 서비스' 또는 '다운로드한 앱' 선택
                4️⃣ '콜매니저 PTT 접근성 서비스' 찾기
                5️⃣ 토글 스위치를 ON으로 변경
                6️⃣ 팝업에서 '사용' 또는 '허용' 클릭
                
                ✅ 완료! 이제 화면이 꺼져도 볼륨키 PTT가 작동합니다.
                
                📞 문제 시 고객센터: 1588-0000
            """.trimIndent())
            .setPositiveButton("🔗 설정 화면 열기") { _, _ ->
                openAccessibilitySettingsDirectly(context)
            }
            .setNegativeButton("확인") { _, _ -> }
            .create()
            
        detailDialog.show()
        } catch (e: Exception) {
            Log.w(TAG, "상세 가이드 Dialog 생성 실패, Toast로 대체: ${e.message}")
            Toast.makeText(context, "설정 > 접근성 > 콜매니저 PTT 접근성 서비스를 활성화하세요", Toast.LENGTH_LONG).show()
            openAccessibilitySettingsDirectly(context)
        }
    }
    
    /**
     * 현재 접근성 서비스 상태 확인 및 가이드 표시
     */
    fun checkAndGuideIfNeeded(context: Context, force: Boolean = false) {
        // PTTDebugHelper로 상태 확인
        val isEnabled = com.designated.callmanager.utils.PTTDebugHelper.isAccessibilityServiceEnabled(context)
        
        if (!isEnabled || force) {
            Log.i(TAG, "접근성 서비스가 비활성화되어 있습니다. 가이드를 표시합니다.")
            showOneClickGuide(context)
        } else {
            Log.i(TAG, "접근성 서비스가 이미 활성화되어 있습니다.")
            Toast.makeText(context, "✅ PTT 접근성 서비스가 활성화되어 있습니다", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 간단한 상태 토스트 표시
     */
    fun showQuickStatus(context: Context) {
        val isEnabled = com.designated.callmanager.utils.PTTDebugHelper.isAccessibilityServiceEnabled(context)
        
        val message = if (isEnabled) {
            "✅ PTT 백그라운드 기능이 활성화되어 있습니다"
        } else {
            "⚠️ PTT 백그라운드 기능을 활성화하려면 설정이 필요합니다"
        }
        
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}