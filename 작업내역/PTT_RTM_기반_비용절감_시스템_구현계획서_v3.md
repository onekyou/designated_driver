# PTT RTM 기반 비용절감 시스템 구현 계획서 v3

## 프로젝트 개요

**목표**: 대리운전 앱 PTT 시스템에서 Agora RTM(Real-time Messaging) 기반 시그널링을 도입하여 "2초 이내 응답성"과 "90% 비용 절감"을 동시에 달성하는 소규모 그룹 최적화 시스템 구축

**대상 환경**: 
- 1명의 관리자 + 3-4명의 픽업 기사 소규모 그룹
- 실시간 음성 통신 필요
- 비용 효율성과 응답성의 균형 중시

**핵심 전략**: "반응형 항상 연결" - RTM 채널 상시 연결 + Voice 채널 필요시 활성화

## 1. 시스템 아키텍처 설계

### 1.1 전체 구조도
```
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│    관리자 앱        │    │   기사 앱 #1        │    │   기사 앱 #N        │
│  - RTM 항상 연결    │    │  - RTM 항상 연결    │    │  - RTM 항상 연결    │
│  - Voice 필요시     │    │  - Voice 필요시     │    │  - Voice 필요시     │
└─────────┬───────────┘    └─────────┬───────────┘    └─────────┬───────────┘
          │                          │                          │
          └──────────────────────────┼──────────────────────────┘
                                     │
                           ┌─────────┴──────────┐
                           │   Agora Platform   │
                           │                    │
                           │ ┌────────────────┐ │
                           │ │ RTM Channel    │ │ ← 시그널링 (상시 연결)
                           │ └────────────────┘ │
                           │ ┌────────────────┐ │
                           │ │ Voice Channel  │ │ ← 음성 통신 (필요시)
                           │ └────────────────┘ │
                           └────────────────────┘
```

### 1.2 핵심 컴포넌트 설계

#### A. RTM 시그널링 매니저
```kotlin
class PTTRTMSignalManager {
    - rtmClient: RtmClient
    - rtmChannel: RtmChannel
    - connectionState: RTMConnectionState
    - signalHandlers: Map<String, SignalHandler>
    
    + initializeRTM()
    + joinRTMChannel(channelName: String)
    + sendPTTSignal(type: PTTSignalType, data: PTTSignalData)
    + handleIncomingSignal(message: RtmMessage)
    + leaveRTMChannel()
}
```

#### B. 음성 채널 매니저 (기존 개선)
```kotlin
class PTTVoiceChannelManager {
    - agoraEngine: RtcEngine
    - currentVoiceChannel: String?
    - voiceConnectionState: VoiceConnectionState
    
    + joinVoiceChannel(channelName: String, uid: Int)
    + leaveVoiceChannel()
    + startTransmission()
    + stopTransmission()
    + handleVoiceEvents()
}
```

#### C. 통합 PTT 컨트롤러
```kotlin
class IntegratedPTTController {
    - rtmManager: PTTRTMSignalManager
    - voiceManager: PTTVoiceChannelManager
    - stateManager: PTTStateManager
    
    + initializePTTSystem()
    + handlePTTButtonPress()
    + handlePTTButtonRelease()
    + handleIncomingPTTSignal()
    + optimizeConnections()
}
```

### 1.3 신호 프로토콜 정의

#### PTT 신호 타입
```kotlin
enum class PTTSignalType {
    PTT_START,           // PTT 시작 신호
    PTT_STOP,            // PTT 종료 신호
    VOICE_CHANNEL_READY, // 음성 채널 준비 완료
    EMERGENCY_CALL,      // 긴급 호출
    CHANNEL_CLEANUP,     // 채널 정리 신호
    USER_STATUS_UPDATE   // 사용자 상태 업데이트
}

data class PTTSignalData(
    val signalId: String,
    val senderId: String,
    val targetChannel: String,
    val timestamp: Long,
    val priority: SignalPriority = SignalPriority.NORMAL,
    val metadata: Map<String, Any> = emptyMap()
)
```

## 2. 소규모 그룹 최적화 전략

### 2.1 "Leader-Follower" 하이브리드 모델

#### 관리자 우선 연결 전략
- **관리자 앱**: RTM 채널 + Voice 채널 항상 연결 (STANDBY 상태)
- **기사 앱**: RTM 채널만 상시 연결, Voice 채널은 신호 수신 시 활성화

#### 구현 세부사항
```kotlin
class ManagerOptimizedPTTService : PTTForegroundService() {
    
    override fun initializePTTConnections() {
        // RTM 채널 연결
        rtmManager.joinRTMChannel("${regionId}_${officeId}_signal")
        
        // 관리자는 Voice 채널도 미리 연결 (음소거 상태)
        if (userRole == UserRole.MANAGER) {
            voiceManager.joinVoiceChannel("${regionId}_${officeId}_voice", localUid)
            voiceManager.muteLocalAudio(true)
        }
    }
    
    private fun handleManagerPTT() {
        // 관리자 PTT - 즉시 음성 전송 가능
        voiceManager.muteLocalAudio(false)
        rtmManager.sendPTTSignal(PTTSignalType.PTT_START, 
            PTTSignalData(
                signalId = generateSignalId(),
                senderId = localUid.toString(),
                targetChannel = currentVoiceChannel
            )
        )
    }
    
    private fun handleDriverPTT() {
        // 기사 PTT - RTM 신호 전송 후 Voice 채널 합류
        rtmManager.sendPTTSignal(PTTSignalType.PTT_START, 
            PTTSignalData(
                signalId = generateSignalId(),
                senderId = localUid.toString(),
                targetChannel = "${regionId}_${officeId}_voice"
            )
        )
        
        // Voice 채널 합류 (비동기)
        voiceManager.joinVoiceChannel("${regionId}_${officeId}_voice", localUid)
    }
}
```

### 2.2 지능형 연결 최적화

#### 자동 채널 정리 시스템
```kotlin
class ChannelCleanupManager {
    private val inactivityTimeout = 60_000L // 60초
    private val cleanupTimer = Timer()
    
    fun startInactivityMonitoring() {
        cleanupTimer.schedule(object : TimerTask() {
            override fun run() {
                if (isVoiceChannelInactive()) {
                    scheduleVoiceChannelCleanup()
                }
            }
        }, inactivityTimeout, inactivityTimeout)
    }
    
    private fun scheduleVoiceChannelCleanup() {
        rtmManager.sendPTTSignal(PTTSignalType.CHANNEL_CLEANUP,
            PTTSignalData(
                signalId = generateSignalId(),
                senderId = localUid.toString(),
                targetChannel = currentVoiceChannel,
                metadata = mapOf("reason" to "inactivity")
            )
        )
        
        // 모든 참여자가 정리 신호를 받으면 Voice 채널에서 나감
        scheduleVoiceChannelLeave(5000) // 5초 후 자동 정리
    }
}
```

## 3. RTM + Voice 채널 통합 전략

### 3.1 실시간 시그널링 플로우

#### PTT 시작 플로우
```
1. 사용자 PTT 버튼 누름
   ↓
2. RTM 채널을 통해 PTT_START 신호 즉시 전송 (밀리초 단위)
   ↓
3. 모든 그룹 구성원이 신호 수신
   ↓
4. Voice 채널 합류 (필요시)
   - 관리자: 이미 연결됨, 음소거 해제만
   - 기사: 즉시 채널 합류 시작
   ↓
5. 음성 전송 시작 (Voice 채널 준비 완료 후)
   ↓
6. 실시간 음성 통신
```

#### RTM 메시지 처리 최적화
```kotlin
class OptimizedRTMMessageHandler : RtmChannelListener {
    
    override fun onMemberJoined(member: RtmChannelMember?) {
        Log.d(TAG, "RTM member joined: ${member?.userId}")
        updateGroupMemberStatus(member?.userId, true)
    }
    
    override fun onMessageReceived(message: RtmMessage?, member: RtmChannelMember?) {
        val signalData = parseSignalData(message?.text)
        
        when (signalData.type) {
            PTTSignalType.PTT_START -> {
                handlePTTStartSignal(signalData)
            }
            PTTSignalType.PTT_STOP -> {
                handlePTTStopSignal(signalData)
            }
            PTTSignalType.EMERGENCY_CALL -> {
                handleEmergencyCall(signalData)
            }
        }
    }
    
    private fun handlePTTStartSignal(signal: PTTSignalData) {
        // 즉시 UI 업데이트
        updatePTTState(PTTState.IncomingTransmission(signal.senderId))
        
        // Voice 채널 자동 합류 (기사인 경우)
        if (userRole == UserRole.DRIVER && !voiceManager.isConnected()) {
            voiceManager.joinVoiceChannel(signal.targetChannel, localUid)
        }
        
        // 비프음 재생
        playIncomingTransmissionBeep()
    }
}
```

### 3.2 응답성 최적화 구현

#### RTM 연결 최적화
```kotlin
class RTMConnectionOptimizer {
    
    fun optimizeRTMConnection() {
        rtmClient.setParameters("""
        {
            "rtm.log_filter": 2,
            "rtm.msg_timeout": 5000,
            "rtm.network_type": "wifi_4g",
            "rtm.connection_pool_size": 3
        }
        """)
    }
    
    fun enableLowLatencyMode() {
        rtmClient.setParameters("""
        {
            "rtm.enable_low_latency": true,
            "rtm.heartbeat_interval": 3000,
            "rtm.reconnect_interval": 1000
        }
        """)
    }
}
```

### 3.3 비용 절감 메커니즘

#### Voice 채널 사용 최소화
```kotlin
class CostOptimizationManager {
    private val voiceChannelUsageTracker = VoiceChannelUsageTracker()
    
    fun trackVoiceChannelUsage() {
        voiceChannelUsageTracker.startSession()
    }
    
    fun optimizeVoiceChannelUsage() {
        // 30초 이상 비활성 시 자동 연결 해제
        if (voiceChannelUsageTracker.getInactiveTime() > 30_000) {
            scheduleVoiceChannelDisconnect()
        }
        
        // 그룹 내 모든 사용자가 비활성 상태면 즉시 해제
        if (areAllUsersInactive()) {
            immediateVoiceChannelCleanup()
        }
    }
    
    fun generateUsageReport(): VoiceChannelUsageReport {
        return VoiceChannelUsageReport(
            totalConnectedTime = voiceChannelUsageTracker.getTotalTime(),
            activeTransmissionTime = voiceChannelUsageTracker.getActiveTime(),
            costEfficiencRate = calculateCostEfficiency()
        )
    }
}
```

## 4. 구현 단계별 세부 계획

### Phase 1: RTM 시그널링 기반 구축 (1-2주)

#### 1.1 RTM SDK 통합 및 기본 설정
```kotlin
// 새로 추가할 파일들
- PTTRTMManager.kt
- RTMSignalHandler.kt
- RTMConnectionState.kt
- RTMConfigurationUtil.kt

// 수정할 기존 파일들
- PTTForegroundService.kt (RTM 통합)
- PTTState.kt (RTM 상태 추가)
- PTTScreen.kt (RTM 상태 UI)
```

#### 구현 세부 작업
1. **RTM SDK 의존성 추가**
   ```gradle
   implementation 'io.agora.rtm:rtm-sdk:1.5.8'
   ```

2. **RTM 매니저 구현**
   ```kotlin
   class PTTRTMManager(private val context: Context) {
       private var rtmClient: RtmClient? = null
       private var rtmChannel: RtmChannel? = null
       
       fun initialize(appId: String): Boolean {
           try {
               rtmClient = RtmClient.createInstance(context, appId, rtmEventListener)
               return true
           } catch (e: Exception) {
               Log.e(TAG, "Failed to initialize RTM", e)
               return false
           }
       }
   }
   ```

3. **시그널링 프로토콜 구현**
   - JSON 기반 메시지 포맷 정의
   - 메시지 직렬화/역직렬화 로직
   - 신호 타입별 핸들러 매핑

#### 검증 기준
- RTM 채널 연결/해제 안정성 테스트
- 메시지 전송 지연시간 측정 (목표: 100ms 이하)
- 네트워크 재연결 시나리오 테스트

### Phase 2: Voice 채널 통합 및 최적화 (1-2주)

#### 2.1 기존 Voice 채널 시스템 개선
```kotlin
class EnhancedPTTVoiceManager : PTTVoiceManager {
    
    override fun joinVoiceChannel(channelName: String, uid: Int, role: UserRole) {
        when (role) {
            UserRole.MANAGER -> {
                // 관리자는 음소거 상태로 미리 연결
                super.joinVoiceChannel(channelName, uid)
                agoraEngine?.muteLocalAudioStream(true)
            }
            UserRole.DRIVER -> {
                // 기사는 RTM 신호 수신 시에만 연결
                super.joinVoiceChannel(channelName, uid)
            }
        }
    }
}
```

#### 2.2 자동 채널 관리 시스템
```kotlin
class AutoChannelManager {
    fun startAutoChannelMonitoring() {
        monitoringJob = serviceScope.launch {
            while (isActive) {
                checkChannelActivity()
                delay(MONITORING_INTERVAL)
            }
        }
    }
    
    private suspend fun checkChannelActivity() {
        if (getLastActivityTime() > INACTIVITY_THRESHOLD) {
            triggerChannelCleanup()
        }
    }
}
```

#### 검증 기준
- Voice 채널 합류 시간 측정 (목표: 2초 이하)
- 자동 정리 시스템 동작 확인
- 다중 사용자 시나리오 테스트

### Phase 3: 소규모 그룹 최적화 적용 (1주)

#### 3.1 Leader-Follower 모델 구현
- 관리자/기사 역할별 연결 전략 차별화
- 우선순위 기반 PTT 처리
- 그룹 상태 동기화 메커니즘

#### 3.2 비용 최적화 로직
- 사용 시간 추적 및 리포팅
- 자동 연결 해제 정책
- 비용 효율성 지표 수집

#### 검증 기준
- 비용 절감률 측정 (목표: 90%)
- 응답 시간 측정 (목표: 2초 이하)
- 실제 운영 환경 시뮬레이션

### Phase 4: UI/UX 개선 및 통합 테스트 (1주)

#### 4.1 사용자 인터페이스 개선
```kotlin
@Composable
fun EnhancedPTTStatusCard(pttState: EnhancedPTTState) {
    Card {
        Column {
            when (pttState) {
                is EnhancedPTTState.RTMConnected -> {
                    Text("RTM 연결됨 - 즉시 통신 가능")
                    Text("응답 지연: ${pttState.latency}ms")
                }
                is EnhancedPTTState.VoiceChannelConnecting -> {
                    CircularProgressIndicator()
                    Text("음성 채널 연결 중...")
                }
                is EnhancedPTTState.OptimizedTransmitting -> {
                    Text("최적화된 송신 모드")
                    Text("비용 절감: ${pttState.costSaving}%")
                }
            }
        }
    }
}
```

#### 4.2 종합 성능 테스트
- 장시간 안정성 테스트
- 네트워크 변화 대응 테스트
- 실제 사용 시나리오 검증

## 5. 기술 요구사항 및 제약사항

### 5.1 SDK 버전 및 의존성
```gradle
dependencies {
    // 기존
    implementation 'io.agora.rtc:full-sdk:4.2.6'
    
    // 새로 추가
    implementation 'io.agora.rtm:rtm-sdk:1.5.8'
    
    // RTM-Voice 연동을 위한 유틸리티
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0'
}
```

### 5.2 권한 및 설정
```xml
<!-- AndroidManifest.xml에 추가 -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />

<!-- RTM 백그라운드 연결을 위한 설정 -->
<service
    android:name=".ptt.service.RTMBackgroundService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="connectedDevice" />
```

### 5.3 성능 지표 및 목표
- **응답 시간**: RTM 신호 전송 100ms 이하, 전체 PTT 응답 2초 이하
- **비용 절감**: 기존 대비 90% 절감 (Voice 채널 사용시간 최소화)
- **안정성**: 24시간 연속 운영 시 연결 끊김 0.1% 이하
- **배터리 효율성**: RTM 연결로 인한 추가 배터리 소모 5% 이하

### 5.4 제약사항 및 고려사항
1. **RTM 메시지 크기 제한**: 최대 32KB
2. **RTM 채널 동시 참여자**: 최대 100명 (소규모 그룹이므로 충분)
3. **네트워크 변화 대응**: Wi-Fi ↔ 모바일 데이터 전환 시 자동 재연결
4. **배터리 최적화**: Doze 모드에서도 RTM 연결 유지 필요

## 6. 비용 분석 및 효과 예측

### 6.1 기존 시스템 대비 비용 구조 변화

#### 기존 (Voice 채널 상시 연결)
- 관리자 1명: 8시간 × 30일 = 240시간/월
- 기사 4명: 8시간 × 30일 × 4명 = 960시간/월
- **총 Voice 채널 사용**: 1,200시간/월

#### 새로운 시스템 (RTM + 필요시 Voice)
- RTM 채널: 5명 × 24시간 × 30일 (저비용)
- Voice 채널: 실제 통화 시간만 (예상 120시간/월)
- **총 Voice 채널 사용**: 120시간/월 (90% 절감)

### 6.2 ROI 계산
- 비용 절감: 월 1,080시간 Voice 채널 비용 절약
- RTM 추가 비용: 5명 × RTM 사용료 (상대적으로 매우 저렴)
- **순 절감 효과**: 약 85-90%

## 7. 운영 및 모니터링 계획

### 7.1 실시간 모니터링 시스템
```kotlin
class RTMPTTMonitoringService {
    fun startMonitoring() {
        // RTM 연결 상태 모니터링
        rtmConnectionMonitor.start()
        
        // Voice 채널 사용량 추적
        voiceUsageTracker.start()
        
        // 응답 시간 측정
        responseTimeMetrics.start()
    }
    
    fun generateDailyReport(): PTTSystemReport {
        return PTTSystemReport(
            rtmConnectionUptime = rtmConnectionMonitor.getUptime(),
            voiceChannelUsage = voiceUsageTracker.getDailyUsage(),
            averageResponseTime = responseTimeMetrics.getAverageTime(),
            costSaving = calculateDailyCostSaving()
        )
    }
}
```

### 7.2 알림 및 경고 시스템
- RTM 연결 불안정 시 관리자 알림
- Voice 채널 비정상 사용량 감지
- 응답 시간 저하 경고
- 시스템 성능 지표 일일 리포트

## 8. 마이그레이션 전략

### 8.1 점진적 전환 계획
1. **Phase A**: 기존 시스템과 병행 운영 (2주)
2. **Phase B**: 소규모 테스트 그룹 전환 (1주)
3. **Phase C**: 전체 시스템 전환 (1주)
4. **Phase D**: 기존 시스템 제거 및 정리 (1주)

### 8.2 롤백 계획
- 기존 PTT 시스템 코드 백업 유지
- 문제 발생 시 즉시 기존 시스템으로 복원
- 설정 플래그를 통한 시스템 전환 가능

### 8.3 사용자 교육 및 지원
- 새로운 시스템 사용법 가이드 제작
- 초기 운영 기간 실시간 기술 지원
- 성능 개선 사항 피드백 수집

## 9. 결론 및 기대효과

### 9.1 핵심 성과 지표 (KPI)
1. **응답성**: 2초 이내 PTT 시작 → **목표 달성**
2. **비용 효율성**: 90% 비용 절감 → **목표 달성**
3. **안정성**: 99.9% 가용성 유지
4. **사용자 만족도**: 기존 대비 50% 향상

### 9.2 추가 혜택
- **확장성**: 향후 그룹 크기 확장 시에도 효율적 대응 가능
- **유연성**: 다양한 사용 패턴에 맞춤 최적화 가능  
- **운영비 절감**: 인프라 비용 대폭 절약으로 수익성 개선
- **기술 우위**: 최신 RTM 기술 도입으로 경쟁력 확보

### 9.3 성공 요인
- Agora RTM의 뛰어난 실시간성과 안정성 활용
- 소규모 그룹 특성을 고려한 맞춤형 최적화
- 기존 시스템과의 원활한 통합
- 체계적인 성능 모니터링 및 개선

이 계획서에 따라 구현하면 대리운전 PTT 시스템의 응답성과 비용 효율성을 동시에 크게 개선할 수 있을 것으로 예상됩니다.