package com.designated.callmanager.service

import android.content.Context
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

/** PTT 송신 상태 — 배너/비프 구동. RECORDING=콜드 음성 메모 녹음 중. */
enum class PttState { IDLE, CONNECTING, TALKING, RECORDING }

/**
 * 매니저 PTT 송신 매니저.
 *  모델: 탭 후 hold-to-talk. press→1차 비프+CONNECTING(마이크 muted join)→수신측 합류(onUserJoined/즉시)
 *  →2차 비프+TALKING(마이크 라이브)→손 뗌→오버톤+mute+~5초 후 leave(종료 기준 워밍창).
 *  지연은 2차 비프가 가림(연결 완료 시점에 울림). RTM 미사용(FCM+RTC).
 */
class PTTManager {

    companion object {
        private const val TAG = "PTTManager"
        private const val APP_ID = "e5aae3aa18484cd2a1fed0018cfb15bd" // 공개값
        private const val LEAVE_DELAY_MS = 5_000L      // 종료 기준 워밍창
        private const val READY_TIMEOUT_MS = 3_000L    // 수신측 미합류 시 강제 발화
    }

    private val functions = Firebase.functions("asia-northeast3")
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var engine: RtcEngine? = null
    private var appCtx: Context? = null
    private var currentChannel: String? = null
    private var remoteUsers = 0
    private var readyFired = false
    private var leaveJob: Job? = null
    private var readyTimeoutJob: Job? = null

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
            Log.i(TAG, "onJoinChannelSuccess channel=$channel uid=$uid elapsed=$elapsed")
        }
        override fun onError(err: Int) {
            Log.e(TAG, "Agora onError code=$err")
        }
        override fun onUserJoined(uid: Int, elapsed: Int) {
            remoteUsers++
            Log.i(TAG, "onUserJoined uid=$uid remoteUsers=$remoteUsers")
            if (_state.value == PttState.CONNECTING && !readyFired) fireReady()
        }
        override fun onUserOffline(uid: Int, reason: Int) {
            if (remoteUsers > 0) remoteUsers--
            Log.i(TAG, "onUserOffline uid=$uid remoteUsers=$remoteUsers")
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
            Log.i(TAG, "RtcEngine created")
        } catch (e: Exception) {
            Log.e(TAG, "RtcEngine 생성 실패", e)
        }
    }

    /** 앱 onResume 워밍업(엔진·appCtx 선설정 → 첫 발화 지연 제거). */
    fun prewarm(ctx: Context) = ensureEngine(ctx)

    /** 효과음 1회(시작 1차/2차·종료 오버톤 공용). 채팅음 = R.raw.ptt_start 재사용. */
    private fun playCue() {
        val ctx = appCtx ?: return
        try {
            val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.ptt_start}")
            RingtoneManager.getRingtone(ctx, uri)?.play()
        } catch (e: Exception) {
            Log.w(TAG, "playCue 실패", e)
        }
    }

    /**
     * 발화 시작(hold press). ctx = 호출 시점 Activity.
     *  - 백그라운드/화면오프 복귀 첫 송신(fromBackground=true) + 콜드: 음성 메모 녹음(누르면 바로 발화, 5초 hold 폐기).
     *  - 포그라운드: 라이브. warm(currentChannel!=null)=즉시 / cold=신규 join(~수초, 포그라운드라 견딜만).
     */
    fun startTransmit(ctx: Context, provinceId: String, cityId: String, officeId: String, fromBackground: Boolean) {
        ensureEngine(ctx)
        val eng = engine ?: run { Log.e(TAG, "startTransmit: engine null"); return }
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
            playCue() // 1차 비프 = "말하세요"
            Log.i(TAG, "startTransmit: COLD(백그라운드 복귀) → 음성 메모 녹음")
            return
        }

        // ===== 포그라운드: 라이브 (warm 재사용 또는 cold 신규 join) =====
        _state.value = PttState.CONNECTING
        playCue() // 1차 비프
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
                        Log.e(TAG, "startTransmit: 토큰/채널 누락"); _state.value = PttState.IDLE; return@launch
                    }
                    val options = ChannelMediaOptions().apply {
                        channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                        clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                        publishMicrophoneTrack = true
                        autoSubscribeAudio = true
                    }
                    eng.joinChannel(token, channelName, 0, options)
                    eng.muteLocalAudioStream(true) // 합류 전까지 무음(첫 음절 유실 방지)
                    currentChannel = channelName
                    Log.i(TAG, "startTransmit: COLD(포그라운드) joinChannel $channelName")
                    sendWake(provinceId, cityId, officeId, channelName)
                }
                // ready 결정: 수신측 이미 상주면 즉시, 아니면 3초 타임아웃(그 전 onUserJoined가 오면 그쪽)
                if (_state.value == PttState.CONNECTING && !readyFired) {
                    if (remoteUsers > 0) fireReady()
                    else readyTimeoutJob = scope.launch { delay(READY_TIMEOUT_MS); if (!readyFired) fireReady() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startTransmit(live) 실패", e); _state.value = PttState.IDLE
            }
        }
    }

    /** 연결 완료 → 2차 비프 + 마이크 라이브. 중복 1회. */
    private fun fireReady() {
        if (readyFired) return
        readyFired = true
        readyTimeoutJob?.cancel()
        _state.value = PttState.TALKING
        playCue() // 2차 비프
        engine?.muteLocalAudioStream(false) // 마이크 라이브
        Log.i(TAG, "fireReady: TALKING (mic live)")
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
            playCue() // 종료 비프
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
        playCue() // 종료 오버톤
        eng.muteLocalAudioStream(true)
        _state.value = PttState.IDLE
        leaveJob?.cancel()
        leaveJob = scope.launch {
            delay(LEAVE_DELAY_MS)
            eng.leaveChannel()
            currentChannel = null
            remoteUsers = 0
            Log.i(TAG, "stopTransmit: leaveChannel (워밍창 만료)")
        }
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
        _state.value = PttState.IDLE
    }
}
