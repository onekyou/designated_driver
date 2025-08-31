# MediaSession PTT 디버깅 가이드

## 1. 로그 확인 체크리스트

### 1.1 PTTForegroundService 로그 필터링
```bash
adb logcat -s PTTForegroundService
```

### 1.2 확인해야 할 주요 로그
- [ ] "Initializing MediaSession for volume key control..."
- [ ] "MediaSession initialized and activated for volume key control"
- [ ] "Volume key detected: direction="
- [ ] "MediaButton event: keyCode="
- [ ] "Volume key event: direction="
- [ ] "VolumeProvider.onAdjustVolume called:"
- [ ] "PTT mode enabled/disabled"

### 1.3 시스템 레벨 MediaSession 로그
```bash
adb logcat | grep -i mediasession
adb logcat | grep -i volumeprovider
adb logcat | grep -i "dispatchVolumeKeyEvent"
```

## 2. 디버깅 코드 추가 위치

### 2.1 MediaSession 상태 확인
```kotlin
// PTTForegroundService.kt에 추가
private fun debugMediaSessionState() {
    Log.d(TAG, "MediaSession active: ${mediaSession.isActive}")
    Log.d(TAG, "MediaSession token: ${mediaSession.sessionToken}")
    Log.d(TAG, "PTT mode enabled: $isPTTModeEnabled")
    
    val controller = mediaSession.controller
    Log.d(TAG, "PlaybackState: ${controller.playbackState?.state}")
    Log.d(TAG, "Volume control type: ${controller.playbackInfo?.volumeControlType}")
}
```

### 2.2 VolumeProvider 상태 확인
```kotlin
// handleVolumeKeyEvent 시작 부분에 추가
Log.d(TAG, """
    Volume Event Debug:
    - Direction: $direction
    - PTT Mode: $isPTTModeEnabled
    - Is Transmitting: $isTransmitting
    - MediaSession Active: ${mediaSession.isActive}
    - Current Volume: ${volumeProvider.currentVolume}
""".trimIndent())
```

## 3. 테스트 시나리오

### 3.1 기본 동작 테스트
1. 앱 시작 후 PTT 서비스 시작
2. PTT 모드 ON 상태에서 볼륨 다운키 누르기
3. 로그 확인: onAdjustVolume 호출 여부
4. PTT 시작/중지 동작 확인

### 3.2 모드 전환 테스트
1. PTT 모드 ON → 볼륨키 동작 확인
2. PTT 모드 OFF → 일반 볼륨 조절 확인
3. 모드 전환 후 MediaSession 상태 확인

### 3.3 백그라운드 테스트
1. 앱을 백그라운드로 전환
2. 화면 끄기
3. 볼륨키 PTT 동작 확인
4. 다른 미디어 앱 실행 후 테스트

## 4. 일반적인 문제와 해결책

### 4.1 onAdjustVolume이 호출되지 않음
**원인:**
- MediaSession이 비활성 상태
- VolumeProvider가 MediaSession에 연결되지 않음
- 다른 앱이 MediaSession 우선순위 보유

**해결책:**
```kotlin
// 1. MediaSession 강제 활성화
mediaSession.isActive = true

// 2. VolumeProvider 재연결
mediaSession.setPlaybackToRemote(volumeProvider)

// 3. AudioFocus 요청
val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
audioManager.requestAudioFocus(...)
```

### 4.2 볼륨 UI가 표시됨
**원인:**
- 이벤트를 제대로 소비하지 못함
- VolumeProvider 설정 문제

**해결책:**
```kotlin
// VolumeProvider에서 currentVolume 고정
override fun onAdjustVolume(direction: Int) {
    // 볼륨 변경 무시, PTT만 처리
    handleVolumeKeyEvent(direction)
    // currentVolume 유지
    setCurrentVolume(50)
}
```

### 4.3 다른 미디어 앱과 충돌
**원인:**
- MediaSession 우선순위 경쟁
- AudioFocus 미확보

**해결책:**
```kotlin
// 높은 우선순위 설정
val metadata = MediaMetadataCompat.Builder()
    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "PTT Active")
    .build()
mediaSession.setMetadata(metadata)

// 활성 상태 유지
mediaSession.setActive(true)
```

## 5. 코드 검증 체크리스트

### 5.1 초기화 검증
- [ ] MediaSession 생성 완료
- [ ] VolumeProvider 생성 완료
- [ ] setPlaybackToRemote() 호출
- [ ] setActive(true) 호출
- [ ] PlaybackState 설정

### 5.2 이벤트 처리 검증
- [ ] onAdjustVolume 콜백 등록
- [ ] handleVolumeKeyEvent 로직 확인
- [ ] PTT 시작/중지 호출 확인

### 5.3 상태 관리 검증
- [ ] isPTTModeEnabled 상태 동기화
- [ ] isTransmitting 플래그 관리
- [ ] updateMediaSessionVolumeControl() 동작

## 6. ADB 명령어 모음

```bash
# 로그 초기화 및 모니터링
adb logcat -c
adb logcat -s PTTForegroundService

# 미디어 세션 상태 확인
adb shell dumpsys media_session

# 오디오 상태 확인
adb shell dumpsys audio

# 앱 권한 확인
adb shell dumpsys package com.designated.callmanager | grep permission

# 서비스 상태 확인
adb shell dumpsys activity services | grep PTTForegroundService
```

## 7. 추가 디버깅 도구

### 7.1 Media Controller Test
```kotlin
// 별도 테스트 함수
fun testMediaController() {
    val controller = MediaControllerCompat(this, mediaSession.sessionToken)
    Log.d(TAG, "Controller state: ${controller.playbackState}")
    Log.d(TAG, "Volume info: ${controller.playbackInfo}")
}
```

### 7.2 System Media Router 확인
```kotlin
val mediaRouter = MediaRouter.getInstance(this)
val route = mediaRouter.selectedRoute
Log.d(TAG, "Selected route: ${route.name}")
```

## 8. 대안 솔루션 검토

### 8.1 MediaButtonReceiver 방식
- BroadcastReceiver로 미디어 버튼 이벤트 수신
- 볼륨키를 미디어 버튼으로 매핑

### 8.2 Notification MediaStyle
- Notification에 MediaStyle 적용
- 미디어 컨트롤 UI 제공

### 8.3 블루투스 헤드셋 버튼
- 블루투스 헤드셋의 재생/일시정지 버튼 활용
- AVRCP 프로토콜 사용

## 9. 참고 링크
- [Android MediaSession Guide](https://developer.android.com/guide/topics/media/media-controls)
- [VolumeProvider Documentation](https://developer.android.com/reference/android/support/v4/media/VolumeProviderCompat)
- [Media Apps Overview](https://developer.android.com/guide/topics/media-apps/media-apps-overview)