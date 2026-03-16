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

| 기능 | 파일 | 위치 |
|------|------|------|
| 콜 요청 | `CallService.kt` | requestCall (L18) |
| 콜 취소 | `CallService.kt` | cancelCall (L33, runTransaction → CANCELLED_BY_CUSTOMER) |
| 활성 콜 조회 | `CallService.kt` | getActiveCall (L73, FCM 미수신 시 fallback) |

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
