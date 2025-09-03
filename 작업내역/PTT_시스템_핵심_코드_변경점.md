# PTT 시스템 핵심 코드 변경점

## 🔄 주요 코드 변경 히스토리

### 1. PTTAccessibilityService.kt - 볼륨키 감지 로직

#### 🔧 최종 수정된 핵심 로직
```kotlin
/**
 * 볼륨키 이벤트 처리
 */
private fun handleVolumeKeyEvent(event: KeyEvent): Boolean {
    when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            // 이미 처리 중이거나 전송 중이면 무시
            if (isProcessing || isTransmitting) {
                Log.d(TAG, "Volume key DOWN ignored - already processing/transmitting")
                return true
            }
            
            // DOWN 이벤트 디바운싱 처리
            if (!debouncer.shouldProcess()) {
                Log.d(TAG, "Volume key DOWN event debounced")
                return true
            }
            
            Log.d(TAG, "Volume key pressed - starting PTT")
            isProcessing = true
            startPTT()
            isTransmitting = true
            isProcessing = false
        }
        
        KeyEvent.ACTION_UP -> {
            // 전송 중이지 않거나 처리 중이면 무시
            if (!isTransmitting || isProcessing) {
                Log.d(TAG, "Volume key UP ignored - not transmitting or processing")
                return true
            }
            
            Log.d(TAG, "Volume key released - stopping PTT")
            isProcessing = true
            stopPTT()
            isTransmitting = false
            isProcessing = false
        }
    }
    
    // 이벤트를 소비하여 시스템 볼륨 조절 방지
    return true
}
```

#### 📝 변경 이유
- **문제**: 볼륨키 DOWN/UP 이벤트가 너무 빨리 연속 발생
- **해결**: `isProcessing` 플래그로 중복 이벤트 방지
- **개선**: 디바운싱 딜레이 100ms → 200ms 증가

### 2. PTTController.kt - 채널 해제 로직 강화

#### 🔧 stopPTT 메서드 상세 로그 추가
```kotlin
suspend fun stopPTT(): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        Log.d(TAG, "Stopping PTT - isConnected: $isConnected, currentChannel: $currentChannel, currentUID: $currentUID")
        
        if (!isConnected) {
            Log.w(TAG, "Not connected to any channel - forcing engine check")
            // 엔진 상태 확인
            val engineStatus = engine.getStatus()
            Log.d(TAG, "Engine status - isInChannel: ${engineStatus.isInChannel}, currentChannel: ${engineStatus.currentChannel}")
            
            if (engineStatus.isInChannel) {
                Log.w(TAG, "Engine still in channel, forcing leave")
                val leaveResult = engine.leaveChannel()
                Log.d(TAG, "Force leave result: ${leaveResult.isSuccess}")
            }
            return@withContext Result.success(Unit)
        }
        
        // ... 나머지 로직
        
        // 채널 완전히 나가기
        val leaveResult = engine.leaveChannel()
        Log.d(TAG, "Leave channel result: ${leaveResult.isSuccess}")
        
        if (leaveResult.isSuccess) {
            currentChannel = null
            currentUID = 0
            isConnected = false
            Log.i(TAG, "Left channel completely on PTT stop")
        } else {
            Log.e(TAG, "Failed to leave channel: ${leaveResult.exceptionOrNull()}")
        }
        
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to stop PTT", e)
        Result.failure(e)
    }
}
```

#### 📝 변경 이유
- **문제**: 채널 해제가 불안정하게 작동 (바로/늦게/안나가는 경우)
- **해결**: 상세한 상태 로그와 강제 해제 로직 추가
- **결과**: ✅ 채널 해제 안정화 완료

### 3. PTTForegroundService.kt - 비프음 및 상태 관리

#### 🔧 비프음 즉시 재생 로직
```kotlin
private fun handleStartPTT() {
    serviceScope.launch {
        try {
            Log.d(TAG, "Starting PTT...")
            beepSoundManager.playStartBeep() // 즉시 시작 비프음 재생
            _pttState.value = PTTState.Connecting
            
            val uid = getOrCreateUID()
            val result = pttController.startPTT(uid)
            
            if (result.isSuccess) {
                _pttState.value = PTTState.Transmitting(true)
                updateNotification("송신 중...")
            } else {
                // 에러 처리
            }
        } catch (e: Exception) {
            Log.e(TAG, "PTT start failed", e)
        }
    }
}
```

#### 🔧 상태 관리 개선
```kotlin
private fun handleStopPTT() {
    serviceScope.launch {
        try {
            Log.d(TAG, "Stopping PTT...")
            val result = pttController.stopPTT()
            
            if (result.isSuccess) {
                beepSoundManager.playEndBeep() // 종료 비프음 재생
                _pttState.value = PTTState.Transmitting(false)
                // 채널에서 완전히 나갔으므로 Disconnected 상태로 변경
                _pttState.value = PTTState.Disconnected
                updateNotification("PTT 준비됨")
            }
        } catch (e: Exception) {
            Log.e(TAG, "PTT stop failed", e)
        }
    }
}
```

#### 📝 변경 이유
- **문제**: 비프음이 채널 참여 후에만 재생됨 → 첫 번째 누를 때 없음
- **해결**: 볼륨키 누르자마자 즉시 비프음 재생
- **상태 개선**: stopPTT 후 명확히 Disconnected 상태로 전환

### 4. BeepSoundManager.kt - 비프음 안정성 개선

#### 🔧 초기화 상태 체크 강화
```kotlin
fun playStartBeep() {
    try {
        if (isInitialized && startSoundId != 0) {
            soundPool?.play(startSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
            Log.d(TAG, "PTT start beep played (real sound)")
        } else {
            // 폴백: 시스템 비프음
            playSystemBeep()
            Log.d(TAG, "PTT start beep played (system fallback) - initialized: $isInitialized, soundId: $startSoundId")
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "Failed to play start beep", e)
        playSystemBeep() // 에러 시 시스템 비프음
    }
}
```

#### 📝 변경 이유
- **문제**: 간헐적으로 비프음이 나오지 않음
- **해결**: 초기화 상태와 사운드 ID 모두 체크
- **디버깅**: 상세한 로그로 실패 원인 추적 가능

## 🚫 시도했지만 실패한 방법들

### 1. PTTManagerService 직접 호출
```kotlin
// ❌ 실패한 접근 방법
val pttManager = PTTManagerService.getInstance()
if (pttManager != null) {
    pttManager.startTransmission()
}
```
**실패 이유**: PTTManagerService가 채널에 연결되지 않은 상태여서 "Not connected to channel" 에러

### 2. PTTControllerManager 싱글톤 패턴
```kotlin
// ❌ 복잡도만 증가시킨 방법
object PTTControllerManager {
    private var instance: PTTController? = null
    
    fun getInstance(context: Context): PTTController? {
        // 복잡한 초기화 로직
    }
}
```
**실패 이유**: 오히려 복잡도만 증가하고 근본적인 문제 해결 안됨

### 3. SimplePTTEngine에 BeepSoundManager 생성자 통합
```kotlin
// ❌ 시도했다가 롤백한 방법
class SimplePTTEngine(
    private val beepSoundManager: BeepSoundManager? = null
) {
    // ...
}
```
**실패 이유**: 의존성 복잡도 증가, 코드 일관성 저해

## ✅ 성공한 핵심 패턴

### 1. 서비스 간 Intent 통신
```kotlin
// ✅ 성공한 패턴
val intent = Intent(this, PTTForegroundService::class.java).apply {
    action = PTTForegroundService.ACTION_START_PTT
}
startService(intent)
```

### 2. 상태 기반 이벤트 처리
```kotlin
// ✅ 성공한 패턴
if (isProcessing || isTransmitting) {
    Log.d(TAG, "Event ignored - already processing/transmitting")
    return true
}
```

### 3. 엔진 상태와 컨트롤러 상태 분리 체크
```kotlin
// ✅ 성공한 패턴
if (!isConnected) {
    val engineStatus = engine.getStatus()
    if (engineStatus.isInChannel) {
        // 강제 해제 로직
    }
}
```

## 📊 성능 및 안정성 지표

### 작업 전 vs 작업 후

| 항목 | 작업 전 | 작업 후 |
|------|---------|---------|
| 볼륨키 감지 | ❌ 작동 안함 | ✅ 안정적 감지 |
| 채널 참여 | ❌ 불안정 | ✅ 자동 참여 |
| 채널 해제 | ❌ 매우 불안정 | ✅ 안정적 해제 |
| 비프음 | ❌ 전혀 없음 | ⚠️ 간헐적 |
| RTM 연동 | ❌ 없음 | ✅ 완전 연동 |

### 현재 남은 이슈 정도

| 이슈 | 심각도 | 발생 빈도 |
|------|--------|-----------|
| 초기 비프음 없음 | 🔴 중간 | 30-50% |
| 볼륨키 짧게 유지 | 🔴 높음 | 70-80% |
| 상태 전환 불안정 | 🟡 낮음 | 10-20% |

---

**마지막 수정**: 채널 해제 안정화 완료
**다음 우선순위**: 비프음 로딩 완료 대기 로직 구현