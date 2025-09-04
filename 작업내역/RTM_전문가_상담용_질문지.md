# Agora RTM 2.x 전문가 상담용 질문지

## 📋 **프로젝트 배경**

### **목표**
- **PTT(Push-to-Talk) 시스템에서 FCM → RTM 대체**를 통한 서버 비용 절감
- FCM 3-5초 지연 → RTM 즉시 연결로 사용자 경험 개선

### **현재 시스템 구조**
```
[사용자 A] ←→ [Agora RTC 4.2.3] ←→ [사용자 B]  (음성 통신)
     ↕              ↕                      ↕
[RTM 2.2.4]   [RTM Signaling]      [RTM 2.2.4]  (상태 동기화)
```

### **앱 구성**
- **콜매니저앱** (관리자용): Android, Kotlin, Jetpack Compose
- **픽업앱** (기사용): Android, Kotlin, Jetpack Compose
- **공통**: Agora RTC 4.2.3 + RTM 2.2.4, Firebase Backend

---

## ✅ **현재까지 구현 완료된 부분**

### **1. RTM 2.x 완전 연동**
```kotlin
// SignalingManager.kt (양쪽 앱 모두 구현)
class SignalingManager(
    private val context: Context,
    private val appId: String,
    private val userId: String
) {
    private var rtmClient: RtmClient? = null
    
    // 초기화 및 로그인 ✅
    suspend fun initialize(callback: (Boolean) -> Unit)
    suspend fun login(token: String? = null): Boolean
    
    // 채널 구독/해제 ✅
    suspend fun subscribeChannel(channelName: String): Boolean
    suspend fun unsubscribeChannel(channelName: String): Boolean
    
    // PTT 시그널링 ✅
    suspend fun sendPttStart(channel: String)
    suspend fun sendPttEnd(channel: String)
}
```

### **2. PTT 상태 동기화**
- A가 PTT 시작 → RTM 메시지 전송 → B에서 "A 사용자 PTT 중" 실시간 표시 ✅
- 충돌 방지: 한 명이 PTT 중일 때 다른 사람 PTT 버튼 비활성화 ✅
- Mutex 기반 동시 접근 방지 로직 구현 ✅

### **3. 자동 채널 관리**
- PTT 시작 시 자동으로 RTC 채널 참여 ✅
- PTT 종료 시 자동으로 RTC 채널 해제 ✅
- RTM 채널은 앱 시작 시 기본 구독 유지 ✅

---

## ❌ **현재 문제점 (핵심 질문 사항)**

### **🔴 문제 상황**
```
1. A와 B가 PTT 통신 중 (정상 작동) ✅
2. 통신 종료 → 둘 다 RTC 채널에서 나감 ✅ 
3. RTM 채널은 구독 유지 상태 ✅
4. A가 다시 PTT 시작 → rtmClient.publish(channel, message) 호출 ✅
5. B가 RTM 메시지를 수신하지 못함 ❌
6. B의 자동 RTC 채널 참여 실패 ❌
```

### **🔍 현재 사용 중인 RTM 구조**
```kotlin
// 메시지 전송 (A 사용자)
rtmClient?.publish(
    channelName = "office_123_ptt",
    message = JsonObject.apply {
        addProperty("type", "PTT_START")
        addProperty("senderId", "user_A") 
        addProperty("channel", "office_123_voice")
        addProperty("timestamp", System.currentTimeMillis())
    }.toString(),
    options = PublishOptions()
)

// 메시지 수신 리스너 (B 사용자) 
override fun onMessageEvent(event: MessageEvent?) {
    val message = event?.message?.data?.toString()
    // 여기서 메시지가 안 들어옴 ❌
    handlePttMessage(message)
}
```

---

## 🔥 **핵심 질문들**

### **Q1. RTM 채널 메시지 전달 조건**
**질문**: RTM 2.x에서 `rtmClient.publish(channelName, message)`로 전송한 메시지가 수신되지 않는 이유는?

**의심 사항**:
- RTM 채널 구독 상태는 유지되는데 메시지만 안 들어옴
- RTC 채널 참여/해제가 RTM 채널 구독에 영향을 주는가?
- RTM 연결 상태(LinkState)가 변경되었을 가능성?

**확인 방법**:
```kotlin
// 현재 디버깅용으로 추가한 로그들
Log.d(TAG, "RTM Login State: ${rtmClient?.isLoggedIn}")
Log.d(TAG, "Subscribed Channels: ${getSubscribedChannels()}")
Log.d(TAG, "Link State: ${currentLinkState}")
```

### **Q2. RTM User Message 활용 가능성**
**질문**: 채널 구독 없이 특정 사용자에게 직접 메시지를 보낼 수 있는가?

**찾고자 하는 API**:
```kotlin
// 이런 식으로 P2P 메시징이 가능한지?
rtmClient?.sendMessageToUser(
    userId = "user_B",
    message = pttStartMessage,
    options = SendMessageOptions()
)

// 수신 리스너
override fun onUserMessageEvent(event: UserMessageEvent?) {
    // 채널 구독 없이도 메시지 수신 가능한지?
}
```

**목표**: FCM처럼 상대방이 어떤 채널에 있든 직접 메시지 전달

### **Q3. Presence API 활용 방안**
**질문**: 사용자의 온라인/오프라인 상태를 확인하여 메시지 전송 여부를 결정할 수 있는가?

```kotlin
// 사용자 상태 확인 후 메시지 전송
val presenceResult = rtmClient?.getPresence(channelName, PresenceOptions())
if (presenceResult?.isUserOnline("user_B") == true) {
    // RTM 메시지 전송
} else {
    // FCM 백업 사용
}
```

### **Q4. RTM Connection Persistence**
**질문**: RTM 연결이 유지되는 조건과 끊어지는 조건은?

**확인하고 싶은 사항**:
- 앱이 백그라운드로 갔을 때 RTM 연결 상태
- 네트워크 재연결 시 자동 복구 여부
- RTC 엔진과 RTM 클라이언트 간 상호 영향

---

## 🔋 **추가 질문: 화면 꺼진 상태에서의 RTM 동작**

### **🔴 화면 꺼짐/백그라운드 상황에서의 문제**

#### **현재 상황**
```
1. 사용자 A: 화면 켜진 상태, 앱 활성화 ✅
2. 사용자 B: 화면 꺼진 상태 또는 앱 백그라운드 ❌
3. A가 PTT 시작 → RTM 메시지 전송
4. B의 앱이 절전 모드로 RTM 메시지 수신 실패 ❌
```

#### **Android 절전 모드 제약**
- **Doze Mode**: Android 6.0+에서 화면 꺼짐 시 네트워크 제한
- **App Standby**: 앱 미사용 시 백그라운드 활동 제한
- **Background Execution Limits**: Android 8.0+의 백그라운드 서비스 제한

### **Q5. RTM 백그라운드 연결 유지**
**질문**: RTM 2.x 연결이 화면 꺼진 상태에서도 유지되는가?

**확인하고 싶은 사항**:
```kotlin
// 화면 꺼짐 상태에서 RTM 동작 여부
- rtmClient?.isLoggedIn 상태 유지?
- onMessageEvent() 호출 여부?
- onLinkStateEvent() 변화 감지?
```

**시나리오**:
1. 앱 포그라운드에서 RTM 로그인 성공
2. 화면 끄기 (또는 홈 버튼으로 백그라운드)
3. 다른 사용자가 RTM 메시지 전송
4. **메시지 수신 가능 여부?**

### **Q6. Android 절전 모드 대응**
**질문**: RTM이 Android의 절전 최적화에 어떻게 대응하는가?

#### **예상 해결 방안들의 실효성 검증**:

**방안 1: Foreground Service 활용**
```kotlin
class RTMBackgroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        // RTM 연결 유지
        return START_STICKY
    }
}
```
**질문**: RTM 클라이언트를 Foreground Service에서 유지하는 것이 효과적인가?

**방안 2: PowerManager WakeLock**
```kotlin
class RTMWakeLockManager {
    private val wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK, 
        "RTM:KeepConnection"
    )
    
    fun acquireWakeLock() {
        wakeLock.acquire(10*60*1000L) // 10분
    }
}
```
**질문**: RTM 메시지 수신을 위한 WakeLock 사용이 권장되는가?

**방안 3: 배터리 최적화 예외**
```kotlin
// 사용자에게 배터리 최적화 예외 요청
val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
intent.data = Uri.parse("package:$packageName")
startActivity(intent)
```
**질문**: RTM 안정성을 위해 배터리 최적화 예외가 필수인가?

### **Q7. Push Notification과의 조합**
**질문**: RTM + FCM 하이브리드로 화면 꺼진 상태 문제를 해결할 수 있는가?

#### **예상 시나리오**:
```kotlin
suspend fun sendPTTNotification(targetUserId: String, message: String) {
    // 1차: RTM 시도 (빠른 응답용)
    val rtmSuccess = rtmClient?.sendMessageToUser(targetUserId, message)
    
    // 2차: FCM 백업 (화면 꺼진 상태용)
    if (!rtmSuccess || isUserInBackground(targetUserId)) {
        sendFCMPushNotification(targetUserId, message)
    }
}
```

**질문들**:
- RTM 전송 실패를 신뢰성있게 감지하는 방법?
- 상대방의 앱 상태(포그라운드/백그라운드)를 RTM으로 확인 가능?
- FCM High Priority로 Doze Mode를 우회할 수 있는가?

### **Q8. 실시간성 vs 배터리 수명 트레이드오프**
**질문**: PTT 시스템에서 실시간성과 배터리 효율성의 최적 균형점은?

#### **현재 고려 중인 전략들**:

**전략 1: 적극적 연결 유지**
```kotlin
// 장점: 즉시 응답 (<1초)
// 단점: 배터리 소모 증가
class AggressiveRTMManager {
    - Foreground Service 상시 실행
    - PARTIAL_WAKE_LOCK 유지
    - 주기적 Keep-Alive 메시지
}
```

**전략 2: 절전형 + FCM 백업**
```kotlin
// 장점: 배터리 효율적
// 단점: FCM 지연 (3-5초)
class PowerEfficientRTMManager {
    - 화면 켜져있을 때만 RTM 활성화
    - 백그라운드에서는 FCM 의존
    - Wake-up 시 RTM 재연결
}
```

**전문가 의견 요청**:
- PTT 시스템의 특성상 어떤 전략이 더 적합한가?
- 실제 운영 환경에서 사용자 수용도는 어떤가?
- 배터리 소모를 최소화하면서 실시간성을 보장하는 방법?

### **Q9. Android 버전별 대응 전략**
**질문**: Android 버전별로 다른 절전 정책에 RTM이 어떻게 대응해야 하는가?

#### **버전별 제약사항**:
```
Android 6.0 (API 23): Doze Mode, App Standby
Android 7.0 (API 24): Doze on the Go
Android 8.0 (API 26): Background Service Limitations
Android 9.0 (API 28): App Standby Buckets  
Android 10+ (API 29+): Background Activity Start 제한
Android 12+ (API 31+): Exact Alarm 권한
Android 13+ (API 33+): Notification 권한
Android 14  (API 34+): Partial Photo Access
```

**각 버전별 RTM 동작 보장 방법**:
- 필수 권한 및 설정
- Whitelist 등록 방법
- 폴백 전략

---

## 🛠️ **시도해볼 해결 방안에 대한 검증 요청**

### **방안 1: 이중 메시징 (Channel + User)**
```kotlin
suspend fun notifyPTTStart(targetUserId: String, channel: String) {
    // 1차: 채널 메시지 (현재 참여자용)
    rtmClient?.publish(channel, message, PublishOptions())
    
    // 2차: P2P 메시지 (채널 밖 사용자용)
    rtmClient?.sendMessageToUser(targetUserId, message, UserMessageOptions())
}
```
**질문**: 이런 방식이 RTM 2.x에서 지원되고 효율적인가?

### **방안 2: 상시 채널 구독**
```kotlin
// 앱 시작 시 "항상 켜져있는" 채널에 구독
val alwaysOnChannel = "office_123_always_on"
rtmClient?.subscribe(alwaysOnChannel)
```
**질문**: 배터리/네트워크 소모와 안정성 측면에서 권장되는가?

### **방안 3: RTM + FCM 하이브리드**
```kotlin
suspend fun ensureMessageDelivery(userId: String, message: String) {
    val rtmSuccess = sendRTMMessage(userId, message)
    if (!rtmSuccess) {
        sendFCMMessage(userId, message) // 백업
    }
}
```
**질문**: RTM 실패를 감지하는 신뢰할 만한 방법이 있는가?

---

## 📊 **현재 측정 데이터**

### **RTM 성능 지표**
- RTM 로그인 시간: ~500ms
- 채널 구독 시간: ~200ms  
- 메시지 전송 성공률: **채널 내 참여자 간 95%+**
- 메시지 전송 실패율: **채널 밖 사용자에게 100%** ← 핵심 문제

### **목표 지표**
- 자동 채널 참여 성공률: 95%+
- RTM 응답 속도: <1초 (vs FCM 3-5초)
- 서버 비용: 0원 (vs FCM 월 $50+)

---

## 🔧 **개발 환경 정보**

### **사용 중인 SDK 버전**
```gradle
implementation 'io.agora.rtc:full-sdk:4.2.3'
implementation 'io.agora:agora-rtm:2.2.4'  // Signaling SDK
```

### **Android 환경**
- Target SDK: 34 (Android 14)
- Kotlin: 1.9.22
- Coroutines: 1.7.3
- Jetpack Compose: 2024.02.00

### **Agora 설정**
- App ID: [Production 환경]
- RTM Token: 개발 중에는 null 사용
- RTC Token: Firebase Functions에서 자동 발급

---

## 💬 **구체적으로 알고 싶은 것들**

### **1. 기술적 검증**
- RTM 2.x User Message API 사용법과 제약사항
- 오프라인 사용자에게 메시지 전송 시 동작 방식
- RTM 연결 안정성과 자동 재연결 로직

### **2. 아키텍처 조언**
- PTT 시스템에서 RTM 활용 베스트 프랙티스
- FCM 대체 시 고려해야 할 트레이드오프
- 대용량(100+ 동시 사용자) 환경에서의 확장성

### **3. 문제 해결**
- 현재 메시지 수신 실패 원인 진단 방법
- RTM 디버깅을 위한 로그 분석 방법
- 실패 시나리오에 대한 대응 전략

---

## 🔥 **화면 꺼짐 관련 핵심 질문들**

### **즉시 확인 필요 사항**:
1. **RTM 2.x가 Doze Mode에서 메시지를 수신할 수 있는가?**
2. **Foreground Service에서 RTM 연결 유지가 효과적인가?**
3. **배터리 최적화 예외 없이도 안정적 동작이 가능한가?**

### **실무 운영 관점**:
1. **사용자에게 요구할 수 있는 최소한의 권한/설정은?**
2. **RTM + FCM 하이브리드의 최적 구현 방법은?**
3. **대용량 사용자 환경에서 검증된 아키텍처는?**

---

## 🎯 **최종 목표 재정리**

### **기능적 목표**:
- ✅ 화면 켜진 상태: RTM으로 즉시 연결 (<1초)
- ❓ **화면 꺼진 상태: RTM 또는 FCM으로 연결 보장 (<5초)**

### **비용 목표**:
- 화면 켜진 상태 95%+ → RTM 사용 (비용 0원)
- 화면 꺼진 상태 → FCM 사용 허용 (최소한의 비용)

### **사용자 경험**:
- 권한 요청 최소화
- 배터리 소모 합리적 수준 유지
- 안정적인 PTT 연결 보장

---

## 🎯 **기대하는 전문가 조언**

### **즉시 해결 방안**
- 현재 구현에서 놓친 부분이나 잘못된 API 사용법
- RTM 메시지 수신 실패 원인과 해결책

### **장기적 아키텍처**
- RTM 기반 실시간 시스템 설계 가이드라인
- 성능 최적화 및 안정성 확보 방안

### **대안 전략**
- RTM으로 완전한 FCM 대체가 어렵다면 최적의 하이브리드 구조
- 비용 대비 효과적인 푸시 알림 시스템 구축 방향

---

**🔥 핵심 질문 요약**: 
1. RTM 2.x에서 채널에 참여하지 않은 사용자에게도 메시지를 전달하여 자동으로 RTC 채널에 참여시킬 수 있는 방법이 있는가?
2. **RTM이 Android 화면 꺼짐/백그라운드 상태에서도 안정적으로 메시지를 수신할 수 있는가? 없다면 RTM+FCM 하이브리드의 최적 구현 방법은?**