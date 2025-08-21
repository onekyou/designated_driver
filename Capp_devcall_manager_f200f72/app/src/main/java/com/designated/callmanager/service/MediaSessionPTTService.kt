package com.designated.callmanager.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.VolumeProviderCompat
import com.designated.callmanager.MainActivity
import com.designated.callmanager.R
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.KeyEvent

/**
 * MediaSessionCompat 기반 PTT 서비스
 * - 백그라운드에서도 볼륨키를 가로채서 PTT로 사용
 * - 미디어 세션을 통한 시스템 레벨 키 이벤트 처리
 * - Foreground Service로 백그라운드 동작 보장
 */
class MediaSessionPTTService : Service() {

    companion object {
        private const val TAG = "MediaSessionPTTService"
        private const val CHANNEL_ID = "media_session_ptt_channel"
        private const val NOTIFICATION_ID = 1003

        const val ACTION_START_MEDIA_SESSION_PTT = "start_media_session_ptt"
        const val ACTION_STOP_MEDIA_SESSION_PTT = "stop_media_session_ptt"

        @Volatile
        private var isServiceRunning = false

        fun isRunning() = isServiceRunning
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var pttManager: PTTManager
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private lateinit var sharedPreferences: SharedPreferences

    // Audio Focus - PTT 전용으로 강화
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // 강제 처리
        .build()

    // Wake Lock
    private var wakeLock: PowerManager.WakeLock? = null

    // PTT 상태
    private var isPTTActive = false
    
    // Accessibility Service PTT 브로드캐스트 리시버 - Android 15 개선
    private val pttReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.designated.callmanager.PTT_ACTION") {
                val action = intent.getStringExtra("action")
                val source = intent.getStringExtra("source") ?: "unknown"
                val timestamp = intent.getLongExtra("timestamp", 0L)
                
                Log.i(TAG, "========== PTT 브로드캐스트 수신 ==========")
                Log.i(TAG, "Action: $action")
                Log.i(TAG, "Source: $source")
                Log.i(TAG, "Timestamp: $timestamp")
                Log.i(TAG, "Current Time: ${System.currentTimeMillis()}")
                
                when (action) {
                    "start" -> {
                        Log.i(TAG, "🎯 PTT 시작 요청 처리 중...")
                        handlePTTStart()
                    }
                    "stop" -> {
                        Log.i(TAG, "🎯 PTT 중지 요청 처리 중...")
                        handlePTTStop()
                    }
                    else -> {
                        Log.w(TAG, "알 수 없는 PTT 액션: $action")
                    }
                }
                Log.i(TAG, "=========================================")
            } else {
                Log.d(TAG, "다른 브로드캐스트 무시: ${intent?.action}")
            }
        }
    }
    
    // MediaSession 상태 감시 및 유지를 위한 핸들러
    private val mediaSessionKeepAliveHandler = Handler(Looper.getMainLooper())
    private val mediaSessionKeepAliveRunnable = object : Runnable {
        override fun run() {
            try {
                if (::mediaSession.isInitialized && !mediaSession.isActive) {
                    Log.w(TAG, "MediaSession이 비활성 상태 - 재활성화")
                    mediaSession.isActive = true
                    requestAudioFocus() // Audio Focus 재요청
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaSession keep-alive 실패", e)
            }
            // 10초마다 검사
            mediaSessionKeepAliveHandler.postDelayed(this, 10000)
        }
    }
    
    // Accessibility Service PTT 이벤트 수신기
    /*
    private val accessibilityPTTReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PTTAccessibilityService.ACTION_PTT_KEY_EVENT) {
                val keyCode = intent.getIntExtra(PTTAccessibilityService.EXTRA_KEY_CODE, -1)
                val keyAction = intent.getIntExtra(PTTAccessibilityService.EXTRA_KEY_ACTION, -1)
                val isLongPress = intent.getBooleanExtra(PTTAccessibilityService.EXTRA_IS_LONG_PRESS, false)
                
                handleAccessibilityPTTEvent(keyCode, keyAction, isLongPress)
            }
        }
    }
    */

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MediaSessionPTTService 생성됨")

        // PTT 상태 초기화 (중요!)
        isPTTActive = false
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        sharedPreferences = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

        setupMediaSession()
        createNotificationChannel()
        requestAudioFocus()
        acquireWakeLock()
        
        // Accessibility Service PTT 이벤트 리스너 등록
        registerPTTReceiver()
        
        // MediaSession Keep-Alive 시작
        startMediaSessionKeepAlive()

        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand 호출됨, action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_MEDIA_SESSION_PTT -> {
                startMediaSessionPTTService()
            }
            ACTION_STOP_MEDIA_SESSION_PTT -> {
                stopMediaSessionPTTService()
            }
            else -> {
                startMediaSessionPTTService()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MediaSessionPTTService 종료됨")

        // MediaSession Keep-Alive 중지
        stopMediaSessionKeepAlive()
        
        // PTT 브로드캐스트 리시버 해제
        unregisterPTTReceiver()
        
        cleanupMediaSession()
        releaseAudioFocus()
        releaseWakeLock()
        if (::pttManager.isInitialized) {
            pttManager.destroy()
        }

        isServiceRunning = false
    }

    /**
     * MediaSession 설정
     */
    private fun setupMediaSession() {
        try {
            mediaSession = MediaSessionCompat(this, "PTT_MediaSession")

            // 볼륨 제어를 앱에서 직접 처리 - 절대 볼륨 제어로 변경
            val volumeProvider = object : VolumeProviderCompat(
                VOLUME_CONTROL_ABSOLUTE, // 절대적 볼륨 제어로 완전 차단
                100, // 최대 볼륨 (0-100)
                50   // 현재 볼륨 (고정값)
            ) {
                override fun onAdjustVolume(direction: Int) {
                    Log.i(TAG, "🎯 Volume adjustment intercepted: direction=$direction, current PTT state=$isPTTActive")
                    
                    when (direction) {
                        AudioManager.ADJUST_LOWER -> {
                            if (!isPTTActive) {
                                Log.i(TAG, "🎯 볼륨 다운으로 PTT 시작")
                                handlePTTStart()
                            } else {
                                Log.d(TAG, "PTT 이미 활성화됨 - 볼륨 다운 무시")
                            }
                        }
                        AudioManager.ADJUST_RAISE -> {
                            if (isPTTActive) {
                                Log.i(TAG, "🎯 볼륨 업으로 PTT 중지")
                                handlePTTStop()
                            }
                        }
                        0 -> {
                            // 버튼을 뗀 경우 (direction=0)
                            if (isPTTActive) {
                                Log.i(TAG, "🎯 버튼을 떼어서 PTT 중지")
                                handlePTTStop()
                            }
                        }
                    }
                    
                    // 실제 볼륨은 변경하지 않음 (PTT 전용)
                    // super.onAdjustVolume는 호출하지 않음
                }

                override fun onSetVolumeTo(volume: Int) {
                    Log.i(TAG, "🎯 Volume set to: $volume - 완전 차단")
                    // 실제 볼륨 변경 차단 - 아무것도 하지 않음
                }
            }

            mediaSession.setPlaybackToRemote(volumeProvider)

            // 미디어 버튼 이벤트 처리
            val mediaSessionCallback = object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.i(TAG, "🎯 Media button: onPlay - PTT 시작")
                    handlePTTStart()
                }

                override fun onPause() {
                    Log.i(TAG, "🎯 Media button: onPause - PTT 중지")
                    handlePTTStop()
                }

                override fun onStop() {
                    Log.i(TAG, "🎯 Media button: onStop - PTT 중지")
                    handlePTTStop()
                }
            }

            mediaSession.setCallback(mediaSessionCallback)

            // 플레이백 상태 설정 - 백그라운드에서도 활성 상태 유지
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f) // PLAYING 상태로 시작
                .build()

            mediaSession.setPlaybackState(playbackState)

            // 메타데이터 설정 - 시스템에 PTT 앱으로 인식되도록 설정
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "PTT 통신")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "콜 매니저")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Push-to-Talk")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1) // 무한 길이
                .build()

            mediaSession.setMetadata(metadata)
            
            // MediaSession을 백그라운드에서도 활성 상태로 유지
            mediaSession.isActive = true
            
            // 추가: Media Session의 우선순위를 높이기 위한 설정
            try {
                mediaSession.setSessionActivity(
                    PendingIntent.getActivity(
                        this, 0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "PendingIntent 설정 실패: ${e.message}")
            }

            Log.i(TAG, "✅ MediaSession 설정 완료")

        } catch (e: Exception) {
            Log.e(TAG, "MediaSession 설정 실패", e)
        }
    }

    /**
     * PTT 시작 처리
     */
    private fun handlePTTStart() {
        if (isPTTActive) {
            Log.d(TAG, "PTT 이미 활성 상태 - 무시")
            return
        }

        Log.i(TAG, "🎯 PTT 시작 - MediaSession을 통한 볼륨키 가로채기")
        isPTTActive = true

        try {
            // PTTManager 초기화 및 시작
            initializePTTManager()
            pttManager.handleVolumeDownPress()

            // 알림 업데이트
            updateNotification("PTT 송신 중", "MediaSession을 통해 PTT가 활성화되었습니다")

            // 플레이백 상태를 재생 중으로 변경
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .build()

            mediaSession.setPlaybackState(playbackState)

        } catch (e: Exception) {
            Log.e(TAG, "PTT 시작 중 오류", e)
            isPTTActive = false
        }
    }

    /**
     * PTT 중지 처리
     */
    private fun handlePTTStop() {
        if (!isPTTActive) {
            Log.d(TAG, "PTT 이미 비활성 상태")
            return
        }

        Log.i(TAG, "🎯 PTT 중지 - MediaSession을 통한 볼륨키 가로채기")
        isPTTActive = false

        try {
            // PTTManager 중지
            if (::pttManager.isInitialized) {
                pttManager.handleVolumeDownRelease()
            }

            // 알림 업데이트
            updateNotification("PTT 대기 중", "MediaSession PTT 서비스가 실행 중입니다")

            // 플레이백 상태를 일시정지로 변경
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                .build()

            mediaSession.setPlaybackState(playbackState)

        } catch (e: Exception) {
            Log.e(TAG, "PTT 중지 중 오류", e)
        }
    }

    /**
     * PTTManager 초기화
     */
    private fun initializePTTManager() {
        if (::pttManager.isInitialized) {
            return
        }

        try {
            val region = sharedPreferences.getString("regionId", null)
            val office = sharedPreferences.getString("officeId", null)

            if (region.isNullOrEmpty() || office.isNullOrEmpty()) {
                Log.w(TAG, "PTTManager 초기화 실패: region/office 정보 없음")
                return
            }

            pttManager = PTTManager.getInstance(
                context = applicationContext,
                userType = "media_session_ptt",
                regionId = region,
                officeId = office
            )

            pttManager.initialize(object : PTTManager.PTTCallback {
                override fun onStatusChanged(status: String) {
                    Log.d(TAG, "PTT 상태: $status")
                }

                override fun onConnectionStateChanged(isConnected: Boolean) {
                    Log.i(TAG, "PTT 연결: $isConnected")
                }

                override fun onSpeakingStateChanged(isSpeaking: Boolean) {
                    Log.i(TAG, "PTT 송신: $isSpeaking")
                }

                override fun onError(error: String) {
                    Log.e(TAG, "PTT 오류: $error")
                }
            })

            Log.i(TAG, "PTTManager 초기화 완료")

        } catch (e: Exception) {
            Log.e(TAG, "PTTManager 초기화 중 오류", e)
        }
    }

    /**
     * MediaSession PTT 서비스 시작
     */
    private fun startMediaSessionPTTService() {
        Log.i(TAG, "MediaSession PTT 서비스 시작")

        val notification = createPTTNotification(
            "MediaSession PTT 활성화",
            "백그라운드에서 볼륨키로 PTT 사용 가능"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        initializePTTManager()
    }

    /**
     * MediaSession PTT 서비스 중지
     */
    private fun stopMediaSessionPTTService() {
        Log.i(TAG, "MediaSession PTT 서비스 중지")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Audio Focus 요청 - PTT를 위한 강화된 설정
     */
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false) // 다이믹에서도 일시정지 방지
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(TAG, "Audio focus changed: $focusChange")
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            Log.w(TAG, "Audio focus 완전 손실 - MediaSession 재활성화 시도")
                            // 잠시 후 MediaSession 재활성화
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    if (::mediaSession.isInitialized) {
                                        mediaSession.isActive = true
                                    }
                                    requestAudioFocus() // 재요청
                                } catch (e: Exception) {
                                    Log.e(TAG, "MediaSession 재활성화 실패", e)
                                }
                            }, 1000)
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            Log.i(TAG, "Audio focus 임시 손실 - PTT 유지")
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            Log.i(TAG, "Audio focus 획득 - PTT 준비 완료")
                        }
                    }
                }
                .build()

            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            Log.i(TAG, "Audio focus request result: $result")
            
            // Audio Focus를 얻지 못한 경우 재시도
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Audio focus 획득 실패 - 3초 후 재시도")
                Handler(Looper.getMainLooper()).postDelayed({
                    requestAudioFocus()
                }, 3000)
            }
        }
    }

    /**
     * Audio Focus 해제
     */
    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        }
    }

    /**
     * Wake Lock 획득
     */
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CallManager:MediaSessionPTTWakeLock"
                )
                wakeLock?.acquire(10 * 60 * 1000L) // 10분
                Log.i(TAG, "Wake Lock 획득됨")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wake Lock 획득 실패", e)
        }
    }

    /**
     * Wake Lock 해제
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "Wake Lock 해제됨")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Wake Lock 해제 실패", e)
        }
    }

    /**
     * MediaSession 정리
     */
    private fun cleanupMediaSession() {
        try {
            if (::mediaSession.isInitialized) {
                mediaSession.isActive = false
                mediaSession.release()
            }
            Log.i(TAG, "MediaSession 정리 완료")
        } catch (e: Exception) {
            Log.e(TAG, "MediaSession 정리 실패", e)
        }
    }

    /**
     * 알림 채널 생성
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MediaSession PTT 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MediaSession을 통한 백그라운드 PTT 서비스"
                setShowBadge(false)
                setSound(null, null)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * PTT 알림 생성
     */
    private fun createPTTNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * 알림 업데이트
     */
    private fun updateNotification(title: String, content: String) {
        try {
            val notification = createPTTNotification(title, content)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "알림 업데이트 실패", e)
        }
    }
    
    /**
     * PTT 브로드캐스트 리시버 등록 - Android 15 호환성 개선
     */
    private fun registerPTTReceiver() {
        try {
            val filter = IntentFilter("com.designated.callmanager.PTT_ACTION")
            
            // Android 15에서 개선된 브로드캐스트 등록
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ (API 33+)에서는 RECEIVER_EXPORTED 플래그 사용
                registerReceiver(pttReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                Log.i(TAG, "PTT 브로드캐스트 리시버 등록됨 (Android 13+ 호환)")
            } else {
                registerReceiver(pttReceiver, filter)
                Log.i(TAG, "PTT 브로드캐스트 리시버 등록됨 (레거시 호환)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "PTT 브로드캐스트 리시버 등록 실패", e)
        }
    }
    
    /**
     * PTT 브로드캐스트 리시버 해제
     */
    private fun unregisterPTTReceiver() {
        try {
            unregisterReceiver(pttReceiver)
            Log.i(TAG, "PTT 브로드캐스트 리시버 해제됨")
        } catch (e: Exception) {
            Log.e(TAG, "PTT 브로드캐스트 리시버 해제 실패", e)
        }
    }
    
    /**
     * MediaSession Keep-Alive 시작
     */
    private fun startMediaSessionKeepAlive() {
        Log.i(TAG, "MediaSession Keep-Alive 시작")
        mediaSessionKeepAliveHandler.post(mediaSessionKeepAliveRunnable)
    }
    
    /**
     * MediaSession Keep-Alive 중지
     */
    private fun stopMediaSessionKeepAlive() {
        Log.i(TAG, "MediaSession Keep-Alive 중지")
        mediaSessionKeepAliveHandler.removeCallbacks(mediaSessionKeepAliveRunnable)
    }
    
    /*
    /**
     * Accessibility Service PTT 이벤트 리스너 등록
     */
    private fun registerAccessibilityPTTReceiver() {
        try {
            val filter = IntentFilter(PTTAccessibilityService.ACTION_PTT_KEY_EVENT)
            LocalBroadcastManager.getInstance(this).registerReceiver(accessibilityPTTReceiver, filter)
            Log.i(TAG, "Accessibility Service PTT 이벤트 리스너 등록됨")
        } catch (e: Exception) {
            Log.e(TAG, "Accessibility Service PTT 이벤트 리스너 등록 실패", e)
        }
    }
    
    /**
     * Accessibility Service PTT 이벤트 리스너 해제
     */
    private fun unregisterAccessibilityPTTReceiver() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(accessibilityPTTReceiver)
            Log.i(TAG, "Accessibility Service PTT 이벤트 리스너 해제됨")
        } catch (e: Exception) {
            Log.e(TAG, "Accessibility Service PTT 이벤트 리스너 해제 실패", e)
        }
    }
    
    /**
     * Accessibility Service에서 전달받은 PTT 이벤트 처리
     */
    private fun handleAccessibilityPTTEvent(keyCode: Int, keyAction: Int, isLongPress: Boolean) {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                when (keyAction) {
                    KeyEvent.ACTION_DOWN -> {
                        Log.i(TAG, "🎯 Accessibility Service PTT 시작 - MediaSession 통합 처리")
                        handlePTTStart()
                    }
                    KeyEvent.ACTION_UP -> {
                        Log.i(TAG, "🎯 Accessibility Service PTT 종료 - MediaSession 통합 처리 (롱프레스: $isLongPress)")
                        handlePTTStop()
                    }
                }
            }
        }
    }
    */
}