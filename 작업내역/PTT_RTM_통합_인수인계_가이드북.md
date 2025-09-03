# 🎯 PTT-RTM 통합 시스템 인수인계 가이드북

## 📋 프로젝트 핵심 목표
**"비용 절약과 즉시 연결을 위한 RTM 기반 PTT 시스템 구축"**

### 왜 이 작업을 하는가?
1. **비용 절약**: FCM 대신 Agora RTM으로 실시간 시그널링 → 서버 비용 0원
2. **즉시 연결**: RTM 상시 연결로 콜드 스타트 없이 바로 PTT 시작
3. **자동 채널 참여**: 앱 시작 시 자동으로 RTM 채널 구독 → 수동 참여 불필요

---

## ✅ 현재까지 완료된 작업

### 1. **SignalingManager.kt 구현 완료** ✔️
```kotlin
위치: call_manager/app/src/main/java/com/designated/callmanager/ptt/manager/SignalingManager.kt
```
- Agora RTM 2.x (Signaling SDK) 정상 연동
- 모든 컴파일 에러 해결
- 이벤트 리스너 완벽 구현
- PTT 전용 메서드 포함 (sendPttStart, sendPttEnd)

### 2. **빌드 의존성 추가 완료** ✔️
```gradle
// build.gradle
implementation 'io.agora.rtc:full-sdk:4.2.3'  // RTC (기존)
implementation 'io.agora:agora-rtm:2.2.4'      // RTM (신규)
```

### 3. **오류 해결 과정 문서화** ✔️
- RTM 1.x → 2.x API 변경사항 파악
- Private 접근자 문제 해결
- 메서드 시그니처 불일치 수정
- Java/Kotlin API 혼용 문제 해결

---

## 🔧 남은 작업 로드맵

### **Phase 1: PTTController 연동** (1-2일)
**목표**: 기존 볼륨버튼 PTT에 RTM 시그널링 추가

#### 작업 1: PTTController.kt 수정
```kotlin
// 위치: call_manager/app/src/main/java/com/designated/callmanager/ptt/core/PTTController.kt

class PTTController {
    // 1. SignalingManager 인스턴스 추가
    private var signalingManager: SignalingManager? = null
    
    // 2. 초기화 메서드에 RTM 초기화 추가
    suspend fun initialize() {
        // 기존 RTC 초기화 유지
        engine.initialize()
        
        // RTM 초기화 추가
        signalingManager = SignalingManager(context, appId, userId)
        signalingManager?.initialize { success ->
            if (success) {
                signalingManager?.login()
                signalingManager?.subscribeChannel("ptt_channel_${officeId}")
            }
        }
    }
    
    // 3. startPTT에 RTM 신호 추가
    suspend fun startPTT(uid: Int?, channel: String?): Result<Unit> {
        // 기존 로직 유지
        val result = engine.joinChannel(...)
        
        // RTM 신호 추가 (실패해도 PTT는 동작)
        try {
            signalingManager?.sendPttStart(channel)
        } catch (e: Exception) {
            Log.w(TAG, "RTM signal failed, but PTT continues")
        }
        
        return result
    }
    
    // 4. stopPTT에 RTM 신호 추가
    suspend fun stopPTT(): Result<Unit> {
        // RTM 신호 먼저 전송
        try {
            signalingManager?.sendPttEnd(currentChannel)
        } catch (e: Exception) {
            // 무시
        }
        
        // 기존 로직
        return engine.stopTransmit()
    }
}
```

#### 작업 2: 상태 관리 추가
```kotlin
// PTTViewModel 또는 PTTController에 추가
private val _otherUsersPttStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
val otherUsersPttStatus = _otherUsersPttStatus.asStateFlow()

// SignalingManager의 메시지 수신 처리
fun handlePttMessage(publisherId: String, message: String) {
    when {
        message.contains("PTT_START") -> {
            _otherUsersPttStatus.update { 
                it + (publisherId to true)
            }
        }
        message.contains("PTT_END") -> {
            _otherUsersPttStatus.update {
                it - publisherId
            }
        }
    }
}
```

---

### **Phase 2: UI 업데이트** (1일)

#### 작업 1: PTTScreen.kt 수정
```kotlin
// 위치: call_manager/app/src/main/java/com/designated/callmanager/ptt/ui/PTTScreen.kt

@Composable
fun PTTScreen() {
    // 다른 사용자 PTT 상태 관찰
    val otherUsersPtt by viewModel.otherUsersPttStatus.collectAsState()
    
    // UI에 표시
    if (otherUsersPtt.isNotEmpty()) {
        Card {
            Text("현재 PTT 사용 중: ${otherUsersPtt.keys.joinToString()}")
        }
    }
    
    // PTT 버튼 비활성화 로직
    Button(
        enabled = otherUsersPtt.isEmpty(), // 다른 사용자가 PTT 중이면 비활성화
        onClick = { viewModel.startPTT() }
    ) {
        Text("PTT")
    }
}
```

---

### **Phase 3: 자동 채널 참여** (1일)

#### 작업 1: Application 클래스에서 초기화
```kotlin
// 위치: CallManagerApplication.kt

class CallManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // RTM 자동 초기화 및 채널 참여
        GlobalScope.launch {
            initializeRTM()
        }
    }
    
    private suspend fun initializeRTM() {
        val userId = getUserId() // SharedPref에서 가져오기
        val officeId = getOfficeId()
        
        if (userId != null && officeId != null) {
            val signalingManager = SignalingManager(this, AGORA_APP_ID, userId)
            signalingManager.initialize { success ->
                if (success) {
                    signalingManager.login()
                    signalingManager.subscribeChannel("ptt_channel_$officeId")
                    
                    // 전역 싱글톤에 저장
                    RTMSingleton.instance = signalingManager
                }
            }
        }
    }
}
```

#### 작업 2: 싱글톤 패턴 구현
```kotlin
// 위치: RTMSingleton.kt

object RTMSingleton {
    var instance: SignalingManager? = null
    
    fun getInstance(context: Context): SignalingManager {
        return instance ?: throw IllegalStateException("RTM not initialized")
    }
}
```

---

### **Phase 4: 픽업앱 적용** (2일)

#### 작업 1: SignalingManager 복사
```bash
# 파일 복사
cp call_manager/.../SignalingManager.kt pickup_app/.../SignalingManager.kt
```

#### 작업 2: build.gradle 수정
```gradle
// pickup_app/app/build.gradle
implementation 'io.agora:agora-rtm:2.2.4'
```

#### 작업 3: PTTController 동일하게 수정

---

## ⚠️ 주의사항 및 트러블슈팅

### 1. **빌드 에러 발생 시**
```kotlin
// 문제: Unresolved reference 에러
// 해결: 다음 순서로 확인
1. build.gradle에 의존성 추가 확인
2. Sync Project
3. Clean → Rebuild
4. Invalidate Caches and Restart
```

### 2. **RTM 연결 실패 시**
```kotlin
// Graceful Degradation 패턴 적용
try {
    signalingManager?.sendSignal()
} catch (e: Exception) {
    // RTM 실패해도 기존 PTT는 정상 동작
    Log.w(TAG, "RTM failed but PTT continues")
}
```

### 3. **토큰 관리**
```kotlin
// RTM과 RTC 토큰은 별개
// RTM은 null 토큰으로도 테스트 가능
signalingManager.login(token = null) // 개발 중에는 null 가능
```

---

## 📊 테스트 체크리스트

### 기능 테스트
- [ ] 볼륨 버튼 PTT 기존 기능 정상 동작
- [ ] RTM 로그인 성공
- [ ] 채널 자동 구독
- [ ] PTT 시작 신호 전송
- [ ] PTT 종료 신호 전송
- [ ] 다른 사용자 PTT 상태 수신
- [ ] UI에 상태 표시
- [ ] RTM 실패 시에도 PTT 동작

### 성능 테스트
- [ ] 콜드 스타트 없이 즉시 PTT
- [ ] 배터리 소모량 체크
- [ ] 네트워크 트래픽 체크
- [ ] 메모리 사용량 체크

---

## 🛠️ 개발 환경 정보

### SDK 버전
- Agora RTC: 4.2.3
- Agora RTM (Signaling): 2.2.4
- Android compileSdk: 34
- Kotlin: 1.9.22

### 주요 파일 위치
```
call_manager/
├── ptt/
│   ├── core/
│   │   └── PTTController.kt        # RTM 연동 추가 필요
│   ├── manager/
│   │   └── SignalingManager.kt     # ✅ 완성
│   ├── service/
│   │   ├── PTTAccessibilityService.kt  # 변경 없음
│   │   └── PTTForegroundService.kt     # 변경 없음
│   └── ui/
│       └── PTTScreen.kt            # UI 업데이트 필요
```

---

## 💡 핵심 포인트 요약

### 왜 이 방식인가?
1. **최소 침습**: 기존 완벽히 동작하는 PTT 시스템 유지
2. **점진적 적용**: 단계별로 기능 추가, 각 단계마다 테스트
3. **안전성**: RTM 실패해도 기존 PTT는 정상 동작

### 최종 목표
- **비용**: FCM 서버 비용 → 0원
- **속도**: 콜드 스타트 3초 → 0초
- **편의성**: 수동 채널 참여 → 자동 참여

### 성공 기준
1. 볼륨 버튼 누르면 즉시 PTT 시작 (지연 없음)
2. 다른 사용자 PTT 상태 실시간 확인
3. 앱 시작 시 자동으로 RTM 채널 참여
4. RTM 장애 시에도 기본 PTT 기능 유지

---

## 🔴 Agora API 통합 삽질 해결 과정 (중요!)

### 왜 이렇게 어려웠나?
Agora가 RTM 1.x에서 2.x로 업그레이드하면서 **"RTM"이라는 이름을 버리고 "Signaling SDK"로 리브랜딩**했습니다. 
문서는 섞여있고, 구글 검색 결과는 대부분 구버전이며, 공식 문서조차 Java/Kotlin 예제가 혼재되어 있었습니다.

### 해결된 주요 문제들과 솔루션

#### 1. **패키지/클래스명 혼란**
```kotlin
// ❌ 잘못된 시도들 (인터넷에 떠도는 예제)
import io.agora.rtm.RtmClient           // RTM 1.x
import io.agora.rtm2.RtmClient          // 존재하지 않음
import io.agora.signaling.RtmClient     // 존재하지 않음

// ✅ 정답 (2.x는 여전히 rtm 패키지 사용)
import io.agora.rtm.*                   // RTM 2.x (Signaling SDK)
```

#### 2. **이벤트 리스너 등록 방법**
```kotlin
// ❌ 시도 1: addEventListener (RTM 1.x 방식)
rtmClient?.addEventListener(listener)

// ❌ 시도 2: setEventListener (잘못된 메서드명)
config.setEventListener(listener)

// ❌ 시도 3: Java 스타일 apply{} 
val config = RtmConfig.Builder(appId, userId).apply {
    eventListener(listener)  // Kotlin에서 오류
}

// ✅ 정답: Java 스타일로 체이닝
val config = RtmConfig.Builder(appId, userId)
    .eventListener(listener)  // 정확한 메서드명
    .build()
```

#### 3. **메시지 내용 접근**
```kotlin
// ❌ 시도 1: text 속성 (RTM 1.x)
event.message.text

// ❌ 시도 2: payload (private)
event.message.payload

// ❌ 시도 3: rawData (존재하지 않음)
event.message.rawData

// ✅ 정답: data 속성
event.message?.data?.toString()
```

#### 4. **RtmClient 생성**
```kotlin
// ❌ 시도 1: 비동기 콜백 (일부 예제에서 제시)
RtmClient.create(config, object : ResultCallback<RtmClient> {
    override fun onSuccess(client: RtmClient?) { }
})

// ❌ 시도 2: createInstance (존재하지 않는 메서드)
RtmClient.createInstance(config)

// ✅ 정답: 동기 메서드
rtmClient = RtmClient.create(config)
```

#### 5. **필수 이벤트 리스너 메서드**
```kotlin
// ❌ 인터넷 예제들이 놓치는 메서드
onConnectionStateChanged  // 이름이 틀림
onTokenPrivilegeWillExpire // 선택사항으로 착각

// ✅ 실제 필수 메서드 (하나라도 빠지면 컴파일 에러)
override fun onLinkStateEvent(event: LinkStateEvent)  // 정확한 이름
override fun onTokenPrivilegeWillExpire(channelName: String?)
override fun onMessageEvent(event: MessageEvent)
override fun onPresenceEvent(event: PresenceEvent)
override fun onTopicEvent(event: TopicEvent)
override fun onStorageEvent(event: StorageEvent)
override fun onLockEvent(event: LockEvent)
```

---

## 🚨 앞으로 남은 작업 시 주의사항

### 1. **PTTController 연동 시 실수하기 쉬운 부분**

#### ❌ 잘못된 접근: RTM을 메인 스레드에서 직접 호출
```kotlin
// 이렇게 하면 ANR 발생 가능
fun startPTT() {
    signalingManager.login()  // 블로킹될 수 있음
    signalingManager.subscribeChannel()
}
```

#### ✅ 올바른 접근: 비동기 처리 + 에러 격리
```kotlin
suspend fun startPTT() = withContext(Dispatchers.IO) {
    try {
        // RTM은 별도 코루틴에서 처리
        launch {
            signalingManager?.sendPttStart(channel)
        }
        
        // RTC는 메인 플로우에서 처리
        engine.startTransmit()
        
    } catch (e: Exception) {
        // RTM 실패해도 PTT는 계속
        Log.w(TAG, "RTM failed: $e")
        return@withContext Result.success(Unit)
    }
}
```

### 2. **상태 동기화 문제**

#### 문제 상황
- A 사용자가 PTT 시작 → RTM 신호 전송
- B 사용자가 신호 수신 → UI 업데이트
- A 사용자가 비정상 종료 → PTT 종료 신호 미전송
- B 사용자는 여전히 "A가 PTT 중"으로 표시

#### 해결책: Presence + Timeout
```kotlin
// SignalingManager에 추가
private fun setupPresenceMonitoring() {
    // 30초마다 presence 확인
    presenceCheckJob = scope.launch {
        while (isActive) {
            delay(30_000)
            checkAndCleanupStaleUsers()
        }
    }
}

private fun checkAndCleanupStaleUsers() {
    val now = System.currentTimeMillis()
    activePttUsers.forEach { (userId, lastSeen) ->
        if (now - lastSeen > 60_000) {  // 60초 타임아웃
            // 자동으로 PTT 종료 처리
            handlePttEnd(userId)
        }
    }
}
```

### 3. **토큰 관리 복잡성**

#### 현재 상황
- RTC는 이미 TokenManager가 관리 중
- RTM도 토큰이 필요하지만 별개 토큰

#### 추천 솔루션
```kotlin
class UnifiedTokenManager {
    suspend fun getRtcToken(channel: String, uid: Int): String
    suspend fun getRtmToken(userId: String): String?  // null 허용
    
    // 개발 중에는 RTM 토큰 null로 진행
    fun isDevelopment() = BuildConfig.DEBUG
    
    suspend fun getTokensForPtt(channel: String, uid: Int): Pair<String, String?> {
        return if (isDevelopment()) {
            getRtcToken(channel, uid) to null  // RTM은 null
        } else {
            getRtcToken(channel, uid) to getRtmToken(uid.toString())
        }
    }
}
```

### 4. **채널명 규칙 통일**

#### 문제: RTC와 RTM 채널명이 다르면 혼란
```kotlin
// ❌ 혼란스러운 방식
rtcChannel = "voice_${officeId}_${timestamp}"
rtmChannel = "signal_${officeId}"

// ✅ 통일된 방식
baseChannel = "ptt_${officeId}"
rtcChannel = baseChannel  // 동일하게 사용
rtmChannel = baseChannel  // 동일하게 사용
```

### 5. **메모리 누수 방지**

#### SignalingManager 생명주기 관리
```kotlin
class PTTController : LifecycleObserver {
    
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun initializeRTM() {
        signalingManager = SignalingManager(...)
        signalingManager?.initialize()
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun cleanupRTM() {
        signalingManager?.logout()
        signalingManager?.release()
        signalingManager = null  // 반드시 null 처리
    }
}
```

---

## 💊 즉시 적용 가능한 디버깅 팁

### 1. **RTM 연결 상태 실시간 모니터링**
```kotlin
// BuildConfig에서 디버그 모드 확인
if (BuildConfig.DEBUG) {
    // 개발자 옵션에 RTM 상태 표시
    DebugOverlay.show("RTM: ${linkState}")
}
```

### 2. **Logcat 필터 설정**
```bash
# RTM 관련 로그만 보기
adb logcat -s SignalingManager:V RTM2Manager:V PTTController:V

# Agora SDK 내부 로그 보기
adb logcat -s "agora-sdk":V
```

### 3. **빌드 실패 시 체크리스트**
```
□ build.gradle에 implementation 'io.agora:agora-rtm:2.2.4' 있는지
□ Sync Project 했는지
□ Clean → Rebuild 했는지
□ 모든 EventListener 메서드 구현했는지 (7개)
□ ErrorInfo를 nullable (ErrorInfo?)로 처리했는지
```

---

## 🎯 핵심 교훈

### Agora 문서를 볼 때
1. **공식 문서 URL 확인**: `/signaling/` 경로가 최신
2. **SDK 버전 확인**: 2.2.0 이상인지
3. **Java 예제를 Kotlin으로 변환 시 주의**
   - apply{} 블록 사용 자제
   - nullable 처리 철저히
   - 타입 추론에 의존하지 말고 명시적 타입 선언

### 구글링할 때
- "Agora RTM" ❌ → "Agora Signaling SDK" ✅
- "RTM 2.x Android" ❌ → "Signaling SDK Android 2024" ✅
- 날짜 필터 사용 (최근 1년 이내)

### 실패했을 때
1. 에러 메시지 그대로 구글링하지 말 것
2. 공식 API Reference 먼저 확인
3. GitHub에서 agora-rtm 검색하여 실제 사용 예제 찾기

---

## 📌 마지막 당부

**"완벽하게 동작하는 볼륨버튼 PTT를 망치지 마세요!"**

- RTM 통합은 **추가 기능**입니다
- 기존 코드는 최대한 보존하세요
- Graceful Degradation: RTM 실패 시 기본 기능 유지
- 작은 단위로 테스트하며 진행하세요

**성공의 열쇠**:
1. 기존 PTT는 건드리지 않기
2. RTM은 옵셔널하게 추가
3. 실패해도 괜찮은 구조로
4. 단계별 커밋으로 롤백 가능하게

---

**작성일**: 2025-09-03
**작성자**: Claude Opus (AI Assistant)
**검증**: 빌드 성공 확인 완료
**추가 조언**: Agora API 통합 삽질 경험 공유

이 가이드북을 따라 진행하면 누구든지 PTT-RTM 통합을 완료할 수 있습니다.
삽질하지 마시고, 이미 해결된 방법을 사용하세요!
화이팅! 🚀