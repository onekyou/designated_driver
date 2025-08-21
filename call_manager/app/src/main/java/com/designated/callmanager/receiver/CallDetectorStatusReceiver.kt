package com.designated.callmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CallDetectorStatusReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "CallDetectorStatus"
        const val ACTION_DETECTOR_STATUS = "com.designated.CALL_DETECTOR_STATUS"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_DETECTOR_STATUS) return
        
        val isRunning = intent.getBooleanExtra("isRunning", false)
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
        val deviceName = intent.getStringExtra("deviceName") ?: "Unknown"
        
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        
        context?.let { ctx ->
            // SharedPreferences에 상태 저장
            val prefs = ctx.getSharedPreferences("call_detector_status", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("is_running", isRunning)
                putLong("last_status_time", timestamp)
                putString("device_name", deviceName)
                apply()
            }
            
            // 로그 기록
            Log.d(TAG, "Call Detector Status - Device: $deviceName, Running: $isRunning, Time: $timeStr")
            
            // UI 업데이트를 위한 로컬 브로드캐스트 전송
            val localIntent = Intent("LOCAL_DETECTOR_STATUS_UPDATE").apply {
                putExtra("isRunning", isRunning)
                putExtra("deviceName", deviceName)
                putExtra("timestamp", timestamp)
            }
            ctx.sendBroadcast(localIntent)
            
            // 서비스가 종료된 경우 알림 표시
            if (!isRunning) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        ctx,
                        "⚠️ 콜 디텍터 [$deviceName] 종료됨",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                // TODO: 필요시 알림(Notification) 생성
                // showNotification(ctx, deviceName)
            }
        }
    }
    
    private fun showNotification(context: Context, deviceName: String) {
        // 필요시 NotificationManager를 통해 상태바 알림 생성
        // 여기서는 Toast로 대체
    }
}