# MediaSession 기반 볼륨키 PTT 시스템 구축 마스터플랜 v3

## 📋 프로젝트 개요
기존 PTT 시스템 위에 MediaSession을 활용한 볼륨키 PTT 기능을 추가하는 프로젝트
**목표**: 복잡한 우회 방법 없이 Android 표준 방식으로 깔끔하게 구현

## 🎯 최종 목표
- PTT 모드에서 볼륨키 누름 → PTT 송신 시작
- PTT 모드에서 볼륨키 뗌 → PTT 송신 중지  
- 볼륨 모드에서 볼륨키 → 정상적인 시스템 볼륨 조절
- 백그라운드에서도 안정적으로 동작

---

## 📝 그간의 실패 분석

### ❌ 실패한 접근법들
1. **External MediaButtonReceiver** - Internal callback과 충돌
2. **AudioManager.registerMediaButtonEventReceiver** - 구식 방법, 불안정
3. **Custom BroadcastReceiver** - 복잡성 증가, 권한 문제
4. **Silent MediaPlayer** - 과도한 리소스 사용
5. **Multiple fallback approaches** - 서로 간섭하여 더 불안정

### 🔍 근본 원인 분석
1. **MediaSession 콜백 등록 순서 오류** - 활성화 후 콜백 설정
2. **MEDIA_BUTTON 권한 누락** - Android 13+ 필수 권한
3. **PlaybackState 설정 부족** - 볼륨키 인식을 위한 최소 요구사항 미충족
4. **AudioFocus 관리 부실** - 다른 미디어 앱과의 경쟁에서 패배

---

## 🏗️ 단계별 구현 계획

### Phase 1: 기초 환경 준비 (필수)
**목표**: MediaSession이 동작할 수 있는 최소 환경 구축

#### Step 1-1: 권한 및 Manifest 정리
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.MEDIA_BUTTON" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

**체크 요소**:
- [ ] MEDIA_BUTTON 권한 추가됨
- [ ] 불필요한 MediaButtonReceiver 선언 제거됨
- [ ] Foreground service type이 mediaPlayback 포함

**실패 시 보강**:
- Android 버전별 권한 차이 확인
- targetSdkVersion과 권한 요구사항 매칭

#### Step 1-2: Service 기본 구조 정리
```kotlin
class PTTForegroundService : Service() {
    private lateinit var mediaSession: MediaSessionCompat
    private var isPTTModeEnabled = true
    
    // 기존 PTT 관련 변수들은 그대로 유지
    private lateinit var pttController: PTTController
    private lateinit var pttEngine: SimplePTTEngine
}
```

**체크 요소**:
- [ ] 불필요한 MediaPlayer, BroadcastReceiver 변수 제거
- [ ] 기존 PTT 시스템과 충돌하지 않는 구조

### Phase 2: MediaSession 핵심 구현 (최소 MVP)
**목표**: 가장 기본적인 MediaSession으로 볼륨키 이벤트 수신

#### Step 2-1: MediaSession 초기화 (올바른 순서)
```kotlin
private fun initializeMediaSession() {
    // 1. MediaSession 생성
    mediaSession = MediaSessionCompat(this, "PTTMediaSession")
    
    // 2. 콜백 먼저 설정 (활성화 전에!)
    mediaSession.setCallback(object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            Log.d(TAG, "📱 onMediaButtonEvent called!")
            val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            return handleVolumeKey(keyEvent)
        }
    })
    
    // 3. 필수 설정
    mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)
    
    // 4. PlaybackState 설정
    val playbackState = PlaybackStateCompat.Builder()
        .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
        .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
        .build()
    mediaSession.setPlaybackState(playbackState)
    
    // 5. 마지막에 활성화
    mediaSession.isActive = true
}
```

**체크 요소**:
- [ ] setCallback이 isActive = true 이전에 호출됨
- [ ] onMediaButtonEvent 로그가 출력됨
- [ ] KeyEvent가 null이 아님

**실패 시 보강**:
- dumpsys media_session으로 MediaSession 등록 확인
- 다른 미디어 앱 종료 후 테스트
- AudioFocus 요청 추가

#### Step 2-2: 볼륨키 핸들링 로직
```kotlin
private fun handleVolumeKey(keyEvent: KeyEvent?): Boolean {
    if (keyEvent == null) return false
    
    // 볼륨키가 아니면 무시
    if (keyEvent.keyCode != KeyEvent.KEYCODE_VOLUME_UP && 
        keyEvent.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
        return false
    }
    
    // PTT 모드가 아니면 시스템에 위임
    if (!isPTTModeEnabled) {
        Log.d(TAG, "Volume mode - passing to system")
        return false
    }
    
    // PTT 모드에서 볼륨키 처리
    when (keyEvent.action) {
        KeyEvent.ACTION_DOWN -> {
            Log.d(TAG, "🎙️ PTT START")
            startPTT()
        }
        KeyEvent.ACTION_UP -> {
            Log.d(TAG, "🎙️ PTT STOP")  
            stopPTT()
        }
    }
    
    return true // 이벤트 소비
}
```

**체크 요소**:
- [ ] ACTION_DOWN 로그 출력됨
- [ ] ACTION_UP 로그 출력됨
- [ ] PTT 모드/볼륨 모드 전환 시 동작 변경됨

**실패 시 보강**:
- KeyEvent.repeatCount 체크 추가
- 디바운싱 로직 추가
- AudioFocus 상태 확인

### Phase 3: 기존 PTT 시스템과 연동
**목표**: MediaSession 이벤트를 기존 PTT 로직에 연결

#### Step 3-1: PTT 시작/중지 연결
```kotlin
private fun startPTT() {
    if (::pttController.isInitialized) {
        // 기존 PTT 시작 로직 호출
        handleStartPTT() // 기존 메서드 재사용
    }
}

private fun stopPTT() {
    if (::pttController.isInitialized) {
        // 기존 PTT 중지 로직 호출
        handleStopPTT() // 기존 메서드 재사용
    }
}
```

**체크 요소**:
- [ ] PTT 송신이 실제로 시작됨
- [ ] PTT 송신이 실제로 중지됨
- [ ] 기존 PTT 버튼과 동일하게 동작

**실패 시 보강**:
- PTT 상태 동기화 로직 추가
- 중복 호출 방지 로직

### Phase 4: 안정성 및 최적화
**목표**: 프로덕션 환경에서 안정적으로 동작

#### Step 4-1: AudioFocus 관리
```kotlin
private fun requestAudioFocus(): Boolean {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { focusChange ->
            Log.d(TAG, "AudioFocus changed: $focusChange")
        }
        .build()
        
    return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
}
```

#### Step 4-2: 리소스 정리
```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    // MediaSession 정리
    if (::mediaSession.isInitialized) {
        mediaSession.isActive = false
        mediaSession.release()
    }
    
    // AudioFocus 해제
    releaseAudioFocus()
    
    // 기존 PTT 리소스 정리는 그대로 유지
}
```

**체크 요소**:
- [ ] 메모리 리크 없음
- [ ] 서비스 종료 시 깔끔하게 정리됨

---

## 🔧 단계별 디버깅 가이드

### Debug Phase 1: MediaSession 등록 확인
```bash
adb shell dumpsys media_session
```
**확인 사항**:
- MediaSession이 active=true로 등록됨
- 우리 앱이 media button session으로 설정됨

### Debug Phase 2: 로그 모니터링
```bash
adb logcat -s "PTTForegroundService" -v time
```
**확인 사항**:
- "onMediaButtonEvent called!" 로그 출력
- KeyEvent가 정상적으로 파싱됨

### Debug Phase 3: 권한 확인
```bash
adb shell pm list permissions | grep MEDIA_BUTTON
adb shell dumpsys package com.designated.callmanager | grep permission
```

---

## 📊 성공/실패 체크리스트

### ✅ 성공 지표
1. **Basic Success**: 
   - [ ] onMediaButtonEvent 콜백이 호출됨
   - [ ] 볼륨키 ACTION_DOWN/UP 이벤트 수신

2. **Functional Success**:
   - [ ] PTT 모드에서 볼륨키 → PTT 송신
   - [ ] 볼륨 모드에서 볼륨키 → 볼륨 조절
   - [ ] 백그라운드에서도 정상 동작

3. **Production Ready**:
   - [ ] 배터리 소모 최소화
   - [ ] 다른 미디어 앱과 충돌 없음
   - [ ] 안정적인 리소스 관리

### ❌ 실패 시 점검 사항

#### Level 1: 기초 환경 문제
- [ ] MEDIA_BUTTON 권한이 granted 상태인가?
- [ ] MediaSession이 dumpsys에서 확인되는가?
- [ ] Foreground service가 정상 실행 중인가?

#### Level 2: 구현 로직 문제
- [ ] setCallback이 isActive 이전에 호출되었는가?
- [ ] PlaybackState가 PLAYING으로 설정되었는가?
- [ ] FLAG_HANDLES_MEDIA_BUTTONS가 설정되었는가?

#### Level 3: 환경/기기 문제
- [ ] 다른 미디어 앱이 AudioFocus를 점유하고 있는가?
- [ ] Samsung One UI의 특수 설정이 간섭하고 있는가?
- [ ] 기기의 볼륨키 하드웨어 문제는 없는가?

---

## 🚀 구현 우선순위

### 최고 우선순위 (P0)
1. MEDIA_BUTTON 권한 추가
2. MediaSession 콜백 등록 순서 수정
3. 기본 onMediaButtonEvent 구현

### 높은 우선순위 (P1)  
4. 볼륨키 이벤트 핸들링
5. PTT 모드 전환 로직
6. 기존 PTT 시스템 연동

### 보통 우선순위 (P2)
7. AudioFocus 관리
8. 에러 핸들링 강화
9. 성능 최적화

### 낮은 우선순위 (P3)
10. 고급 디버깅 기능
11. 통계/모니터링
12. UI/UX 개선

---

## 📁 코드 구조

### 권장 파일 구조
```
PTTForegroundService.kt
├── MediaSession 관련
│   ├── initializeMediaSession()
│   ├── handleVolumeKey()
│   └── releaseMediaSession()
├── AudioFocus 관리
│   ├── requestAudioFocus()
│   └── releaseAudioFocus()
└── PTT 연동
    ├── startPTT()
    └── stopPTT()
```

### 핵심 메서드 시그니처
```kotlin
// 필수 구현 메서드들
private fun initializeMediaSession()
private fun handleVolumeKey(keyEvent: KeyEvent?): Boolean
private fun startPTT()
private fun stopPTT()
private fun requestAudioFocus(): Boolean
private fun releaseAudioFocus()
```

---

## 🎯 최종 검증 시나리오

### Scenario 1: 기본 동작
1. 앱 실행 → PTT 서비스 시작
2. PTT 모드 활성화
3. 볼륨키 다운 → PTT 송신 시작 확인
4. 볼륨키 업 → PTT 송신 중지 확인

### Scenario 2: 모드 전환  
1. PTT 모드 → 볼륨 모드 전환
2. 볼륨키 → 시스템 볼륨 조절 확인
3. 볼륨 모드 → PTT 모드 전환
4. 볼륨키 → PTT 동작 확인

### Scenario 3: 백그라운드 동작
1. 앱을 백그라운드로 이동
2. 다른 앱 실행 중 볼륨키 테스트
3. PTT 기능 정상 동작 확인

---

## 💡 핵심 원칙

1. **Keep It Simple**: 복잡한 우회 로직 지양
2. **Android Standard**: 표준 MediaSession API만 사용
3. **Minimal Integration**: 기존 PTT 시스템 최소 변경
4. **Proper Order**: MediaSession 생명주기 순서 엄수
5. **Clean Resources**: 리소스 정리 철저히

---

## 📞 비상 계획 (Contingency Plan)

만약 기본 MediaSession 접근법도 실패할 경우:

### Plan B: AccessibilityService
- 시스템 레벨에서 키 이벤트 직접 가로채기
- 사용자 권한 요구하지만 확실한 방법

### Plan C: 앱 포그라운드 전용
- Activity.onKeyDown()으로 제한적 구현
- 백그라운드 동작 포기하고 안정성 확보

### Plan D: 하드웨어 버튼 대안
- 블루투스 헤드셋 버튼 활용
- 화면 터치 PTT 버튼 강화

---

**마지막 업데이트**: 2024-08-30
**버전**: v3.0 - 기초 중심 재설계
**작성자**: MediaSession PTT 개발팀