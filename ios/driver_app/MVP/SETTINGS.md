# iOS 기사앱 Phase 1 — SETTINGS (Xcode 프로젝트 설정·Info.plist·Capabilities)

> **범위**: Bundle ID, deployment target, Capabilities, Info.plist 권한, Firebase 설정, APNs 키, URL Schemes, ATS, Privacy Manifest, 빌드 구성·Signing.
> **제외 (Phase 2)**: VoIP Background Mode, Audio Background Mode, App Groups, Live Activity widget extension target — `R1_*.md` 참조.
> **전제**: Xcode 15.4+ (iOS 17.2 SDK), Swift 5.10+, Apple Developer 계정 보유.

---

## 1. 원본 소스

- Android `AndroidManifest.xml`, `app/build.gradle.kts`
- 인벤토리: `ios/FUNCTIONAL_INVENTORY.md` §2.4, §2.6
- 합의: `ios/WORKING_DOC.md` 주제 1 (iOS 17.2), 주제 5 (Phase 분리), `ios/README.md`
- PUSH.md (FCM/APNs 부분과 일관 유지)

---

## 2. 요약

Phase 1 기사앱은 **단일 메인 타겟**(앱 본체)만으로 구성. Live Activity widget extension·App Clip·Notification Service Extension은 모두 Phase 2(R1) 또는 손님앱 전용이라 본 문서에서는 다루지 않는다. Background Modes는 `remote-notification`만, Capabilities는 `Push Notifications`와 `Sign in with Apple`(미사용 — 추가 안 함) 이외 추가 없음. Privacy Manifest는 Firebase SDK 의무 항목 + 자체 사용 API만 선언.

---

## 3. iOS 설정

### 3.1 Bundle ID 확정

- **기사앱**: `com.designated.driver.ios`
- **손님앱**: `com.designated.customer.ios` (별도 프로젝트, 본 문서 범위 외)
- **App Clip (손님앱 전용)**: `com.designated.customer.ios.Clip` (별도)

App Store Connect / Apple Developer Member Center에 동일 ID로 App ID 등록 필요. Provisioning Profile은 Xcode automatic signing으로 관리.

### 3.2 Deployment Target

| 설정 | 값 |
|---|---|
| iOS Deployment Target | **17.2** |
| Swift Language Version | 5.10+ (Swift 6 mode 옵션 권장 — strict concurrency 컴파일 보호) |
| Build System | New Build System (Xcode 기본) |

근거: WORKING_DOC §5 주제 1 — @Observable + SwiftData + Push-to-Start Live Activity(R1)에 17.2가 안전 하한선.

### 3.3 Capabilities (Signing & Capabilities 탭)

| Capability | Phase 1 | 비고 |
|---|---|---|
| **Push Notifications** | ✓ | APNs 등록 |
| **Background Modes → Remote notifications** | ✓ | data-only push wakeup |
| Background Modes → Voice over IP | ✗ | R1 (`R1_LOCKSCREEN.md`) |
| Background Modes → Audio | ✗ | R1 (CallKit ringtone) |
| Background Modes → Background fetch | ✗ | 미사용 |
| Background Modes → Background processing | ✗ | R1 (BGTaskScheduler 도입 시) |
| App Groups | ✗ | R1 (Live Activity 공유) |
| Associated Domains | ✗ | 손님앱 App Clip 전용 |
| Sign in with Apple | ✗ | 미사용 |
| Keychain Sharing | ✗ | 단일 앱 내부 사용만 |
| Communication Notifications | ✗ | 미사용 |

### 3.4 Info.plist

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- 앱 메타 -->
    <key>CFBundleDisplayName</key>
    <string>콜마당 기사</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>

    <!-- iOS 최소 버전 (Xcode 자동 동기화되나 명시 권장) -->
    <key>MinimumOSVersion</key>
    <string>17.2</string>

    <!-- Background Modes (Capabilities와 동기화) -->
    <key>UIBackgroundModes</key>
    <array>
        <string>remote-notification</string>
    </array>

    <!-- Firebase: AppDelegate Proxy off (PUSH.md 참조) -->
    <key>FirebaseAppDelegateProxyEnabled</key>
    <false/>

    <!-- 권한 사유 문구 (한국어) -->
    <key>NSUserNotificationsUsageDescription</key>
    <string>배차 알림을 받기 위해 알림을 허용해주세요.</string>

    <!-- 위치 권한: Phase 1엔 Geocoder만 사용. 백그라운드 추적 없음 -->
    <key>NSLocationWhenInUseUsageDescription</key>
    <string>출발지·도착지 입력 시 현재 위치를 사용합니다.</string>

    <!-- 카메라/사진: QR 추천 화면이 QR 스캔이 아닌 표시만 → 미선언 -->
    <!-- 마이크: 미사용 -->
    <!-- 연락처: 미사용 -->

    <!-- 인터페이스 방향 (세로 고정) -->
    <key>UISupportedInterfaceOrientations</key>
    <array>
        <string>UIInterfaceOrientationPortrait</string>
    </array>

    <!-- Status bar -->
    <key>UIStatusBarStyle</key>
    <string>UIStatusBarStyleDefault</string>

    <!-- Scene 설정 (SwiftUI App life-cycle 사용) -->
    <key>UIApplicationSceneManifest</key>
    <dict>
        <key>UIApplicationSupportsMultipleScenes</key>
        <false/>
    </dict>

    <!-- ATS: Firebase·자체 API 모두 HTTPS만 사용 → 기본값 유지(예외 없음) -->
    <!-- (NSAppTransportSecurity 키 미선언이 가장 안전) -->

    <!-- URL Schemes: Phase 1엔 외부 진입 없음. 추후 deep link 필요 시 추가 -->
</dict>
</plist>
```

### 3.5 Firebase 설정

1. **Firebase Console** → Project Settings → **iOS app 등록** (Bundle ID `com.designated.driver.ios`)
2. `GoogleService-Info.plist` 다운로드 → Xcode 프로젝트 루트(앱 타겟에 추가, "Copy items if needed" + Target Membership 체크)
3. Swift Package Manager 의존성 추가: `https://github.com/firebase/firebase-ios-sdk` (latest stable)
   - FirebaseCore
   - FirebaseAuth
   - FirebaseFirestore
   - FirebaseMessaging
   - FirebaseDatabase
   - FirebaseFunctions
4. `App` 진입에서 `FirebaseApp.configure()` (PUSH.md §4.1 참조)

> **주의**: `GoogleService-Info.plist`는 **Bundle ID와 정확히 일치**해야 함. 잘못된 ID 등록 시 Auth/Firestore SDK가 silently 잘못된 프로젝트를 가리킬 수 있음.

### 3.6 APNs 인증서 / 키 발급

1. Apple Developer → Certificates, Identifiers & Profiles → **Keys** → "+" → "Apple Push Notifications service (APNs)" 체크 → 키 이름 입력 → Continue → Register
2. **p8 파일 다운로드** (1회만 가능 — 안전 보관)
3. **Key ID** 기록 (10자 영숫자)
4. **Team ID**: 동일 페이지 우측 상단 또는 Membership 탭에서 확인
5. Firebase Console → Project Settings → **Cloud Messaging** → Apple app configuration → **APNs Authentication Key** 업로드 (p8 + Key ID + Team ID)

> Phase 2 VoIP 채택 시 **동일 p8 키 재사용 가능**. CF 측에서 `apns-push-type: voip` 헤더로 분리 발송 (R1).

### 3.7 URL Schemes

Phase 1엔 외부 진입 없음. 향후 추가 시 (예: 매니저앱 ↔ 기사앱 deep link, 또는 웹 → 앱):

```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLName</key>
        <string>com.designated.driver.ios.url</string>
        <key>CFBundleURLSchemes</key>
        <array><string>callmadang-driver</string></array>
    </dict>
</array>
```

### 3.8 App Transport Security (ATS)

- 기본값 유지 (모든 연결 HTTPS 강제)
- Firebase·Cloud Functions·자체 API 모두 HTTPS이므로 예외 선언 불필요
- 주소 검색 API(카카오/네이버) 호출도 HTTPS 엔드포인트 사용 확인 후 도입

### 3.9 Privacy Manifest (PrivacyInfo.xcprivacy)

iOS 17부터 Apple이 의무화. 자체 앱 + 사용 SDK 양쪽 모두 선언 필요. **2024년 봄 이후 신규/업데이트 앱은 누락 시 심사 경고/리젝**.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>NSPrivacyTracking</key>
    <false/>

    <key>NSPrivacyTrackingDomains</key>
    <array/>

    <key>NSPrivacyCollectedDataTypes</key>
    <array>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypeEmailAddress</string>
            <key>NSPrivacyCollectedDataTypeLinked</key>
            <true/>
            <key>NSPrivacyCollectedDataTypeTracking</key>
            <false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypePhoneNumber</string>
            <key>NSPrivacyCollectedDataTypeLinked</key>
            <true/>
            <key>NSPrivacyCollectedDataTypeTracking</key>
            <false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypePreciseLocation</string>
            <key>NSPrivacyCollectedDataTypeLinked</key>
            <true/>
            <key>NSPrivacyCollectedDataTypeTracking</key>
            <false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypeUserID</string>
            <key>NSPrivacyCollectedDataTypeLinked</key>
            <true/>
            <key>NSPrivacyCollectedDataTypeTracking</key>
            <false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypeDeviceID</string>
            <key>NSPrivacyCollectedDataTypeLinked</key>
            <true/>
            <key>NSPrivacyCollectedDataTypeTracking</key>
            <false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
    </array>

    <!-- 자체 사용 Required Reason API (Apple 명시 카테고리만) -->
    <key>NSPrivacyAccessedAPITypes</key>
    <array>
        <dict>
            <key>NSPrivacyAccessedAPIType</key>
            <string>NSPrivacyAccessedAPICategoryUserDefaults</string>
            <key>NSPrivacyAccessedAPITypeReasons</key>
            <array><string>CA92.1</string></array>
        </dict>
        <dict>
            <key>NSPrivacyAccessedAPIType</key>
            <string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
            <key>NSPrivacyAccessedAPITypeReasons</key>
            <array><string>C617.1</string></array>
        </dict>
    </array>
</dict>
</plist>
```

> Firebase SDK 등 외부 의존성은 SDK 자체가 PrivacyInfo.xcprivacy를 포함 (Firebase 11.x+ 자동 포함). 별도 선언 불필요.

### 3.10 빌드 구성 (Schemes / Configurations)

| Configuration | 용도 | Bundle ID 변형 | Firebase 프로젝트 |
|---|---|---|---|
| Debug | 로컬 개발 (시뮬레이터/실기기) | `com.designated.driver.ios.dev` (옵션) | `dev` 프로젝트 (옵션) |
| Release | TestFlight + App Store | `com.designated.driver.ios` | `prod` 프로젝트 |

> 단순화 위해 Phase 1은 **Debug/Release 동일 Bundle ID**(`com.designated.driver.ios`) + 동일 Firebase 프로젝트 사용 가능. 추후 분리 시 `GoogleService-Info-Dev.plist` 추가 + Build Phase Run Script로 교체.

Schemes:
- **DriverApp** (Run = Debug, Archive = Release)
- TestFlight 업로드: Archive → Validate → Distribute App → App Store Connect

### 3.11 Signing

- Apple Developer 계정 (Individual 또는 Organization, $99/year)
- Xcode → Signing & Capabilities → **Automatically manage signing** 활성 → Team 선택
- Provisioning Profile은 자동 생성·갱신
- 수동 관리 필요 시(CI 등) → Provisioning Profile + Distribution Certificate (.p12) 생성 별도 절차

---

## 4. 스펙

### 4.1 디렉토리 구조 (권장)

```
DriverApp/
├── DriverAppMain.swift               # @main App
├── AppDelegate.swift                  # 푸시·Firebase 초기화
├── Info.plist
├── PrivacyInfo.xcprivacy
├── GoogleService-Info.plist
├── Assets.xcassets/
│   ├── AppIcon.appiconset/
│   └── AccentColor.colorset/
├── Models/                            # MODELS.md 대상
├── ViewModels/                        # VIEWMODELS.md 대상
├── Views/                             # SCREENS.md 대상
├── Repositories/                      # FIRESTORE.md 대상
├── Push/                              # PushRouter, TokenUploader (PUSH.md)
├── Auth/                              # AUTH.md 대상
└── Resources/
    └── Localizable.xcstrings           # ko.lproj 통합
```

### 4.2 의존성 버전 (Swift Package)

| 패키지 | 권장 버전 | 메모 |
|---|---|---|
| firebase-ios-sdk | 11.x latest | iOS 13+ 지원, 17.2 호환 OK |

타사 라이브러리는 Phase 1에선 **도입 보류**. swift-async-algorithms 등은 필요 시점에 평가(Topic 4 합의).

### 4.3 환경별 차이 (현재 단일 환경 가정)

Phase 1은 **단일 Firebase 프로젝트** 운영. dev/staging/prod 분리는 Phase 2 운영 안정화 후.

---

## 5. 체크리스트

### 5.1 Apple Developer / App Store Connect
- [ ] Apple Developer 계정 활성 ($99 결제 확인)
- [ ] App ID 등록 — `com.designated.driver.ios`
- [ ] APNs Auth Key (p8) 발급 + Key ID/Team ID 기록 + 안전 보관
- [ ] App Store Connect → 새 앱 생성 (이름·SKU·기본 언어 한국어)

### 5.2 Firebase Console
- [ ] iOS 앱 등록 (Bundle ID 일치)
- [ ] `GoogleService-Info.plist` 다운로드
- [ ] Cloud Messaging → APNs Auth Key 업로드 (p8 + Key ID + Team ID)
- [ ] Firestore Security Rules 검증 (기존 Android 규칙 그대로)
- [ ] Functions deployed 상태 확인 (`acknowledgeNotification`, `oncallassigned` 등)

### 5.3 Xcode 프로젝트
- [ ] Bundle ID `com.designated.driver.ios`
- [ ] Deployment Target 17.2
- [ ] Capabilities: Push Notifications + Background Modes(Remote notifications)
- [ ] Info.plist 권한 문구 한국어 + UIBackgroundModes + FirebaseAppDelegateProxyEnabled=false
- [ ] PrivacyInfo.xcprivacy 추가 + 데이터 타입·API 사유 선언
- [ ] GoogleService-Info.plist 프로젝트 추가 + 타겟 멤버십
- [ ] firebase-ios-sdk Swift Package 추가 (Core/Auth/Firestore/Messaging/Database/Functions)
- [ ] 자동 Signing + Team 선택
- [ ] 화면 방향 세로 고정
- [ ] AppIcon (1024×1024 + 모든 사이즈) 등록

### 5.4 빌드·배포
- [ ] Debug 빌드 시뮬레이터 실행 성공
- [ ] Debug 빌드 실기기 실행 성공 (Push Notification 수신 검증 가능)
- [ ] Release Archive → Validate → no warnings
- [ ] TestFlight 업로드 + 외부 테스터 초대
- [ ] 첫 심사 제출 시 App Review Notes 한국어 작성:
  - 테스트 계정 (이메일/비밀번호)
  - "지방 대리운전 회사 기사용 앱. 콜매니저(별도 앱)에서 배차된 콜을 기사가 수락·거절·운행·정산 처리합니다."
  - 권한 사유 명시

---

## 6. Phase 2 deferred (R1/R2)

| 항목 | Phase 2 추가 작업 |
|---|---|
| Background Modes → Voice over IP | R1 VoIP push 도입 시 |
| Background Modes → Audio | R1 CallKit ringtone 재생 시 |
| Background Modes → Background processing | R1 BGTaskScheduler 도입 시 |
| App Groups | R1 Live Activity widget extension과 데이터 공유 시 |
| Live Activity widget extension target | R1 Live Activity Push-to-Start 도입 시 |
| `BGTaskSchedulerPermittedIdentifiers` Info.plist | R1 SyncWorker 백그라운드 sync |

---

## 7. 참조

- Apple: [Configuring Background Execution Modes](https://developer.apple.com/documentation/xcode/configuring-background-execution-modes)
- Apple: [Information Property List](https://developer.apple.com/documentation/bundleresources/information-property-list)
- Apple: [Privacy manifest files](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files)
- Apple: [Describing data use in privacy manifests](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files/describing-data-use-in-privacy-manifests)
- Apple: [Describing use of required reason API](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files/describing-use-of-required-reason-api)
- Apple: [TestFlight](https://developer.apple.com/testflight/)
- Apple: [App Store Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- Firebase: [Add Firebase to your Apple project](https://firebase.google.com/docs/ios/setup)
- Firebase: [APNs setup](https://firebase.google.com/docs/cloud-messaging/ios/certs)
- 본 프로젝트: `ios/driver_app/MVP/PUSH.md`, `ios/WORKING_DOC.md` 주제 1, `ios/README.md`
