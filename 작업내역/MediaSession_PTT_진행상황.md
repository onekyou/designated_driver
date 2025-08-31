# MediaSession 기반 PTT 시스템 구현 진행 상황

## 작업 날짜: 2025-08-30

## 1. 배경 및 문제점
### 초기 문제
- Android 15 (Samsung One UI 6.1)에서 AccessibilityService의 `onKeyEvent()`가 볼륨키 이벤트를 받지 못하는 문제 발생
- 접근성 서비스가 활성화되어 있음에도 불구하고 볼륨키 PTT가 작동하지 않음
- Samsung의 One UI가 시스템 키 이벤트를 표준 Android보다 더 강력하게 제어하는 것으로 확인

### 전문가 자문 결과 (참조: 접근성과 MediaSession.txt)
- 근본 원인: Samsung One UI가 시스템 키 이벤트를 높은 우선순위로 가로채기 때문 (90% 이상 확률)
- 권장 해결책: AccessibilityService 대신 MediaSession API 사용
- MediaSession이 Android의 공식적인 미디어 컨트롤 방법이며, 볼륨키 제어에 가장 적합

## 2. 완료된 작업

### 2.1 AccessibilityService 완전 제거
- ✅ PTTAccessibilityService.kt 삭제 (call_manager, pickup_app 모두)
- ✅ ptt_accessibility_service_config.xml 삭제
- ✅ AndroidManifest.xml에서 AccessibilityService 선언 제거
- ✅ BIND_ACCESSIBILITY_SERVICE 권한 제거
- ✅ 접근성 관련 UI 컴포넌트 제거
- ✅ strings.xml에서 접근성 서비스 설명 제거

### 2.2 MediaSession 구현 (Call Manager 앱)
- ✅ MediaSession 의존성 추가 (`androidx.media:media:1.7.0`)
- ✅ FOREGROUND_SERVICE_MEDIA_PLAYBACK 권한 추가
- ✅ PTTForegroundService에 MediaSession 초기화 로직 구현
- ✅ VolumeProviderCompat을 통한 볼륨키 이벤트 처리
- ✅ MediaSessionCompat.Callback을 통한 미디어 버튼 이벤트 처리
- ✅ PTT 모드/볼륨 모드 토글 기능 구현
- ✅ PTTScreen에 PTTModeToggleCard UI 추가

### 2.3 주요 구현 내용
```kotlin
// MediaSession 초기화
mediaSession = MediaSessionCompat(this, "PTTServiceMediaSession")

// VolumeProvider 설정 (볼륨키 이벤트 가로채기)
volumeProvider = object : VolumeProviderCompat(
    VolumeProviderCompat.VOLUME_CONTROL_RELATIVE,
    100, 50
) {
    override fun onAdjustVolume(direction: Int) {
        handleVolumeKeyEvent(direction)
    }
}

// PTT 모드에 따른 볼륨 제어 전환
if (isPTTModeEnabled) {
    mediaSession.setPlaybackToRemote(volumeProvider)  // PTT 모드
} else {
    mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC)  // 일반 볼륨
}
```

## 3. 현재 문제점

### 3.1 주요 이슈
- **볼륨키 PTT가 작동하지 않음**
  - 증상: 볼륨키를 눌러도 PTT가 시작되지 않고 일반 볼륨 조절만 됨
  - 로그 분석 결과: MediaSession 관련 이벤트는 시스템 레벨에서 발생하나, 앱의 콜백이 호출되지 않음

### 3.2 확인된 로그
```
adjustSuggestedStreamVolumeForUid : suggestedStreamType=-2147483648, direction=-1
dispatchVolumeKeyEvent, pkg=com.designated.callmanager, opPkg=com.designated.callmanager, pid=9986
```
- 시스템은 볼륨키 이벤트를 감지하고 있음
- 이벤트가 앱으로 전달되고 있음
- 하지만 VolumeProvider의 `onAdjustVolume()` 콜백이 호출되지 않음

## 4. 의심되는 원인

### 4.1 MediaSession 활성화 문제
- MediaSession이 제대로 활성화되지 않았을 가능성
- PlaybackState 설정이 부적절할 가능성

### 4.2 VolumeProvider 연결 문제
- `setPlaybackToRemote(volumeProvider)` 호출 타이밍 문제
- MediaSession과 VolumeProvider 연결이 제대로 되지 않음

### 4.3 시스템 우선순위 문제
- 다른 미디어 앱이 MediaSession을 선점하고 있을 가능성
- 시스템 볼륨 UI가 우선순위를 가져가는 문제

## 5. 시도해볼 해결 방법

### 5.1 MediaSession 우선순위 확보
- [ ] MediaSessionCompat.setSessionActivity() 설정
- [ ] MediaController 생성 및 연결
- [ ] AudioFocus 요청 추가

### 5.2 PlaybackState 개선
- [ ] STATE_PLAYING으로 변경 (현재 STATE_PAUSED)
- [ ] 더미 미디어 재생 상태 유지
- [ ] MediaMetadata 설정 추가

### 5.3 VolumeProvider 개선
- [ ] VOLUME_CONTROL_ABSOLUTE 타입 시도
- [ ] onSetVolumeTo() 메서드 구현
- [ ] 초기 볼륨 값 조정

### 5.4 대안 고려
- [ ] MediaButtonReceiver 사용
- [ ] 하드웨어 키 이벤트 직접 처리
- [ ] 블루투스 헤드셋 버튼 활용

## 6. Pickup 앱 적용 상태
- ⏸️ MediaSession 구현 부분적 완료
- ⏸️ Call Manager 앱의 문제 해결 후 동일하게 적용 예정

## 7. 참고 자료
- 전문가 자문 문서: `C:\app_dev\designated_driver\작업내역\접근성과 MediaSession.txt`
- Android MediaSession 공식 문서
- Samsung One UI 키 이벤트 처리 특성

## 8. 다음 단계
1. PTTForegroundService 로그 상세 분석
2. MediaSession 활성화 상태 확인
3. VolumeProvider 콜백 호출 여부 디버깅
4. 필요시 대안 솔루션 검토

## 9. 중요 노트
- AccessibilityService 방식으로는 Android 15 + Samsung 기기에서 볼륨키 제어 불가능
- MediaSession이 공식 권장 방법이나, 구현에 세부 조정이 필요
- 백그라운드 및 화면 꺼짐 상태에서도 작동해야 함