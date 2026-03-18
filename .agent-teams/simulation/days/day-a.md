# Day A: 이월 + 퇴근/재출근 + 이체 (20콜) ★회귀 핵심

## 목표
`filteredTrips`(8108b1ec), `업무마감↔로그아웃 분리`(232e4f73), `자동로그인`(3bbbce7b) 변경이
기존 이월/정산 흐름을 깨뜨리지 않았는지 검증.

## 설정
- 사무실1만 (mgr-main + drv-a + drv-b)
- depositRatio: 60%
- 결제: 현금 위주 + 이체/포인트 소량

---

## Phase 1: A,B 1차 운행 + 마감 (8콜)

> carryOver가 발생하도록 현금 수령액 < 사무실 몫이 되는 구성

### 기사A 1차 (4콜, 전부 현금)
| # | 콜ID | 유형 | 결제 | 요금 | cashReceived | 비고 |
|---|------|------|------|------|-------------|------|
| 1 | CA-001 | 전화 | 현금 | 20,000 | 20,000 | 정상 |
| 2 | CA-002 | 앱 | 현금 | 25,000 | 25,000 | 정상 |
| 3 | CA-003 | 전화 | 현금 | 18,000 | 18,000 | 정상 |
| 4 | CA-004 | 전화 | 현금 | 15,000 | 15,000 | 정상 |

**A 1차 정산** (depositRatio=60%):
- totalFare=78,000 / totalCredit=0 / officeDeposit=46,800 / finalDeposit=46,800
- driverShare=31,200 / cashReceived=78,000
- **기사 A가 realDeposit=6,800으로 제출** (나머지는 다음에 입금)
→ carryOver = 0 - 46,800 + 6,800 = **-40,000** (미납)

### 기사B 1차 (4콜, 전부 현금)
| # | 콜ID | 유형 | 결제 | 요금 | cashReceived | 비고 |
|---|------|------|------|------|-------------|------|
| 5 | CA-005 | 전화 | 현금 | 30,000 | 30,000 | 정상 |
| 6 | CA-006 | 앱 | 현금 | 22,000 | 22,000 | 정상 |
| 7 | CA-007 | 전화 | 현금 | 18,000 | 18,000 | 정상 |
| 8 | CA-008 | 전화 | 현금 | 20,000 | 20,000 | 정상 |

**B 1차 정산** (depositRatio=60%):
- totalFare=90,000 / totalCredit=0 / officeDeposit=54,000 / finalDeposit=54,000
- driverShare=36,000 / cashReceived=90,000
- **기사 B가 realDeposit=34,000으로 제출** (나머지는 다음에 입금)
→ carryOver = 0 - 54,000 + 34,000 = **-20,000** (미납)

### 1차 마감 절차
1. drv-a: submitDailySettlement
2. drv-b: submitDailySettlement
3. mgr-main: confirmDailySettlement (A) → confirmDailySettlement (B)

**CP1**: A carryOver = -40,000
**CP2**: B carryOver = -20,000

---

## Phase 2: A 퇴근 → 매니저 이체 → 재출근 → 수령

1. **A 퇴근** (업무마감 — 로그아웃 아님, `232e4f73` 변경사항)
2. **매니저가 A에게 이체**: mgr-main → transferCarryOver (A, 40,000원)
   - A의 정산 상태: TRANSFERRED
3. **A 재출근** (자동로그인으로 앱 재진입, `3bbbce7b` 변경사항)
4. **A 이체 수령 확인**: drv-a → acknowledgeTransfer
   - A의 정산 상태: SETTLED

**CP3**: A 정산 상태 PENDING_CONFIRM → CONFIRMED → TRANSFERRED → SETTLED
**CP4**: A 업무마감 후에도 로그인 상태 유지 (로그아웃 분리)

---

## Phase 3: A 2차 운행 (6콜)

> **핵심 검증**: filteredTrips가 2차 운행분만 표시하는지 (`8108b1ec`)

| # | 콜ID | 유형 | 결제 | 요금 | cashReceived | 비고 |
|---|------|------|------|------|-------------|------|
| 9 | CA-009 | 전화 | 현금 | 25,000 | 25,000 | 2차 운행 |
| 10 | CA-010 | 앱 | 현금 | 20,000 | 20,000 | 2차 운행 |
| 11 | CA-011 | 전화 | 현금+포인트 | 22,000 | 14,000 | 현금14k+포인트8k |
| 12 | CA-012 | 전화 | 현금 | 18,000 | 18,000 | 2차 운행 |
| 13 | CA-013 | 앱 | 이체 | 30,000 | 0 | 이체 |
| 14 | CA-014 | 전화 | 현금 | 15,000 | 15,000 | 2차 운행 |

**CP5**: A의 UI에 2차 운행 6콜만 표시 (1차 4콜 미표시) — filteredTrips 검증
**CP6**: A의 2차 정산에 originalCarryOver = 0 (이미 SETTLED이므로)

---

## Phase 4: B 운행 중 이체 → 수령 → 마감 (4콜)

| # | 콜ID | 유형 | 결제 | 요금 | cashReceived | 비고 |
|---|------|------|------|------|-------------|------|
| 15 | CA-015 | 전화 | 현금 | 28,000 | 28,000 | 정상 |
| 16 | CA-016 | 앱 | 현금 | 22,000 | 22,000 | 정상 |
| 17 | CA-017 | 전화 | 현금 | 16,000 | 16,000 | 정상 |
| 18 | CA-018 | 전화 | 현금 | 24,000 | 24,000 | 정상 |

1. B가 CA-015~016 완료
2. **매니저가 B에게 이체** (B가 아직 운행 중): transferCarryOver (B, 20,000원)
3. **B가 이체 수령**: acknowledgeTransfer
4. B가 CA-017~018 완료
5. B 마감: submitDailySettlement

**CP7**: B의 이체가 운행 중에도 정상 처리
**CP8**: B의 마감 정산에 originalCarryOver = 0 (이미 수령했으므로)

---

## Phase 5: 최종 마감 (2콜)

| # | 콜ID | 유형 | 결제 | 요금 | cashReceived | 비고 |
|---|------|------|------|------|-------------|------|
| 19 | CA-019 | 전화 | 현금 | 20,000 | 20,000 | A 마무리 |
| 20 | CA-020 | 앱 | 현금 | 15,000 | 15,000 | A 마무리 |

1. A 마감: submitDailySettlement (2차분)
2. mgr-main: confirmDailySettlement (A 2차) → confirmDailySettlement (B 2차)
3. mgr-main: finalizeSettlementSession

**CP9**: 업무마감 후 로그인 상태 유지 (업무마감 ≠ 로그아웃)
**CP10**: 전체 정산 정합성 (3자 일치)

---

## 검증 체크리스트

| CP | 검증 항목 | 회귀 대상 커밋 |
|----|----------|---------------|
| CP1 | A carryOver = -40,000 | 기존 로직 |
| CP2 | B carryOver = -20,000 | 기존 로직 |
| CP3 | A 정산 상태 전이 완전성 | `232e4f73` |
| CP4 | 업무마감 후 로그인 유지 | `232e4f73` |
| CP5 | filteredTrips 2차분만 표시 | `8108b1ec` |
| CP6 | 2차 originalCarryOver = 0 | `8108b1ec` |
| CP7 | 운행 중 이체 정상 처리 | 기존 로직 |
| CP8 | B originalCarryOver = 0 | `8108b1ec` |
| CP9 | 최종 마감 후 로그인 유지 | `232e4f73` |
| CP10 | 전체 정산 3자 일치 | 종합 |

---

## 결과

> 시뮬레이션 완료 후 아래에 기록

### 발견 이슈
(없음)

### 정산 검증
(미완료)
