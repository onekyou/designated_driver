# Current Status — 날짜별 상태 누적

> MEMORY.md에서 분리된 상세 상태 이력. 날짜 내림차순 정렬 (최신이 위).

---

## 2026-04-19 (이어서) — 손님앱 Flutter Week 1~3 Chunk 1 완료 (Domain Foundation)

**커밋 2건**: `e59d13fa` + `b4d68ae6` (`manager-direct-drive`)

### 배경

Week 0~1 스캐폴딩 완료 후 실제 포팅 첫 chunk. MVP 매핑 문서는 중앙 `lib/domain/` 구조를 권장했으나, 실전 검증된 기사앱은 **feature-based Clean Architecture** (`lib/features/{f}/domain/entities/` + shared는 `lib/core/`) 사용. 손님앱도 이 검증된 패턴 미러링으로 확정.

리스크 최소화 위해 Week 1~3을 2 chunk 분산:
- **Chunk 1 (이번 세션)**: Converters 3 + Enums 3 + MVP 문서 경로 보정 (파일 상호 의존 적어 rollback 안전)
- **Chunk 2 (다음 세션)**: Freezed Models 6종 (CustomerCall 20필드, CustomerInfo 17필드 등). 인프라 검증된 상태에서 진입

### 1. MVP 문서 경로 보정 (`e59d13fa`)

`flutter/customer_app/MVP/` 20곳 경로 이관:
- `lib/domain/enums/` → `lib/core/domain/enums/`
- `lib/domain/models/converters/` → `lib/core/domain/converters/`
- `lib/domain/models/xxx.dart` → `lib/features/{call,profile,point,ad}/domain/entities/xxx.dart`
- `lib/domain/state/` → `lib/features/{call,point,profile,auth}/presentation/state/` (UI state per-feature)

실제 앱 동작 변경 0. Chunk 2 Models 작성 시 문서 참조와 코드 경로 일치 확보.

### 2. Domain Foundation 코드 작성 (`b4d68ae6`)

#### pubspec.yaml
- `dependencies.json_annotation: ^4.9.0`
- `dev_dependencies.json_serializable: ^6.8.0`
- Chunk 2 `@JsonSerializable` 인프라 선제 준비

#### `lib/core/domain/enums/` 3 파일
- **call_state.dart** — sealed class (Dart 3), 6 하위타입. Firestore 11종 → UI 6종 축소 매핑. `fromFirestoreStatus` / `displayName` / `isCancellable` / `isFinal`
- **customer_grade.dart** — enhanced enum 6 named 필드 (json/displayName/icon/pointRate/minCalls/colorArgb). Kotlin 1:1 포팅 + Dart 관용 메서드
- **transaction_type.dart** — enhanced enum 3 필드 (json/displayName/sign). Kotlin 단순 enum + Flutter UI 포맷팅 확장(formatAmount/isValidAmount)

#### `lib/core/domain/converters/` 3 파일
- **timestamp_converter.dart** — `JsonConverter<DateTime?, dynamic>`. Firestore Timestamp / int(ms) / ISO String 모두 대응
- **customer_grade_converter.dart** — null 폴백 bronze
- **transaction_type_converter.dart** — 미매칭 폴백 earn

#### `test/core/domain/enums/` 3 파일 (34 assertions)
- Firestore status 매핑 / round-trip / 경계값 / 부호 포맷 전수 검증

### 검증
- `flutter pub get` → 145 deps 해결
- `flutter analyze` → 0 issues
- `flutter test` → **35/35 PASS** (34 신규 assertions + 1 기본 widget_test)
- `grep -rc "lib/domain/" flutter/customer_app/` → **0 hits**

### Chunk 2 진입 조건 충족

다음 세션에서 착수:
1. Freezed 6 모델 (`lib/features/{call,profile,point,ad}/domain/entities/`) + `@JsonSerializable`
2. `build_runner build --delete-conflicting-outputs` → `.freezed.dart` + `.g.dart` 생성
3. CustomerCall Firestore 3중복 필드 custom fromFirestore factory
4. CustomerInfo 2경로 저장 구조 (`customers/{uid}` + `customerInfo/{phone}`)
5. Firestore 샘플 round-trip 테스트

**플랜 문서**: `C:\Users\kala1\.claude\plans\jazzy-swinging-meadow.md`

---

## 2026-04-19 손님앱 Flutter Week 0~1 스캐폴딩 완료 + P0 enum 드리프트 해소

**두 커밋**: `1e909768` → `69295495` (`manager-direct-drive`)

### 1. MVP 문서 정리 (`1e909768`)

REVIEW_FINDINGS.md P0 B.1~B.3 재검증 결과:
- **B.1 AuthUiState 9필드**: MODELS.md:1030 이미 정의 완료 (사실상 해소 상태)
- **B.2 CustomerPoints.calculateEarnPoints**: MODELS.md:448 `_();` + :481 메서드 이미 구현
- **B.3 grade.jsonValue**: NOTIFIERS.md:257 이미 `grade.json`으로 통일

실제 미해결 드리프트 발견 → 이번 세션에서 해소:
- `ENUMS.md §2` CustomerGrade: Kotlin 원본의 `icon` (이모지) + `color` (ARGB) **누락**
- `MODELS.md §4/§5`: CustomerGrade/TransactionType 중복 정의, 속성명 `jsonValue` vs `json` 드리프트

해결:
- ENUMS.md §2: named constructor + 6필드 (json/displayName/icon/pointRate/minCalls/colorArgb) + 메서드 (fromCallCount/fromString/toJson/calculatePoints/nextGrade/callsToNext)
- MODELS.md §4/§5: 본문 삭제 → ENUMS.md 포인터로 축약 (단일 원본 강제)
- NOTIFIERS.md `grade.json` 참조와 정합

### 2. Flutter 프로젝트 스캐폴딩 (`69295495`)

**핵심 결정 (사용자 확정)**:
- iOS Bundle ID: `com.designated.customer.app` (Android와 통일, 의제 2 결정과 일치)
- 브랜치: `manager-direct-drive` 계속
- 디렉토리: repo root `customer_app_flutter/` (driver와 대칭)

**스캐폴드**: `flutter create --org com.designated --project-name customer_app_flutter --platforms=android,ios` (Flutter 3.41.6 / Dart 3.11.4)

**변경 상세**:
- pubspec.yaml: FCM.md §0 기준, firebase analytics/crashlytics/remote_config + go_router + mobile_scanner + in_app_update 추가. 드라이버 대비 firebase_database/flutter_foreground_task/qr_flutter 제외 (Presence/Foreground Service/QR 생성 불필요)
- iOS Podfile: platform 15.0 + post_install IPHONEOS_DEPLOYMENT_TARGET 15.0
- iOS Info.plist: CFBundleDisplayName=손님앱, 권한 한글 4종 (Location WhenInUse only / Camera QR 신규 / Microphone R2 / SpeechRecognition R2). UIBackgroundModes=fetch+remote-notification (location 제거, 의제 8)
- iOS PrivacyInfo.xcprivacy 선제 작성 (TestFlight 대비)
- Android build.gradle.kts: applicationId=com.designated.customer.app, Compose 블록 제거 (LockScreen 없음), google-services plugin
- AndroidManifest.xml: 손님 권한 subset (Camera/Location WhenInUse 추가, FOREGROUND_SERVICE/FULL_SCREEN_INTENT/RECEIVE_BOOT_COMPLETED 제외)
- pbxproj Bundle ID 6곳 치환

**검증**:
- flutter pub get: 143 deps 해결
- flutter analyze: 0 issues
- flutter test: 1/1 PASS (기본 widget_test)
- flutter build apk --debug: app-debug.apk 생성 (24분, JAVA_HOME=Android Studio jbr)

**미완료 (후속)**:
- iOS `GoogleService-Info.plist`: Firebase Console에서 Bundle ID `com.designated.customer.app` iOS 앱 신규 등록 후 다운로드 배치 (사용자 수동)
- Codemagic `customer_app_flutter` 워크플로 추가 (별도 세션)
- Week 1~3 실제 포팅 (6 Freezed 모델 + 4 Riverpod Notifier + 11 화면)

**다음 세션 시작점**:
- Week 1~3 Phase 1 포팅 시작 (MODELS.md §1~6 Freezed 모델부터)
- 병렬: 사용자가 Firebase Console에서 iOS 앱 등록 + GoogleService-Info.plist 다운로드

**플랜 문서**: `C:\Users\kala1\.claude\plans\jazzy-swinging-meadow.md`

---

## 2026-04-18 재개 세션 — 작업 없이 종료 (다음 세션 시작점 재정리)

**세션 요약**: git pull 동기화 완료. 어제 기록한 후보 4건 중 사용자 확인을 거쳐 아래와 같이 재분류. 실제 코드/배포 변경 없음.

**직전 커밋**: `27b02e18` Phase B 선작업 (B-5 LockScreen iOS 가드 + Runner.entitlements + pbxproj CODE_SIGN_ENTITLEMENTS)

**다음 세션 시작점 (재분류)**:

1. **[보류] Emulator 시나리오 11 실제 실행** (30~60분) — Codemagic Build #4에서 `Platform.isIOS == true` + payload "ios" 저장이 이미 런타임 확증됨. 시나리오 11 실행은 **중복 확증** 성격이므로 필수 아님. 필요 시 `cd functions && npm run build` → `firebase emulators:start --only functions,firestore` → `npx ts-node functions/test/scenarios/11-acceptance-events.test.ts`.
2. **[보류, 사용자 결정 2026-04-18]** 재배차 UI rejectedByDriver 필터링 — 사용자가 "지금 작업할 필요없어"로 명시. dead data 상태는 유지되지만 이번 세션에서 다루지 않음. 운영 데이터 누적 후 필요성 재판단. 상세: `memory/rejected_by_driver_dead_data.md`
3. **[대기] 사용자 수동 액션**: ASC API Key + APNs Authentication Key 발급 (Apple Developer Portal)
4. **[대기] 아이폰 실기기 도착 시**: Phase B RFC(`memory/phase6_day4_phaseB_rfc.md`) 기반 B-1/B-2 적용 + Codemagic Phase B 워크플로우 추가

**즉시 착수 가능한 코딩 작업 없음** — 모두 외부 의존(기기 도착/사용자 수동) 대기 혹은 보류.

**손님앱 Flutter 초기화는 별도 세션에서 진행 중** (이 세션에서 다루지 않음).

---

## 2026-04-18 저녁 — Codemagic iOS Simulator 검증 PASS (Phase A 완전 확증)

**Codemagic Build** `69e285293e9d20cb0c3bd492` (Build #4, commit `7a06cba9`)
- Status: finished, Duration 15m 53s, Machine Mac mini M2
- 전 스텝 PASS:
  - Flutter unit test (payload helper) 10s — 3/3 PASS
  - Boot iOS Simulator 1m 4s — boot fix(`7a06cba9`) 작동 확인
  - **Flutter integration_test on iOS Simulator 7m 57s** — `Platform.isIOS == true` + payload "ios" 저장 런타임 확증
  - Flutter build iOS (no codesign) 4m 13s — 새 Bundle ID(`com.designated.driverapp.app`) + 신규 GoogleService-Info.plist 정상 컴파일
- Artifact: Runner.app.zip 15.17 MB

**이정표 의미**
- Day 4 Phase A 런타임 확증 완료. Platform.isIOS 분기가 실제 iOS 환경에서 "ios" 문자열 산출
- Bundle ID 변경(Xcode + Firebase Console) 통합 빌드 검증
- 아이폰 실기기 없이도 검증 가능한 영역은 전부 확증. Phase B(실기기 전용 분기)만 남음

**남은 것**
- Phase 6 ④⑤ ASC API Key / APNs Authentication Key 발급 (사용자 수동)
- Phase 6 ⑥ TestFlight 배포 (아이폰 도착 후)
- Phase 6 ⑦ 손님앱 Flutter 포팅 (병렬 가능, Mac 무관)
- Day 4 Phase B (iOS 분기 4곳 + Info.plist 심사 대응)

---

## 2026-04-18 오후 — Phase 6 ② Day 4 Phase A 완료 (Flutter platform 저장 + iOS Sim 검증 인프라)

**commit `fab1050c`** — 8 files (+97/-7). Kotlin `6e017fcc`의 platform/fcmTokenPlatform 저장 로직을 Flutter 기사앱으로 이식 + Codemagic iOS Simulator integration_test 경로 신규 구축.

**Phase A/B 분리 배경**: 아이폰 실기기 2-3일 후 도착 → 맹목적 Platform.isIOS 분기 확장은 Android 회귀 위험. 따라서 저장 필드만 Phase A, 기타 분기(권한/알림/Foreground Service/잠금화면)는 Phase B로 이관.

**핵심 산출물**:
- `buildTokenUpdatePayload` 헬퍼 (`lib/core/utils/fcm_token_payload.dart`) — `isIos` 파라미터 주입으로 Platform 의존성 모킹 가능
- 3케이스 단위 테스트 (Windows 로컬 3/3 PASS)
- iOS Simulator integration_test (`Platform.isIOS == true`일 때 payload가 "ios" 포함 실행 검증)
- codemagic.yaml에 3 스텝 추가: Flutter unit test → Boot iOS Simulator → integration_test (+2.5분 빌드)

**팀원 3명 리뷰 PASS_WITH_NOTES**:
- flutter-expert: AppConstants 섹션 정리(P1) 이번 커밋 반영. dart:io scope 정답
- kotlin-expert: **Day 3 rules affectedKeys 6필드 경고** — `fcmTokenUpdatedAt` 필수. 빠뜨리면 Flutter 토큰 갱신 전량 거부
- firebase-analyst: CF 경로(`acceptanceEvents.ts:35 raw === "ios"`) 완전 호환. 에뮬레이터 시나리오 2개 설계 완료

**수동 액션 필요**:
- Codemagic `triggering.events: []`이라 자동 트리거 안 됨 → Codemagic UI에서 "Start new build" 실행
- 빌드 결과로 iOS Simulator 런타임 검증 확보

**Phase B 진입 조건**: 아이폰 실기기 도착 + Codemagic Phase A 빌드 성공

---

## 2026-04-18 오전 — Phase 6 ① 서버측 CF 보강 완료 (FCM apns + acceptanceEvents + rules 신규)

**성과**: iOS Flutter 기사앱 출시 전 서버 준비 완료. **클라이언트 코드 영향 0**. 프로덕션 CF 배포 후 실기기 회귀 없음 확인.

### 작업 내용 (커밋 `745070fa`, 21 files, +1271/-437)

**A. FCM apns 블록 (34곳 audit, 31곳 수정)**
- `functions/src/utils/fcmPayload.ts` 헬퍼 신규 (buildFcmPayload / buildMulticastFcmPayload, time-sensitive·active 2 레벨)
- index.ts 22곳 + handlers/settlement.ts 2곳: 헬퍼 일괄 적용
- 예외 7곳: 기존 최상위 `notification` 유지 + apns 수동 추가 (POINTS_EARNED, call_status_update 고객용, DRIVER_APPROVAL_REQUEST, new_customer, CALL_DETECTOR_CRASH, SETTLEMENT_SUBMITTED, SETTLEMENT_DISCREPANCY — Android Kotlin 호환)
- 스킵 2곳: 재전송 저장 payload 재사용, testFcmMessage 테스트 함수
- **data 블록 100% 보존 → Android 회귀 0**

**B. acceptanceEvents 집계 (R1_LOCKSCREEN 발동 기준)**
- `functions/src/analytics/acceptanceEvents.ts` + `aggregateMonthly.ts` 신규
- onCallStatusChanged ASSIGNED→ACCEPTED/REJECTED 분기 신규 추가 (dead data였던 `rejectedByDriver`의 첫 소비처)
- checkAssignedTimeout 오프라인/3분 타임아웃 2곳 훅
- aggregateMonthlyStats 스케줄러 배포 (매월 1일 00:00 KST, Android/iOS 각 100건 이상 + 5%p 차이 시 R1 경보)
- `functions/scripts/backfill-platform.js` 1회성 스크립트 작성 (실행은 Phase 6 ②)

**D. firestore.rules (신규 블록만)**
- acceptanceEvents + monthlyStats 규칙 추가 (기존 규칙 건드리지 않음)
- designated_drivers/customerInfo 기존 규칙의 affectedKeys·fcmTokenPlatform 필드 허용은 Phase 6 ②로 이관 (Kotlin platform 배포 순서 의존성 회피)

**메모리**
- `memory/rejected_by_driver_dead_data.md` 신규: 기사앱 write 3곳 / consumer 0 / 재배차 UI 필터링 후속 이슈

### 검증

- **TypeScript strict 컴파일**: 0 오류
- **Firebase Emulator 8개 시나리오**: 5 PASS / 3 FAIL (1, 2, 4 = 정산 세션 검증 타임아웃)
- **git stash로 베이스라인 재현**: 동일하게 5 PASS / 3 FAIL → 실패 3건은 **Windows + Emulator + v2 함수의 CF 트리거 대기열 지연**이 진짜 원인 (베이스라인에서도 존재하는 인프라 문제, 내 변경 무관 확증)
- **프로덕션 배포**: `firebase deploy --only firestore:rules` + `--only functions` 성공. aggregateMonthlyStats 신규 create, 기존 47개 함수 update 전부 successful
- **실기기 회귀 (S21+/S22/Flip4)**: 크게 문제 없음. S22에서 "알림만" 표시되고 연속 알림·FullScreenIntent 미동작 현상은 **서버 변경 무관** — Android 14+ FullScreenIntent 권한 / 배터리 최적화 / 알림 채널 설정 이슈 (Flip4는 정상)

### 남은 작업

- **Phase 6 ② (다음 세션)**: Kotlin 기사앱 platform 필드 저장 + Flutter iOS 코드 보강 (fcmTokenPlatform, Platform.isIOS, 권한 문구) + backfill-platform.js 실행 + firestore.rules 기존 규칙 affectedKeys 제한
- **후속 이슈**: Call Manager/Detector 재배차 UI에서 rejectedByDriver 기반 기사 필터링 (dead data 완전 해소)
- **S22 기기 세팅 확인 (사용자 액션)**: FullScreenIntent 권한 / 배터리 최적화 / 알림 채널 중요도 Flip4와 동일 설정

---

## 2026-04-17 — Apple 생태계 진입 + iOS 빌드 인프라 구축 (Phase 6 코딩 진입 직전)

**⚠️ 중요 프레이밍**: 오늘 "빌드 성공"은 **기존 3/27 코드가 iOS 컴파일된다는 껍데기 검증**이지, **iOS 대응 코드 보강이 끝난 것이 아님**. 실제 iOS 출시까지는 Phase 6 코딩 보강(7개 항목)이 남아있음. 상세: `memory/phase6_coding_remaining.md`

### 오늘 실제 달성한 것

**① Apple Developer / App Store Connect 설정**
- Apple Team ID: `VCJD377MAU`
- Bundle ID (App ID 등록): `com.designated.driverapp.app` (Android applicationId와 통일)
- Capabilities: Push Notifications 활성
- App Store Connect 앱 `콜마당 기사` 생성 (기본 언어 한국어, SKU `driverapp-ios-001`)

**② Codemagic 빌드 인프라**
- 레포 연결 완료 (`manager-direct-drive` 브랜치)
- `codemagic.yaml` 레포 루트 배치 (Build ID `69e2395f...`)
- Workflow: `Driver iOS Test Build (Phase A - No Codesign)` — `mac_mini_m2`, `flutter: 3.41.6`, `xcode: latest`, `max_build_duration: 90`
- **첫 빌드 45분 타임아웃** → max_build_duration 45→90 상향 → **재빌드 10분 37초 성공**
- Artifact: `Runner.app.zip` 15.16 MB (⚠️ 서명 없음, 아이폰 설치 불가)

**③ 스펙 문서 정리 (.md 파일만 — 런타임 영향 없음)**
- P0 9건 재검증 (`memory/p0_status_2026-04-17.md`)
- A.1/A.2/A.3/B.3/C.1 = MVP 매핑 문서(`flutter/*/MVP/*.md`)의 Dart 코드 예시 수정
- D.1 = CF 스펙 (`flutter/SERVER_TASKS.md`) 수정 — **실제 CF 배포는 아직 안 함**
- E.1(a) = 재검증으로 이미 정확 확인

**④ 메모리 정리**
- `memory/apple_ios_ids.md`: Apple 식별자 기록
- `memory/user_ios_experience.md`: 사용자 iOS 무경험 프로필
- `memory/p0_status_2026-04-17.md`: P0 재검증 결과
- `memory/phase6_coding_remaining.md`: **Phase 6 남은 코딩 작업 체크리스트 (신규)**

### 오늘 커밋 6건 (`manager-direct-drive` 푸시)

```
c60dec3a docs(memory): 2026-04-17 상태 기록 (1차, 이후 정정 커밋 추가됨)
64814dc8 fix(codemagic): max_build_duration 45 → 90
9fabf885 fix(codemagic): codemagic.yaml 레포 루트 이동
a943a1e5 fix(flutter): P0 A.1/A.2/A.3/B.3/C.1 (.md 스펙 문서) 패치
d759fdbe feat(codemagic): Phase A 파이프라인 + iOS 15.0 타깃 상향
04e54502 docs(flutter): P0 D.1 + Apple ID 등록 정보 메모리화
```

### P0 진행 상태 (정확한 해석)

| # | 대상 파일 | 상태 | 의미 |
|---|----------|------|------|
| A.1/A.2/A.3 | `flutter/driver_app/MVP/NOTIFIERS.md` | ✅ 스펙 수정 | 미래 참조용 문서 정리 |
| B.3 | `flutter/customer_app/MVP/NOTIFIERS.md` | ✅ 스펙 수정 | 손님앱 신규 포팅 시 참조 |
| C.1 | `flutter/driver_app/MVP/MODELS.md` | ✅ 스펙 수정 | 미래 참조용 |
| D.1 | `flutter/SERVER_TASKS.md` | ✅ CF 스펙 수정 | **실제 CF 배포는 ❌ 아직** |
| E.1(a) | `flutter/SERVER_TASKS.md` | ✅ 이미 정확 | — |
| B.2 | — | ✅ false positive | 이미 정의됨 (MODELS.md:481) |
| B.1 | `flutter/customer_app/MVP/MODELS.md` | 🟡 4/9 확인 | 나머지 5 필드 검증 대기 |
| E.1(b) | — | ❌ 미해결 | 별도 보안 설계 세션 필요 (phone-uid 매핑) |

### 다음 세션 우선순위 (Phase 6 코딩)

**"MVP 매핑 + P0 패치 완료 → 코딩 진입"이 원 계획**. 아직 착수 안 한 작업:

1. **서버측 (CF) 보강** — 배포만 하면 끝. 의제 4 apns + 의제 11 acceptanceEvents
2. **기사앱 iOS 코드 보강** — fcmTokenPlatform, Platform.isIOS 분기, 권한 문구
3. **Bundle ID 실제 변경** — Xcode + Firebase Console + GoogleService-Info.plist
4. **App Store Connect API Key** — Codemagic 서명 권한
5. **codemagic.yaml Phase B** — 서명 + TestFlight 업로드
6. **TestFlight 배포** — 기사 테스터 초대 + 실기기 파일럿
7. **손님앱 신규 포팅** — Phase 2 (기사앱 안정화 후)

상세 체크리스트: `memory/phase6_coding_remaining.md`

### 사용자 측 준비 (병렬 가능)
- 앱 아이콘 1024×1024 PNG no alpha
- 스크린샷 6.5"/5.5" iPhone
- 앱 설명 한국어 (프로모 250자 + description 4000자)
- 개인정보 정책 URL (호스팅 필요)
- 기사 테스터 5~10명 Apple ID 수집

---

## 2026-04-16 — Flutter 전환 결정 + Swift 네이티브 보류

**방향 전환** (master plan: `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md`)
- 배경: Mac 공수 1~2개월 지연 → 4/15 Swift 네이티브 계획 보류, Codemagic 기반 Flutter로 iOS 출시
- 기사앱: 기존 `driver_app_flutter/` Phase 1~5 재활용 + iOS 빌드만 추가
- 손님앱 Phase 1: Kotlin → Flutter 신규 포팅 (같은 applicationId로 Play Store 업데이트)
- 손님앱 Phase 2: Mac 도착 후 Swift App Clip 하이브리드 추가

**4/16 하루치 작업 (19 커밋, `70bd6a19`~`c290d198`)**
- 팀 구성: kotlin-expert + flutter-expert 2인 (`.agent-teams/flutter-expert.md` 신규)
- 의제 14개 중 **12개 결정 완료** — 미결: 의제 2(Bundle ID), 의제 3(Codemagic)
- MVP 매핑 문서 19개 (driver 10 + customer 9) + `SERVER_TASKS.md` + `FUNCTIONAL_INVENTORY.md`
- **마지막 커밋 `c290d198`**: `flutter/REVIEW_FINDINGS.md` — 교차 리뷰 P0 9건 (코딩 진입 전 수정 필수)

**사용자 측 Week 0 준비**
- 완료: Apple Developer + Codemagic 계정 + App Store Connect 앱 생성
- 남은 2가지: 앱 메타데이터(아이콘/스크린샷/설명/개인정보 URL), 기사 테스터 5~10명 Apple ID

**다음 스텝**: P0 9건 패치 → 의제 2/3 결정 → Codemagic 첫 iOS 빌드 → TestFlight Internal

**Swift 자산 (`ios/`)**: 폐기 아님, Flutter MVP 실패 시 복귀용 보류

---

## 2026-03-27 — Flutter 기사앱 Phase 1~5 구현 완료 (`940d5e6f`)

- Phase 1: 구조정리 (61파일 삭제) + Firestore 경로 수정 + DriverWorkflowNotifier
- Phase 2: 정산 시스템 (SettlementCalc + 일일마감 + 이월금)
- Phase 3: Presence + ForegroundService + LockScreenActivity + 주소검색
- Phase 4: FCM 6종 완전 대응 + Delivery ACK
- Phase 5: 회원가입/비밀번호찾기/콜상세/QR추천
- 빌드 환경: Flutter 3.41.6, AGP 8.7, Kotlin 2.1, Gradle 8.9
- Flutter SDK: `/c/Users/kala1/flutter_sdk/`
- 패키지명: `com.designated.driverapp.app`
- 남은 작업: Phase 6 (전환 전략 결정) + 실기기 테스트

---

## 2026-03-24 — 손님앱 콜 취소 소통 개선 완료 (`79c3860c`)

- 손님앱: ACCEPTED/PREPARING 상태에서도 취소 가능 (기존 WAITING/ASSIGNED만)
- 기사앱: cancelTrip() HOLD → CANCELLED_BY_DRIVER (확정 취소, 재배차 없음)
- 3개 앱 모두: 취소 시 일반 알림(notification bar) 표시
- 기사앱: 취소 시 Snackbar "고객이 콜을 취소했습니다" + 알림 클릭 시 홈 이동 (검은 화면 수정)
- CF: 취소 시 매니저 FCM 전송 + 기사 FCM에 title/body 추가
- Firestore 보안 규칙: 고객 취소(CANCELLED_BY_CUSTOMER) + 기사 취소(CANCELLED_BY_DRIVER) 허용 추가

**손님앱 익명인증 복원**: Phone Auth 필수 → Anonymous Auth 자동 로그인으로 변경

**배포 완료**
- CF `onCallCancelledByDriver` (title/body 추가 + 매니저 FCM)
- Firestore 보안 규칙 (고객/기사 취소 허용 확장)

---

## 2026-03-18 — 시뮬레이션 테스트 완료

- 순차 흐름 7일 335콜 + 10사무실 공유콜 127CP + 피크 부하 100건 ALL PASS
- 3단계 실기기 테스트 대부분 완료
  - ⏳ 전화감지 3대 중복검증 → 파일럿에서 검증
  - ⏳ 네트워크 오프라인 시나리오 → 파일럿 1주차 이후

---

## Firestore 상태 (2026-03-18 기준)
- provinces(16) + admins(1) + 사무실 `nEkf0X9g3LZtRX94Mrzu`
- VIP 사무실 `0evNgfgm3xdq0v3VTFYK`는 삭제됨
- 상세: `memory/firestore-incident.md`
