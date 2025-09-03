# PTT 볼륨키 시스템 개발 현황 및 남은 문제점

## 📋 작업 개요
- **목표**: 볼륨키를 통한 PTT(Push-to-Talk) 시스템 구현
- **핵심 기능**: 자동 채널 참여/해제, 비용절감을 위한 RTM 2.x 연동
- **작업 기간**: 2024년 후반 ~ 현재

## ✅ 완료된 작업 항목

### 1. 볼륨키 감지 시스템 구현 ✅
- **PTTAccessibilityService**: Android AccessibilityService를 통한 볼륨키 이벤트 감지
- **디바운싱 로직**: 빠른 연속 키 입력 방지 (100ms → 200ms로 조정)
- **처리 상태 플래그**: `isProcessing`으로 중복 이벤트 방지

### 2. PTT 시스템 아키텍처 구축 ✅
- **PTTForegroundService**: 백그라운드 PTT 서비스 운영
- **PTTController**: RTC 엔진과 토큰 관리 통합
- **SimplePTTEngine**: Agora RTC SDK 래핑
- **BeepSoundManager**: PTT 시작/종료 비프음 관리

### 3. RTM 2.x 시그널링 연동 ✅
- **SignalingManager**: RTM 2.x SDK를 통한 실시간 시그널링
- **자동 채널 구독**: 기본 채널 정보 설정 시 자동 RTM 채널 구독
- **PTT 신호 전송**: 시작/종료 신호를 RTM으로 전송 (FCM 대체)

### 4. 자동 채널 관리 구현 ✅
- **자동 참여**: 볼륨키 누를 때 자동으로 채널 참여
- **자동 해제**: 볼륨키 놓을 때 채널에서 완전 해제 ✅ (안정적으로 작동)
- **토큰 자동 갱신**: Firebase Functions를 통한 Agora 토큰 자동 발급

### 5. 상태 관리 시스템 ✅
- **StateFlow 기반**: 실시간 PTT 상태 관리
- **상태 전환**: Disconnected → Connecting → Connected → Transmitting → Disconnected
- **알림 업데이트**: 현재 상태에 따른 Notification 업데이트

## ❌ 남은 문제점들

### 1. 🔴 간헐적 초기 비프음 문제
**증상**: 
- 첫 번째 볼륨키 누를 때 비프음이 나오지 않는 경우 발생
- 가끔 나오고 가끔 안 나옴

**추정 원인**:
- BeepSoundManager 초기화 타이밍 이슈
- SoundPool 로딩 지연 문제

**시도한 해결방법**:
- 초기화 상태 체크 강화
- 시스템 비프음 폴백 로직 추가
- 상세한 디버그 로그 추가

**다음 시도할 방법**:
- SoundPool.OnLoadCompleteListener 추가
- 비프음 파일 사전 로딩 확인
- MediaPlayer로 대체 시도

### 2. 🔴 볼륨키 누름 지속 문제
**증상**:
- 볼륨키를 누르고 있어도 송신 상태가 짧게만 유지됨
- DOWN → UP 이벤트가 너무 빨리 연속 발생

**추정 원인**:
- Android 시스템의 볼륨키 이벤트 처리 방식
- AccessibilityService의 이벤트 소비 불완전

**시도한 해결방법**:
- 디바운싱 딜레이 증가 (100ms → 200ms)
- 처리 상태 플래그 추가
- UP 이벤트 디바운싱 제거

**다음 시도할 방법**:
- KeyEvent.getRepeatCount() 활용
- 하드웨어 키 이벤트 직접 처리
- Timer 기반 지연 처리 도입

### 3. 🟡 송신 대기 상태 문제
**증상**:
- 실제 송신 전에 "송신대기" 상태로 잠깐 변경됨
- 상태 전환이 불안정함

**추정 원인**:
- PTTState 상태 관리의 중복 설정
- Agora 채널 참여와 전송 시작 사이의 지연

**시도한 해결방법**:
- 상태 전환 순서 명확화
- stopPTT 시 Disconnected 상태로 직접 변경

**현재 상태**: 부분적으로 개선됨, 완전 해결 필요

## 📂 주요 파일 구조

```
call_manager/app/src/main/java/com/designated/callmanager/
├── ptt/
│   ├── service/
│   │   ├── PTTAccessibilityService.kt     # 볼륨키 감지
│   │   └── PTTForegroundService.kt        # PTT 백그라운드 서비스
│   ├── core/
│   │   ├── PTTController.kt               # PTT 메인 컨트롤러
│   │   ├── SimplePTTEngine.kt             # RTC 엔진 래핑
│   │   ├── BeepSoundManager.kt            # 비프음 관리
│   │   └── UIDManager.kt                  # UID 관리
│   ├── manager/
│   │   └── SignalingManager.kt            # RTM 2.x 시그널링
│   ├── network/
│   │   └── TokenManager.kt                # Agora 토큰 관리
│   └── state/
│       └── PTTState.kt                    # PTT 상태 정의
└── res/raw/
    └── ptt_beep.m4a                      # PTT 비프음 파일
```

## 🔧 주요 설정 및 권한

### AndroidManifest.xml 설정
```xml
<!-- AccessibilityService 등록 -->
<service android:name=".ptt.service.PTTAccessibilityService"
         android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data android:name="android.accessibilityservice"
               android:resource="@xml/accessibility_service_config" />
</service>

<!-- Foreground Service 등록 -->
<service android:name=".ptt.service.PTTForegroundService"
         android:foregroundServiceType="microphone" />
```

### 필요한 권한
- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.BIND_ACCESSIBILITY_SERVICE`
- `android.permission.POST_NOTIFICATIONS` (Android 13+)

## 📊 테스트 로그 분석

### 정상 동작 시 로그 패턴
```
I PTTAccessibilityService: Volume key pressed - starting PTT
I PTTForegroundService: Starting PTT...
D BeepSoundManager: PTT start beep played (real sound)
I PTTController: Getting token for channel: yangpyong_office_4_ptt, UID: 1000
I PTTController: Joining channel: yangpyong_office_4_ptt with UID: 1000
I PTTForegroundService: Joined channel: yangpyong_office_4_ptt with UID: 1000
I PTTController: PTT started successfully on channel: yangpyong_office_4_ptt
I PTTAccessibilityService: Volume key released - stopping PTT
I PTTController: Left channel completely on PTT stop
```

### 문제 발생 시 로그 패턴
```
I PTTAccessibilityService: Volume key pressed - starting PTT
D BeepSoundManager: PTT start beep played (system fallback) - initialized: true, soundId: 0
I PTTAccessibilityService: Volume key released - stopping PTT  # ← 너무 빠른 UP 이벤트
```

## 🎯 다음 단계 작업 계획

### 우선순위 1: 비프음 안정화
1. SoundPool.OnLoadCompleteListener 구현
2. 비프음 파일 로딩 완료 대기 로직
3. MediaPlayer 대체 방안 검토

### 우선순위 2: 볼륨키 지속 처리 개선
1. KeyEvent.getRepeatCount() 활용 검토
2. Timer 기반 지연 해제 로직 구현
3. 하드웨어 키 이벤트 직접 처리 방안

### 우선순위 3: 픽업앱 이식
1. 성공한 로직을 pickup_app에 이식
2. 불필요한 코드 제거
3. 공통 모듈화 검토

## 💡 참고사항

### 현재 작동하는 부분
- ✅ 볼륨키 감지 (AccessibilityService)
- ✅ 채널 자동 참여
- ✅ 채널 자동 해제 (안정적)
- ✅ RTM 시그널링 연동
- ✅ 기본적인 PTT 송수신

### 불안정한 부분
- ❌ 초기 비프음 (간헐적 실패)
- ❌ 볼륨키 지속 누름 (짧게만 유지)
- ⚠️ 상태 전환 (부분적 개선)

### 개발환경 정보
- **Android Studio**: 최신 버전
- **Kotlin**: 1.8+
- **Agora RTC SDK**: 4.x
- **Agora RTM SDK**: 2.x
- **Firebase**: Functions, Firestore
- **Target SDK**: 34 (Android 14)

---

**작성일**: 2024년 말
**마지막 업데이트**: PTT 연결해제 안정화 완료
**다음 작업**: 비프음 및 볼륨키 지속 처리 개선