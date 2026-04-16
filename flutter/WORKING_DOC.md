# Flutter 전환 — 팀 논의 작업 문서

> **목적**: kotlin-expert + flutter-expert 2인 팀이 Flutter 전환의 미결 사항을 의제별로 논의하여 매핑 문서 작성 기반 마련.
> **상태**: 의제 0/1 착수 대기
> **작성일**: 2026-04-16
> **마스터 플랜**: `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md`

---

## 1. 배경

- **iOS 출시 긴급, Mac 공수 1~2개월 지연** → Codemagic 기반 Mac 없이 출시 전략으로 Flutter 전환 (자세한 근거: `flutter/README.md` §결정 배경)
- **이전 Swift 계획(`ios/`)은 보류 자산**으로 보존
- **기사앱**: Flutter `driver_app_flutter/` Phase 1~5 기구현, iOS 빌드만 추가
- **손님앱**: Kotlin → Flutter 신규 포팅 (Phase 1) + Mac 도착 후 App Clip Swift 추가 (Phase 2)
- **CF/Firestore**: 공유, 영향 없음

---

## 2. 의제 분류 (3 트랙)

### 트랙 1 — 긴급 출시 블로커 의제 (Week 1~2 선결)
빌드/배포 진입 전 반드시 결정해야 코드 작성 가능.

| 순서 | 주제 | 영향 범위 | 결정 상태 |
|------|------|----------|----------|
| **1** | iOS 최소 버전 (iOS 15 권장, Podfile `IPHONEOS_DEPLOYMENT_TARGET = '12.0'` 즉시 상향) | Firebase Flutter SDK + 모든 플러그인 호환성 | ✅ **iOS 15.0 확정** |
| **2** | 기사앱 Bundle ID 전략 (유지+즉시교체 / 병행+듀얼토큰 / 새 ID 유지) | Apple Developer 앱 생성 직결, FCM 토큰 충돌 | ⏳ 미결 |
| **3** | 배포 파이프라인: Codemagic 설정 (codemagic.yaml, 인증서 자동 관리, TestFlight 업로드) | Mac 없이 출시의 핵심 | ⏳ 미결 |
| **4** | CF `oncallassigned` payload에 `apns` 블록 추가 (iOS data-only 수신 안정화) | 서버측 수정, 빌드 전 완료 | ✅ **승인, data 블록 보존** |
| **5** | FCM 토큰 필드명 + 플랫폼 라벨링 (`fcmToken` Kotlin/Flutter 대소문자 일치 확인) | 병행 배포 시 토큰 충돌 | ✅ **단일 `fcmToken` + `fcmTokenPlatform` 메타** |
| **6** | Privacy Manifest + Permission 사유 문자열 + 데모 계정 준비 | 심사 통과 필수 | ✅ **4종/3종 사유 문자열 + Privacy Manifest 수동 + Podfile 매크로** |

### 트랙 2 — 구현 설계 의제 (Week 2~4 병렬 논의)
출시 전 결정되어야 하나 병렬 논의 가능.

| 순서 | 주제 | 영향 범위 | 결정 상태 |
|------|------|----------|----------|
| 7 | iOS LockScreen 대체 (B: Time Sensitive / R1: CallKit 심사 실측) | 기사앱 배차 UX | ✅ **Time Sensitive MVP + Feature Flag 롤백 구조** |
| 8 | iOS Foreground Service 대체 (onDisconnect + APNs silent + BGAppRefreshTask 3단) | Presence | ✅ **옵션 C, 기존 PresenceService.dart 1:1 포팅 이미 완료** |
| 9 | 자동로그인 credential 이관 (MethodChannel 마이그레이션 vs 재로그인 강제 UX) | 전환 시 기사 재로그인 리스크 | ✅ **재로그인 + 이메일 사전 채움 + Firebase Auth 자동 상속 기대** |
| 10 | 손님앱 전환 전략 (즉시 교체 vs forceUpdate vs 점진적) | 배포된 Kotlin 사용자 | ✅ **같은 applicationId + Staged Rollout + Remote Config 최소 버전** |
| 11 | 측정 인프라 (수락률 로깅, R1 발동 기준 평가 가능화) | iOS 출시 후 R1 판단 | ✅ **CF acceptanceEvents + Analytics/Crashlytics 즉시 도입** |
| 12 | 로컬 저장소 (sqflite 신규 도입 vs shared_preferences 유지) | 정산 캐시 | ✅ **MVP shared_preferences + Firestore 오프라인, sqflite는 R1** |

### 트랙 3 — Phase 2 + 잔여 (Mac 도착 시점 논의)

| 순서 | 주제 | 영향 범위 | 결정 상태 |
|------|------|----------|----------|
| 13 | 손님앱 Phase 2 App Clip Swift 타겟 설계 | 귀속 완성 | ⏳ Mac 대기 |
| 14 | 잔여 (Dart 3 patterns, Riverpod 2.x 고정, FVM, App Check, split-per-abi) | 기반 정책 | ⏳ 미결 |

---

## 3. 논의 진행 규칙

1. **중재자(메인 Claude)가 의제를 제시** → kotlin-expert + flutter-expert 동시 답변 요청
2. **각 팀원은 본인 컨텍스트 파일 + 본 문서 + 관련 소스 읽고 답변 형식 엄수**
3. **답변 후 idle**, 직접 반론 금지, 중재자 경유
4. **중재자가 양측 답변 종합** → 결론 + 근거 + 트레이드오프를 §5에 누적
5. 의제 1~6은 가급적 **5건 이상 동시 진행** (병렬화)

### 답변 형식 (양측 공통)
```
## [결론] — 한 문장
## [근거] — Kotlin 라인 또는 Flutter 패키지 + 본 프로젝트 제약
## [대상 측에 요청할 검증 사항] — 1~2개
## [트레이드오프] — 2~3개
## [위험 신호]
## [참고] — flutter-expert만 작성: pub.dev URL 또는 패키지명
```

---

## 4. 우선순위 결정 흐름

```
플랜 승인 (완료)
    ↓
의제 1 (iOS 최소 버전) — 모든 플러그인·CallKit 결정의 선결
    ↓
의제 2/3/4/5/6 병렬 논의 (긴급 출시 블로커)
    ↓
Codemagic 첫 빌드 성공 + 의제 7~12 병렬 (구현 설계)
    ↓
TestFlight Internal 배포 + 의제 11 측정 인프라 활성화
    ↓
External 베타 → App Store 심사 → Release
    ↓
Mac 도착 → 의제 13 App Clip Phase 2
```

---

## 5. 논의 결과 (누적)

> 각 의제 결정 도달 시 이 섹션에 **결론 + 근거 + 반대 의견·트레이드오프 + 결정일** 기록.

### 의제 1: iOS 최소 버전 — **iOS 15.0 확정** (2026-04-16)

**결정**: Flutter 기사앱·손님앱 iOS deployment target = **iOS 15.0**

**근거** (양측 합의):
- **kotlin-expert**: Kotlin `LockScreenActivity.kt:96` `setShowWhenLocked(true)` API 27(Android 8.1+) 기반 → iOS 등가는 Time Sensitive Notification(iOS 15+) 필수. iOS 14는 Focus/DND 우회 수단 없어 Kotlin UX 최소 등가조차 불가.
- **flutter-expert**: `Podfile:2, 47` `IPHONEOS_DEPLOYMENT_TARGET = '12.0'` 현재값이 Firebase Flutter SDK(iOS 13+ 강제)와 빌드 충돌. Time Sensitive Notification이 의제 5 LockScreen 대체 MVP(B) 카드.
- **커버리지**: iOS 15 ≈ 97% (한국, 2026.04 추정). iOS 16(Live Activity)/17(push-to-start)은 Phase 1 R1/R2 대상이므로 MVP에 과잉.

**즉시 조치**: `driver_app_flutter/ios/Podfile` 두 곳(라인 2, 47)을 `'15.0'`으로 상향.

**트레이드오프 기록**:
- iOS 14: 커버리지 99% but Time Sensitive 없음 → 반대
- iOS 15 (선택): 97%, Time Sensitive 확보, 최소 등가
- iOS 16: 92%, Live Activity R1 선점 이점. Phase 1 대상 아님
- iOS 17: 85%, push-to-start. 과잉

**위험 신호**: R1 도입 시 Live Activity 위해 iOS 16 재상향 가능성 (재논의 리스크). Phase 1 출시 후 R1 발동 기준 평가 시 "iOS 15 유지 + VoIP+CallKit" vs "iOS 16 상향 + Live Activity" 재결정 필요.

### 의제 2: 기사앱 Bundle ID 전략
- **상태**: 미결
- **선택지**:
  - (A) Bundle ID `com.designated.driverapp` 유지 + 즉시 교체 (Play Console forceUpdate)
  - (B) `com.designated.driverapp.app` 새 ID + 병행 배포 + 듀얼 토큰 필드(`fcmToken_android`, `fcmToken_ios`)
  - (C) 새 ID 유지 + 즉시 교체 (Play Store 별개 앱으로 보임)
- **선결 조건** (B 선택 시): CF `oncallassigned` 전체 수정 + 마이그레이션 스크립트 + 기사 문서 스키마 변경

### 의제 3: 배포 파이프라인 Codemagic 설정
- **상태**: 미결
- **결정 항목**: codemagic.yaml workflow 구조, 인증서 관리(App Store Connect API Key vs fastlane match), TestFlight 자동 업로드 트리거, 무료 티어(월 500분) 한계 점검

### 의제 4: CF `oncallassigned` payload `apns` 블록 — **승인, data 블록 보존 원칙** (2026-04-16)

**결정**: `functions/src/index.ts:560-573`에 `apns` 블록 추가. **단, 기존 `data` 블록의 `title`/`body`/`callId`/`notificationId`/`type` 필드는 그대로 유지**. 최상위 `notification` 블록 추가 금지.

**근거** (양측 합의 + 충돌 해소):
- **Admin SDK 자동 분기 확인**: Firebase Admin SDK가 토큰 플랫폼 판별 후 해당 블록만 적용 → `apns` + `android` 동시 지정 시 양쪽 독립 작동 (flutter-expert 검증)
- **Kotlin 측 회귀 없음**: `MyFirebaseMessagingService.kt:134-146`이 `data` 블록만 파싱. `apns` 블록은 Android에 전달 안 됨 (kotlin-expert 확인)
- **iOS 측 효과 확정**: `content-available:1` + `alert` + `interruption-level: "time-sensitive"` 3종 동시 지정 시 iOS 15+에서 **alert 배너 + 앱 백그라운드 실행 + Focus 우회** 3가지 모두 활성 (flutter-expert)

**⚠️ 충돌 해소 주의사항**:
- flutter-expert 제안 중 "data에서 title/body 제거하고 **최상위 notification 블록으로 이동**"은 **채택하지 않음**
- 이유: kotlin-expert 경고대로 최상위 `notification` 블록이 존재하면 Android Kotlin 클라이언트에서 **시스템 자동 알림 + FullScreenIntent 커스텀 알림 이중 표시** 회귀 발생
- **원칙: `data` 블록 100% 보존 + `apns.payload.aps.alert`만 iOS에 추가** (중복 표시 방지)

**권장 payload 형태**:
```typescript
const driverPayload = {
  data: {
    callId, notificationId, type: "call_assigned",
    title: "새로운 콜 배정",
    body: "새로운 콜이 배정되었습니다. 즉시 확인해주세요!"
  },
  android: { priority: "high" as const, ttl: 30000 },
  apns: {
    headers: {
      "apns-push-type": "alert",
      "apns-priority": "10",
      "apns-expiration": String(Math.floor(Date.now()/1000) + 30),
    },
    payload: {
      aps: {
        alert: { title: "새로운 콜 배정", body: "..." },
        sound: "default",
        "content-available": 1,
        "mutable-content": 1,
        "interruption-level": "time-sensitive",
      },
    },
  },
  token: driverFcmToken,
};
```

**후속 작업 (별도 과제)**:
- CF 41개 함수 중 `admin.messaging().send` 호출하는 **28곳 이상** 전체에 동일 패턴 적용 필요 (kotlin-expert 지적)
- Firebase Emulator 또는 Staging 프로젝트에서 Android/iOS 양 플랫폼 실측 필수

**위험 신호**:
- Admin SDK 자동 분기 가정이 **실측 전에는 확정 아님**. 배포 전 반드시 Emulator 테스트
- `apns-priority: 10`은 alert 있을 때만 허용. silent push(content-available only)는 `5` 필수 — 실수 시 Apple rate-limit

### 의제 5: FCM 토큰 필드명 + 플랫폼 라벨링 — **단일 `fcmToken` 유지 + `fcmTokenPlatform` 메타 필드 추가** (2026-04-16)

**결정**: 필드명 `fcmToken` (camelCase) 3개 앱·CF 모두 이미 일치. 듀얼 필드 도입 배제. **플랫폼 메타 필드 1개만 추가**.

**검증 결과** (양측 코드 직접 Grep):

| 위치 | 라인 | 필드명 |
|------|------|--------|
| Kotlin 기사앱 | `driver_app/.../Constants.kt:26` — `const val FIELD_FCM_TOKEN = "fcmToken"` | `fcmToken` |
| Kotlin 기사앱 저장 | `MyFirebaseMessagingService.kt:55, 96` — `driverRef.update(Constants.FIELD_FCM_TOKEN, token)` | `fcmToken` |
| Kotlin 손님앱 (5곳 하드코딩) | `MainActivity.kt:315,592`, `service/MyFirebaseMessagingService.kt:404`, `ProfileSetupViewModel.kt:154,181` | `"fcmToken"` |
| **Flutter 기사앱** | `driver_app_flutter/lib/core/constants/app_constants.dart:26` + `lib/services/fcm_service.dart:100` | `'fcmToken'` |
| CF 소비 | `functions/src/index.ts:549, 612, 816, 935, 1195` — `driverData?.fcmToken` | `fcmToken` |

**→ 3개 앱 + CF 모두 `fcmToken` 완전 일치. 필드명 변경 불필요**.

**이전 우려 해소**:
- 1라운드 kotlin-expert가 제기한 "Flutter가 `regions/` 오인 경로 저장 중" = **사실 오류 재확인**. flutter-expert가 `fcm_service.dart:95-98` 직접 확인 완료.
- `memory/plan_flutter_driver_app.md:78-80`의 "수정 대상" 리스트를 "현재 상태"로 오독한 사례. 팀 규칙 "주장 근거는 코드 라인 우선, 메모리 문서는 보조"에 반영됨.

**플랫폼 라벨링 방식** (kotlin-expert 권장 + flutter-expert 동의):
- **듀얼 필드(`fcmToken_android`/`fcmToken_ios`) 반대**: CF 50+ 라인 수정 + 손님앱 5곳 하드코딩 교체 + 마이그레이션 스크립트 + 보안 규칙 수정 = 1주 작업량, 실익 낮음
- **단일 `fcmToken` + 신규 메타 필드 `fcmTokenPlatform: "android" | "ios"`** 추가
- CF 수정 **불필요** (Admin SDK가 `apns`+`android` 자동 분기, 의제 4 근거)
- 메타 필드는 운영 통계·디버깅 용도
- Flutter 구현: `Platform.isIOS ? 'ios' : 'android'` 1줄 추가

**구현 영역**:
1. Flutter 기사앱 `fcm_service.dart` 토큰 저장 시 `'fcmTokenPlatform': Platform.isIOS ? 'ios' : 'android'` 추가
2. Kotlin 기사앱 `MyFirebaseMessagingService.kt:55` 토큰 저장 시 `"fcmTokenPlatform" to "android"` 추가
3. 손님앱 동일 패턴 (5곳 수정 대신 상수 도입 권고)

**위험 신호**:
- 메타 필드 저장 **깜빡하면** 추후 플랫폼별 payload 최적화 시 재작업
- 의제 2 전환 전략이 "즉시 교체" 채택 시 이 메타 필드 도입은 완전 선택사항. "병행 배포" 시 필수 (토큰 충돌 판별용)

**후속 연관 의제**:
- 의제 2 결정 후 메타 필드 필수/선택 재확인
- 의제 4의 Admin SDK 자동 분기 가정이 실측 실패 시 본 결정 재논의 필요

### 의제 6: Privacy Manifest + Permission + 데모 계정
- **상태**: 미결
- **결정 항목**: `PrivacyInfo.xcprivacy` 자동 생성 여부 (Firebase Flutter SDK 11+ 자동 제공), Info.plist 권한 사유 문자열 12종 확정, App Store Connect Review Notes 데모 계정 준비

### 의제 6: Privacy Manifest + Permission + 데모 계정 — **확정** (2026-04-16)

**결정**: 기사앱 사유 문자열 4종 + 손님앱 3종 + 앱 레벨 Privacy Manifest 수동 작성 + Podfile 매크로 설정.

**근거** (양측 합의):
- **kotlin-expert**: `driver_app/AndroidManifest.xml:6-25` 17개 + `customer_app/AndroidManifest.xml:6-22` 5개 전수. iOS 실제 필요는 Location/Microphone/Speech/Camera 한정.
- **flutter-expert**: Firebase SDK 10.22+/11.x는 sub-package manifest 자동 제공. 앱 레벨은 `shared_preferences`(UserDefaults `CA92.1`), `path_provider`(FileTimestamp `C617.1`) Required Reason API 명시 필수.

**Info.plist 사유 문자열 (한국어, 심사 수준)**:

**기사앱**:
```
NSLocationWhenInUseUsageDescription: "운행 시작 시 현재 위치를 출발지로 자동 입력하기 위해 사용합니다. 주기적 위치 추적은 하지 않습니다."
NSMicrophoneUsageDescription: "출발지·목적지·요금을 음성으로 입력할 때 사용합니다."
NSSpeechRecognitionUsageDescription: "음성으로 말한 내용을 텍스트로 변환하여 주소·요금 입력을 돕습니다. 녹음은 저장되지 않습니다."
```

**손님앱**:
```
NSCameraUsageDescription: "사무실 QR 코드를 스캔하여 연결된 사무실 정보를 불러오는 데 사용합니다."
NSLocationWhenInUseUsageDescription: "현재 계신 위치를 출발지로 자동 입력하기 위해 사용합니다."
NSMicrophoneUsageDescription / NSSpeechRecognitionUsageDescription: 동일 패턴
```

**iOS에서 명시 제외 (Android 전용, 8종)**: FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, USE_FULL_SCREEN_INTENT, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

**추가 수정 사항 (flutter-expert 실측 발견)**:
- `driver_app_flutter/ios/Runner/Info.plist`: `NSLocationAlwaysUsageDescription` 제거 (iOS 11+ deprecated, `WhenInUseUsageDescription`만 유지)
- `driver_app_flutter/ios/Podfile` `post_install`에 `permission_handler` 매크로 설정 필수:
  ```ruby
  target.build_configurations.each do |config|
    config.build_settings['GCC_PREPROCESSOR_DEFINITIONS'] ||= [
      '$(inherited)',
      'PERMISSION_LOCATION=1',
      'PERMISSION_MICROPHONE=1',
      'PERMISSION_SPEECH_RECOGNIZER=1',
      'PERMISSION_CAMERA=1',  # 손님앱만
      'PERMISSION_NOTIFICATIONS=1',
    ]
  end
  ```
- `Runner/PrivacyInfo.xcprivacy` **신규 작성** (앱 레벨):
  - `NSPrivacyCollectedDataTypes`: User ID, Phone Number(손님), Device ID, Crash Data, Coarse Location
  - `NSPrivacyAccessedAPITypes`: `UserDefaults`(CA92.1), `FileTimestamp`(C617.1), `DiskSpace`(C617.1)

**데모 계정 준비**:
- Firebase Auth 정적 테스트 이메일 + 테스트 사무실 Firestore 문서
- CF `checkAssignedTimeout` 화이트리스트에 테스트 계정 제외 (심사 중 자동 타임아웃 방지)
- App Review Notes에 상세 시나리오 안내 (로그인 → 배차 수신 → 운행 → 정산 완결)

**위험 신호**:
- Podfile 매크로 누락 시 `permission_handler` 코드가 모든 권한을 번들 → 심사 시 **사유 없는 권한 포함**으로 질문 받음
- Privacy Manifest에 `Crashlytics` (기사앱 `driver_app/build.gradle:125`)가 수집하는 Crash Data 선언 누락 시 리젝

---

### 의제 7: iOS LockScreen 대체 — **Time Sensitive MVP 확정, R1 CallKit 보류 + Feature Flag** (2026-04-16)

**결정**: Phase 1 MVP는 flutter_local_notifications Time Sensitive Notification. CallKit은 R1 후보 + **심사 리젝 시 즉시 롤백 가능한 Feature Flag 구조**로 사전 설계.

**근거** (양측 합의):
- **kotlin-expert**: `LockScreenActivity.kt` 3요소 중 **"DND 우회"만 Time Sensitive로 보존**. "반복 사운드(line 136-150)·무한 진동(line 181-189)·FullScreenIntent"는 iOS MVP에서 유실 확정. Android 반복음이 수락률에 기여했다는 **정량 데이터 부재** — 5%p 발동 기준 측정 자체가 측정 인프라 선결(의제 11) 의존.
- **flutter-expert**: 현재 Flutter 코드에 `interruptionLevel` 설정 **0건** (`fcm_service.dart:157-160` 실측). Phase 6 착수 시 반드시 추가.

**구현 사양**:

**Phase 1 MVP — Time Sensitive Notification**:
```dart
// fcm_service.dart:157-160 수정
final DarwinNotificationDetails iOSDetails = DarwinNotificationDetails(
  presentAlert: true, presentSound: true, presentBanner: true,
  interruptionLevel: type == typeCallAssigned
      ? InterruptionLevel.timeSensitive
      : InterruptionLevel.active,
);
```

+ iOS Entitlement 추가: `com.apple.developer.usernotifications.time-sensitive` (프로비저닝 프로파일)
+ 서버 측은 의제 4 결정 `apns.payload.aps.interruption-level: "time-sensitive"` 이미 반영

**R1 CallKit 보류 + Feature Flag 사전 설계** (flutter-expert 권장):
```dart
// lib/core/feature_flags.dart (신규)
class FeatureFlags {
  static const bool useCallKitOnIos = false;  // 심사 리젝 시 1줄 롤백
}

// lib/services/incoming_call_service.dart (신규, 플랫폼 분기 라우터)
class IncomingCallService {
  Future<void> handleCallAssigned(CallData data) async {
    if (Platform.isAndroid) {
      return LockScreenService.showLockScreen(data);  // 기존 MethodChannel
    }
    if (Platform.isIOS && FeatureFlags.useCallKitOnIos) {
      return CallKitService.showIncoming(data);  // R1 도입 시
    }
    return FcmLocalNotification.showTimeSensitive(data);  // Phase 1 기본
  }
}
```

**Android MethodChannel 안전장치** (flutter-expert 발견):
- `lock_screen_service.dart` 시작 부분에 `if (!Platform.isAndroid) return;` 명시 추가 (iOS에서 MethodChannel 호출 자체 차단)

**유실되는 Kotlin 계약 (사용자 인지 필수)**:
| Kotlin 기능 | iOS MVP 대체 | 유실 정도 |
|---|---|---|
| DND 우회 | Time Sensitive interruptionLevel | 완전 보존 |
| 주머니 속 폰 (화면 자동 점등 + 반복) | 1회 배너 + 기본 사운드 1회 | **대부분 유실** |
| 음식점 소음 (반복 사운드) | 1회 재생 | **완전 유실** |

**R1 CallKit 심사 리스크** (Kotlin 판단 불가, flutter-expert 경고):
- Apple Review Guideline 2.1 + 4.5.4: CallKit은 VoIP 통신 한정
- Uber Driver도 과거 CallKit 중단 이력
- 리젝 시 **Feature Flag 1줄 변경 + 재제출 1~2주** 롤백 가능 구조로 대비

**R1 발동 선결 조건**:
- **의제 11 (측정 인프라) 선결 필수** — 수락률 5%p 비교 가능한 로깅 없으면 R1 발동 판정 자체 불가
- Kotlin 저장소에 수락률 집계 **부재 확인** (`functions/src/index.ts` grep, `TRIGGERS.md:12` 자체 인정)

**위험 신호**:
- `interruptionLevel` entitlement 누락 시 Time Sensitive가 **일반 배너로 격하** → DND 우회 실패
- 측정 인프라 없이 MVP 출시하면 R1 발동 판단이 감각에 의존 → 영원히 발동 안 되거나 과잉 발동

---

### 의제 8: iOS Foreground Service 대체 — **옵션 C(Kotlin 동급) 시작, onDisconnect 중심 + BACKGROUND 허용** (2026-04-16)

**결정**: Kotlin PresenceManager 계약을 iOS에서 동급 수준으로만 먼저 구현. APNs silent push + BGAppRefreshTask 신규 보호막은 R1 후보로 보류. 파일럿 실측 후 옵션 B/A 단계 확장.

**근거** (양측 합의):
- **kotlin-expert**: Kotlin 3상태(ONLINE/BACKGROUND/OFFLINE) 중 CF `checkAssignedTimeout`이 **"offline + 타임아웃"만 문제 취급** (`functions/src/index.ts:3512`). BACKGROUND는 ONLINE과 동일하게 "문제 없음" → iOS에서 BACKGROUND 오탐 허용 가능.
- **flutter-expert 중대 발견**: **`driver_app_flutter/lib/services/presence_service.dart` 이미 Kotlin 1:1 포팅 완료 상태** (경로 `presence/drivers/{uid}` ✓, 상태 3종 ✓, `onDisconnect().set({status: 'offline'})` ✓, `.info/connected` 리스너 + 재연결 재등록 ✓, `onAppForeground/Background/Logout/cleanup` 훅 ✓).

**즉 Phase 6에서 Presence 관련 추가 개발 대부분 불필요** — 기존 Flutter 코드 그대로 iOS에서 동작.

**대체 평가**:

| Kotlin 계약 | Flutter 현재 상태 | iOS 실제 동작 |
|---|---|---|
| OFFLINE 자동 (`onDisconnect`) | ✅ 구현됨 | **100% 동등** (Firebase SDK cross-platform) |
| ONLINE 포그라운드 복귀 | ✅ 구현됨 | **100% 동등** (AppLifecycleState.resumed) |
| BACKGROUND 진입 | ✅ 구현됨 | **70% 동등** (iOS suspended 빠름, 단명) — CF가 무시하므로 영향 없음 |

**iOS 특유 리스크 (flutter-expert 발견)**:
- iOS BACKGROUND → suspended → WebSocket 연결 suspend → **`onDisconnect` 트리거 3~5분 지연** 가능성
- 기사가 iOS 앱 백그라운드 후 5분간 OFFLINE 판정 안 될 수 있음 — CF 타임아웃 3분 대비 **iOS에선 "ASSIGNED + 살아있음 오인" 가능**
- CLAUDE.md 3/18 "CF #1 offline 즉시 복귀 → 타임아웃 적용"이 이 리스크를 이미 완화 중

**APNs silent push / BGAppRefreshTask — Phase 1 배제**:
- **Apple throttle**: 앱당 시간당 3회 (flutter-expert). 배차 alert와 quota 경합 가능성
- `workmanager` iOS: 하루 0~3회 실행 보고 사례, 보장 없음
- 신규 보호막 도입 이득이 **Kotlin에 없던 초과 보호**라 Phase 1 비용 대비 효과 낮음

**권장 단계 확장 계획**:
- **Phase 1**: 옵션 C (onDisconnect + ScenePhase 관측, **현재 Flutter 구현 그대로**)
- **파일럿 중 offline 오판 발생 시 옵션 B**: CF `checkAssignedTimeout` 주기 1분 → 30초 단축 + `lastSeen` 기반 2분 미갱신 → offline 판정 추가
- **그래도 부족 시 옵션 A**: APNs silent push + BGAppRefreshTask 도입 (R1)

**추가 수정 (flutter-expert 발견)**:
- `driver_app_flutter/lib/services/foreground_service.dart:23-25` `showNotification: false`는 올바른 설정 (iOS 완전 no-op 재확정)
- Android-only로 유지, iOS는 no-op 보장

**위험 신호**:
- iOS suspended 시 `onDisconnect` 재등록 타이밍 주의 — PresenceManager.kt:64-76 패턴대로 **재연결 시마다 재등록** 반드시 보존 (`presence_service.dart` 이미 반영됨 확인)
- 파일럿 중 iOS 기사에서 "배차 받았는데 offline로 복귀" 이상 현상 발생 시 의제 8 재논의 진입

---

### 의제 9: 자동로그인 credential 이관 — **재로그인 강제 + UX 보강** (2026-04-16)

**결정**: Kotlin EncryptedSharedPreferences → Flutter flutter_secure_storage 직접 이관 배제. **Firebase Auth currentUser 자동 상속 기대 + 실패 시 재로그인** 원칙. 재로그인 UX는 이메일 사전 채움 + 비밀번호 재설정 경로 다이얼로그로 최적화.

**근거** (양측 수렴):
- **kotlin-expert 실측**: Kotlin `SecurePreferencesManager.kt` 파일명 `"secure_prefs"`, 키 3종(`auto_login`/`identifier`/`password`), AES256-GCM + MasterKey `_androidx_security_master_key_`
- **flutter-expert 실측**: Flutter `auth_local_datasource.dart` 키 3종(`saved_email`/`saved_password`/`auto_login_enabled`) — **파일명·키 이름·암호화 방식 모두 다름** → flutter_secure_storage가 Kotlin 파일 직접 읽기 불가
- **공통 결론**: 기사 200명 규모 + MethodChannel 마이그레이션 90~95% 성공률이나 개발 2일 + 테스트 부담

**구현 사양**:

1. **Flutter 앱 첫 실행 시 Firebase Auth 세션 자동 상속 시도**:
   ```dart
   final user = FirebaseAuth.instance.currentUser;
   if (user != null) {
     // 세션 유효 → collectionGroup 쿼리로 기사 문서 재조회 → 홈 진입
   } else {
     // 세션 무효 → 로그인 화면 (이메일 사전 채움)
   }
   ```
   - 같은 applicationId (`com.designated.driverapp.app`)이면 Firebase Auth SDK 내부 토큰이 `shared_prefs/com.google.firebase.auth.api.Store.{projectId}.xml`에 잔존 → 자동 상속 기대

2. **재로그인 UX 보강**:
   - 마지막 이메일 `shared_preferences` 1건 저장 (MethodChannel 경유 Kotlin 측 identifier 1회 복사 선택사항)
   - 로그인 화면 진입 시 이메일 필드 자동 채움
   - **"비밀번호 찾기" 버튼 상단 노출** (기사 연령대 고려, flutter-expert 지적)

3. **iOS Keychain 설정 필수** (flutter-expert):
   ```dart
   const FlutterSecureStorage(
     iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock_this_device),
   )
   ```
   - 기본 `unlocked`는 **잠금화면 FCM 수신 시 토큰 조회 불가** — 배차 FCM 처리 영향

4. **손님앱**: Anonymous Auth는 Firebase Auth SDK가 자동 관리 → credential 이관 **이슈 없음**. 의제 9는 **기사앱 전용**

**위험 신호**:
- Firebase Auth currentUser 자동 상속은 **Kotlin `firebase-auth-ktx` (bom 33.x) vs Flutter `firebase_auth ^5.3.1` SDK 간 호환성 실측 필요**. 실패 시 전원 재로그인
- `SecurePreferencesManager.kt:39-44` 평문 password 저장 패턴을 **Flutter에서 재현하지 말 것**. flutter_secure_storage는 그대로 보관해도 HW-backed 암호화이나 **Phase 1부터 비밀번호 미저장 + Firebase Auth 세션 의존**이 권장

---

### 의제 10: 손님앱 전환 전략 — **같은 applicationId + Staged Rollout** (2026-04-16)

**결정**: Kotlin 손님앱 `com.designated.customer.app` 패키지명 유지 + Flutter 버전으로 Google Play 자동 업데이트. Staged Rollout(1%→5%→25%→100%) 적용. iOS는 신규 출시 별도 타이밍.

**근거** (양측 수렴):
- **kotlin-expert**: CLAUDE.md "미배포" 기록이나 `versionCode = 12` 제출 이력 가능성 존재 → 사용자 확인 필요. Anonymous Auth uid 자동 상속 기대
- **flutter-expert**: 같은 applicationId로 Play Store 업데이트 경로 성립. `in_app_update: ^4.2.3` + `firebase_remote_config: ^5.1.0` 최소 버전 강제 구조 권장

**구현 사양**:

1. **Android — 같은 applicationId 유지**:
   - `com.designated.customer.app` 그대로 Flutter 앱 사용
   - `versionCode`만 증가 (Kotlin 12 → Flutter 13 이상)
   - SharedPreferences 평문 데이터(Install Referrer 10+ 필드) 자동 보존
   - Firebase Auth Anonymous uid 자동 상속 기대 (실측 필요)

2. **Staged Rollout (Google Play Console)**:
   - Internal Testing 1주 (테스터 5~10명)
   - Closed Testing / Beta 2주 (외부 기사 사용자)
   - Open Testing 1주 + Production 5% → 25% → 100% (각 3일)

3. **iOS — 독립 신규 출시**:
   - 기존 Kotlin iOS 앱 없음 → Flutter 버전이 첫 출시
   - Android Staged Rollout 안정화 후 iOS 착수 가능

4. **Remote Config 최소 버전 강제**:
   - `firebase_remote_config` 도입
   - Remote Config 키: `min_supported_version_android`, `min_supported_version_ios`
   - 앱 시작 시 버전 체크 → 미달 시 강제 업그레이드 화면
   - (Apple 공식 forceUpdate API 없음을 커뮤니티 패턴으로 보완)

5. **Firestore 데이터 호환성** (기존 문서 그대로):
   - `customers/{uid}`, `customerInfo/{phone}`, `customerPoints/{phone}` 경로 보존
   - `customerInfo/{phone}.fcmToken`은 Flutter 첫 실행 시 재발급 (의제 4·5 정합)

**필수 패키지 추가**:
- `in_app_update: ^4.2.3` (Android In-App Updates)
- `firebase_remote_config: ^5.1.0` (양 플랫폼 최소 버전 강제)

**위험 신호**:
- **Kotlin 손님앱 Play Store 실제 배포 상태를 사용자(사업주)가 명확히 확인** 필요. CLAUDE.md "미배포"는 명세 기록, versionCode 12 제출 이력은 실무 정황
- Anonymous Auth uid 자동 상속 실패 시 **기존 손님의 `customers/{uid}`/포인트/등급 전부 고아** → 심각한 신뢰 손실. 실기기 업그레이드 검증 필수
- Install Referrer SharedPreferences 10+ 필드 MethodChannel 이관 누락 시 **외상 결제 계좌 정보 빈칸** 리그레션 (검토 포인트 3 재확인)

---

### 의제 11: 측정 인프라 — **CF 서버측 이벤트 + 클라이언트 Analytics/Crashlytics 병행** (2026-04-16)

**결정**: **양측 권고 모두 채택**. CF에 `acceptanceEvents` 컬렉션 + 기사 문서 `platform` 필드 + Flutter/Kotlin 양쪽에 `firebase_analytics` + `firebase_crashlytics` 즉시 도입.

**근거** (양측 중요 발견):
- **kotlin-expert**: CF `notifications` 컬렉션(ACK 기록)만 존재, 수락률 카운터 부재 확인. Kotlin 기사앱도 Analytics 호출 0건 (Grep)
- **flutter-expert**: Flutter `pubspec.yaml`에 `firebase_analytics`, `firebase_crashlytics` **둘 다 없음** (Grep 0건). **R1 발동 판단 근거 데이터 0 상태**

**구현 사양**:

1. **서버 측 (CF) — `acceptanceEvents` 컬렉션 신규**:
   ```
   /acceptanceEvents/{eventId}:
     callId, assignedDriverId, platform ("android" | "ios"),
     assignedAt, acceptedAt | rejectedAt | timeoutAt,
     latencyMs, outcome: "accepted" | "rejected" | "timeout"
   ```
   - `onCallStatusChanged` CF에 ASSIGNED → ACCEPTED/WAITING 전이 시 이벤트 기록
   - `checkAssignedTimeout`에 타임아웃 시 `outcome: "timeout"` 이벤트 기록
   - 월 집계: `aggregateMonthlyStats` 신규 스케줄러 → `/monthlyStats/{YYYY-MM}`

2. **기사 문서 `platform` 필드 추가** (의제 5 `fcmTokenPlatform`과 병합):
   - Flutter 첫 로그인 시 `platform: Platform.isIOS ? 'ios' : 'android'` 저장 (의제 5 메타 필드와 동일 write)
   - Kotlin 측도 `LoginViewModel.kt` 성공 경로에 `platform: "android"` 1줄 추가
   - 기존 기사 200명 문서 1회성 마이그레이션 스크립트 (`platform = "android"` 기본값)

3. **클라이언트 측 Flutter 필수 패키지**:
   ```yaml
   firebase_analytics: ^11.3.0
   firebase_crashlytics: ^4.1.0
   ```

4. **최소 지표 4종** (flutter-expert):
   - **수락률**: `call_accepted` / `call_assigned_received`. 목표 95%+, R1 임계치 80% 미만
   - **배차 Time-To-Accept**: 목표 30초, 임계치 90초 초과
   - **FCM 도달률**: 클라이언트 수신 이벤트 / CF 전송 count. 목표 98%+, 임계치 90% 미만
   - **Offline 판정 빈도**: 일 1명당 3회 초과 시 Presence 재검토

5. **Crashlytics Flutter 설정** (`main.dart`):
   ```dart
   FlutterError.onError = FirebaseCrashlytics.instance.recordFlutterFatalError;
   PlatformDispatcher.instance.onError = (error, stack) {
     FirebaseCrashlytics.instance.recordError(error, stack, fatal: true);
     return true;
   };
   ```
   - iOS Podfile Run Script Phase 추가 (dSYM 업로드)

6. **Kotlin 측도 동등 추가**:
   - `driver_app/build.gradle.kts`에 `firebase-analytics-ktx` + `firebase-crashlytics-ktx` 추가
   - `LoginViewModel`, `MyFirebaseMessagingService`에 `logEvent('call_assigned_received', ...)` 호출 추가

7. **데이터 집계 단계적 확장**:
   - MVP: Firestore 직접 집계 + Manager 앱 대시보드
   - 100 사무실 초과: CF 배치 집계 + `daily_stats/` 컬렉션
   - 2000 사무실: BigQuery + Looker Studio

**위험 신호**:
- **측정 인프라 없으면 R1_LOCKSCREEN 발동 기준 "5%p 하락" 평가 불가능** (TRIGGERS.md 명시). 의제 7 결정(Time Sensitive MVP + R1 CallKit)과 연결되므로 **Phase 1 출시 전 측정 인프라 반드시 구축**
- `acceptanceEvents` 신규 컬렉션 Firestore 보안 규칙 업데이트 필수 (admin 전용 write)
- Analytics/Crashlytics 추가 ~30MB 증가 (iOS 앱 바이너리) → App Clip 50MB 한도와 무관(메인 앱만 영향)
- Kotlin 측 Analytics 동시 도입은 **별도 과제로 분리** (플랜 범위 밖이나 Phase 1 출시 전 완료 필요)

---

### 의제 12: 로컬 저장소 — **MVP는 shared_preferences + Firestore 오프라인, sqflite는 R1** (2026-04-16)

**결정**: Phase 1 MVP는 `shared_preferences` + `flutter_secure_storage` + Firebase Firestore 자동 오프라인 persistence로 정산 캐시 커버. **sqflite / drift 신규 도입은 R1_PENDING_SYNC 발동 시점**으로 연기.

**근거** (양측 부분 대립, flutter-expert 권고 채택):
- **kotlin-expert**: Phase 1부터 sqflite 도입 권장. Kotlin Room 2테이블(`pending_syncs` + `settlement_cache`) 운영 중이므로 등가 보존 필요
- **flutter-expert**: MVP는 shared_preferences + Firestore offline으로 충분. 정산 캐시 1년 ~7200 row × 200B = ~1.4MB, shared_preferences 권장 한도 근처이나 가능. sqflite 초기 도입 러닝 커브 대비 실익 작음
- **중재자 판단**: Firestore SDK 자동 오프라인이 핵심 데이터를 커버 + Kotlin `addPendingSync` 호출처 자체가 불명확(FUNCTIONAL_INVENTORY §1.7 지적) → Phase 1은 flutter-expert 안. 단 R1_PENDING_SYNC 발동 시 즉시 도입

**Phase 1 MVP 구조**:
- **자격증명**: `flutter_secure_storage` (iOS Keychain accessibility `first_unlock_this_device`)
- **사무실 정보**: `shared_preferences` (Install Referrer 10+ 필드 포함)
- **정산 캐시**: Firestore SDK 자동 offline persistence (cloud_firestore 기본 enable)
- **이월금 리스너**: cloud_firestore snapshots() Stream (유일한 실시간 리스너)

**R1_PENDING_SYNC 발동 시** (R1_PENDING_SYNC TRIGGERS.md 조건 충족):
- 패키지 채택 우선순위:
  1. **drift (권장)**: 타입 안전 + 코드 생성 + Flow<List<T>> 등가 Stream 지원. Kotlin Room과 가장 유사
  2. **sqflite**: 성숙 + 공식. 수동 SQL 필요
  3. **isar**: 가장 빠르지만 maintenance 불확실 (pub.dev 재확인 필요)
- Kotlin `PendingSyncEntity.callSettlementJson` Gson 직렬화 → Flutter Freezed JsonSerializable 이관

**위험 신호**:
- Kotlin `SettlementDao.kt:67` `observePendingSyncCount(): Flow<Int>` UI 실시간 배지 패턴 → shared_preferences로는 재현 불가. **Phase 1은 해당 배지 UI 생략하거나 Firestore 리스너 대체**
- `SettlementRepository.kt:145, 194-228` optimistic version compare는 순수 함수이나 SQL 트랜잭션 내 보장 → Phase 1에선 Firestore 트랜잭션으로 대체 가능
- Firestore offline persistence 기본 크기 한도 **100MB** (충분). 이를 초과하는 로컬 DB 요구가 발생하면 R1 긴급 진입

---

### 의제 13, 14: (Mac 도착 후 + 기반 정책 — 추후 상세 논의)

---

## 6. 신규 식별 필요 조치 (플랜 확정 시 반영)

1. **Podfile iOS 15 상향** + `permission_handler` 매크로 설정 (의제 1 + 6)
2. **CF 전체 28곳+ `apns` 블록 audit** (의제 4 확대 과제)
3. **`driver_app_flutter/ios/Runner/PrivacyInfo.xcprivacy` 신규 작성** (의제 6)
4. **`fcm_service.dart` `interruptionLevel: .timeSensitive` 추가** (의제 7)
5. **`feature_flags.dart` + `incoming_call_service.dart` 신규 작성** (의제 7 R1 롤백 대비)
6. **CF `acceptanceEvents` 컬렉션 + 월 집계 스케줄러 신규** (의제 11, R1 평가용)
7. **기사 문서에 `platform` + `fcmTokenPlatform` 필드 추가** (의제 5 + 11 통합)
8. **Flutter pubspec: `firebase_analytics`, `firebase_crashlytics`, `in_app_update`, `firebase_remote_config` 추가** (의제 10 + 11)
9. **Kotlin 기사앱에도 Analytics/Crashlytics 도입** (별도 과제, Phase 1 출시 전 완료)
10. **기사 재로그인 UX: 이메일 사전 채움 + 비밀번호 재설정 경로 다이얼로그** (의제 9)
11. **iOS `KeychainAccessibility.first_unlock_this_device` 설정** (의제 9)
12. **손님앱 Firestore 보안 규칙 업데이트** (`acceptanceEvents` 컬렉션 admin 전용)

---

## 6. 신규 식별 필요 조치 (플랜 확정 시 반영)

1. **Podfile iOS 15 상향** + `permission_handler` 매크로 설정 (의제 1 + 6)
2. **CF 전체 28곳+ `apns` 블록 audit** (의제 4 확대 과제)
3. **`driver_app_flutter/ios/Runner/PrivacyInfo.xcprivacy` 신규 작성** (의제 6)
4. **`fcm_service.dart` `interruptionLevel: .timeSensitive` 추가** (의제 7)
5. **`feature_flags.dart` + `incoming_call_service.dart` 신규 작성** (의제 7 R1 롤백 대비)
6. **측정 인프라 선결** — `onCallAssigned`/`onCallAccepted` CF에 이벤트 로깅 추가 (의제 11, R1 평가용)

---

## 6. 결정 이후 작업 (Phase 진입)

모든 트랙 1 의제 결정 후:
1. `flutter/driver_app/MVP/*.md` 매핑 문서 작성 (kotlin-expert 주 저자)
2. `flutter/customer_app/MVP/*.md` 매핑 문서 작성
3. Codemagic 첫 빌드 시도
4. iOS Phase 6 코드 구현 + 손님앱 Phase 1 포팅
5. TestFlight Internal 배포

---

## 7. 참고

- 마스터 플랜: `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md`
- 팀원 컨텍스트: `.agent-teams/kotlin-expert.md`, `.agent-teams/flutter-expert.md`
- iOS 자산 (보류, 참조): `ios/WORKING_DOC.md`, `ios/FUNCTIONAL_INVENTORY.md`, `ios/driver_app/MVP/OVERVIEW.md`, `ios/driver_app/REINFORCEMENT/TRIGGERS.md`, `ios/customer_app/PLAN.md`
