# Android 15 PTT 접근성 서비스 자동 비활성화 문제 해결

## 📅 작업 날짜: 2025-08-19

## 🎯 문제 상황
- **Android 13**: 화면 꺼진 상태에서도 볼륨 PTT 정상 작동
- **Android 15**: 접근성 서비스가 자동으로 비활성화되어 포그라운드에서만 작동

## ✅ 구현된 해결 방안

### 1. 접근성 서비스 설정 강화
**파일:** `ptt_accessibility_service_config.xml`
```xml
android:isAccessibilityTool="true"  <!-- Android 15 필수 -->
android:accessibilityFlags="...|flagEnableAccessibilityVolume"
```

### 2. PTTAccessibilityService Foreground 실행
**주요 변경사항:**
- Android 15 감지 시 Foreground Service로 전환
- 지속적인 알림 표시로 서비스 유지
- 15분마다 서비스 상태 체크 및 재설정

```kotlin
if (Build.VERSION.SDK_INT >= 35) { // Android 15
    startForegroundServiceForAndroid15()
    startPeriodicServiceCheck()
}
```

### 3. AndroidManifest.xml 권한 강화
```xml
<service
    android:name=".service.PTTAccessibilityService"
    android:exported="true"  <!-- Android 15 필수 -->
    android:foregroundServiceType="specialUse"
    android:directBootAware="true">
```

### 4. 버전별 분기 처리
- Android 13-14: 기존 방식 유지
- Android 15: 강화된 Wake Lock + Foreground Service

## 🔧 핵심 개선사항

### Android 15 전용 처리
1. **Foreground Service 필수 실행**
   - 접근성 서비스를 Foreground로 유지
   - 시스템의 자동 종료 방지

2. **주기적 서비스 체크**
   - 15분마다 서비스 상태 확인
   - 필요시 자동 재활성화

3. **강화된 Wake Lock**
   ```kotlin
   PowerManager.PARTIAL_WAKE_LOCK or 
   PowerManager.ACQUIRE_CAUSES_WAKEUP or
   PowerManager.ON_AFTER_RELEASE
   ```

4. **사용자 알림 시스템**
   - 접근성 서비스 비활성화 감지
   - 설정 화면으로 즉시 이동 가능한 알림

## 📱 테스트 필요사항

1. Android 15 실제 디바이스에서 테스트
2. 장시간 백그라운드 유지 테스트
3. 재부팅 후 자동 시작 확인
4. 배터리 최적화 예외 설정 확인

## 🚀 추가 권장사항

1. **사용자 가이드 제공**
   - Android 15 사용자를 위한 설정 안내
   - 접근성 서비스 유지 방법 설명

2. **대체 방안 준비**
   - MediaProjection API 활용 검토
   - Companion Device Manager 연동

3. **모니터링 강화**
   - Firebase Analytics로 서비스 중단 추적
   - 자동 복구 메커니즘 구현

## 📌 주의사항

- Android 15는 보안 강화로 접근성 서비스 제한이 엄격함
- 제조사별 추가 제한 가능성 있음 (Samsung OneUI 등)
- 정기적인 Android 정책 변경 모니터링 필요

---

**작성자:** Claude Code Assistant  
**상태:** 구현 완료 - 실제 디바이스 테스트 필요