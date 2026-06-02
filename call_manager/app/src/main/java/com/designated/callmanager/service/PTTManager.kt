package com.designated.callmanager.service

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.designated.callmanager.R
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

/** PTT 상태 — 배너/비프 구동. RECORDING=콜드 음성 메모 녹음 중. LISTENING=수신 중(타 단말 발화 청취). */
enum class PttState { IDLE, CONNECTING, TALKING, RECORDING, LISTENING }

/**
 * 매니저 PTT 송수신 매니저 (단일 RtcEngine 통합).
 *  송신(TX): 탭 후 hold-to-talk. press→1차 비프+CONNECTING(mic muted join)→수신측 합류→2차 비프+TALKING
 *           →손 뗌→오버톤+mute+~5초 후 leave(워밍창). 콜드(백그라운드 복귀)는 음성 메모 녹음.
 *  수신(RX): FCM ptt_dispatch wake → onWake fast-join(mic 미publish) → 타 단말 음성 스피커 재생 → 종료 오버톤.
 *  ★ Agora 엔진은 프로세스당 싱글톤이라 송/수신을 한 인스턴스로 통합. mode 플래그로 eventHandler 콜백 분기.
 *  RTM 미사용(FCM+RTC). 인스턴스는 CallManagerApplication 단일 소유(MainActivity·PttReceiverService 공유).
 */
class PTTManager {

    companion object {
        private const val TAG = "PTTManager"
        private const val APP_ID = "e5aae3aa18484cd2a1fed0018cfb15bd" // 공개값
        private const val LEAVE_DELAY_MS = 5_000L      // 종료 기준 워밍창
        private const val READY_TIMEOUT_MS = 3_000L    // 수신측 미합류 시 강제 발화
        private const val WAKE_FALLBACK_MS = 10_000L   // 수신 wake 후 오디오 미도착 fallback leave
    }

    /** 단일 엔진 용도 — TX(송신)/RX(수신). join 직전 설정, leave 시 NONE. */
    private enum class EngineMode { NONE, TX, RX }

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

    // 콜드 음성 메모 녹음 (백그라운드/화면오프 복귀 첫 송신만)
    private val pttRecorder = PttRecorder()
    private var recordPending = false

    /** 콜드 발화 종료 시 (녹음 파일, 길이ms) 전달 → 호출측이 채팅 음성 메모로 업로드. */
    var onColdVoiceMemo: ((File, Long) -> Unit)? = null
    /** RECORD_AUDIO 미허가로 녹음 불가 시 → 호출측이 권한 요청. */
    var onRecordUnavailable: (() -> Unit)? = null

    private val _state = MutableStateFlow(PttState.IDLE)
    val state: StateFlow<PttState> = _state.asStateFlow()
    val isTalking: Boolean get() = _state.value == PttState.TALKING
    val isRecording: Boolean get() = _state.value == PttState.RECORDING

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.i(TAG, "onJoinChannelSuccess channel=$channel uid=$uid elapsed=$elapsed mode=$mode")
        }
        override fun onError(err: Int) {
            Log.e(TAG, "Agora onError code=$err")
        }
        override fun onUserJoined(uid: Int, elapsed: Int) {
            remoteUsers++
            Log.i(TAG, "onUserJoined uid=$uid remoteUsers=$remoteUsers mode=$mode")
            // 송신: 수신측 합류 → 발화 시작(2차 비프). 수신 모드에선 무시.
            if (mode == EngineMode.TX && _state.value == PttState.CONNECTING && !readyFired) fireReady()
        }
        override fun onUserOffline(uid: Int, reason: Int) {
            if (remoteUsers > 0) remoteUsers--
            Log.i(TAG, "onUserOffline uid=$uid remoteUsers=$remoteUsers mode=$mode")
            // 수신: 송신자 이탈 → 오버톤(가드) + 즉시 leave (들을 사람 없음)
            if (mode == EngineMode.RX) {
                if (wasStarted) { wasStarted = false; playOverTone() }
                leaveJob?.cancel()
                engine?.leaveChannel()
                currentChannel = null
                mode = EngineMode.NONE
                _state.value = PttState.IDLE
            }
        }
        // 수신 전용: 송신자 오디오 STARTED 후 STOPPED(remote mute)면 발화 종료 → 오버톤 + 5초 재앵커.
        override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            if (mode != EngineMode.RX) return // 송신 중 원격오디오 무시 (오발/에코 차단)
            when (state) {
                Constants.REMOTE_AUDIO_STATE_STARTING, Constants.REMOTE_AUDIO_STATE_DECODING -> {
                    wasStarted = true
                    leaveJob?.cancel() // 청취 중 이탈 방지
                    _state.value = PttState.LISTENING
                    Log.i(TAG, "수신: 발화 시작 감지")
                }
                Constants.REMOTE_AUDIO_STATE_STOPPED -> {
                    if (reason == Constants.REMOTE_AUDIO_REASON_REMOTE_MUTED && wasStarted) {
                        wasStarted = false
                        Log.i(TAG, "수신: 발화 종료 감지 → 오버톤 + 5초 재앵커")
                        playOverTone()
                        _state.value = PttState.IDLE
                        scheduleLeave(LEAVE_DELAY_MS)
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
            // 무전기: 통화 오디오를 이어피스→스피커로 (송신 효과음·수신 음성 가청).
            engine?.setDefaultAudioRoutetoSpeakerphone(true)
            Log.i(TAG, "RtcEngine created")
        } catch (e: Exception) {
            Log.e(TAG, "RtcEngine 생성 실패", e)
        }
    }

    /** 앱 onResume 워밍업(엔진·appCtx 선설정 → 첫 발화 지연 제거). */
    fun prewarm(ctx: Context) = ensureEngine(ctx)

    /**
     * 효과음 1회(시작 1차/2차·종료 오버톤 공용). 채팅음 = R.raw.ptt_start 재사용.
     *  inCall=true(라이브, 통화모드): ring/notification 스트림이 억제되므로 통화 신호음 usage로 가청.
     *  inCall=false(음성 메모, 통화 없음): 기존 default(억제 없음, 음성메모 비프 정상).
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

    /** 수신: 송신자 발화 종료 오버톤 — 통화모드(Agora active)라 inCall=true로 가청. */
    private fun playOverTone() = playCue(inCall = true)

    /**
     * 발화 시작(hold press). ctx = 호출 시점 Activity.
     *  - 수신 중(LISTENING)이면 먼저 정리 후 송신 전환(단일 채널).
     *  - 백그라운드/화면오프 복귀 첫 송신(fromBackground=true) + 콜드: 음성 메모 녹음(누르면 바로 발화).
     *  - 포그라운드: 라이브. warm(currentChannel!=null)=즉시 / cold=신규 join(~수초).
     */
    fun startTransmit(ctx: Context, provinceId: String, cityId: String, officeId: String, fromBackground: Boolean) {
        ensureEngine(ctx)
        val eng = engine ?: run { Log.e(TAG, "startTransmit: engine null"); return }

        // 수신 중(또는 수신 fallback 채널 유지)이면 정리 후 송신 전환 (단일 채널)
        if (_state.value == PttState.LISTENING || (mode == EngineMode.RX && currentChannel != null)) {
            leaveJob?.cancel()
            eng.leaveChannel()
            currentChannel = null
            wasStarted = false
            mode = EngineMode.NONE
            Log.i(TAG, "startTransmit: 수신 정리 후 송신 전환")
        }

        leaveJob?.cancel()
        readyTimeoutJob?.cancel()
        readyFired = false

        if (currentChannel == null && fromBackground) {
            // ===== 백그라운드/화면오프 복귀 첫 송신 → 음성 메모 녹음 (누르면 바로 발화) =====
            val started = pttRecorder.start(ctx)
            if (!started) {
                Log.w(TAG, "startTransmit: 녹음 시작 불가(권한?) — 발화 취소")
                _state.value = PttState.IDLE
                onRecordUnavailable?.invoke()
                return
            }
            recordPending = true
            _state.value = PttState.RECORDING
            playCue(inCall = false) // 1차 비프 = "말하세요" (음성 메모, 통화 없음)
            // 수신측 Doze 선행 깨우기 — 녹음+업로드와 병렬화. fire-and-forget.
            scope.launch { sendPreWake(provinceId, cityId, officeId) }
            Log.i(TAG, "startTransmit: COLD(백그라운드 복귀) → 음성 메모 녹음")
            return
        }

        // ===== 포그라운드: 라이브 (warm 재사용 또는 cold 신규 join) =====
        _state.value = PttState.CONNECTING
        mode = EngineMode.TX
        playCue(inCall = true) // 1차 비프 (라이브, 통화모드)
        scope.launch {
            try {
                if (currentChannel != null) {
                    // 웜: 이미 join·mic muted 상태. 수신측 재-wake + 타이머 리셋.
                    Log.i(TAG, "startTransmit: WARM 채널 재사용 ($currentChannel)")
                    sendWake(provinceId, cityId, officeId, currentChannel!!)
                } else {
                    // 포그라운드 cold: 신규 join(라디오 깨어있어 ~수초). 음성 메모 미사용.
                    val tokenData = hashMapOf("regionId" to provinceId, "officeId" to officeId, "uid" to 0)
                    val result = functions.getHttpsCallable("generateAgoraToken").call(tokenData).await()
                    @Suppress("UNCHECKED_CAST")
                    val map = result.getData() as? Map<String, Any?> ?: emptyMap()
                    val token = map["token"] as? String
                    val channelName = map["channelName"] as? String
                    if (token.isNullOrBlank() || channelName.isNullOrBlank()) {
                        Log.e(TAG, "startTransmit: 토큰/채널 누락"); _state.value = PttState.IDLE; mode = EngineMode.NONE; return@launch
                    }
                    val options = ChannelMediaOptions().apply {
                        channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                        clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                        publishMicrophoneTrack = true
                        autoSubscribeAudio = true
                    }
                    eng.enableLocalAudio(true) // 수신 모드(enableLocalAudio false)에서 전환 대비 마이크 복구
                    eng.joinChannel(token, channelName, 0, options)
                    eng.muteLocalAudioStream(true) // 합류 전까지 무음(첫 음절 유실 방지)
                    currentChannel = channelName
                    Log.i(TAG, "startTransmit: COLD(포그라운드) joinChannel $channelName")
                    sendWake(provinceId, cityId, officeId, channelName)
                }
                // ready 결정: 수신측 이미 상주면 즉시, 아니면 3초 타임아웃(그 전 onUserJoined 오면 그쪽)
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
        playCue(inCall = true) // 2차 비프 (라이브, 통화모드)
        engine?.muteLocalAudioStream(false) // 마이크 라이브
        Log.i(TAG, "fireReady: TALKING (mic live)")
    }

    /**
     * ptt_dispatch wake 수신 → 채널 fast-join(수신). PttReceiverService가 호출.
     *  - 송신 중(CONNECTING/TALKING/RECORDING): 수신 join 스킵(자기 발화 우선).
     *  - 이미 채널 유지중(수신 or 송신 워밍창): fallback 타이머만 리셋(재join 안 함 = 에코 2차 차단).
     *  채널 = `${regionId}_${officeId}_ptt` (송신과 동일 사무실).
     */
    fun onWake(ctx: Context, regionId: String, officeId: String, expectedChannel: String?) {
        ensureEngine(ctx)
        val eng = engine ?: run { Log.e(TAG, "onWake: engine null"); return }

        val s = _state.value
        if (s == PttState.CONNECTING || s == PttState.TALKING || s == PttState.RECORDING) {
            Log.i(TAG, "onWake: 송신 중 — 수신 join 스킵")
            return
        }
        if (currentChannel != null) {
            Log.i(TAG, "onWake: 채널 유지중 ($currentChannel) — fallback 타이머 리셋")
            scheduleLeave(WAKE_FALLBACK_MS)
            return
        }

        scope.launch {
            try {
                val tokenData = hashMapOf("regionId" to regionId, "officeId" to officeId, "uid" to 0)
                val result = functions.getHttpsCallable("generateAgoraToken").call(tokenData).await()
                @Suppress("UNCHECKED_CAST")
                val map = result.getData() as? Map<String, Any?> ?: emptyMap()
                val token = map["token"] as? String
                val channelName = map["channelName"] as? String
                if (token.isNullOrBlank() || channelName.isNullOrBlank()) {
                    Log.e(TAG, "onWake: 토큰/채널 누락"); return@launch
                }
                if (expectedChannel != null && expectedChannel != channelName) {
                    Log.w(TAG, "채널 불일치 wake=$expectedChannel token=$channelName (사무실 설정 확인)")
                }
                // join 직전 송신이 시작됐으면 취소 (수신보다 자기 발화 우선)
                val s2 = _state.value
                if (s2 == PttState.CONNECTING || s2 == PttState.TALKING || s2 == PttState.RECORDING) {
                    Log.i(TAG, "onWake: join 직전 송신 시작 감지 — 취소"); return@launch
                }
                val options = ChannelMediaOptions().apply {
                    channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                    // BROADCASTER role(마이크 미publish): 합류 시 송신측 onUserJoined 즉시 울려 연결완료 신호(3초 타임아웃 회피).
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
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

    /** 송신 워밍창/수신 fallback 공용 leave 스케줄. 단일 채널이라 leaveJob 하나 공유. */
    private fun scheduleLeave(delayMs: Long) {
        val eng = engine ?: return
        leaveJob?.cancel()
        leaveJob = scope.launch {
            delay(delayMs)
            eng.leaveChannel()
            currentChannel = null
            remoteUsers = 0
            wasStarted = false
            mode = EngineMode.NONE
            _state.value = PttState.IDLE
            Log.i(TAG, "scheduleLeave: leaveChannel (${delayMs / 1000}s)")
        }
    }

    /** 콜드 녹음 시작 시 수신측 Doze 선행 깨우기 (Agora 미사용, 라디오 깨우기 목적). 실패 무해. */
    private suspend fun sendPreWake(provinceId: String, cityId: String, officeId: String) {
        try {
            val data = hashMapOf("provinceId" to provinceId, "cityId" to cityId, "officeId" to officeId)
            functions.getHttpsCallable("sendPttPreWake").call(data).await()
            Log.i(TAG, "sendPttPreWake 완료 (수신측 Doze 선행 깨우기)")
        } catch (e: Exception) {
            Log.w(TAG, "sendPttPreWake 실패(무해)", e)
        }
    }

    private suspend fun sendWake(provinceId: String, cityId: String, officeId: String, channelName: String) {
        try {
            val wakeData = hashMapOf(
                "provinceId" to provinceId, "cityId" to cityId, "officeId" to officeId,
                "channelName" to channelName, "senderName" to "매니저",
            )
            functions.getHttpsCallable("sendPttWake").call(wakeData).await()
            Log.i(TAG, "sendPttWake 완료 channel=$channelName")
        } catch (e: Exception) {
            Log.e(TAG, "sendPttWake 실패", e)
        }
    }

    /**
     * 발화 종료(손 뗌).
     *  - 콜드(녹음 중): 녹음 종료 → 음성 메모 콜백.
     *  - 웜(라이브): 오버톤 + mute + ~5초 후 leave.
     */
    fun stopTransmit() {
        readyTimeoutJob?.cancel()

        if (recordPending) {
            recordPending = false
            val result = pttRecorder.stop()
            playCue(inCall = false) // 종료 비프 (음성 메모, 통화 없음)
            _state.value = PttState.IDLE
            if (result != null && result.durationMs >= PttRecorder.MIN_RECORD_MS) {
                Log.i(TAG, "stopTransmit: COLD 음성 메모 (${result.durationMs}ms)")
                onColdVoiceMemo?.invoke(result.file, result.durationMs)
            } else {
                Log.i(TAG, "stopTransmit: COLD 녹음 너무 짧음/실패 — drop")
                result?.file?.delete()
            }
            // 웜업 없음 — 다음 포그라운드 송신은 cold-live(~수초)로 충분(본인 확인)
            return
        }

        // 웜(라이브) 종료
        val eng = engine ?: run { _state.value = PttState.IDLE; return }
        playCue(inCall = true) // 종료 오버톤 (라이브, 통화모드)
        eng.muteLocalAudioStream(true)
        _state.value = PttState.IDLE
        scheduleLeave(LEAVE_DELAY_MS) // mode=TX 유지(워밍창 재발화 warm), 만료 시 NONE
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
