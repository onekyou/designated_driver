package com.designated.driverapp.service

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.designated.driverapp.R
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 기사 PTT 수신 매니저 (수신전용).
 *  - FCM ptt_dispatch wake → fast-join → 매니저 음성 자동 스피커 재생.
 *  - 수신전용: LIVE_BROADCASTING / AUDIENCE role / 마이크 미publish.
 *  - 종료 오버톤: 매니저 발화 종료(remote mute / offline) 감지 → 로컬 비프(R.raw.ptt_start) "오버".
 *  - leave 앵커 = 발화 종료 기준 ~5초(긴 메시지 중 이탈 방지). RTM 미사용(FCM+RTC).
 */
class PttAudioManager {

    companion object {
        private const val TAG = "PttAudioManager"
        private const val APP_ID = "e5aae3aa18484cd2a1fed0018cfb15bd" // 공개값
        private const val LEAVE_DELAY_MS = 5_000L      // 발화 종료 기준 워밍창
        private const val WAKE_FALLBACK_MS = 10_000L   // wake 후 오디오 안 오는 비정상 케이스 fallback
        private const val TOKEN_PREFS = "ptt_token_cache"
        private const val TOKEN_VALID_MS = 86_400_000L         // 24h (발급 시각 기준)
        private const val TOKEN_REFRESH_MARGIN_MS = 3_600_000L // 만료 1h 전 갱신
    }

    private val functions = Firebase.functions("asia-northeast3")
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var engine: RtcEngine? = null
    private var appCtx: Context? = null
    private var currentChannel: String? = null
    private var leaveJob: Job? = null
    private var wasStarted = false // 매니저 오디오 STARTED를 본 뒤에만 오버톤(조기 오발 가드)
    private var speakingUid = 0    // 실제 발화 중인 송신자 uid — 메시(3자+) 다른 broadcaster 상태변화 오인 차단

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            engine?.setEnableSpeakerphone(true) // 라우트 강제(스피커) — 이어피스 플립 차단
            Log.i(TAG, "수신 join 성공 channel=$channel uid=$uid elapsed=$elapsed spk=${engine?.isSpeakerphoneEnabled}")
        }
        override fun onError(err: Int) {
            Log.e(TAG, "Agora onError code=$err")
        }
        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.i(TAG, "송신자 입장 uid=$uid")
        }
        // 매니저 오디오 상태 변화 — STARTED 후 STOPPED(remote mute)면 발화 종료 → 오버톤 + 5초 재앵커
        override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            when (state) {
                Constants.REMOTE_AUDIO_STATE_STARTING, Constants.REMOTE_AUDIO_STATE_DECODING -> {
                    speakingUid = uid              // 실제 발화자 래칭
                    wasStarted = true
                    leaveJob?.cancel() // 청취 중 이탈 방지
                    // ★ 스피커 재설정 금지 — 이 콜백은 발화 중 상태전이마다 재발화 → 재생 중 라우트 리셋=끊김.
                    //   라우트는 onJoinChannelSuccess 1회 강제로 충분(sticky).
                    Log.i(TAG, "매니저 발화 시작 감지 uid=$uid state=$state")
                }
                Constants.REMOTE_AUDIO_STATE_STOPPED -> {
                    Log.i(TAG, "원격 STOPPED uid=$uid reason=$reason speakingUid=$speakingUid wasStarted=$wasStarted")
                    // ★ 발화자 본인의 mute일 때만 종료 — 다른 broadcaster(비발화 수신자) 상태변화 오인 차단
                    if (uid == speakingUid && reason == Constants.REMOTE_AUDIO_REASON_REMOTE_MUTED && wasStarted) {
                        wasStarted = false
                        speakingUid = 0
                        Log.i(TAG, "매니저 발화 종료 감지 → 오버톤 + 5초 재앵커")
                        playOverTone()
                        engine?.let { scheduleLeave(it, LEAVE_DELAY_MS) }
                    }
                }
            }
        }
        // 발화 중이던 송신자 이탈 — 오버톤 + 즉시 leave. 비발화 broadcaster 이탈은 무시(재생 유지).
        override fun onUserOffline(uid: Int, reason: Int) {
            Log.i(TAG, "송신자 이탈 uid=$uid speakingUid=$speakingUid")
            if (uid != speakingUid && wasStarted) return // 다른 broadcaster 이탈 — 무시
            if (wasStarted) { wasStarted = false; playOverTone() }
            speakingUid = 0
            leaveJob?.cancel()
            engine?.leaveChannel()
            currentChannel = null
        }
    }

    private fun ensureEngine(ctx: Context) {
        if (appCtx == null) appCtx = ctx.applicationContext ?: ctx
        if (engine != null) return
        try {
            val config = RtcEngineConfig().apply {
                mContext = appCtx
                mAppId = APP_ID
                mEventHandler = eventHandler
            }
            engine = RtcEngine.create(config)
            engine?.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD)
            engine?.setDefaultAudioRoutetoSpeakerphone(true) // 기본 라우트 스피커(현재 누락 보완)
            engine?.setParameters("{\"che.audio.keep.audiosessiontype\":true}") // 실험: leave 후 OS 오디오세션 유지
            Log.i(TAG, "RtcEngine created (수신)")
        } catch (e: Exception) {
            Log.e(TAG, "RtcEngine 생성 실패", e)
        }
    }

    /** 토큰 prewarm — DriverForegroundService 진입점에서 미리 발급해 캐시(수신 join 가속). */
    fun prewarmToken(ctx: Context, regionId: String, officeId: String) {
        ensureEngine(ctx)
        scope.launch { ensureToken(regionId, officeId) }
    }

    /**
     * Agora 토큰 캐시 — office별 24h 토큰. 만료 1h 전까지 캐시 재사용(함수 호출 0), 임박/미스 시 발급.
     *  수신측 가속 = READY_TIMEOUT 완화 핵심. lazy 갱신(prewarm + onWake 직전 폴백), 주기 타이머 없음.
     */
    private suspend fun ensureToken(regionId: String, officeId: String): Pair<String, String>? {
        val ctx = appCtx ?: return null
        val key = "${regionId}_${officeId}"
        val prefs = ctx.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cachedToken = prefs.getString("token_$key", null)
        val cachedChannel = prefs.getString("channel_$key", null)
        val expireMs = prefs.getLong("expire_$key", 0L)
        if (cachedToken != null && cachedChannel != null && now < expireMs - TOKEN_REFRESH_MARGIN_MS) {
            return cachedToken to cachedChannel
        }
        return try {
            val tokenData = hashMapOf("regionId" to regionId, "officeId" to officeId, "uid" to 0)
            val result = functions.getHttpsCallable("generateAgoraToken").call(tokenData).await()
            @Suppress("UNCHECKED_CAST")
            val map = result.getData() as? Map<String, Any?> ?: emptyMap()
            val token = map["token"] as? String
            val channelName = map["channelName"] as? String
            if (token.isNullOrBlank() || channelName.isNullOrBlank()) {
                Log.e(TAG, "ensureToken: 토큰/채널 누락"); return null
            }
            prefs.edit()
                .putString("token_$key", token)
                .putString("channel_$key", channelName)
                .putLong("expire_$key", now + TOKEN_VALID_MS)
                .apply()
            Log.i(TAG, "ensureToken: 신규 발급+캐시 ($channelName)")
            token to channelName
        } catch (e: Exception) {
            Log.e(TAG, "ensureToken 실패", e); null
        }
    }

    /** 종료 오버톤(매니저 발화 끝) — 채팅음 R.raw.ptt_start 로컬 재생. */
    private fun playOverTone() {
        val ctx = appCtx ?: return
        try {
            val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.ptt_start}")
            RingtoneManager.getRingtone(ctx, uri)?.play()
        } catch (e: Exception) {
            Log.w(TAG, "playOverTone 실패", e)
        }
    }

    /**
     * ptt_dispatch wake 수신 → 채널 fast-join (이미 join 중이면 fallback 타이머만 리셋).
     *  regionId/officeId 는 기사 prefs 출처(채널 = `${regionId}_${officeId}_ptt`, 매니저와 동일 사무실).
     */
    fun onWake(ctx: Context, regionId: String, officeId: String, expectedChannel: String?) {
        // [PTT 차단] 운행 중(콜 IN_PROGRESS)이면 음성 수신 join 자체를 스킵(채팅 텍스트는 별개 경로라 계속 쌓임).
        //  풀패키지명 — 이 파일은 io.agora.rtc2.Constants 를 import 중이라 driverapp Constants 와 충돌 회피.
        val blocked = ctx.getSharedPreferences(
                com.designated.driverapp.data.Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(com.designated.driverapp.data.Constants.PREF_KEY_PTT_BLOCK_ONTRIP, false)
        if (blocked) {
            Log.i(TAG, "onWake: 운행 중(콜 IN_PROGRESS) — PTT 음성 수신 차단(join skip)")
            return
        }
        ensureEngine(ctx)
        val eng = engine ?: run { Log.e(TAG, "onWake: engine null"); return }

        if (currentChannel != null) {
            Log.i(TAG, "onWake: 채널 유지중 ($currentChannel) — fallback 타이머 리셋")
            scheduleLeave(eng, WAKE_FALLBACK_MS)
            return
        }

        scope.launch {
            try {
                val tc = ensureToken(regionId, officeId)  // 캐시 우선 → 수신 join 가속(READY_TIMEOUT 완화 핵심)
                if (tc == null) { Log.e(TAG, "onWake: 토큰 확보 실패"); return@launch }
                val (token, channelName) = tc
                if (expectedChannel != null && expectedChannel != channelName) {
                    Log.w(TAG, "채널 불일치 wake=$expectedChannel token=$channelName (사무실 설정 확인)")
                }
                val options = ChannelMediaOptions().apply {
                    channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                    // BROADCASTER role: 마이크 미publish라 실질 수신전용이지만, audience와 달리
                    // 합류 시 매니저쪽 onUserJoined를 울려 "연결 완료" 신호가 즉시 전달됨(3초 타임아웃 회피).
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                    publishMicrophoneTrack = false
                    autoSubscribeAudio = true
                }
                eng.joinChannel(token, channelName, 0, options)
                eng.enableLocalAudio(false) // 마이크 미publish (수신전용)
                currentChannel = channelName
                wasStarted = false
                Log.i(TAG, "onWake: joinChannel $channelName (수신)")
                scheduleLeave(eng, WAKE_FALLBACK_MS) // 오디오 오면 STARTED가 취소, 안 오면 fallback leave
            } catch (e: Exception) {
                Log.e(TAG, "onWake 실패", e)
            }
        }
    }

    private fun scheduleLeave(eng: RtcEngine, delayMs: Long) {
        leaveJob?.cancel()
        leaveJob = scope.launch {
            delay(delayMs)
            eng.leaveChannel()
            currentChannel = null
            wasStarted = false
            Log.i(TAG, "leaveChannel (${delayMs / 1000}s)")
        }
    }

    fun release() {
        leaveJob?.cancel()
        try {
            engine?.leaveChannel()
            RtcEngine.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "release 실패", e)
        }
        engine = null
        currentChannel = null
        wasStarted = false
    }
}
