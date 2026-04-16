# 빌드 / 배포 사양 (Codemagic + Podfile + Privacy Manifest)

> **의제 1, 3, 6, 11, 14 결정 통합** — Mac 없이 Codemagic 기반 iOS 정식 출시 핵심 문서
> **타임라인**: Apple Developer 승인 후 의제 3 결정 → codemagic.yaml 작성 → 첫 빌드
> **작성일**: 2026-04-16

---

## 1. iOS 빌드 환경 사양

### 1.1 deployment target (의제 1)

| 항목 | 값 |
|------|----|
| iOS minimum | **15.0** |
| Android minSdk | 24 (Android 7.0, 변경 없음) |
| Android targetSdk | 34+ (Phase 1) |
| Flutter SDK | 3.41.6 (FVM 고정, 의제 14-C) |
| Dart SDK | `^3.5.4` |

### 1.2 Bundle ID (의제 2 결정 후 확정)

현재 불일치 상태 → 의제 2에서 통일:
- Android: `com.designated.driverapp.app`
- iOS 현재: `com.designated.driverAppFlutter` (변경 예정)

**Apple Developer Program 등록 후**: Apple Developer Console에서 App ID 등록 → Bundle ID 최종 확정 → Firebase Console iOS 앱 등록 → `GoogleService-Info.plist` 갱신.

---

## 2. Podfile 사양 (의제 1, 6)

**파일**: `driver_app_flutter/ios/Podfile`

```ruby
# Uncomment this line to define a global platform for your project
platform :ios, '15.0'   # 의제 1: '12.0' → '15.0'

ENV['COCOAPODS_DISABLE_STATS'] = 'true'

project 'Runner', {
  'Debug' => :debug,
  'Profile' => :release,
  'Release' => :release,
}

def flutter_root
  generated_xcode_build_settings_path = File.expand_path(File.join('..', 'Flutter', 'Generated.xcconfig'), __FILE__)
  unless File.exist?(generated_xcode_build_settings_path)
    raise "#{generated_xcode_build_settings_path} must exist."
  end

  File.foreach(generated_xcode_build_settings_path) do |line|
    matches = line.match(/FLUTTER_ROOT\=(.*)/)
    return matches[1].strip if matches
  end
  raise "FLUTTER_ROOT not found in #{generated_xcode_build_settings_path}."
end

require File.expand_path(File.join('packages', 'flutter_tools', 'bin', 'podhelper'), flutter_root)

flutter_ios_podfile_setup

target 'Runner' do
  use_frameworks!

  flutter_install_all_ios_pods File.dirname(File.realpath(__FILE__))
end

post_install do |installer|
  installer.pods_project.targets.each do |target|
    flutter_additional_ios_build_settings(target)

    # 의제 1: deployment target 강제
    target.build_configurations.each do |config|
      config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '15.0'

      # 의제 6: permission_handler 매크로 — 사용 권한만 명시
      # permission_handler GitHub README 참조
      config.build_settings['GCC_PREPROCESSOR_DEFINITIONS'] ||= [
        '$(inherited)',
        'PERMISSION_LOCATION=1',
        'PERMISSION_MICROPHONE=1',
        'PERMISSION_SPEECH_RECOGNIZER=1',
        'PERMISSION_NOTIFICATIONS=1',
      ]
    end
  end
end
```

**적용 후 명령**: `cd ios && pod install --repo-update` (Mac 또는 Codemagic CI).

---

## 3. iOS 설정 파일 체크리스트

### 3.1 Info.plist 추가 항목

`driver_app_flutter/ios/Runner/Info.plist`:

| 항목 | 값 | 의제 |
|------|----|------|
| `NSLocationWhenInUseUsageDescription` | "운행 시작 시 현재 위치를 출발지로 자동 입력하기 위해 사용합니다. 주기적 위치 추적은 하지 않습니다." | 6 |
| `NSMicrophoneUsageDescription` | "출발지·목적지·요금을 음성으로 입력할 때 사용합니다." | 6 |
| `NSSpeechRecognitionUsageDescription` | "음성으로 말한 내용을 텍스트로 변환하여 주소·요금 입력을 돕습니다. 녹음은 저장되지 않습니다." | 6 |
| `UIBackgroundModes` | `[fetch, remote-notification, location]` | 4 |
| `NSLocationAlwaysUsageDescription` | **제거** (iOS 11+ deprecated) | 6 |

### 3.2 Runner.entitlements (신규)

`driver_app_flutter/ios/Runner/Runner.entitlements`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>com.apple.developer.usernotifications.time-sensitive</key>
  <true/>
  <key>aps-environment</key>
  <string>production</string>
</dict>
</plist>
```

**Apple Developer Console**: App ID Capabilities → Time Sensitive Notifications + Push Notifications 활성화 → 새 프로비저닝 프로파일 발급.

### 3.3 PrivacyInfo.xcprivacy (신규, 의제 6)

상세 내용은 `flutter/driver_app/MVP/PLATFORM_CHANNELS.md` §3.4 참조.

### 3.4 GoogleService-Info.plist (Bundle ID 확정 후)

Firebase Console → iOS 앱 등록(Bundle ID 최종) → 다운로드 → `ios/Runner/GoogleService-Info.plist`로 배치.

---

## 4. pubspec.yaml 의존성 추가 (의제 11, 14)

`driver_app_flutter/pubspec.yaml`에 추가:

```yaml
dependencies:
  # ... (기존 의존성 유지)

  # 의제 11 — 측정 인프라 필수 (R1 발동 평가용)
  firebase_analytics: ^11.3.0
  firebase_crashlytics: ^4.1.0

  # 의제 10 — 손님앱 전환 + 의제 11 — 최소 버전 강제
  in_app_update: ^4.2.3
  firebase_remote_config: ^5.1.0
```

**버전 핀**: `^` caret 사용으로 minor/patch 자동 상승 허용, major는 차단 (의제 14-B 정책).

**`pubspec.lock` 커밋**: 팀 + CI 빌드 일치 보장.

---

## 5. FVM 도입 (의제 14-C)

**즉시 도입** 결정 (Phase 6 코드 수정 전).

### 5.1 설치 (개발자 머신)

```bash
dart pub global activate fvm
fvm install 3.41.6
```

### 5.2 프로젝트 설정

`driver_app_flutter/.fvmrc`:

```json
{
  "flutter": "3.41.6",
  "flavors": {
    "production": "3.41.6"
  }
}
```

### 5.3 .gitignore 추가

`driver_app_flutter/.gitignore`:

```
.fvm/flutter_sdk/
```

### 5.4 IDE 설정 (VS Code)

`driver_app_flutter/.vscode/settings.json`:

```json
{
  "dart.flutterSdkPath": ".fvm/flutter_sdk",
  "search.exclude": {
    "**/.fvm": true
  },
  "files.watcherExclude": {
    "**/.fvm": true
  }
}
```

### 5.5 명령어 변환

| 기존 | FVM |
|------|-----|
| `flutter build apk` | `fvm flutter build apk` |
| `flutter pub get` | `fvm flutter pub get` |
| `flutter run` | `fvm flutter run` |

---

## 6. Codemagic 설정 (의제 3, Apple Developer 승인 후 확정)

### 6.1 codemagic.yaml 초안

`driver_app_flutter/codemagic.yaml` (Apple 승인 후 수치 확정):

```yaml
workflows:
  ios-release:
    name: iOS Release Build
    instance_type: mac_mini_m2
    max_build_duration: 60
    integrations:
      app_store_connect: codemagic-app-store-key
    environment:
      ios_signing:
        distribution_type: app_store
        bundle_identifier: com.designated.driverapp.app  # 의제 2 결정값
      vars:
        APP_STORE_APPLE_ID: "TBD"  # App Store Connect 앱 Apple ID
      flutter: fvm  # 의제 14-C
      xcode: latest
      cocoapods: default
    scripts:
      - name: Set up FVM
        script: |
          dart pub global activate fvm
          fvm install
          fvm use $(cat .fvmrc | jq -r '.flutter')
      - name: Get Flutter packages
        script: fvm flutter pub get
      - name: Run code generation (Freezed)
        script: fvm dart run build_runner build --delete-conflicting-outputs
      - name: Pod install
        script: |
          cd ios
          pod install --repo-update
      - name: Flutter analyze
        script: fvm flutter analyze
      - name: Run tests
        script: fvm flutter test
      - name: Flutter build ipa
        script: |
          fvm flutter build ipa --release \
            --export-options-plist=/Users/builder/export_options.plist
    artifacts:
      - build/ios/ipa/*.ipa
      - /tmp/xcodebuild_logs/*.log
      - flutter_drive.log
    publishing:
      app_store_connect:
        auth: integration
        submit_to_testflight: true
        beta_groups:
          - Internal Testers
        submit_to_app_store: false  # External 베타 검증 후 수동 승격

  android-release:
    name: Android Release Build (App Bundle)
    instance_type: linux_x2
    environment:
      android_signing:
        - keystore_reference  # Codemagic UI에서 등록
      flutter: fvm
      vars:
        PACKAGE_NAME: "com.designated.driverapp.app"
    scripts:
      - name: Set up FVM
        script: dart pub global activate fvm && fvm install
      - name: Get packages
        script: fvm flutter pub get
      - name: Code generation
        script: fvm dart run build_runner build --delete-conflicting-outputs
      - name: Build App Bundle (의제 14-F)
        script: fvm flutter build appbundle --release
    artifacts:
      - build/app/outputs/bundle/release/*.aab
      - build/app/outputs/mapping/release/mapping.txt
    publishing:
      google_play:
        credentials: $GCLOUD_SERVICE_ACCOUNT_CREDENTIALS
        track: internal  # 내부 테스트 → Closed → Production 단계 (의제 10 Staged Rollout)
        in_app_update_priority: 3  # 의제 10
```

### 6.2 인증 설정 (Codemagic UI)

1. **App Store Connect API Key 발급**:
   - App Store Connect → Users and Access → Keys → Generate API Key (Admin role)
   - `.p8` 파일 다운로드 + Key ID + Issuer ID 메모

2. **Codemagic 등록**:
   - Teams → Integrations → App Store Connect → Add new key
   - `codemagic-app-store-key` 명칭으로 저장

3. **iOS 인증서 자동 관리**:
   - Codemagic이 자동으로 distribution certificate + provisioning profile 생성·갱신
   - Apple Developer Console에서 매번 수동 발급 불필요

4. **Android Keystore**:
   - Codemagic UI → Code signing identities → Android keystores
   - keystore.jks + alias + passwords 등록 → `keystore_reference` 명칭 매핑

5. **Google Play Service Account**:
   - GCP Console → Service Account 생성 → Play Console에 권한 부여
   - JSON key를 `GCLOUD_SERVICE_ACCOUNT_CREDENTIALS` 환경변수로 등록

### 6.3 빌드 트리거

| 시나리오 | 트리거 |
|---------|--------|
| 매 push (main 브랜치) | 자동 빌드 + TestFlight Internal 자동 배포 |
| Tag (`v1.0.0` 등) | 자동 빌드 + TestFlight External + 수동 App Store 승격 |
| 수동 빌드 | Codemagic UI 클릭 |
| 90일 build 만료 직전 | 새 빌드 자동 (TestFlight 만료 갱신) |

---

## 7. 빌드 단계별 체크리스트

### 7.1 Week 0 (즉시, Apple Developer 등록 시작 직후)

- [ ] Apple Developer Program 등록 ($99/년) — 사용자 측
- [ ] Codemagic 계정 생성 + GitHub 연동 — 사용자 측
- [ ] Bundle ID 임시 등록 (의제 2 결정 전 placeholder) — `com.designated.driverapp.app` 추정
- [ ] App Store Connect에서 iOS 앱 신규 생성

### 7.2 Week 0~1 (의제 1, 6, 14 적용)

- [ ] `Podfile` line 2, 47 → `'15.0'` 상향
- [ ] `Podfile` `post_install`에 permission_handler 매크로 추가
- [ ] `Info.plist` Permission 사유 4종 추가 + `NSLocationAlwaysUsageDescription` 제거
- [ ] `Runner.entitlements` 신규 작성 (Time Sensitive + APNs)
- [ ] `PrivacyInfo.xcprivacy` 신규 작성 (Privacy Manifest)
- [ ] `pubspec.yaml` 의존성 4종 추가 (analytics, crashlytics, in_app_update, remote_config)
- [ ] `.fvmrc` 작성 + `.gitignore` 업데이트 + IDE 설정
- [ ] `fvm flutter pub get` 검증

### 7.3 Week 1~2 (의제 5, 7, 9, 11 코드 보강)

- [ ] `lib/core/feature_flags.dart` 신규 (CallKit Feature Flag)
- [ ] `lib/services/incoming_call_service.dart` 신규 (라우팅)
- [ ] `lib/services/lock_screen_service.dart` `Platform.isAndroid` 가드 추가
- [ ] `lib/services/fcm_service.dart` `interruptionLevel: .timeSensitive` 추가 (line 157~160)
- [ ] `lib/services/fcm_service.dart` 토큰 저장 시 `fcmTokenPlatform` 메타 추가
- [ ] `lib/services/auth_service.dart` Firebase Auth currentUser 자동 상속 + 이메일 사전 채움
- [ ] `flutter_secure_storage` iOS `KeychainAccessibility.first_unlock_this_device` 설정
- [ ] `main.dart` Crashlytics 초기화

### 7.4 Week 2~3 (Apple 승인 후 의제 2, 3 적용)

- [ ] 의제 2 결정 → Bundle ID 최종 확정
- [ ] Firebase Console iOS 앱 등록 + `GoogleService-Info.plist` 다운로드
- [ ] APNs Authentication Key (.p8) 발급 + Firebase Console 등록
- [ ] Apple Developer Console에서 App ID Capabilities 활성화
- [ ] 의제 3 결정 → `codemagic.yaml` 작성 (본 문서 §6.1 기준)
- [ ] App Store Connect API Key 발급 + Codemagic 등록
- [ ] 첫 iOS 빌드 시도 → 빌드 로그 디버깅
- [ ] TestFlight Internal 첫 업로드

### 7.5 Week 3~5 (TestFlight 파일럿)

- [ ] 기사 5~10명 Internal Testing 배포
- [ ] Crashlytics 원격 로그 수집
- [ ] `acceptanceEvents` 측정 데이터 검증 (의제 11)
- [ ] 버그 수정 후 재빌드 (Codemagic 자동)

### 7.6 Week 5~7 (External + 정식 출시)

- [ ] App Store Connect External Testing 신청
- [ ] 메타데이터 입력 (스크린샷 5장, 설명, 카테고리, 개인정보 정책 URL)
- [ ] App Review 제출
- [ ] 심사 통과 → Production Release
- [ ] Android 동시 정식 출시 (의제 10 Staged Rollout)

---

## 8. Mac 없이 가능한 작업 vs Mac 필요한 작업

### Mac 없이 가능 (Windows + Codemagic)
- 모든 코드 작성·수정·테스트
- pubspec 의존성 관리
- Android 실기기 디버깅
- Codemagic으로 iOS 빌드 + TestFlight 업로드
- App Store Connect 메타데이터 + 스크린샷 업로드 (웹 UI)
- App Review 제출
- App Store 정식 출시
- 업데이트 배포 (forceUpdate via Remote Config)

### Mac 필수 (Phase 2 또는 Phase 1 디버깅 시)
- iOS 실기기 USB 디버깅 (긴급 시 MacinCloud 1~3일 단기)
- App Clip Swift 타겟 작성 (Phase 2, 의제 13)
- Apple Configurator (특수 상황)

**Phase 1 정식 출시는 Mac 0대로 완결 가능** (의제 결정 누적 결과).

---

## 9. 위험 신호 + 사전 완화

| 리스크 | 완화 |
|-------|------|
| Codemagic 첫 빌드 Pod install 실패 | Podfile iOS 15.0 상향 + repo-update + Codemagic 로그 디버깅. 최악 MacinCloud 1일 |
| Bundle ID 의제 2 결정 지연 | Week 0~1 작업은 placeholder Bundle ID로 진행 가능 (Info.plist/Podfile 무관) |
| Privacy Manifest 누락으로 자동 리젝 | §3.3 PrivacyInfo.xcprivacy 사전 작성 + Firebase SDK 자동 manifest 의존 |
| Apple Developer 등록 1주 이상 지연 (개인 vs 법인) | 개인 등록 권장 (즉시 가입). 법인 전환은 출시 후 |
| TestFlight 90일 build 만료 | Codemagic 정기 빌드 스케줄링 또는 정식 출시 후 정기 릴리즈로 자연 갱신 |
| `firebase_analytics` 추가로 앱 크기 증가 (~5MB) | App Bundle 무관 (Google Play 동적 전달). iOS 50MB 한도 여유 |
| FVM 미도입 시 CI 빌드 SDK 버전 불일치 | §5 FVM 즉시 도입 + .fvmrc 커밋 |

---

## 10. 비용 예상

| 항목 | 비용 |
|------|------|
| Apple Developer Program | $99/년 |
| Codemagic 무료 티어 | 0원 (월 500분, 소규모 충분) |
| Codemagic 유료 (필요 시) | $95/월 (빌드 시간 확장) |
| MacinCloud 단기 (긴급) | $5~10/일 |
| Firebase 사용료 | 기존 동일 (Spark/Blaze) |

**Phase 1 정식 출시까지 최소 비용: $99 (Apple Developer)**.

---

## 11. 참조

- `flutter/driver_app/MVP/OVERVIEW.md` — Phase 6 실행 사양 종합
- `flutter/driver_app/MVP/PLATFORM_CHANNELS.md` — Info.plist + Entitlements 상세
- `flutter/driver_app/MVP/FCM.md` — Time Sensitive Notification 클라이언트 측 (예정, flutter-expert 작성)
- `flutter/WORKING_DOC.md` §5 의제 1, 3, 6, 11, 14 결정 전문
- `driver_app_flutter/pubspec.yaml` — 현재 의존성 (의제 11/14 추가 대상)
- `driver_app_flutter/ios/Podfile` — 현재 IPHONEOS_DEPLOYMENT_TARGET = '12.0' (상향 대상)
- Codemagic 공식 문서: https://docs.codemagic.io/yaml-quick-start/building-a-flutter-app/
- Apple Developer Program: https://developer.apple.com/programs/enroll/
