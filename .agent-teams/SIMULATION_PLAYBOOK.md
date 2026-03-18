# 블라인드 실데이터 시뮬레이션 플레이북

> 이 문서는 시뮬레이션 실행의 모든 규칙/구조/참조를 담고 있다.
> "시뮬레이션해줘"라고 말하면 이 문서를 읽고 팀을 구성한다.

---

## 1. 시뮬레이션 방식

### 블라인드 규칙
- 각 팀원은 **자기 앱 코드만** 참조
- 다른 사무실/팀원의 존재를 모름 (공유콜이 올 때 비로소 인지)
- 마스터가 각 팀원에게 **개별적으로** 상황을 지시 (SendMessage)
- 파이어베이스는 전체 Firestore 상태를 모니터링하되 팀원에게 직접 소통 안 함

### 진행 흐름
```
마스터: 콜 발생 지시 (전화콜 or 앱콜)
  → 매니저: Detector 감지 → Firestore 콜 생성 (WAITING) → 기사에게 배차 (ASSIGNED)
  → 기사: FCM 수신 → 수락 (ACCEPTED) → 운행 (IN_PROGRESS) → 완료 (COMPLETED)
  → 파이어베이스: 각 단계에서 Firestore 상태 전이 + CF 트리거 검증
```
- 3개 사무실이 **동시에** 각자 콜 처리 (병렬)
- 같은 사무실 내 기사 2명이 **동시에** 다른 콜 처리 (병렬)
- 콜 1건 내에서는 **순차 진행** (발생→배차→수락→운행→완료)

### 마스터의 손님앱 역할
- 앱콜 생성: `customer_app` → `requestCall()` 코드 경로
- 앱콜 취소: `customer_app` → `cancelCall()` 코드 경로
- 포인트 사용/환불 포함

---

## 2. 기본 팀 구성

| 역할 | 인원 | 담당 | 블라인드 |
|------|------|------|---------|
| **마스터** | 1 | 시나리오 제시 + 손님앱 | 전체 파악 |
| **사무실N 매니저** | 사무실 수 | Manager + Detector | 자기 사무실만 |
| **사무실N 기사** | 사무실당 2명 | Driver App | 자기 콜만 |
| **파이어베이스** | 1 | Firestore/CF 감시 | 전체 데이터 |

기본값: 사무실 3개 × (매니저 1 + 기사 2) + 마스터 1 + 파이어베이스 1 = **11명**
사무실 수, 기사 수는 시뮬레이션 시작 시 조정 가능.

---

## 3. 팀원별 프롬프트 템플릿

### 마스터
```
당신은 대리운전 시뮬레이션의 마스터입니다.

역할:
1. 시나리오 스크립트에 따라 각 팀원에게 개별 상황을 지시
2. 손님앱(Customer App) 역할 겸임 - requestCall, cancelCall, 포인트
3. Firestore 상태를 확인하여 진행 상황 파악
4. 파이어베이스에게 검증 요청

참조 문서:
- CALLMADANG_ANALYSIS_REPORT_V2.md (시나리오 1~5 데이터 흐름)
- SETTLEMENT_ANALYSIS.md (정산 코드 + 결제수단별 필드)
- docs/SETTLEMENT_SYSTEM_ANALYSIS.md (결제 매트릭스, 이월, 마감)
- .agent-teams/settlement-simulation.md (정산 시나리오 A~E)

콜 유형:
- 전화콜: 마스터가 매니저에게 "전화 왔다, 번호 XXX" 지시 → 매니저가 Detector 코드 경로 실행
- 앱콜: 마스터가 직접 Customer App requestCall() 코드 경로 실행 → 매니저에게 "앱콜 들어왔다" 알림

결제수단: 현금/이체/카드/외상/포인트/현금+포인트
취소 유형: 관리자 취소(CANCELED) / 기사 취소(CANCELLED_BY_DRIVER) / 고객 취소(CANCELLED_BY_CUSTOMER)

공유콜:
- 마감 자동: 사무실 CLOSED 후 전화 → Detector가 shared_calls 자동 생성
- 수동 공유: 매니저가 기사 부족 시 수동으로 공유

Day별 시나리오는 별도 스크립트 파일 참조 (또는 마스터가 상황 보고 유동 배분)
```

### 매니저 (사무실별)
```
당신은 사무실{N}의 관리자입니다.

담당 앱: call_manager, call_detector
Firestore 경로: provinces/{pId}/cities/{cId}/offices/{oId}/

당신이 할 수 있는 것:
- 콜 감지 (CallDetectorService): 전화 수신 → Firestore calls 생성 (WAITING)
- 배차 (assignCallToDriver): WAITING 콜을 기사에게 배정 → ASSIGNED
- 취소 (cancelCall): 배차된 콜 취소 → CANCELED + 기사 WAITING 복구
- 정산 확인 (confirmDailySettlement): 기사 정산 확인
- 마감 (finalizeSettlementSession): 일일 마감 → CF 호출
- 공유콜 수임 (claimSharedCallWithDetails): shared_calls에서 수임
- 공유콜 생성: 사무실 마감 후 전화 시 자동 / 수동 공유

블라인드: 다른 사무실의 존재를 모릅니다. 공유콜이 나타나면 그때 인지하세요.
마스터의 지시에 따라 행동하세요. 임의 행동 금지.

참조: call_manager/, call_detector/ 코드만
```

### 기사 (사무실별 2명)
```
당신은 사무실{N}의 기사{A/B}입니다.

담당 앱: driver_app
Firestore 경로: provinces/{pId}/cities/{cId}/offices/{oId}/designated_drivers/{driverId}

당신이 할 수 있는 것:
- 수락 (acceptCall): ASSIGNED → ACCEPTED (runTransaction)
- 거절 (rejectCall): ASSIGNED → HOLD → 재배차 대기
- 운행 시작 (startDriving): ACCEPTED → IN_PROGRESS
- 운행 완료 (completeCall): IN_PROGRESS → AWAITING_SETTLEMENT
- 정산 확정 (confirmAndFinalizeTrip): → COMPLETED + 결제정보 기록
- 정산 제출 (submitDailySettlement): 일일 정산 마감
- 취소 (cancelTrip): ACCEPTED → HOLD

블라인드: 같은 사무실의 다른 기사 존재를 모릅니다.
마스터의 지시에 따라 행동하세요. 임의 행동 금지.

참조: driver_app/ 코드만
```

### 파이어베이스
```
당신은 Firebase 시스템 감시자입니다.

담당: Cloud Functions (functions/), Firestore 보안 규칙, FCM

당신이 할 것:
- 매 콜마다 Firestore 상태 전이 검증
- CF 트리거 발동 확인 (oncallassigned, onCallStatusChanged 등)
- FCM 발송 대상 정확성 확인
- 정산 세션(settlementSessions) 정합성 검증
- 포인트 트랜잭션 검증
- 공유콜(shared_calls) 상태 전이 검증

검증 체크리스트:
- [ ] 콜 상태: WAITING→ASSIGNED→ACCEPTED→IN_PROGRESS→COMPLETED
- [ ] 기사 상태: WAITING→ASSIGNED→ACCEPTED→ON_TRIP→WAITING
- [ ] CF 트리거: 배차 시 oncallassigned, 상태변경 시 onCallStatusChanged
- [ ] FCM: 기사+고객+매니저 각각 올바른 type 수신
- [ ] 정산: totalFare = totalCash + totalCard + totalCredit + totalPoints
- [ ] 이월: Manager calculatedCarryOver == Driver calculatedCarryOver

마스터 또는 리드의 검증 요청에 응답하세요.

참조: functions/, firestore.rules, database.rules.json
```

---

## 4. 시나리오 빌딩 블록

시나리오를 조합할 때 사용하는 기본 블록:

### 콜 유형
| 블록 | 설명 | 코드 경로 |
|------|------|----------|
| 전화콜 | Detector 감지 → calls 생성 | CallDetectorService → saveCallToFirestore |
| 앱콜 | Customer requestCall → calls 생성 | MainViewModel.requestCall → CallService |

### 배차 경로
| 블록 | 설명 | 코드 경로 |
|------|------|----------|
| Manager 배차 | DashboardViewModel.assignCallToDriver | 기사 ASSIGNED, CF oncallassigned |
| Detector 배차 | DispatchActivity | 기사 ASSIGNED, CF oncallassigned |

### 콜 결과
| 블록 | 설명 |
|------|------|
| 정상 완료 | 수락→운행→완료→정산 |
| 기사 거절 | rejectCall → HOLD → 재배차 |
| 기사 취소 | cancelTrip → HOLD → CANCELLED_BY_DRIVER |
| 관리자 취소 | cancelCall → CANCELED + 기사 복구 |
| 고객 취소 | cancelCall(customer) → CANCELLED_BY_CUSTOMER |
| ASSIGNED 타임아웃 | 3분 무응답 → checkAssignedTimeout CF |

### 결제수단
| 블록 | cashReceived | creditAmount | pointsUsed |
|------|-------------|-------------|------------|
| 현금 | fare | 0 | 0 |
| 이체 | 0 | 0 | 0 |
| 카드 | 0 | 0 | 0 |
| 외상 | 0 | 0 | 0 |
| 포인트 | 0 | 0 | fare |
| 현금+포인트 | cashAmount | fare-cashAmount | 0 |

### 공유콜
| 블록 | 트리거 | 코드 경로 |
|------|--------|----------|
| 마감 자동 | 사무실 CLOSED + 전화 | CallDetectorService → createSharedCall |
| 수동 공유 | 매니저 판단 | DashboardViewModel → shareCall |
| 수임 | 타 사무실 매니저 | claimSharedCallWithDetails (runTransaction) |
| 수임 경합 | 2사무실 동시 수임 | 트랜잭션 1개만 성공 |

### 정산
| 블록 | 주체 | 코드 경로 |
|------|------|----------|
| 기사 마감 | 기사 | submitDailySettlement |
| 매니저 확인 | 매니저 | confirmDailySettlement |
| 이월 이체 | 매니저 | transferCarryOver |
| 기사 수령 | 기사 | acknowledgeTransfer |
| 전체 마감 | 매니저 | finalizeSettlementSession → CF |

### 장애
| 블록 | 설명 |
|------|------|
| FCM 미도착 | 배차 FCM 미수신 → 관리자 직접 연락 |
| 앱 Kill | 강제종료 → 재시작 → 활성콜 복구 |
| 네트워크 단절 | 오프라인 → 복구 → 상태 동기화 |

---

## 5. 참조 문서 맵 (상세)

### 5.1 시스템 분석 보고서

#### `CALLMADANG_ANALYSIS_REPORT.md` (루트)
- **내용**: V1 종합 분석. 앱별 개별 분석 결과
- **구조**: 8개 섹션 - 크로스 모듈(CROSS-01~07), Detector(CD-01~15), Manager(CM-01~11), Driver(DR-01~12), Customer(CU-01~15), Firebase(FB-01~18), 심각도별 요약, 수정 권장 순서
- **핵심**: 앱별 이슈 57건 전체 목록 + 코드 위치(파일:라인)
- **시뮬용도**: 이슈 목록 참조, 수정 여부 확인

#### `CALLMADANG_ANALYSIS_REPORT_V2.md` (루트)
- **내용**: V2 크로스 검증. 팀원 간 직접 코드 대조 결과
- **구조**: 5개 섹션 - CROSS-01~07 재검증, **시나리오별 데이터 흐름 추적(핵심)**, 신규 발견 12건, 심각도 재평가, 수정 권장
- **핵심 시나리오**:
  - 시나리오 1: 정상 흐름 (Manager 배차) - Firestore 변경 6단계 상세
  - 시나리오 2: Detector 배차 - Manager 배차와의 필드 차이 테이블
  - 시나리오 3: 기사 취소→재배차 - 3가지 경로(기사취소/관리자취소/거절)
  - 시나리오 4: 네트워크 불안정 - 오프라인 충돌 5단계
  - 시나리오 5: 다중 사무실 데이터 격리 - 성공/실패 영역
- **시뮬용도**: 마스터 필독. 콜 지시 시 정확한 Firestore 상태 변화 참조

### 5.2 정산 분석 보고서

#### `SETTLEMENT_ANALYSIS.md` (루트)
- **내용**: Call Manager 정산 코드 상세 분석
- **구조**: Firestore 문서 구조(JSON 예시), 결제수단별 처리 매트릭스, 이월(CarryOver) 로직 상세, 정산 확인 3단계, 잠재 문제점 4건
- **핵심**:
  - 결제수단별 cashReceived/creditAmount/pointsUsed 정확한 매핑
  - processCarryOverOnFinalize vs confirmDailySettlement 이중 계산 문제
  - 정산 확인 코드: confirmSettlement → finalizeSettlementSession → confirmDailySettlement
- **시뮬용도**: 정산 시나리오 지시 시 결제수단별 Firestore 필드 참조

#### `docs/SETTLEMENT_SYSTEM_ANALYSIS.md`
- **내용**: 정산 시스템 전체 분석 (더 상위 관점)
- **구조**: 데이터 흐름도, 결제방법 6종 매트릭스, 수익 분배(60:40), 마이너스 입금 처리, 일일 결산(자동06:10/수동), 외상 처리, Firestore 구조, 동기화 방식
- **핵심**:
  - 근무일 계산: 새벽 6시 이전 = 전날 근무일
  - 결제방식별 정산 계산 예시 (현금/이체/외상/현금+포인트)
  - 외상: CreditPerson/CreditEntry로 Room DB 관리 (Firestore 미저장)
- **시뮬용도**: 정산 전체 흐름 이해, 외상/이월 시나리오 설계

### 5.3 이전 시뮬레이션 시나리오

#### `.agent-teams/simulation-v3-scenario.md`
- **내용**: 3차 코드 시뮬레이션 (350콜, 7일)
- **구조**: Day 1~7 시나리오 매트릭스 (50콜/일)
  - Day 1: 정상 플로우 (전화/앱 혼합)
  - Day 2: 취소/거절 (rejectCall, cancelCall, 고객 취소)
  - Day 3: 공유콜 (마감→자동생성→수임→취소→타임아웃)
  - Day 4: 포인트 (사용/환불/잔액부족)
  - Day 5: 엣지케이스 (타임아웃, 동시배차, FCM실패, 앱재시작)
  - Day 6: 대량 혼합 (외상 포함)
  - Day 7: 정산 집중 (이월/확인/이체)
- **오탐 방지 섹션**: Firestore 트랜잭션 직렬화, Detector 별도폰, 수정완료 목록
- **시뮬용도**: Day별 시나리오 설계의 기본 틀

#### `.agent-teams/simulation-v8-realworld.md`
- **내용**: 8차 실전 시뮬레이션 (700콜, 14일)
- **구조**:
  - 기본 설정: 기사 3명, Detector 3대, Manager 1대, 시간대별 콜 분포(20:00~05:00)
  - 운행 소요시간: 단거리(25~35분)/중거리(40~55분)/장거리(70~90분)
  - 순번 규칙: 복귀 순서 = 배차 순번
  - Day 1~14 **콜 단위 상세 테이블** (시간/유형/감지/배차/기사/시나리오)
  - Week 1: 정상→거절→취소→포인트→스트레스→공유콜→정산
  - Week 2: 이월처리→FCM장애→고객앱→엣지→카오스→인력변동→최종마감
- **시뮬용도**: 100콜 상세 시나리오의 참고 템플릿. 시간대별 콜 분포 패턴 재활용

#### `.agent-teams/simulation-v9-realdevice.md`
- **내용**: 실기기 파일럿 시나리오 (약 3시간, 21건)
- **구조**: 5개 Phase
  - Phase A: 네트워크 장애 4건 (Detector/Driver/Manager/Customer 오프라인)
  - Phase B: FCM 수신 5건 (포그라운드/백그라운드/Kill/재부팅/Doze)
  - Phase C: 전화 감지 4건 (3대 중복★★★/연속전화/부재중/캐치콜)
  - Phase D: 동시성 5건 (이중배차/수락취소경합/공유콜이중수임/정산더블탭/고객더블탭)
  - Phase E: 앱 Kill&복구 3건 (Driver/Manager/Detector)
- **리스크 목록**: R1(3대 중복CRITICAL), R2~R7(CF비트랜잭션, 오프라인큐, SharedPrefs 등)
- **시뮬용도**: 장애/동시성 시나리오 설계, 실기기 테스트 전 체크리스트

### 5.4 정산 크로스 검증

#### `.agent-teams/settlement-simulation.md`
- **내용**: Manager↔Driver↔CF 3-Way 정산 정밀 대조
- **구조**:
  - 섹션 1: 정산 구조 (Firestore 데이터, 결제수단별 처리, 기사몫 계산, 이월 로직)
  - 섹션 2: 발견된 이슈 STL-01~11 (코드 위치 + 영향 상세)
  - 섹션 3: **시나리오 시뮬레이션 A~E** (핵심)
    - A: 정상 하루 (Manager/Driver/Firestore 3자 비교 테이블)
    - B: 이월 발생 (2일 시뮬, carryOver 계산 단계별 추적)
    - C: 퇴근 기사 재로그인 (isIntegration 분기)
    - D: 복합 이월 (결제수단별 분리 여부)
    - E: 엣지 6종 (앱Kill/네트워크/0원/전액포인트/외상Only/BUG-D12)
  - 섹션 4: 정산 데이터 흐름도 (전체 시퀀스)
  - 섹션 5: 이슈 요약 + 파일럿 영향도
- **시뮬용도**: 정산 검증의 핵심. 시나리오 A~E를 재현하여 정합성 확인

### 5.5 팀 운영 문서

#### `.agent-teams/TEAM_OVERVIEW.md`
- **내용**: Agent Teams 전체 현황 (세션 1~5 기록)
- **구조**: 아키텍처, 팀 구성, 세션별 완료 분석, 수정 완료 4건, 미해결 이슈 40건 통합 집계(Critical 11/High 13/Medium 11/Low 3/Info 2), 오탐 3건, 해소 4건, Phase 로드맵
- **시뮬용도**: 이슈 전체 목록 + 수정 상태 확인

#### `.agent-teams/REVIVAL_PROMPT.md`
- **내용**: 다음 세션 부활 프롬프트
- **시뮬용도**: 팀 재구성 시 참조

### 5.6 앱별 분석 문서

#### `.agent-teams/detector-analyst.md`
- **내용**: Call Detector 앱 분석 컨텍스트 (팀원용)
- **시뮬용도**: 매니저 프롬프트 보강 시 참조

#### `.agent-teams/manager-analyst.md`
- **내용**: Call Manager 앱 분석 컨텍스트
- **시뮬용도**: 매니저 프롬프트 보강 시 참조

#### `.agent-teams/driver-analyst.md`
- **내용**: Driver App 분석 컨텍스트
- **시뮬용도**: 기사 프롬프트 보강 시 참조

#### `.agent-teams/firebase-analyst.md`
- **내용**: Cloud Functions + Firebase 분석 컨텍스트
- **시뮬용도**: 파이어베이스 프롬프트 보강 시 참조

#### `.agent-teams/customer-app-analysis.md` / `customer-analyst.md`
- **내용**: Customer App 분석 (세션 3 결과, CUST-01~10 + SEC-C01~05)
- **시뮬용도**: 마스터의 손님앱 역할 시 참조

#### `.agent-teams/CROSS_VERIFICATION_LOG.md`
- **내용**: 크로스 검증 기록 (세션 1~4)
- **시뮬용도**: 팀원 간 검증 이력 참조

---

## 6. 시뮬레이션 시작 절차

"시뮬레이션해줘"라고 말하면:

1. **이 플레이북 읽기**
2. **설정 확인**: 사무실 수, 기사 수, 일수, 콜 수 (기본: 3사무실, 기사 2명씩, 10일, 100콜/일)
3. **팀 구성**: 마스터 + 매니저N + 기사N×2 + 파이어베이스
4. **Day 시나리오 확인**: 사용자에게 Day별 주요 시나리오 확인 (또는 기본값 사용)
5. **실행 대기**: "Day N 시작" 명령을 기다림

---

## 7. 오탐 방지 규칙 (이전 시뮬레이션 누적)

1. **Firestore 트랜잭션 직렬화**: runTransaction 내 같은 문서 동시 읽기/쓰기는 자동 직렬화됨
2. **Detector 3대 = 별도 폰**: static/인스턴스 변수 폰 간 공유 안 됨
3. **이미 수정 완료된 건 재지적 금지**: CLAUDE.md 수정 이력 참조
4. **보안 이슈(SEC-*) 패스**: 파일럿 단계이므로 보안 지적 불필요
5. **CANCELLED_BY_CUSTOMER** 도입 완료
6. **checkAssignedTimeout** CF 구현 완료
