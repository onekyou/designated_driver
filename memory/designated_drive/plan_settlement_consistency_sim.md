---
name: 정산 일관성 검증 시뮬레이션 설계
description: 1주일 50콜/일 시뮬레이션 — Firestore vs 매니저UI vs 기사앱UI 일치 검증
type: project
---

# 정산 일관성 검증 시뮬레이션

## 목적
Firestore(실제 정산 데이터) ↔ 콜매니저 UI ↔ 기사앱 UI가 **모든 상황에서 항상 일치**하는지 검증

## 인프라
기존 Firebase Emulator 테스트 (`functions/test/`) 기반 확장
- `npm run test:emulator` 파이프라인에 통합
- 기존 helpers (createCall, assignCall, completeCall 등) 재사용

## 시뮬레이션 규모
- **기간**: 7일 (Day 1~7)
- **일일 콜**: 50건
- **기사**: 5명 (A~E)
- **사무실**: 1개 (depositRatio: 60%)
- **총 콜**: 350건

---

## 검증 공식 (3자 일치 체크포인트)

매 체크포인트에서 아래 3개 값이 일치해야 PASS:

```
[Firestore 실제값]
  settlementSession.totals (전체)
  designated_drivers/{id}.carryOver.balance (기사별)
  designated_drivers/{id}.dailySettlement (기사별)

[콜매니저 UI 계산 로직]
  filteredTrips 기반: totalFare, deposit, realDeposit, carryOver
  driverLastClearedMap 기반 필터링 적용

[기사앱 UI 계산 로직]
  loadTodaySettlement 기반: totalFare, officeDeposit, driverShare, realDeposit
  settlementLastCleared 기반 필터링 적용
```

---

## 일별 시나리오

### Day 1 (월) — 기본 흐름 (50콜)
| 시나리오 | 콜수 | 기사 | 핵심 검증 |
|----------|------|------|-----------|
| 정상 완료 (현금) | 20 | A,B,C,D,E 균등 | 기본 정산 계산 일치 |
| 정상 완료 (이체) | 15 | A,B,C | 외상(totalCredit) 계산 일치 |
| 정상 완료 (현금+포인트) | 10 | D,E | 혼합결제 분리 계산 |
| 거절 → 재배차 → 완료 | 5 | A→B | 거절 후 기사 상태 복구 + 정산 귀속 |

**체크포인트 1**: 50콜 완료 후 → 3자 totalFare/deposit/credit 일치
**체크포인트 2**: 기사 A,B 업무마감 제출 → calculatedCarryOver 일치
**체크포인트 3**: 매니저 확인 → carryOver.balance 갱신 일치

---

### Day 2 (화) — 퇴근 후 재로그인 ★핵심★ (50콜)
| 시나리오 | 콜수 | 기사 | 핵심 검증 |
|----------|------|------|-----------|
| 1차 운행 + 마감 | 15 | A,B | settlementLastCleared 설정 |
| 재로그인 + 2차 운행 | 15 | A,B | filteredTrips에 1차 콜 미포함 |
| 일반 운행 (마감 없음) | 20 | C,D,E | 정상 흐름 유지 |

**체크포인트 4**: 기사A 1차 마감 → 매니저 확인 → clearSettlement
**체크포인트 5**: 기사A 2차 운행 시작 → filteredTrips = 2차 콜만
**체크포인트 6**: 기사A 2차 마감 → originalCarryOver = 0 (이월 정리됨)
**체크포인트 7**: 매니저 UI에서 기사A 카드: 2차 콜만 표시

---

### Day 3 (수) — 이월금 + 이체 타이밍 (50콜)
| 시나리오 | 콜수 | 기사 | 핵심 검증 |
|----------|------|------|-----------|
| 외상 비율 높은 운행 | 15 | A,B | realDeposit 음수 → 미지급금 발생 |
| 정상 현금 운행 | 15 | C,D | 미지급금 없음 |
| **퇴근 후 이체** | 5 | A | 마감→퇴근→매니저가 이체→기사 오프라인 상태에서 이체 반영 |
| **출근 후 마감 전 이체** | 5 | B | 운행 중(마감 전)에 매니저가 이전 이월금 이체 |
| 이체 → 수령확인 사이클 | 10 | A,B | PENDING→TRANSFERRED→SETTLED |

#### 시나리오 3-1: 퇴근 후 이체 (기사 A)
```
① A 15콜 운행 → 마감 제출 (PENDING_CONFIRM)
② 매니저 확인 (CONFIRMED) → carryOver.balance = 30,000 (미지급금 발생)
③ A 퇴근 (clearSettlement → settlementLastCleared 갱신, OFFLINE)
④ 매니저가 퇴근한 A에게 이체 (transferCarryOver)
   → carryOver.status = TRANSFERRED, balance = 30,000
⑤ 검증: 기사 오프라인이지만 Firestore에 TRANSFERRED 반영됨
⑥ A 다음날 출근 → 앱 시작 시 carryOverListener 작동 → UI에 이체됨 표시
⑦ A 수령확인 → balance=0, SETTLED
```

#### 시나리오 3-2: 출근 후 마감 전 이체 (기사 B)
```
① B 이전 이월금 50,000원 있음 (PENDING 상태)
② B 출근 → 운행 시작 (5콜 진행 중, 아직 마감 전)
③ 매니저가 B의 이전 이월금 50,000원 이체 (transferCarryOver)
   → carryOver.status = TRANSFERRED, balance = 50,000
④ B의 기사앱 UI: 이체됨 표시 + 현재 운행 정산은 별도
⑤ B 수령확인 → carryOver: balance=0, SETTLED
⑥ B 추가 5콜 운행 → 마감 제출
   → originalCarryOver = 0 (수령 후이므로), 새 정산만 계산
⑦ 검증: 이전 이월금과 오늘 정산이 완전 분리
```

**체크포인트 8**: 미지급금 발생 → carryOver.balance 양수 → 3자 일치
**체크포인트 9**: 퇴근 후 이체 → Firestore TRANSFERRED 상태 + 기사 오프라인에서도 정확
**체크포인트 10**: 재출근 시 기사앱 UI에 TRANSFERRED 정상 표시
**체크포인트 11**: 마감 전 이체 → 기사앱 실시간 반영 + 운행 정산과 분리
**체크포인트 12**: 수령확인 후 마감 제출 → originalCarryOver = 0, 새 정산만 계산

---

### Day 4 (목) — 거절 + 재제출 (50콜)
| 시나리오 | 콜수 | 기사 | 핵심 검증 |
|----------|------|------|-----------|
| 정상 운행 | 30 | 전원 | 기본 흐름 |
| 마감 제출 → 거절 → 재제출 | 10 | A,B | REJECTED 후 재제출 시 계산 정확성 |
| 다중 거절 (2회 거절 → 승인) | 10 | C | originalTripCount 추적 정확성 |

**체크포인트 13**: 거절 후 기사 UI에 REJECTED 표시
**체크포인트 14**: 재제출 시 tripCount, totalFare 정확
**체크포인트 15**: 2회 거절 후 최종 승인 → carryOver 정확

---

### Day 5 (금) — 피크타임 동시성 (50콜)
| 시나리오 | 콜수 | 기사 | 핵심 검증 |
|----------|------|------|-----------|
| 동시 콜 완료 (5건 동시) | 25 | 전원 | CF 동시 트리거 시 세션 중복 방지 |
| 취소 유형 혼합 | 15 | A,B,C | CANCELED/BY_DRIVER/BY_CUSTOMER 정산 미포함 |
| 타임아웃 → 재배차 | 10 | D→E | 3분 미수락 → WAITING → 재배차 |

**체크포인트 16**: 동시 완료 후 settlementSession.calls 중복 없음
**체크포인트 17**: 취소된 콜이 정산에 포함되지 않음
**체크포인트 18**: 재배차된 콜 정산 귀속 = 최종 완료 기사

---

### Day 6 (토) — 통합 정산 (1차+2차 합산) (50콜)
| 시나리오 | 콜수 | 기사 | 핵심 검증 |
|----------|------|------|-----------|
| 1차 운행 + 마감 대기 | 15 | A,B | PENDING_CONFIRM 상태 유지 |
| 2차 운행 (마감 대기 중) | 15 | A,B | 통합 정산 (isIntegration) |
| 일반 운행 | 20 | C,D,E | 간섭 없음 |

**체크포인트 19**: 1차 제출 후 기사UI: calculatedCarryOver / 매니저UI: carryOver.balance (일시적 불일치 허용)
**체크포인트 20**: 2차 제출 → 통합: mergedTripCount = 1차+2차
**체크포인트 21**: 매니저 확인 → originalTripCount/originalTotalFare 보존
**체크포인트 22**: 최종 carryOver.balance = calculatedCarryOver 일치

---

### Day 7 (일) — 엣지케이스 종합 (50콜)
| 시나리오 | 콜수 | 기사 | 핵심 검증 |
|----------|------|------|-----------|
| 이월금 있는 상태로 시작 | 10 | A | Day 6 이월 → Day 7 공제 |
| 0원 운행 (테스트콜) | 5 | B | fare=0 시 정산 계산 |
| 전액 포인트 결제 | 5 | C | cashReceived=0, pointsUsed=전액 |
| 대량 연속 운행 (20건/기사) | 20 | D | 대량 콜 시 계산 정확성 |
| 마지막 정리: 전원 마감 | 10 | 전원 | 전원 SETTLED → 잔액 0 |

**체크포인트 23**: Day 6 이월 → Day 7 공제 정확
**체크포인트 24**: 0원/전액포인트 예외 처리
**체크포인트 25**: 20건 연속 → settlementSession.calls 누락 없음
**체크포인트 26**: 전원 마감 + SETTLED → 모든 balance = 0

---

## 검증 함수 설계

```typescript
interface ConsistencyCheckResult {
  checkpoint: string;
  driverId: string;

  // Firestore 실제값
  firestore: {
    sessionTotalFare: number;
    sessionCallCount: number;
    carryOverBalance: number;
    carryOverStatus: string;
    dailySettlementStatus: string;
    calculatedCarryOver: number;
  };

  // 콜매니저 로직 재현
  managerCalc: {
    filteredTripCount: number;
    totalFare: number;
    deposit: number;
    totalCredit: number;
    realDeposit: number;
    displayedCarryOver: number;
  };

  // 기사앱 로직 재현
  driverCalc: {
    tripCount: number;
    totalFare: number;
    officeDeposit: number;
    driverShare: number;
    realDeposit: number;
    displayedCarryOver: number;
  };

  // 일치 여부
  match: boolean;
  mismatches: string[];  // 불일치 필드 목록
}

// 3자 일치 검증 함수
async function assertTripleConsistency(
  officeRef: string,
  driverId: string,
  depositRatio: number,
  checkpoint: string
): Promise<ConsistencyCheckResult>
```

---

## 발견된 잠재적 불일치 시나리오 (10건)

| # | 시나리오 | 위험도 | 시뮬레이션 Day |
|---|----------|--------|---------------|
| 1 | lastClearedMillis 시점 불일치 (기사↔매니저) | 🔴 | Day 2 |
| 2 | PENDING_CONFIRM 중 carryOver 표시 차이 | 🟡 | Day 6 |
| 3 | 다중 거절 시 originalTripCount 추적 | 🟡 | Day 4 |
| 4 | settlementLastCleared 레이스 컨디션 | 🟡 | Day 2 |
| 5 | realDeposit 검증 없음 (기사 자체 계산) | 🔴 | Day 3 |
| 6 | 동시 콜 완료 시 세션 중복 | 🟡 | Day 5 |
| 7 | depositRatio 변경 중간 적용 | 🟡 | 미포함 |
| 8 | 오프라인 동기화 지연 | 🟡 | 미포함 (에뮬 한계) |
| 9 | 이월금 공제 후 음수 처리 | 🟡 | Day 7 |
| 10 | 전원 SETTLED 후 잔액 0 보장 | 🔴 | Day 7 |
| 11 | 퇴근 후 이체 — 기사 오프라인 상태에서 이체 반영 | 🔴 | Day 3 |
| 12 | 마감 전 이체 — 운행 중 이전 이월금 이체 시 정산 분리 | 🔴 | Day 3 |

---

## 실행 방법 (준비 완료 후)

```bash
# 1. 에뮬레이터 시작
cd functions && firebase emulators:start

# 2. 시뮬레이션 실행
npx ts-node test/scenarios/settlement-consistency-sim.ts

# 3. 결과 리포트
# → test/scenarios/settlement-consistency-report.json
# → 26개 체크포인트 × PASS/FAIL + 불일치 상세
```

## 산출물
- `functions/test/scenarios/settlement-consistency-sim.ts` — 메인 시뮬레이션
- `functions/test/scenarios/consistency-checker.ts` — 3자 일치 검증 함수
- `functions/test/scenarios/settlement-consistency-report.json` — 결과 리포트

**Why:** 정산 금액이 Firestore/매니저/기사 간 어긋나면 실제 돈 문제로 직결. 코드 수정 후 반드시 자동 검증 필요.
**How to apply:** 정산 관련 코드 수정 시 이 시뮬레이션을 돌려서 26개 체크포인트 PASS 확인.
