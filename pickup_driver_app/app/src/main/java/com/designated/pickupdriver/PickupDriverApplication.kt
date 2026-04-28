package com.designated.pickupdriver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PickupDriverApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_CALL_CHANGES,
                "콜 상태 변경",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "사무실 콜의 신규/상태 변경 알림"
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_CALL_CHANGES = "pickup_call_changes"
    }
}
