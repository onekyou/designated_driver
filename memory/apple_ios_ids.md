---
name: apple_ios_ids
description: Apple Developer 계정의 기사앱 iOS 식별자 — Team ID, Bundle ID, App ID 등록 정보
type: reference
---

# Apple Developer — 기사앱 iOS 식별자

## 등록 완료 (2026-04-17)

| 항목 | 값 |
|------|-----|
| **Apple Team ID** | `VCJD377MAU` |
| **Bundle ID (Explicit App ID)** | `com.designated.driverapp.app` |
| **Description (Developer Portal)** | `Designated Driver` |
| **Capabilities Enabled** | Push Notifications |
| **Platform** | iOS |
| **App Store Connect 앱 이름** | `콜마당 기사` |
| **기본 언어** | 한국어 |
| **SKU** | `driverapp-ios-001` |
| **사용자 액세스 권한** | 전체 액세스 |

## 쓰임새
- Codemagic `codemagic.yaml` 환경변수에 Team ID 필요 (`APP_STORE_CONNECT_TEAM_ID`)
- App Store Connect API Key 발급 시 Team ID 필요
- `driver_app_flutter/ios/Runner.xcodeproj/project.pbxproj`의 `PRODUCT_BUNDLE_IDENTIFIER`를 기존 `com.designated.driverAppFlutter` → `com.designated.driverapp.app`으로 변경 필요

## Android 대응
- Kotlin `driver_app/app/build.gradle` applicationId: `com.designated.driverapp.app` (동일)
- Flutter `driver_app_flutter/android/app/build.gradle` applicationId: `com.designated.driverapp.app` (동일)
- → iOS/Android 통일 ID로 FCM 토큰 관리 단순화 (의제 2 권장안 반영)

## 향후 발급 예정
- App Store Connect API Key (Codemagic 연동 시)
- APNs Authentication Key (.p8) 또는 APNs Certificate (Firebase Console 업로드용)
