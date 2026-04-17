---
name: phase6_coding_remaining
description: Phase 6 (iOS 출시를 위한 실제 코딩 보강) 남은 작업 체크리스트. 누가 다시 봐도 현 위치를 알 수 있도록 정리
type: project
---

# Phase 6 — iOS 출시 전 남은 코딩 보강

**작성일**: 2026-04-17
**Why**: "MVP 매핑 문서 + P0 패치 완료 = 코딩 진입 가능"이 원래 계획. 오늘 인프라(Codemagic)는 구축했으나 **실제 iOS 대응 코드 보강은 대부분 미착수** 상태. 누가 세션을 다시 열어도 현 위치를 알 수 있도록 정리.
**How to apply**: 아래 순서대로 한 항목씩 처리. 각 항목 완료 시 체크하고 커밋 해시 기록.

---

## 현재 위치 한눈에 보기

```
[완료] 계획/설계 단계
  ├─ ✅ MVP 매핑 문서 27개 (4/16, 팀원 작업)
  ├─ ✅ 교차 리뷰 P0 9건 발견 (4/16, REVIEW_FINDINGS.md)
  └─ ✅ P0 스펙 패치 7/9 (4/17, .md 문서 정리 — 런타임 영향 없음)

[완료] iOS 인프라
  ├─ ✅ Apple Developer App ID 등록 (com.designated.driverapp.app)
  ├─ ✅ App Store Connect 앱 생성 (콜마당 기사)
  ├─ ✅ Codemagic 레포 연결 + codemagic.yaml Phase A
  └─ ✅ iOS 15.0 타깃 상향 (Podfile + pbxproj)
  └─ ✅ 빌드 파이프라인 작동 확인 (no codesign, 10m 37s)

[진행 중] Phase 6 실제 코딩 ← 지금 이 위치
  └─ 아래 7개 항목 착수 전

[대기] 배포
  ├─ TestFlight Internal
  ├─ 파일럿 테스트
  └─ App Store 심사
```

---

## 7개 코딩 보강 항목 (권장 순서)

### ① 서버측 보강 (CF) — ✅ 완료 (2026-04-18, commit 745070fa)

- [x] **의제 4 FCM `apns` 블록 추가** — 34곳 audit, 헬퍼 적용 24곳 + 예외 처리 7곳 + 스킵 2곳(재전송/testFcmMessage)
  - `functions/src/utils/fcmPayload.ts` 신규 (buildFcmPayload / buildMulticastFcmPayload)
  - 예외 7곳 (기존 최상위 notification 유지 + apns 수동 추가): POINTS_EARNED, call_status_update 고객용, DRIVER_APPROVAL_REQUEST, new_customer, CALL_DETECTOR_CRASH, SETTLEMENT_SUBMITTED, SETTLEMENT_DISCREPANCY
  - data 블록 100% 보존 → Android 회귀 0

- [x] **의제 11 `acceptanceEvents` 컬렉션 + 월 집계**
  - `functions/src/analytics/acceptanceEvents.ts` + `aggregateMonthly.ts` 신규
  - onCallStatusChanged ASSIGNED→ACCEPTED/REJECTED 분기 신규 추가 (dead data였던 rejectedByDriver 첫 소비처)
  - checkAssignedTimeout 오프라인/3분 타임아웃 2곳 훅
  - aggregateMonthlyStats 스케줄러 배포 완료 (매월 1일 00:00 KST)
  - R1_LOCKSCREEN 자동 평가 로직 (Android/iOS 각 100건 이상일 때만 5%p 판정)

- [x] **Firestore 보안 규칙 갱신 (신규 블록만)**
  - `acceptanceEvents`, `monthlyStats` 규칙 추가 (기존 규칙 건드리지 않음)
  - **미완료**: designated_drivers/customerInfo 기존 규칙의 `affectedKeys` + fcmTokenPlatform 필드 write 허용은 **Phase 6 ②로 이관** (Kotlin platform 배포 순서 의존성 회피)
  - E.1(b) customerInfo 권한 취약점은 **별도 보안 설계 세션** 필요 (phone-uid 매핑)

- [ ] **남은 ①번 후속** (②와 함께 진행):
  - `functions/scripts/backfill-platform.js` 실행 — Kotlin platform 저장 코드 배포 후
  - `firestore.rules` 기존 규칙의 affectedKeys 제한 추가 — 위 배포 이후

### ② 기사앱 클라이언트 코드 보강 (Kotlin + Flutter) — **iOS TestFlight 출시와 묶어서 진행**

> **⚠️ 배포 순서 의존성 (역순 진행 시 회귀 발생)**
>
> 반드시 아래 순서대로. 한 단계라도 앞뒤 바꾸면 Firestore write 거부 → FCM 토큰 저장 전체 실패 → 배차 알림 0% 도달의 대형 회귀.
>
> **Day 1. Kotlin 기사앱 재배포 (platform 저장 코드 포함)**
> - Kotlin에 `platform: "android"` + `fcmTokenPlatform: "android"` 저장 코드 추가
> - APK 빌드 → 실기기 3대(S21+/S22/Flip4) 설치 → 로그캣 확인
> - 이 단계 완료 전엔 절대 Day 3(rules)로 넘어가지 말 것
>
> **Day 2. `backfill-platform.js` 실행**
> - `node functions/scripts/backfill-platform.js` (GOOGLE_APPLICATION_CREDENTIALS 설정 필요)
> - 기존 기사 문서 전체에 `platform: "android"` + `fcmTokenPlatform: "android"` 일괄 추가
> - 완료 후 Firebase Console → Firestore에서 샘플 기사 문서 확인 (필드 존재 여부)
>
> **Day 3. `firestore.rules` 업데이트 + 배포**
> - `designated_drivers` 업데이트 규칙에 `platform`/`fcmTokenPlatform`/`fcmToken`/`lastLoginTime`/`status` 필드 `affectedKeys().hasOnly([...])` 허용 조항 추가
> - `customerInfo`도 동일 방식으로 `fcmTokenPlatform` 허용
> - `firebase deploy --only firestore:rules`
> - 이 시점부터 Kotlin 앱이 Firestore write 시 platform 필드 필수 아니지만 허용됨
>
> **Day 4. Flutter iOS 코드 보강 + TestFlight 빌드**
> - `Platform.isIOS ? "ios" : "android"` 로직으로 `platform` + `fcmTokenPlatform` 저장
> - Info.plist 권한 사유 문자열 검토
> - Codemagic Phase B(서명 + TestFlight)으로 빌드 + 업로드
>
> **Day 5. iOS 기사 테스터 초대 + 파일럿**
> - TestFlight Internal Tester로 iOS 기사 합류
> - 이 시점부터 `acceptanceEvents`에 `platform: "ios"` 이벤트가 쌓이기 시작
> - `monthlyStats` 다음 집계(매월 1일) 때 Android vs iOS 수락률 비교 가능
>
> **역순 시 회귀 시나리오**
> - Day 3(rules)을 Day 1(Kotlin) 전에 배포 시: Kotlin이 아직 platform 저장 코드 없으면 `affectedKeys` 허용 목록에 platform 추가 전에는 write 가능한 필드 목록 변화가 있을 수 있어 위험 (안전하게 Kotlin 먼저)
> - Day 4(Flutter iOS)을 Day 1(Kotlin) 전에 배포 시: Android 전체가 platform 필드 없이 새 iOS 기사만 있는 상태 → Android 기사들은 CF fallback "android"로 집계되지만 첫 Android 로그인 갱신 시 필드 새로 추가되는 혼란

- [ ] **Kotlin 기사앱 platform 필드 저장 추가**
  - 파일: `driver_app/app/src/main/java/com/designated/driverapp/DriverRepository.kt` (또는 FCM 토큰 등록·기사 로그인 지점)
  - 추가 필드: `"platform" to "android"`, `"fcmTokenPlatform" to "android"`
  - APK 재빌드 + 사내 테스트 APK 배포 (Play Store 심사 아님 — 현재 공개 배포 전 상태)

- [ ] **Flutter 기사앱 fcmTokenPlatform 메타 필드 저장** (의제 5)
  - FCM 토큰 획득 시 `Platform.isIOS ? 'ios' : 'android'` 함께 저장
  - `driver_app_flutter/lib/services/fcm_service.dart` 또는 토큰 등록 지점
  - Firestore `designated_drivers/{uid}.fcmTokenPlatform` + `.platform` 필드 신규

- [ ] **Flutter Platform.isIOS 분기 처리**
  - 현재 `driver_app_flutter/lib/`에 `dart:io` import 0건 → iOS 특이 로직 전무
  - 필요한 분기: FCM 알림 표시 방식 (iOS는 Time Sensitive), 권한 요청, 백그라운드 모드 등

- [ ] **Info.plist 권한 사유 문자열 한국어 최종 검토**
  - 현재 상태: 이미 5개 Privacy 사유 존재 (위치 2, 마이크, 음성인식, 사진) — 내용 적절성 검토
  - `driver_app_flutter/ios/Runner/Info.plist`
  - 필요 시 문구 수정 + App Store 심사 대응

- [ ] **backfill-platform.js 실행** (Day 2)
  - 선행 조건: Kotlin APK 재배포 완료
  - `export GOOGLE_APPLICATION_CREDENTIALS=<service-account>.json`
  - `node functions/scripts/backfill-platform.js`
  - 완료 후 Firestore Console에서 샘플 확인

- [ ] **firestore.rules 기존 규칙 affectedKeys 확장** (Day 3)
  - 선행 조건: backfill 완료
  - `designated_drivers` 업데이트 규칙에 `['fcmToken', 'fcmTokenPlatform', 'platform', 'lastLoginTime', 'status']` 허용
  - `customerInfo` 업데이트 규칙에 `['fcmToken', 'fcmTokenPlatform']` 허용
  - `firebase deploy --only firestore:rules`

**현재 서버 동작 (Phase 6 ① 결과)**: 기사 문서에 `platform` 필드 없으면 CF가 `"android"` fallback. 모든 기사가 실제 Android라 데이터 정확. iOS 기사 0명인 한 현재 상태로 운영 가능 — Phase 6 ② 작업은 iOS 출시와 묶어서.

**2026-04-18 확인**: 실제 운영 기사 0명 (테스트 단계). Firestore에는 테스트 계정만 존재. 이에 따라:
- **Day 2 backfill 실행 불필요** — 기존 대량 기사 문서가 없으므로. 신규 로그인 기사는 Day 1 코드로 자동 필드 저장. iOS 출시 직전 실제 기사 합류 시점에 재평가. 건너뛰어도 무해 (재로그인 과정에서 자동 채워짐)
- **Day 3 rules affectedKeys 제한도 지금 시급 아님** — 현재 rules는 owner/admin write 허용, 필드 제한 없음. 보안 강화가 주목적이라 iOS 출시 시점에 묶어서 진행
- **실질적 남은 작업은 Day 4 (Flutter iOS 코드 보강 + TestFlight 배포) 하나로 압축**

**Day 1 완료 기록 (2026-04-18)**:
- Kotlin 기사앱 3곳 수정 (Constants.kt + MyFirebaseMessagingService 2곳 + DriverViewModel 1곳)
- S22 (R5CT41TJZFP) + Flip4 (R3CT80K78NP) 재설치 (debug 서명 충돌로 기존 언인스톨 후 신규 설치, 데이터 초기화됨)
- 재로그인 후 Firestore `designated_drivers/{uid}`에 `platform: "android"` 필드 생성 확인 완료
- 커밋: `6e017fcc`
- S21+ (R3CR312MB1L)는 재설치 안 함 — 필요 시 동일 방법으로 진행

### ③ Bundle ID 실제 변경

- [ ] **Xcode PRODUCT_BUNDLE_IDENTIFIER 변경 (6곳)**
  - `driver_app_flutter/ios/Runner.xcodeproj/project.pbxproj`
  - `com.designated.driverAppFlutter` → `com.designated.driverapp.app`
  - Runner target Debug/Profile/Release + RunnerTests Debug/Profile/Release = 6곳

- [ ] **Firebase Console iOS 앱 신규 등록**
  - Firebase Console → 프로젝트 → 설정 → iOS 앱 추가 → `com.designated.driverapp.app`
  - `GoogleService-Info.plist` 다운로드 → `driver_app_flutter/ios/Runner/` 교체
  - 기존 `com.designated.driverAppFlutter` iOS 앱은 삭제 또는 보관

- [ ] **customerInfo 및 기타 Bundle ID 참조 검토**
  - `driver_app_flutter/ios/Runner/Info.plist`의 CFBundleDisplayName
  - URL schemes 등록된 경우 업데이트

### ④ App Store Connect API Key 발급

- [ ] **API Key 발급** (사용자 액션)
  - App Store Connect → Users and Access → Keys → **+** → "App Store Connect API"
  - Access: "App Manager" 또는 "Developer" 권한
  - 발급된 `.p8` 파일 다운로드 (1회만 가능) + Key ID + Issuer ID 기록

- [ ] **Codemagic에 API Key 등록**
  - Codemagic Teams → Integrations → Apple Developer Portal
  - Key ID, Issuer ID, .p8 파일 업로드

### ⑤ codemagic.yaml 서명 + TestFlight 업로드 추가

- [ ] **codemagic.yaml 업그레이드 (Phase B)**
  - 기존 `--no-codesign` → 정식 서명 빌드 (`flutter build ipa --release --export-options-plist=...`)
  - `integrations.app_store_connect` 추가
  - `publishing.app_store_connect.submit_to_testflight: true`
  - `integrations.app_store_connect.beta_groups: ['Internal Testers']`

- [ ] **APNs Authentication Key 발급 + Firebase 연동**
  - Apple Developer → Keys → APNs Authentication Key (.p8)
  - Firebase Console → 프로젝트 → Cloud Messaging → APNs Authentication Key 업로드
  - iOS FCM 푸시 경로 활성화

### ⑥ TestFlight 배포

- [ ] **첫 TestFlight 빌드 성공**
  - Codemagic 재빌드 → IPA 생성 → TestFlight 자동 업로드
  - Apple 처리 시간 ~10~30분
  - TestFlight 앱에서 Internal Tester로 테스트

- [ ] **테스터 Apple ID 수집 및 초대** (사용자 액션)
  - 기사 5~10명 Apple ID 이메일 수집
  - App Store Connect → TestFlight → Internal Testing → 초대
  - 테스터가 TestFlight 앱 설치 → 콜마당 기사 앱 설치

### ⑦ 실기기 파일럿 (Phase 1 완료 기준 8항목)

`flutter/driver_app/PLAN.md` 완료 기준 전체:

- [ ] 콜 사이클 1순환 iOS 실기기 검증 (콜매니저 Android ↔ iOS 기사앱)
- [ ] 로그인·자동로그인 검증 (flutter_secure_storage)
- [ ] FCM 6종 수신 + Delivery ACK (Time Sensitive 포함)
- [ ] 취소 경로 전 분기 + 기사 UX (Snackbar, 홈 이동)
- [ ] 정산 흐름 전수 (5결제 방식, 공식 검증, 통합 제출)
- [ ] Presence online/background/offline 전이
- [ ] Firestore 자동 오프라인 큐잉
- [ ] TestFlight Internal 파일럿 1주 운영 데이터 수집 (R1 발동 판단)

---

## 손님앱 Phase 2 (기사앱 안정화 후)

**현 상태**: 손님앱 Flutter 프로젝트 자체가 아직 없음 (`customer_app_flutter/` 미존재)

- [ ] Flutter 프로젝트 초기화 (`flutter create customer_app_flutter`)
- [ ] MVP 매핑 문서 27개 중 customer_app 9개 기반 실제 Dart 코드 작성
- [ ] 기존 Kotlin `customer_app/` 기능 1:1 포팅
- [ ] QR/Install Referrer 핵심 온보딩 플로우
- [ ] Phase 2 App Clip (Mac 도착 후)

---

## 참조 파일 맵

| 상황 | 참조 문서 |
|------|----------|
| MVP 매핑 스펙 (설계도) | `flutter/driver_app/MVP/` 10 문서, `flutter/customer_app/MVP/` 9 문서 |
| 서버측 작업 전체 | `flutter/SERVER_TASKS.md` |
| 교차 리뷰 P0/P1/P2 | `flutter/REVIEW_FINDINGS.md` |
| 의제 결정 누적 | `flutter/WORKING_DOC.md` |
| Phase 1 완료 기준 | `flutter/driver_app/PLAN.md` |
| 기능 인벤토리 3컬럼 | `flutter/FUNCTIONAL_INVENTORY.md` |
| 공유 로직 명세 | `flutter/SHARED_LOGIC.md` |
| Apple 식별자 | `memory/apple_ios_ids.md` |
| P0 재검증 결과 | `memory/p0_status_2026-04-17.md` |
| iOS 인벤토리 발견 | `memory/pending-work.md` AND-01~06 |

---

## 누가 봐도 알 수 있게: "지금 뭐 하고 있나"

**한 문장**: MVP 매핑 문서 + P0 패치로 **설계는 100% 완료**, iOS 인프라(Codemagic)는 **구축 완료**, 이제 **Phase 6 실제 코딩 보강**에 착수해야 하는 단계. 첫 항목은 서버측 CF 보강 (의제 4 apns 블록).
