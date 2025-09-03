# Agora RTM 2.x (Signaling SDK) Android API 정확한 구조 및 오류 해결 문서

**작성일**: 2025-01-28  
**기반**: Agora 공식 문서, Migration Guide, 실제 오류 분석  
**목적**: RTM 2.x API 사용 시 발생하는 모든 오류 해결 및 정확한 구현 가이드  
**적용 대상**: Android Studio 4.1+, Android SDK API Level 24+

## 1. 중요한 변경사항 개요

### 1.1 명칭 변경
- **RTM 1.x** → **Signaling SDK 2.x**로 리브랜딩
- 패키지명: `rtm-sdk` → `agora-rtm`

### 1.2 주요 아키텍처 변경
- **1.x**: Channel 기반 (채널 생성 → 가입 → 메시지 전송)
- **2.x**: Pub/Sub 모델 (publish/subscribe로 분리)

## 2. 자주 발생하는 오류 유형 및 원인

### 2.1 주요 오류 패턴
- **Type mismatch: Serializable? but Result<Unit> was expected**
- **Type mismatch: Result<Unit> but Serializable? was expected**  
- **Unresolved reference: errorDescription**
- **Unresolved reference: publishTextMessage**
- **No value passed for parameter 'resultCallback'**
- **Cannot access 'publisher': it is private in 'PresenceEvent'**

### 2.2 오류 원인 분석
1. **비동기 작업 처리 방식 변경**: RTM 2.x에서 콜백 구조가 완전히 변경됨
2. **API 메서드명 변경**: 많은 메서드가 새로운 이름으로 변경됨
3. **콜백 타입 통일**: 다양한 콜백이 `ResultCallback<T>` 형태로 통일됨
4. **Presence 이벤트 구조 변경**: private 필드 접근 방식 변경

## 3. Gradle 의존성 (정확한 설정)

### 3.1 올바른 의존성 설정
```gradle
dependencies {
    // RTM 2.x (Signaling SDK) - 정확한 패키지명
    implementation 'io.agora:agora-rtm:2.2.4'
    
    // 기존 RTC SDK (변경 없음)
    implementation 'io.agora.rtc:full-sdk:4.2.3'
    
    // Kotlin Serialization (RTM 신호용)
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0'
}
```

### 3.2 잘못된 설정 (오류 발생)
```gradle
// ❌ 잘못된 패키지명
implementation 'io.agora.rtm:rtm-sdk:2.x.x'  // 이것은 1.x 방식

// ✅ 올바른 패키지명  
implementation 'io.agora:agora-rtm:2.2.4'
```

## 4. 정확한 Import 구조

### 4.1 올바른 Import (RTM 2.x)
```kotlin
// 핵심 클래스
import io.agora.rtm.RtmClient
import io.agora.rtm.RtmConfig
import io.agora.rtm.RtmEventListener

// 콜백 및 결과 처리
import io.agora.rtm.ResultCallback
import io.agora.rtm.ErrorInfo

// 메시지 및 옵션
import io.agora.rtm.PublishOptions
import io.agora.rtm.SubscribeOptions

// 이벤트 관련
import io.agora.rtm.MessageEvent
import io.agora.rtm.PresenceEvent

// 상수 및 상태
import io.agora.rtm.RtmConnectionState
import io.agora.rtm.RtmConnectionChangeReason

// 채널 관련 (2.x에서는 StreamChannel 사용)
import io.agora.rtm.StreamChannel
```

### 4.2 잘못된 Import (오류 발생)
```kotlin
// ❌ RTM 1.x 방식 - 2.x에서는 존재하지 않음
import io.agora.rtm.RtmClientListener  // 2.x에서는 RtmEventListener
import io.agora.rtm.RtmMessage.Builder  // 2.x에서는 다른 방식
import io.agora.rtm.RtmResult            // 실제로는 ResultCallback 사용

// ❌ 존재하지 않는 클래스들
import io.agora.rtm.RtmConstants.RtmConnectionState  // 직접 import 필요
```

## 4. 클라이언트 초기화 (정확한 방법)

### 4.1 RTM 2.x 방식
```kotlin
// Config 생성
val config = RtmConfig.Builder(appId, userId)
    .eventListener(createEventListener())
    .build()

// 클라이언트 생성
val rtmClient = RtmClient.create(config)
```

### 4.2 이벤트 리스너 구조
```kotlin
private fun createEventListener(): RtmEventListener {
    return object : RtmEventListener {
        override fun onMessageEvent(event: MessageEvent) {
            // 메시지 수신 처리
            val messageData = String(event.message.rawData)
            Log.d(TAG, "Message received: $messageData")
        }
        
        override fun onPresenceEvent(event: PresenceEvent) {
            // 사용자 입장/퇴장 이벤트
            Log.d(TAG, "Presence event: ${event.eventType}")
        }
        
        override fun onConnectionStateChanged(
            channelName: String?,
            state: RtmConnectionState?,
            reason: RtmConnectionChangeReason?
        ) {
            Log.d(TAG, "Connection state: $state, reason: $reason")
        }
        
        override fun onTokenPrivilegeWillExpire(channelName: String?) {
            Log.w(TAG, "Token will expire for channel: $channelName")
        }
    }
}
```

## 5. 로그인 (정확한 API)

### 5.1 올바른 로그인 방식
```kotlin
rtmClient.login(token, object : ResultCallback<Void> {
    override fun onSuccess(responseInfo: Void?) {
        Log.i(TAG, "RTM login successful")
    }
    
    override fun onFailure(errorInfo: ErrorInfo) {
        Log.e(TAG, "RTM login failed: ${errorInfo.errorDescription}")
    }
})
```

### 5.2 로그아웃
```kotlin
rtmClient.logout(object : ResultCallback<Void> {
    override fun onSuccess(responseInfo: Void?) {
        Log.i(TAG, "RTM logout successful")
    }
    
    override fun onFailure(errorInfo: ErrorInfo) {
        Log.e(TAG, "RTM logout failed: ${errorInfo.errorDescription}")
    }
})
```

## 6. 메시지 발송 (Pub/Sub 모델)

### 6.1 메시지 발행 (Publish)
```kotlin
val publishOptions = PublishOptions().apply {
    customType = "PTT_SIGNAL"
}

rtmClient.publish(channelName, messageData, publishOptions, object : ResultCallback<Void> {
    override fun onSuccess(responseInfo: Void?) {
        Log.d(TAG, "Message published successfully")
    }
    
    override fun onFailure(errorInfo: ErrorInfo) {
        Log.e(TAG, "Message publish failed: ${errorInfo.errorDescription}")
    }
})
```

### 6.2 채널 구독 (Subscribe)
```kotlin
val subscribeOptions = SubscribeOptions().apply {
    withMessage = true
    withPresence = true
}

rtmClient.subscribe(channelName, subscribeOptions, object : ResultCallback<Void> {
    override fun onSuccess(responseInfo: Void?) {
        Log.d(TAG, "Channel subscribed successfully")
    }
    
    override fun onFailure(errorInfo: ErrorInfo) {
        Log.e(TAG, "Channel subscribe failed: ${errorInfo.errorDescription}")
    }
})
```

## 7. 상세 오류 해결 가이드

### 7.1 자주 발생하는 오류 및 해결방법

| 오류 메시지 | 원인 | 잘못된 코드 | 올바른 코드 |
|-------------|------|-------------|-------------|
| `Unresolved reference: errorDescription` | ErrorInfo 접근 방식 변경 | `errorInfo.errorDescription` | `errorInfo?.errorDescription ?: "Unknown error"` |
| `Type mismatch: inferred type is Serializable? but Result<Unit> was expected` | 콜백 타입 불일치 | `{ errorInfo: ErrorInfo? -> }` | `object : ResultCallback<Void> { ... }` |
| `Type mismatch: inferred type is Result<Unit> but Serializable? was expected` | 반환 타입 오류 | `return Result.success(Unit)` | `callback.onSuccess(null)` |
| `Unresolved reference: publishTextMessage` | API 메서드명 변경 | `channel.publishTextMessage()` | `rtmClient.publish()` |
| `No value passed for parameter 'resultCallback'` | 콜백 파라미터 누락 | `rtmClient.login(token)` | `rtmClient.login(token, callback)` |
| `Cannot access 'publisher': it is private in 'PresenceEvent'` | Presence 이벤트 접근 방식 변경 | `event.publisher` | `event.publisherId` |
| `Unresolved reference: RtmConnectionState` | Import 누락 또는 잘못된 경로 | `RtmConstants.RtmConnectionState` | `import io.agora.rtm.RtmConnectionState` |
| `None of the following functions can be called...` | 함수 시그니처 불일치 | 잘못된 파라미터 타입/개수 | 올바른 파라미터 확인 후 수정 |

### 7.2 구체적인 오류 해결 예시

#### 오류 1: 로그인 콜백 타입 오류
```kotlin
// ❌ 잘못된 방식 (오류 발생)
rtmClient?.login(token) { errorInfo: ErrorInfo? ->
    if (errorInfo != null) {
        Log.e(TAG, "Login failed: ${errorInfo.errorDescription}")
    }
}

// ✅ 올바른 방식 (RTM 2.x)
rtmClient?.login(token, object : ResultCallback<Void> {
    override fun onSuccess(responseInfo: Void?) {
        Log.i(TAG, "RTM login successful")
    }
    
    override fun onFailure(errorInfo: ErrorInfo) {
        Log.e(TAG, "RTM login failed: ${errorInfo.errorDescription}")
    }
})
```

#### 오류 2: 메시지 발송 API 변경
```kotlin
// ❌ 잘못된 방식 (RTM 1.x 스타일)
channel.publishTextMessage(message, PublishOptions()) { errorInfo ->
    // 처리
}

// ✅ 올바른 방식 (RTM 2.x)
val publishOptions = PublishOptions()
rtmClient.publish(channelName, message, publishOptions, object : ResultCallback<Void> {
    override fun onSuccess(responseInfo: Void?) {
        Log.d(TAG, "Message published successfully")
    }
    
    override fun onFailure(errorInfo: ErrorInfo) {
        Log.e(TAG, "Message publish failed: ${errorInfo.errorDescription}")
    }
})
```

#### 오류 3: 이벤트 리스너 구현
```kotlin
// ❌ 잘못된 방식 (존재하지 않는 메서드)
override fun onMessageReceived(message: RtmMessage, peerId: String) {
    // 2.x에서는 이 메서드가 존재하지 않음
}

// ✅ 올바른 방식 (RTM 2.x)
override fun onMessageEvent(event: MessageEvent) {
    val messageData = String(event.message.rawData)
    val publisherId = event.publisherId
    Log.d(TAG, "Message received from $publisherId: $messageData")
}
```

### 7.3 Graceful Degradation 강화 패턴
```kotlin
class SafeRTMManager(private val context: Context) {
    private val TAG = "SafeRTMManager"
    
    // 안전한 RTM 작업 래퍼
    private inline fun <T> safeRTMOperation(
        operation: () -> T,
        defaultValue: T,
        operationName: String
    ): T {
        return try {
            operation()
        } catch (e: Exception) {
            Log.w(TAG, "RTM $operationName failed, using default value", e)
            defaultValue
        }
    }
    
    // 안전한 초기화
    suspend fun safeInitialize(appId: String, userId: String, token: String?): Result<Unit> {
        return safeRTMOperation(
            operation = { 
                // 실제 RTM 초기화 로직
                initializeRTMUnsafe(appId, userId, token)
                Result.success(Unit)
            },
            defaultValue = Result.success(Unit), // RTM 실패해도 성공으로 처리
            operationName = "initialization"
        )
    }
    
    // 안전한 메시지 전송
    fun safeSendMessage(channelName: String, message: String) {
        safeRTMOperation(
            operation = { sendMessageUnsafe(channelName, message) },
            defaultValue = Unit,
            operationName = "message sending"
        )
    }
}
```

### 7.4 디버깅 팁

#### Android Studio에서 오류 해결하기
1. **Alt + Enter**: 오류가 있는 코드에 커서를 놓고 자동 import 제안 확인
2. **Ctrl + Q**: 메서드 시그니처 확인
3. **Ctrl + B**: 클래스/메서드 정의로 이동하여 실제 API 확인

#### 로그 기반 디버깅
```kotlin
class DebugRTMManager {
    private val TAG = "DebugRTMManager"
    
    fun debugRTMAPIs() {
        Log.d(TAG, "=== RTM API Debug Info ===")
        Log.d(TAG, "RtmClient class: ${RtmClient::class.java.name}")
        Log.d(TAG, "Available methods:")
        
        RtmClient::class.java.methods.forEach { method ->
            Log.d(TAG, "  - ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
        }
    }
}

## 8. 완전한 RTM2Manager 구현 예시

```kotlin
class RTM2Manager(private val context: Context) {
    private val TAG = "RTM2Manager"
    private var rtmClient: RtmClient? = null
    private var isInitialized = false
    
    suspend fun initialize(appId: String, userId: String, token: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Config 생성
            val config = RtmConfig.Builder(appId, userId)
                .eventListener(createEventListener())
                .build()
            
            // 클라이언트 생성
            rtmClient = RtmClient.create(config)
            
            // 로그인 (토큰이 있는 경우)
            if (!token.isNullOrEmpty()) {
                rtmClient?.login(token, object : ResultCallback<Void> {
                    override fun onSuccess(responseInfo: Void?) {
                        Log.i(TAG, "RTM login successful")
                        isInitialized = true
                    }
                    
                    override fun onFailure(errorInfo: ErrorInfo) {
                        Log.e(TAG, "RTM login failed: ${errorInfo.errorDescription}")
                    }
                })
            } else {
                isInitialized = true
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "RTM initialization failed", e)
            Result.success(Unit) // Graceful degradation
        }
    }
    
    suspend fun sendPTTSignal(channelName: String, signalType: String, senderUid: Int): Result<Unit> {
        if (!isInitialized) return Result.success(Unit)
        
        try {
            val signalData = """{"type":"$signalType","senderId":"$senderUid","timestamp":${System.currentTimeMillis()}}"""
            val publishOptions = PublishOptions().apply {
                customType = "PTT_SIGNAL"
            }
            
            rtmClient?.publish(channelName, signalData, publishOptions, object : ResultCallback<Void> {
                override fun onSuccess(responseInfo: Void?) {
                    Log.d(TAG, "PTT signal sent: $signalType")
                }
                
                override fun onFailure(errorInfo: ErrorInfo) {
                    Log.w(TAG, "PTT signal failed: ${errorInfo.errorDescription}")
                }
            })
            
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "RTM signal error", e)
            return Result.success(Unit) // Graceful degradation
        }
    }
    
    private fun createEventListener(): RtmEventListener {
        return object : RtmEventListener {
            override fun onMessageEvent(event: MessageEvent) {
                try {
                    val messageData = String(event.message.rawData)
                    Log.d(TAG, "RTM message received from ${event.publisherId}: $messageData")
                    // PTT 신호 처리 로직
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle RTM message", e)
                }
            }
            
            override fun onPresenceEvent(event: PresenceEvent) {
                Log.d(TAG, "RTM presence event: ${event.eventType}")
            }
            
            override fun onConnectionStateChanged(
                channelName: String?,
                state: RtmConnectionState?,
                reason: RtmConnectionChangeReason?
            ) {
                Log.d(TAG, "RTM connection state: $state")
            }
            
            override fun onTokenPrivilegeWillExpire(channelName: String?) {
                Log.w(TAG, "RTM token will expire")
            }
        }
    }
    
    fun cleanup() {
        try {
            rtmClient?.logout(object : ResultCallback<Void> {
                override fun onSuccess(responseInfo: Void?) {
                    Log.d(TAG, "RTM logout successful")
                }
                
                override fun onFailure(errorInfo: ErrorInfo) {
                    Log.w(TAG, "RTM logout failed: ${errorInfo.errorDescription}")
                }
            })
            rtmClient = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "RTM cleanup error", e)
        }
    }
}
```

## 9. 주요 API 변경사항 요약표

| 기능 | RTM 1.x | RTM 2.x (Signaling SDK) |
|------|---------|-------------------------|
| **의존성** | `rtm-sdk:1.x.x` | `agora-rtm:2.x.x` |
| **클라이언트 생성** | `RtmClient.createInstance()` | `RtmClient.create(config)` |
| **로그인** | `login(token, userId, callback)` | `login(token, ResultCallback)` |
| **메시지 전송** | `channel.sendMessage()` | `publish(channel, message, options)` |
| **채널 관리** | `createChannel() + join()` | `subscribe(channel, options)` |
| **콜백 타입** | 다양한 콜백 인터페이스 | `ResultCallback<Void>` 통일 |
| **에러 처리** | `errorCode` | `ErrorInfo.errorDescription` |

## 10. 단계별 오류 해결 체크리스트

### 10.1 빌드 오류 발생 시 해결 순서

#### 1단계: 기본 설정 확인
- [ ] `build.gradle`에 `implementation 'io.agora:agora-rtm:2.2.4'` 정확히 설정
- [ ] Android Studio를 재시작하고 Gradle Sync 실행
- [ ] Project Structure에서 RTM SDK가 제대로 추가되었는지 확인

#### 2단계: Import 문 수정
- [ ] 모든 RTM 관련 import를 올바른 2.x 버전으로 변경
- [ ] `RtmClientListener` → `RtmEventListener` 변경
- [ ] `RtmResult` → `ResultCallback<Void>` 변경
- [ ] 존재하지 않는 클래스 import 제거

#### 3단계: API 호출 방식 수정
- [ ] 로그인: `login(token, ResultCallback)` 형태로 변경
- [ ] 메시지 전송: `publish()` 메서드 사용
- [ ] 이벤트 처리: `onMessageEvent()` 메서드 구현
- [ ] 콜백을 `object : ResultCallback<Void>` 형태로 변경

#### 4단계: Graceful Degradation 적용
- [ ] 모든 RTM 호출을 try-catch로 감싸기
- [ ] RTM 실패 시에도 기존 PTT 기능 정상 동작 확인
- [ ] 로그 메시지로 RTM 상태 확인 가능하게 구현

### 10.2 오류별 빠른 해결 참조표

| 오류 키워드 | 빠른 해결 방법 | 참조 섹션 |
|-------------|----------------|-----------|
| `Unresolved reference: errorDescription` | `errorInfo?.errorDescription ?: "Unknown"` 사용 | 7.1 |
| `Type mismatch: Serializable` | `ResultCallback<Void>` 명시적 사용 | 7.2 |
| `publishTextMessage` | `rtmClient.publish()` 사용 | 7.2 |
| `No value passed for parameter` | 콜백 파라미터 추가 | 7.1 |
| `Cannot access 'publisher'` | `event.publisherId` 사용 | 7.2 |
| `RtmConnectionState not found` | 올바른 import 추가 | 4.1 |

### 10.3 권장 구현 패턴

#### 최소 침습 방식 (Minimal Invasive)
```kotlin
// 기존 PTT 코드에 최소한의 RTM 기능만 추가
class MinimalRTMManager(context: Context) {
    private var rtmClient: RtmClient? = null
    
    fun tryInitialize(appId: String, userId: String) {
        try {
            val config = RtmConfig.Builder(appId, userId).build()
            rtmClient = RtmClient.create(config)
            Log.d(TAG, "RTM initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "RTM initialization failed, continuing without RTM", e)
            rtmClient = null
        }
    }
    
    fun tryPublishSignal(channel: String, message: String) {
        rtmClient?.let { client ->
            try {
                val options = PublishOptions()
                client.publish(channel, message, options, object : ResultCallback<Void> {
                    override fun onSuccess(responseInfo: Void?) {
                        Log.d(TAG, "RTM signal sent")
                    }
                    override fun onFailure(errorInfo: ErrorInfo) {
                        Log.w(TAG, "RTM signal failed: ${errorInfo.errorDescription}")
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "RTM publish error", e)
            }
        }
    }
}
```

#### 단계별 테스트 방식
1. **Step 1**: 클라이언트 생성만 테스트
2. **Step 2**: 로그인 기능 추가 테스트  
3. **Step 3**: 메시지 발송 기능 추가 테스트
4. **Step 4**: 이벤트 수신 기능 추가 테스트

### 10.4 성능 및 안정성 고려사항

#### 메모리 관리
```kotlin
// RTM 리소스 정리
override fun onDestroy() {
    super.onDestroy()
    rtmManager?.cleanup()
}

fun cleanup() {
    try {
        rtmClient?.logout(object : ResultCallback<Void> {
            override fun onSuccess(responseInfo: Void?) {}
            override fun onFailure(errorInfo: ErrorInfo) {}
        })
        rtmClient = null
    } catch (e: Exception) {
        Log.e(TAG, "Cleanup error", e)
    }
}
```

#### 네트워크 상태 고려
```kotlin
// 네트워크 상태에 따른 RTM 동작 조절
private fun isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return connectivityManager.activeNetworkInfo?.isConnected == true
}

fun smartRTMOperation() {
    if (isNetworkAvailable()) {
        // RTM 작업 수행
        performRTMOperation()
    } else {
        Log.w(TAG, "No network, skipping RTM operation")
        // 로컬 처리만 수행
    }
}
```

## 11. 문제 발생 시 지원 및 문의

### 11.1 추가 도움이 필요한 경우
- **Agora 공식 문서**: [https://docs.agora.io/en/signaling](https://docs.agora.io/en/signaling)
- **GitHub Issues**: Agora RTM SDK 관련 이슈 검색
- **Agora Developer Community**: 개발자 커뮤니티 질문

### 11.2 이 문서의 한계
- RTM 2.x의 모든 기능을 다루지 않음 (PTT 시스템에 필요한 기능 위주)
- 특정 버전(2.2.4) 기준으로 작성됨
- 실제 프로덕션 환경에서 추가 테스트 필요

---

**참고 자료**:
- [Agora Signaling SDK 공식 문서](https://docs.agora.io/en/signaling/get-started/sdk-quickstart)
- [RTM 1.x → 2.x Migration Guide](https://docs.agora.io/en/signaling/overview/migration-guide)
- 실제 개발 과정에서 발견된 오류 패턴 분석
- Android Studio 4.1+ 및 Android SDK API Level 24+ 환경 테스트

**최종 업데이트**: 2025-01-28  
**문서 버전**: 1.1  
**적용 환경**: RTM 2.2.4, Android Studio 4.1+, Kotlin 1.8+