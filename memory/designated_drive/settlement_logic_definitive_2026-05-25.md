# 정산 로직 명문화 (2026-05-25)

> **목적**: 모든 정산 로직을 *코드 그대로* 명문화. 추측 0, 인용 100%.
> **작성 배경**: 본인 발화 (2026-05-25) — "이런 식의 소모전 힘들어. 차라리 모든 정산 로직을 명문화해 파일로."
> **이 파일은 단일 출처(Single Source of Truth)** — PTT plan §3 정산 단순화는 이 파일 기준으로 재설계.

## 메타데이터

- **작성일**: 2026-05-25
- **코드 스냅샷 시점**: 5/25 master 헤드 기준 (manager-direct-drive 브랜치 작업물 포함)
- **이전 자료**: `memory/designated_drive/settlement_analysis.md` (2026-02-24, 580줄) — 본 파일이 *현재 시점* 갱신 + 본인 운영 정정 반영
- **검증 근거 파일**:
  - `call_manager/.../ui/settlement/SettlementViewModel.kt` (2,166줄)
  - `call_manager/.../ui/settlement/SettlementCalculator.kt`
  - `call_manager/.../data/settlement/SettlementModels.kt`
  - `call_manager/.../data/SettlementData.kt`
  - `driver_app/.../data/settlement/SettlementCalc.kt`
  - `driver_app/.../data/settlement/SettlementModels.kt`
  - `driver_app/.../viewmodel/DriverViewModel.kt` (submitDailySettlement L1592, carryOverListener L1500, confirmReceiveCarryOver L1558)
  - `driver_app/.../ui/home/HistorySettlementScreen.kt` (1,073줄 — 기사 UI)
  - `functions/src/handlers/settlement.ts` (598줄)
  - `functions/src/index.ts` (onCallCompletedUpdateSettlement L4965, autoFinalizeSettlements L5008)

---

## 1. Firestore 데이터 모델

### 1.1 `settlementSessions/{date}` — 사무실 단위 일일 정산 문서

경로: `provinces/{p}/cities/{c}/offices/{o}/settlementSessions/{YYYY-MM-DD}`
키: 근무일 기준 (calculateWorkDate, §5)

```
{
  metadata: {
    version: Long              // 낙관적 동시성용
    lastUpdatedAt: Timestamp
    lastUpdatedBy: String      // "call_manager" | "driver_app" | "cloud_function" | "auto_finalize"
    depositRatio: Int          // 사무실 몫 비율 (기본 60)
    createdAt: Timestamp
    isFinalized: Boolean       // 일일 마감 플래그
  },
  totals: {
    totalFare: Long            // 총 운행료
    totalDeposit: Long         // 총 사무실 몫 = totalFare * depositRatio / 100
    totalDriverShare: Long     // 총 기사 몫 = totalFare - totalDeposit
    totalCash: Long            // "현금" + "현금+포인트"의 cashReceived 부분
    totalCard: Long            // "이체" + "카드" 운영 X
    totalCredit: Long          // 모든 콜의 creditAmount 합
    totalPoints: Long          // 모든 콜의 pointsUsed 합
    callCount: Int
  },
  calls: Array<{
    callId, driverId, driverName, customerName, customerPhone,
    departure, destination,
    fare: Long,
    paymentMethod: String      // "현금" | "이체" | "외상" | "현금+포인트" | "포인트" (카드 미사용)
    cashReceived: Long         // 현금+포인트의 현금 부분
    creditAmount: Long         // 기사 입장의 "사무실에 줘야 할 돈 부족분"
    pointsUsed: Long
    completedAt: Timestamp
    confirmedByOffice: Boolean // 매니저가 개별 콜 확인했는지
    syncedAt: Timestamp
  }>
}
```

**주의**: `calls`는 Array. 콜 추가/수정 시 *전체 배열 재기록* (transaction).

### 1.2 `designated_drivers/{driverId}.dailySettlement` (필드)

기사 1명의 *그날 영업 마감* 데이터. 기사 문서 안의 nested map.

```
dailySettlement: {
  date: String                       // YYYY-MM-DD (calculateWorkDate)
  finalDeposit: Long                 // 최종 사무실 납입액 = officeDeposit - totalCredit
  realDeposit: Long                  // 기사가 실제 입금한 금액 (UI 입력)
  settlementDiff: Long               // 정산 차액 = realDeposit - finalDeposit (양수=환급, 음수=미납)
  totalFare: Long
  totalCredit: Long                  // 현금 외 결제 합 (= 이체+외상+포인트+포인트부분)
  tripCount: Int
  status: String                     // WORKING | PENDING_CONFIRM | CONFIRMED | REJECTED
  submittedAt: Timestamp             // 기사 마감 시간
  confirmedAt: Timestamp             // 매니저 확인 시간
  confirmedBy: String                // adminId
  calculatedCarryOver: Long          // = originalCarryOver - finalDeposit + realDeposit
  originalCarryOver: Long            // 마감 시점 carryOver.balance 원본
  originalTripCount: Int             // 통합제출 시 1차 마감 데이터 보존용
  originalTotalFare: Long
  originalRealDeposit: Long
}
```

### 1.3 `designated_drivers/{driverId}.carryOver` (필드)

기사 1명의 *누적 미정산 잔액*. 사무실 ↔ 기사 송금 미청산 잔액.

```
carryOver: {
  balance: Long                      // 양수 = 사무실이 기사에게 줄 돈 / 음수 = 기사가 사무실에 줄 돈
  status: String                     // PENDING | TRANSFERRED | SETTLED
  lastUpdatedAt: Timestamp
  transferredAt: Timestamp           // 매니저가 이체 처리한 시점
  transferredBy: String              // adminId
  todayAmount: Long                  // 오늘 발생한 미수령금 (참고용)
}
```

### 1.4 `designated_drivers/{driverId}.settlementLastCleared` (필드)

기사별 *마지막 정산 마감 시점*. Timestamp.
- 기사가 퇴근(`clearSettlement`) 시 갱신
- 매니저가 일괄 마감(`clearAllTrips`) 시 갱신
- `fetchCompletedCalls`에서 이 시점 *이후*의 COMPLETED 콜만 정산 대상에 포함

### 1.5 `designated_drivers/{driverId}.status` (필드, 정산 외 자리)

`WAITING | ONLINE | PREPARING | ON_TRIP` 등. `confirmDailySettlement` 시 `status="WAITING"` 강제 (운행 종료 후 대기 복귀).

### 1.6 `offices/{officeId}.settlementLastCleared` (필드)

사무실 전체 *마지막 일괄 마감 시점*. `clearAllTrips()` 시 갱신.
- `applyDirectRunFilter()`에서 cutoff로 사용 — 이 시점 이후 직접운행만 표시

### 1.7 `offices/{officeId}.depositRatio` (필드)

사무실 수수료 비율. 기본 60. UI에서 수정 가능 (30~90 clamp). `updateOfficeShareRatio`.

---

## 2. 결제 수단 5종 (운영) + 카드 미사용

### 2.1 코드 상수
```
"현금" | "이체" | "외상" | "현금+포인트" | "포인트" | "카드"(미사용)
```

### 2.2 결제 수단별 처리 (SettlementCalculator.calculateCreditForTrip)
```kotlin
fun calculateCreditForTrip(fare: Int, paymentMethod: String, cashAmount: Int?): Int {
    return when {
        paymentMethod == "현금" -> 0
        paymentMethod == "현금+포인트" -> {
            val cash = cashAmount ?: 0
            if (cash > 0) fare - cash else fare
        }
        else -> fare // 이체, 외상, 포인트는 전액 외상
    }
}
```

→ **`creditAmount`는 기사 입장의 "사무실에 줄 돈 부족분"** (현금 외 결제 = 기사 손에 현금 없음 = 사무실 몫 충당 불가).

### 2.3 결제 수단별 사무실 입장 (calculatePaymentBreakdown + calculateRealIncome)
```kotlin
val cashSum = cashTrips.sumOf { it.fare } +
    cashPlusPointTrips.sumOf { trip.cashAmount ?: 0 }
val bankSum = bankTrips.sumOf { it.fare }              // 이체 전체
val creditSum = creditTrips.sumOf { fare or creditAmount } // 외상
val pointSum = cashPlusPointTrips.sumOf { fare - cashReceived } +
    pointOnlyTrips.sumOf { it.fare }                    // 포인트

// 사무실 실수입 = 기사납입 + 이체 + 미수금 - 포인트차감
fun calculateRealIncome(driverDeposit, bankSum, creditSum, pointSum) =
    driverDeposit + bankSum + creditSum - pointSum
```

→ **이체** = 사무실로 즉시 수금 (`+ bankSum`)
→ **외상** = 사무실 미수금 (`+ creditSum`, 회수 시점에 별도 처리)
→ **포인트** = 사무실 실손 (`- pointSum`)
→ **현금** = 기사 수령 → 기사가 사무실 몫 입금 (`driverDeposit = cashSum - driverShare`)

### 2.4 본인 운영 정정 (2026-05-25)
| 항목 | 본인 발화 | 코드 정합 |
|------|----------|----------|
| 카드 | 미사용 | 상수 존재하나 운영 X |
| 이체 | 손님 → *사무실* 입금 (기사 X) | ✅ `bankSum` 별도 수입으로 분리 |
| 외상 | 사무실 채권 + *매니저 수금 책임* | ✅ `creditSum` + `CreditManagementScreen`에서 고객별 관리 |
| 포인트 | 사무실 실손 (사무실이 손해 흡수) | ✅ `- pointSum`으로 실수입에서 차감 |
| 외상/이체/포인트 | *기사 몫 선지급* (기사 입장 = 운행 즉시 기사 몫 받은 것으로 처리) | ✅ `creditAmount = fare` (기사 입장 통합 변수) + `carryOver.balance` 양수 = 사무실이 기사에게 줄 돈 누적 |
| 기사 관점 | 현금 외 = 모두 외상 등가 | ✅ `calculateCreditForTrip`에서 이체/외상/포인트 모두 `creditAmount = fare` |

---

## 3. 정산 공식

### 3.1 콜 단위 (콜 추가 시)
```
fare                = 콜 운행료
officeDeposit (콜)  = fare × depositRatio / 100   (사무실 몫)
driverShare (콜)    = fare - officeDeposit         (기사 몫)
cashReceived        = 현금 수령액 (현금: fare, 현금+포인트: cashAmount, 그 외: 0)
creditAmount        = 사무실 줘야 할 부족분 (현금 외: fare, 현금+포인트: fare - cashAmount)
pointsUsed          = 포인트 사용액
```

### 3.2 일일 단위 (기사 1명)
```
totalFare           = sum(콜.fare)
officeDeposit       = totalFare × depositRatio / 100
driverShare         = totalFare - officeDeposit
totalCredit         = sum(콜.creditAmount)   // 현금 외 결제 합
cashReceived (총)   = sum(콜.cashReceived)
rawFinalDeposit     = officeDeposit - totalCredit    // 외상 차감 후 사무실 몫
finalDeposit        = rawFinalDeposit (또는 양수만 의미 있음)
realDeposit         = 기사가 UI에서 입력한 실 납입액
                      기본값 표시 = adjustedDeposit (이월 반영)
settlementDiff      = realDeposit - adjustedDeposit
                      양수: 환급금 (기사가 받을 돈)
                      음수: 미납금 (기사가 더 낼 돈)
```

**해석**:
- `rawFinalDeposit > 0`: 사무실에 입금할 현금이 있음
- `rawFinalDeposit < 0`: 사무실이 기사에게 줘야 할 돈 (외상이 사무실 몫보다 큼 = 기사 몫 선지급 발생)

### 3.3 이월 (carryOver)
```
originalCarryOver   = 마감 직전 carryOver.balance (어제까지 누적)
calculatedCarryOver = originalCarryOver - finalDeposit + realDeposit
                    = originalCarryOver + (realDeposit - finalDeposit)
                    = originalCarryOver + settlementDiff
```

**해석**:
- carryOver.balance 양수 = *사무실이 기사에게 줄 돈* 누적 (이체/외상/포인트로 기사 몫 선지급 미정산분)
- carryOver.balance 음수 = *기사가 사무실에 줄 돈* 누적 (현금 콜 사무실 몫 부족 입금분)
- finalDeposit > realDeposit → settlementDiff 음수 → carryOver 감소 (기사가 덜 입금 = 미납 증가)
- realDeposit > finalDeposit → settlementDiff 양수 → carryOver 증가 (기사가 더 입금 = 환급 발생)

### 3.4 driver_app SettlementCalc.kt 보조 함수
```kotlin
calculateAdjustedDeposit(rawFinalDeposit, carryOverBalance):
  carryOverBalance >= 0 (미수령금):
    rawFinalDeposit > 0 ? maxOf(0, rawFinalDeposit - carryOverBalance) : 0
  carryOverBalance < 0 (미납금):
    rawFinalDeposit > 0 ? rawFinalDeposit + (-carryOverBalance) : (-carryOverBalance)

calculateRemainingCarryOver(rawFinalDeposit, carryOverBalance):
  carryOverBalance >= 0:
    rawFinalDeposit > 0 ? maxOf(0, carryOverBalance - rawFinalDeposit)
                        : carryOverBalance + (-rawFinalDeposit)
  carryOverBalance < 0:
    rawFinalDeposit > 0 ? (carryOverBalance + rawFinalDeposit)
                        : carryOverBalance + (-rawFinalDeposit)

calculateUsedFromCarryOver(rawFinalDeposit, carryOverBalance):
  rawFinalDeposit > 0 && carryOverBalance > 0 ? minOf(carryOverBalance, rawFinalDeposit) : 0
```

→ 기사 UI의 *실납입* 기본값은 `adjustedDeposit` (이월 반영). 기사가 정정 가능.

---

## 4. 상태 머신 (3종)

### 4.1 `dailySettlement.status`
```
WORKING          (기본값, 기사 미마감)
  ↓ submitDailySettlement
PENDING_CONFIRM  (기사 마감 완료, 매니저 확인 대기)
  ↓ confirmDailySettlement
CONFIRMED        (매니저 확인 완료)
  또는
  ↓ rejectDailySettlement
REJECTED         (매니저 거절, 기사 재제출 필요)
```

**잔존 위험**: WORKING → PENDING_CONFIRM 전이 *강제 X* (기사가 마감 안 해도 다음날 진입 가능). PENDING_CONFIRM → CONFIRMED 전이 *강제 X* (매니저 확인 안 해도 다음날 진입 가능).

### 4.2 `carryOver.status`
```
PENDING          (미수령/미납 상태)
  ↓ transferCarryOver (매니저 액션)
TRANSFERRED      (매니저 이체 완료, 기사 수령 확인 대기)
  ↓ confirmReceiveCarryOver (기사 액션)
SETTLED          (기사 수령 확인 완료, balance=0)

  ↑ balance != 0 변경 시 PENDING으로 회귀 가능
```

**잔존 위험**: TRANSFERRED 상태에서 기사가 수령 확인 안 해도 잔존. 운영 영향은 *UI 표시*만, 신규 정산 진행에 영향 적음.

### 4.3 `settlementSession.metadata.isFinalized`
```
false  (기본값, 일일 마감 안 됨)
  ↓ finalizeSettlementSession (매니저 액션, CF `finalizeSettlementAndNotifyDrivers`)
  또는 autoFinalizeSettlements (매일 06:10 KST cron)
true   (일일 마감 완료, calls 추가 차단)
```

**잔존 위험**: 06:10 cron이 *어제 영업일*만 처리 → 더 이전 미마감 세션은 *영구 잔존*.

---

## 5. 영업일 정의 (★ 중요 — 본인 기억과 코드 불일치)

### 5.1 `calculateWorkDate` (settlement.ts:60, SettlementViewModel.kt:383, getTodaySessionDate L1026)
```typescript
// settlement.ts:60
function calculateWorkDate(timestamp: Date): string {
  const utc = new Date(timestamp);
  const koreaTime = new Date(utc.getTime() + (9 * 60 * 60 * 1000));
  if (koreaTime.getHours() < 6) {
    koreaTime.setDate(koreaTime.getDate() - 1);
  }
  return koreaTime.toISOString().substring(0, 10);
}
```

```kotlin
// SettlementViewModel.kt:383
private fun calculateWorkDate(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    if (calendar.get(Calendar.HOUR_OF_DAY) < 6) {
        calendar.add(Calendar.DAY_OF_MONTH, -1)
    }
    return SimpleDateFormat("yyyy-MM-dd").format(calendar.time)
}
```

→ **영업일 = 새벽 06:00 ~ 다음날 새벽 06:00** (한국 시간 기준)

### 5.2 `autoFinalizeSettlements` cron (index.ts:5008)
```typescript
schedule: "10 6 * * *",       // 매일 새벽 6시 10분 KST
timeZone: "Asia/Seoul"
```

→ **매일 06:10 KST에 어제 영업일 자동 마감**

### 5.3 ⚠️ 본인 기억 vs 코드 — **불일치**
- 본인 발화 (2026-05-25): "영업일은 오전 10시로 잡혀있지 않나?"
- 코드 (5/25 master 헤드 + manager-direct-drive 브랜치): **새벽 6시 기준**
- 결론: 본인 기억 *오류*. 또는 *과거에 10시였다가 6시로 변경*되었을 가능성 (git blame 미확인). 현재 코드는 6시.
- PTT plan §3 정산 단순화 진입 시 *영업일 시간 본인 결정* 필요 (6시 유지 vs 10시 변경 vs 다른 시간).

---

## 6. 정산 흐름 (시퀀스)

### 6.1 콜 종료 → 정산 세션 추가 (자동)
- 트리거: `onCallCompletedUpdateSettlement` (index.ts:4965, onDocumentUpdated)
- 조건: `before.status !== "COMPLETED" && after.status === "COMPLETED" && !handledByManager`
- 호출: `addCallToSettlementSession(provinceId, cityId, officeId, callData, callId)` (settlement.ts:88)
- 동작:
  - `workDate = calculateWorkDate(completedAt)` 으로 세션 doc ID 결정
  - 세션 존재 + `isFinalized=false` → calls 배열에 추가 + totals 재계산
  - 세션 존재 + `isFinalized=true` → **추가 차단** (warn log)
  - 세션 없음 → 새 세션 생성 (depositRatio는 office 문서에서 읽음, 기본 60)

### 6.2 기사 업무 마감 → submitDailySettlement
- 트리거: 기사가 HistorySettlementScreen에서 "업무마감" 버튼 (HistorySettlementScreen.kt:1036)
- 활성 조건: `isDepositConfirmed && totalCount > 0` (실납입 확인 + 1콜 이상)
- 호출: `viewModel.submitDailySettlement(actualDeposit, onResult)` (DriverViewModel.kt:1592)
- 동작:
  - `isIntegration = (이전 dailySettlement.status == PENDING_CONFIRM)` — 재제출 케이스
  - `calculatedCarryOver = originalCarryOver - finalDeposit + realDeposit` (DriverViewModel.kt:1654)
  - Firestore `designated_drivers/{driverId}.dailySettlement`에 `status="PENDING_CONFIRM"` 저장
  - **★ Firestore `designated_drivers/{driverId}.status`도 `"PENDING_CONFIRM"`으로 동시 set** (DriverViewModel.kt:1685) — 다음 콜 배차 차단 게이트 자리. 매니저가 `confirmDailySettlement`해서 `status="WAITING"`으로 풀어줘야 다음 콜 받을 수 있음.
  - 기사 logout *안 함* (매니저 확인 대기)

**미마감 누락 위험 (부분 차단됨)**:
- "업무마감" 버튼 누름이 *강제 X*. 기사가 앱 닫거나 logout 후 종료 가능 → `dailySettlement.status = WORKING` 또는 *부재* 상태로 다음날 진입.
- 단 *마감 후* 단계는 driver.status=PENDING_CONFIRM 게이트로 *배차 차단 작동 중* (매니저 confirm 안 함 → 기사 다음날 콜 못 받음).

### 6.3 매니저 정산 확인 → confirmDailySettlement
- 트리거: 매니저가 DailySessionScreen 또는 DriverSummaryScreen에서 "정산확인" 버튼
- 호출: `viewModel.confirmDailySettlement(driverId, settlementDiff, onResult)` (SettlementViewModel.kt:1866)
- 동작 (transaction):
  - `newBalance = dailySettlement.calculatedCarryOver` (기사앱 계산값 *그대로 사용*, 매니저측 검증 X)
  - 기존 `carryOver.status == TRANSFERRED` 면 TRANSFERRED 유지 (이체 플래그 보호)
  - 그 외: `newBalance > 0 → PENDING`, `newBalance < 0 → PENDING`, `newBalance == 0 → SETTLED`
  - update: `dailySettlement.status="CONFIRMED"`, `carryOver.balance=newBalance`, `driver.status="WAITING"`
  - FCM: `notifyDriverSettlementResult` callable로 기사에게 알림

**미확인 누락 위험**: 매니저가 "정산확인" 안 함 → `PENDING_CONFIRM` 잔존 → 기사 *퇴근 불가* (HistorySettlementScreen.kt:967, "매니저 확인 대기 중" 카드 표시).

### 6.4 매니저 이체 → transferCarryOver
- 트리거: 매니저가 DriverSummaryScreen에서 "이체하기" 버튼
- 호출: `viewModel.transferCarryOver(driverId, driverName, carryOverBalance, todayUnpaid, onResult)` (SettlementViewModel.kt:1586)
- 동작:
  - `totalBalance = carryOverBalance` (confirmDailySettlement에서 이미 오늘분 포함된 calculatedCarryOver 저장됨 → 재합산 X)
  - Firestore set + merge: `carryOver.status="TRANSFERRED"`, `transferredAt=now`, `transferredBy=adminId`
  - 로컬 carryOverList 즉시 업데이트 (리스너 대기 X)
  - FCM: `sendDriverNotification` callable → 기사에게 "CARRYOVER_TRANSFERRED" 알림
- ⚠️ **실제 송금은 코드 안에 없음** — 매니저가 *별도로 카카오뱅크/계좌이체 수기 송금* 또는 *현금 정산 시 차감*. 코드는 *상태 기록 + 알림*만.

### 6.5 기사 수령 확인 → confirmReceiveCarryOver
- 트리거: 기사가 HistorySettlementScreen에서 "수령완료" 버튼 (carryOverStatus=TRANSFERRED 시 표시)
- 호출: `viewModel.confirmReceiveCarryOver(onResult)` (DriverViewModel.kt:1558)
- 동작: Firestore update 5개 필드 (DriverViewModel.kt:1569-1577)
  - `carryOver.balance = 0`
  - `carryOver.status = "SETTLED"`
  - `carryOver.transferredAt = null`
  - `carryOver.transferredBy = null`
  - `carryOver.lastUpdatedAt = now()`

### 6.6 매니저 일괄 마감 → clearAllTrips
- 트리거: 매니저가 SettlementTabHost에서 "전체내역 초기화"
- 호출: `clearAllTrips()` (SettlementViewModel.kt:694)
- 동작:
  - Room `sessions` 테이블에 새 세션 INSERT + trips 모두 finalized 마킹
  - 직접운행 트립도 Room settlements에 isFinalized=true로 기록
  - `offices/{o}.settlementLastCleared = now()` 갱신 → 직접운행 리스너 재필터링
  - 기사별 `processCarryOverOnFinalize(driverId, todayResult)` 호출 (CONFIRMED/REJECTED 기사는 건너뜀)
  - 기사별 `clearDailySettlement(driverId)` 호출 → `dailySettlement` 필드 *전체 삭제*
  - `closingTime` SharedPreferences 저장 (DashboardViewModel용)

### 6.7 자동 마감 → autoFinalizeSettlements
- 트리거: cron `10 6 * * *` (KST, index.ts:5008)
- 호출: `autoFinalizeSettlementSessions()` (settlement.ts:246)
- 동작:
  - `yesterdayDate = getYesterdayWorkDate()` (어제 영업일)
  - 모든 사무실 순회 → `settlementSessions/{yesterdayDate}` 조회
  - 존재 + `isFinalized=false` → `metadata.isFinalized=true` 업데이트
  - 이미 finalized → 스킵
  - **carryOver / dailySettlement는 손대지 않음** — session 마감 플래그만 갱신

### 6.8 Cleanup Gate (5/10 추가)
- 트리거: 매니저앱 ON_RESUME 시점 (DashboardViewModel.kt, commit `f46ff609`)
- 게이트 조건: 오늘 09:00 KST 이후 + 같은 날 첫 ON_RESUME
- 동작: PENDING_CONFIRM 잔존 기사 *banner* 표시 → 매니저 명시 클릭 시 `confirmAllPendingDailySettlements` (SettlementViewModel.kt:1979)
- 일괄 확정 로직은 `confirmDailySettlement`와 동일 (각 driverId마다 transaction)
- ⚠️ *자동 확정 X*. 매니저가 banner 무시하면 PENDING_CONFIRM *영구 잔존* 가능.

---

## 7. 핵심 함수 자리 표

### 7.1 call_manager (SettlementViewModel.kt)
| 함수 | 라인 | 역할 |
|------|-----|------|
| `init` | 153 | 콜드 스타트 시 carryOverListener / officeLastClearedListener / managerDirectListener 시작 |
| `loadSettlementData` | 394 | office.settlementLastCleared + depositRatio 로드 → fetchCompletedCalls |
| `fetchCompletedCallsWithDriverFilter` | 452 | 기사별 settlementLastCleared 적용 콜 조회 → Room INSERT |
| `startCallsListener` | 533 | 실시간 COMPLETED 콜 리스너 2개 (updatedAt + completedAt 기준) |
| `startCarryOverListener` | 1492 | designated_drivers 컬렉션 전체 리스너 → carryOver + dailySettlement 파싱 → carryOverList / dailySettlementList state 갱신 |
| `clearAllTrips` | 694 | 매니저 일괄 마감 (Room 세션 + processCarryOverOnFinalize + clearDailySettlement + office.settlementLastCleared) |
| `confirmSettlement` | 1129 | 개별 콜 confirmedByOffice=true 토글 (transaction) |
| `finalizeSettlementSession` | 1202 | CF `finalizeSettlementAndNotifyDrivers` 호출 (직접운행 only면 스킵) |
| `confirmDailySettlement` | 1866 | 기사 dailySettlement.status=CONFIRMED + carryOver 갱신 (transaction) |
| `confirmAllPendingDailySettlements` | 1979 | Cleanup gate 일괄 확정 (5/10 추가) |
| `rejectDailySettlement` | 2076 | dailySettlement.status=REJECTED (carryOver 미변경) |
| `transferCarryOver` | 1586 | carryOver.status=TRANSFERRED + FCM (실제 송금 X, 상태만) |
| `cancelTransfer` | 1713 | carryOver.status=PENDING 회귀 |
| `processCarryOverOnFinalize` | 1759 | clearAllTrips에서 호출. CONFIRMED/REJECTED 기사는 건너뜀, PENDING_CONFIRM은 calculatedCarryOver 사용 |
| `clearDailySettlement` | 1834 | dailySettlement 필드 *전체 삭제* (FieldValue.delete) |

### 7.2 driver_app (DriverViewModel.kt)
| 함수 | 라인 | 역할 |
|------|-----|------|
| `startCarryOverListener` | 1500 | 기사 본인 designated_drivers/{driverId} 리스너 → todaySettlement / dailySettlementStatus / carryOver state. **PENDING_CONFIRM 시 calculatedCarryOver로 masking 표시** (UI 일관성, L1530-1538) — 업무마감 후 매니저 확인 대기 중 carryOver 표시값을 *계산된 잔액*으로 즉시 반영. TRANSFERRED 상태는 masking 안 함 (원본 확정값). |
| `confirmReceiveCarryOver` | 1558 | carryOver 5개 필드 update (balance=0, status=SETTLED, transferredAt=null, transferredBy=null, lastUpdatedAt=now) |
| `submitDailySettlement` | 1592 | 기사 업무 마감 (isIntegration 분기, calculatedCarryOver 계산, dailySettlement 저장 + **driver.status=PENDING_CONFIRM 동시 set** = 배차 차단 게이트) |
| `clearSettlement` | ~1880 | 퇴근 처리 (settlementLastCleared 갱신, 로컬 todaySettlement 초기화) |
| `refreshTodaySettlement` | ~1830 | calls 컬렉션 기반 오늘 정산 재계산 |

### 7.3 functions (settlement.ts + index.ts)
| 함수 | 위치 | 역할 |
|------|-----|------|
| `calculateWorkDate` | settlement.ts:60 | 새벽 6시 이전 = 전날 |
| `addCallToSettlementSession` | settlement.ts:88 | onCallCompletedUpdateSettlement에서 호출, calls 배열에 추가 |
| `recalculateTotals` | settlement.ts:202 | 결제 수단별 집계 (totalCash/totalCard/totalCredit/totalPoints) |
| `autoFinalizeSettlementSessions` | settlement.ts:246 | 어제 모든 사무실 세션 isFinalized=true |
| `checkSettlementDiscrepancies` | settlement.ts:313 | 원본 calls vs 세션 calls 불일치 검사 |
| `notifyDriversSettlementFinalized` | settlement.ts:394 | 일일 마감 시 로그인 기사에게 FCM (SETTLEMENT_FINALIZED) |
| `notifyDriverSettlementResultHandler` | settlement.ts:544 | confirmDailySettlement 후 기사에게 FCM (SETTLEMENT_CONFIRMED/REJECTED) |
| `onCallCompletedUpdateSettlement` | index.ts:4965 | COMPLETED 전이 트리거 |
| `autoFinalizeSettlements` | index.ts:5008 | cron "10 6 * * *" KST |

---

## 8. Cloud Functions 정산 트리거 + 스케줄

### 8.1 트리거
- `onCallCompletedUpdateSettlement`: `provinces/{p}/cities/{c}/offices/{o}/calls/{callId}` onDocumentUpdated
  - 조건: COMPLETED 진입 + !handledByManager

### 8.2 스케줄
- `autoFinalizeSettlements`: `"10 6 * * *"` Asia/Seoul (매일 06:10 KST)

### 8.3 Callable
- `finalizeSettlementAndNotifyDrivers` (call_manager → 매니저 마감 시)
- `notifyDriverSettlementResult` (call_manager → confirmDailySettlement 후)
- `sendDriverNotification` (call_manager → transferCarryOver 후 CARRYOVER_TRANSFERRED FCM)

---

## 9. 본인 운영 정정 vs 코드 정합 표

| 본인 정정 (2026-05-25) | 코드 자리 | 정합 |
|--------------------------|----------|------|
| 카드 미사용 | paymentMethod 상수에는 존재 | 운영 X (코드 잔존) |
| 이체 = 손님 → 사무실 즉시 수금 | `calculateRealIncome: + bankSum` | ✅ |
| 포인트 = 사무실 실손 | `calculateRealIncome: - pointSum` | ✅ |
| 외상 = 사무실 미수금 | `creditSum` + `CreditManagementScreen` (고객별) | ✅ |
| 외상 수금 책임자 = 매니저 | `CreditManagementScreen.kt` 매니저 UI | ✅ |
| 외상/이체/포인트 = 기사 몫 *선지급* | `carryOver.balance` 양수 누적 = 사무실이 기사에게 줄 돈 | ✅ |
| 기사 관점 = 현금 외 = 외상 등가 | `calculateCreditForTrip`: 이체/외상/포인트 = `creditAmount = fare` | ✅ |
| 영업일 오전 10시 (본인 기억) | `calculateWorkDate` 새벽 6시 + `autoFinalize` 06:10 KST | ❌ **불일치** — 코드는 6시, 본인 기억은 10시. 결정 자리. |

---

## 10. (이전됨) 5/7 incident 원인 후보 + 11. 잠재 문제 + 12. PTT 연결

본 절은 *분석 의견*이라 책임 분리 원칙(5/27 본인 통찰)에 따라 **redesign 파일로 이전**:

- `settlement_redesign_2026-05-27.md` §7 = 5/7 incident 원인 후보 (역사 자료, 본인 결정: 원인 특정 부차)
- `settlement_redesign_2026-05-27.md` §8 = 알려진 잠재 문제 + PTT plan §3 연결
- §11.5는 5/27 코드 정독 후 **잘못 분석으로 폐기 확정** (redesign §5.4 참조)

본 definitive 파일은 *코드 사실*만 (§1~9 + §13~14). 분석은 redesign으로.

---

## 13. 미확인 자리 (본인 답 필요)

5/24 이전 시점에 *영업일 10시*였다가 *6시로 변경*된 흔적은 본 명문화 작성 중 git log 미확인. 본인이 *언제 어떤 이유로 변경*했는지 기억나면 자료 보완 가능. 단 현재 코드 (5/25 master + manager-direct-drive)는 *명백히 6시*.

---

## 14. 이 명문화의 사용법

본 파일은 **정산 *코드 사실*만**의 단일 출처. 책임 분리 원칙(2026-05-27 본인 통찰)에 따라:

- **본 파일** (`settlement_logic_definitive_2026-05-25.md`) = 코드 사실. §1~14 + 본문 정정. 코드 변경 시만 갱신
- **재설계 파일** (`settlement_redesign_2026-05-27.md`) = 본인 결정 + Zero-base 모델 + 강제 게이트 인벤토리. 본인 결정 변경 시 갱신
- **세션 인계** (`settlement_session_2026-05-27.md`) = 본 세션 흐름 스냅샷

사용 흐름:
- **PTT plan §3 정산 단순화 진입 시**: 본 파일 §12 "영향받는 자리" + 재설계 §4 강제 게이트 인벤토리 + §7 코드 모듈 분리 원칙을 *작업 매트릭스*로 사용
- **운영 변경 시**: 본 파일 갱신 후 MEMORY.md 인덱스 갱신
- **추측으로 묻기 전**: 본 파일 *먼저 확인* (본인 지적 정합)
- **본인 결정 사항 확인 시**: 재설계 파일로 이동 (본 파일에 결정 사항 박지 않음)

