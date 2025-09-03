# 현재 PTT 코드 분석 및 RTM 2.x 연동 최종 계획

## 1. 현재 PTT 시스템 구조 분석

### ✅ **완전 작동 중인 볼륨 버튼 PTT 시스템**

```
볼륨키 누름 → PTTAccessibilityService → PTTForegroundService → PTTController → SimplePTTEngine → Agora RTC
```

#### 핵심 컴포넌트들:

**1. PTTAccessibilityService.kt** (127-170라인)
```kotlin
private fun startPTT() {
    val intent = Intent(this, PTTForegroundService::class.java).apply {
        action = PTTForegroundService.ACTION_START_PTT
    }
    startForegroundService(intent)
}

private fun stopPTT() {
    val intent = Intent(this, PTTForegroundService::class.java).apply {
        action = PTTForegroundService.ACTION_STOP_PTT
    }
    startService(intent)
}
```

**2. PTTForegroundService.kt** (381-410라인)
```kotlin
private fun handleStartPTT() {
    serviceScope.launch {
        playStartBeep() // 비프음
        val uid = getOrCreateUID()
        val result = pttController.startPTT(uid) // PTT 시작
    }
}
```

**3. PTTController.kt** (47-114라인)
```kotlin
suspend fun startPTT(uid: Int? = null, channel: String? = null): Result<Unit> {
    // 1. 토큰 획득 (TokenManager)
    // 2. 채널 참여 (SimplePTTEngine)
    // 3. 전송 시작
}
```

---

## 2. RTM 시그널링 현황

### ❌ **현재 RTM 없음**: 
- RTM 관련 코드는 **데이터 모델만 존재**
- **실제 RTM 연동은 구현되지 않음**
- PTTSignalData.kt에 RTM 관련 enum만 정의됨

### 🔍 **RTM 연동이 필요한 이유**:
현재는 **각자 독립적으로 PTT 시작/종료**하는 구조
- 다른 사용자가 PTT 중인지 모름
- 동시 전송 시 충돌 가능
- 중재 시스템 없음

---

## 3. RTM 2.x 연동 지점 설계

### 📍 **최소 침습 연동 방식**

#### **Phase 1: PTTController에 RTM 신호 추가**
```kotlin
// PTTController.kt 수정점
suspend fun startPTT(uid: Int? = null, channel: String? = null): Result<Unit> {
    try {
        // 기존 로직 유지
        val tokenResult = tokenManager.getToken(...)
        val joinResult = engine.joinChannel(...)
        
        // 🆕 RTM 신호 추가 (기존 동작에 영향 없음)
        rtm2Manager?.sendPTTStartSignal(channelName, uid)
        
        // 기존 전송 시작
        engine.startTransmit()
        
    } catch (e: Exception) {
        // 기존 에러 처리
    }
}

suspend fun stopPTT(): Result<Unit> {
    try {
        // 기존 로직
        engine.stopTransmit()
        
        // 🆕 RTM 신호 추가
        rtm2Manager?.sendPTTStopSignal(currentChannel, currentUID)
        
    } catch (e: Exception) {
        // 기존 에러 처리
    }
}
```

#### **Phase 2: RTM2Manager 추가**
```kotlin
// 새로운 클래스: RTM2Manager.kt
class RTM2Manager(private val context: Context) {
    private var rtmClient: RtmClient? = null
    
    suspend fun initialize(appId: String, userId: String, token: String?): Boolean {
        val config = RtmConfig.Builder(appId, userId)
            .eventListener(createEventListener())
            .build()
        rtmClient = RtmClient.create(config)
        // ... 로그인 및 구독 로직
    }
    
    suspend fun sendPTTStartSignal(channel: String, senderUid: Int) {
        val signal = PTTSignal(
            type = PTTSignalType.PTT_START,
            senderId = senderUid.toString(),
            timestamp = System.currentTimeMillis()
        )
        rtmClient?.publish(channel, Json.encodeToString(signal), PublishOptions())
    }
    
    // RTM 신호 수신 처리
    private fun createEventListener(): RtmEventListener {
        return object : RtmEventListener {
            override fun onMessageEvent(event: MessageEvent?) {
                // PTT 신호 수신 → UI 업데이트
            }
        }
    }
}
```

---

## 4. 불필요한 코드 정리 목록

### 🗑️ **삭제할 파일들**
1. **Phase1RTMTest.kt** - ✅ 이미 삭제됨
2. **Phase1TestRunner.kt** - ✅ 이미 삭제됨  
3. **EnhancedPTTRTMManager.kt** - ✅ 이미 삭제됨

### 🔄 **활용할 기존 코드**
1. **PTTSignalData.kt** - RTM 신호 데이터 모델 (그대로 사용)
2. **PTTAccessibilityService.kt** - 볼륨키 감지 (변경 없음)
3. **PTTForegroundService.kt** - 서비스 로직 (변경 없음)
4. **PTTController.kt** - RTM 연동 추가만

### 📁 **현재 PTT 파일 구조 (정리 완료)**
```
ptt/
├── service/
│   ├── PTTAccessibilityService.kt    ✅ 완성 (변경 없음)
│   ├── PTTForegroundService.kt       ✅ 완성 (변경 없음)
│   └── PTTSignalingService.kt        ❓ 용도 확인 필요
├── core/
│   ├── PTTController.kt              ✅ RTM 연동 추가 예정
│   └── SimplePTTEngine.kt            ✅ 완성 (변경 없음)
├── data/
│   └── PTTSignalData.kt              ✅ 완성 (RTM 모델 포함)
└── manager/ (새로 생성)
    └── RTM2Manager.kt                🆕 새로 구현
```

---

## 5. 최종 구현 계획 (단계별)

### **Phase 1: RTM 2.x 매니저 구현 (1주)**
- [ ] RTM2Manager.kt 생성
- [ ] Gradle dependencies 업데이트 (`io.agora:agora-rtm:2.2.4`)
- [ ] RTM 로그인 및 채널 구독 구현
- [ ] 기본 신호 송수신 테스트

### **Phase 2: PTTController 연동 (3일)**  
- [ ] PTTController에 RTM2Manager 추가
- [ ] startPTT()에 RTM 신호 전송 코드 추가
- [ ] stopPTT()에 RTM 신호 전송 코드 추가
- [ ] 에러 처리 (RTM 실패해도 기존 PTT는 동작)

### **Phase 3: 신호 수신 처리 (3일)**
- [ ] RTM 신호 수신 시 UI 업데이트
- [ ] "다른 사용자 PTT 중" 표시
- [ ] PTT 충돌 방지 로직
- [ ] 상태 동기화

### **Phase 4: 테스트 및 안정화 (1주)**
- [ ] RTM 1.x 코드 완전 제거
- [ ] 통합 테스트
- [ ] 에러 시나리오 테스트
- [ ] 성능 검증

---

## 6. 기술적 구현 세부사항

### **RTM 2.x 의존성 (build.gradle 수정)**
```gradle
dependencies {
    // 기존 RTC (변경 없음)
    implementation 'io.agora.rtc:full-sdk:4.2.3'
    
    // 🆕 RTM 2.x 추가
    implementation 'io.agora:agora-rtm:2.2.4'
    
    // 기존 Kotlin Serialization (활용)
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0'
}
```

### **RTM 2.x 연동 흐름**
```kotlin
// 1. 볼륨키 누름 (기존과 동일)
PTTAccessibilityService.handleVolumeKeyEvent()

// 2. 서비스 호출 (기존과 동일)
PTTForegroundService.handleStartPTT()

// 3. 컨트롤러 호출 (RTM 신호 추가)
PTTController.startPTT() {
    // 기존 로직
    engine.joinChannel()
    engine.startTransmit()
    
    // 🆕 RTM 신호
    rtm2Manager.sendPTTStartSignal()
}

// 4. 다른 사용자들이 RTM 신호 수신
RTM2Manager.onMessageEvent() {
    // UI에 "김기사님 PTT 중" 표시
    updatePTTStatus(senderId, isTransmitting = true)
}
```

---

## 7. 예상 수정 파일 목록

### **새로 생성**
- `RTM2Manager.kt` - RTM 2.x 시그널링 매니저
- `PTTSignal.kt` - RTM 신호 간소화 모델 (기존 PTTSignalData 활용)

### **수정 필요** 
- `PTTController.kt` - RTM 신호 전송 추가 (20줄 내외)
- `build.gradle` - RTM 2.x 의존성 추가 (1줄)
- `PTTScreen.kt` - RTM 상태 표시 UI (선택적)

### **변경 없음**
- `PTTAccessibilityService.kt` ✅ 완벽 동작 중
- `PTTForegroundService.kt` ✅ 완벽 동작 중  
- `SimplePTTEngine.kt` ✅ RTC 엔진
- `PTTSignalData.kt` ✅ 모델 정의 완료

---

## 8. 리스크 및 대응

### **🚨 주요 리스크**
1. **RTM 2.x 연결 실패** → 기존 PTT는 정상 동작 (Graceful degradation)
2. **토큰 관리 복잡성** → 기존 TokenManager 활용
3. **성능 영향** → RTM 신호는 비동기 처리

### **✅ 안전장치**
- RTM 실패해도 볼륨 버튼 PTT는 정상 동작
- 기존 코드 최소 변경
- 단계별 테스트로 안정성 확보

---

## 결론

**현재 볼륨 버튼 PTT는 완벽히 작동**하므로, **최소한의 변경**으로 RTM 2.x 시그널링만 추가하는 방식이 가장 안전하고 효율적입니다.

**핵심 변경점**:
1. RTM2Manager.kt 새로 생성
2. PTTController.kt에 RTM 신호 전송 2줄 추가  
3. build.gradle에 의존성 1줄 추가

이렇게 하면 **기존 완벽한 볼륨 버튼 PTT**에 **RTM 2.x 시그널링**이 자연스럽게 추가되어 **다중 사용자 PTT 시스템**이 완성됩니다.