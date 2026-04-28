package com.designated.pickupdriver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PickupDriverApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return

            val callChannel = NotificationChannel(
                CHANNEL_CALL_CHANGES,
                "콜 상태 변경",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "사무실 콜의 신규/상태 변경 알림"
            }
            nm.createNotificationChannel(callChannel)

            // 사무실 단톡방 채널 (스펙 §10) — HIGH/ptt_start 효과음/DND 우회 X
            if (nm.getNotificationChannel(CHANNEL_CHAT_MESSAGES) == null) {
                val chatChannel = NotificationChannel(
                    CHANNEL_CHAT_MESSAGES,
                    "단톡방 메시지",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "사무실 단톡방 메시지 알림"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setBypassDnd(false)
                    setSound(
                        Uri.parse("android.resource://$packageName/${R.raw.ptt_start}"),
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                }
                nm.createNotificationChannel(chatChannel)
            }
        }
    }

    companion object {
        const val CHANNEL_CALL_CHANGES = "pickup_call_changes"
        const val CHANNEL_CHAT_MESSAGES = "chat_messages_ptt"
    }
}
