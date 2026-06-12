package com.designated.driverapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.designated.driverapp.service.PresenceManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DriverApplication : Application() {

    private val TAG = "DriverApplication"

    companion object {
        const val CHANNEL_CHAT_MESSAGES = "chat_messages_v2"

        /**
         * 앱이 포그라운드 상태인지 여부
         * ProcessLifecycleOwner에 의해 정확하게 관리됨
         */
        @Volatile
        var isInForeground: Boolean = false
            private set

        internal fun setForegroundState(inForeground: Boolean) {
            isInForeground = inForeground
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 사무실 단톡방 알림 채널 (call_manager와 동일 ID — 패키지 단위 분리)
        registerChatNotificationChannel()

        // 앱 라이프사이클 관찰자 등록 (포그라운드/백그라운드 감지)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())

        // 로그인 상태일 때만 Presence 초기화
        if (FirebaseAuth.getInstance().currentUser != null) {
            PresenceManager.initialize(this)
        }

        // Auth 상태 변경 리스너
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                Log.d(TAG, "사용자 로그인됨 - Presence 초기화")
                PresenceManager.reinitialize(this)
            } else {
                Log.d(TAG, "사용자 로그아웃됨 - Presence 정리")
                PresenceManager.onLogout()
            }
        }
    }

    private fun registerChatNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        // 옛 ptt음 단톡방 채널(죽은 채널) 정리 — 현재는 chat_messages_v2(기본음) 사용 (2026-06-12 알림정리)
        try { nm.deleteNotificationChannel("chat_messages_ptt") } catch (_: Exception) {}

        if (nm.getNotificationChannel(CHANNEL_CHAT_MESSAGES) != null) return

        val channel = NotificationChannel(
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
        nm.createNotificationChannel(channel)
        Log.d(TAG, "[$CHANNEL_CHAT_MESSAGES] 알림 채널 등록 완료")
    }

    /**
     * 앱 라이프사이클 관찰자
     * 포그라운드/백그라운드 전환 감지
     */
    inner class AppLifecycleObserver : DefaultLifecycleObserver {

        override fun onStart(owner: LifecycleOwner) {
            // 앱이 포그라운드로 전환
            Log.d(TAG, "앱 포그라운드 전환")
            setForegroundState(true)
            PresenceManager.onAppForeground()
        }

        override fun onStop(owner: LifecycleOwner) {
            // 앱이 백그라운드로 전환
            Log.d(TAG, "앱 백그라운드 전환")
            setForegroundState(false)
            PresenceManager.onAppBackground()
        }
    }
}