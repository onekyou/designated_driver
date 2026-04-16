# 기능 인벤토리 (Flutter 트랙 — 3컬럼)

> **목적**: 기사앱·손님앱 기능을 라이프사이클 단계별 전수 인벤토리. **3컬럼 구조**(Android 평가 / iOS 평가 / Flutter 매핑)로 플랫폼별 차이와 Dart 매핑을 동시 파악.
> **분류 라벨**: B(Base, 1차 출시 필수) / R1(1차 보강) / R2(2차 보강) / D(미적용 검토)
> **작성일**: 2026-04-16
> **참조 원본**: `ios/FUNCTIONAL_INVENTORY.md` (Swift 트랙, 본 문서 Part A의 iOS 컬럼 기준)

---

## 평가 차원

각 라이프사이클 단계에 대해:
- **Happy Path**: 정상 네트워크 + 정상 흐름
- **Cancellation**: 발생 가능한 모든 취소 (관리자/기사/손님/타임아웃)
- **Offline**: 네트워크 끊김 시 동작

각 항목 컬럼:
- **Android 분류**: Kotlin 원본 또는 Flutter Android 빌드 기준
- **iOS 분류**: Apple 플랫폼 제약 기준 (Swift/Flutter 무관)
- **Flutter 매핑**: Dart 패키지 + 핵심 코드 위치 또는 매핑 전략

---

# Part A — 기사앱 (driver_app)

> **상태**: 본 Part A는 `ios/FUNCTIONAL_INVENTORY.md` 63항목을 3컬럼으로 재구성합니다. 아래는 우선 구간(1.1~1.3) 템플릿이며, 의제 1~6 결정 후 나머지 구간을 같은 형식으로 채웁니다.

## 1. 라이프사이클 단계 (콜 1순환)

### 1.1 콜매니저 배차 → 기사앱 수신

| 항목 | Android 분류 | iOS 분류 | Flutter 매핑 |
|------|-------------|---------|-------------|
| Happy Path (FCM `call_assigned` 수신) | **B** (Kotlin `MyFirebaseMessagingService.kt:148-176` 기존 동작) | **B** (Time Sensitive `interruptionLevel = .timeSensitive`). R1: VoIP push + CallKit + Live Activity | `firebase_messaging` `FirebaseMessaging.onMessage` + `onBackgroundMessage`. iOS는 `flutter_local_notifications` Time Sensitive presentation. **선결: CF payload `apns` 블록 추가 (의제 4)** |
| Cancellation - 관리자 취소 (`call_cancelled`) | **B** (`MyFirebaseMessagingService.kt:177-188` LocalBroadcast + 시스템 알림) | **B** (UNUserNotificationCenter alert) | `firebase_messaging` 핸들러에서 Riverpod Notifier ref.read로 콜 정리. flutter_local_notifications로 시스템 알림 |
| Cancellation - 타임아웃 (3분, CF) | **B** (서버측, 클라이언트 영향 없음) | **B** (동일) | 영향 없음. Firestore 리스너가 assignedCalls에서 자동 제거 |
| Offline - 기사 폰 | **B** (`DriverViewModel.kt:238-267` `loadCurrentActiveCall()` 1회 조회) | **B** (동일 패턴) | `cloud_firestore` `whereIn(status, [...]).get()` 1회. ConsumerWidget `initState` 또는 Riverpod `FutureProvider` |

### 1.2 기사 수락 → 콜매니저+손님앱 통지

| 항목 | Android 분류 | iOS 분류 | Flutter 매핑 |
|------|-------------|---------|-------------|
| Happy Path (acceptCall) | **B** (`DriverViewModel.kt:371-475` 낙관적 UI + 트랜잭션) | **B** | `DriverWorkflowNotifier` (Riverpod StateNotifier) + Freezed UiState copy. `cloud_firestore` `runTransaction` async |
| Firestore 트랜잭션 (콜+기사 상태) | **B** (단일 `runTransaction`) | **B** | `FirebaseFirestore.instance.runTransaction((tx) async { ... })`. `_isAccepting` 플래그는 Notifier state 필드 |
| 콜매니저 FCM 통지 (서버측) | **B** | **B** | 영향 없음 (CF) |
| 손님앱 FCM 통지 (DRIVER_ASSIGNED, 서버측) | **B** | **B** | 영향 없음 (CF). 손님앱 Flutter 포팅 시 Part B에서 평가 |
| Cancellation - 트랜잭션 실패 | **B** (롤백 + errorMessage) | **B** | try/catch + Notifier state 복원. SnackBar로 errorMessage 표시 |
| Offline - 트랜잭션 큐잉 | **B?** (Firestore SDK 자동 큐잉, 트랜잭션 특성 확인 필요) | **B?** (동일 SDK 동작) | `cloud_firestore` 자동 offline persistence. R1: sqflite/drift PendingSync 큐 |

### 1.3 기사 거절 → 콜 WAITING 복귀

| 항목 | Android 분류 | iOS 분류 | Flutter 매핑 |
|------|-------------|---------|-------------|
| Happy Path (LockScreen 거절) | **D** (`LockScreenActivity.kt:205-247` dead code, CLAUDE.md 3/18 거절 버튼 제거) | **D** (이관 대상 아님) | dead code, 이관 안 함 |
| Happy Path (앱 내 거절) | **B** (`DriverViewModel.kt:477-518` rejectCall) | **B** (동일 async transaction) | `DriverWorkflowNotifier.rejectCall()` async. R1(VoIP 채택 시): CallKit CXEndCallAction delegate에서 호출 |
| 콜매니저 통지 | **B** | **B** | 영향 없음 (CF) |
| Offline 시 처리 | **B** (즉시 실패 가능, 재시도 없음) | **B** | 동일. R1: PendingSync 검토 (거절은 시효성 있어 큐 의미 제한적) |

### 1.4 ~ 1.9 (운행 시작 → WAITING 복귀)

> **상태**: `ios/FUNCTIONAL_INVENTORY.md` §1.4~§1.9 내용을 3컬럼으로 재구성. 의제 1~6 결정 후 채움.
> **요약**:
> - 1.4 운행 시작: B (Android/iOS 모두), Flutter는 Form 위젯 + Notifier
> - 1.5 운행 중: 위치 추적 미사용 → B 단순. Geolocator는 R2
> - 1.6 운행 완료: B, callRef.update() 단일 쓰기
> - 1.7 정산 입력 → 제출: B (정산 공식), R1 (PendingSync 큐), B (매니저 반려 재제출)
> - 1.8 이월금 처리: B (이월금 리스너 — 유일한 실시간 리스너), B (transferCarryOver/SETTLED)
> - 1.9 WAITING 복귀: B (트랜잭션 내 driver.status), B (Presence 유지), B (콜매니저 인지)

---

## 2. 횡단 기능 (라이프사이클 외부)

### 2.1 로그인·회원가입

| 항목 | Android 분류 | iOS 분류 | Flutter 매핑 |
|------|-------------|---------|-------------|
| 로그인 (Firebase Auth + collectionGroup) | **B** (`LoginViewModel.kt:70-266`) | **B** | `firebase_auth` `signInWithEmailAndPassword()` async. `cloud_firestore` `collectionGroup("designated_drivers").where('authUid', isEqualTo: uid)` |
| 자동 로그인 | **B** (EncryptedSharedPreferences) | **B** (Keychain Services) | `flutter_secure_storage` AndroidOptions(encryptedSharedPreferences: true) + IOSOptions. **마이그레이션 의제 9: 기존 Kotlin EncryptedSharedPreferences 데이터 → flutter_secure_storage MethodChannel 이관** |
| 회원가입 + 승인 대기 | **B** (`SignUpViewModel.kt:164-202`) | **B** | `createUserWithEmailAndPassword` + `setData('pending_drivers/{uid}')` |
| 비밀번호 찾기 | **B** | **B** | `sendPasswordResetEmail` |
| 로그아웃 + cleanup | **B** | **B** | `signOut()` + Notifier 초기화 + flutter_secure_storage clear + Realtime DB presence offline |

### 2.2 ~ 2.X (FCM, Presence, 정산, 등)

> **상태**: 의제 결정 후 채움. 주요 항목 요약:
> - 2.2 FCM 6종 핸들링 (call_assigned, call_cancelled, SETTLEMENT_*, CARRYOVER_TRANSFERRED): 모두 B
> - 2.3 Presence (Firebase RTDB onDisconnect): B (Android/iOS), `firebase_database` 직접 사용
> - 2.4 정산 공식 (settlement_calc.dart): B 순수 함수
> - 2.5 LockScreen 알림 UI: Android B (네이티브 유지) / iOS D 풀스크린 + R1 CallKit
> - 2.6 Foreground Service: Android B (`flutter_foreground_task`) / iOS D (구조적 불가)
> - 2.7 BootReceiver: Android B (네이티브 유지) / iOS D
> - 2.8 추천 QR: B (Android/iOS) / R2

---

# Part B — 손님앱 (customer_app, Kotlin → Flutter 신규 포팅)

> **원본**: Kotlin `customer_app/app/src/main/java/com/designated/customer/`
> **인벤토리**: 6 models, 6 ViewModels (MainViewModel 995 LOC), 11 screens, 5 FCM types

## B.1 라이프사이클 (콜 요청 → 배차 → 운행 → 완료)

| 항목 | Android 분류 | iOS 분류 | Flutter 매핑 |
|------|-------------|---------|-------------|
| 콜 요청 (`requestCall`) | **B** (`CallService.kt`) | **B** | `CallNotifier.requestCall()` Riverpod Notifier. `cloud_firestore` setData |
| 콜 취소 (CANCELLED_BY_CUSTOMER, ACCEPTED/PREPARING까지) | **B** (`CallService.kt:cancelCall`) | **B** | `CallNotifier.cancelCall()` runTransaction |
| 활성 콜 복구 (`restoreActiveCall`) | **B** (`MainViewModel.kt:191-236`) | **B** | `CallNotifier` initState에서 Firestore `whereIn` 1회 조회. **리스너 아님 (비용 최적화)** |
| FCM 5종 수신 | **B** | **B** | `firebase_messaging` 핸들러에서 5종 분기. iOS Time Sensitive |
| 포인트 사용 (콜 요청 시) | **B** (`PointService.usePoints`) | **B** | `PointNotifier.usePoints()`. CallNotifier에서 직접 호출 (교차 의존 1) |
| 포인트 환불 (콜 취소 시) | **B** | **B** | CallNotifier 핸들러에서 `ref.read(pointNotifierProvider.notifier).refund()` (교차 의존 2) |
| 포인트 적립 (CF가 처리) | **B** (`MainViewModel.handleRideCompleted`) | **B** | `RIDE_COMPLETED` FCM 핸들러에서 PointNotifier 갱신 + 팝업 트리거 (교차 의존 3) |

## B.2 인증·온보딩

| 항목 | Android 분류 | iOS 분류 | Flutter 매핑 |
|------|-------------|---------|-------------|
| Phone Auth (Firebase) | **B** (`PhoneAuthViewModel.kt`) | **B** | `firebase_auth` Phone Auth. SMS 자동 인식 (Android만, iOS는 사용자 입력) |
| 약관 동의 (Terms + Privacy) | **B** | **B** | TermsAgreementScreen 위젯. shared_preferences에 동의 시점 저장 |
| 프로필 설정 (닉네임/주소/전화번호 중복 체크 CF) | **B** (`ProfileSetupViewModel.kt`) | **B** | `ProfileNotifier.checkPhoneNumberDuplicate()` cloud_functions httpsCallable |
| 사무실 정보 매칭 (Install Referrer / 수동 입력) | **B** (Install Referrer Android) | **B** Phase 1 수동 입력 / **R1** Phase 2 App Clip | Phase 1: 수동 입력 화면 + Firestore 사무실 검색. Phase 2 (Mac 도착 후): Swift App Clip + App Group UserDefaults 이관 |

## B.3 화면 (11개)

| 화면 | Kotlin LOC | Flutter 위젯 |
|------|-----------|-------------|
| HomeScreen | ~400 | `HomeScreen extends ConsumerStatefulWidget`, CallNotifier 의존 |
| PhoneAuthScreen | — | AuthNotifier 의존 |
| TermsAgreementScreen + PrivacyPolicyScreen | — | shared_preferences |
| ProfileSetupScreen + ProfileScreen | — | ProfileNotifier 의존 |
| PointScreen + PointHistoryScreen | — | PointNotifier StreamProvider 구독 |
| CallHistoryScreen | — | Firestore 쿼리 + paginated list |
| MainNavigation (BottomNav 5탭) | — | GoRouter ShellRoute + BottomNavigationBar |

## B.4 데이터 모델 (6개, Kotlin → Freezed)

| 모델 | Kotlin 필드 | Flutter Freezed |
|------|-----------|----------------|
| CustomerCall | 18 fields | `@freezed class CustomerCall with _$CustomerCall { ... }` + JSON serialization |
| CustomerInfo | 12 fields | 동일 패턴 |
| CustomerPoints | 8 fields | StreamProvider로 실시간 구독 |
| CustomerGrade | enum BRONZE/SILVER/GOLD/VIP | Dart enum + extension method (calculatePoints) |
| PointTransaction | 9 fields | history paginated |
| BannerAdData | 10 fields | R2 |

## B.5 FCM 5종

| 타입 | 처리 (Flutter) |
|------|---------------|
| CALL_RECEIVED | CallNotifier.restoreActiveCall() (백그라운드 isolate) |
| DRIVER_ASSIGNED | 팝업 + 진동 (Vibration plugin) + 알림음 (audioplayers) |
| RIDE_COMPLETED | PointNotifier 갱신 + 팝업 트리거 |
| CALL_CANCELLED | 팝업 닫기 + PointNotifier.refund() |
| call_status_update | CallNotifier.updateStatus() |

## B.6 iOS-Incompatible Android Features

| 기능 | Android Flutter | iOS Flutter | 대체 |
|------|----------------|-------------|------|
| Install Referrer API | `play_install_referrer` 패키지 | 불가능 | Phase 1 수동 입력 / Phase 2 App Clip Swift |
| LocalBroadcastManager | Stream/StreamController 또는 EventChannel | NotificationCenter 자동 (Flutter 추상화) | Riverpod ref.listen |
| Vibrator (무한 진동) | `vibration` 패키지 | 시스템 한정 진동만 허용 | 1회 진동으로 대체 |
| RingtoneManager | `audioplayers` 패키지 | 시스템 사운드 재생 | 동일 (1회) |

---

## 분류 결과 요약 (목표)

### 기사앱 (Part A, 63항목)
| 카테고리 | Android B | iOS B | iOS R1 | iOS R2 | iOS D |
|---------|----------|-------|--------|--------|-------|
| 라이프사이클 1.1~1.9 | (대부분 B) | (대부분 B) | LockScreen 풀스크린 등 | 위치 추적 등 | Foreground 영구, BootReceiver |
| 횡단 2.x | (대부분 B) | (대부분 B) | PendingSync 큐 등 | BG sync 등 | — |

### 손님앱 (Part B)
| 카테고리 | Android B | iOS B | iOS R1 | iOS R2 |
|---------|----------|-------|--------|--------|
| 라이프사이클 B.1 | (전체 B) | (전체 B) | — | — |
| 인증·온보딩 B.2 | (전체 B) | Phase 1 수동 입력 | Phase 2 App Clip | — |
| FCM B.5 | (전체 B) | (전체 B) | Push 안정화 | — |

---

## 작업 순서 (의제 결정 후)

1. **의제 1~6 결정 완료 시**: Part A §1.4~§1.9 + §2.x 채움
2. **kotlin-expert + flutter-expert 협업**: 각 항목 매핑 검증
3. **결정 누적**: `flutter/WORKING_DOC.md` §5에 결정 근거 기록
4. **MVP/*.md 작성 진입**: 본 인벤토리를 기반으로 MODELS.md, NOTIFIERS.md 등 상세 매핑

---

## 참조

- `ios/FUNCTIONAL_INVENTORY.md` — Swift 트랙 원본 (iOS 컬럼 기준 자료)
- `ios/SHARED_LOGIC.md` — Firestore·상태·정산 명세
- `flutter/driver_app/PLAN.md`, `flutter/customer_app/PLAN.md`
- `flutter/WORKING_DOC.md` — 의제 14개
- Kotlin 원본:
  - `driver_app/app/src/main/java/com/designated/driverapp/`
  - `customer_app/app/src/main/java/com/designated/customer/`
- Flutter 현재 구현: `driver_app_flutter/lib/`
