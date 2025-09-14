package com.example.calldetector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Build
import androidx.core.content.edit

/**
 * 기기 부팅이 완료되면 자동으로 서비스를 시작하는 브로드캐스트 리시버
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            val sharedPrefs = context.getSharedPreferences("call_detector_prefs", MODE_PRIVATE)
            val stoppedByUser = sharedPrefs.getBoolean("service_stopped_by_user", false)

            if (stoppedByUser) {
                return
            }

            val deviceName = sharedPrefs.getString("device_name", "알 수 없음") ?: "알 수 없음"

            sharedPrefs.edit {
                putBoolean("service_running", true)
            }

            val serviceIntent = Intent(context, CallDetectorService::class.java)
            serviceIntent.putExtra("START_REASON", "BOOT_COMPLETED")
            serviceIntent.putExtra("device_name", deviceName)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: IllegalStateException) {
            }
        }
    }
}