# Platform Channels — LockScreen + Foreground Service

> **의제 7 (LockScreen Android 유지 / iOS Time Sensitive)** + **의제 8 (Foreground Service Android only / iOS no-op + Presence onDisconnect)** 결정 반영
> **원칙**: Flutter Android 빌드는 기존 Kotlin 네이티브 자산을 100% 보존. iOS는 plugin no-op 또는 명시적 stub 분기.
> **작성일**: 2026-04-16

---

## 1. LockScreen MethodChannel

### 1.1 현재 구현

**파일**: `driver_app_flutter/lib/services/lock_screen_service.dart`

```dart
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class LockScreenService {
  static const _channel = MethodChannel('com.designated.driverapp/lockscreen');

  static Future<void> showLockScreen({
    required String callId,
    String? customerName,
    String? destination,
    String? phoneNumber,
  }) async {
    try {
      await _channel.invokeMethod('showLockScreen', {
        'callId': callId,
        'customerName': customerName ?? '고객',
        'destination': destination ?? '',
        'phoneNumber': phoneNumber ?? '',
      });
    } on PlatformException catch (e) {
      debugPrint('[LockScreen] 실행 실패: $e');
    }
  }
}
```

### 1.2 의제 7 보강 — iOS 명시적 stub

**현재 문제**: iOS에서 MethodChannel 호출 시 `MissingPluginException` 또는 `PlatformException` 발생 → catch로 무시되나 **불필요 호출 비용 + 로그 노이즈**.

**개선안** (Phase 6 Week 1~2):

```dart
import 'dart:io' show Platform;
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class LockScreenService {
  static const _channel = MethodChannel('com.designated.driverapp/lockscreen');

  /// 잠금화면 콜 알림 표시 (Android only).
  /// iOS는 의제 7에 따라 Time Sensitive Notification으로 대체 — 본 함수는 no-op.
  static Future<void> showLockScreen({
    required String callId,
    String? customerName,
    String? destination,
    String? phoneNumber,
  }) async {
    // iOS는 Apple 정책상 잠금화면 풀스크린 UI 불가 (의제 7, FUNCTIONAL_INVENTORY 1.1 D 분류)
    if (!Platform.isAndroid) {
      debugPrint('[LockScreen] iOS는 stub. Time Sensitive Notification으로 대체됨');
      return;
    }
    try {
      await _channel.invokeMethod('showLockScreen', {
        'callId': callId,
        'customerName': customerName ?? '고객',
        'destination': destination ?? '',
        'phoneNumber': phoneNumber ?? '',
      });
    } on PlatformException catch (e) {
      debugPrint('[LockScreen] 실행 실패: $e');
    }
  }
}
```

### 1.3 Android Kotlin 측 (참고, 변경 없음)

**채널 등록**: `driver_app_flutter/android/app/src/main/kotlin/com/designated/driverapp/MainActivity.kt`

```kotlin
class MainActivity : FlutterActivity() {
    private val LOCKSCREEN_CHANNEL = "com.designated.driverapp/lockscreen"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, LOCKSCREEN_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "showLockScreen" -> {
                        val callId = call.argument<String>("callId") ?: ""
                        val customerName = call.argument<String>("customerName") ?: "고객"
                        val destination = call.argument<String>("destination") ?: ""
                        val phoneNumber = call.argument<String>("phoneNumber") ?: ""

                        val intent = Intent(this, LockScreenActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("callId", callId)
                            putExtra("customerName", customerName)
                            putExtra("destination", destination)
                            putExtra("phoneNumber", phoneNumber)
                        }
                        startActivity(intent)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }
}
```

**LockScreenActivity.kt** — Kotlin 원본 그대로 보존:
- `setShowWhenLocked(true)` + `setTurnScreenOn(true)` + `requestDismissKeyguard()`
- 3초 반복 알림음 (`MediaPlayer` + `lifecycleScope.launch`) — Kotlin `LockScreenActivity.kt:136-150`
- 무한 진동 (`VibrationEffect.createWaveform(pattern, 0)`) — line 181-189
- "확인" 버튼만 (CLAUDE.md 3/18 거절 버튼 제거 반영)

**Flutter Android 빌드 시 변경 없음**: 기존 Kotlin Activity가 그대로 동작.

### 1.4 iOS 대체 — Time Sensitive Notification

iOS는 본 MethodChannel을 호출하지 않고 **`flutter_local_notifications` Time Sensitive Notification + APNs `interruption-level: time-sensitive`** 로 대체.

상세 구현은 `flutter/driver_app/MVP/FCM.md` §3 참조.

### 1.5 R1 CallKit 도입 시 (Feature Flag 구조)

**의제 7 R1 발동 기준 충족 시** (수락률 5%p 하락 등), iOS에 한해 CallKit/VoIP push 도입. 코드 변경 최소화 위한 라우팅 구조:

**파일**: `lib/core/feature_flags.dart` (신규)

```dart
/// 의제 7 결정: R1 CallKit 심사 리젝 시 1줄 롤백 가능
class FeatureFlags {
  /// iOS CallKit 사용 여부 — 기본 false (Phase 1 MVP)
  /// R1 발동 + 심사 통과 후 true 전환
  static const bool useCallKitOnIos = false;

  /// 측정 인프라 활성화 (의제 11)
  static const bool enableAnalytics = true;
  static const bool enableCrashlytics = true;
}
```

**파일**: `lib/services/incoming_call_service.dart` (신규)

```dart
import 'dart:io' show Platform;
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import '../core/feature_flags.dart';
import 'lock_screen_service.dart';
import 'fcm_local_notification_service.dart';
// import 'callkit_service.dart'; // R1 도입 시 활성화

/// 배차 알림 라우팅 — 플랫폼·Feature Flag별 분기
class IncomingCallService {
  /// 새 배차 수신 시 적절한 알림 채널로 라우팅
  static Future<void> handleCallAssigned({
    required String callId,
    required String customerName,
    required String destination,
    required String phoneNumber,
  }) async {
    // Android: 기존 LockScreenActivity (Kotlin 네이티브)
    if (Platform.isAndroid) {
      return LockScreenService.showLockScreen(
        callId: callId,
        customerName: customerName,
        destination: destination,
        phoneNumber: phoneNumber,
      );
    }

    // iOS R1 (Feature Flag enable 시)
    if (Platform.isIOS && FeatureFlags.useCallKitOnIos) {
      // R1 도입 시:
      // return CallKitService.showIncomingCall(
      //   callId: callId, callerName: customerName, ...
      // );
    }

    // iOS 기본 (Phase 1 MVP) — Time Sensitive Notification
    return FcmLocalNotificationService.showTimeSensitive(
      callId: callId,
      title: '새로운 콜 배정',
      body: '$customerName · $destination',
    );
  }
}
```

**롤백 시나리오**: App Store가 CallKit 사용을 거부하면 `FeatureFlags.useCallKitOnIos = false` 1줄 변경 → 즉시 Time Sensitive로 복귀 → 핫픽스 재제출 (1~2일).

---

## 2. Foreground Service (`flutter_foreground_task`)

### 2.1 현재 구현 (변경 없음)

**파일**: `driver_app_flutter/lib/services/foreground_service.dart`

```dart
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter/foundation.dart';

class DriverForegroundService {
  static bool _initialized = false;

  static Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;

    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: 'driver_foreground_channel',
        channelName: '기사앱 서비스',
        channelDescription: '기사앱이 실행 중입니다',
        channelImportance: NotificationChannelImportance.LOW,
        priority: NotificationPriority.LOW,
        visibility: NotificationVisibility.VISIBILITY_SECRET,
      ),
      iosNotificationOptions: const IOSNotificationOptions(
        showNotification: false, // iOS는 no-op (의제 8)
      ),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.nothing(),
        autoRunOnBoot: true,
        autoRunOnMyPackageReplaced: true,
        allowWakeLock: true,
        allowWifiLock: true,
      ),
    );
  }

  static Future<void> start() async { /* ... */ }
  static Future<void> updateNotification(String text) async { /* ... */ }
  static Future<void> stop() async { /* ... */ }
}
```

### 2.2 iOS no-op 명시 (의제 8)

`flutter_foreground_task` 플러그인의 iOS 구현은 **사실상 no-op** (showNotification: false). 의제 8 결정에 따라 iOS Foreground Service 영구 활성은 **구조적 불가(D)**.

**문서화 보강** (코드 주석에 명시):

```dart
/// Driver Foreground Service.
///
/// **Android**: `flutter_foreground_task`로 영구 알림 + WAKE_LOCK 유지.
/// Kotlin 원본 `DriverForegroundService`와 동등 동작.
///
/// **iOS**: 플러그인이 사실상 no-op (의제 8 D 분류).
/// Apple 정책상 영구 백그라운드 프로세스 불가.
/// 대체: `presence_service.dart`의 onDisconnect + ScenePhase 관측으로 Presence 유지.
class DriverForegroundService { ... }
```

### 2.3 iOS 대체 — Presence onDisconnect (의제 8)

**파일**: `driver_app_flutter/lib/services/presence_service.dart` (이미 구현됨, 변경 없음)

- `presence/drivers/{uid}` 경로
- 상태 3종: `online` / `background` / `offline`
- `onDisconnect().set({status: 'offline'})` — 서버 측 자동 트리거
- `.info/connected` 리스너 + 재연결 시 onDisconnect 재등록 (필수 패턴)
- `AppLifecycleState` (Flutter 3+) 또는 `ScenePhase` (SwiftUI) 관측으로 foreground/background 전환

**iOS 특성**:
- Apple 배터리 정책으로 BACKGROUND → suspended 빠름
- WebSocket suspend 시 `onDisconnect` 트리거 **3~5분 지연** 가능 (의제 8 위험 신호)
- 그러나 CF `checkAssignedTimeout`이 `offline + 타임아웃`만 문제 취급 → BACKGROUND 오탐 허용

**파일럿 모니터링 항목** (의제 11 측정 인프라):
- iOS 기사 일 1명당 `offline` 판정 빈도 (3회 초과 시 의제 8 옵션 B/A 확장 검토)

---

## 3. AndroidManifest 권한 / Info.plist 설정

### 3.1 Android (변경 없음, 의제 6에서 iOS 무관 확정)

`driver_app_flutter/android/app/src/main/AndroidManifest.xml` (Kotlin 기사앱과 동일):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<application ...>
  <activity android:name=".LockScreenActivity"
            android:showOnLockScreen="true"
            android:turnScreenOn="true"
            android:excludeFromRecents="true"
            android:taskAffinity="" />

  <service android:name=".DriverForegroundService"
           android:foregroundServiceType="specialUse"
           android:exported="false">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
              android:value="기사 운행 상태 관리 및 콜 수신 서비스" />
  </service>

  <receiver android:name=".BootReceiver" android:exported="true">
    <intent-filter>
      <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
  </receiver>
</application>
```

### 3.2 iOS Info.plist (의제 6 결정)

**파일**: `driver_app_flutter/ios/Runner/Info.plist`

추가 필요:

```xml
<!-- 의제 6: Permission 사유 문자열 4종 -->
<key>NSLocationWhenInUseUsageDescription</key>
<string>운행 시작 시 현재 위치를 출발지로 자동 입력하기 위해 사용합니다. 주기적 위치 추적은 하지 않습니다.</string>
<key>NSMicrophoneUsageDescription</key>
<string>출발지·목적지·요금을 음성으로 입력할 때 사용합니다.</string>
<key>NSSpeechRecognitionUsageDescription</key>
<string>음성으로 말한 내용을 텍스트로 변환하여 주소·요금 입력을 돕습니다. 녹음은 저장되지 않습니다.</string>

<!-- 의제 6 추가 권장: 제거 대상 키 -->
<!-- <key>NSLocationAlwaysUsageDescription</key>  iOS 11+ deprecated, 제거 -->

<!-- 의제 4·7: FCM 백그라운드 + Time Sensitive -->
<key>UIBackgroundModes</key>
<array>
  <string>fetch</string>
  <string>remote-notification</string>
  <string>location</string>  <!-- 운행 시작 시 1회 위치 조회 -->
</array>
```

### 3.3 iOS Entitlements (의제 7)

**파일**: `driver_app_flutter/ios/Runner/Runner.entitlements` (신규 또는 추가)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <!-- 의제 7: Time Sensitive Notification -->
  <key>com.apple.developer.usernotifications.time-sensitive</key>
  <true/>

  <!-- APNs (의제 4) -->
  <key>aps-environment</key>
  <string>production</string>  <!-- 개발 시 development -->
</dict>
</plist>
```

**프로비저닝 프로파일 재발급 필수**: Apple Developer Console에서 **App ID Capabilities → Time Sensitive Notifications, Push Notifications 활성화** 후 새 프로파일 다운로드.

### 3.4 iOS Privacy Manifest (의제 6)

**파일**: `driver_app_flutter/ios/Runner/PrivacyInfo.xcprivacy` (신규)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>NSPrivacyCollectedDataTypes</key>
  <array>
    <dict>
      <key>NSPrivacyCollectedDataType</key>
      <string>NSPrivacyCollectedDataTypeUserID</string>
      <key>NSPrivacyCollectedDataTypeLinked</key>
      <true/>
      <key>NSPrivacyCollectedDataTypeTracking</key>
      <false/>
      <key>NSPrivacyCollectedDataTypePurposes</key>
      <array>
        <string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string>
      </array>
    </dict>
    <dict>
      <key>NSPrivacyCollectedDataType</key>
      <string>NSPrivacyCollectedDataTypeDeviceID</string>
      <key>NSPrivacyCollectedDataTypeLinked</key>
      <true/>
      <key>NSPrivacyCollectedDataTypeTracking</key>
      <false/>
      <key>NSPrivacyCollectedDataTypePurposes</key>
      <array>
        <string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string>
      </array>
    </dict>
    <dict>
      <key>NSPrivacyCollectedDataType</key>
      <string>NSPrivacyCollectedDataTypeCrashData</string>
      <key>NSPrivacyCollectedDataTypeLinked</key>
      <false/>
      <key>NSPrivacyCollectedDataTypeTracking</key>
      <false/>
      <key>NSPrivacyCollectedDataTypePurposes</key>
      <array>
        <string>NSPrivacyCollectedDataTypePurposeAnalytics</string>
      </array>
    </dict>
    <dict>
      <key>NSPrivacyCollectedDataType</key>
      <string>NSPrivacyCollectedDataTypeCoarseLocation</string>
      <key>NSPrivacyCollectedDataTypeLinked</key>
      <true/>
      <key>NSPrivacyCollectedDataTypeTracking</key>
      <false/>
      <key>NSPrivacyCollectedDataTypePurposes</key>
      <array>
        <string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string>
      </array>
    </dict>
  </array>

  <key>NSPrivacyAccessedAPITypes</key>
  <array>
    <dict>
      <key>NSPrivacyAccessedAPIType</key>
      <string>NSPrivacyAccessedAPICategoryUserDefaults</string>
      <key>NSPrivacyAccessedAPITypeReasons</key>
      <array>
        <string>CA92.1</string>  <!-- App functionality -->
      </array>
    </dict>
    <dict>
      <key>NSPrivacyAccessedAPIType</key>
      <string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
      <key>NSPrivacyAccessedAPITypeReasons</key>
      <array>
        <string>C617.1</string>  <!-- App functionality -->
      </array>
    </dict>
    <dict>
      <key>NSPrivacyAccessedAPIType</key>
      <string>NSPrivacyAccessedAPICategoryDiskSpace</string>
      <key>NSPrivacyAccessedAPITypeReasons</key>
      <array>
        <string>E174.1</string>  <!-- App functionality -->
      </array>
    </dict>
  </array>

  <key>NSPrivacyTracking</key>
  <false/>
</dict>
</plist>
```

**Firebase SDK 자체 manifest** (firebase_core/messaging/firestore 등 sub-package에 자동 포함). 본 manifest는 **앱 레벨**만 작성.

### 3.5 Podfile (의제 1 + 6)

**파일**: `driver_app_flutter/ios/Podfile`

수정 (line 2 + line 47):

```ruby
# Uncomment this line to define a global platform for your project
platform :ios, '15.0'   # ← 의제 1: '12.0' → '15.0'

# CocoaPods analytics sends network stats synchronously affecting flutter build latency.
ENV['COCOAPODS_DISABLE_STATS'] = 'true'

# ... (기존 내용 유지) ...

post_install do |installer|
  installer.pods_project.targets.each do |target|
    flutter_additional_ios_build_settings(target)

    # ← 의제 1: '15.0' deployment target 강제
    target.build_configurations.each do |config|
      config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '15.0'

      # ← 의제 6: permission_handler 매크로 — 사용 권한만 명시
      config.build_settings['GCC_PREPROCESSOR_DEFINITIONS'] ||= [
        '$(inherited)',
        'PERMISSION_LOCATION=1',
        'PERMISSION_MICROPHONE=1',
        'PERMISSION_SPEECH_RECOGNIZER=1',
        'PERMISSION_NOTIFICATIONS=1',
        # 사용 안 하는 권한은 명시 안 함 — 심사 시 사유 없는 권한 포함 방지
      ]
    end
  end
end
```

---

## 4. 신규 추가 파일 목록 (Phase 6 Week 1~2)

| 파일 | 신규/수정 | 설명 |
|------|----------|------|
| `lib/core/feature_flags.dart` | 신규 | CallKit Feature Flag (의제 7) |
| `lib/services/incoming_call_service.dart` | 신규 | 배차 알림 플랫폼 라우팅 (의제 7) |
| `lib/services/lock_screen_service.dart` | 수정 | iOS stub 명시적 분기 (의제 7) |
| `lib/services/foreground_service.dart` | 수정 | iOS no-op 주석 보강 (의제 8) |
| `lib/services/fcm_local_notification_service.dart` | 신규 | iOS Time Sensitive 발신 헬퍼 (의제 7) |
| `ios/Runner/Info.plist` | 수정 | Permission 사유 4종 + UIBackgroundModes (의제 6, 4) |
| `ios/Runner/Runner.entitlements` | 신규 | Time Sensitive + APNs Capability (의제 7, 4) |
| `ios/Runner/PrivacyInfo.xcprivacy` | 신규 | App 레벨 Privacy Manifest (의제 6) |
| `ios/Podfile` | 수정 | iOS 15.0 + permission_handler 매크로 (의제 1, 6) |

---

## 5. 테스트 시나리오

### Android 실기기
1. 앱 종료 상태 → FCM call_assigned 수신 → **LockScreenActivity 자동 기동** + 화면 점등 + 3초 반복 알림음 + 무한 진동
2. 앱 백그라운드 + 잠금 화면 → FCM 수신 → LockScreenActivity 풀스크린
3. 앱 포그라운드 → FCM 수신 → 앱 내 NewCallPopup 다이얼로그 (별도 핸들러)

### iOS 실기기 (Phase 1 MVP)
1. 앱 종료 상태 → FCM call_assigned 수신 → **Time Sensitive 배너** (Focus 우회) + 기본 사운드 1회
2. 앱 백그라운드 + 잠금 화면 → 동일 (배너만)
3. 앱 포그라운드 → 앱 내 NewCallPopup
4. **iOS LockScreen 풀스크린 UI 부재 확인** (Apple 정책)
5. **3초 반복 알림음 부재 확인** (Time Sensitive 한계)

### iOS R1 (CallKit 도입 후, 미래)
1. `FeatureFlags.useCallKitOnIos = true` 활성화
2. 앱 종료 상태 → VoIP push 수신 → **CallKit 풀스크린 통화 UI** + 시스템 링톤 반복
3. 심사 통과 시 R1 정식 배포
4. 심사 리젝 시 `useCallKitOnIos = false` 1줄 변경 + 핫픽스 재제출

---

## 6. 위험 신호 + 사전 완화

| 리스크 | 완화 |
|-------|------|
| iOS에서 LockScreenService MethodChannel 호출 시 PlatformException 무한 반복 | §1.2 명시적 stub 분기로 호출 자체 차단 |
| `flutter_foreground_task` iOS 동작 오인 (실제 no-op) | §2.2 코드 주석에 명시 + 의제 8 옵션 C 확정 |
| iOS Entitlement 누락 시 Time Sensitive Notification 일반 배너로 격하 | §3.3 Runner.entitlements + Apple Dev Console 활성화 절차 명시 |
| Privacy Manifest 누락 시 App Store 자동 리젝 (2024.5~) | §3.4 PrivacyInfo.xcprivacy 사전 작성 |
| Podfile 12.0 잔존 시 Firebase 10.x CocoaPods 설치 실패 | §3.5 라인 2 + 47 동시 상향 |
| permission_handler 매크로 누락 시 사용 안 하는 권한도 바이너리 포함 → 심사 질문 | §3.5 GCC_PREPROCESSOR_DEFINITIONS 사용 권한만 명시 |
| iOS BACKGROUND → suspended 빠름 → onDisconnect 3~5분 지연 | §2.3 파일럿 모니터링 + 의제 8 옵션 B/A 확장 트리거 |

---

## 참조

- `flutter/driver_app/MVP/OVERVIEW.md` — Phase 6 실행 사양
- `flutter/driver_app/MVP/FCM.md` — Time Sensitive Notification 상세 (의제 7 클라이언트 측)
- `flutter/driver_app/MVP/BUILD.md` — Codemagic + Podfile 빌드 사양
- `flutter/WORKING_DOC.md` §5 의제 7, 8 결정 전문
- `driver_app_flutter/lib/services/lock_screen_service.dart` — 현재 구현
- `driver_app_flutter/lib/services/foreground_service.dart` — 현재 구현
- `driver_app_flutter/lib/services/presence_service.dart` — Presence onDisconnect 1:1 포팅 완료 확인
- `driver_app/app/src/main/java/com/designated/driverapp/LockScreenActivity.kt` — Kotlin 원본 (변경 없음)
