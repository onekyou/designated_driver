---
name: phase6_day4_phaseB_rfc
description: Phase 6 ② Day 4 Phase B RFC — Platform.isIOS 분기 4곳 + Entitlement + Codemagic Phase B. 아이폰 도착 시 즉시 착수 가능한 수정 스니펫 + 검증 전략.
type: project
---

# Phase 6 ② Day 4 Phase B RFC (2026-04-18)

**Why**: Phase A 확증 완료(Codemagic Build #4, `7a06cba9`, 15m 53s). Phase A는 저장 필드만 포함, Platform.isIOS 기타 분기는 아이폰 실기기 부재로 Phase B로 이관. 본 RFC는 아이폰 도착 즉시 단계적으로 적용할 수정 스니펫과 검증 전략.

**How to apply**: 분기 한 건씩 추가 → Codemagic + iOS 실기기로 즉시 회귀 확인 → 다음 건 진행. 맹목적 일괄 적용 금지.

---

## 변경 대상 5곳

### B-1. LocalNotification iOS interruptionLevel (Time Sensitive)

**파일**: `lib/services/fcm_service.dart:148-160` (`_showLocalNotification`)

**현재**:
```dart
const iosDetails = DarwinNotificationDetails(
  presentAlert: true, presentBadge: true, presentSound: true,
);
```

**제안**: `call_assigned` 타입일 때 `interruptionLevel: .timeSensitive` (집중모드/방해금지 통과)
```dart
final iosDetails = DarwinNotificationDetails(
  presentAlert: true,
  presentBadge: true,
  presentSound: true,
  interruptionLevel: (type == typeCallAssigned)
      ? InterruptionLevel.timeSensitive
      : InterruptionLevel.active,
);
```

**전제 entitlement** (Xcode Runner target → Signing & Capabilities):
- `com.apple.developer.usernotifications.time-sensitive = YES`
- Apple Developer Portal → App ID `com.designated.driverapp.app` → Capabilities → Time Sensitive Notifications 체크

**회귀 위험**: Android 영향 0 (Android block은 그대로). iOS는 옵션 추가라 권한 미승인 상태에서도 fallback `.active`.

---

### B-2. Android FullScreenIntent 활성화 (iOS 무관)

**파일**: 동일 `_showLocalNotification`

**현재**: Android에 `fullScreenIntent` 없음 → 잠금화면 통과 안 됨 (Kotlin은 LockScreenActivity로 처리 중)

**제안**: `call_assigned`일 때만 Android 측에 추가
```dart
final androidDetails = AndroidNotificationDetails(
  channelId, channelName,
  channelDescription: channelName,
  importance: Importance.high,
  priority: Priority.high,
  showWhen: true,
  enableVibration: true,
  playSound: true,
  fullScreenIntent: (type == typeCallAssigned), // 신규
  category: (type == typeCallAssigned)
      ? AndroidNotificationCategory.call
      : null,
);
```

**주의**: Android 14+는 `USE_FULL_SCREEN_INTENT` 권한을 Call/Alarm 카테고리 앱만 자동 허용. 일반 앱은 Settings에서 수동 토글 필요. Flutter AndroidManifest.xml에 `<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />` 선언 필요.

**회귀 위험**: Kotlin 기사앱과 병존 시 중복 알림. Flutter 기사앱이 LockScreenActivity를 MethodChannel로 이미 호출(B-5 참조)하므로 fullScreenIntent까지 켜면 삼중 알림. **신중 적용**: Kotlin LockScreenActivity 경로와 병행 중이면 fullScreenIntent는 Phase B 후반으로 연기.

---

### B-3. FCM 권한 요청 provisional 옵션

**파일**: `lib/services/fcm_service.dart:33-35`

**현재**: `provisional: false` → 첫 앱 실행 시 시스템 알림 권한 dialog

**제안 옵션**:
- (a) 현상 유지 — 명시적 권한 허용이 이후 메시지 수신율 높음
- (b) `provisional: true` + 이후 critical 콜 수신 후 정식 승격 — 첫 인상 부드러움

**판단**: 대리운전 기사앱은 앱 설치 직후 명시적 허용이 UX상 자연스러움 (콜 알림이 핵심 기능). **(a) 현상 유지 권장**. Phase B에서는 **변경 없음**.

---

### B-4. LocalNotification iOS Time Sensitive permission 요청

**파일**: `lib/services/fcm_service.dart:65-82` (`_initializeLocalNotifications`)

**현재**:
```dart
const iosSettings = DarwinInitializationSettings(
  requestAlertPermission: true,
  requestBadgePermission: true,
  requestSoundPermission: true,
);
```

**제안**: Critical Alert은 Apple 승인 엔터프라이즈 전용이라 일반 앱 불가. Time Sensitive는 entitlement만으로 충분 (별도 permission 요청 없음). **현재 그대로 유지**, B-1의 interruptionLevel로 커버.

---

### B-5. LockScreenService iOS 호출 가드

**파일**: `lib/services/fcm_service.dart:183-190` + `lib/services/lock_screen_service.dart`

**현재**: `showLockScreenForCall`이 iOS에서도 호출됨 → MethodChannel PlatformException → try-catch로 삼킴. 에러 로그만 남고 UX 문제 없음이나 불필요한 invoke.

**제안**: `lock_screen_service.dart` 내부에서 iOS 가드
```dart
import 'dart:io' show Platform;

class LockScreenService {
  static Future<void> showLockScreen({...}) async {
    if (Platform.isIOS) return; // iOS는 Time Sensitive + CallKit로 대체 (B-1에 흡수)
    try {
      await _channel.invokeMethod('showLockScreen', {...});
    } on PlatformException catch (e) {
      debugPrint('[LockScreen] 실행 실패: $e');
    }
  }
}
```

**회귀 위험**: 0. Android 동작 그대로.

---

## Foreground Service (변경 불필요)

**파일**: `lib/services/foreground_service.dart:23-25`

이미 `iosNotificationOptions.showNotification = false` 설정됨. iOS에서 `start()` 호출해도 백그라운드 알림 띄우지 않음. **Phase B 변경 없음**.

단 iOS Background Modes(`fetch`, `remote-notification`, `location`)는 Apple 정책상 **무제한 백그라운드 실행 불가**. Kotlin의 DriverForegroundService와 동등한 동작은 iOS 원천 불가. 대신 FCM `content-available: 1` (silent push) + iOS APNs에 의존.

---

## Info.plist 변경 범위

**현재 6개 Privacy 문구 모두 App Store 심사 통과 수준** (flutter-expert Explore 검토). 변경 불필요.

**추가 가능 (심사 피드백 시)**:
- `NSUserTrackingUsageDescription` — IDFA 사용 시에만 (현재 미사용)
- `NSCameraUsageDescription` — QR 스캔 기능 추가 시 (현재 미사용, 향후 손님앱 확장 시)

---

## Entitlements 추가 (B-1 전제)

**파일**: `ios/Runner/Runner.entitlements` (신규 생성 필요 시)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>aps-environment</key>
    <string>development</string>
    <key>com.apple.developer.usernotifications.time-sensitive</key>
    <true/>
</dict>
</plist>
```

**pbxproj 참조 추가**: `CODE_SIGN_ENTITLEMENTS = Runner/Runner.entitlements;` (Debug/Release/Profile 3곳)

**Xcode GUI 경로** (로컬 Mac 또는 Codemagic SSH 세션):
- Runner target → Signing & Capabilities → `+ Capability` → **Push Notifications** 추가 → Time Sensitive 자동 포함
- 또는 수동으로 pbxproj에 `CODE_SIGN_ENTITLEMENTS` 추가 + entitlements 파일 생성

**Apple Developer Portal**:
- Identifiers → `com.designated.driverapp.app` → Edit → **Push Notifications** 체크 → Save
- APNs Authentication Key(.p8) 발급 후 Firebase Console → Cloud Messaging → APNs 등록

---

## Codemagic Phase B yaml

**현재 워크플로우 `ios-driver-test-build`에 추가 스텝**, 또는 **신규 워크플로우 `ios-driver-testflight`로 분리** 권장.

**신규 워크플로우 초안**:
```yaml
  ios-driver-testflight:
    name: Driver iOS TestFlight (Phase B - Signed Build)
    instance_type: mac_mini_m2
    max_build_duration: 90
    environment:
      flutter: 3.41.6
      xcode: latest
      cocoapods: default
      ios_signing:
        distribution_type: app_store
        bundle_identifier: com.designated.driverapp.app
      groups:
        - app_store_credentials  # ASC API Key (Codemagic Teams → Integrations)
    integrations:
      app_store_connect: codemagic_app_store  # Integration 이름
    cache:
      cache_paths:
        - $HOME/Library/Caches/CocoaPods
        - $FLUTTER_ROOT/.pub-cache
    triggering:
      events: []
    scripts:
      - name: Flutter version check
        working_directory: driver_app_flutter
        script: flutter --version
      - name: Flutter pub get
        working_directory: driver_app_flutter
        script: flutter pub get
      - name: Set up code signing
        script: |
          keychain initialize
          app-store-connect fetch-signing-files "$(yq r ios/Runner.xcodeproj/project.pbxproj PRODUCT_BUNDLE_IDENTIFIER)" \
            --type IOS_APP_STORE \
            --create
          keychain add-certificates
          xcode-project use-profiles
      - name: Flutter unit test
        working_directory: driver_app_flutter
        script: flutter test test/fcm_token_payload_test.dart
      - name: CocoaPods install
        working_directory: driver_app_flutter/ios
        script: pod install --repo-update
      - name: Flutter build ipa
        working_directory: driver_app_flutter
        script: |
          flutter build ipa --release \
            --export-options-plist=/Users/builder/export_options.plist
    artifacts:
      - driver_app_flutter/build/ios/ipa/*.ipa
      - /tmp/xcodebuild_logs/*.log
    publishing:
      app_store_connect:
        auth: integration
        submit_to_testflight: true
        beta_groups:
          - Internal Testers
```

**사용자 측 Codemagic Teams 설정 필요**:
1. Codemagic Teams → Integrations → Developer Portal → ASC API Key(.p8 + Key ID + Issuer ID) 업로드 → 이름 `codemagic_app_store`
2. Environment variable group `app_store_credentials` 생성 (빈 값 OK, integration이 주입)

---

## 검증 전략 (Phase B 각 건별)

### B-1 검증 (Time Sensitive)
1. 변경 적용 → Codemagic 재빌드 → Runner.app.zip 다운로드
2. 아이폰 실기기 설치 (Xcode 또는 TestFlight) → 집중모드 ON → 테스트 콜 발송
3. 기대: 집중모드 통과 + 알림 배너 상단 표시
4. 실패 시: Apple Developer Portal 체크 + Xcode Capability 확인

### B-2 검증 (FullScreenIntent)
1. Flutter 기사앱만 단독 설치 (Kotlin 미설치 기기)
2. 화면 잠금 → 테스트 콜 발송
3. 기대: 잠금화면에서 전체 화면 콜 알림
4. Android 14+는 Settings → "다른 앱 위에 표시" 권한 수동 허용 필요

### B-5 검증 (LockScreen iOS 가드)
1. iOS 실기기에서 테스트 콜 → PlatformException 로그 0건 확인
2. Android 기기에서 테스트 콜 → 기존 LockScreenActivity 정상 표시

### Codemagic Phase B 검증
1. 워크플로우 신규 실행 → IPA 생성 확인
2. App Store Connect → TestFlight 탭 → 신규 빌드 "Processing" → "Ready to Test"
3. Internal Tester 초대 → 테스트 Apple ID로 TestFlight 앱에서 설치

---

## 배포 순서 (권장)

```
[1일차] B-5 (LockScreen iOS 가드) 적용 — 회귀 0, 단독 커밋
[1일차] B-1 (Time Sensitive) 적용 — Apple Developer Portal + Entitlements 병행
[1-2일차] Apple Developer Portal APNs Key 발급 → Firebase Console 등록
[2일차] ASC API Key 발급 → Codemagic Teams 등록
[2일차] codemagic.yaml Phase B 워크플로우 추가 커밋
[3일차] B-2 (FullScreenIntent) 적용 — Kotlin 기사앱 미설치 기기 확보 후
[3일차] 첫 TestFlight IPA 업로드 + Internal Tester 초대
[4일차~] 파일럿 1주 운영 + R1_LOCKSCREEN 발동 판단 (acceptanceEvents 집계)
```

---

## 폐기된 대안

- **Critical Alert**: Apple 엔터프라이즈 승인 필요 (일반 앱 거부). 시도 불가
- **CallKit integration**: VoIP 통합 필요, 현재 규모 과잉
- **Silent Push + 로컬 알림 직접 구성**: iOS 백그라운드 체인 복잡도 증가, Time Sensitive로 충분

---

## 관련 파일 경로 (착수 시 참조)

- `driver_app_flutter/lib/services/fcm_service.dart` B-1, B-2, B-3, B-4
- `driver_app_flutter/lib/services/lock_screen_service.dart` B-5
- `driver_app_flutter/ios/Runner/Info.plist` (변경 불필요 확인)
- `driver_app_flutter/ios/Runner/Runner.entitlements` (신규 생성)
- `driver_app_flutter/ios/Runner.xcodeproj/project.pbxproj` CODE_SIGN_ENTITLEMENTS 추가
- `codemagic.yaml` 신규 워크플로우 `ios-driver-testflight`
- Apple Developer Portal / App Store Connect (사용자 GUI 액션)
