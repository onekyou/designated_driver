# 기존 볼륨버튼 PTT + RTM 2.x 연동 계획서 v8

## 전제 조건
- ✅ **볼륨 버튼 PTT**: 현재 잘 작동 중
- ❌ **RTM 2.x**: 아직 미구현 상태 (기존 RTM 1.x 사용 중)
- 🎯 **목표**: 기존 볼륨 버튼 PTT 동작을 유지하며 RTM 2.x로 마이그레이션

---

## 1. 현재 상황 분석

### 1.1 기존 시스템 (잘 작동 중)
```
볼륨 버튼 누름 → PTTAccessibilityService → PTT 시작/종료
                                      ↓
                              기존 RTM 1.x 시그널링
                                      ↓
                              RTC 음성 채널 제어
```

### 1.2 목표 시스템
```
볼륨 버튼 누름 → PTTAccessibilityService → PTT 시작/종료
                                      ↓
                              RTM 2.x 시그널링 (NEW)
                                      ↓
                              RTC 음성 채널 제어
```

---

## 2. RTM 1.x → 2.x 마이그레이션 전략

### 2.1 변경 최소화 원칙
1. **PTTAccessibilityService**: 변경 없음 (이미 잘 작동)
2. **볼륨 버튼 감지**: 변경 없음 (이미 완벽)
3. **RTC 음성 채널**: 변경 없음 (호환됨)
4. **변경 대상**: RTM 시그널링 부분만

### 2.2 단계별 교체 전략

#### Phase 1: RTM 2.x 매니저 추가 (기존 코드 보존)
```kotlin
// 기존 RTM 1.x 코드 유지하면서 새로 추가
class RTM2xManager(context: Context) {
    // RTM 2.x 구현
}

// 기존 PTT 컨트롤러에 RTM 2.x 옵션 추가
class UnifiedPTTController {
    private val rtm1xManager = EnhancedPTTRTMManager() // 기존
    private val rtm2xManager = RTM2xManager()          // 신규
    
    fun startPTT() {
        // 볼륨 버튼에서 호출되는 기존 로직
        if (useRTM2x) {
            rtm2xManager.sendPTTStart()  // 신규
        } else {
            rtm1xManager.sendPTTStart()  // 기존 (fallback)
        }
        
        // RTC 음성 채널은 동일
        voiceChannelManager.startTransmission()
    }
}
```

#### Phase 2: RTM 2.x 검증 및 전환
```kotlin
class RTMMigrationController {
    fun enableRTM2x() {
        // 설정을 통해 RTM 2.x 활성화
        SharedPreferences.edit()
            .putBoolean("use_rtm_2x", true)
            .apply()
    }
    
    fun fallbackToRTM1x() {
        // 문제 발생 시 RTM 1.x로 롤백
        SharedPreferences.edit()
            .putBoolean("use_rtm_2x", false)
            .apply()
    }
}
```

#### Phase 3: RTM 1.x 제거
```kotlin
// RTM 2.x 안정화 후 기존 코드 정리
class UnifiedPTTController {
    private val rtmManager = RTM2xManager() // RTM 2.x만 사용
    
    fun startPTT() {
        // 볼륨 버튼 → RTM 2.x → RTC (완전 전환)
        rtmManager.sendPTTStart()
        voiceChannelManager.startTransmission()
    }
}
```

---

## 3. RTM 2.x 연동 포인트

### 3.1 기존 PTT 이벤트와 연동
```kotlin
/**
 * 현재 볼륨 버튼 PTT에서 호출되는 메서드들
 */
interface PTTEventHandler {
    fun onPTTStartRequested()    // 볼륨키 Down
    fun onPTTStopRequested()     // 볼륨키 Up
    fun onEmergencyCall()        // 긴급 호출
}

/**
 * RTM 2.x 연동 구현
 */
class RTM2xPTTHandler : PTTEventHandler {
    
    override fun onPTTStartRequested() {
        // 기존 볼륨 버튼 이벤트 → RTM 2.x 신호
        CoroutineScope(Dispatchers.IO).launch {
            rtm2xManager.publishPTTSignal(
                channelName = "driver_channel",
                signalType = PTTSignalType.PTT_START,
                senderId = getCurrentUserId()
            )
        }
    }
    
    override fun onPTTStopRequested() {
        CoroutineScope(Dispatchers.IO).launch {
            rtm2xManager.publishPTTSignal(
                channelName = "driver_channel", 
                signalType = PTTSignalType.PTT_STOP,
                senderId = getCurrentUserId()
            )
        }
    }
}
```

### 3.2 RTM 2.x 신호 수신 처리
```kotlin
/**
 * RTM 2.x로부터 받은 신호를 기존 RTC 시스템과 연동
 */
class RTM2xSignalProcessor {
    
    fun handleIncomingSignal(signal: PTTSignal) {
        when (signal.type) {
            PTTSignalType.PTT_START -> {
                // 다른 사용자 PTT 시작 → RTC 수신 모드
                voiceChannelManager.enableReceiveMode()
                showPTTIndicator("${signal.senderId}님이 송신 중")
            }
            
            PTTSignalType.PTT_STOP -> {
                // 다른 사용자 PTT 종료 → 대기 모드
                voiceChannelManager.enterStandbyMode() 
                hidePTTIndicator()
            }
            
            PTTSignalType.EMERGENCY_CALL -> {
                // 긴급 호출 → 기존 처리 로직 활용
                handleEmergencyCall(signal.senderId)
            }
        }
    }
}
```

---

## 4. 구현 단계 (안전한 마이그레이션)

### Phase 1: RTM 2.x 병렬 구현 (2주)
- [x] 현재 작동하는 볼륨 버튼 PTT 분석
- [ ] RTM 2.x 매니저 구현 (기존 코드 건드리지 않음)
- [ ] RTM 2.x 테스트 환경 구축
- [ ] 설정을 통한 RTM 1.x/2.x 선택 기능

### Phase 2: 연동 및 검증 (1주)
- [ ] 볼륨 버튼 이벤트 → RTM 2.x 신호 변환
- [ ] RTM 2.x 신호 → RTC 채널 제어 연동
- [ ] A/B 테스트 (RTM 1.x vs 2.x 비교)
- [ ] 안정성 검증

### Phase 3: 전환 및 정리 (1주)
- [ ] RTM 2.x를 기본값으로 설정
- [ ] RTM 1.x 코드 제거
- [ ] 최종 테스트 및 검증
- [ ] 문서화

---

## 5. 핵심 연동 지점

### 5.1 볼륨 버튼 → RTM 2.x
```kotlin
// PTTAccessibilityService.kt (기존 코드 활용)
private fun handleVolumeKey(event: KeyEvent) {
    when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            // 기존: pttController?.startPTT()
            // 신규: RTM 2.x 신호 추가
            pttController?.startPTT()
        }
        KeyEvent.ACTION_UP -> {
            // 기존: pttController?.stopPTT() 
            // 신규: RTM 2.x 신호 추가
            pttController?.stopPTT()
        }
    }
}
```

### 5.2 RTM 2.x → RTC 제어
```kotlin
// 기존 RTC 제어 로직 재활용
class VoiceChannelManager {
    fun handleRTM2xSignal(signal: PTTSignal) {
        when (signal.type) {
            PTT_START -> startReceiving(signal.senderId)
            PTT_STOP -> stopReceiving()
            EMERGENCY -> handleEmergency(signal)
        }
    }
}
```

---

## 6. 리스크 완화 전략

### 6.1 롤백 계획
- RTM 2.x 문제 발생 시 즉시 RTM 1.x로 복귀
- 볼륨 버튼 PTT는 항상 정상 동작 보장
- 사용자는 차이를 느끼지 못하도록

### 6.2 점진적 전환
- 개발자 모드에서 RTM 2.x 테스트
- 베타 테스터 대상 A/B 테스트  
- 안정성 확인 후 전체 적용

### 6.3 모니터링
- RTM 연결 상태 실시간 모니터링
- 에러율 추적 및 알림
- 성능 비교 (RTM 1.x vs 2.x)

---

## 결론

**기존 볼륨 버튼 PTT의 완벽한 동작을 보존**하면서 **RTM 2.x의 장점**을 점진적으로 도입하는 안전한 마이그레이션 계획입니다.

핵심은 **"기존 동작 + RTM 2.x 추가"** 방식으로 위험을 최소화하고, 사용자 경험에 영향을 주지 않으면서 기술적 개선을 달성하는 것입니다.