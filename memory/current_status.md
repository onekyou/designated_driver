# Current Status — 날짜별 상태 누적

> MEMORY.md에서 분리된 상세 상태 이력. 날짜 내림차순 정렬 (최신이 위).

---

## 2026-04-23 — 정산 이월금(carryOver) 중복 합산 fix 커밋 + 1004 사무실 실기기 테스트 준비

**커밋**: `607c6bb7 fix(settlement): 이월금(carryOver) 중복 합산 제거`

### 배경
여러 세션에 걸쳐 미커밋 상태로 남아있던 정산 관련 5 파일의 WIP 를 파악·검증·커밋한 세션. 상황 분석 플랜: `C:\Users\kala1\.claude\plans\reactive-dazzling-wind.md`

### 커밋된 파일 5종
- `call_manager/.../SettlementViewModel.kt` — `transferCarryOver` (balance+todayUnpaid → balance 단독), `confirmDailySettlement` (TRANSFERRED 상태 유지)
- `call_manager/.../AllTripsScreen.kt` — `managerFinalized` 판정 도입 (TRANSFERRED/SETTLED/isConfirmed)
- `call_manager/.../DriverSummaryScreen.kt` — `isConfirmedOrLater` / `hasSubmitted` 단계 분기
- `driver_app/.../HistorySettlementScreen.kt` — `managerFinalized` 시 adjustedDeposit/usedFromCarryOver=0
- `driver_app/.../DriverViewModel.kt` — carryOverListener 에서 `status=TRANSFERRED` 시 calculatedCarryOver masking 제외

### 공통 설계 원리
`balance` 에 오늘분이 이미 포함된 확정 시점(TRANSFERRED / SETTLED / CONFIRMED / PENDING_CONFIRM+balance>0)을 판정해 재가산·재공제를 단락. 오늘분이 두 번 더해지는 다섯 경로를 한 번에 차단.

### 실기기 준비 상태
- **설치 완료**: driver_app → S21+ / S22 / Z Flip4, call_detector → S21+
- **테스트 사무실**: 1004@naver.com 사장 — `provinces/gyeonggi/cities/yangpyeong/offices/RUbeBEvGGYP5wMhJHhMF`
- **제로 베이스**: `functions/scripts/reset-office-1004.js --execute` 로 calls(10)+settlementSessions(1) 삭제 + 2 기사 carryOver=0/dailySettlement 삭제. admins/settings/managerTokens 유지
- **로컬 캐시**: 옵션 B 선택 (Firestore 만 정리, pm clear 없음)

### 미커밋 남은 3 시나리오 검증 대기
1. 정상: 기사 콜 1건 완료 → submit → 매니저 정산확인 → 이체 → 수령
2. 스킵: submit → 정산확인 skip → 바로 이체
3. 누적: 어제 수령완료 상태에서 오늘 새 submit (하루 뒤 가능)

### 부수적 관찰 기록
`AllTripsScreen` 의 `todayUnpaidByDriver` 는 Room DB 기반 `trips` 에서 파생되므로, **기사 콜 완료 즉시는 실시간 반영 안 됨** (carryOverList/dailySettlementList 는 Firestore 리스너로 실시간). 기사 업무마감 시점에 집계 재동기화되어 최종 숫자가 맞춰진다. 의도된 Local-First 절충, 기능상 문제 없음 (사용자 판정).

### 확인된 기존 제약
- call_detector + call_manager 동일 기기 공존 시 배차 팝업 충돌 (`memory/session_2026-04-20_evening_test.md` 기록). 이번 테스트에서 S21+ 에 두 앱이 모두 설치되어 있으므로 주의 필요

---

## 2026-04-19 (이어서⁵) — 손님앱 Flutter Week 1~3 Chunk 5 완료 (ProfileNotifier 본체 + FCM 저장 활성화)

**커밋**: (Chunk 5 단일 commit + 메모리 commit)

### 결과물

#### 신규 파일
- **profile_notifier.dart** (~240 LOC) — loadProfile / updateProfile (2a 리팩토링) / reloadOfficeInfo / acceptTerms / updateHomeAddress + ref.listen(authNotifierProvider) uid watch
- **profile_notifier_test.dart** (~430 LOC, 18 cases)

#### 개편
- **profile_ui_state.dart**: `error` + `isSaving` 2필드 추가 → build_runner freezed 재생성
- **auth_notifier.dart** `_registerFcmToken`: Firestore 저장 블록 활성화. **순환 의존 회피 — SharedPreferences 직접 읽기** (ProfileNotifier 경유 시 CircularDependencyError 발생). 사무실 정보 source of truth 는 SharedPreferences
- **auth_notifier_test.dart**: test #10 "저장 안 함" → "저장 함" 으로 업데이트 + #10b "사무실 정보 없음 스킵" 신규

### 테스트 18 cases (+Chunk 4 #10 업데이트)
1~3: SharedPreferences office 로드/리로드
4~7: loadProfile (문서 있음/없음/빈 uid/사무실 정보 없음)
8~10: updateProfile (성공/fcmToken null/인증 부족)
11: **호출 순서 검증** — customers 선저장 → customerInfo 후저장 (saveCallOrder 리스트)
12: **부분 실패** — `_FailingCustomerInfoProfileNotifier` subclass 로 customerInfo 쓰기 예외 주입 → customers 는 저장됨 + false 반환 (rollback 없음 — Kotlin 일치)
13~14: acceptTerms (4키 저장 + 매번 갱신)
15: updateHomeAddress
16~18: uid watch (null→값 / A→B / listener 등록)

### 검증 3관문 통과
- `flutter analyze`: 0 issues
- `flutter test`: **111/111 PASS** (기존 92 + 신규 20 (ProfileNotifier 18 + AuthNotifier #10b) - smoke 1 제거 경로 정리)
- `flutter build apk --debug`: **17.4s** (캐시 활용)

### 결정 기록
1. **순환 의존 회피**: `AuthNotifier._registerFcmToken` 에서 `ref.read(profileNotifierProvider)` 대신 `ref.read(sharedPreferencesProvider)` 직접 사용. ProfileNotifier 도 동일 SharedPreferences 로부터 읽으므로 source of truth 일관성 유지. **교훈**: Notifier 간 상호 참조는 순환 위험. shared infra (SharedPreferences / Secure Storage) 를 middle layer 로 두는 편이 안전
2. **호출 순서 검증**: `saveCallOrder` 공개 리스트 (`@visibleForTesting`) 로 append → 테스트 쉬움. 비용 최소
3. **부분 실패 테스트**: ProfileNotifier subclass (`_FailingCustomerInfoProfileNotifier`) 로 `saveCustomerInfoForTest` 오버라이드. provider override 로 주입. FakeFirestore 한계 우회
4. **CF `checkPhoneNumberDuplicate` 제외**: 복원 트리거 플랜 파일 명시 — 30일 경과 OR 손님 100명 초과 OR 고객문의 1건. 복원 위치: AuthNotifier.verifyPhoneNumber 직전 또는 ProfileNotifier.updateProfile 초입

### 범위 밖 메모 (Chunk 6+)
- FCM onTokenRefresh rotate 실제 emission 테스트 (Stream.fromIterable 활용)
- Chunk 8 OfficeCodeScreen 체크리스트: QR/코드 입력 → SharedPreferences 저장 → `profileNotifierProvider.notifier.reloadOfficeInfo()` **필수 호출**
- `designated_customer_flutter` 폴더 조사 — 과거 flutterfire configure 흔적 가능성

### 다음 세션 (Chunk 6)
1. **PointNotifier 본체** (~200 LOC): customerPoints/{phone} 실시간 리스너 + earn/use/cancel + PointService.kt 이식
2. FCM onTokenRefresh emission 실제 테스트
3. `designated_customer_flutter` 폴더 1차 조사

**플랜 문서**: `C:\Users\kala1\.claude\plans\jazzy-swinging-meadow.md`

---

## 2026-04-19 (이어서⁴) — 손님앱 Flutter Week 1~3 Chunk 4 완료 (AuthNotifier 본체 + mocktail 테스트)

**커밋**: (Chunk 4 단일 commit)

### 결과물

#### 신규 파일 (4)
- `lib/core/utils/fcm_token_payload.dart` — `buildCustomerFcmPayload(token, phoneNumber, isIos?)` 헬퍼 (기사앱 패턴 재사용). 의제 5 fcmTokenPlatform 메타 포함. Platform.isIOS 주입 가능
- `lib/features/auth/presentation/notifiers/phone_formatter.dart` — `formatKoreanPhoneNumber` top-level (010 → +82, PhoneAuthViewModel.kt:127-135 이관)
- `test/core/utils/fcm_token_payload_test.dart` — 4 cases (isIos 양방향, 4필드 존재, phoneNumber 원본 보존)
- `test/features/auth/presentation/notifiers/phone_formatter_test.dart` — 4 cases (hyphen/공백/이미 +82 edge)

#### 개편 파일
- **auth_notifier.dart** 껍데기 → 본체 (+180 LOC)
  - 생성자에서 `_initAnonymousAuth()` + `_subscribeFcmToken()` 자동 호출 2줄 추가
  - `_initAnonymousAuth` (MainActivity.kt:397-411): currentUser 상속 우선 + null 시 signInAnonymously + 실패 시 error state (Crashlytics 생략, 기사앱 선례 따름)
  - `sendVerificationCode` (PhoneAuthViewModel.kt:43-87): verifyPhoneNumber + 4 콜백 (completed/failed/codeSent/timeout)
  - `verifyCode` + `_signInWithCredential` (PhoneAuthViewModel.kt:89-124): **linkWithCredential 핵심** (Anonymous uid 보존, Kotlin 원본 대비 개선)
  - `_handleCredentialAlreadyInUse` (§3.5): credential-already-in-use fallback → signInWithCredential 전환
  - `_subscribeFcmToken` + `_registerFcmToken` + `initialFcmTokenRegistration`: phone 체크까지 구현, Firestore 저장 블록은 **Chunk 5 ProfileNotifier 완성 후 활성화** (TODO 주석)
- **auth_notifier_test.dart** (smoke 흡수 + 본체 테스트 11 cases)
- **android/app/build.gradle.kts**: ndkVersion 27.0 → 28.2.13676358 (jni plugin 요구)
- **pubspec.yaml**: dev_dependencies 에 `mock_exceptions: ^0.8.2` 추가 (firebase_auth_mocks 예외 주입용)

#### 삭제
- `test/features/auth/presentation/notifiers/auth_notifier_smoke_test.dart` (본체 테스트로 흡수)

### 검증 3관문 통과
- **flutter analyze**: 0 issues
- **flutter test**: **92/92 PASS** (기존 76 + 신규 16 — AuthNotifier 11 + fcm_token_payload 4 + phone_formatter 4 - smoke 3 제거)
- **flutter build apk --debug**: app-debug.apk 생성 (**75.1s**, Chunk 3 대비 **2배 단축** — NDK 경고 해소 효과)

### 결정 기록
1. **Crashlytics 호출 생략**: 기사앱 선례에 따라 `FirebaseCrashlytics.instance.recordError` 호출 미포함. try/catch + debugPrint 로 대체. 테스트 환경 호환성 확보
2. **linkWithCredential happy path 테스트 불가**: firebase_auth_mocks 0.14.2 라이브러리 버그 — `MockUser.linkWithCredential` 내부 `MockUserCredential(false, mockUser: anonymous_this)` 에서 assert 실패. 우회로 **비-익명 currentUser + signInWithCredential 경로** 검증 (test #6)
3. **`_registerFcmToken` Firestore 저장 블록 보류**: ProfileNotifier (Chunk 5) 의 provinceId/cityId/officeId 필요. 현재는 phone 체크 후 debugPrint + TODO 주석. Chunk 5에서 ref.read(profileNotifierProvider) 로 활성화
4. **NDK 버전 upgrade 선반영**: jni plugin 28.2 요구 충족. APK 빌드 시간 2배 단축
5. **mock_exceptions 직접 의존**: firebase_auth_mocks 에 transitive 로 포함되지만 import 경로 명시화 위해 pubspec 등록

### 다음 세션 (Chunk 5)
1. **ProfileNotifier 본체** (~200 LOC):
   - customers/{uid} Firestore 로드/저장 (AuthNotifier.anonymousUid 기반)
   - customerInfo/{phone} 2경로 저장 (FCM 토큰 + 사무실 연락처)
   - 사무실 정보 SharedPreferences 연계 (provinceId/cityId/officeId)
   - 약관 동의 저장 (SharedPreferences)
2. **AuthNotifier._registerFcmToken Firestore 저장 블록 활성화**: `ref.read(profileNotifierProvider)` 로 provinceId 등 읽어서 `buildCustomerFcmPayload` + `.set(merge)` 수행
3. **ProfileNotifier 테스트 10+** (FakeFirestore round-trip)
4. (병렬) 사용자: iOS GoogleService-Info.plist 완료되면 Codemagic iOS 빌드 시도

**플랜 문서**: `C:\Users\kala1\.claude\plans\jazzy-swinging-meadow.md`

---

## 2026-04-19 (이어서³) — 손님앱 Flutter Week 1~3 Chunk 3 완료 (Infra + AuthNotifier 껍데기)

**커밋**: (Chunk 3 commit + 직후 일괄 push 8→9)

### 결과물 (신규 13 파일 + 개편 3 파일)

#### UI State 4종 (lib/features/{f}/presentation/state/)
- **call_ui_state.dart** — 6필드 (MODELS.md §7.4, `callStatus` 제외 → CallStatus Freezed 동반 필요한 Chunk 4+에서 동시 추가)
- **point_ui_state.dart** — 8필드. CustomerPoints? 참조
- **profile_ui_state.dart** — 9필드 (MVP 6 + `provinceId/cityId/officeId` nullable 3 추가). 주석으로 "NOTIFIERS.md §5.5 _registerFcmToken 경로 구성용" 명시. Chunk 4 null→값 transition 테스트 예정
- **auth_ui_state.dart** — 9필드 (P0 B.1 확정. Anonymous + Phone Auth 통합)

#### Core Infra
- **lib/core/providers.dart** — Firebase singletons 3 (firestore/auth/messaging) + SharedPreferences (override) + SecureStorage
- **lib/core/routing/app_router.dart** — GoRouter 골격 (SCREENS.md §2.1 1:1 이관). 11 routes + ShellRoute + 4 BottomNav
- **lib/core/routing/main_shell.dart** — SCREENS.md §2.2 BottomNavigation 4탭 + BackHandler 홈복귀 (onPopInvokedWithResult)
- **lib/core/routing/placeholder_screen.dart** — 공용 placeholder. Chunk 4+에서 실제 화면으로 교체

#### AuthNotifier 껍데기
- **lib/features/auth/presentation/notifiers/auth_notifier.dart** — 생성자 + Firebase 4주입 + updatePhoneNumber/updateVerificationCode (구현) + sendVerificationCode/verifyCode/initialFcmTokenRegistration (UnimplementedError stub) + authNotifierProvider
- **⚠️ 생성자에서 `_initAnonymousAuth()` / `_subscribeFcmToken()` 호출 제외** — 빈 껍데기 상태 유지. Chunk 4에서 2줄 추가

#### main.dart 개편
- Firebase.initializeApp + ProviderScope + SharedPreferences override + MaterialApp.router + GoRouter 연결
- 기본 Counter 앱 코드 전량 제거

#### build_runner generated (4)
- `.freezed.dart` × 4 (UI State). `.g.dart` 미생성 (UI State는 JSON 직렬화 불필요)

#### 단위 테스트 (5 파일, 12 신규 assertions)
- UI State 4종: defaults + copyWith (8 cases)
- AuthNotifier smoke: `ProviderContainer` + `MockFirebaseAuth` + `FakeFirebaseFirestore` + `_MockFirebaseMessaging` (mocktail) — provider 배선 / update 메서드 / UnimplementedError 검증 (3 cases)
- widget_test.dart: 기본 Counter 테스트 제거 (MyApp 삭제됨) → trivial placeholder

### 의존성 추가 (dev_dependencies)
- `firebase_auth_mocks: ^0.14.0`
- `fake_cloud_firestore: ^3.0.3`
- `mocktail: ^1.0.4`

### 검증 3관문 통과
- **flutter analyze**: 0 issues (13 info → onPopInvoked/`(_, __)` deprecated 수정 후 0)
- **flutter test**: **76/76 PASS** (기존 64 + 신규 12)
- **flutter build apk --debug**: app-debug.apk 생성 (162.5s)
  - ⚠️ NDK 버전 경고 (jni 28.2 vs 프로젝트 27.0) — backward-compatible 작동, Chunk 3 범위 외

### 결정 기록
1. **callStatus 필드 제외**: CallStatus/DriverInfo Freezed 동반 필요 → scope creep. Chunk 4에서 필드+모델 동시 추가
2. **ProfileUiState 3필드 확장**: FCM 경로 구성 필요 → Chunk 4 재편 방지
3. **AuthNotifier smoke 보강**: mocktail + firebase_auth_mocks로 provider 배선 assertion → Chunk 4 본격 테스트 디딤돌
4. **iOS build deferred**: GoogleService-Info.plist 미배치 (사용자 Week 0 잔여). Android APK만 검증
5. **providers.dart 단일 유지** (~25 LOC): 분할 트리거는 (a)100+LOC (b)다른 SDK 혼재 (c)merge 충돌 3회+

### 다음 세션 (Chunk 4)
1. AuthNotifier 본체 로직:
   - `_initAnonymousAuth` + 생성자 호출 추가
   - `sendVerificationCode` + `verifyCode` + `_signInWithCredential` (linkWithCredential 핵심)
   - `_handleCredentialAlreadyInUse` fallback (`credential-already-in-use`)
   - `_subscribeFcmToken` + `_registerFcmToken` + `initialFcmTokenRegistration` (의제 5 fcmTokenPlatform 메타)
2. AuthNotifier 테스트 10+ cases (currentUser 상속 / signInAnonymously / linkWithCredential / fallback / FCM 토큰 등록)
3. (병렬) 사용자: iOS Firebase Console 앱 등록 + GoogleService-Info.plist 배치

**플랜 문서**: `C:\Users\kala1\.claude\plans\jazzy-swinging-meadow.md`

---

## 2026-04-19 (이어서²) — 손님앱 Flutter Week 1~3 Chunk 2 완료 (Freezed Models 5종)

**커밋**: `0d284b1f`

### 결과물 (21 files staged)

#### 5 Freezed 모델 (lib/features/{f}/domain/entities/)

**Medium risk (표준 Converter)**:
- `point/customer_points.dart` 9필드 — @CustomerGradeConverter + @TimestampConverter + 비즈니스 메서드 5종 (shouldUpdateGrade/updatedGrade/canUsePoints/calculateEarnPoints/callsToNextGrade)
- `point/point_transaction.dart` 10필드 — @TransactionTypeConverter + @TimestampConverter. amount 부호 Kotlin 관용 유지

**Low risk (R2 뼈대)**:
- `ad/banner_ad_data.dart` 11필드 — Phase 1 미사용, R2_BANNER_AD 발동 시 Notifier 연동

**High risk (custom fromFirestore + toFirestore factory)**:
- `call/customer_call.dart` 20필드 — `@JsonKey(name: 'assignedDriverId')` on driverId, 3중복 필드 fallback(customerAddress→departure), toFirestore가 currentLocation을 customerAddress+departure+departure_set 3곳 + destinationLocation을 destination+destination_set 2곳에 중복 저장, timestamp Timestamp↔int 양방향
- `profile/customer_info.dart` 17필드 — 2경로 저장(customers/{uid} + customerInfo/{phone}) 대응, legacy `address`→`homeAddress` fallback

#### build_runner generated (10 files)
- `.freezed.dart` × 5 + `.g.dart` × 5. `dart run build_runner build --delete-conflicting-outputs`. 기사앱 정책 일치하여 git commit.

#### Unit test 5 files (29 assertions 신규)
- customer_call_test 핵심: 3중복 필드 저장 / @JsonKey assignedDriverId 매핑 / timestamp 변환 / 부호 유지
- 나머지 4개: fromJson round-trip + 비즈니스 메서드 + Defaults

### 수정 (main.dart)
Dart 3.11 dot-shorthand(`colorScheme: .fromSeed(...)`, `mainAxisAlignment: .center`)이 build_runner 내부 analyzer 3.9 미지원 → 명시 표기로 수정.

### 특수 처리
`customer_call.dart` 상단에 `// ignore_for_file: invalid_annotation_target` — Freezed 2.x + json_annotation 4.x 공식 패턴의 analyzer lint false positive 억제.

### 검증
- `flutter analyze`: 0 issues (10 generated 파일 포함)
- `flutter test`: **64/64 PASS** (기존 35 + 신규 29)
- `dart run build_runner`: 10 outputs written

### 다음 세션 (Chunk 3 후보)
1. UI State 구조체 4종 (CallUiState / PointUiState / ProfileUiState / AuthUiState)
2. Service/Repository Provider 정의 + GoRouter 골격
3. 첫 Notifier — AuthNotifier 권장 (Anonymous + Phone Auth `linkWithCredential`, AUTH.md §3.4)

**플랜 문서**: `C:\Users\kala1\.claude\plans\jazzy-swinging-meadow.md` (Chunk 3 시 overwrite)

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
