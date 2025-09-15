package com.example.calldetector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit

/**
 * 기기 부팅이 완료되면 자동으로 서비스를 시작하는 브로드캐스트 리시버
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootCompletedReceiver", "🚀 기기 부팅 완료 감지, 서비스 시작 여부 확인 중")
            
            // 사용자가 서비스를 종료했는지 확인
            val sharedPrefs = context.getSharedPreferences("call_detector_prefs", MODE_PRIVATE)
            val stoppedByUser = sharedPrefs.getBoolean("service_stopped_by_user", false)
            
            if (stoppedByUser) {
                Log.i("BootCompletedReceiver", "⛔ 사용자가 앱을 종료했습니다. 부팅 후 서비스가 자동으로 시작되지 않습니다.")
                return
            }
            
            // 종료되지 않은 상태라면 서비스 시작
            Log.i("BootCompletedReceiver", "✅ 부팅 후 서비스 자동 시작")
            
            // 디바이스 이름 가져오기
            val deviceName = sharedPrefs.getString("device_name", "알 수 없음") ?: "알 수 없음"
            
            // 서비스가 시작될 것임을 표시
            sharedPrefs.edit {
                putBoolean("service_running", true)
            }
            
            // 서비스 시작 인텐트 생성
            val serviceIntent = Intent(context, CallDetectorService::class.java)
            serviceIntent.putExtra("START_REASON", "BOOT_COMPLETED")
            serviceIntent.putExtra("device_name", deviceName)
            
            // API 레벨에 따라 적절한 서비스 시작 방법 선택
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                    Log.i("BootCompletedReceiver", "🚀 Android O 이상에서 startForegroundService 호출")
                } else {
                    context.startService(serviceIntent)
                    Log.i("BootCompletedReceiver", "🚀 Android O 미만에서 startService 호출")
                }
            } catch (e: IllegalStateException) {
                Log.e("BootCompletedReceiver", "❌ Failed to start CallDetectorService after boot", e)
            }
        }
    }
} 