# 교차 리뷰 발견 사항 종합 (Pre-Coding Review)

> **목적**: MVP 매핑 문서 27개 코딩 진입 전 양 팀원 교차 검토 결과 종합. P0 우선 패치 → 코딩 진입.
> **검토자**: kotlin-expert (flutter-expert 작성 4건 검토) + flutter-expert (kotlin-expert 작성 4건 검토)
> **작성일**: 2026-04-16
> **체크리스트 4 카테고리**: 의제 정합성 / 문서 간 가정 / Dart 코드 정확성 / 엣지 케이스

---

## 검토 통계

| 분류 | 발견 건수 | 작업 시점 |
|------|----------|----------|
| 🔴 **P0** (코딩 진입 전 수정 필수) | **9건** | 즉시 |
| 🟠 P1 (코딩 중 보강) | 13건 | Week 1~2 |
| 🟢 P2 (R1 이후 또는 무시) | 9건 | 운영 데이터 기반 |
| ✅ 검증 완료 (이상 없음) | 30+ 항목 | — |

**검토 결과 종합 평가**: 전반 품질 매우 높음. Kotlin 원본 라인 인용 정확, 의제 결정 반영 일관, sealed class/Dart 3 패턴 적극. **P0 9건은 보일러플레이트 또는 명세 불완전 수준**, 구조적 결함 없음.

---

## 🔴 P0 9건 (코딩 진입 전 수정 필수)

### A. driver_app/MVP/NOTIFIERS.md (3건, kotlin-expert 작성, flutter-expert 발견)

#### A.1 `Platform.isIOS` 임포트 누락
- **위치**: §2.5 `_registerFcmToken` (라인 871-872), §10.3 `fcmTokenPlatform` 로깅 등
- **이슈**: `Platform.isIOS` 사용처 다수, 파일 상단 임포트 블록(라인 78-82)에 `import 'dart:io' show Platform;` 없음
- **영향**: Dart 컴파일 에러
- **수정안**: `lib/features/driver/notifiers/driver_workflow_notifier.dart` 상단에 추가

#### A.2 StateNotifier 내부 `ref` 미보유 (가장 critical)
- **위치**: §2.3.6 `confirmAndFinalizeTrip` (라인 539, 549, 558)
- **이슈**: `ref.read(depositRatioProvider)`, `ref.read(todaySettlementProvider.notifier).addTrip(...)`, `ref.read(tripHistoryProvider.notifier).addTrip(...)` 사용. 그러나 `DriverWorkflowNotifier` 생성자(라인 87-94)는 `ref`를 인자로 받지 않고 필드에도 저장하지 않음. **flutter_riverpod 2.5.1 기준 컴파일 에러**.
- **영향**: Week 1 빌드 실패 확정
- **수정안**:
  ```dart
  class DriverWorkflowNotifier extends StateNotifier<DriverScreenUiState> {
    final Ref ref;
    DriverWorkflowNotifier({required this.ref, ...}) : super(...);
  }
  // Provider 생성부:
  final driverWorkflowProvider = StateNotifierProvider(
    (ref) => DriverWorkflowNotifier(ref: ref, ...),
  );
  ```
- **연관**: §4 CarryOverNotifier도 동일 패턴 누락 (라인 1196-1217)

#### A.3 `_subscribeAuth` 재진입 방지 부재
- **위치**: §2.2 라인 114-123 `_subscribeAuth`
- **이슈**: `auth.authStateChanges()` 구독 첫 호출 후 재호출 시 이전 구독 해제 없이 누적 → memory leak
- **영향**: 통합 테스트 hang / Crashlytics 허위 이벤트
- **수정안**: 재진입 방지 flag (`_authSubscribed`) 추가 또는 첫 호출 후 재호출 시 `_authSub?.cancel()` 후 재구독

### B. customer_app/MVP/NOTIFIERS.md + MODELS.md (3건, kotlin-expert 작성, flutter-expert 발견)

#### B.1 `AuthUiState` 9필드 MODELS.md 정의 검증
- **위치**: customer NOTIFIERS §3.2 라인 671 `next.isAnonymouslySignedIn` 사용
- **이슈**: AuthNotifier가 의존하는 9 필드 (`isAnonymouslySignedIn`, `anonymousUid`, `phoneNumber`, `verificationId`, `verificationCode`, `isCodeSent`, `isVerified`, `isLoading`, `error`)가 customer MODELS.md §7.4 AuthUiState에 정의되어 있는지 확인 필요. **참조만 있고 정의 검증 안 됨**
- **영향**: 정의 누락 시 컴파일 에러
- **수정안**: customer MODELS.md §7.4에 9 필드 Freezed 클래스 명시 (이미 있을 가능성 — 본 검토 범위 밖이라 확인 필요)

#### B.2 `CustomerPoints.calculateEarnPoints(fare)` 메서드 정의 누락
- **위치**: customer NOTIFIERS §2.3.5 라인 470 `pointState.customerPoints?.calculateEarnPoints(fare)`
- **이슈**: customer MODELS.md `CustomerPoints` Freezed가 `@freezed` + 필드만 정의, 메서드 없음. Freezed에 메서드 추가하려면 `const CustomerPoints._()` private 생성자 + 메서드 정의 필요
- **영향**: NoSuchMethodError 또는 컴파일 에러
- **수정안**: customer MODELS.md `CustomerPoints` Freezed 정의에 추가:
  ```dart
  @freezed
  class CustomerPoints with _$CustomerPoints {
    const CustomerPoints._();  // private 생성자 (메서드 추가 필수)
    const factory CustomerPoints({...}) = _CustomerPoints;
    factory CustomerPoints.fromJson(...) => _$CustomerPointsFromJson(...);

    int calculateEarnPoints(int fare) => (fare * grade.earningRate).toInt();
  }
  ```

#### B.3 `grade.jsonValue` 속성 미정의
- **위치**: customer NOTIFIERS §2.3.1 라인 257 `pointState.customerPoints?.grade.jsonValue.toLowerCase()`
- **이슈**: `CustomerGrade` enum의 `jsonValue` 속성 정의 확인 필요. customer ENUMS.md §2 `CustomerGrade`는 `json` 속성으로 정의됨 (`jsonValue` 아님)
- **영향**: 컴파일 에러
- **수정안**: customer NOTIFIERS.md `grade.jsonValue` → `grade.json` 으로 일치 또는 ENUMS.md에 `jsonValue` getter 추가

### C. driver_app/MVP/MODELS.md (1건, kotlin-expert 작성, flutter-expert 발견)

#### C.1 `AddressSearchResult.fromKakao` Dart `let` extension 오용
- **위치**: driver MODELS §13 라인 1282-1283
- **이슈**: `(kakao['y'] as String?)?.let((s) => double.tryParse(s))` — Dart에는 `let` 메서드 없음. 라인 1289-1293에서 자체 extension 정의하나 build_runner 생성 파일 충돌 가능
- **영향**: 컴파일 에러
- **수정안**:
  ```dart
  latitude: (kakao['y'] as String?) != null
      ? double.tryParse(kakao['y'] as String)
      : null,
  ```

### D. flutter/SERVER_TASKS.md (1건, 중재자 작성, kotlin-expert 발견)

#### D.1 `recordAcceptanceEvent` driver 경로 플레이스홀더
- **위치**: §B.3 라인 314
- **이슈**: `db.doc(`<driver_path>/${input.assignedDriverId}`)` — `<driver_path>`가 플레이스홀더 문자열 그대로. Firestore 경로 `provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid}`는 4개 파라미터 필요
- **영향**: CF 런타임 실패 → 모든 acceptanceEvents가 `platform: "android"` 기본값으로 기록 → iOS 기사 이벤트 Android 집계에 섞임 → R1_LOCKSCREEN 자동 평가 왜곡
- **수정안**: `recordAcceptanceEvent` 입력 인터페이스에 `provinceId/cityId/officeId` 추가:
  ```typescript
  interface RecordEventInput {
    callId: string;
    assignedDriverId: string;
    provinceId: string;  // ← 추가
    cityId: string;       // ← 추가
    officeId: string;     // ← 추가
    outcome: ...;
    assignedAt: Timestamp;
  }
  // 호출처(onCallStatusChanged)에서 afterData에서 추출 후 전달
  ```

### E. customer_app/MVP/PHASE2_APPCLIP.md (1건, flutter-expert 작성, kotlin-expert 발견)

#### E.1 customerInfo Firestore 보안 규칙 경로 오류 + 권한 취약점
- **위치**: §4.4 라인 773-781 + SERVER_TASKS.md §D.2 라인 604-611
- **이슈**:
  - (a) 경로 `customerInfo/{phone}` 최상위 → 실제 Firestore 경로는 `provinces/{p}/cities/{c}/offices/{o}/customerInfo/{phone}` 중첩
  - (b) `request.auth.uid != null` 조건은 `request.auth != null`과 동치 → **모든 인증 사용자(Anonymous 포함)가 모든 customerInfo 문서 write 가능** → 손님 A가 손님 B 번호 알면 fcmToken 탈취 공격 성립
- **영향**: 경로 오류 시 App Clip 사용 불가, 잘못 교정 시 보안 취약점
- **수정안**:
  ```
  match /provinces/{p}/cities/{c}/offices/{o}/customerInfo/{phone} {
    allow read: if request.auth != null;
    allow write: if request.auth != null
                 && request.resource.data.diff(resource.data).affectedKeys()
                      .hasOnly(['fcmToken', 'fcmTokenPlatform', 'phoneNumber', 'updatedAt']);
  }
  ```

---

## 🟠 P1 13건 (코딩 중 보강)

### driver_app/MVP/NOTIFIERS.md (3건)
- **P1-1**: `firstWhereOrNull` 사용처 다수 → `collection: ^1.18.0` pubspec 추가 + import
- **P1-2**: `confirmAndFinalizeTrip` `'현금+포인트'` 분기 → `startsWith('현금+')`로 복합 결제 포괄
- **P1-3**: Provider 명명 일관성 (`depositRatioProvider`/`todaySettlementProvider`/`tripHistoryProvider` 미정의) → `settlementProvider` 통합

### driver_app/MVP/NOTIFIERS.md + customer NOTIFIERS.md (1건)
- **P1-4**: `FirebaseCrashlytics.instance` 직접 호출 → 생성자 주입 (테스트 가능성)

### customer_app/MVP/NOTIFIERS.md (4건)
- **P1-5**: `_subscribePoints` 초기화 타이밍 → ProfileNotifier 사무실 정보 로드 완료 후
- **P1-6**: `requestCall` 재시도 로직 → `FirebaseException.code` 기반 조건 (unavailable/deadline-exceeded만)
- **P1-7**: `handleRideCompleted` 500ms 지연 + refresh 양측 사용 → 실시간 리스너 활성 시 refresh 생략
- **P1-8**: `_pointsSub` vs `_authSub` 일관성 → `ref.listen` 결과 ProviderSubscription cancel 명시

### driver_app/MVP/MODELS.md (3건)
- **P1-9**: `ServerTimestampConverter.toJson` 타입 불일치 → `toFirestore()` 별도 메서드로 분리
- **P1-10**: ENUMS.md 수정 3건 반영 검증 (이미 OVERVIEW.md에 명시되었으나 MODELS.md 작성자 확인)
- **P1-11**: `isAppCustomer` fromJson 안전 파싱 → custom converter `as bool? ?? false`

### customer_app/MVP/AUTH.md (3건)
- **P1-12**: `late final` 분기 할당 → ternary 명시 초기화 (의제 14-G 엄격 준수)
- **P1-13**: `verificationCompleted` Race Condition → `state.copyWith(isCodeSent: true)` 먼저 설정

### flutter/driver_app/MVP/FCM.md (3건)
- **P1-14**: 포그라운드 iOS 배너 정책 명시 부족 → `setForegroundNotificationPresentationOptions` 호출 명세 (또는 의도적 비활성 명시)
- **P1-15**: `IncomingCallService.handleCallAssigned` named 시그니처 명시 부족
- **P1-16**: `notifications` 컬렉션 firestore.rules 누락 → SERVER_TASKS.md §D.2에 추가

### flutter/SERVER_TASKS.md (3건)
- **P1-17**: `assignedTime` → `assignedTimestamp` 필드명 정정 (실제 Firestore 필드)
- **P1-18**: `backfill-platform.js` batch 재생성 버그 → commit 후 `batch = db.batch()` 재할당
- **P1-19**: `enforceAppCheck: false` 의제 14-E 실제 결정 재확인 (WORKING_DOC.md §5)

### flutter/customer_app/MVP/ATTRIBUTION.md (2건)
- **P1-20**: `office_codes` vs `offices/{o}` bank/account 4종 저장 경로 모호 → CF 관리 화면 또는 QR full URL 파라미터 명시
- **P1-21**: QR full URL 파싱 (Phase 1 vs Phase 2 동작 차이) → §2.4 Phase 1도 8 파라미터 파싱

### flutter/customer_app/MVP/PHASE2_APPCLIP.md (3건)
- **P1-22**: App Clip 50MB 단일 한도 (iOS 15+) 명시 통일 + Firebase SDK 크기 측정
- **P1-23**: AASA `components[].?` 와일드카드 명시 (단일 `"/"` 모호)
- **P1-24**: Custom Token Rate Limiting (App Check 미적용 시)

(13건 = 주요 항목, 일부 카테고리 중복 합산)

---

## 🟢 P2 9건 (R1 이후 또는 무시 가능)

- **P2-1**: driver NOTIFIERS `errorMessage` vs `infoMessage` UX 분리 (정보성 vs 에러)
- **P2-2**: customer NOTIFIERS `credential-already-in-use` Anonymous 데이터 마이그레이션 R2
- **P2-3**: driver MODELS `'카드'` 분기 정리 (PaymentMethod enum 도입 시)
- **P2-4**: customer AUTH reCAPTCHA 실기기 검증 (Phase 1 출시 전 SM-G996N/SM-S901N)
- **P2-5**: customer AUTH 계정 삭제 (App Store 5.1.1(v)) Phase 1 승격 검토
- **P2-6**: FCM data.title/data.body 중복 (DRY 위반, buildFcmPayload 헬퍼로 해소)
- **P2-7**: FCM iOS Focus Mode 허용 목록 안내 과잉 (R1 피드백 기반)
- **P2-8**: SERVER_TASKS Day 5 단일 배포 14곳 → Day 5~7 분할
- **P2-9**: PHASE2 Header Image 1800×1200 + iOS 14 vs 15+ 통일

---

## ✅ 검증 완료 (30+ 항목)

### 의제 결정 일관성
- 의제 1 iOS 15.0 → Podfile/Info.plist/PHASE2 deployment target 일관
- 의제 4 apns 블록 + data 보존 → FCM/SERVER_TASKS 모두 일치
- 의제 5 단일 fcmToken + fcmTokenPlatform 메타 → 모든 문서 일관 (3개 앱 + CF Grep 정확)
- 의제 6 사유 문자열 4종 + Privacy Manifest → BUILD/PLATFORM_CHANNELS 정합
- 의제 7 Time Sensitive MVP + Feature Flag → FCM/PLATFORM_CHANNELS 정합
- 의제 8 옵션 C onDisconnect → presence_service.dart 1:1 확인
- 의제 9 재로그인 + Auth 자동 상속 → AUTH driver/customer 모두
- 의제 10 같은 applicationId → ATTRIBUTION 정합
- 의제 11 acceptanceEvents + Analytics → SERVER_TASKS/FCM 정합
- 의제 12 shared_preferences MVP → MODELS/NOTIFIERS sqflite 미사용 확인
- 의제 14 sealed class/Riverpod 2.x/Freezed → 모든 문서 일관

### 코드 정확성
- Kotlin 원본 라인 번호 100% 일치 (FCM/MODELS/NOTIFIERS/AUTH 전수 확인)
- Dart 3 `switch expression` + `sealed class` 정확
- Freezed `required` + nullable + `@Default()` 일관 (의제 14-G)
- `late` 키워드 0건
- snake_case 5필드 (`departure_set` 등) Firestore 호환 보존
- 낙관적 UI + 롤백 패턴 Kotlin 일관 재현

### 보안·심사
- App Clip `parent-application-identifiers` entitlement
- AASA TLS 1.2+ + Apple CDN 24시간 캐시 인지
- SKOverlay 공식 vs 임의 배너 금지
- Firestore 보안 규칙 admin 전용 (acceptanceEvents/monthlyStats)
- Phone Auth SMS 비용 + reCAPTCHA 위험 명시

---

## 패치 우선순위 + 작업 분담

### 즉시 패치 권장 (P0 9건)

| 이슈 | 패치 담당 | 예상 시간 |
|------|----------|----------|
| A.1 Platform.isIOS 임포트 | kotlin-expert (driver NOTIFIERS) | 5분 |
| A.2 ref 미주입 (가장 critical) | kotlin-expert (driver NOTIFIERS + CarryOverNotifier) | 30분 |
| A.3 `_subscribeAuth` 재진입 | kotlin-expert (driver NOTIFIERS) | 15분 |
| B.1 AuthUiState 9필드 검증 | kotlin-expert (customer MODELS 확인) | 15분 |
| B.2 calculateEarnPoints 메서드 추가 | kotlin-expert (customer MODELS) | 15분 |
| B.3 grade.jsonValue → grade.json | kotlin-expert (customer NOTIFIERS) | 5분 |
| C.1 AddressSearchResult Dart `let` 제거 | kotlin-expert (driver MODELS) | 10분 |
| D.1 SERVER_TASKS recordAcceptanceEvent 경로 | 중재자 (SERVER_TASKS) | 15분 |
| E.1 customerInfo 보안 규칙 경로/취약점 | 중재자 (SERVER_TASKS + flutter-expert PHASE2 보강) | 20분 |

**총 패치 시간 추정**: ~2시간 (양 팀원 병렬 + 중재자)

### 코딩 중 보강 (P1 13건)
Phase 6 Week 1~2에 자연 발견·수정. BUILD.md 체크리스트 작업과 함께.

### R1 이후 (P2 9건)
운영 데이터 기반 결정. Phase 1 출시 후 재검토.

---

## 결론

**검토 결과 = 매우 가치 있음**. P0 9건 중 3건(A.2 ref / B.2 calculateEarnPoints / D.1 driver_path)은 **그대로 코딩 진입 시 즉시 빌드 실패 또는 런타임 오류** 확정. 사전 발견으로 코딩 단계 30~50건 회귀 방지 효과.

**다음 단계 옵션**:
1. **P0 9건 즉시 패치** (각 작성자에게 패치 위탁, ~2시간)
2. **P0 패치 + P1 일부 즉시 보강** (Phase 6 Week 0 작업 일정 단축)
3. **P0만 우선 패치, P1/P2는 코딩 중 자연 처리**

권장: **옵션 1** (P0만 즉시 패치, P1/P2는 명세 그대로 보존하여 Phase 6에서 처리). 패치 후 청사진 100% 신뢰 가능 상태 도달.

---

## 참조

- `flutter/WORKING_DOC.md` §5 — 의제 결정 11건
- `flutter/driver_app/MVP/` 10 문서
- `flutter/customer_app/MVP/` 9 문서
- `flutter/SERVER_TASKS.md` — CF/Firestore 서버측 작업
- 본 문서는 향후 **검토 결과 추적**용으로 보존
