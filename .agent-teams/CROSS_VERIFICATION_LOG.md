# 크로스 검증 기록

> 마지막 업데이트: 2026-02-24 13:00 KST

## 2026-02-24 세션 1 (V2 분석)

### CROSS-01: 기사 상태 4-Way 불일치

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| detector → manager | Detector 배차 시 "ON_TRIP" 설정, Manager는? | Manager는 "ASSIGNED" → 불일치 확인 |
| detector → driver | Detector 배차 후 Driver 수신 코드 일치 여부 | Driver는 콜 status만 확인, 기사 status 무시 → 기능적 문제 없음 |
| manager → driver | Manager "ASSIGNED" vs Driver enum 확인 | Driver enum에 ASSIGNED 존재 → 일치 |
| firebase → 전원 | Cloud Functions "배차중" 사용 확인 | index.ts:1198에서 한글 문자열 확인 → 4자 불일치 확정 |

**결론**: 4자 불일치 확정 → 이번 세션에서 수정 완료 (모두 "ASSIGNED"로 통일)

### CROSS-02: CallStatus enum 불일치

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| manager → 전원 | Manager enum 15개 공유 | Detector 6개, Driver 7개와 대조 |
| detector → manager | Detector enum 6개 중 실사용 1개(WAITING) | 나머지는 문자열 리터럴 사용 → enum 의미 없음 |

**결론**: Critical 유지. 통일 필요하나 기능적으로 문자열 값이 일치하는 항목은 작동함.

### CROSS-05: 내장 vs 독립 Detector 동기화

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| detector → manager | 독립 Detector의 wasRinging/ExcludeNumber 로직 | Manager 내장 Detector에 누락 확인 |

**결론**: Critical. 내장 Detector가 독립 버전보다 기능 부족.

### CROSS-07: rejectCall/cancelCall

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| driver → manager | rejectCall() 빈 함수 확인 요청 | manager 확인 → 실제로 비어있음 |
| driver → firebase | rejectCall 보안 규칙에서도 차단되는지 | ASSIGNED → HOLD 전이 규칙 확인 → 규칙도 미지원 |
| manager → driver | cancelCall 기사 상태 복구 여부 | **이번 세션에서 수정 완료** |

**결론**: rejectCall은 Critical 유지 (3중 차단: 코드 비어있음 + FCM 무시 + 보안규칙). cancelCall은 해소.

## 2026-02-24 세션 2 (코드 수정 + 재검증)

### cancelCall() 수정 후 크로스 검증

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| team-lead → driver | 수락 다이얼로그 중 취소 FCM → 팝업 닫히는지 | **포그라운드: 정상**, 백그라운드/LockScreen: 누락 |
| team-lead → manager | FCM 필드명 3자 대조 | Manager→CF→Driver 필드명 **모두 일치** |
| team-lead → firebase | 기존 트리거와 중복 FCM 여부 | onCallStatusChanged는 Manager에만 FCM → **중복 없음** |

### BUG-D12: 포인트 이중 적립

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| driver → firebase | pointTransactions vs customerPointTransactions | firebase 확인 → 컬렉션명 불일치로 중복 체크 실패 → **이중 적립 확정** |

### NEW-11: 이중 배차 경쟁

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| detector → manager | 배차 시 status 체크 없음 확인 | manager 확인 → assignCallToDriver도 체크 없음 → **양쪽 모두 미검증 확정** |

### NEW-12: Callable Functions 인증

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| firebase → 전원 | 14개 onCall 함수에 request.auth 체크 없음 | 전원 확인 → **비인증 호출 가능 확정** |

### 오탐 확인

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| detector → firebase | isOfficeAdmin nested vs top-level 경로 | team-lead 직접 확인 → top-level 3곳 일치 → **오탐** |
| driver → firebase | BUG-D09 필드명 불일치 | 의도적 설계 확인 → **오탐** |
| detector → manager | NEW-09 doc.id vs authUid | 등록 흐름에서 보장 → **오탐** |
| manager → 전원 | approveDriver 한글 문자열 | 데드코드 확인 (_showApprovalPopup 미사용) → **Low로 하향** |

### CD-01: 마감 시 RINGING+IDLE 이중 공유콜 생성

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| team-lead → manager | CD-01 수정 후 Manager 콜 목록 영향 확인 | callType 필드 필터링 여부, AFTER_HOURS_QUICK vs AFTER_HOURS 차이 | **검증 완료** |

**검증 결과**:
- Manager 쿼리에서 callType 필터링 없음 → 이중 생성 시 2건 모두 표시됨
- UI에서 "AFTER_HOURS_QUICK"은 마감콜로 인식, "AFTER_HOURS"는 일반 공유콜로 표시 (별도 이슈)
- **CD-01 수정 정당성 확인**: Manager 측에 중복 방지 로직 없으므로 생성 측(Detector)에서 방지하는 것이 올바름

**수정 내용**: `sharedCallCreatedFromRinging` 플래그로 IDLE에서 중복 생성 방지 (CallDetectorService.kt 4곳 수정)

**추가 발견 (별도 이슈)**: DashboardScreen.kt:1829에서 "AFTER_HOURS" callType이 마감콜 조건에 누락 → IDLE 경로로만 생성된 공유콜이 일반 공유콜로 표시됨

## 2026-02-24 세션 3 (Customer App 집중 분석)

### Customer App FCM 연동 크로스 검증

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| team-lead → driver | 기사 상태변경 시 Customer FCM 매핑 확인 | 6개 상태 전이 분석 | **ACCEPTED/IN_PROGRESS/AWAITING_SETTLEMENT → Customer FCM 없음 확인** |
| team-lead → manager | Manager 취소/재배차 → Customer 알림 확인 | cancelCall/assignCallToDriver 분석 | **CANCELED 상태 트리거 미감지 확인, 재배차 알림 없음 확인** |
| team-lead → firebase | 보안 규칙/포인트/FCM 함수 전체 점검 | **보안 CRITICAL 1건 + High 3건, BUG-D12 3자 불일치 업그레이드, OFFICE_CLOSED FCM 미처리** |

### 교차 확인된 이슈

| 이슈 | 확인자 | 결과 |
|------|--------|------|
| CUST-01 (Manager 취소 FCM 누락) | team-lead + manager-analyst | **양쪽 모두 확인**: "CANCELED" 상태가 onCallCancelledByDriver 조건에 없음 |
| CUST-03 (ACCEPTED/IN_PROGRESS FCM 없음) | team-lead + driver-analyst | **양쪽 모두 확인**: onCallStatusChanged는 Manager에만 FCM 전송 |
| CUST-10 / BUG-D12 (이중 포인트 적립) | team-lead + driver-analyst + firebase-analyst | **3자 확인**: CF(`customerPointTransactions`) + CustomerApp(`pointTransactions`) + DriverApp(`pointTransactions`) → 3-Way 이중 적립 확정, Critical로 업그레이드 |

### 신규 발견 (팀원 보고)

| 이슈 | 발견자 | 내용 |
|------|--------|------|
| NEW-17 (CUST-11) | manager-analyst | 재배차 시 Customer App에 기사 변경 알림 미전송 |
| RIDE_COMPLETED payload 부족 | driver-analyst | paymentMethod, cashReceived, finalFare 미포함 (LOW) |
| Manager UI isAppCustomer 미표시 | manager-analyst | 앱 고객/전화 고객 구분 UI 없음 (LOW) |
| SEC-C01~C05 보안 이슈 5건 | firebase-analyst | 콜 생성 비인증(CRITICAL), customerPoints/pointTransactions/customerInfo 소유권 미검증(High x3), 콜 취소 본인 미검증(Medium) |
| BUG-D12 3자 불일치 | firebase-analyst | CF `customerPointTransactions` vs App `pointTransactions` 컬렉션명 불일치 → 중복 체크 실패 → Critical 업그레이드 |
| OFFICE_CLOSED FCM 미처리 | firebase-analyst | CF가 전송하지만 Customer App when절에 없음 (Medium) |

## 2026-02-24 세션 4 (정산 로직 크로스 검증)

### 정산 데이터 모델 크로스 검증

| 발신 | 수신 | 내용 | 결과 |
|------|------|------|------|
| team-lead → manager | SettlementModels.kt 양쪽 비교 | SettlementSession, CallSettlement, SettlementTotals 필드 동일 | **동일 확인** (양쪽 동일한 data class) |
| team-lead → driver | SettlementRepository 오프라인 동기화 확인 | saveCallSettlement → Room DB → WorkManager 재시도 | **구현 확인**, 단 confirmAndFinalizeTrip에서 호출 안 됨 (CF 의존) |
| team-lead → firebase | settlement.ts recalculateTotals 교차 대조 | Kotlin SettlementTotals.addCall vs TS recalculateTotals | **로직 동일**, 다만 외상/포인트 switch case 누락 |

### 정산 금액 3-Way 대조

| 항목 | Manager UI (DriverSummaryScreen) | Driver App (loadTodaySettlement) | Cloud Functions (settlement.ts) |
|------|------|------|------|
| totalCredit 계산 | paymentMethod → 비현금=fare | totalFare - totalCashReceived | creditAmount 필드 (0 for 외상) |
| deposit 반올림 | **roundToInt()** | toInt() (버림) | Math.floor() (버림) |
| pointsUsed | 미읽기 (없음) | (미사용) | callData.pointsUsed |
| creditAmount 출처 | Firestore calls.creditAmount (0 for 외상) | (미사용, 대신 totalFare-cash) | callData.creditAmount (0 for 외상) |

**결론**: 라이브 UI 금액은 일치 (양쪽 paymentMethod 기반). settlementSessions 감사 데이터는 creditAmount/pointsUsed 미설정으로 부정확.

### settlementLastCleared 경로 크로스 검증

| 작업 | 쓰기 대상 | 읽기 대상 | 일치 |
|------|----------|----------|------|
| CF finalizeSettlement | offices/{oId} | - | |
| Manager loadSettlementData | - | offices/{oId} | ✓ |
| Driver clearSettlement | drivers/{dId} | - | |
| Driver loadSettlementData | - | drivers/{dId} | ✓ |
| **CF → Driver 경로** | **offices → drivers** | **불일치** | ⚠️ STL-06 |

### 이월(CarryOver) 계산 크로스 검증

| 항목 | Manager (transferCarryOver) | Driver (submitDailySettlement) | 일치 |
|------|------|------|------|
| originalCarryOver | carryOver.balance (Firestore) | carryOver.balance (Firestore) | ✓ |
| todayUnpaid 계산 | paymentMethod 기반 rawFinalDeposit | totalFare - totalCashReceived - driverShare | ✓ (동일 결과) |
| confirmDailySettlement | calculatedCarryOver → carryOver.balance | (기사가 계산) | ✓ |

**결론**: 이월 계산 양쪽 일치. Firestore 트랜잭션으로 원자적 업데이트 ✓

## 시나리오 시뮬레이션 결과 요약

### 시나리오 1 (Manager 배차): 정상 흐름 확인
- 전 단계에서 4개 앱 상태값 일치
- 비원자적 업데이트 외 문제 없음

### 시나리오 2 (Detector 배차): CROSS-01 불일치 발견 → 수정 완료
- 수정 후 시나리오 1과 동일한 상태 전이

### 시나리오 3 (기사 취소 → 재배차): rejectCall 비어있어 불가능
- CROSS-07의 핵심. 재배차 흐름 자체가 구현 안 됨

### 시나리오 4 (네트워크 불안정): 낙관적 업데이트 의존
- Driver App에 실시간 리스너 없음 → FCM 유일 경로
- 오프라인 캐시 미활용

### 시나리오 5 (동시 사용 10개 사무실): 데이터 격리 확인
- provinces/cities/offices 구조로 쿼리 격리 OK
- 보안 규칙의 `request.auth == null` 패턴이 격리 무력화 위험

### 시나리오 A (정상 하루): Manager ↔ Driver UI 금액 일치 ✓
- 10건 (현금5/포인트3/외상2) 기준 양쪽 금액 동일
- settlementSessions의 totalCredit/totalPoints만 부정확

### 시나리오 B (이월 발생): 이월 금액 양쪽 일치 ✓
- Day 1 → Day 2 이월 계산 정상 흐름 확인
- carryOver.balance 단일 금액 누적 방식

### 시나리오 C (퇴근 기사 재로그인): 기본 흐름 정상, 엣지 주의
- clearSettlement 후 재로그인: 이전 콜 미표시 ✓
- 매니저 확인(CONFIRMED) 후 재로그인 + 추가 운행: dailySettlement에 이전 건수 누락 가능 ⚠️ STL-07

### 시나리오 D (복합 이월): 결제 수단별 분리 미지원
- carryOver는 단일 balance만 관리
- 외상 잔액은 Manager Room DB에서 별도 관리 (Firestore 미저장)

### 시나리오 E (엣지 케이스):
- 강제종료 후 재시작: Firestore 기반 데이터 복구 ✓
- 네트워크 끊김: 로컬 Room 저장 후 WorkManager 재동기화 ✓
- 0원 콜: 정상 동작 ✓
- 전액 포인트 콜: Manager pointsUsed=0 문제 ⚠️, 나머지 정상
- BUG-D12 영향: 정산 금액에 직접 영향 없음, 포인트 잔액만 오염
