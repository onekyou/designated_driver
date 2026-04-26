# Flutter 기사앱 제작 계획서 (v3.2 — 교차검증 반영)

## Context
Kotlin 기사앱(95% 완성)을 참고하여 Flutter 기사앱을 완성. Android+iOS 동시 지원.
Flutter 앱은 Clean Architecture가 구축되어 있으나 과도한 추상화 + Firestore 경로 오류 + 트랜잭션 미사용 + Firebase 설정 누락 등 구조적 문제 존재.
v1→v2→v3 3차 검토 + 최종 교차검증을 통해 **최소 레이어 아키텍처**로 확정.
v3.2: 2026-03-27 Flutter/Kotlin 코드 교차검증 후 6개 불일치 항목 보완.

---

## 최종 아키텍처

```
┌─────────────────────────────────────┐
│  UI (Flutter Widgets)               │
│  HomeScreen, SettlementScreen...    │
└──────────┬──────────────────────────┘
           │ ref.watch / ref.read
┌──────────▼──────────────────────────┐
│  Notifiers (Riverpod StateNotifier) │
│  DriverWorkflowNotifier → Firestore │  ← 콜/운행/기사 상태 직접 처리
│  SettlementNotifier → Repository    │  ← 정산만 오프라인 동기화 필요
│  AuthNotifier → Repository          │  ← 인증만 오프라인 로그인 필요
└──────────┬──────────────────────────┘
           │
    ┌──────┴──────────────┐
    │                     │
┌───▼──────────┐  ┌───────▼────────────┐
│  Firestore   │  │  Repository        │
│  (직접 호출) │  │  (Auth/Settlement) │
│  콜, 기사,   │  │  오프라인 로직 有  │
│  운행 상태   │  │                    │
└──────────────┘  └────────────────────┘
```

**콜/기사/운행**: Notifier → Firestore 직접 (Kotlin ViewModel과 동일)
**인증**: Notifier → AuthRepository (오프라인 로그인, 자격증명 저장)
**정산**: Notifier → SettlementRepository (오프라인 캐시 + Firestore 동기화)

---

## Phase 1: 구조 정리 + 핵심 워크플로우 (통합)

**목표**: Firebase 설정 수정 + 과도한 추상화 제거 + Firestore 경로 수정 + 핵심 워크플로우 동작

### 1.0 Firebase 설정 수정 (최우선)
| 문제 | 해결 |
|------|------|
| **Android `google-services.json` 누락** | Firebase Console에서 `com.designated.driverapp` 앱 등록 → 다운로드 → `android/app/` 배치 |
| **iOS `GoogleService-Info.plist` 불일치** | Bundle ID `driverAppFlutter` ≠ `driverapp`. Firebase Console에서 재생성 또는 Bundle ID 통일 |
| **`firebase_options.dart` 검증** | FlutterFire CLI (`flutterfire configure`) 재실행으로 자동 갱신 |

### 1.1 삭제 대상 (~40개 파일)
| 대상 | 파일 수 | 이유 |
|------|---------|------|
| `features/*/domain/usecases/` | 12개 | repository 감싸기만 함 (auth 4 + call 4 + trip 3 + driver 1) |
| `features/call/data/` (repository + datasource) | 4개 | Notifier에서 직접 Firestore |
| **`features/trip/` 전체** | **~8개** | **Kotlin에서 Trip은 별도 컬렉션 아님 — Call 상태 전이로 처리. Call 엔티티에 통합** |
| `features/driver/data/` | 4개 | Notifier에서 직접 Firestore |
| `features/*/domain/repositories/` (call, driver) | 2개 | 인터페이스 불필요 |
| `features/customer/domain/usecases/` | 1개 | UseCase 제거 일관 적용 |
| `features/customer/data/` | 4개 | Firestore 직접 읽기로 전환 (포인트 계산은 서버 측) |
| `features/location/data/` | 3개 | TODO만 있는 미구현 코드 |
| `core/di/injection.dart` | 1개 | GetIt → Riverpod |
| `core/usecases/usecase.dart` | 1개 | UseCase 베이스 불필요 |
| `core/error/failures.dart` | 1개 | Either 제거 |
| `lib/models/` (중복 3개) | 3개 | domain entity와 중복 |
| `lib/core/constants.dart` | 1개 | app_constants.dart와 중복 |
| `lib/screens/home_screen.dart` (구버전) | 1개 | home_screen_new.dart만 유지 |
| `lib/services/auth_service.dart` | 1개 | AuthNotifier와 중복 |
| 기존 CallNotifier, DriverNotifier, TripNotifier + state | 6개 | WorkflowNotifier로 통합 |

> **v3.2 변경**: Trip feature 전체 삭제 (별도 컬렉션 아님), Customer data layer 삭제 추가, 파일 수 36→~40개

### 1.2 Firestore 경로 수정
- `regions/{r}/offices/{o}` → `provinces/{p}/cities/{c}/offices/{o}`
- `regionId` → `provinceId` + `cityId` (엔티티, 상수, DataSource 전부)
- **⚠️ fcm_service.dart 포함** — 현재 `regions/{regionId}/offices/{officeId}/designated_drivers/{driverId}`로 FCM 토큰 저장 중, 반드시 수정

> **v3.2 추가**: fcm_service.dart FCM 토큰 저장 경로도 수정 대상에 포함

### 1.3 Either → try-catch 전환
- `dartz` 패키지 제거
- Repository: `Either<Failure, T>` → `Future<T>` (실패 시 throw)
- Notifier: `fold()` → `try-catch`

### 1.4 Riverpod DI Provider (`lib/core/providers.dart`, ~60줄)
### 1.5 DriverScreenUiState (Freezed, ~80줄)
### 1.6 DriverWorkflowNotifier (핵심, ~600줄) — Firestore 직접, 낙관적 UI + 롤백

**⚠️ CallStatus enum Kotlin 완전 일치 필수 (v3.2 추가)**:
```
콜 상태: WAITING, ASSIGNED, ACCEPTED, IN_PROGRESS, AWAITING_SETTLEMENT, COMPLETED
취소 3종: CANCELED (관리자), CANCELLED_BY_DRIVER (기사), CANCELLED_BY_CUSTOMER (고객)
기타: HOLD (재배차 대기), SHARED_WAITING, CLAIMED
```
- Flutter 기존 `PENDING` → `WAITING`, `PICKED_UP` → `IN_PROGRESS`로 변경
- Trip 관련 상태는 Call 상태 전이로 통합 (별도 TripStatus enum 삭제)
### 1.7 HomeScreen (~600줄)
### 1.8 서브 화면 (offline, waiting, new_call_popup, in_progress, trip_preparation)
### 1.9 GoRouter 내비게이션 (~80줄)
### 1.10 AppLifecycleObserver (~40줄) — Presence 연동

**예상 규모**: ~2,300줄 신규 + 36개 파일 삭제

---

## Phase 2: 정산 시스템

- 정산 엔티티 + SettlementCalc (순수 함수, ~330줄)
- SettlementNotifier (~400줄)
- Settlement Repository (sqflite + Firestore, ~380줄)
- HistorySettlementScreen (~800줄)

**예상 규모**: ~1,900줄 신규

---

## Phase 3: 서비스 + 네이티브 기능

- Presence Service (firebase_database, ~120줄)
- Foreground Service (flutter_foreground_task, ~80줄)
- Address Search (카카오 API + dio, ~130줄)
- Voice Input + 한국어 숫자 변환 (~100줄)
- 네이티브 LockScreenActivity + Channel (~270줄)
- 카카오 API 키 환경 설정

**예상 규모**: ~700줄 신규

---

## Phase 4: FCM 완성

- FCM Service 재작성 (~300줄) — 6개 메시지 타입:
  1. `call_assigned` — 배차 알림 (포그라운드: LocalBroadcast, 백그라운드: LockScreenActivity)
  2. `call_cancelled` — 취소 알림 (항상 시스템 알림 + UI 갱신)
  3. `SETTLEMENT_FINALIZED` — 정산 확정 알림
  4. `SETTLEMENT_CONFIRMED` — 관리자 정산 승인
  5. `SETTLEMENT_REJECTED` — 관리자 정산 반려
  6. `CARRYOVER_TRANSFERRED` — 이월금 이체 완료
- Delivery ACK (~30줄) — Cloud Function `acknowledgeNotification` 호출
- 알림 탭 → GoRouter Deep Link (~40줄)

> **v3.2 추가**: FCM 6종 구체 목록 및 각 타입별 처리 방식 명시

**예상 규모**: ~370줄 신규

---

## Phase 5: 나머지 화면

- signup, forgot_password, call_details, referral_qr, 로그인 개선
- **예상 규모**: ~750줄 신규

---

## Phase 6: Kotlin→Flutter 전환 준비

- 패키지명 전략 결정 (같은 리스팅 vs 별도)
- FCM 토큰 공존 문제 해결
- iOS 배포 (Apple Developer, APNs, App Store Connect)

---

## pubspec.yaml 변경

**추가**: firebase_database, cloud_functions, flutter_foreground_task, sqflite, workmanager, audioplayers, vibration, go_router
**삭제**: get_it, injectable, hive, dartz, equatable

---

## 전체 규모

| 항목 | 수치 |
|------|------|
| 신규 코드 | ~6,020줄 |
| 최종 파일 수 | ~40개 |
| 삭제 파일 | ~40개 (v3.2: Trip 전체 + Customer data 추가) |
| Phase 수 | 6 (Phase 2+3 병행 가능) |

---

## 위험 요소

| 위험 | 수준 | 대응 |
|------|------|------|
| Firestore 경로 누락 | 높음 | `regionId` 전체 Grep + **fcm_service.dart 포함** |
| CallStatus enum 불일치 | 높음 | Kotlin CallStatus와 1:1 매핑 검증 (v3.2 추가) |
| 정산 계산 정밀도 | 높음 | Kotlin 테스트 1:1 포팅 |
| FCM 백그라운드 | 높음 | onResume 시 refreshActiveCallStatus() |
| 잠금화면 | 중간 | 네이티브 LockScreenActivity 유지 |
| 낙관적 UI 롤백 | 중간 | prevState 캡처 + try-catch |
| iOS 차이 | 중간 | Critical Alert, 부팅자동시작 불가 수용 |
