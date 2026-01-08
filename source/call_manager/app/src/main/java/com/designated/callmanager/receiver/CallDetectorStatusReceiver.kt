package com.designated.callmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            val prefs = ctx.getSharedPreferences("call_detector_status", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("is_running", isRunning)
                putLong("last_status_time", timestamp)
                putString("device_name", deviceName)
                apply()
            }

            val localIntent = Intent("LOCAL_DETECTOR_STATUS_UPDATE").apply {
                putExtra("isRunning", isRunning)
                putExtra("deviceName", deviceName)
                putExtra("timestamp", timestamp)
            }
            ctx.sendBroadcast(localIntent)

            if (!isRunning) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        ctx,
                        "⚠️ 콜 디텍터 [$deviceName] 종료됨",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // TODO: 필요시 알림(Notification) 생성
            }
        }
    }

    private fun showNotification(context: Context, deviceName: String) {
    }
}