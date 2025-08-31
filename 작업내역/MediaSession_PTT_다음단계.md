# MediaSession PTT 구현 - 다음 단계 작업 계획

## 즉시 해결해야 할 문제
**핵심 문제: 볼륨키를 눌러도 PTT가 작동하지 않고 일반 볼륨 조절만 됨**

## 1. 즉각적인 확인 사항

### 1.1 로그 모니터링 (우선순위: 높음)
```bash
# PTTForegroundService 태그로 필터링하여 확인
adb logcat -s PTTForegroundService
```

다음 로그가 나타나는지 확인:
- "Volume key detected: direction=" → VolumeProvider가 이벤트를 받고 있는지
- "MediaButton event: keyCode=" → MediaButton 콜백이 호출되는지
- "Volume key event: direction=" → handleVolumeKeyEvent가 실행되는지

### 1.2 PTT 모드 상태 확인
- PTT/볼륨 토글 스위치가 PTT 모드로 되어 있는지 확인
- isPTTModeEnabled 값이 true인지 로그로 확인

## 2. 수정이 필요할 수 있는 부분

### 2.1 MediaSession 초기화 순서 변경
```kotlin
// 현재: updateMediaSessionVolumeControl() → setPlaybackState() → setActive()
// 변경: setPlaybackState() → setActive() → updateMediaSessionVolumeControl()
```

### 2.2 PlaybackState 상태 변경
```kotlin
// 현재: STATE_PAUSED
// 테스트: STATE_PLAYING으로 변경
.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
```

### 2.3 AudioFocus 요청 추가
```kotlin
// MediaSession 초기화 시 AudioFocus 요청
val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
audioManager.requestAudioFocus(
    null,
    AudioManager.STREAM_MUSIC,
    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
)
```

## 3. Pickup 앱 적용 작업

### 3.1 남은 작업 (Call Manager 문제 해결 후)
- [ ] handleTogglePTTMode() 메서드 구현
- [ ] updateMediaSessionVolumeControl() 메서드 구현  
- [ ] onDestroy()에서 MediaSession 정리
- [ ] PTTScreen에 PTTModeToggleCard UI 추가

### 3.2 현재 상태
- MediaSession 의존성 추가 완료
- AndroidManifest.xml 수정 완료
- PTTForegroundService 부분 구현 완료

## 4. 테스트 계획

### 4.1 단계별 테스트
1. **기본 동작 테스트**
   - PTT 서비스 시작
   - 로그 확인
   - 볼륨키 동작 확인

2. **모드 전환 테스트**
   - PTT 모드 ↔ 볼륨 모드 전환
   - 각 모드에서 볼륨키 동작 확인

3. **백그라운드 테스트**
   - 앱 백그라운드 상태
   - 화면 꺼짐 상태
   - 다른 앱 실행 중

## 5. 예상 소요 시간
- 문제 해결: 1-2시간
- Pickup 앱 적용: 30분
- 전체 테스트: 30분

## 6. 리스크 및 대안
- **리스크**: Samsung 기기의 특수한 제약으로 MediaSession도 작동하지 않을 가능성
- **대안 1**: 화면 오버레이 버튼 사용
- **대안 2**: 블루투스 헤드셋 버튼 활용
- **대안 3**: 알림바의 액션 버튼 사용

## 7. 완료 기준
- [ ] 볼륨 다운키로 PTT 송신 시작/중지 가능
- [ ] PTT/볼륨 모드 전환 정상 작동
- [ ] 백그라운드 및 화면 꺼짐 상태에서 작동
- [ ] Call Manager와 Pickup 앱 모두 동일하게 작동