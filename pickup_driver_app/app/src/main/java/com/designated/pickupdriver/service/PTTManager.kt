package com.designated.pickupdriver.service

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.designated.pickupdriver.R
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/** PTT 상태 — 배너/비프 구동. RECORDING=콜드 음성 메모 녹음 중(현재 미사용·보존). LISTENING=수신 중. */
enum class PttState { IDLE, CONNECTING, TALKING, RECORDING, LISTENING }

/**
 * 픽업기사 PTT 송수신 매니저 (단일 RtcEngine 통합).
 *  ★ 원활한 통신 우선 재설계 (2026-06-03): "대화 중엔 항상 warm 라이브, 콜드 녹음 최소화".
 *  - 포그라운드 발화 = 항상 라이브 (콜드 음성메모 트리거 제거; 코드는 screen-off 트랙 대비 보존).
 *  - 수신(RX)/워밍(WARM) 중 응답 = leaveChannel 없이 updateChannelMediaOptions로 즉시 TX 승격(재join 0).
 *  - 워밍창 대화 단위 연장(CONV_WARM_MS): 발화/수신마다 리셋 → 대화 오가는 동안 양쪽 채널 warm.
 *  mode 플래그(NONE/TX/RX/WARM)로 eventHandler 분기. RTM 미사용(FCM+RTC).
 *  인스턴스는 PickupDriverApplication 단일 소유(MainActivity·PttReceiverService 공유).
 *  (call_manager PTTManager fork — senderName "픽업기사")
 */
class PTTManager {

    companion object {
        private const val TAG = "PTTManager"
        private const val APP_ID = "e5aae3aa18484cd2a1fed0018cfb15bd" // 공개값 (call_manager와 동일 Agora 프로젝트=같은 채널)
        private const val CONV_WARM_MS = 45_000L       // 대화 워밍창 (발화/수신마다 리셋)
        private const val READY_TIMEOUT_MS = 3_000L    // (cold-live / 상대 부재 warm) 미합류 시 강제 발화
        private const val WAKE_FALLBACK_MS = 10_000L   // 수신 신규 join 후 오디오 미도착 방어
        private const val TOKEN_PREFS = "ptt_token_cache"
        private const val TOKEN_VALID_MS = 86_400_000L         // 24h (발급 시각 기준)
        private const val TOKEN_REFRESH_MARGIN_MS = 3_600_000L // 만료 1h 전 갱신
    }

    /** 단일 엔진 용도 — TX(송신)/RX(청취)/WARM(채널 유지·중립). join 직전/종료 시 설정. */
    private enum class EngineMode { NONE, TX, RX, WARM }

    private val functions = Firebase.functions("asia-northeast3")
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var engine: RtcEngine? = null
    private var appCtx: Context? = null
    private var currentChannel: String? = null
    private var remoteUsers = 0
    private var readyFired = false
    private var leaveJob: Job? = null
    private var readyTimeoutJob: Job? = null
    private var mode = EngineMode.NONE          // 현재 엔진 용도 — eventHandler 콜백 분기
    private var wasStarted = false              // 수신: 송신자 오디오 STARTED 본 뒤에만 오버톤
    private var speakingUid = 0                 // 실제 발화 중인 송신자 uid — 메시(3자+) 다른 broadcaster 오인 차단

    // 콜드 음성 메모 녹음 (현재 미사용·보존 — screen-off 송신 트랙 부활 대비)
    private val pttRecorder = PttRecorder()
    private var recordPending = false

    /** (보존) 콜드 발화 종료 시 콜백. 현재 startTransmit이 호출 안 함. */
    var onColdVoiceMemo: ((File, Long) -> Unit)? = null
    /** RECORD_AUDIO 미허가 시 → 호출측 권한 요청 (보존). */
    var onRecordUnavailable: (() -> Unit)? = null

    private val _state = MutableStateFlow(PttState.IDLE)
    val state: StateFlow<PttState> = _state.asStateFlow()
    val isTalking: Boolean get() = _state.value == PttState.TALKING
    val isRecording: Boolean get() = _state.value == PttState.RECORDING

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            engine?.setEnableSpeakerphone(true) // 라우트 강제(스피커) — 이어피스 플립 차단
            Log.i(TAG, "onJoinChannelSuccess channel=$channel uid=$uid elapsed=$elapsed mode=$mode spk=${engine?.isSpeakerphoneEnabled}")
        }
        override fun onError(err: Int) {
            Log.e(TAG, "Agora onError code=$err")
        }
        override fun onUserJoined(uid: Int, elapsed: Int) {
            remoteUsers++
            Log.i(TAG, "onUserJoined uid=$uid remoteUsers=$remoteUsers mode=$mode")
            // 송신: 수신측 합류 → 발화 시작(2차 비프). 수신/중립 모드에선 무시.
            if (mode == EngineMode.TX && _state.value == PttState.CONNECTING && !readyFired) fireReady()
        }
        override fun onUserOffline(uid: Int, reason: Int) {
            if (remoteUsers > 0) remoteUsers--
            Log.i(TAG, "onUserOffline uid=$uid remoteUsers=$remoteUsers mode=$mode speakingUid=$speakingUid")
            // 수신/중립: 상대 이탈. 발화자 본인 이탈 시에만 오버톤. 들을 사람 0이면 워밍창 전환.
            if (mode == EngineMode.RX || mode == EngineMode.WARM) {
                if (uid == speakingUid && wasStarted) { wasStarted = false; speakingUid = 0; playOverTone() }
                if (remoteUsers <= 0) {
                    _state.value = PttState.IDLE
                    mode = EngineMode.WARM
                    scheduleLeave(CONV_WARM_MS)
                }
            }
        }
        // 수신/중립: 상대 오디오 STARTED 후 STOPPED(remote mute)면 발화 종료 → 오버톤 + 워밍창 재앵커.
        override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            if (mode != EngineMode.RX && mode != EngineMode.WARM) return // 송신 중 원격오디오 무시(오발/에코 차단)
            when (state) {
                Constants.REMOTE_AUDIO_STATE_STARTING, Constants.REMOTE_AUDIO_STATE_DECODING -> {
                    speakingUid = uid              // 실제 발화자 래칭
                    mode = EngineMode.RX            // WARM→RX 승격 (채널 이미 연결 → 즉시 청취)
                    wasStarted = true
                    leaveJob?.cancel()              // 청취 중 이탈 방지
                    // ★ 스피커 재설정 금지 — 이 콜백은 발화 중 상태전이마다 재발화 → 재생 중 라우트 리셋=끊김.
                    //   라우트는 onJoinChannelSuccess 1회 강제로 충분(sticky).
                    _state.value = PttState.LISTENING
                    Log.i(TAG, "수신: 발화 시작 감지 uid=$uid state=$state")
                }
                Constants.REMOTE_AUDIO_STATE_STOPPED -> {
                    Log.i(TAG, "수신 STOPPED uid=$uid reason=$reason speakingUid=$speakingUid wasStarted=$wasStarted")
                    // ★ 발화자 본인의 mute일 때만 종료 — 다른 broadcaster(비발화 수신자) 상태변화 오인 차단
                    if (uid == speakingUid && reason == Constants.REMOTE_AUDIO_REASON_REMOTE_MUTED && wasStarted) {
                        wasStarted = false
                        speakingUid = 0
                        Log.i(TAG, "수신: 발화 종료 감지 → 오버톤 + 워밍창 재앵커")
                        playOverTone()
                        _state.value = PttState.IDLE
                        mode = EngineMode.WARM
                        scheduleLeave(CONV_WARM_MS)
                    }
                }
            }
        }
    }

    /** 엔진 생성 — 살아있는 Context(Activity/Service)를 받아 생성 + appCtx 저장. */
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
            engine?.setDefaultAudioRoutetoSpeakerphone(true)
            engine?.setParameters("{\"che.audio.keep.audiosessiontype\":true}") // 실험: leave 후 OS 오디오세션 유지(콜드 재조인 지연↓)
            Log.i(TAG, "RtcEngine created")
        } catch (e: Exception) {
            Log.e(TAG, "RtcEngine 생성 실패", e)
        }
    }

    /** 앱 onResume 워밍업(엔진·appCtx 선설정 → 첫 발화 지연 제거). 채널 pre-join은 안 함. */
    fun prewarm(ctx: Context) = ensureEngine(ctx)

    /** 토큰 prewarm — office 확정(로그인)·onResume에서 미리 발급해 캐시(첫 발화 시 함수 호출 0). */
    fun prewarmToken(ctx: Context, provinceId: String, officeId: String) {
        ensureEngine(ctx)
        scope.launch { ensureToken(provinceId, officeId) }
    }

    /**
     * Agora 토큰 캐시 — office별 24h 토큰. 만료 1h 전까지 캐시 재사용(함수 호출 0), 임박/미스 시 발급.
     *  lazy 갱신: prewarm(로그인·onResume) + 발화/수신 직전 폴백. 주기 타이머 없음.
     */
    private suspend fun ensureToken(provinceId: String, officeId: String): Pair<String, String>? {
        val ctx = appCtx ?: return null
        val key = "${provinceId}_${officeId}"
        val prefs = ctx.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cachedToken = prefs.getString("token_$key", null)
        val cachedChannel = prefs.getString("channel_$key", null)
        val expireMs = prefs.getLong("expire_$key", 0L)
        if (cachedToken != null && cachedChannel != null && now < expireMs - TOKEN_REFRESH_MARGIN_MS) {
            return cachedToken to cachedChannel
        }
        return try {
            val tokenData = hashMapOf("regionId" to provinceId, "officeId" to officeId, "uid" to 0)
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

    /**
     * 효과음 1회(시작 1차/2차·종료 오버톤 공용). 채팅음 = R.raw.ptt_start.
     *  inCall=true(통화모드): ring/notification 억제되므로 통화 신호음 usage로 가청.
     */
    private fun playCue(inCall: Boolean) {
        val ctx = appCtx ?: return
        try {
            val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.ptt_start}")
            val ringtone = RingtoneManager.getRingtone(ctx, uri) ?: return
            if (inCall) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
        } catch (e: Exception) {
            Log.w(TAG, "playCue 실패", e)
        }
    }

    /** 수신: 상대 발화 종료 오버톤 — 통화모드(Agora active)라 inCall=true. */
    private fun playOverTone() = playCue(inCall = true)

    /**
     * 발화 시작(hold press). 포그라운드 전용(볼륨키)=Doze 아님 → 항상 라이브.
     *  - currentChannel != null (WARM/RX/TX-warm): leaveChannel 없이 즉시 TX 승격(재join 0).
     *  - currentChannel == null: cold-live 신규 join(~1.4초, 2차 비프가 가림).
     */
    fun startTransmit(ctx: Context, provinceId: String, cityId: String, officeId: String) {
        ensureEngine(ctx)
        val eng = engine ?: run { Log.e(TAG, "startTransmit: engine null"); return }
        leaveJob?.cancel()
        readyTimeoutJob?.cancel()
        readyFired = false

        if (currentChannel != null) {
            // ===== WARM/RX warm 재사용 — 마이크 토글로 즉시 TX 승격 =====
            _state.value = PttState.CONNECTING
            mode = EngineMode.TX
            playCue(inCall = true) // 1차 비프
            eng.updateChannelMediaOptions(ChannelMediaOptions().apply { publishMicrophoneTrack = true })
            eng.enableLocalAudio(true)
            eng.muteLocalAudioStream(true) // fireReady까지 무음(첫 음절 유실 방지)
            Log.i(TAG, "startTransmit: WARM 채널 재사용 ($currentChannel) — 즉시 TX 승격")
            scope.launch {
                scope.launch { sendWake(provinceId, cityId, officeId, currentChannel!!) } // fire-and-forget
                if (_state.value == PttState.CONNECTING && !readyFired) {
                    if (remoteUsers > 0) fireReady()
                    else readyTimeoutJob = scope.launch { delay(READY_TIMEOUT_MS); if (!readyFired) fireReady() }
                }
            }
            return
        }

        // ===== cold-live 신규 join =====
        _state.value = PttState.CONNECTING
        mode = EngineMode.TX
        playCue(inCall = true) // 1차 비프
        scope.launch {
            try {
                val tc = ensureToken(provinceId, officeId)  // 캐시 우선(함수 호출 0), 미스 시 폴백 발급
                if (tc == null) {
                    Log.e(TAG, "startTransmit: 토큰 확보 실패"); _state.value = PttState.IDLE; mode = EngineMode.NONE; return@launch
                }
                val (token, channelName) = tc
                val options = ChannelMediaOptions().apply {
                    channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                    publishMicrophoneTrack = true
                    autoSubscribeAudio = true
                }
                eng.enableLocalAudio(true)
                eng.joinChannel(token, channelName, 0, options)
                eng.muteLocalAudioStream(true) // 합류 전까지 무음
                currentChannel = channelName
                Log.i(TAG, "startTransmit: COLD-LIVE joinChannel $channelName")
                scope.launch { sendWake(provinceId, cityId, officeId, channelName) } // fire-and-forget
                if (_state.value == PttState.CONNECTING && !readyFired) {
                    if (remoteUsers > 0) fireReady()
                    else readyTimeoutJob = scope.launch { delay(READY_TIMEOUT_MS); if (!readyFired) fireReady() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startTransmit(live) 실패", e); _state.value = PttState.IDLE; mode = EngineMode.NONE
            }
        }
    }

    /** 연결 완료 → 2차 비프 + 마이크 라이브. 중복 1회. */
    private fun fireReady() {
        if (readyFired) return
        readyFired = true
        readyTimeoutJob?.cancel()
        _state.value = PttState.TALKING
        playCue(inCall = true) // 2차 비프
        engine?.muteLocalAudioStream(false) // 마이크 라이브
        Log.i(TAG, "fireReady: TALKING (mic live)")
    }

    /**
     * ptt_dispatch wake 수신 → 채널 fast-join(수신). PttReceiverService가 호출.
     *  - 송신 중(CONNECTING/TALKING): 수신 join 스킵(자기 발화 우선).
     *  - 이미 채널 유지중(WARM/RX/TX-warm): ★ 타이머 손대지 않음 — 살아있어 fallback 불필요(상대 STARTING이 RX 승격).
     *  - 채널 없음: RX 신규 join + WAKE_FALLBACK 방어.
     */
    fun onWake(ctx: Context, regionId: String, officeId: String, expectedChannel: String?) {
        ensureEngine(ctx)
        val eng = engine ?: run { Log.e(TAG, "onWake: engine null"); return }

        val s = _state.value
        if (s == PttState.CONNECTING || s == PttState.TALKING) {
            Log.i(TAG, "onWake: 송신 중 — 수신 join 스킵")
            return
        }
        if (currentChannel != null) {
            Log.i(TAG, "onWake: 채널 유지중 ($currentChannel) — 타이머 유지(상대 발화 STARTING 대기)")
            return
        }

        scope.launch {
            try {
                val tc = ensureToken(regionId, officeId)  // 캐시 우선 → 수신 join 가속(READY_TIMEOUT 완화 핵심)
                if (tc == null) { Log.e(TAG, "onWake: 토큰 확보 실패"); return@launch }
                val (token, channelName) = tc
                if (expectedChannel != null && expectedChannel != channelName) {
                    Log.w(TAG, "채널 불일치 wake=$expectedChannel token=$channelName")
                }
                val s2 = _state.value
                if (s2 == PttState.CONNECTING || s2 == PttState.TALKING) {
                    Log.i(TAG, "onWake: join 직전 송신 시작 — 취소"); return@launch
                }
                val options = ChannelMediaOptions().apply {
                    channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER // 합류 신호 위해 broadcaster(mic 미publish)
                    publishMicrophoneTrack = false
                    autoSubscribeAudio = true
                }
                eng.joinChannel(token, channelName, 0, options)
                eng.enableLocalAudio(false) // 마이크 미publish (수신전용)
                currentChannel = channelName
                wasStarted = false
                mode = EngineMode.RX
                _state.value = PttState.LISTENING
                Log.i(TAG, "onWake: joinChannel $channelName (수신)")
                scheduleLeave(WAKE_FALLBACK_MS) // 오디오 오면 STARTING이 취소, 안 오면 fallback leave
            } catch (e: Exception) {
                Log.e(TAG, "onWake 실패", e)
            }
        }
    }

    /** 워밍창/fallback 공용 leave 스케줄. 만료 시 채널 완전 정리(mode=NONE). */
    private fun scheduleLeave(delayMs: Long) {
        val eng = engine ?: return
        leaveJob?.cancel()
        leaveJob = scope.launch {
            delay(delayMs)
            eng.leaveChannel()
            currentChannel = null
            remoteUsers = 0
            wasStarted = false
            speakingUid = 0
            mode = EngineMode.NONE
            _state.value = PttState.IDLE
            Log.i(TAG, "scheduleLeave: leaveChannel (${delayMs / 1000}s)")
        }
    }

    private suspend fun sendWake(provinceId: String, cityId: String, officeId: String, channelName: String) {
        try {
            val wakeData = hashMapOf(
                "provinceId" to provinceId, "cityId" to cityId, "officeId" to officeId,
                "channelName" to channelName, "senderName" to "픽업기사",
            )
            functions.getHttpsCallable("sendPttWake").call(wakeData).await()
            Log.i(TAG, "sendPttWake 완료 channel=$channelName")
        } catch (e: Exception) {
            Log.e(TAG, "sendPttWake 실패", e)
        }
    }

    /**
     * 발화 종료(손 뗌). 라이브: 오버톤 + 마이크 발행 중지 + 워밍창(WARM) 전환.
     *  (콜드 녹음 recordPending 분기는 현재 도달 불가 — 보존.)
     */
    fun stopTransmit() {
        readyTimeoutJob?.cancel()

        if (recordPending) { // 도달 불가(보존)
            recordPending = false
            val result = pttRecorder.stop()
            playCue(inCall = false)
            _state.value = PttState.IDLE
            if (result != null && result.durationMs >= PttRecorder.MIN_RECORD_MS) {
                onColdVoiceMemo?.invoke(result.file, result.durationMs)
            } else {
                result?.file?.delete()
            }
            return
        }

        val eng = engine ?: run { _state.value = PttState.IDLE; mode = EngineMode.NONE; return }
        playCue(inCall = true) // 종료 오버톤
        eng.updateChannelMediaOptions(ChannelMediaOptions().apply { publishMicrophoneTrack = false })
        // ★ enableLocalAudio(false) 호출 금지 — 토글 시 remote audio playback pause 유발(WARM 수신 무음 회귀).
        //   mic 발행은 위 publishMicrophoneTrack=false로 충분. enableLocalAudio는 true 유지(수신 재생 정상).
        eng.muteLocalAudioStream(true)
        _state.value = PttState.IDLE
        mode = EngineMode.WARM // 채널 유지(워밍창) — 다음 발화/수신 즉시 라이브
        scheduleLeave(CONV_WARM_MS)
        Log.i(TAG, "stopTransmit: WARM 전환 (${CONV_WARM_MS / 1000}s 워밍창)")
    }

    fun release() {
        leaveJob?.cancel()
        readyTimeoutJob?.cancel()
        if (recordPending) {
            recordPending = false
            pttRecorder.cancel()
        }
        try {
            engine?.leaveChannel()
            RtcEngine.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "release 실패", e)
        }
        engine = null
        currentChannel = null
        remoteUsers = 0
        mode = EngineMode.NONE
        wasStarted = false
        _state.value = PttState.IDLE
    }
}
