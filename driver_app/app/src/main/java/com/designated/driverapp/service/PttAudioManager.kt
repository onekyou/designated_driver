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
    }

    private val functions = Firebase.functions("asia-northeast3")
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var engine: RtcEngine? = null
    private var appCtx: Context? = null
    private var currentChannel: String? = null
    private var leaveJob: Job? = null
    private var wasStarted = false // 매니저 오디오 STARTED를 본 뒤에만 오버톤(조기 오발 가드)

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.i(TAG, "수신 join 성공 channel=$channel uid=$uid elapsed=$elapsed")
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
                    wasStarted = true
                    leaveJob?.cancel() // 청취 중 이탈 방지
                    Log.i(TAG, "매니저 발화 시작 감지")
                }
                Constants.REMOTE_AUDIO_STATE_STOPPED -> {
                    if (reason == Constants.REMOTE_AUDIO_REASON_REMOTE_MUTED && wasStarted) {
                        wasStarted = false
                        Log.i(TAG, "매니저 발화 종료 감지 → 오버톤 + 5초 재앵커")
                        playOverTone()
                        engine?.let { scheduleLeave(it, LEAVE_DELAY_MS) }
                    }
                }
            }
        }
        // 매니저 채널 이탈 — 오버톤(가드) + 즉시 leave(들을 사람 없음)
        override fun onUserOffline(uid: Int, reason: Int) {
            Log.i(TAG, "송신자 이탈 uid=$uid")
            if (wasStarted) { wasStarted = false; playOverTone() }
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
            Log.i(TAG, "RtcEngine created (수신)")
        } catch (e: Exception) {
            Log.e(TAG, "RtcEngine 생성 실패", e)
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
        ensureEngine(ctx)
        val eng = engine ?: run { Log.e(TAG, "onWake: engine null"); return }

        if (currentChannel != null) {
            Log.i(TAG, "onWake: 채널 유지중 ($currentChannel) — fallback 타이머 리셋")
            scheduleLeave(eng, WAKE_FALLBACK_MS)
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
