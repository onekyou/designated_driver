package com.designated.pickupdriver.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.designated.pickupdriver.PickupDriverApplication
import com.designated.pickupdriver.R
import com.designated.pickupdriver.data.Constants

/**
 * 픽업기사 PTT 수신 Foreground Service.
 *  - ACTION_PTT_JOIN_CHANNEL: 라이브 PTT wake → PTTManager.onWake fast-join (화면 off에서도 수신).
 *  - ACTION_PLAY_VOICE_MEMO: 콜드 음성 메모 자동재생 (MediaPlayer + AudioFocus).
 *  ★ 엔진은 PickupDriverApplication 단일 소유 — onDestroy에서 pttManager.release() 호출 금지(MainActivity와 공유).
 *  (call_manager PttReceiverService fork — prefs 키 픽업 정합)
 */
class PttReceiverService : Service() {

    private val pttManager get() = PickupDriverApplication.getInstance().pttManager

    private var voiceMemoPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
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
                    pttManager.onWake(this, regionId, officeId, channelName)
                }
            }
            ACTION_PLAY_VOICE_MEMO -> {
                val url = intent.getStringExtra(EXTRA_AUDIO_URL)
                if (url.isNullOrBlank()) Log.w(TAG, "음성 메모 재생: URL 없음")
                else playVoiceMemoInternal(url)
            }
        }
        return START_NOT_STICKY
    }

    // ===== PTT 콜드 음성 메모 자동재생 (driver_app DriverForegroundService 이식) =====
    private fun playVoiceMemoInternal(url: String) {
        try {
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

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("무전기 수신")
            .setContentText("PTT 음성 수신 중")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        // 수신(Agora 미publish)·콜드 재생(MediaPlayer)은 마이크 미사용 → microphone FGS 타입 금지.
        //  microphone은 "사용 중(while-in-use)" 권한이라 백그라운드(FCM wake) 시작 시 SecurityException.
        //  타입 없이 띄우고, 백그라운드 제약(ForegroundServiceStartNotAllowedException)은 크래시 대신 종료.
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground 차단 — FGS 없이 종료", e)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "PTT 수신", NotificationManager.IMPORTANCE_LOW).apply {
                description = "무전기 음성 수신 상태"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ★ pttManager.release() 호출 금지 — 엔진은 Application 단일 소유(MainActivity 공유). voiceMemoPlayer만 정리.
        runCatching { voiceMemoPlayer?.release() }
        voiceMemoPlayer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PttReceiverService"
        private const val CHANNEL_ID = "ptt_receiver_channel"
        private const val NOTIFICATION_ID = 7301

        const val ACTION_PTT_JOIN_CHANNEL = "com.designated.pickupdriver.ACTION_PTT_JOIN_CHANNEL"
        const val ACTION_PLAY_VOICE_MEMO = "com.designated.pickupdriver.ACTION_PLAY_VOICE_MEMO"
        const val EXTRA_CHANNEL_NAME = "channelName"
        const val EXTRA_SENDER_NAME = "senderName"
        const val EXTRA_AUDIO_URL = "audioUrl"

        /** 라이브 PTT wake → 수신 join (포그라운드/백그라운드 무관). */
        fun newPttDispatchIntent(context: Context, channelName: String, senderName: String): Intent {
            return Intent(context, PttReceiverService::class.java).apply {
                action = ACTION_PTT_JOIN_CHANNEL
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_SENDER_NAME, senderName)
            }
        }

        /** 콜드 음성 메모 자동재생 — FCM 수신 시 서비스로 위임(포그라운드/백그라운드 무관). */
        fun playVoiceMemo(context: Context, audioUrl: String) {
            val intent = Intent(context, PttReceiverService::class.java).apply {
                action = ACTION_PLAY_VOICE_MEMO
                putExtra(EXTRA_AUDIO_URL, audioUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
