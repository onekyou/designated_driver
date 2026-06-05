package com.designated.driverapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.designated.driverapp.MainActivity
import com.designated.driverapp.R
import com.designated.driverapp.data.Constants
import com.designated.driverapp.model.CallInfo
import com.designated.driverapp.model.DriverStatus
import android.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "DriverForegroundService"
private const val SERVICE_STATUS_CHANNEL_ID = "DriverServiceStatusChannel"
private const val CALL_CHANNEL_ID = "DriverCallChannel"
private const val NOTIFICATION_ID = 1
private const val SERVICE_STATUS_NOTIFICATION_TITLE = "대리운전 기사앱"
private const val SERVICE_STATUS_NOTIFICATION_TEXT = "서비스 실행 중"

/**
 * FCM 기반 포그라운드 서비스
 * - Firestore 리스너 제거됨
 * - FCM 알림을 통해 콜 정보 수신
 * - 포그라운드 상태 유지 및 알림 관리만 담당
 */
class DriverForegroundService : Service() {

    private val _driverStatus = MutableStateFlow<DriverStatus>(DriverStatus.OFFLINE)
    val driverStatus: StateFlow<DriverStatus> = _driverStatus

    private val _assignedCall = MutableStateFlow<CallInfo?>(null)
    val assignedCall: StateFlow<CallInfo?> = _assignedCall

    // PTT 수신 매니저 (FCM ptt_dispatch wake → fast-join → 음성 재생). 수신전용.
    // 엔진은 onWake 시점 Service 컨텍스트로 생성(컨텍스트 불안정 회피).
    private val pttAudioManager = PttAudioManager()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DriverForegroundService onCreate")
        createNotificationChannel()

        // 기본 포그라운드 알림 시작
        val notification = createStatusNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        // FCM에서 전달받은 콜 정보 처리
        intent?.let { handleIntent(it) }

        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_NEW_CALL_ASSIGNED -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                Log.d(TAG, "새 콜 배정: callId=$callId")
                // 알림은 MyFirebaseMessagingService에서 생성하므로 여기서는 처리하지 않음
            }
            ACTION_CLEAR_CALL -> {
                Log.d(TAG, "콜 상태 클리어")
                clearAssignedCallState()
                // 기본 상태 알림으로 복원
                val notification = createStatusNotification()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE_STATUS -> {
                val status = intent.getStringExtra(EXTRA_DRIVER_STATUS)
                Log.d(TAG, "기사 상태 업데이트: $status")
                _driverStatus.value = DriverStatus.fromString(status)
            }
            ACTION_PTT_JOIN_CHANNEL -> {
                val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME)
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val regionId = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
                val officeId = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)
                Log.d(TAG, "PTT wake: channel=$channelName from=$senderName region=$regionId office=$officeId")
                if (regionId.isNullOrBlank() || officeId.isNullOrBlank()) {
                    Log.w(TAG, "PTT wake: 사무실 정보 없음 — join 불가")
                } else {
                    pttAudioManager.onWake(this, regionId, officeId, channelName)
                }
            }
            ACTION_PLAY_VOICE_MEMO -> {
                val url = intent.getStringExtra(EXTRA_AUDIO_URL)
                if (url.isNullOrBlank()) {
                    Log.w(TAG, "음성 메모 재생: URL 없음")
                } else {
                    playVoiceMemoInternal(url)
                }
            }
        }
    }

    // ===== PTT 콜드 음성 메모 자동재생 =====
    private var voiceMemoPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private fun playVoiceMemoInternal(url: String) {
        try {
            // 직전 재생 정리(직렬화)
            voiceMemoPlayer?.let { runCatching { it.release() } }
            voiceMemoPlayer = null

            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            requestAudioFocusCompat(am, attrs)

            val mp = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    runCatching { it.release() }
                    voiceMemoPlayer = null
                    abandonAudioFocusCompat(am)
                }
                setOnErrorListener { _, _, _ ->
                    runCatching { release() }
                    voiceMemoPlayer = null
                    abandonAudioFocusCompat(am)
                    true
                }
                prepareAsync()
            }
            voiceMemoPlayer = mp
            Log.i(TAG, "음성 메모 자동재생 시작: $url")
        } catch (e: Exception) {
            Log.e(TAG, "음성 메모 재생 실패", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocusCompat(am: AudioManager, attrs: AudioAttributes) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocusCompat(am: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    fun clearAssignedCallState() {
        _assignedCall.value = null
    }

    fun updateDriverStatus(status: DriverStatus) {
        _driverStatus.value = status
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DriverForegroundService onDestroy")
        pttAudioManager.release()
        runCatching { voiceMemoPlayer?.release() }
        voiceMemoPlayer = null
    }

    private val binder = LocalBinder()

    inner class LocalBinder : android.os.Binder() {
        fun getService(): DriverForegroundService = this@DriverForegroundService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 긴급 콜 알림 채널
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "콜 배정 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "새로운 대리운전 호출이 배정되었을 때 알립니다."
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)

            // 서비스 상태 채널
            val statusChannel = NotificationChannel(
                SERVICE_STATUS_CHANNEL_ID,
                "서비스 실행 상태",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "앱 백그라운드 서비스 실행 상태를 표시합니다."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            notificationManager.createNotificationChannel(statusChannel)
        }
    }

    private fun createStatusNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SERVICE_STATUS_CHANNEL_ID)
            .setContentTitle(SERVICE_STATUS_NOTIFICATION_TITLE)
            .setContentText(SERVICE_STATUS_NOTIFICATION_TEXT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_NEW_CALL_ASSIGNED = "com.designated.driverapp.ACTION_NEW_CALL_ASSIGNED"
        const val ACTION_CLEAR_CALL = "com.designated.driverapp.ACTION_CLEAR_CALL"
        const val ACTION_UPDATE_STATUS = "com.designated.driverapp.ACTION_UPDATE_STATUS"
        const val ACTION_PTT_JOIN_CHANNEL = "com.designated.driverapp.ACTION_PTT_JOIN_CHANNEL"
        const val ACTION_PLAY_VOICE_MEMO = "com.designated.driverapp.ACTION_PLAY_VOICE_MEMO"

        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_DRIVER_STATUS = "driverStatus"
        const val EXTRA_CHANNEL_NAME = "channelName"
        const val EXTRA_SENDER_NAME = "senderName"
        const val EXTRA_AUDIO_URL = "audioUrl"

        /** PTT 콜드 음성 메모 자동재생 — FCM 수신 시 서비스로 위임(포그라운드/백그라운드 무관). */
        fun playVoiceMemo(context: Context, audioUrl: String) {
            val intent = Intent(context, DriverForegroundService::class.java).apply {
                action = ACTION_PLAY_VOICE_MEMO
                putExtra(EXTRA_AUDIO_URL, audioUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun newCallAssignedIntent(context: Context, callId: String, title: String, body: String): Intent {
            return Intent(context, DriverForegroundService::class.java).apply {
                action = ACTION_NEW_CALL_ASSIGNED
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
            }
        }

        fun clearCallIntent(context: Context): Intent {
            return Intent(context, DriverForegroundService::class.java).apply {
                action = ACTION_CLEAR_CALL
            }
        }

        fun newPttDispatchIntent(context: Context, channelName: String, senderName: String): Intent {
            return Intent(context, DriverForegroundService::class.java).apply {
                action = ACTION_PTT_JOIN_CHANNEL
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_SENDER_NAME, senderName)
            }
        }
    }
}
