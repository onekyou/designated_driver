package com.designated.pickupdriver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.designated.pickupdriver.service.PTTManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PickupDriverApplication : Application() {

    /**
     * PTT 송수신 매니저 — 프로세스당 단일 Agora 엔진 보장.
     *  MainActivity(송신·배너)와 PttReceiverService(수신 join·콜드 재생)가 이 인스턴스를 공유한다.
     *  ★ by lazy 필수 — PTTManager 생성자가 Firebase.functions 접근. 즉시 생성하면 Application
     *    인스턴스화(onCreate 이전, FirebaseApp 미초기화) 시점에 호출돼 크래시. 첫 접근(onCreate 이후)으로 지연.
     */
    val pttManager by lazy { PTTManager() }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return

            // 구 채널 정리 — ptt음 채널은 기본 알림음 채널(_default_v1)로 교체 (2026-06-12 알림정리, ptt음은 PTT 전용)
            listOf(CHANNEL_CALL_CHANGES_LEGACY, "pickup_call_changes_ptt", "chat_messages_ptt").forEach { oldId ->
                try { nm.deleteNotificationChannel(oldId) } catch (_: Exception) {}
            }

            // 콜 상태 변경 채널 — HIGH/기본 알림음 (2026-06-12 알림정리)
            if (nm.getNotificationChannel(CHANNEL_CALL_CHANGES) == null) {
                val callChannel = NotificationChannel(
                    CHANNEL_CALL_CHANGES,
                    "콜 상태 변경",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "사무실 콜의 신규/상태 변경 알림"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                }
                nm.createNotificationChannel(callChannel)
            }

            // 사무실 단톡방 채널 (스펙 §10) — HIGH/기본 알림음/DND 우회 X (2026-06-12 알림정리)
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
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
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
        const val CHANNEL_CALL_CHANGES = "pickup_call_changes_default_v1"  // 기본 알림음 (2026-06-12 알림정리)
        private const val CHANNEL_CALL_CHANGES_LEGACY = "pickup_call_changes"
        const val CHANNEL_CHAT_MESSAGES = "chat_messages_default_v1"  // 기본 알림음 (2026-06-12 알림정리)

        @Volatile
        private var INSTANCE: PickupDriverApplication? = null
        fun getInstance(): PickupDriverApplication =
            INSTANCE ?: throw IllegalStateException("Application not initialized")
    }
}
