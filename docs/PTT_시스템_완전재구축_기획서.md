# PTT 시스템 완전 재구축 기획서
## Designated Driver - Push To Talk 통신 시스템

**작성일:** 2025년 1월 15일  
**프로젝트:** 대리운전 콜매니저 ↔ 픽업기사 PTT 통신  
**목표:** 안정적인 양방향 음성 통신 시스템 구축

---

## 📋 목차
1. [현재 상황 분석](#1-현재-상황-분석)
2. [Agora SDK 정책 및 사용방법](#2-agora-sdk-정책-및-사용방법)
3. [Walkietalkie 성공사례 분석](#3-walkietalkie-성공사례-분석)
4. [실패 원인 및 교훈](#4-실패-원인-및-교훈)
5. [재구축 전략](#5-재구축-전략)
6. [단계별 구현 계획](#6-단계별-구현-계획)
7. [실패 방지 체크리스트](#7-실패-방지-체크리스트)
8. [성공 기준](#8-성공-기준)

---

## 1. 현재 상황 분석

### 1.1 기존 시도 결과
| 구분 | 콜매니저 | 픽업앱 | 결과 |
|------|----------|--------|------|
| **수신** | ✅ 성공 | ✅ 성공 | 양방향 수신 가능 |
| **송신** | ✅ 성공 | ❌ Error -17 | 일방향 송신만 가능 |
| **채널 참여** | ✅ 성공 | ❌ 실패 | 권한/설정 문제 |

### 1.2 핵심 문제점
1. **App ID/Token 불일치**: 여러 프로젝트 설정이 혼재
2. **채널 참여 패턴 혼재**: Walkietalkie 방식과 비용절약 방식 충돌
3. **권한 설정 복잡**: 불필요한 권한 누적으로 인한 부작용
4. **상태 관리 오류**: `isConnected` 상태와 실제 연결 상태 불일치
5. **디버깅 복잡성**: 레거시 코드로 인한 원인 파악 어려움

### 1.3 검증된 성공 사례
- **콜매니저 → 픽업앱**: 음성 전달 성공 확인
- **자동 채널 참여**: 수신 대기 모드 정상 작동
- **Walkietalkie 앱**: 완전한 양방향 통신 성공

---

## 2. Agora SDK 정책 및 사용방법

### 2.1 Agora 핵심 개념
```
📡 App ID + Token = 프로젝트 인증 키
🏠 Channel = 음성 통화방 (동적 생성/소멸 가능)
👥 Users = 같은 Channel에 참여해야 통신 가능
🎤 Local Audio = enableLocalAudio(true/false)로 송신 제어
💰 Billing = 채널 연결 시간 기준 과금
```

### 2.2 비용 최적화 패턴 비교

#### 패턴 A: 상시 연결 방식 (Walkietalkie)
```kotlin
// 장점: 즉시 응답, 안정적 통신
// 단점: 높은 비용
onCreate() → joinChannel() → 계속 연결 유지
PTT 시작 → enableLocalAudio(true)
PTT 종료 → enableLocalAudio(false) // 채널 유지
```

#### 패턴 B: 필요시 연결 방식 (목표)
```kotlin
// 장점: 낮은 비용
// 단점: 연결 지연, 복잡한 동기화
PTT 시작 → joinChannel() → enableLocalAudio(true)
PTT 종료 → enableLocalAudio(false) → leaveChannel()
```

### 2.3 Agora 에러 코드 참조
| 코드 | 의미 | 주요 원인 |
|------|------|----------|
| **0** | 성공 | - |
| **-7** | Token 관련 오류 | 만료/불일치/잘못된 App ID |
| **-17** | 채널 참여 거부 | 이미 참여중/권한 부족/네트워크 |
| **-3** | 초기화 실패 | Engine 설정 오류 |

---

## 3. Walkietalkie 성공사례 분석

### 3.1 핵심 성공 요소
```kotlin
// 📍 1. 명확한 설정 (혼재 없음)
private val appId = "a719c12f1d884f778cb768be0a59f819"
private val token = "007eJxTYIi80yOu4P0..." // 검증된 유효한 토큰
private val channelName = "driver_channel" // 고정 채널명

// 📍 2. 단순한 채널 관리
private fun joinAgoraChannelAndSpeak() {
    if (isConnected) {
        startSpeakingActual() // 이미 연결됨 → 바로 송신
    } else {
        rtcEngine?.joinChannel(token, channelName, null, 0) // 미연결 → 참여
    }
}

// 📍 3. 상태 기반 분기 처리
override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
    isConnected = true
    if(awaitingDoubleClickRelease) { // PTT 대기 중이었다면
        startSpeakingActual() // 즉시 송신 시작
    }
}
```

### 3.2 Walkietalkie 아키텍처
```
App 시작 → Engine 초기화 → 권한 확인
              ↓
       사용자 대기 상태
              ↓
    볼륨키 더블클릭 감지
              ↓
   연결상태 확인 → [연결됨] → 즉시 송신 시작
              ↓     [미연결] → 채널 참여 → 성공 시 송신
         키 릴리즈 감지
              ↓
         송신 중단 (채널 유지)
```

---

## 4. 실패 원인 및 교훈

### 4.1 기술적 실패 원인
1. **토큰 관리 실패**
   - 여러 App ID가 섞임 (Walkietalkie용 vs 우리 프로젝트용)
   - 토큰 만료 상태 미확인
   - App Certificate 설정 불일치

2. **상태 관리 오류**
   - `isConnected` 플래그가 실제 연결 상태와 불일치
   - 자동 참여와 수동 참여 로직 충돌
   - 채널 참여 성공 콜백 처리 미흡

3. **권한 설정 과도**
   - 불필요한 시스템 권한 추가로 인한 부작용
   - 권한 요청 순서와 타이밍 문제

### 4.2 프로세스 실패 원인
1. **점진적 수정의 한계**
   - 기존 코드 위에 계속 수정하면서 복잡도 증가
   - 근본 원인보다 증상 치료에 집중
   - 일관성 없는 설정값들이 누적

2. **테스트 방법론 부족**
   - 단계별 검증 없이 전체 기능 테스트
   - 로그 분석 체계 부족
   - 성공/실패 기준 모호

### 4.3 핵심 교훈
```
💡 "복잡한 시스템일수록 단순한 구조에서 시작해야 한다"
💡 "성공 사례(Walkietalkie)를 정확히 복제 후 점진적 변경"
💡 "각 단계별 명확한 성공 기준과 검증 방법 필요"
💡 "설정값 일관성이 코드 로직보다 더 중요"
```

---

## 5. 재구축 전략

### 5.1 기본 원칙
1. **🎯 단순함 우선**: 복잡한 기능보다 안정적인 기본 기능
2. **📊 단계적 검증**: 각 단계별 명확한 성공 기준
3. **🔄 점진적 확장**: 기본 성공 후 추가 기능 개발
4. **📝 철저한 로깅**: 모든 단계에서 상세한 로그 기록
5. **🧪 실환경 테스트**: 실제 기기에서 지속적 테스트

### 5.2 아키텍처 설계

#### 새로운 앱 구조
```
DesignatedDriver-PTT/
├── common/                    # 공통 모듈
│   ├── AgoraConfig.kt        # App ID, Token 통합 관리
│   ├── PTTEngine.kt          # Agora Engine 래퍼
│   └── ChannelSync.kt        # 채널 동기화 로직
├── callmanager-ptt/          # 콜매니저 PTT 앱
│   └── CallManagerPTTActivity.kt
├── pickup-ptt/               # 픽업앱 PTT 앱
│   └── PickupPTTActivity.kt
└── docs/                     # 문서
    └── test-protocols.md     # 테스트 프로토콜
```

#### 데이터 흐름 설계
```
[콜매니저 PTT 시작]
        ↓
    새 채널 생성
        ↓
   Firebase DB 업데이트 → [픽업앱] 채널 정보 수신
        ↓                        ↓
    채널 참여 완료         자동 채널 참여
        ↓                        ↓
   송신 시작 (Local Audio On)   수신 대기
        ↓                        ↓
      음성 전달      →      음성 수신 성공
```

---

## 6. 단계별 구현 계획

### 📅 Phase 1: 기반 설정 (1일차)

#### 6.1.1 새로운 Agora 프로젝트 생성
```bash
# Agora Console에서 수행
1. 새 프로젝트 생성: "DesignatedDriver-PTT-Production"
2. App Certificate 활성화
3. 테스트용 Token 생성 (24시간 유효)
4. 임시 채널명: "test_office_001"
5. 프로젝트 설정 문서화
```

#### 6.1.2 최소 권한 설정
```xml
<!-- AndroidManifest.xml - 필수 권한만 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<!-- 추가 권한은 성공 후 단계적으로 추가 -->
```

#### 6.1.3 성공 기준
- ✅ Agora Console에서 프로젝트 확인 가능
- ✅ App ID, Token 발급 완료
- ✅ 테스트 앱 빌드 성공

---

### 📅 Phase 2: PTT 핵심 로직 구현 (2일차)

#### 6.2.1 단일 테스트 앱 생성
```kotlin
class SimplePTTActivity : ComponentActivity() {
    // 🔒 고정 설정 (변경 금지)
    private val appId = "새로_발급받은_APP_ID"
    private val token = "새로_발급받은_TOKEN"
    private val channelName = "test_channel_001"
    
    // Walkietalkie 코드 그대로 복사
    private fun initializeAgoraEngine() {
        // Walkietalkie MainActivity.kt의 initializeAgoraEngine() 동일하게
    }
    
    // 볼륨키 PTT만 구현 (UI 복잡성 제거)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Walkietalkie의 더블클릭 로직 동일하게 복사
    }
}
```

#### 6.2.2 로깅 시스템 구축
```kotlin
object PTTLogger {
    fun logChannelJoin(result: Int, appId: String, token: String, channel: String) {
        Log.i("PTT_DETAILED", """
            🔍 Channel Join Attempt:
            - App ID: ${appId.take(8)}...
            - Token: ${token.take(20)}...
            - Channel: $channel
            - Result: $result (${getErrorMessage(result)})
            - Timestamp: ${System.currentTimeMillis()}
        """.trimIndent())
    }
}
```

#### 6.2.3 성공 기준
- ✅ 앱 실행 시 Engine 초기화 성공 (로그 확인)
- ✅ 볼륨키 더블클릭 감지 성공
- ✅ 모든 Agora API 호출에서 result = 0

---

### 📅 Phase 3: 단일 기기 테스트 (3일차)

#### 6.3.1 검증 항목
```kotlin
// 테스트 체크리스트
class PTTTestValidator {
    fun validateChannelJoin(): Boolean {
        // ✅ joinChannel result = 0
        // ✅ onJoinChannelSuccess 콜백 호출
        // ✅ isConnected = true 설정
    }
    
    fun validatePTTStart(): Boolean {
        // ✅ enableLocalAudio(true) result = 0
        // ✅ 마이크 권한 확인
        // ✅ 송신 상태 UI 업데이트
    }
    
    fun validatePTTEnd(): Boolean {
        // ✅ enableLocalAudio(false) result = 0
        // ✅ 수신 대기 상태로 복귀
    }
}
```

#### 6.3.2 문제 해결 프로토콜
```
⚠️ 문제 발생 시 순서:
1. 로그에서 result 코드 확인
2. Agora Console에서 프로젝트 상태 확인
3. Token 유효기간 확인
4. 네트워크 연결 확인
5. Walkietalkie 앱과 설정값 비교
```

#### 6.3.3 성공 기준
- ✅ 3회 연속 채널 참여 성공
- ✅ PTT 시작/종료 10회 연속 성공
- ✅ 30분간 안정적 연결 유지

---

### 📅 Phase 4: 멀티 기기 테스트 (4일차)

#### 6.4.1 동일 APK 테스트
```
📱 기기 A (Galaxy S21): 테스트 APK 설치
📱 기기 B (iPhone): 불가 → Android 기기 2대 준비
    대안: Android 에뮬레이터 + 실제 기기

테스트 시나리오:
1. 기기 A PTT 시작 → 기기 B 음성 수신 확인
2. 기기 B PTT 시작 → 기기 A 음성 수신 확인  
3. 교대로 10회 대화 테스트
4. 동시 PTT 상황 테스트 (충돌 처리)
```

#### 6.4.2 네트워크 테스트
```
📶 WiFi 환경 테스트
📶 LTE 환경 테스트  
📶 WiFi ↔ LTE 혼합 테스트
📶 네트워크 끊김 복구 테스트
```

#### 6.4.3 성공 기준
- ✅ 양방향 음성 통신 성공
- ✅ 네트워크 변경 시 자동 복구
- ✅ 음질 만족도 확인

---

### 📅 Phase 5: 앱 분리 및 역할 정의 (5일차)

#### 6.5.1 콜매니저 PTT 앱
```kotlin
class CallManagerPTTActivity : ComponentActivity() {
    private val userType = "CALL_MANAGER"
    private val appId = "동일한_APP_ID" // Phase 4에서 검증된 설정
    private val token = "동일한_TOKEN"
    
    // 🎯 콜매니저 고유 기능
    private fun generateChannelName(officeId: String): String {
        return "office_${officeId}_ptt_${System.currentTimeMillis()}"
    }
    
    private fun notifyPickupApps(channelName: String) {
        // Firebase DB에 채널 정보 공유
    }
}
```

#### 6.5.2 픽업앱 PTT 앱
```kotlin
class PickupPTTActivity : ComponentActivity() {
    private val userType = "PICKUP_DRIVER"
    private val appId = "동일한_APP_ID" // 콜매니저와 완전 동일
    private val token = "동일한_TOKEN"
    
    // 🎯 픽업앱 고유 기능
    private fun listenForChannelInvites() {
        // Firebase DB에서 채널 정보 구독
    }
    
    private fun autoJoinChannel(channelName: String) {
        // 자동 채널 참여 로직
    }
}
```

#### 6.5.3 성공 기준
- ✅ 두 앱 모두 독립적으로 빌드 성공
- ✅ 각 앱의 고유 기능 동작 확인
- ✅ 공통 설정값 일관성 유지

---

### 📅 Phase 6: 채널 동기화 메커니즘 (6일차)

#### 6.6.1 Firebase Realtime Database 방식
```kotlin
// 채널 정보 공유 구조
data class ChannelInfo(
    val channelName: String,
    val createdBy: String, // "CALL_MANAGER" or "PICKUP_DRIVER"
    val officeId: String,
    val timestamp: Long,
    val isActive: Boolean
)

// 콜매니저: 채널 생성 시 정보 공유
private fun shareChannelInfo(channelName: String) {
    val channelInfo = ChannelInfo(
        channelName = channelName,
        createdBy = "CALL_MANAGER",
        officeId = currentOfficeId,
        timestamp = System.currentTimeMillis(),
        isActive = true
    )
    
    database.getReference("active_channels/$currentOfficeId")
        .setValue(channelInfo)
}

// 픽업앱: 채널 정보 구독
private fun subscribeToChannelUpdates() {
    database.getReference("active_channels/$currentOfficeId")
        .addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val channelInfo = snapshot.getValue(ChannelInfo::class.java)
                if (channelInfo?.isActive == true && !isConnected) {
                    autoJoinChannel(channelInfo.channelName)
                }
            }
        })
}
```

#### 6.6.2 대안: FCM 푸시 알림 방식
```kotlin
// 콜매니저: PTT 시작 시 알림 전송
private fun notifyPickupApps(channelName: String) {
    val message = RemoteMessage.Builder("pickup_app_topic")
        .setData(mapOf(
            "type" to "PTT_CHANNEL_INVITE",
            "channelName" to channelName,
            "officeId" to currentOfficeId,
            "senderType" to "CALL_MANAGER"
        ))
        .build()
        
    FirebaseMessaging.getInstance().send(message)
}

// 픽업앱: 알림 수신 처리
override fun onMessageReceived(remoteMessage: RemoteMessage) {
    if (remoteMessage.data["type"] == "PTT_CHANNEL_INVITE") {
        val channelName = remoteMessage.data["channelName"]
        if (channelName != null) {
            autoJoinChannel(channelName)
        }
    }
}
```

#### 6.6.3 성공 기준
- ✅ 콜매니저 PTT → 픽업앱 자동 참여 (3초 이내)
- ✅ 픽업앱 PTT → 콜매니저 자동 참여 (3초 이내)
- ✅ 네트워크 지연 시에도 정상 동작

---

### 📅 Phase 7: 통합 테스트 및 최적화 (7일차)

#### 6.7.1 최종 검증 시나리오
```
🧪 시나리오 1: 기본 통신
1. 콜매니저 PTT 시작
2. 픽업앱 자동 참여 확인 (3초 이내)
3. 음성 전달 확인
4. PTT 종료 후 채널 정리 확인

🧪 시나리오 2: 역방향 통신  
1. 픽업앱 PTT 시작
2. 콜매니저 자동 참여 확인 (3초 이내)
3. 음성 전달 확인
4. PTT 종료 후 채널 정리 확인

🧪 시나리오 3: 연속 대화
1. 콜매니저 → 픽업앱 (10초)
2. 픽업앱 → 콜매니저 (10초)
3. 5회 반복
4. 음질 및 연결 안정성 확인

🧪 시나리오 4: 예외 상황
1. 네트워크 끊김 중 PTT 시도
2. 배터리 절약 모드에서 동작
3. 다른 앱 사용 중 PTT 수신
4. 긴급 상황 시 우선순위 처리
```

#### 6.7.2 성능 최적화
```kotlin
// 배터리 최적화 예외 요청
private fun requestBatteryOptimizationException() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${packageName}")
        startActivity(intent)
    }
}

// 채널 정리 자동화
private fun scheduleChannelCleanup(channelName: String) {
    Handler(Looper.getMainLooper()).postDelayed({
        if (!isSpeaking && isConnected) {
            leaveChannel()
            markChannelInactive(channelName)
        }
    }, 30000) // 30초 후 자동 정리
}
```

#### 6.7.3 최종 성공 기준
- ✅ 양방향 통신 성공률 98% 이상
- ✅ 채널 참여 시간 3초 이내
- ✅ 음질 선명도 만족
- ✅ 배터리 효율성 확인
- ✅ 24시간 안정성 테스트 통과

---

## 7. 실패 방지 체크리스트

### 7.1 매일 수행 체크리스트

#### 🔍 기술적 확인사항
```
□ Agora Console에서 프로젝트 활성 상태 확인
□ Token 유효기간 확인 (만료 24시간 전 갱신 알림)
□ 로그에서 모든 Agora API result=0 확인
□ 실제 음성 송수신 테스트 (최소 1회)
□ 두 앱 모두 동일한 App ID/Token 사용 확인
□ Firebase DB 연결 상태 확인
□ 네트워크 연결 안정성 확인
```

#### 📋 프로세스 확인사항
```
□ 당일 작업 목표 달성 여부 확인
□ 발생한 문제점 문서화
□ 다음 단계 준비사항 점검
□ 코드 백업 및 버전 관리
□ 테스트 결과 기록 업데이트
```

### 7.2 문제 발생 시 대응 프로토콜

#### 🚨 1단계: 즉시 확인사항
```
1. 로그에서 최근 error/result 코드 확인
2. Agora Console에서 실시간 모니터링 확인
3. 네트워크 연결 상태 확인
4. 앱 권한 설정 확인
5. 기기 재부팅 후 재테스트
```

#### 🔧 2단계: 상세 분석
```
1. Walkietalkie 앱과 설정값 1:1 비교
2. Firebase Console에서 데이터 동기화 확인
3. 다른 기기에서 동일 현상 재현 시도
4. 이전 성공 버전과 차이점 분석
5. Agora 공식 문서에서 에러 코드 조회
```

#### 🛠️ 3단계: 해결 시도
```
1. 문제 격리 (한 앱씩 단독 테스트)
2. 설정값 초기화 후 재설정
3. Token 재발급 및 적용
4. 최소 권한으로 되돌리기
5. 이전 성공 버전으로 롤백
```

#### 📞 4단계: 외부 지원
```
1. Agora 공식 지원팀 문의
2. 개발자 커뮤니티 질문 게시
3. Firebase 지원팀 문의
4. 전문가 컨설팅 요청
```

### 7.3 품질 보증 체크리스트

#### 📊 성능 지표 모니터링
```
□ 채널 참여 성공률: __% (목표: 98% 이상)
□ 평균 참여 시간: __초 (목표: 3초 이내)
□ 음성 품질 점수: __/10 (목표: 8점 이상)
□ 배터리 소모율: __%/시간 (목표: 5% 이하)
□ 메모리 사용량: __MB (목표: 100MB 이하)
```

#### 🧪 테스트 커버리지
```
□ 단일 기기 테스트: ___회 성공 / ___회 시도
□ 멀티 기기 테스트: ___회 성공 / ___회 시도
□ 네트워크 변경 테스트: ___회 성공 / ___회 시도
□ 장시간 안정성 테스트: ___시간 연속 성공
□ 예외 상황 테스트: ___개 시나리오 통과
```

---

## 8. 성공 기준

### 8.1 기술적 성공 기준

#### 🎯 필수 요구사항 (Must Have)
```
✅ 콜매니저 PTT → 픽업앱에서 음성 수신 성공
✅ 픽업앱 PTT → 콜매니저에서 음성 수신 성공  
✅ 양방향 통신 안정성 98% 이상
✅ 채널 참여 시간 3초 이내
✅ 음성 품질 명료도 만족
```

#### 🚀 선택 요구사항 (Nice to Have)
```
⭐ 비용 최적화 (필요시에만 연결)
⭐ 사용자 친화적 UI/UX
⭐ 백그라운드 동작 지원
⭐ 다중 사무실 지원
⭐ 통화 기록 및 모니터링
```

### 8.2 비즈니스 성공 기준

#### 📈 운영 지표
```
• 일일 PTT 사용 횟수: 목표 50회 이상
• 사용자 만족도: 목표 4.5/5.0 이상
• 기술 지원 문의: 목표 주 5건 이하
• 시스템 가동률: 목표 99.9% 이상
• 월간 운영 비용: 목표 예산 내 유지
```

#### 🎯 사용자 경험 지표
```
• PTT 응답 시간: 3초 이내
• 음성 품질 만족도: 80% 이상
• 연결 실패율: 2% 이하
• 사용법 학습 시간: 5분 이내
• 일일 평균 사용 시간: 30분 이상
```

### 8.3 최종 검증 체크리스트

#### ✅ 출시 전 최종 확인
```
□ 모든 필수 요구사항 100% 달성
□ 7일간 연속 안정성 테스트 통과
□ 다양한 Android 기기에서 호환성 확인
□ 보안 취약점 점검 완료
□ 사용자 매뉴얼 및 문서 완성
□ 운영진 교육 완료
□ 백업 및 복구 계획 수립
□ 모니터링 시스템 구축 완료
```

---

## 📞 연락처 및 지원

**프로젝트 관리자:** [이름]  
**기술 책임자:** [이름]  
**Agora 지원:** support@agora.io  
**Firebase 지원:** firebase-support@google.com  

**문서 버전:** v1.0  
**최종 수정일:** 2025년 1월 15일  
**다음 검토일:** 2025년 1월 22일

---

> 💡 **중요:** 이 문서는 PTT 시스템 재구축의 완전한 로드맵입니다. 각 단계별로 체크리스트를 준수하고, 문제 발생 시 즉시 대응 프로토콜을 따라주세요. 성공적인 구현을 위해 단계별 검증을 철저히 수행하시기 바랍니다.