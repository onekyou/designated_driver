# RTM 기반 자동 채널 참여 현황 분석 및 해결방안

## 🎯 비용 절감 목표와 현재 상황

### 💰 **핵심 목표: FCM → RTM 대체를 통한 비용 절감**
- **기존**: FCM 푸시 알림 → 자동 채널 참여 (서버 비용 발생)
- **목표**: RTM 메시징 → 자동 채널 참여 (서버 비용 0원)
- **추가 혜택**: FCM 지연(3-5초) → RTM 즉시 연결

---

## ✅ **현재까지 완료된 구현**

### 1. **RTM 2.x (Signaling SDK) 완전 연동** ✅
**콜매니저 완료**:
```kotlin
// call_manager/app/src/main/java/com/designated/callmanager/ptt/manager/SignalingManager.kt
- RTM 2.x 로그인/로그아웃
- 채널 구독/해제
- 메시지 송수신
- PTT 시작/종료 신호 전송
```

**픽업앱 완료**:
```kotlin
// pickup_app/app/src/main/java/com/designated/pickupapp/ptt/manager/SignalingManager.kt
- 콜매니저와 동일한 RTM 기능
- PTTStatus 콜백 연동
- 메시지 파싱 로직 추가
```

### 2. **PTTController RTM 연동** ✅
**콜매니저**:
```kotlin
// PTTController.kt - Line 69-84, 128-148
- RTM 자동 초기화
- PTT 시작 시 RTM 신호 전송
- PTT 종료 시 RTM 신호 전송
- Graceful degradation (RTM 실패해도 PTT 동작)
```

**픽업앱**:
```kotlin
// PTTController.kt - Line 62-103, 125-148
- RTM SignalingManager 통합
- 자동 채널 구독 로직
- PTT 충돌 방지 (PTTLockManager)
```

### 3. **상태 동기화 시스템** ✅
**픽업앱**:
```kotlin
// HomeScreen.kt - StatusCard, TestDebugPanel
- 다른 사용자 PTT 상태 실시간 표시
- "김기사님 PTT 중" UI 구현
- 디버그 패널로 RTM 상태 모니터링
```

### 4. **충돌 방지 시스템** ✅
```kotlin
// PTTLockManager.kt
- Mutex 기반 동시 PTT 방지
- 타임스탬프 우선순위 처리  
- 10초 타임아웃으로 데드락 방지
```

### 5. **테스트 및 디버깅 시스템** ✅
```kotlin
// PTTTestHelper.kt, TestDebugPanel
- 자동 테스트 시나리오
- 실시간 로그 모니터링
- 성능 측정 (메모리, 스레드)
- RTM 상태 실시간 표시
```

---

## ❌ **현재 문제점: 자동 채널 참여 실패**

### 🔴 **핵심 문제: "둘 다 나간 후 한쪽이 PTT 시도 시 자동 참여 안됨"**

#### **문제 상황**:
```
1. A와 B가 PTT 통신 중 ✅ (정상 작동)
2. 통신 종료 → 둘 다 채널에서 나감 ✅
3. A가 다시 PTT 시작 → RTM 메시지 전송
4. B가 RTM 메시지를 수신하지 못함 ❌
5. B의 자동 채널 참여 실패 ❌
```

#### **기술적 원인 분석**:
RTM의 **채널 기반 메시징 특성** 때문:
- RTM 메시지는 **채널에 참여한 사용자에게만** 전달됨
- 둘 다 채널에서 나간 상태 → 메시지 수신 불가
- **P2P 메시징이 아닌 채널 브로드캐스트 방식**

---

## 🔍 **RTM 2.x API 분석 결과**

### **현재 사용 중인 방식 (문제 있음)**:
```kotlin
// 채널 기반 메시징 (Channel Message)
rtmClient?.publish(channelName, message, PublishOptions())
```
- 채널 참여자에게만 전달
- 채널에 없으면 수신 불가

### **대안 방법들**:

#### 1. **User Message (P2P 메시징)** 🔍 **조사 중**
```kotlin
// RTM 2.x User Message API
rtmClient?.sendMessageToUser(userId, message, SendMessageOptions())
```
- 채널 참여 없이도 직접 메시지 전송
- FCM과 유사한 동작 가능성
- **확인 필요**: 상대방이 오프라인일 때 동작 여부

#### 2. **Presence 기반 상태 확인** 🔍 **조사 중**
```kotlin
// 사용자 온라인 상태 확인
rtmClient?.getPresence(channelName, PresenceOptions())
```
- 상대방이 온라인인지 먼저 확인
- 온라인일 때만 RTM 메시지 전송
- 오프라인이면 FCM 백업 사용

#### 3. **상시 채널 구독** 🔍 **구현 검토 중**
```kotlin
// 앱 시작 시 기본 채널에 항상 구독
signalingManager?.subscribeChannel("office_${officeId}_always")
```
- 앱이 실행 중일 때 항상 RTM 채널 유지
- 배터리/네트워크 소모 증가 우려

---

## 🛠️ **해결방안 우선순위**

### **1순위: User Message 방식 도입** 🔥
**목표**: 채널 참여 없이도 직접 메시지 전송
```kotlin
// 구현할 방법
class SignalingManager {
    // 기존: 채널 메시지
    suspend fun sendPttStart(channel: String) {
        rtmClient?.publish(channel, message, options)
    }
    
    // 🆕 추가: P2P 메시지
    suspend fun sendPttStartToUser(userId: String) {
        rtmClient?.sendMessageToUser(userId, message, options)
    }
}
```

**구현 계획**:
1. RTM 2.x User Message API 조사 및 테스트
2. SignalingManager에 P2P 메시징 추가
3. PTTController에서 채널 메시지와 P2P 메시지 병행 사용
4. 수신측에서 User Message 이벤트 처리 추가

### **2순위: 하이브리드 시스템 (RTM + FCM)** 🔄
**목표**: RTM 실패 시 FCM 백업
```kotlin
suspend fun notifyPTTStart(targetUserId: String, channel: String) {
    try {
        // 1차: RTM User Message 시도
        signalingManager?.sendPttStartToUser(targetUserId)
        Log.d(TAG, "RTM message sent successfully")
    } catch (e: Exception) {
        // 2차: FCM 백업
        sendFCMNotification(targetUserId, channel)
        Log.w(TAG, "RTM failed, using FCM backup: $e")
    }
}
```

### **3순위: 상시 채널 구독** ⚡
**목표**: 항상 RTM 채널 유지
```kotlin
class RTMBackgroundManager {
    fun maintainAlwaysOnChannel(officeId: String) {
        val backgroundChannel = "office_${officeId}_background"
        signalingManager?.subscribeChannel(backgroundChannel)
        // 배터리 최적화 예외 설정 필요
    }
}
```

---

## 📋 **구체적 구현 단계**

### **Phase A: RTM User Message 조사 및 구현** (3-5일)

#### A1. API 조사
- [ ] RTM 2.x `sendMessageToUser` API 확인
- [ ] 오프라인 사용자에게 메시지 전송 가능 여부 확인
- [ ] 메시지 수신 이벤트 리스너 확인

#### A2. SignalingManager 확장
```kotlin
// 추가할 메서드들
suspend fun sendPttStartToUser(userId: String, channel: String): Boolean
suspend fun sendPttEndToUser(userId: String, channel: String): Boolean
private fun handleUserMessage(event: UserMessageEvent)
```

#### A3. PTTController 통합
```kotlin
// startPTT에서 이중 발송
suspend fun startPTT() {
    // 기존: 채널 메시지 (현재 참여자용)
    signalingManager?.sendPttStart(channelName)
    
    // 🆕 추가: P2P 메시지 (채널 밖 사용자용)
    getOtherOfficeUsers().forEach { userId ->
        signalingManager?.sendPttStartToUser(userId, channelName)
    }
}
```

### **Phase B: 자동 채널 참여 로직 강화** (2-3일)

#### B1. autoJoinChannel 개선
```kotlin
suspend fun autoJoinChannel(
    channel: String,
    senderUID: Int,
    messageType: String // "CHANNEL_MSG" or "USER_MSG"
): Result<Unit> {
    // User Message에서 온 경우 더 적극적으로 참여
    if (messageType == "USER_MSG") {
        // 강제 채널 참여 로직
    }
}
```

#### B2. 사용자 ID 매핑 시스템
```kotlin
class UserMappingManager {
    // Firebase UID → RTM UserId 매핑
    fun getRtmUserId(firebaseUid: String): String
    fun getOfficeUsers(officeId: String): List<String>
}
```

### **Phase C: 테스트 및 검증** (3-4일)

#### C1. 시나리오 테스트
```kotlin
// PTTTestHelper에 추가할 테스트
fun testAutoJoinAfterBothLeft() {
    // 1. 둘 다 채널 참여
    // 2. 둘 다 채널 나가기  
    // 3. A가 PTT 시작
    // 4. B의 자동 참여 확인
}
```

#### C2. 성능 및 안정성 테스트
- RTM vs FCM 응답 속도 비교
- 배터리 소모량 측정
- 네트워크 사용량 측정
- 오프라인/온라인 전환 테스트

---

## 🎯 **최종 목표와 성공 지표**

### **비용 절감 목표 달성**:
- FCM 서버 비용 → 0원 ✅ **달성 가능**
- RTM만으로 자동 채널 참여 구현 ⚠️ **부분 달성**

### **기능 완성도**:
- [x] RTM 시그널링 연동 (100%)
- [x] 상태 동기화 (100%) 
- [x] 충돌 방지 (100%)
- [ ] **자동 채널 참여 (50%)** ← **현재 작업 중**

### **성공 지표**:
1. **둘 다 나간 후 자동 참여 성공률 95%+**
2. RTM 응답 시간 < 1초 (vs FCM 3-5초)
3. 배터리 소모량 기존 대비 +10% 이내
4. 네트워크 사용량 FCM 대비 50% 절감

---

## 🚨 **현재 차단 요소와 해결 경로**

### **차단 요소**:
1. **RTM User Message API 미조사** → 3일 내 조사 완료 예정
2. **P2P 메시징 구현 경험 부족** → 단계별 프로토타입으로 해결
3. **오프라인 사용자 처리 방안 불명** → FCM 백업 시스템으로 해결

### **리스크 완화 방안**:
- RTM User Message가 불가능하면 → FCM 하이브리드로 전환
- 성능 문제 발생 시 → 상시 구독 방식으로 전환  
- 복잡도 증가 시 → 기존 채널 방식 유지하되 FCM 병행

---

## 💡 **결론**

**현재 상황**: RTM 기반 자동 채널 참여는 **80% 완성**된 상태
**남은 작업**: RTM User Message 방식 도입으로 **완전한 FCM 대체** 구현
**예상 완료**: 2주 내 모든 문제 해결 가능

**핵심은 RTM의 P2P 메시징 기능을 활용하여 채널 참여 없이도 메시지를 전달할 수 있도록 하는 것**입니다. 이를 통해 진정한 **FCM 대체 시스템**을 완성할 수 있을 것으로 예상됩니다.

---

**작성일**: 2025-09-03  
**분석자**: Claude Sonnet 4  
**기반 데이터**: 실제 구현 코드 + 최신 작업 문서 분석  
**다음 액션**: RTM User Message API 조사 및 프로토타입 구현