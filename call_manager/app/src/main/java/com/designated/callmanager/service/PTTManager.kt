package com.designated.callmanager.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ServerValue
import com.google.firebase.functions.FirebaseFunctions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * PTT (Push-to-Talk) 매니저 클래스
 * 워키토키 앱의 검증된 로직을 기반으로 구현
 */
class PTTManager private constructor(
    private val context: Context,
    private val userType: String, // "call_manager", "pickup_driver", "driver"
    private val regionId: String,
    private val officeId: String
) {
    
    companion object {
        private const val TAG = "PTTManager"
        
        @Volatile
        private var INSTANCE: PTTManager? = null
        
        fun getInstance(
            context: Context,
            userType: String,
            regionId: String,
            officeId: String
        ): PTTManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PTTManager(
                    context.applicationContext,
                    userType,
                    regionId,
                    officeId
                ).also { INSTANCE = it }
            }
        }
    }
    
    // Agora 관련 변수
    private var rtcEngine: RtcEngine? = null
    private var isConnected = false
    private var isSpeaking = false
    private var currentChannelName: String? = null
    private var currentToken: String? = null
    private val appId = "e5aae3aa18484cd2a1fed0018cfb15bd"
    
    // PTT 상태 관리
    private var awaitingDoubleClickRelease = false
    private var currentVolume: Int = 50
    
    // Firebase 관련
    private lateinit var functions: FirebaseFunctions
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private val myUserId get() = auth.currentUser?.uid ?: "anonymous_${UUID.randomUUID().toString().substring(0, 6)}"
    
    // PTT 세션 동기화
    private var pttSessionRef: DatabaseReference? = null
    private var sessionListener: ValueEventListener? = null
    private var isSessionInitiator = false
    
    // Phase 1: 보안 강화된 토큰 관리
    private val tokenCache = TokenCache()
    private val secureTokenManager by lazy { SecureTokenManager(context) }
    private var smartConnectionManager: SmartConnectionManager? = null
    
    // Phase 2: 디바운싱 로직으로 비용 최적화
    private val pttDebouncer by lazy { PTTDebouncer(250L) }
    
    // SoundPool 관련
    private lateinit var soundPool: SoundPool
    private var soundIdPttEffect: Int = 0
    private var soundPoolLoaded = false
    
    // 콜백 인터페이스
    interface PTTCallback {
        fun onStatusChanged(status: String)
        fun onConnectionStateChanged(isConnected: Boolean)
        fun onSpeakingStateChanged(isSpeaking: Boolean)
        fun onError(error: String)
    }
    
    private var callback: PTTCallback? = null
    
    // Agora 이벤트 핸들러
    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Handler(Looper.getMainLooper()).post {
                isConnected = true
                Log.i(TAG, "Channel joined successfully: $channel, uid: $uid")
                callback?.onStatusChanged("채널 연결됨")
                callback?.onConnectionStateChanged(true)
                
                if (awaitingDoubleClickRelease) {
                    startSpeakingActual()
                }
            }
        }
        
        override fun onLeaveChannel(stats: RtcStats?) {
            Handler(Looper.getMainLooper()).post {
                isConnected = false
                Log.i(TAG, "Left channel")
                callback?.onStatusChanged("채널 퇴장함")
                callback?.onConnectionStateChanged(false)
            }
        }
        
        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.i(TAG, "User joined: $uid")
        }
        
        override fun onUserOffline(uid: Int, reason: Int) {
            Log.i(TAG, "User offline: $uid, reason: $reason")
        }
        
        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            var someoneIsSpeaking = false
            speakers?.forEach { speaker ->
                if (speaker.uid != 0 && speaker.volume > 10) {
                    someoneIsSpeaking = true
                    return@forEach
                }
            }
            
            if (isConnected && !isSpeaking) {
                Handler(Looper.getMainLooper()).post {
                    if (someoneIsSpeaking) {
                        callback?.onStatusChanged("수신 중...")
                    } else {
                        callback?.onStatusChanged("수신 대기 중...")
                    }
                }
            }
        }
        
        override fun onError(err: Int) {
            Handler(Looper.getMainLooper()).post {
                val errorMsg = "Agora 오류: ${agoraErrorToString(err)}"
                Log.e(TAG, "Agora error: $err - $errorMsg")
                callback?.onError(errorMsg)
                
                if (err == Constants.ERR_TOKEN_EXPIRED || err == Constants.ERR_INVALID_TOKEN) {
                    leaveAgoraChannel()
                    // TODO: 토큰 갱신 로직
                }
            }
        }
        
        override fun onConnectionStateChanged(state: Int, reason: Int) {
            Handler(Looper.getMainLooper()).post {
                Log.i(TAG, "Connection state: ${agoraConnStateToString(state)}, reason: ${agoraConnReasonToString(reason)}")
                
                val wasConnected = isConnected
                isConnected = (state == Constants.CONNECTION_STATE_CONNECTED || state == Constants.CONNECTION_STATE_RECONNECTING)
                
                if (!isConnected && wasConnected && isSpeaking) {
                    Log.w(TAG, "Connection lost while speaking, forcing PTT stop")
                    forceStopSpeaking()
                }
                
                when (state) {
                    Constants.CONNECTION_STATE_DISCONNECTED -> callback?.onStatusChanged("연결 끊김")
                    Constants.CONNECTION_STATE_CONNECTING -> callback?.onStatusChanged("연결 중...")
                    Constants.CONNECTION_STATE_CONNECTED -> callback?.onStatusChanged("연결됨")
                    Constants.CONNECTION_STATE_RECONNECTING -> callback?.onStatusChanged("재연결 중...")
                    Constants.CONNECTION_STATE_FAILED -> callback?.onStatusChanged("연결 실패")
                }
                
                callback?.onConnectionStateChanged(isConnected)
            }
        }
    }
    
    fun initialize(callback: PTTCallback?) {
        this.callback = callback
        
        if (!checkPermissions()) {
            callback?.onError("오디오 권한이 필요합니다")
            return
        }
        
        setupSoundPool()
        setupFirebase()
        initializeAgoraEngine()
        
        // 스마트 연결 관리자 초기화
        smartConnectionManager = SmartConnectionManager(this)
    }
    
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun setupSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()
            
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                soundPoolLoaded = true
                Log.i(TAG, "Sound loaded successfully: $sampleId")
            } else {
                Log.e(TAG, "Failed to load sound: $sampleId")
            }
        }
        
        // PTT 효과음 로드 (비프음)
        try {
            val resId = context.resources.getIdentifier("ptt_beep", "raw", context.packageName)
            if (resId != 0) {
                soundIdPttEffect = soundPool.load(context, resId, 1)
                Log.i(TAG, "PTT beep sound loaded successfully")
            } else {
                Log.w(TAG, "PTT beep sound file (ptt_beep.mp3) not found in res/raw/")
            }
        } catch (e: Exception) {
            Log.w(TAG, "PTT sound not available", e)
        }
    }
    
    private fun setupFirebase() {
        try {
            // Asia Northeast 3 (Seoul) 지역의 Firebase Functions 사용
            functions = FirebaseFunctions.getInstance("asia-northeast3")
            auth = FirebaseAuth.getInstance()
            database = FirebaseDatabase.getInstance("https://calldetector-5d61e-default-rtdb.firebaseio.com/")
            
            // PTT 세션 경로 설정
            pttSessionRef = database.getReference("ptt_sessions/${regionId}_${officeId}")
            
            // PTT 세션 변화 감지 리스너 설정
            setupPTTSessionListener()
            
            Log.i(TAG, "Firebase Functions (asia-northeast3), Auth and Database initialized")
            callback?.onStatusChanged("Firebase 준비됨")
            
        } catch (e: Exception) {
            Log.e(TAG, "Firebase setup failed", e)
            callback?.onError("Firebase 초기화 실패")
        }
    }
    
    private fun initializeAgoraEngine() {
        if (rtcEngine != null) {
            Log.w(TAG, "Agora Engine already initialized")
            return
        }
        
        try {
            val config = RtcEngineConfig()
            config.mContext = context
            config.mAppId = appId
            config.mEventHandler = rtcEventHandler
            // 아시아 지역 설정 추가
            config.mAreaCode = RtcEngineConfig.AreaCode.AREA_CODE_GLOB
            
            rtcEngine = RtcEngine.create(config)
            rtcEngine?.apply {
                setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
                enableAudio()
                setEnableSpeakerphone(true)
                setDefaultAudioRoutetoSpeakerphone(true)
                setAudioProfile(
                    Constants.AUDIO_PROFILE_DEFAULT,
                    Constants.AUDIO_SCENARIO_CHATROOM
                )
                adjustPlaybackSignalVolume(currentVolume)
                enableLocalAudio(false)
            }
            
            Log.i(TAG, "Agora Engine initialized successfully")
            callback?.onStatusChanged("엔진 준비됨")
            
        } catch (e: Exception) {
            Log.e(TAG, "Agora engine initialization failed", e)
            callback?.onError("Agora 엔진 초기화 실패")
        }
    }
    
    /**
     * PTT 버튼 눌림 처리 - Phase 2 디바운싱 적용
     * - 즉시 채널 참여 및 송신 시작
     * - 재연결 방지로 비용 최적화
     */
    fun handleVolumeDownPress(): Boolean {
        Log.i(TAG, "=== PTTManager.handleVolumeDownPress() CALLED (Phase 2) ===")
        Log.i(TAG, "Current state - isSpeaking: $isSpeaking, awaitingDoubleClickRelease: $awaitingDoubleClickRelease, isConnected: $isConnected")
        
        // 중복 호출 방지: 이미 송신 중이거나 처리 중인 경우 무시
        if (isSpeaking || awaitingDoubleClickRelease) {
            Log.i(TAG, "PTT already in progress, ignoring duplicate press")
            return true
        }
        
        // 스마트 연결 관리자에 알림
        smartConnectionManager?.onPTTPressed()
        
        Log.i(TAG, "PTT button pressed - starting PTT with Phase 2 optimization")
        playSound(soundIdPttEffect)
        awaitingDoubleClickRelease = true
        
        // Phase 2: 디바운서를 통한 스마트 채널 관리
        pttDebouncer.onPTTPressed {
            // 채널 연결 액션
            joinChannelAndSpeak()
        }
        
        // 송신은 즉시 시작 (사용자 피드백)
        if (isConnected) {
            startSpeakingActual()
        }
        
        return true
    }
    
    /**
     * PTT 버튼 뗌 처리 - Phase 2 디바운싱 적용
     * - 송신 즉시 중지 (사용자 피드백)
     * - 채널 해제는 250ms 후 (비용 최적화)
     */
    fun handleVolumeDownRelease(): Boolean {
        if (awaitingDoubleClickRelease || isSpeaking) {
            Log.i(TAG, "PTT button released - stopping PTT with Phase 2 optimization")
            
            // Phase 2: 송신 즉시 중지 (사용자 경험 유지)
            stopSpeakingOnly()  // 마이크만 끄고 채널은 유지
            awaitingDoubleClickRelease = false
            
            // Phase 2: 디바운서를 통한 지연된 채널 해제
            pttDebouncer.onPTTReleased {
                // 250ms 후 실행될 채널 해제 액션
                leaveAgoraChannel()
                callback?.onStatusChanged("PTT 연결 해제됨 (Phase 2 최적화)")
            }
            
            // 스마트 연결 관리자에 알림
            smartConnectionManager?.onPTTReleased()
        }
        return true
    }
    
    private suspend fun generateToken(): String? {
        // Phase 1: 보안 강화된 토큰 확인
        val secureToken = secureTokenManager.getToken(regionId, officeId, userType)
        if (secureToken != null) {
            currentChannelName = secureToken.channelName
            currentToken = secureToken.token
            Log.i(TAG, "Using secure cached token - Channel: $currentChannelName")
            callback?.onStatusChanged("보안 토큰 사용. 채널: $currentChannelName")
            return currentToken
        }
        
        // 캐시에 없으면 새로 생성
        return try {
            val data = hashMapOf(
                "regionId" to regionId,
                "officeId" to officeId,
                "userType" to userType
            )
            
            Log.i(TAG, "Generating new token with data: $data")
            
            val result = functions
                .getHttpsCallable("generateAgoraToken")
                .call(data)
                .await()
            
            Log.i(TAG, "Raw function result: ${result.data}")
            
            val resultData = result.data as? Map<String, Any>
            currentChannelName = resultData?.get("channelName") as? String
            currentToken = resultData?.get("token") as? String
            
            Log.i(TAG, "Parsed results - Channel: $currentChannelName, Token: $currentToken, Token length: ${currentToken?.length ?: 0}")
            
            if (resultData != null) {
                Log.i(TAG, "Full result data: appId=${resultData["appId"]}, testMode=${resultData["testMode"]}, expiresIn=${resultData["expiresIn"]}")
                
                // 테스트 모드이고 토큰이 비어있는지 확인
                val testMode = resultData["testMode"] as? Boolean ?: false
                if (testMode && currentToken.isNullOrBlank()) {
                    Log.w(TAG, "⚠️ 테스트 모드에서 빈 토큰 수신됨. Agora App Certificate가 설정되지 않았습니다.")
                    Log.w(TAG, "💡 해결방법: Firebase Console > Functions > Secrets에서 AGORA_APP_CERTIFICATE 설정")
                }
            }
            
            // Phase 1: 보안 토큰 저장
            if (!currentToken.isNullOrBlank() && !currentChannelName.isNullOrBlank()) {
                val secureToken = SecureTokenManager.SecureToken(
                    token = currentToken!!,
                    channelName = currentChannelName!!,
                    generatedAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000), // 24시간
                    regionId = regionId,
                    officeId = officeId,
                    userType = userType
                )
                secureTokenManager.saveToken(secureToken)
                Log.i(TAG, "Secure token saved successfully")
            }
            
            // 콜백을 통해 상태 업데이트
            callback?.onStatusChanged("토큰 생성 완료. 채널: $currentChannelName")
            
            currentToken
            
        } catch (e: Exception) {
            Log.e(TAG, "Token generation failed", e)
            callback?.onError("토큰 생성 실패: ${e.message}")
            null
        }
    }
    
    private fun joinChannelAndSpeak() {
        Log.i(TAG, "joinChannelAndSpeak() called - isConnected: $isConnected, isSpeaking: $isSpeaking")
        
        // 이미 송신 중인 경우 중복 처리 방지
        if (isSpeaking) {
            Log.w(TAG, "Already speaking, ignoring joinChannelAndSpeak call")
            return
        }
        
        if (isConnected) {
            Log.i(TAG, "Already connected to channel, starting to speak immediately")
            startSpeakingActual()
        } else if (rtcEngine != null) {
            // 비동기로 토큰 생성 후 채널 참여
            callback?.onStatusChanged("토큰 생성 중...")
            
            // 코루틴에서 토큰 생성
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val token = generateToken()
                    
                    if (token != null && currentChannelName != null) {
                        // 메인 스레드에서 채널 참여
                        Handler(Looper.getMainLooper()).post {
                            callback?.onStatusChanged("채널 연결 중...")
                            
                            Log.i(TAG, "About to join channel - Engine: ${rtcEngine != null}, Channel: $currentChannelName")
                            Log.i(TAG, "Token details - Length: ${token.length}, Token: ${if (token.isBlank()) "EMPTY" else "HAS_VALUE"}")
                            Log.i(TAG, "App ID: $appId")
                            
                            // 빈 토큰인 경우 특별 처리
                            if (token.isBlank()) {
                                Log.w(TAG, "Token is empty - this will likely cause JOIN_CHANNEL_REJECTED (-17)")
                                callback?.onError("토큰이 비어있습니다. Agora App Certificate 설정을 확인하세요.")
                                awaitingDoubleClickRelease = false
                                return@post
                            }
                            
                            val joinResult = rtcEngine?.joinChannel(token, currentChannelName, null, 0)
                            Log.i(TAG, "Join channel result: $joinResult, Channel: $currentChannelName, Token length: ${token.length}")
                            
                            if (joinResult != 0) {
                                val errorMsg = agoraErrorToString(joinResult ?: -1)
                                Log.e(TAG, "Failed to join channel: $errorMsg")
                                if (joinResult == -17) {
                                    Log.e(TAG, "JOIN_CHANNEL_REJECTED (-17) - 가능한 원인:")
                                    Log.e(TAG, "1. 빈 토큰으로 App Certificate가 활성화된 채널 참여 시도")
                                    Log.e(TAG, "2. 잘못된 App ID: $appId")
                                    Log.e(TAG, "3. 잘못된 채널명: $currentChannelName")
                                }
                                callback?.onError("채널 참여 실패: $errorMsg")
                                awaitingDoubleClickRelease = false
                            } else {
                                Log.i(TAG, "Join channel call successful, waiting for onJoinChannelSuccess callback")
                            }
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            callback?.onError("토큰 또는 채널명 생성 실패")
                            awaitingDoubleClickRelease = false
                        }
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        Log.e(TAG, "Token generation error", e)
                        callback?.onError("토큰 생성 중 오류: ${e.message}")
                        awaitingDoubleClickRelease = false
                    }
                }
            }
        } else {
            callback?.onError("Agora 엔진이 초기화되지 않았습니다")
            awaitingDoubleClickRelease = false
        }
    }
    
    private fun startSpeakingActual() {
        if (!isSpeaking) {
            isSpeaking = true
            val result = rtcEngine?.enableLocalAudio(true)
            Log.i(TAG, "Enable local audio result: $result")
            
            if (result == 0) {
                // 🎯 핵심 기능: PTT 세션 시작 알림
                createPTTSession()
                
                callback?.onStatusChanged("송신 중...")
                callback?.onSpeakingStateChanged(true)
                Log.i("PTT_SESSION", "✅ PTT 송신 시작 및 세션 생성됨")
            } else {
                Log.e(TAG, "Failed to enable local audio: $result")
                isSpeaking = false
                callback?.onError("송신 시작 실패")
            }
        }
    }
    
    /**
     * Phase 2: 송신만 중지 (채널은 유지)
     * - 사용자 피드백은 즉시 제공
     * - 채널은 디바운싱을 위해 유지
     */
    private fun stopSpeakingOnly() {
        if (isSpeaking) {
            isSpeaking = false
            val result = rtcEngine?.enableLocalAudio(false)
            Log.i(TAG, "Disable local audio result (Phase 2): $result")
            
            playSound(soundIdPttEffect)
            callback?.onSpeakingStateChanged(false)
            callback?.onStatusChanged("송신 중지 (채널 유지)")
            
            Log.i(TAG, "Speaking stopped, channel maintained for debouncing")
        }
    }
    
    /**
     * 기존 방식: 송신 중지 + 즉시 채널 해제
     */
    private fun stopSpeakingAndLeaveChannel() {
        if (isSpeaking) {
            isSpeaking = false
            val result = rtcEngine?.enableLocalAudio(false)
            Log.i(TAG, "Disable local audio result: $result")
            
            playSound(soundIdPttEffect)
            callback?.onSpeakingStateChanged(false)
            
            // 즉시 채널에서 나감 (Phase 1 방식)
            leaveAgoraChannel()
            callback?.onStatusChanged("PTT 연결 해제됨")
        }
    }
    
    private fun forceStopSpeaking() {
        if (isSpeaking) {
            Log.w(TAG, "Force stopping PTT")
            isSpeaking = false
            rtcEngine?.enableLocalAudio(false)
            playSound(soundIdPttEffect)
            callback?.onSpeakingStateChanged(false)
            awaitingDoubleClickRelease = false
            
            if (isConnected) {
                callback?.onStatusChanged("수신 대기 중...")
            } else {
                callback?.onStatusChanged("연결 끊김")
            }
        }
    }
    
    private fun leaveAgoraChannel() {
        if (isConnected) {
            Log.i(TAG, "Leaving Agora channel")
            val result = rtcEngine?.leaveChannel()
            Log.d(TAG, "Leave channel result: $result")
        }
    }
    
    fun adjustVolume(increase: Boolean) {
        if (!isConnected) {
            Log.w(TAG, "Cannot adjust volume - not connected")
            return
        }
        
        val step = 20
        val oldVolume = currentVolume
        
        currentVolume = if (increase) {
            (currentVolume + step).coerceAtMost(400)
        } else {
            (currentVolume - step).coerceAtLeast(0)
        }
        
        if (oldVolume != currentVolume) {
            val result = rtcEngine?.adjustPlaybackSignalVolume(currentVolume)
            Log.i(TAG, "Volume adjusted from $oldVolume to $currentVolume, result: $result")
        }
    }
    
    private fun playSound(soundId: Int) {
        if (soundPoolLoaded && soundId != 0) {
            soundPool.play(soundId, 0.7f, 0.7f, 1, 0, 1.0f)
        }
    }
    
    fun destroy() {
        Log.i(TAG, "Destroying PTT Manager")
        
        // 송신 중이면 중지
        if (isSpeaking) {
            forceStopSpeaking()
        }
        
        // Agora 정리
        leaveAgoraChannel()
        RtcEngine.destroy()
        rtcEngine = null
        
        // SoundPool 정리
        if (::soundPool.isInitialized) {
            soundPool.release()
        }
        
        // 스마트 연결 관리자 정리
        smartConnectionManager?.destroy()
        smartConnectionManager = null
        
        // Phase 2: 디바운서 정리
        pttDebouncer.destroy()
        
        // 토큰 캐시 정리
        tokenCache.clearCache()
        
        // PTT 세션 리스너 정리
        cleanupPTTSessionListener()
        
        INSTANCE = null
    }
    
    /**
     * PTT 세션 종료 (채널에서 완전히 나감)
     */
    fun disconnectPTT() {
        Log.i(TAG, "Disconnecting from PTT channel")
        
        if (isSpeaking) {
            forceStopSpeaking()
        }
        
        leaveAgoraChannel()
        callback?.onStatusChanged("PTT 연결 해제됨")
    }
    
    // 상태 조회 함수들
    fun isConnected() = isConnected
    fun isSpeaking() = isSpeaking
    fun getCurrentChannelName() = currentChannelName
    
    // 드라이버 상태 업데이트
    fun updateDriverStatus(status: SmartConnectionManager.DriverStatus) {
        smartConnectionManager?.updateDriverStatus(status)
    }
    
    // Phase 1 테스트용 함수들
    fun testSecureTokenSave(): Boolean {
        Log.i("PTT_PHASE1_TEST", "========== PHASE 1 TEST: SAVE TOKEN ==========")
        return try {
            val testToken = SecureTokenManager.SecureToken(
                token = "test_secure_token_${System.currentTimeMillis()}",
                channelName = "test_channel_${regionId}_${officeId}",
                generatedAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000),
                regionId = regionId,
                officeId = officeId,
                userType = userType
            )
            secureTokenManager.saveToken(testToken)
            Log.i("PTT_PHASE1_TEST", "✅ Test token save successful")
            true
        } catch (e: Exception) {
            Log.e("PTT_PHASE1_TEST", "❌ Test token save failed", e)
            false
        }
    }
    
    fun testSecureTokenRetrieve(): SecureTokenManager.SecureToken? {
        Log.i("PTT_PHASE1_TEST", "========== PHASE 1 TEST: RETRIEVE TOKEN ==========")
        return try {
            val token = secureTokenManager.getToken(regionId, officeId, userType)
            if (token != null) {
                Log.i("PTT_PHASE1_TEST", "✅ Test token retrieve successful")
                Log.i("PTT_PHASE1_TEST", "Token valid for: ${token.remainingHours()} hours")
            } else {
                Log.w("PTT_PHASE1_TEST", "⚠️ No token found")
            }
            token
        } catch (e: Exception) {
            Log.e("PTT_PHASE1_TEST", "❌ Test token retrieve failed", e)
            null
        }
    }
    
    fun testTokenExpiry(): Boolean {
        Log.i("PTT_PHASE1_TEST", "========== PHASE 1 TEST: TOKEN EXPIRY ==========")
        return try {
            val expiredToken = SecureTokenManager.SecureToken(
                token = "expired_test_token",
                channelName = "expired_channel",
                generatedAt = System.currentTimeMillis() - (25 * 60 * 60 * 1000), // 25시간 전
                expiresAt = System.currentTimeMillis() - (60 * 60 * 1000), // 1시간 전 만료
                regionId = "${regionId}_expired",
                officeId = "${officeId}_expired",
                userType = userType
            )
            
            // 만료된 토큰 저장
            secureTokenManager.saveToken(expiredToken)
            
            // 조회 시도 (만료되어 null 반환되어야 함)
            val retrieved = secureTokenManager.getToken("${regionId}_expired", "${officeId}_expired", userType)
            if (retrieved == null) {
                Log.i("PTT_PHASE1_TEST", "✅ Expired token correctly rejected")
                true
            } else {
                Log.e("PTT_PHASE1_TEST", "❌ Expired token incorrectly returned")
                false
            }
        } catch (e: Exception) {
            Log.e("PTT_PHASE1_TEST", "❌ Token expiry test failed", e)
            false
        }
    }
    
    // Phase 2 테스트용 함수들
    fun testDebouncing(): Boolean {
        Log.i("PTT_PHASE2_TEST", "========== PHASE 2 TEST: DEBOUNCING ==========")
        return pttDebouncer.testDebouncing()
    }
    
    fun testContinuousUsage(): Boolean {
        Log.i("PTT_PHASE2_TEST", "========== PHASE 2 TEST: CONTINUOUS USAGE ==========")
        return pttDebouncer.testContinuousUsage()
    }
    
    fun testCostSaving(): Boolean {
        Log.i("PTT_PHASE2_TEST", "========== PHASE 2 TEST: COST SAVING ==========")
        return try {
            // 테스트 시작 전 통계 리셋
            pttDebouncer.resetStats()
            
            // 시뮬레이션: 3번의 연속 PTT 사용
            var mockConnectCount = 0
            val mockConnect: () -> Unit = { mockConnectCount++ }
            val mockDisconnect: () -> Unit = { }
            
            // 1차 사용
            pttDebouncer.onPTTPressed(mockConnect)
            pttDebouncer.onPTTReleased(mockDisconnect)
            
            // 250ms 내 2차 사용 (재연결 방지)
            Thread.sleep(100)
            pttDebouncer.onPTTPressed(mockConnect) 
            pttDebouncer.onPTTReleased(mockDisconnect)
            
            // 250ms 내 3차 사용 (재연결 방지)
            Thread.sleep(100)
            pttDebouncer.onPTTPressed(mockConnect)
            pttDebouncer.onPTTReleased(mockDisconnect)
            
            // 결과 분석
            val expectedConnections = 1  // 3번 사용했지만 1번만 연결
            val actualConnections = mockConnectCount
            val costSavingRatio = ((3 - actualConnections).toFloat() / 3 * 100).toInt()
            
            Log.i("PTT_PHASE2_TEST", "Expected connections: $expectedConnections")
            Log.i("PTT_PHASE2_TEST", "Actual connections: $actualConnections")
            Log.i("PTT_PHASE2_TEST", "Cost saving: $costSavingRatio%")
            
            if (actualConnections == expectedConnections) {
                Log.i("PTT_PHASE2_TEST", "✅ Cost saving test successful - ${costSavingRatio}% 비용 절약")
                true
            } else {
                Log.e("PTT_PHASE2_TEST", "❌ Cost saving test failed")
                false
            }
            
        } catch (e: Exception) {
            Log.e("PTT_PHASE2_TEST", "❌ Cost saving test failed", e)
            false
        }
    }
    
    fun getDebounceStats(): PTTDebouncer.CostSavingStats {
        return pttDebouncer.getCostSavingStats()
    }
    
    // Helper functions
    private fun agoraErrorToString(err: Int): String {
        return when (err) {
            Constants.ERR_OK -> "No error"
            Constants.ERR_FAILED -> "General error"
            Constants.ERR_INVALID_ARGUMENT -> "Invalid argument"
            Constants.ERR_NOT_READY -> "Not ready"
            Constants.ERR_NOT_SUPPORTED -> "Not supported"
            Constants.ERR_REFUSED -> "Refused"
            Constants.ERR_BUFFER_TOO_SMALL -> "Buffer too small"
            Constants.ERR_NOT_INITIALIZED -> "Not initialized"
            Constants.ERR_INVALID_STATE -> "Invalid state"
            Constants.ERR_NO_PERMISSION -> "No permission"
            Constants.ERR_TIMEDOUT -> "Timed out"
            Constants.ERR_CANCELED -> "Canceled"
            Constants.ERR_TOO_OFTEN -> "Too often"
            Constants.ERR_BIND_SOCKET -> "Bind socket error"
            Constants.ERR_NET_DOWN -> "Network down"
            Constants.ERR_JOIN_CHANNEL_REJECTED -> "Join channel rejected"
            Constants.ERR_LEAVE_CHANNEL_REJECTED -> "Leave channel rejected"
            Constants.ERR_ALREADY_IN_USE -> "Already in use"
            Constants.ERR_INVALID_APP_ID -> "Invalid App ID"
            Constants.ERR_INVALID_CHANNEL_NAME -> "Invalid channel name"
            Constants.ERR_INVALID_TOKEN -> "Invalid token"
            Constants.ERR_TOKEN_EXPIRED -> "Token expired"
            -7 -> "ERR_INVALID_TOKEN / ERR_TOKEN_EXPIRED (Compatibility)"
            else -> "Unknown error ($err)"
        }
    }
    
    private fun agoraConnStateToString(state: Int): String {
        return when (state) {
            Constants.CONNECTION_STATE_DISCONNECTED -> "Disconnected"
            Constants.CONNECTION_STATE_CONNECTING -> "Connecting"
            Constants.CONNECTION_STATE_CONNECTED -> "Connected"
            Constants.CONNECTION_STATE_RECONNECTING -> "Reconnecting"
            Constants.CONNECTION_STATE_FAILED -> "Failed"
            else -> "Unknown state ($state)"
        }
    }
    
    private fun agoraConnReasonToString(reason: Int): String {
        return when (reason) {
            Constants.CONNECTION_CHANGED_CONNECTING -> "Connecting"
            Constants.CONNECTION_CHANGED_JOIN_SUCCESS -> "Join Success"
            Constants.CONNECTION_CHANGED_INTERRUPTED -> "Interrupted"
            Constants.CONNECTION_CHANGED_BANNED_BY_SERVER -> "Banned by Server"
            Constants.CONNECTION_CHANGED_JOIN_FAILED -> "Join Failed"
            Constants.CONNECTION_CHANGED_LEAVE_CHANNEL -> "Leave Channel"
            Constants.CONNECTION_CHANGED_INVALID_APP_ID -> "Invalid App ID"
            Constants.CONNECTION_CHANGED_INVALID_CHANNEL_NAME -> "Invalid Channel Name"
            Constants.CONNECTION_CHANGED_INVALID_TOKEN -> "Invalid Token"
            Constants.CONNECTION_CHANGED_TOKEN_EXPIRED -> "Token Expired"
            Constants.CONNECTION_CHANGED_REJECTED_BY_SERVER -> "Rejected by Server"
            Constants.CONNECTION_CHANGED_SETTING_PROXY_SERVER -> "Setting Proxy Server"
            Constants.CONNECTION_CHANGED_RENEW_TOKEN -> "Renew Token"
            Constants.CONNECTION_CHANGED_CLIENT_IP_ADDRESS_CHANGED -> "Client IP Address Changed"
            Constants.CONNECTION_CHANGED_KEEP_ALIVE_TIMEOUT -> "Keep Alive Timeout"
            Constants.CONNECTION_CHANGED_REJOIN_SUCCESS -> "Rejoin Success"
            Constants.CONNECTION_CHANGED_LOST -> "Connection Lost"
            else -> "Unknown reason ($reason)"
        }
    }
    
    // ========== PTT 세션 동기화 시스템 ==========
    
    /**
     * PTT 세션 변화 감지 리스너 설정
     */
    private fun setupPTTSessionListener() {
        sessionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sessionData = snapshot.value as? Map<String, Any>
                
                if (sessionData != null) {
                    val active = sessionData["active"] as? Boolean ?: false
                    val initiator = sessionData["initiator"] as? String
                    val channelName = sessionData["channel_name"] as? String
                    val token = sessionData["token"] as? String
                    val startedAt = sessionData["started_at"] as? Long
                    
                    Log.i("PTT_SESSION", "📡 세션 변화 감지: active=$active, initiator=$initiator, channel=$channelName")
                    
                    if (active && initiator != userType && !isConnected && channelName != null && token != null) {
                        Log.i("PTT_SESSION", "🎯 자동 채널 참여 시작 - 다른 앱($initiator)이 PTT 시작")
                        autoJoinPTTChannel(channelName, token)
                    } else if (!active && isConnected && !isSpeaking) {
                        Log.i("PTT_SESSION", "🚪 자동 채널 퇴장 - PTT 세션 종료됨")
                        autoLeavePTTChannel()
                    }
                } else {
                    Log.d("PTT_SESSION", "📭 세션 데이터 없음 또는 삭제됨")
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("PTT_SESSION", "❌ 세션 리스너 오류: ${error.message}")
            }
        }
        
        pttSessionRef?.addValueEventListener(sessionListener!!)
        Log.i("PTT_SESSION", "🔔 PTT 세션 리스너 설정 완료")
    }
    
    /**
     * PTT 세션 생성 (송신 시작 시)
     */
    private fun createPTTSession() {
        if (currentChannelName == null || currentToken == null) {
            Log.w("PTT_SESSION", "⚠️ 채널명 또는 토큰이 없어 세션 생성 불가")
            return
        }
        
        try {
            isSessionInitiator = true
            val sessionData = mapOf(
                "active" to true,
                "initiator" to userType,
                "channel_name" to currentChannelName,
                "token" to currentToken,
                "started_at" to ServerValue.TIMESTAMP,
                "participants" to mapOf(
                    myUserId to mapOf(
                        "app_type" to userType,
                        "joined_at" to ServerValue.TIMESTAMP
                    )
                )
            )
            
            pttSessionRef?.setValue(sessionData)?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i("PTT_SESSION", "✅ PTT 세션 생성 성공 - 다른 앱들이 자동 참여할 예정")
                } else {
                    Log.e("PTT_SESSION", "❌ PTT 세션 생성 실패: ${task.exception?.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e("PTT_SESSION", "💥 PTT 세션 생성 중 오류", e)
        }
    }
    
    /**
     * PTT 세션 종료 (송신 중지 시)
     */
    private fun endPTTSession() {
        if (!isSessionInitiator) {
            return
        }
        
        try {
            val sessionData = mapOf(
                "active" to false,
                "ended_at" to ServerValue.TIMESTAMP
            )
            
            pttSessionRef?.updateChildren(sessionData)?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i("PTT_SESSION", "✅ PTT 세션 종료 성공 - 다른 앱들이 자동 퇴장할 예정")
                } else {
                    Log.e("PTT_SESSION", "❌ PTT 세션 종료 실패: ${task.exception?.message}")
                }
            }
            
            isSessionInitiator = false
            
        } catch (e: Exception) {
            Log.e("PTT_SESSION", "💥 PTT 세션 종료 중 오류", e)
        }
    }
    
    /**
     * 다른 앱이 시작한 PTT 채널에 자동 참여
     */
    private fun autoJoinPTTChannel(channelName: String, token: String) {
        Log.i("PTT_SESSION", "🚀 자동 PTT 채널 참여 시작: $channelName")
        
        if (rtcEngine == null) {
            Log.e("PTT_SESSION", "❌ Agora 엔진이 초기화되지 않음")
            return
        }
        
        try {
            // 현재 토큰 및 채널명 업데이트
            currentChannelName = channelName
            currentToken = token
            
            // 채널 참여
            val joinResult = rtcEngine?.joinChannel(token, channelName, null, 0)
            Log.i("PTT_SESSION", "📞 자동 채널 참여 요청: $joinResult")
            
            if (joinResult == 0) {
                callback?.onStatusChanged("자동 PTT 참여 중...")
            } else {
                val errorMsg = agoraErrorToString(joinResult ?: -1)
                Log.e("PTT_SESSION", "❌ 자동 채널 참여 실패: $errorMsg")
                callback?.onError("자동 PTT 참여 실패: $errorMsg")
            }
            
        } catch (e: Exception) {
            Log.e("PTT_SESSION", "💥 자동 채널 참여 중 오류", e)
            callback?.onError("자동 PTT 참여 오류: ${e.message}")
        }
    }
    
    /**
     * PTT 세션 종료 시 자동 채널 퇴장
     */
    private fun autoLeavePTTChannel() {
        Log.i("PTT_SESSION", "🚪 자동 PTT 채널 퇴장 시작")
        
        try {
            leaveAgoraChannel()
            callback?.onStatusChanged("자동 PTT 퇴장 완료")
            
        } catch (e: Exception) {
            Log.e("PTT_SESSION", "💥 자동 채널 퇴장 중 오류", e)
        }
    }
    
    /**
     * PTT 세션 리스너 정리
     */
    private fun cleanupPTTSessionListener() {
        sessionListener?.let { listener ->
            pttSessionRef?.removeEventListener(listener)
            Log.i("PTT_SESSION", "🧹 PTT 세션 리스너 정리 완료")
        }
        sessionListener = null
        isSessionInitiator = false
    }
}