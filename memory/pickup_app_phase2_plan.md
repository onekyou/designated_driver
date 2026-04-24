---
name: 픽업기사앱 Phase 2 플랜
description: MVP(2026-04-24) 이후 추가할 기능 로드맵 — pickup_assignments / FCM / 내 담당 탭 / 콜매니저 배정 UI / pickup_drivers 상태 필드
type: project
---

# 픽업기사앱 Phase 2 플랜

## 컨텍스트

- **Phase 1 MVP 완료** (2026-04-24, commit `58428830`): 로그인/회원가입/읽기전용 대시보드
- MVP 스코프 밖이었던 구 PLAN(`pickup_driver_app/PLAN.md`)의 핵심 기능 재진입 준비
- MVP에서 의도적으로 제거/연기한 모든 기능이 여기 모임

## 핵심 재사용 포인트 (MVP 산출물)

| 항목 | 경로 |
|------|------|
| build.gradle (firebase-messaging-ktx 이미 포함) | `pickup_driver_app/app/build.gradle` |
| AppModule (FirebaseAuth/Firestore/SharedPreferences) | `pickup_driver_app/app/src/main/java/com/designated/pickupdriver/di/AppModule.kt` |
| Constants (pickup 전용 상수들) | `pickup_driver_app/app/src/main/java/com/designated/pickupdriver/data/Constants.kt` |
| Navigation (Scaffold { NavHost } 패턴) | `pickup_driver_app/app/src/main/java/com/designated/pickupdriver/MainActivity.kt` |
| DashboardViewModel (snapshot listener 1개) | `pickup_driver_app/.../ui/dashboard/DashboardViewModel.kt` |

**핵심 전제**: MVP 구조를 파괴하지 않고 추가만으로 Phase 2 도달 가능. 플랜 v2 `C:\Users\kala1\.claude\plans\tranquil-puzzling-squirrel.md` 참조.

---

## Phase 2 작업 항목 (의존성 순)

### A. 서버측 (0 → 1)

#### A1. Firestore 컬렉션 신설: `pickup_assignments`
```
provinces/{p}/cities/{c}/offices/{o}/pickup_assignments/{assignmentId}
  ├── pickupDriverId    (픽업기사 UID)
  ├── pickupDriverName
  ├── driverId          (대리기사 UID — 담당 대상)
  ├── driverName
  ├── status            (ACTIVE | COMPLETED | CANCELLED)
  ├── assignedAt        (timestamp)
  └── completedAt       (timestamp?)
```

#### A2. `firestore.rules` 추가 (`pickup_assignments` 블록)
- read: `isOfficeAdmin` OR (픽업기사 본인 = `resource.data.pickupDriverId == request.auth.uid`)
- create: `isOfficeAdmin` (콜매니저가 발급)
- update: `isOfficeAdmin` OR (픽업기사 본인이 ACTIVE → COMPLETED 전환)
- `calls` / `designated_drivers` 읽기는 이미 모든 인증 사용자 허용 상태 — 변경 불필요

#### A3. `pickup_drivers` 스키마 확장
- `fcmToken: string` — FCM 토큰 저장용 신규 필드
- `status: string` — WAITING | ON_TASK | OFFLINE (런타임 상태)
- 마이그레이션 스크립트 선택: 기존 문서에 기본값 주입 (`functions/scripts/backfill-pickup-drivers-v2.js`)

#### A4. Cloud Functions 신규 2개
- `onPickupAssignmentCreated` (onDocumentCreated) — `pickup_assignments` 문서 생성 시 대상 픽업기사의 `fcmToken` 조회 → FCM "픽업 배정 알림" 전송
- `onPickupAssignmentUpdated` (onDocumentUpdated) — `status == COMPLETED|CANCELLED` 전환 시 관리자에게 FCM

---

### B. 픽업기사앱 확장

#### B1. `MyFirebaseMessagingService` 신규 파일
- 경로: `pickup_driver_app/app/src/main/java/com/designated/pickupdriver/MyFirebaseMessagingService.kt`
- AndroidManifest에 `<service>` + `<intent-filter>` 선언 추가
- `onNewToken()` → `pickup_drivers/{uid}.fcmToken` 업데이트
- `onMessageReceived()` → data payload type별 처리 (`ASSIGNMENT_CREATED` 등) → NotificationCompat.Builder 로 노티 + LocalBroadcast/Flow로 UI 갱신 트리거

#### B2. 로그인 시 FCM 토큰 저장
- `LoginViewModel.findPickupDriverDocumentAndSaveInfo()` 성공 직후 `FirebaseMessaging.getInstance().token` 조회 → `pickup_drivers/{uid}.fcmToken` 업데이트
- 로그아웃 시 토큰 nullify (선택)

#### B3. "내 담당" 탭 + BottomNavigation
- MainActivity의 Scaffold { NavHost } 를 "main" 컨테이너 라우트로 변경:
  - `main` 라우트 = Scaffold(bottomBar = BottomNavigation) + NavHost (`dashboard`, `myAssignments`)
  - `login`, `signup`은 기존 그대로
- 신규 화면: `ui/assignments/MyAssignmentsScreen.kt` + `MyAssignmentsViewModel.kt`
- `pickup_assignments` where `pickupDriverId == uid` AND `status == ACTIVE` snapshot listener
- 각 assignment 카드: 대리기사 이름 + `designated_drivers/{driverId}` 조회(one-shot 또는 listener)로 현재 상태 → 자동 "송출 필요/대기/회수 필요" 분류

#### B4. DELIVER/RETRIEVE 자동 판단 로직
- 대리기사 현재 콜 상태 → 픽업 임무 유형:
  ```
  ASSIGNED / ACCEPTED → DELIVER (고객 → 기사 위치로 이동, 목적지 = call.customerAddress)
  IN_PROGRESS → 대기/뒤따르기
  AWAITING_SETTLEMENT / COMPLETED → RETRIEVE (운행 종료 위치 → 사무실, 목적지 = call.destination_set)
  ```
- 각 기사의 현재 `assignedCallId` → `calls/{callId}` 읽기 (캐시 활용)

#### B5. 완료 액션
- 픽업기사가 카드에서 [완료] 버튼 → `pickup_assignments/{id}.status = COMPLETED`
- rules가 이 전환을 픽업기사 본인에게만 허용

#### B6. 자동로그인/오프라인 로그인 (선택)
- driver_app `SecurePreferencesManager` + `SessionManager` + `UserSession` 복사
- LoginViewModel 의 init / autoLogin / attemptOfflineLogin 재이식
- 우선순위 낮음 — 사용자 요청 시에만

---

### C. 콜매니저 확장

#### C1. 기사 목록/배차 팝업에 "픽업기사 배정" 버튼
- `DashboardScreen.kt` / `DashboardViewModel.kt` 의 각 기사 카드 컨텍스트에 [픽업 배정] 버튼 추가
- 클릭 시 픽업기사 목록 다이얼로그(`PickupAssignDialog`) → 선택 → `pickup_assignments` 생성

#### C2. 픽업기사 현황 패널
- 대시보드 하단 또는 별도 Drawer에 픽업기사 리스트
- 각 픽업기사의 현재 ACTIVE assignments 개수 표시 (e.g., "이기사: 임무중 (2건)")

#### C3. 매칭 해제/재배정
- 픽업기사 현황 패널에서 assignment 우클릭/롱프레스 → 해제 or 다른 픽업기사로 재배정

---

## 구현 순서 권장

1. **A1~A3 (서버 스키마)** — 코드 없이 rules + CF 뼈대만. 먼저 확정.
2. **B2 (로그인 FCM 토큰 저장)** — 가장 작은 변경, 검증 쉬움.
3. **B1 (FCM 서비스)** — 알림 받기 시작.
4. **C1+A1 (콜매니저 배정 UI + 실제 pickup_assignments 발급)** — 엔드투엔드 경로 활성화.
5. **A4 (CF `onPickupAssignmentCreated`)** — 픽업기사에게 푸시 실제 도달.
6. **B3~B5 (내 담당 탭 + DELIVER/RETRIEVE 자동 판단 + 완료 액션)** — 가장 큰 UX 변화.
7. **C2~C3 (콜매니저 현황 패널 + 해제/재배정)** — 운영 편의.
8. **B6 (자동로그인)** — 선택 기능.

---

## 위험 / 결정 필요 사항

- **pickup_assignments 단위가 "기사별"인지 "콜별"인지 최종 확정** — 구 PLAN은 기사 단위(1 픽업기사 : N 대리기사). 현장 피드백 후 확정.
- **FCM 멀티 디바이스 전략** — 픽업기사가 여러 기기 로그인 시 토큰 처리 (배열 필드 or 최신 토큰 덮어쓰기)
- **기존 PLAN.md의 "송출/회수/대기" 자동 판단 로직이 실제 대리기사 상태 머신과 정합하는지 검증 필요**
- **전략 피벗 유의**: `memory/strategy_pivot_2026-04-21.md` 에 따라 대리 관련 투자 최소. Phase 2는 "첫 실사용자(박상준)가 픽업 기능 요청했을 때" 즉시 진입 가능한 준비 상태만 유지.

---

## 참조

- MVP 플랜: `C:\Users\kala1\.claude\plans\tranquil-puzzling-squirrel.md`
- MVP 커밋: `58428830` (2026-04-24)
- 구 PLAN (Phase 2 스코프 원본): `pickup_driver_app/PLAN.md`
- 콜매니저 approveDriver 분기 (변경 불필요, 이미 지원): `call_manager/.../PendingDriversViewModel.kt:170-179`
- Firestore rules pickup_drivers 블록: `firestore.rules:91-93, 209-214`
