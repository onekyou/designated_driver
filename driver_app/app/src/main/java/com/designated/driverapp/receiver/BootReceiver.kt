package com.designated.driverapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.designated.driverapp.service.DriverForegroundService
import com.google.firebase.auth.FirebaseAuth

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.d(TAG, "로그아웃 상태 — 서비스 시작 안 함")
            return
        }

        Log.d(TAG, "로그인 상태 (${currentUser.uid}) — DriverForegroundService 시작")

        val serviceIntent = Intent(context, DriverForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
