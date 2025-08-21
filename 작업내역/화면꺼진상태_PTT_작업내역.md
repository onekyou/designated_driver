# 화면이 꺼진 상태에서 PTT 송신 문제 해결 작업내역

## 📅 작업 날짜: 2025-08-18

## 🎯 작업 목표
화면이 완전히 꺼진 상태에서도 PTT(Push-to-Talk) 송신이 가능하도록 시스템 개선

## 🔍 문제 현황
- 기존 PTT 시스템이 화면이 꺼진 상태에서 제대로 작동하지 않음
- 볼륨키 PTT 기능이 화면 off 상태에서 응답하지 않는 문제

---

## 📋 완료된 작업 항목

### ✅ 1. 강화된 Wake Lock 및 Screen Wake 기능 추가
**파일:** `PTTAccessibilityService.kt`
- CPU Wake Lock (`PARTIAL_WAKE_LOCK`) 추가로 화면 꺼져도 키 이벤트 처리 가능
- PTT 버튼 누를 때 임시 화면 깨우기 기능 (`SCREEN_BRIGHT_WAKE_LOCK`)
- 자동 Wake Lock 해제 로직으로 배터리 최적화

### ✅ 2. MediaSessionPTTService Wake Lock 및 화면 처리 개선
**파일:** `MediaSessionPTTService.kt`
- `ACQUIRE_CAUSES_WAKEUP` 플래그 추가로 Wake Lock 강화
- 화면 꺼진 상태 감지 시 PTT 전용 화면 깨우기 로직 구현
- 화면 상태별 PTT 처리 로깅 강화

### ✅ 3. Battery Optimization 예외 처리 추가
**파일:** `BatteryOptimizationHelper.kt` (기존 구현 확인)
- 배터리 최적화 예외 요청 기능 구현됨
- 사용자 권한 요청 및 설정 유도 기능

### ✅ 4. 화면 꺼진 상태에서 PTT 송신 문제 해결
**주요 개선사항:**
- PTT 시작/중지 로직에 화면 상태 감지 추가
- 화면 꺼진 상태에서 PTT 시작 시 2초간 화면 깨우기
- PowerManager를 통한 화면 상태 실시간 체크
- 상세한 로깅으로 디버깅 지원

### ✅ 5. 화면 꺼진 상태 PTT 기능 빌드 및 테스트
**결과:**
- 성공적으로 빌드 완료
- APK 파일 생성: `app-debug.apk`, `app-release-unsigned.apk`

---

## 🛠️ 주요 코드 변경사항

### MediaSessionPTTService.kt 개선사항

#### Wake Lock 강화
```kotlin
// 기존
wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "CallManager:MediaSessionPTTWakeLock"
)

// 개선 후
wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
    "CallManager:MediaSessionPTTWakeLock"
)
```

#### PTT 시작 시 화면 처리 로직 추가
```kotlin
// 화면이 꺼진 상태에서도 PTT 처리를 위한 추가 Wake Lock
if (!powerManager.isInteractive) {
    Log.i(TAG, "화면 꺼진 상태에서 PTT 시작 - 추가 처리 적용")
    
    // 짧은 시간 동안 화면 깨우기 (PTT 피드백을 위해)
    val screenWakeLock = powerManager.newWakeLock(
        PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "CallManager:PTTScreenWakeup"
    )
    screenWakeLock.acquire(2000) // 2초간 화면 켜기
}
```

### PTTAccessibilityService.kt 개선사항

#### 화면 깨우기 로직
```kotlin
private fun wakeUpScreen() {
    try {
        if (!powerManager.isInteractive) {
            Log.i(TAG, "📱 화면이 꺼진 상태 감지 - 화면 깨우기 시도")
            
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CallManager:PTTScreenWake"
            )
            screenWakeLock?.acquire(3000) // 3초간 화면 켜기
        }
    } catch (e: Exception) {
        Log.e(TAG, "화면 깰기 실패", e)
    }
}
```

---

## 🔧 이전 커밋 내역 (참고)

### 최근 커밋: `30af5c1` - "feat: Android 15 호환성 개선 및 픽업앱 기능 추가"
**주요 변경사항:**
- AndroidManifest.xml 권한 추가 (88줄 수정)
- MediaSessionPTTService.kt 새로 생성 (740줄 추가)
- PTTAccessibilityService.kt 새로 생성 (166줄 추가)
- ptt_accessibility_service_config.xml 설정 파일 추가
- 총 10개 파일에서 2,352줄 추가, 81줄 삭제

### 이전 커밋들:
- `6ae77f7` - "fix: PTT 볼륨키 시스템 차단 문제 해결"
- `95cbdb0` - "feat: PTT 자동채널 참여 기능 구현 (서비스 레벨)"
- `d3c878b` - "feat: 픽업앱 PTT 최적화 시스템 완전 이식 (Phase 1-4)"

---

## ❌ 문제점 및 추가 작업 필요사항

### 현재 문제:
사용자 피드백에 따르면 화면 꺼진 상태에서 PTT가 여전히 작동하지 않음

### 추가 검토 필요사항:
1. **Android 도즈 모드(Doze Mode) 대응**
   - Android 6.0+ 도즈 모드에서 앱 활동 제한 문제
   - 화이트리스트 등록 필요성 검토

2. **제조사별 배터리 최적화 정책**
   - Samsung, Xiaomi, Huawei 등 제조사별 절전 모드 대응
   - 자동 시작 관리자 설정 안내 필요

3. **Accessibility Service 활성화 상태 체크**
   - 사용자가 접근성 서비스를 비활성화했을 가능성
   - 런타임 상태 체크 및 안내 로직 필요

4. **Audio Focus 경합 문제**
   - 다른 미디어 앱과의 Audio Focus 경합
   - 더 강력한 Audio Focus 정책 필요

5. **Wake Lock 권한 문제**
   - 일부 Android 버전에서 Wake Lock 제한
   - 대체 방안 검토 필요

---

## 📊 테스트 결과

### 빌드 상태: ✅ 성공
- Debug APK: `C:\app_dev\designated_driver\call_manager\app\build\outputs\apk\debug\app-debug.apk`
- Release APK: `C:\app_dev\designated_driver\call_manager\app\build\outputs\apk\release\app-release-unsigned.apk`

### 실제 테스트: ❌ 실패
- 화면 꺼진 상태에서 PTT 미작동 확인됨
- 추가 디버깅 및 문제 분석 필요

---

## 🚀 다음 단계 계획

1. **실제 디바이스에서 로그 수집**
   - ADB logcat을 통한 상세 로그 분석
   - PTT 버튼 누를 때 실제 이벤트 발생 여부 확인

2. **Android 도즈 모드 대응**
   - 앱 배터리 최적화 예외 설정 강화
   - REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 권한 활용

3. **제조사별 최적화 정책 우회**
   - 각 제조사별 절전 설정 안내 UI 추가
   - 자동 시작 관리 설정 가이드 제공

4. **대체 PTT 트리거 방식 검토**
   - Volume Key 대신 다른 하드웨어 버튼 활용
   - 화면 터치 기반 PTT 모드 추가

---

**작성자:** Claude Code Assistant  
**작성일:** 2025-08-18  
**상태:** 문제 지속 중 - 추가 작업 필요