# 볼륨 버튼 PTT 시스템 구현계획서 v7 (실무판)

## 개요

본 계획서는 **볼륨 버튼 PTT** 기능에 집중하여 대리운전 픽업 기사용 무전 시스템을 구축하는 실무적 접근을 제시합니다. 음성/햅틱 피드백은 차후 필요시 추가하며, 현재는 **안정적인 볼륨 버튼 동작**과 **RTM 2.x 기반 시그널링**에 집중합니다.

### 핵심 목표
1. **볼륨 버튼으로 PTT 제어**: 운전 중 안전한 무전 사용
2. **RTM 2.x 정식 마이그레이션**: 안정적인 시그널링 구현
3. **단계별 점진적 구현**: 테스트 가능한 최소 단위로 진행
4. **불필요한 복잡성 제거**: 현재 필요한 기능만 구현

---

## 1. 기술 스택 (단순화)

### 1.1 핵심 SDK
```gradle
dependencies {
    // Agora RTC SDK (음성 채널)
    implementation 'io.agora.rtc:full-sdk:4.2.3'
    
    // Agora RTM SDK 2.x (시그널링)
    implementation 'io.agora:agora-rtm:2.2.4'
    
    // Kotlin Serialization (메시지 직렬화)
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0'
    
    // 코루틴
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 1.2 권한 (최소 필요)
```xml
<!-- PTT 필수 권한 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- 볼륨 버튼 PTT -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

---

## 2. Phase 1: 볼륨 버튼 PTT 핵심 구현

### 2.1 볼륨 버튼 감지 서비스
```kotlin
/**
 * 볼륨 버튼 PTT 접근성 서비스
 * - 볼륨키 Down: PTT 시작
 * - 볼륨키 Up: PTT 종료
 */
class PTTAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "PTTAccessibilityService"
    }
    
    private var pttController: PTTController? = null
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        pttController = PTTController.getInstance()
        Log.i(TAG, "PTT Accessibility Service connected")
    }
    
    override fun onKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                handleVolumeKey(event)
                true // 시스템 볼륨 동작 차단
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                handleVolumeKey(event)  
                true // 시스템 볼륨 동작 차단
            }
            else -> false
        }
    }
    
    private fun handleVolumeKey(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // PTT 시작
                pttController?.startPTT()
                Log.d(TAG, "PTT Started via Volume Key")
            }
            KeyEvent.ACTION_UP -> {
                // PTT 종료
                pttController?.stopPTT()
                Log.d(TAG, "PTT Stopped via Volume Key")
            }
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 필요시 구현
    }
    
    override fun onInterrupt() {
        // 서비스 중단 시 정리
    }
}
```

### 2.2 PTT 컨트롤러 (단순화)
```kotlin
/**
 * PTT 핵심 로직 컨트롤러
 */
class PTTController private constructor() {
    
    companion object {
        private const val TAG = "PTTController"
        
        @Volatile
        private var INSTANCE: PTTController? = null
        
        fun getInstance(): PTTController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PTTController().also { INSTANCE = it }
            }
        }
    }
    
    private var rtmManager: RTM2Manager? = null
    private var voiceManager: VoiceChannelManager? = null
    private var isPTTActive = false
    
    /**
     * PTT 초기화
     */
    suspend fun initialize(context: Context): Boolean {
        return try {
            // RTM 매니저 초기화
            rtmManager = RTM2Manager(context)
            rtmManager?.initialize(
                appId = BuildConfig.AGORA_APP_ID,
                userId = getUserId(),
                token = getToken()
            )
            
            // Voice 매니저 초기화
            voiceManager = VoiceChannelManager(context)
            voiceManager?.initialize(BuildConfig.AGORA_APP_ID)
            
            Log.i(TAG, "PTT Controller initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "PTT Controller initialization failed", e)
            false
        }
    }
    
    /**
     * PTT 시작
     */
    fun startPTT() {
        if (isPTTActive) {
            Log.w(TAG, "PTT already active, ignoring start request")
            return
        }
        
        try {
            isPTTActive = true
            
            // 1. RTM 신호 전송 (PTT 시작 알림)
            CoroutineScope(Dispatchers.IO).launch {
                rtmManager?.sendPTTSignal(PTTSignalType.PTT_START)
            }
            
            // 2. 음성 채널 참여 및 송신 시작
            voiceManager?.startTransmission()
            
            Log.i(TAG, "PTT transmission started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PTT", e)
            isPTTActive = false
        }
    }
    
    /**
     * PTT 종료
     */
    fun stopPTT() {
        if (!isPTTActive) {
            Log.w(TAG, "PTT not active, ignoring stop request")
            return
        }
        
        try {
            isPTTActive = false
            
            // 1. 음성 송신 중지
            voiceManager?.stopTransmission()
            
            // 2. RTM 신호 전송 (PTT 종료 알림)
            CoroutineScope(Dispatchers.IO).launch {
                rtmManager?.sendPTTSignal(PTTSignalType.PTT_STOP)
            }
            
            Log.i(TAG, "PTT transmission stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop PTT", e)
        }
    }
    
    private fun getUserId(): String {
        // Firebase Auth 또는 SharedPreferences에서 사용자 ID 가져오기
        return "driver_${System.currentTimeMillis()}"
    }
    
    private fun getToken(): String? {
        // 서버에서 RTM 토큰 받기 (차후 구현)
        return null // 개발 단계에서는 null 허용
    }
}
```

### 2.3 RTM 2.x 매니저 (핵심만)
```kotlin
/**
 * RTM 2.x 기반 시그널링 매니저
 */
class RTM2Manager(private val context: Context) {
    
    companion object {
        private const val TAG = "RTM2Manager"
        private const val CHANNEL_NAME = "ptt_channel"
    }
    
    private var rtmClient: RtmClient? = null
    private var isConnected = false
    
    /**
     * RTM 초기화
     */
    suspend fun initialize(appId: String, userId: String, token: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val config = RtmConfig.Builder(appId, userId)
                    .eventListener(createEventListener())
                    .build()
                
                rtmClient = RtmClient.create(config)
                
                // 로그인
                login(token)
                
                // 채널 구독
                subscribeToChannel(CHANNEL_NAME)
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "RTM initialization failed", e)
                false
            }
        }
    }
    
    /**
     * PTT 신호 전송
     */
    suspend fun sendPTTSignal(signalType: PTTSignalType) {
        if (!isConnected) {
            Log.w(TAG, "RTM not connected, cannot send signal")
            return
        }
        
        try {
            val signal = PTTSignal(
                type = signalType,
                senderId = getCurrentUserId(),
                timestamp = System.currentTimeMillis()
            )
            
            val message = Json.encodeToString(signal)
            
            rtmClient?.publish(CHANNEL_NAME, message, PublishOptions()) { result ->
                if (result.errorCode == 0) {
                    Log.d(TAG, "PTT signal sent: $signalType")
                } else {
                    Log.e(TAG, "Failed to send PTT signal: ${result.errorCode}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending PTT signal", e)
        }
    }
    
    private fun login(token: String?) {
        rtmClient?.login(token) { result ->
            if (result.errorCode == 0) {
                isConnected = true
                Log.i(TAG, "RTM login successful")
            } else {
                Log.e(TAG, "RTM login failed: ${result.errorCode}")
            }
        }
    }
    
    private suspend fun subscribeToChannel(channelName: String) {
        rtmClient?.subscribe(channelName, SubscribeOptions()) { result ->
            if (result.errorCode == 0) {
                Log.i(TAG, "Subscribed to channel: $channelName")
            } else {
                Log.e(TAG, "Failed to subscribe: ${result.errorCode}")
            }
        }
    }
    
    private fun createEventListener(): RtmEventListener {
        return object : RtmEventListener {
            override fun onMessageEvent(event: MessageEvent?) {
                event?.message?.let { message ->
                    try {
                        val signal = Json.decodeFromString<PTTSignal>(message)
                        handleIncomingSignal(signal)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing PTT signal", e)
                    }
                }
            }
            
            override fun onConnectionStateChanged(
                channelName: String?, 
                state: RtmConnectionState?, 
                reason: RtmConnectionChangeReason?
            ) {
                Log.d(TAG, "Connection state: $state, reason: $reason")
                isConnected = (state == RtmConnectionState.CONNECTED)
            }
        }
    }
    
    private fun handleIncomingSignal(signal: PTTSignal) {
        Log.d(TAG, "Received PTT signal: ${signal.type} from ${signal.senderId}")
        // UI 업데이트 또는 다른 처리 로직
    }
    
    private fun getCurrentUserId(): String {
        // 현재 사용자 ID 반환
        return "current_user"
    }
}
```

### 2.4 데이터 모델 (최소)
```kotlin
@Serializable
enum class PTTSignalType {
    PTT_START,
    PTT_STOP,
    EMERGENCY_CALL
}

@Serializable
data class PTTSignal(
    val type: PTTSignalType,
    val senderId: String,
    val timestamp: Long
)
```

---

## 3. Phase 2: 음성 채널 연동

### 3.1 음성 채널 매니저 (기본)
```kotlin
/**
 * Agora RTC 음성 채널 매니저
 */
class VoiceChannelManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceChannelManager"
        private const val CHANNEL_NAME = "voice_channel"
    }
    
    private var rtcEngine: RtcEngine? = null
    private var isInChannel = false
    
    fun initialize(appId: String): Boolean {
        return try {
            val config = RtcEngineConfig()
            config.mContext = context
            config.mAppId = appId
            config.mEventHandler = createEventHandler()
            
            rtcEngine = RtcEngine.create(config)
            rtcEngine?.enableAudio()
            
            Log.i(TAG, "Voice channel manager initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize voice manager", e)
            false
        }
    }
    
    fun startTransmission() {
        if (!isInChannel) {
            joinChannel()
        }
        
        // 마이크 활성화
        rtcEngine?.enableLocalAudio(true)
        rtcEngine?.muteLocalAudioStream(false)
    }
    
    fun stopTransmission() {
        // 마이크 비활성화
        rtcEngine?.muteLocalAudioStream(true)
    }
    
    private fun joinChannel() {
        rtcEngine?.joinChannel(null, CHANNEL_NAME, null, 0)
    }
    
    private fun createEventHandler(): IRtcEngineEventHandler {
        return object : IRtcEngineEventHandler() {
            override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                isInChannel = true
                Log.i(TAG, "Joined voice channel: $channel")
            }
            
            override fun onLeaveChannel(stats: RtcStats?) {
                isInChannel = false
                Log.i(TAG, "Left voice channel")
            }
        }
    }
}
```

---

## 4. 구현 단계 (실무적)

### Phase 1: 볼륨 버튼 감지 (1주)
- [ ] PTTAccessibilityService 구현
- [ ] 접근성 서비스 설정 XML
- [ ] 볼륨키 이벤트 감지 테스트
- [ ] AndroidManifest 권한 설정

### Phase 2: RTM 2.x 연동 (1주)  
- [ ] RTM2Manager 구현
- [ ] 시그널 송수신 테스트
- [ ] 토큰 관리 기본 구조
- [ ] 에러 처리

### Phase 3: 음성 채널 연동 (1주)
- [ ] VoiceChannelManager 구현
- [ ] PTTController와 연동
- [ ] 송수신 테스트
- [ ] 통합 테스트

### Phase 4: 안정화 (1주)
- [ ] 예외 처리 강화
- [ ] 연결 안정성 개선
- [ ] 메모리 누수 검사
- [ ] 배포 준비

---

## 5. 현재 작업: 불필요한 코드 정리

### 삭제할 파일들
1. **Phase1RTMTest.kt** - 복잡한 테스트 시스템
2. **Phase1TestRunner.kt** - 테스트 실행기
3. **EnhancedPTTRTMManager.kt** - 과도한 복잡성
4. **RTM2xTestSuite.kt** - 당장 불필요한 테스트

### 정리할 기능들
1. **음성/햅틱 피드백** - 차후 구현
2. **복잡한 재전송 로직** - 기본 기능 안정화 후
3. **고급 에러 복구** - Phase 4에서 처리
4. **성능 최적화** - 기본 동작 확인 후

---

## 6. 핵심 목표 재확인

**✅ 볼륨 버튼으로 PTT 시작/종료**
**✅ RTM 2.x를 통한 안정적인 시그널링**  
**✅ RTC를 통한 음성 송수신**
**✅ 단계별 테스트 가능한 구현**

음성/햅틱 피드백, 고급 에러 처리, 성능 최적화 등은 **기본 기능이 안정화된 후** 순차적으로 추가합니다.

이 계획서는 **현실적이고 구현 가능한** 접근법으로 볼륨 버튼 PTT의 핵심 기능에 집중합니다.