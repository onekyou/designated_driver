# RTM 기반 자동채널참여 현황분석 및 해결방안 질문지

## 📋 **현재 상황 요약**

### ✅ **정상 작동하는 것**
- **RTC 음성 통신**: 양쪽 기기에서 PTT 송신 완벽 작동
- **PTT 서비스**: PTTForegroundService 양쪽 모두 정상 실행 중
- **Firebase Auth**: 로그인 상태 (앱 진입 가능)
- **AGORA_APP_ID**: 설정됨 (RTC 송신 가능하므로 확인됨)

### ❌ **작동하지 않는 것**  
- **자동채널참여**: 상대방이 PTT 시작해도 수신측이 채널에 자동 참여 안 됨
- **RTM 메시지 수신**: 어떤 RTM 관련 로그도 확인되지 않음

---

## 🔍 **기술적 상세 정보**

### **1. 프로젝트 구조**
- **앱 A**: 콜매니저 (com.designated.callmanager)
- **앱 B**: 픽업앱 (com.designated.pickupapp) 
- **두 기기**: 실제 안드로이드 기기에서 테스트 중
- **네트워크**: 동일 Wi-Fi 환경

### **2. 구현된 RTM 아키텍처**
```
PTT 시작 → PTTController.startPTT() → RTM 신호 전송
      ↓
SignalingManager.sendPttStart() → Agora RTM 2.x
      ↓
상대방 SignalingManager.onMessageEvent() → 자동참여 트리거 (실패)
```

### **3. 핵심 구현 코드**

#### **RTM 초기화 (PTTController.kt)**
```kotlin
private fun initializeRTMIfPossible() {
    rtmScope.launch {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null && BuildConfig.AGORA_APP_ID.isNotEmpty()) {
            signalingManager = SignalingManager(context, BuildConfig.AGORA_APP_ID, userId)
            signalingManager?.initialize { success ->
                if (success) {
                    signalingManager?.login()
                    Log.i(TAG, "RTM initialized successfully") // 🚨 이 로그가 안 나옴
                }
            }
        }
    }
}
```

#### **채널 구독 (PTTController.kt)**  
```kotlin
fun setDefaultChannelInfo(regionId: String, officeId: String) {
    defaultRegionId = regionId
    defaultOfficeId = officeId
    subscribeToRTMChannelIfReady() // 🚨 호출되지만 효과 없음
}
```

#### **RTM 메시지 송신 (PTTController.startPTT)**
```kotlin
// 8. RTM 시작 신호 전송 (실패해도 PTT는 정상 동작)
sendRTMStartSignal(channelName, finalUID) // 🚨 호출되지만 수신 안 됨
```

#### **자동참여 트리거 (SignalingManager.kt)**
```kotlin
private fun triggerAutoJoin(publisherId: String, message: String) {
    val intent = Intent(context, PTTForegroundService::class.java).apply {
        action = PTTForegroundService.ACTION_AUTO_JOIN
        putExtra(PTTForegroundService.EXTRA_CHANNEL, channel)
        putExtra(PTTForegroundService.EXTRA_SENDER_UID, senderUid)
    }
    context.startForegroundService(intent) // 🚨 실행되지 않음
}
```

---

## 🔬 **진단 테스트 결과**

### **서비스 상태 확인**
```bash
adb shell dumpsys activity services | grep PTTForegroundService
```
**결과**: ✅ 양쪽 모두 정상 실행 중
- 콜매니저: `foregroundId=1001`, 7분간 실행 중  
- 픽업앱: `foregroundId=2001`, 8분간 실행 중

### **RTM 로그 확인**
```bash
adb logcat | grep -E "(RTM|SignalingManager|onMessageEvent)"
```
**결과**: ❌ **아무 로그도 없음** (가장 의심스러운 부분)

### **Firebase Auth 상태**
**결과**: ✅ 로그인됨 (앱 실행 가능)

---

## ❓ **전문가님께 드리는 질문**

### **Q1. RTM 초기화 실패 원인**
RTM 관련 로그가 전혀 없다는 것은 초기화조차 되지 않았다는 의미인가요?
- Firebase Auth는 정상
- AGORA_APP_ID도 설정됨 (RTC는 작동)
- 그런데 RTM만 초기화 안 되는 이유가 있나요?

### **Q2. RTM vs RTC 차이점**
RTC(음성)는 정상 작동하는데 RTM(메시징)은 작동 안 하는 경우가 있나요?
- 동일한 AGORA_APP_ID 사용
- 동일한 네트워크 환경
- 서로 다른 초기화 조건이 있나요?

### **Q3. RTM 2.x 디버깅 방법**
RTM이 초기화되었는지, 로그인되었는지, 채널 구독이 되었는지 확인하는 가장 확실한 방법은?

### **Q4. 네트워크/방화벽 이슈**  
RTM이 RTC와 다른 포트나 프로토콜을 사용하나요?
- 회사/가정용 Wi-Fi에서 RTM만 차단되는 경우가 있나요?

### **Q5. 코드 구조 문제**
현재 구조에서 명백한 오류가 있나요?
```kotlin
// 이 순서가 맞나요?
1. RTM 초기화
2. 채널 구독  
3. PTT 시작 시 RTM 메시지 전송
4. 상대방에서 메시지 수신 → 자동참여
```

### **Q6. 권한/매니페스트 이슈**
RTM에 추가로 필요한 권한이나 설정이 있나요?
- 현재는 RTC용 권한만 설정됨
- RTM 전용 권한이 별도로 필요한가요?

---

## 📱 **테스트 환경 정보**

- **Android 버전**: 다양 (두 기기)
- **네트워크**: 동일 Wi-Fi 환경  
- **Agora SDK 버전**: 
  - RTC: `io.agora.rtc:full-sdk:4.2.3` ✅ 작동
  - RTM: `io.agora:agora-rtm:2.2.4` ❌ 작동 안 함
- **빌드 환경**: Android Studio, Kotlin

---

## 💡 **기대하는 답변**

1. **근본 원인 1가지** (추측이 아닌 명확한 원인)
2. **즉시 테스트할 수 있는 해결방법** 
3. **RTM 디버깅을 위한 추가 로그 코드**

현재 **송신은 완벽하게 작동**하고, **서비스도 정상 실행** 중인데 **RTM 메시지 수신만 안 되는** 상황입니다.

가장 의심스러운 것은 **RTM 초기화조차 되지 않는 것** 같다는 점입니다.