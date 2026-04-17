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

### ① 서버측 보강 (CF) — 우선
**왜 먼저**: 배포만 하면 끝. 클라이언트 코드 영향 적음.

- [ ] **의제 4 FCM `apns` 블록 추가** (28곳+ CF 함수)
  - `oncallassigned`, `onCallStatusChanged`, `onCallCancelled*` 등 모든 FCM 송신 함수
  - `payload.aps.'content-available': 1` + `payload.aps.'mutable-content': 1`
  - 상세: `flutter/SERVER_TASKS.md` A 섹션

- [ ] **의제 11 `acceptanceEvents` 컬렉션 + 월 집계** (신규 모듈 ~300 LOC)
  - `recordAcceptanceEvent` 함수 구현 (P0-D.1 패치 완료된 스펙)
  - `onCallStatusChanged`에서 ACCEPTED/REJECTED 시 호출
  - `checkAssignedTimeout`에서 타임아웃 시 호출
  - R1_LOCKSCREEN 발동 판단용 iOS vs Android 수락률 집계
  - 상세: `flutter/SERVER_TASKS.md` B 섹션

- [ ] **Firestore 보안 규칙 갱신** (customerInfo fcmTokenPlatform 필드 write 허용)
  - 상세: `flutter/SERVER_TASKS.md` D 섹션
  - E.1(b) customerInfo 권한 취약점은 **별도 보안 설계 세션** 필요 (phone-uid 매핑)

### ② 기사앱 Flutter 코드 보강 (`driver_app_flutter/lib/`)

- [ ] **fcmTokenPlatform 메타 필드 저장** (의제 5)
  - FCM 토큰 획득 시 `Platform.isIOS ? 'ios' : 'android'` 함께 저장
  - `driver_app_flutter/lib/services/fcm_service.dart` 또는 토큰 등록 지점
  - Firestore `designated_drivers/{uid}.fcmTokenPlatform` 필드 신규

- [ ] **Platform.isIOS 분기 처리**
  - 현재 `driver_app_flutter/lib/`에 `dart:io` import 0건 → iOS 특이 로직 전무
  - 필요한 분기: FCM 알림 표시 방식 (iOS는 Time Sensitive), 권한 요청, 백그라운드 모드 등

- [ ] **Info.plist 권한 사유 문자열 한국어 최종 검토**
  - 현재 상태: 이미 5개 Privacy 사유 존재 (위치 2, 마이크, 음성인식, 사진) — 내용 적절성 검토
  - `driver_app_flutter/ios/Runner/Info.plist`
  - 필요 시 문구 수정 + App Store 심사 대응

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
