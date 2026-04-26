# 프로젝트 특징 + 핵심 코드 맵

## 사업 구조

지방 대리운전 회사 통합 관리 플랫폼. **관리자 1명이 모든 것을 처리**하는 극한 멀티태스킹 환경.

### 인력 구성 (사무실당)
- 관리자 1명 (콜 수신 + 배차 + 픽업 운전 겸직)
- 대리기사 5명
- 픽업기사 3명 (관리자 포함, 대리기사 운행 후 데리러 감)

### 전화 시스템
- 관리자가 **3대 폰 항상 소지** (각각 다른 번호)
  - 메인폰: Call Manager + 내장 Call Detector
  - 서브폰 2대: Call Detector 전용
- 번호 유래: 폐업 사무실 번호를 매입하여 고객층 확보 (지방 관행)
- 감지 방식: **통화버튼 클릭 → 통화 → 종료 후 감지** (벨만 울리면 감지 안 됨)
- 고객 정보: 기기 연락처(Contacts)에 단골 이름+주소 저장 → 자동 매칭

### 배차 흐름
- **핵심**: 전화 받은 그 폰에서 바로 배차 (DispatchActivity)
- Call Manager는 전체 현황 + 놓친 콜 관리 + 수동 새콜생성 + 정산
- 부재중 콜: 다른 폰 통화 중 → 못 받음 → Call Manager "새콜생성"으로 수동 배차

### 영업 환경
- 영업시간: 오후 5시 ~ 다음날 자정 또는 새벽 2시 (사무실마다 다름)
- 일일 콜: 평균 50~100건
- 피크 타임: 저녁 8시, 자정 (2회)
- 기사 대기: 보통 사무실, 일부 거점지역 자기 차에서 대기

### 현재 최대 pain point
- **정산 불일치** → 이 시스템의 핵심 목표가 정산 정확성 확보

---

## 앱별 핵심 코드 맵

### Call Detector (`call_detector/`)
전화 감지 + 즉시 배차 전용

| 기능 | 파일 | 위치 |
|------|------|------|
| 전화 상태 감지 | `CallDetectorService.kt` | onStartCommand (RINGING→OFFHOOK→IDLE) |
| 발신 전화 필터링 | `CallDetectorService.kt` | wasRinging 플래그 (OFFHOOK without RINGING → skip) |
| 중복 콜 방지 (서비스) | `CallDetectorService.kt` | PROCESSING_THRESHOLD_MS = 5초 |
| 중복 콜 방지 (Firestore) | `CallDetectorService.kt` | DUPLICATE_CALL_CHECK_MS = 10초 |
| 제외번호 필터 | `ExcludeNumberManager.kt` | IDLE + RINGING 양쪽에서 체크 |
| 즉시 배차 팝업 | `DispatchActivity.kt` | 기사 목록 실시간 리스너 (WAITING/ONLINE만) |
| 배차 실행 | `DispatchActivity.kt` | updateCallWithDriver (runTransaction) |
| 부팅 자동시작 | `BootCompletedReceiver.kt` | service_stopped_by_user 플래그 존중 |
| 서비스 복구 | `CallDetectorService.kt` | START_STICKY |

### Call Manager (`call_manager/`)
관리자용 종합 관리

| 기능 | 파일 | 위치 |
|------|------|------|
| 배차 | `DashboardViewModel.kt` | assignCallToDriver (L689, runTransaction) |
| 취소 | `DashboardViewModel.kt` | cancelCall (L810, runTransaction → CANCELED) |
| 수동 새콜생성 | `DashboardViewModel.kt` | createCallWithInputData (L1387, 10초 중복체크) |
| 빈 콜 생성 | `DashboardViewModel.kt` | createEmptyCallAndShowAssignment (L1255) |
| 공유콜 생성 | `DashboardViewModel.kt` | shareCall (L1567, SHARED 재공유 차단) |
| 공유콜 수임 | `DashboardViewModel.kt` | claimSharedCallWithDetails (L1625, runTransaction) |
| 공유콜 취소 | `DashboardViewModel.kt` | reopenSharedCall (L1718) |
| 내장 Detector | `CallDetectorService.kt` | Manager 전용 (fallback 경로 있어 오프라인 복원력 높음) |
| 정산 확인 | `SettlementViewModel.kt` | confirmDailySettlement (PENDING_CONFIRM→CONFIRMED) |
| 이월 처리 | `SettlementViewModel.kt` | transferCarryOver (TRANSFERRED + FCM) |
| 부팅 자동시작 | `BootReceiver.kt` | 로그인 상태일 때만 |

### Driver App (`driver_app/`)
기사용 운행 + 정산

| 기능 | 파일 | 위치 |
|------|------|------|
| 콜 수락 | `DriverViewModel.kt` | acceptCall (L367, runTransaction, 실패 시 UI 롤백) |
| 콜 거절 | `DriverViewModel.kt` | rejectCall (L473, → WAITING 복귀) |
| 운행 취소 | `DriverViewModel.kt` | cancelTrip (L522, → HOLD) |
| 운행 시작 | `DriverViewModel.kt` | startDriving (L576, → IN_PROGRESS) |
| 운행 완료 | `DriverViewModel.kt` | completeCall (L636, 단순 update → 오프라인 OK) |
| 정산 확정 | `DriverViewModel.kt` | confirmAndFinalizeTrip (L666, runTransaction → COMPLETED) |
| 정산 제출 | `DriverViewModel.kt` | submitDailySettlement (L1315, PENDING_CONFIRM) |
| 이월 수령확인 | `DriverViewModel.kt` | confirmReceiveCarryOver (L1281, → SETTLED) |
| 취소 수신 처리 | `DriverViewModel.kt` | handleCallCancelled (L903) |
| 오프라인 화면 | `OfflineScreen.kt` | NetworkMonitor 기반 |
| FCM 수신 | `MyFirebaseMessagingService.kt` | data-only + high priority |
| FCM 백그라운드 | `MyFirebaseMessagingService.kt` | IMPORTANCE_HIGH + FullScreenIntent |
| FCM Kill 복구 | `MyFirebaseMessagingService.kt` | data-only → 서비스 자동 instantiate |
| FCM 토큰 복구 | `MyFirebaseMessagingService.kt` | retryPendingFcmToken (MainActivity.onCreate) |
| 부팅 자동시작 | 없음 | **기사가 수동 실행 필요** |

### Customer App (`customer_app/`)
고객용 (미배포)

#### 인증/온보딩 흐름
```
QR 스캔 → Play Store(referrer 포함) → 앱 설치 → Install Referrer 자동 파싱 → 사무실 귀속
화면: LOADING → OFFICE_SELECTION → TERMS_AGREEMENT → PHONE_AUTH → PROFILE_SETUP → MAIN
```
- QR 귀속 성공 시 OFFICE_SELECTION 자동 스킵
- 인증: Firebase Phone Auth (SMS, +82 자동 변환)
- 재방문: FirebaseAuth.currentUser + 프로필 존재 → 바로 MAIN

| 기능 | 파일 | 위치 |
|------|------|------|
| **온보딩/귀속** | | |
| Install Referrer 처리 | `MainActivity.kt` | checkInstallReferrer (L176, onCreate에서 호출) |
| Referrer 파싱+저장 | `MainActivity.kt` | parseAndSaveReferrer (L241, p/c/o + 연락처 → SharedPrefs) |
| 초기 화면 판단 | `MainActivity.kt` | LaunchedEffect (L449, SharedPrefs → 화면 분기) |
| 사무실 선택 (수동) | `OfficeSelectionScreen.kt` | QR 없을 때 수동 선택 |
| 약관 동의 | `TermsAgreementScreen.kt` | 필수/선택 약관 UI |
| 개인정보처리방침 | `PrivacyPolicyScreen.kt` | 방침 열람 |
| **인증** | | |
| SMS 인증 | `PhoneAuthScreen.kt` | 전화번호 입력 → 인증코드 검증 |
| Phone Auth 로직 | `PhoneAuthViewModel.kt` | Firebase Phone Auth (+82 변환) |
| 익명 인증 (미사용) | `AnonymousAuthViewModel.kt` | 테스트용, 어디서도 호출 안 됨 |
| **프로필/저장** | | |
| 프로필 입력 | `ProfileSetupScreen.kt` | 닉네임, 주소 입력 |
| Firestore 저장 | `ProfileSetupViewModel.kt` | offices/{o}/customers/{uid} + customerInfo/{phone} |
| SharedPreferences | `PreferencesManager.kt` | officeId/provinceId/cityId + 연락처 + 기사추천 |
| **핵심 기능** | | |
| 콜 요청 | `CallService.kt` | requestCall (L18) |
| 콜 취소 | `CallService.kt` | cancelCall (L33, runTransaction → CANCELLED_BY_CUSTOMER) |
| 활성 콜 조회 | `CallService.kt` | getActiveCall (L73, FCM 미수신 시 fallback) |
| FCM 토큰 저장 | `MainActivity.kt` | requestAndSaveFcmToken (L330, customers + customerInfo 이중 저장) |

#### QR 귀속 파라미터 (Install Referrer)
| 파라미터 | 용도 | 필수 |
|----------|------|------|
| `p` (또는 `r`) | provinceId | ✅ |
| `c` | cityId | ✅ |
| `o` | officeId | ✅ |
| `phone` | 사무실 전화번호 | 선택 |
| `bank` | 은행명 | 선택 |
| `account` | 계좌번호 | 선택 |
| `holder` | 예금주 | 선택 |
| `driver` | 기사 ID | 선택 |
| `driverName` | 기사 이름 | 선택 |

- QR URL 생성: Call Manager `AttributionManagementViewModel.kt` (L252)
- QR 설정 저장: Firestore `settings/attribution` 문서
- 랜딩페이지: `customer_app/landing/app/download/page.tsx` (Next.js)
- Remote Config `allowDirectInstall=false` → QR 없이 설치 차단

#### 테스트 코드 잔존 (배포 전 제거 필요)
- `OfficeSelectionScreen.kt` L76: VIP 사무실 테스트 버튼
- `ProfileSetupViewModel.kt` L87: testOffice → VIP 등급 자동 부여
- `AnonymousAuthViewModel.kt`: 미사용 익명 인증 클래스

### Cloud Functions (`functions/`)

| 기능 | 파일 | 트리거 |
|------|------|--------|
| 배차 FCM | `index.ts` | oncallassigned (L350, onDocumentWritten) |
| 상태변경 FCM | `index.ts` | onCallStatusChanged (L1706) |
| 공유콜 알림 | `index.ts` | onSharedCallCreated (L781) |
| 공유콜 복사 | `index.ts` | onSharedCallClaimed (L1017) |
| 기사/고객 취소 처리 | `index.ts` | onCallCancelledByDriver (L2740) |
| 배차 타임아웃 | `index.ts` | checkAssignedTimeout (L3253, 매 1분, 3분 기본) |
| 정산 자동 추가 | `index.ts` | onCallCompletedUpdateSettlement (L4470) |
| 관리자 취소 FCM | `index.ts` | notifyDriverCancellation (L4695, Callable) |
| 정산 세션 관리 | `handlers/settlement.ts` | addCallToSettlementSession (L105, runTransaction) |
| workDate 계산 | `handlers/settlement.ts` | calculateWorkDate (L58, KST 6AM 기준) |

---

## 상태 전이 맵

### 콜 상태
```
WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED
                 ↓         ↓            ↓
              (타임아웃)  (거절)      (운행취소)
              → WAITING  → WAITING    → HOLD → 재배차 → ASSIGNED

취소 경로:
  CANCELED (관리자)
  CANCELLED_BY_DRIVER (기사)
  CANCELLED_BY_CUSTOMER (고객, WAITING/ASSIGNED만)

공유 경로:
  WAITING → SHARED_WAITING → (타 사무실 수임 시) CLAIMED
```

### 기사 상태
```
ONLINE/WAITING → ASSIGNED → PREPARING → ON_TRIP → WAITING (완료 후 복귀)
```

### 정산 플로우
```
Driver submitDailySettlement → PENDING_CONFIRM
Manager confirmDailySettlement → CONFIRMED
Manager transferCarryOver → TRANSFERRED
Driver confirmReceiveCarryOver → SETTLED
```

---

## 핵심 설계 패턴

### 이중 배차 방지
모든 배차/수락/취소에 `runTransaction` 사용. 트랜잭션 내에서 현재 status 확인 후 업데이트.

### 중복 콜 방지
- Detector: 5초(서비스레벨) + 10초(Firestore레벨) 이중 체크
- Manager 수동생성: 10초 Firestore 체크 (fdd01be2에서 추가)

### 공유콜 재공유 차단
- shareCall에서 callType=="SHARED" 체크 (fdd01be2에서 추가)

### 오프라인 처리
- `add()/update()`: Firestore 로컬 캐시에 즉시 완료 → 복구 시 자동 동기화
- `runTransaction()`: **서버 왕복 필수** → 오프라인 시 실패 → catch에서 에러 메시지 + UI 복구

### FCM 전략
- 모든 FCM: **data-only + priority: "high"**
- 앱 Kill 상태에서도 서비스 자동 instantiate → onMessageReceived 호출
- 백그라운드: IMPORTANCE_HIGH + FullScreenIntent → heads-up 알림 + 잠금화면

### 정산 공식
```
officeDeposit = totalFare × depositRatio / 100  (사무실 몫)
driverShare = totalFare - officeDeposit          (기사 몫)
realDeposit = cashReceived - driverShare         (실 납입액)
carryOver = originalCarryOver - finalDeposit + realDeposit
```
- depositRatio: 사무실별 수수료율 (세션 생성 시 lock, 당일 변경 영향 없음)
- workDate: KST 6AM 기준 (새벽 6시 전 완료 → 전날)

---

## Presence 시스템 결론 (2026-03-17 확정)

**리스너 방식 폐기**
- 오감지, 2~4분 지연, onDisconnect 덮어쓰기 발생 → 도움보다 문제가 많음
- Realtime DB 기반 실시간 리스너 구조는 본 시스템에서 부적합

**CF 스케줄러 방식 채택**
- 매 1분 presence `.get()` 조회 → 오감지 없음, 서버 측, 앱 무관
- **배차 시점 보호**: `oncallassigned` CF가 즉시 presence 확인 (기존)
- **배차~완료 보호**: `checkAssignedTimeout` CF가 매 1분 체크 (신규)
- online/background는 동일 취급, offline만 문제로 판단

**3/18 보정**: `#1 offline 즉시 복귀 → 타임아웃 적용` (1분 유예로 FCM 도달 기회 보장)

---

## 5번째 앱: Pickup Driver App (2026-04-26 추가, 운영 중)

- **applicationId**: `com.designated.pickupapp` (Firebase Console 기존 등록 재사용)
- **저장소**: 순수 Firestore snapshot listener (단일, FCM/Room 없음, 앱 열려있을 때만 실시간)
- **대시보드**: 진행중 콜 5상태 표시 + `departure_set → waypoints_set → destination_set` headlineSmall+Bold 경로 + 요금 포맷
- **로그인/회원가입**: driver_app fork 기반
- **서버측 변경 0**: 기존 `approveDriver()` 가 `driverType="픽업기사"` → pickup_drivers 자동 라우팅, firestore.rules 이미 완비
- **상세 플랜**: `memory/designated_drive/pickup_driver_app/plan_pickup_driver_app.md`, `pickup_app_phase2_plan.md`

## 6번째 앱: 업소용 앱 (개발 예정, 마스터 N=1 무기 직접 의존)

- **사용자**: 식당 사장님/직원
- **핵심 기능**: 단순호출 버튼 (대리/택시) + 포인트 표시
- **우선순위**: **1순위** (체크리스트 B-3 클코 작업 지시 항목)
- **상세**: `memory/callmadang_master_2026-04-27.md` §10.2 + `memory/restaurant/README.md`
