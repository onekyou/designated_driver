# 정산 로직 정밀 크로스 검증 결과

> 마지막 업데이트: 2026-02-24 KST
> 검증 범위: Call Manager, Driver App, Cloud Functions

---

## 1. 정산 구조 파악

### 1.1 Firestore 데이터 구조

```
provinces/{pId}/cities/{cId}/offices/{oId}/
├── settlementSessions/{YYYY-MM-DD}     ← 공유 정산 문서 (일별 1개)
│   ├── metadata: { version, depositRatio, isFinalized, lastUpdatedBy, ... }
│   ├── totals: { totalFare, totalDeposit, totalDriverShare, totalCash, totalCard, totalCredit, totalPoints, callCount }
│   └── calls: [ { callId, driverId, fare, paymentMethod, cashReceived, creditAmount, pointsUsed, confirmedByOffice, ... } ]
│
├── calls/{callId}                       ← 개별 콜 문서
│   ├── status: "COMPLETED"
│   ├── fareFinal / fare_set
│   ├── paymentMethod: "현금" | "이체" | "외상" | "현금+포인트" | "포인트"
│   ├── cashReceived (현금/현금+포인트만 설정)
│   ├── creditAmount (현금+포인트만 설정 ⚠️)
│   ├── pointsUsed
│   └── completedAt
│
├── designated_drivers/{driverId}
│   ├── dailySettlement: { date, finalDeposit, realDeposit, settlementDiff, totalFare, totalCredit,
│   │                       tripCount, status, calculatedCarryOver, originalCarryOver, ... }
│   ├── carryOver: { balance, status, todayAmount, transferredAt, transferredBy, ... }
│   └── settlementLastCleared: Timestamp  ← 기사별 마감 시점
│
├── depositRatio: 60                     ← 사무실 수수료율 (기본 60%)
└── settlementLastCleared: Timestamp     ← 사무실 마감 시점 (CF가 설정)
```

### 1.2 결제 수단별 처리 로직

| 결제수단 | cashReceived 설정 | creditAmount 설정 | pointsUsed 설정 | CF totalCash | CF totalCard | CF totalCredit |
|---------|:-:|:-:|:-:|:-:|:-:|:-:|
| **현금** | ✅ = fare | ❌ | ❌ | += fare | 0 | 0 |
| **이체** | ❌ | ❌ | ❌ | 0 | += fare | 0 |
| **외상** | ❌ | ❌ ⚠️ | ❌ | 0 | 0 | 0 ⚠️ |
| **현금+포인트** | ✅ = cashAmount | ✅ = fare - cashAmount | ❌ | += cashReceived | 0 | += creditAmount |
| **포인트** | ❌ | ❌ | ✅ = fare | 0 | 0 | 0 |

> ⚠️ **외상**: creditAmount가 Firestore에 기록되지 않아 settlementSessions에서 0으로 집계됨

### 1.3 기사 몫 계산 방식

```
총 운행료 = Σ(각 콜의 fareFinal)
사무실 몫 (납입액) = 총 운행료 × depositRatio / 100  (기본 60%)
기사 몫 = 총 운행료 - 사무실 몫                       (기본 40%)
```

**반올림 차이**:
| 위치 | 계산식 | 방식 |
|------|--------|------|
| Manager DriverSummaryScreen | `(fareSum * ratio / 100.0).roundToInt()` | **반올림** |
| Manager createSettlementSession | `(totalFare * ratio / 100)` | 정수 나눗셈 (버림) |
| Driver App | `(totalFare * ratio / 100.0).toInt()` | 버림 |
| Cloud Functions | `Math.floor(totalFare * depositRatio / 100)` | 버림 |

→ Manager UI만 반올림, 나머지 모두 버림 → **총 운행료가 홀수일 때 1원 차이 발생**

### 1.4 현금 과부족 처리

```
현금 수령 = Σ(현금 콜 fare) + Σ(현금+포인트 콜 cashReceived)
기사 실납부 = 현금 수령 - 기사 몫
  → 양수: 기사가 사무실에 현금 납입
  → 음수: 사무실이 기사에게 미지급 (이월 발생)
```

### 1.5 이월(CarryOver) 로직

```
상태 흐름: PENDING → TRANSFERRED → SETTLED (0으로 리셋)

오늘 미지급 계산 (Manager UI / DriverSummaryScreen):
  totalNonCash = Σ(비현금 콜의 fare)    ← 이체/외상/포인트 전부 포함
  rawFinalDeposit = (totalFare × ratio%) - totalNonCash
  todayUnpaid = max(0, -rawFinalDeposit)  ← 음수면 미지급 발생

업무마감 시 계산 (Driver App / submitDailySettlement):
  finalDeposit = officeDeposit - totalCredit    ← officeDeposit = totalFare × ratio%
  remainingCarryOver = originalCarryOver - finalDeposit + realDeposit
    → calculatedCarryOver 필드에 저장

매니저 확인 시 (Manager / confirmDailySettlement):
  carryOver.balance = dailySettlement.calculatedCarryOver  ← 기사가 계산한 값 직접 사용
```

### 1.6 Manager 마감 시 변경되는 Firestore 문서

| 시점 | 문서 경로 | 변경 필드 |
|------|----------|----------|
| Manager "업무 마감" | `settlementSessions/{date}` | metadata.isFinalized=true, version++, lastUpdatedBy="call_manager_finalize" |
| | `offices/{oId}` | settlementLastCleared = now |
| Manager "정산 확인" | `designated_drivers/{dId}` | dailySettlement.status="CONFIRMED", carryOver.balance=calculatedCarryOver |
| Manager "이체하기" | `designated_drivers/{dId}` | carryOver.status="TRANSFERRED", balance=total, transferredAt, transferredBy |
| Driver "업무마감" | `designated_drivers/{dId}` | dailySettlement = { date, finalDeposit, realDeposit, ..., status="PENDING_CONFIRM" } |
| Driver "수령완료" | `designated_drivers/{dId}` | carryOver.balance=0, status="SETTLED" |
| Driver "저장/초기화" | `designated_drivers/{dId}` | settlementLastCleared=now, status="OFFLINE" |

---

## 2. 발견된 이슈

### STL-01 (High): 외상/이체/포인트 결제 시 creditAmount Firestore 미기록

**현상**: Driver App `confirmAndFinalizeTrip`에서 "현금+포인트"일 때만 `creditAmount = fare - cashAmount`를 설정. "외상", "이체", "포인트" 결제에서는 creditAmount를 기록하지 않음.

**코드 위치**: `driver_app/.../DriverViewModel.kt:648-663`
```kotlin
if (paymentMethod == "현금" && cashAmount != null) {
    tripData[Constants.FIELD_CASH_RECEIVED] = cashAmount
} else if (paymentMethod == "현금+포인트" && cashAmount != null) {
    tripData[Constants.FIELD_CASH_RECEIVED] = cashAmount
    tripData["creditAmount"] = fareToSet - cashAmount
}
// "외상", "이체", "포인트" → creditAmount 미설정
```

**영향**:
- Cloud Functions `addCallToSettlementSession`에서 `callData.creditAmount || 0` → 외상 금액 0으로 집계
- `settlementSessions.totals.totalCredit`이 실제보다 적게 기록됨
- 정산 불일치 검사(`checkSettlementDiscrepancies`)에서도 부정확한 비교
- **실시간 UI에는 영향 없음** (Manager/Driver 모두 paymentMethod 기반 로컬 계산 사용)

**파일**: `settlement.ts:104`, `SettlementViewModel.kt:1124`

---

### STL-02 (High): Manager pointsUsed=0 하드코딩

**현상**: Manager `createSettlementSessionFromLocalData`에서 모든 콜의 pointsUsed를 0으로 설정.

**코드 위치**: `call_manager/.../SettlementViewModel.kt:1139`
```kotlin
pointsUsed = 0L,  // ← 항상 0
```

**영향**:
- Manager가 먼저 세션을 생성하면 (CF보다 먼저) pointsUsed가 0으로 기록됨
- CF가 먼저 세션을 생성하면 정상 (CF는 `callData.pointsUsed` 사용)
- SettlementData 모델에도 pointsUsed 필드 자체가 없음 (`SettlementData.kt`)
- **포인트 결제가 있는 날**: Manager 세션의 totalPoints = 0

**파일**: `SettlementData.kt` (pointsUsed 필드 부재)

---

### STL-03 (High): submitDailySettlement이 settlementLastCleared를 갱신하지 않음

**현상**: 기사가 "업무마감"(submitDailySettlement)을 해도 `settlementLastCleared`가 업데이트되지 않음. `clearSettlement`(저장/초기화)을 별도로 눌러야 갱신됨.

**코드 위치**: `driver_app/.../DriverViewModel.kt:1328-1425` (submitDailySettlement에 settlementLastCleared 갱신 없음)

**영향**:
- 기사가 "업무마감"만 하고 "저장/초기화"를 안 누르면 → 다음 로그인 시 이전 콜 + 새 콜이 모두 표시
- 다만, `submitDailySettlement`의 통합(isIntegration) 로직이 이전 마감과 합산하므로 데이터 자체는 올바름
- **UX 혼동**: 기사가 이전 운행을 다시 보게 됨

---

### STL-04 (Medium): settlementSessions totalFare ≠ 부분합

**현상**: settlementSessions의 totalFare와 (totalCash + totalCard + totalCredit + totalPoints)가 일치하지 않을 수 있음.

**예시** (외상 2건 × 25,000원):
```
totalFare = 50,000
totalCash = 0, totalCard = 0, totalCredit = 0 (STL-01), totalPoints = 0
→ 부분합 = 0 ≠ 50,000 (50,000원 불명)
```

**코드 위치**: `settlement.ts:183-222` (recalculateTotals: 외상/포인트 switch case 누락)

---

### STL-05 (Medium): 수수료 계산 반올림 불일치

**현상**: Manager UI만 `roundToInt()` (반올림), 나머지 3곳은 `toInt()/Math.floor()` (버림).

**코드 위치**:
- `DriverSummaryScreen.kt:92`: `(fareSum * ratio / 100.0).roundToInt()` ← **반올림**
- `DriverSummaryScreen.kt:70`: `(fareSum * ratio / 100.0).toInt()` ← 버림
- `DriverViewModel.kt:1546`: `(totalFare * ratio / 100.0).toInt()` ← 버림
- `settlement.ts:209`: `Math.floor(totalFare * depositRatio / 100)` ← 버림

**영향**: 총 운행료가 100으로 나누어 떨어지지 않을 때 Manager UI의 기사별 탭과 전체 탭 사이 1원 차이 가능

---

### STL-06 (Medium): settlementLastCleared 2-Way 경로 불일치

**현상**: CF는 **office** 문서에, Driver는 **driver** 문서에 `settlementLastCleared`를 설정/읽음.

| 작업 | 쓰기 대상 | 읽기 대상 |
|------|----------|----------|
| CF finalizeSettlement | `offices/{oId}.settlementLastCleared` | - |
| Manager loadSettlementData | - | `offices/{oId}.settlementLastCleared` ✓ |
| Driver clearSettlement | `drivers/{dId}.settlementLastCleared` | - |
| Driver loadSettlementData | - | `drivers/{dId}.settlementLastCleared` |

**영향**: Manager가 "업무 마감"을 해도 기사 문서의 `settlementLastCleared`는 갱신 안 됨. 기사가 `clearSettlement`을 직접 눌러야 기사 측 데이터가 필터링됨.

---

### STL-07 (Medium): 매니저 확인 후 재로그인 시 dailySettlement 이전 세션 누락

**현상**: 기사 10건 운행 → 업무마감 → 매니저 확인(CONFIRMED) → 기사 재로그인 → 1건 추가 → 재업무마감 시 `isIntegration = false` (CONFIRMED ≠ PENDING_CONFIRM) → 이전 10건이 dailySettlement에서 빠짐.

**코드 위치**: `DriverViewModel.kt:1354`
```kotlin
val isIntegration = prevSettlement.status == DailySettlementStatus.PENDING_CONFIRM
// CONFIRMED 상태면 통합 안 됨 → 새 세션으로 덮어씀
```

**영향**: dailySettlement에 기록된 tripCount/totalFare가 실제(11건)보다 적음(1건). 다만 carryOver.balance는 누적이므로 금액 자체는 정확.

---

### STL-09 (Critical): processCarryOverOnFinalize와 confirmDailySettlement의 carryOver 이중 계산

**현상**: Manager "전체내역 초기화"(`clearAllTrips`) 시 기사별로 `processCarryOverOnFinalize()`를 호출하여 carryOver를 업데이트함. 이 계산이 Driver의 `submitDailySettlement`에서 계산한 `calculatedCarryOver`와 **다른 공식**을 사용.

**코드 위치**: `call_manager/.../SettlementViewModel.kt:535-585` (clearAllTrips → processCarryOverOnFinalize)

**Manager 계산 (processCarryOverOnFinalize:1467)**:
```kotlin
val todayResult = cashReceived - driverShare     // 현금수령 - 기사몫
val newBalance = currentBalance - todayResult    // 기존잔액 - 오늘결과
// realDeposit(기사 실납입) 미반영!
```

**Driver 계산 (submitDailySettlement:1379)**:
```kotlin
val remainingCarryOver = originalCarryOver - mergedFinalDeposit + mergedRealDeposit
// realDeposit(기사 실납입) 포함!
```

**차이점**: Manager 공식에 `realDeposit` 미포함 → **realDeposit ≠ 0일 때 결과 불일치**

**시나리오**:
```
carryOver.balance = 50,000 (기존 이월)
오늘 운행: 현금 3건 총 60,000원
driverShare = 24,000 (40%)
cashReceived = 60,000

Driver: realDeposit = 36,000 (실제 납부)
Driver calculatedCarryOver = 50,000 - 36,000 + 36,000 = 50,000

Manager processCarryOverOnFinalize:
todayResult = 60,000 - 24,000 = 36,000
newBalance = 50,000 - 36,000 = 14,000  ← 다름!
```

**가드 조건**: `dailySettlement.status == CONFIRMED → skip` (line 1451) 이지만:
- `PENDING_CONFIRM` 상태에서 clearAllTrips → 가드 통과 → **이중 처리 발생**
- clearAllTrips 직후 `clearDailySettlement()`이 dailySettlement 삭제 → `confirmDailySettlement` 불가

**수정 방향** (다음 세션 실행 예정):
- `processCarryOverOnFinalize`의 공식을 Driver `submitDailySettlement`와 동일하게 변경
- 현재: `newBalance = currentBalance - todayResult` (todayResult = cashReceived - driverShare)
- 변경: `newBalance = currentBalance - finalDeposit + realDeposit` 형태로 realDeposit 반영
- **핵심**: PENDING_CONFIRM 기사도 가드 조건에 포함시키거나, realDeposit을 dailySettlement에서 읽어 반영

**파일럿 운영 가이드 (즉시 적용)**:
> ⚠️ **전체내역 초기화 전에 반드시 기사별 정산 확인(CONFIRMED)을 먼저 완료하세요.**
> CONFIRMED된 기사는 processCarryOverOnFinalize에서 건너뛰므로 (line 1451) 이중 계산이 발생하지 않습니다.
> 순서: 기사별 "정산 확인" 버튼 클릭 → 모든 기사 CONFIRMED 확인 → "전체내역 초기화" 클릭

**영향**: carryOver.balance가 잘못 설정되어 기사 미지급금 불일치

---

### STL-10 (Medium): 신구 정산 시스템 병존

**현상**: 두 개의 정산 시스템이 동시에 존재.

| 시스템 | 위치 | 컬렉션 | 상태 |
|--------|------|--------|------|
| **구** | `finalizeWorkDay.ts` | `settlements/` | 사용 가능 (미삭제) |
| **신** | `handlers/settlement.ts` | `settlementSessions/` | 현재 주 시스템 |

**코드 위치**: `functions/src/finalizeWorkDay.ts` (export됨: `index.ts:2016`)

**영향**: `finalizeWorkDay` Cloud Function이 여전히 export되어 호출 가능. `settlements/` 컬렉션에 별도로 데이터 기록 가능성.

---

### STL-11 (Medium): 자동 마감 스케줄 vs 수동 마감 병존

**현상**: CF `autoFinalizeSettlements`가 매일 06:10에 자동 마감하고, Manager `finalizeSettlementSession`도 수동 마감. 동일 세션이 이중 마감될 수 있음.

**코드 위치**: `index.ts:4182` (autoFinalizeSettlements), `index.ts:4440` (finalizeSettlementAndNotifyDrivers)

**영향**: 자동 마감은 `metadata.isFinalized` 체크 후 스킵하므로 데이터 문제 없음. 단, `settlementLastCleared` 덮어쓰기 타이밍 문제 가능.

---

### STL-08 (Low): 현금+포인트의 creditAmount 의미 혼동

**현상**: "현금+포인트" 결제에서 `creditAmount = fare - cashAmount`로 설정됨. 이 값은 실제로 **포인트 결제분**이지만 필드명이 `creditAmount(외상)`임.

**코드 위치**: `DriverViewModel.kt:662`

**영향**: Manager가 이 값을 외상으로 표시할 수 있음 (외상 관리 화면에서 잘못 집계). 실제로 SettlementViewModel이 `creditAmount > 0`이면 외상 인물에 추가: `addOrIncrementCredit()` 호출 → 포인트 결제가 외상으로 등록됨.

**코드 위치**: `SettlementViewModel.kt:342-353` (creditAmount > 0 이면 외상 인물 추가)

---

## 3. 시나리오 시뮬레이션

### 시나리오 A: 정상 하루

**조건**: 기사 1명, 콜 10건 (현금 5건 × 20,000 / 포인트 3건 × 15,000 / 외상 2건 × 25,000), 수수료 60%

| 항목 | Manager UI | Driver App UI | SettlementSession (Firestore) |
|------|-----------|--------------|-------------------------------|
| totalFare | 195,000 | 195,000 | 195,000 |
| totalCash | 100,000 | 100,000 | 100,000 |
| totalCard | 0 | 0 | 0 |
| totalCredit (비현금) | 95,000 ✓ | 95,000 ✓ | **0** ⚠️ STL-01 |
| totalPoints | (미표시) | (미표시) | 45,000 (CF) / **0** (Manager) ⚠️ STL-02 |
| deposit (60%) | **117,000** ¹ | 117,000 | 117,000 |
| driverShare (40%) | 78,000 | 78,000 | 78,000 |
| realDeposit | 22,000 | 22,000 | (없음) |
| carryOver | 0 | 0 | 0 |

¹ DriverSummaryScreen:92에서 `roundToInt()` 사용 → 195,000 × 60% = 117,000 (차이 없음, 나누어 떨어지므로)

**결과**: ✅ Manager UI와 Driver App UI 일치. ⚠️ SettlementSession의 totalCredit/totalPoints 부정확.

---

### 시나리오 B: 이월 발생

**Day 1**: 기사 콜 8건 (이체 6건 × 20,000 / 현금 2건 × 15,000), 수수료 60%

```
totalFare = 150,000
cashReceived = 30,000 (현금 2건)
driverShare = 60,000 (40%)
officeDeposit = 90,000 (60%)
realDeposit = 30,000 - 60,000 = -30,000 (기사가 현금 부족)
```

**업무마감 (submitDailySettlement)**:
```
totalCredit = 150,000 - 30,000 = 120,000
finalDeposit = 90,000 - 120,000 = -30,000
기사가 realDeposit=0 입력 (현금 납부 안 함)
calculatedCarryOver = 0 - (-30,000) + 0 = 30,000
→ carryOver.balance = 30,000 (사무실이 기사에게 30,000원 미지급)
```

**Day 2**: 기사 로그인 → carryOver.balance = 30,000 표시

**Day 2 콜 7건** (현금 5건 × 18,000 / 외상 2건 × 20,000):
```
totalFare = 130,000
cashReceived = 90,000
driverShare = 52,000
officeDeposit = 78,000
realDeposit = 90,000 - 52,000 = 38,000 (기사가 38,000 납부 가능)
```

**업무마감 (submitDailySettlement)**:
```
originalCarryOver = 30,000 (Day 1에서 이월)
totalCredit = 130,000 - 90,000 = 40,000
finalDeposit = 78,000 - 40,000 = 38,000
기사가 realDeposit=38,000 입력
calculatedCarryOver = 30,000 - 38,000 + 38,000 = 30,000
→ carryOver.balance = 30,000 (여전히 30,000원 미지급)
```

| 확인 포인트 | Manager | Driver | 일치 |
|-----------|---------|--------|------|
| Day 2 carryOver | 30,000 | 30,000 | ✅ |
| Day 2 totalFare | 130,000 | 130,000 | ✅ |
| Day 2 driverShare | 52,000 | 52,000 | ✅ |

**결과**: ✅ 이월 금액 양쪽 일치. 이월 데이터 흐름 정상.

---

### 시나리오 C: 퇴근 기사 재로그인

**흐름**:
1. 기사 10건 운행 → submitDailySettlement (status=PENDING_CONFIRM)
2. clearSettlement → settlementLastCleared = T1, status=OFFLINE
3. Manager가 추가 1건 요청 → 기사 재로그인
4. loadSettlementData → lastClearedMillis = T1 → **이전 10건 미표시** ✓
5. 11번째 콜 완료
6. submitDailySettlement → isIntegration? → prevSettlement.status = **PENDING_CONFIRM** → **통합!** ✓
7. dailySettlement: prev 10건 + curr 1건 = 11건 ✓

**만약 매니저가 2단계 전에 확인(CONFIRMED) 했다면**:
- 5-6단계에서 isIntegration = false (CONFIRMED ≠ PENDING_CONFIRM)
- dailySettlement: **1건만 기록** ⚠️ STL-07
- carryOver.balance는 이전 확인 시 설정된 값 유지 → 금액은 정확

| 확인 포인트 | 결과 |
|-----------|------|
| Manager에서 11건 전부 보이는지 | ✅ (office-level settlementLastCleared 기준, 전체 표시) |
| Driver에서 이전 10건 안 보이는지 | ✅ (clearSettlement 후 T1 이후만 표시) |
| 합산 누락/중복 | ✅ (Manager 기준), ⚠️ (Driver dailySettlement: 시나리오에 따라 1건만) |

---

### 시나리오 D: 복합 이월

**Day 1**: 현금 3건(60,000) + 외상 2건(40,000) + 이체 2건(30,000)
```
totalFare = 130,000 / cashReceived = 60,000 / officeDeposit = 78,000
driverShare = 52,000 / realDeposit = 60,000 - 52,000 = 8,000
```

**⚠️ 이월이 결제 수단별로 분리되는가?**

**아니오.** 이월(carryOver)은 `balance` 단일 금액으로만 관리됨. 현금 부족분과 외상 잔액이 분리되지 않음.

```
carryOver = {
  balance: 30000,    // 단일 누적 금액
  todayAmount: 10000,
  status: "PENDING"
}
```

**외상은 별도 관리**: Manager의 CreditPerson/CreditEntry로 로컬 Room DB에서 관리 (Firestore 미저장).

| 항목 | 관리 위치 | 분리 여부 |
|------|----------|----------|
| 현금 과부족 | carryOver.balance (Firestore) | ❌ 단일 금액 |
| 외상 잔액 | CreditPerson/CreditEntry (Manager Room DB) | ✅ 인물별 |
| 이체 수령 | (별도 관리 없음, 즉시 처리) | - |
| 포인트 | pointTransactions (Firestore, 별도 컬렉션) | ✅ 건별 |

**Day 2**: 포인트 2건(20,000) + 현금 3건(45,000) → 전일 이월 2종 반영?

이월 1종(carryOver.balance)만 반영됨. 외상 잔액은 Manager Room DB에서만 표시되며 carryOver와 별도.

---

### 시나리오 E: 엣지 케이스

#### E-1: 이월 상태에서 기사 앱 강제종료 후 재시작

- carryOver는 **Firestore에 저장**됨 → 앱 재시작해도 유지 ✓
- _todaySettlement은 **로컬 StateFlow**로만 존재 → **초기화됨** ⚠️
- **loadSettlementData**가 재호출되어 Firestore calls에서 다시 계산 → 복구됨 ✓
- **PendingSync** (Driver App Room DB): 오프라인 동기화 대기 데이터는 Room DB에 남아있음 → SettlementSyncWorker가 재시도 ✓

#### E-2: 정산 도중 네트워크 끊김

- **Driver 콜 완료 시**: confirmAndFinalizeTrip은 Firestore update → 실패하면 에러 메시지
  - SettlementRepository.saveCallSettlement → 로컬 Room에 저장 후 네트워크 연결 시 동기화 (PendingSync)
  - **단**, confirmAndFinalizeTrip은 SettlementRepository를 직접 호출하지 않음 → CF 의존
  - CF 트리거는 calls 문서 업데이트 성공 후 자동 실행 → calls 업데이트 자체가 실패하면 전체 실패
- **Manager 마감 시**: Cloud Function 호출 실패 → 에러 메시지, 재시도 가능
- **Manager 정산 확인 시**: Firestore 트랜잭션 사용 → 실패 시 원자적 롤백 ✓

#### E-3: 0원 콜

- fare = 0 → 모든 계산에서 0 처리됨 → 정상 동작
- callCount는 증가 → 총 콜 수에 포함됨

#### E-4: 전액 포인트 콜

- paymentMethod = "포인트", pointsUsed = fare
- cashReceived = 0 → totalCash에 불포함
- **Manager UI**: "포인트" → else 분기 → fare가 totalCredit(비현금)에 포함 → **의미적으로 혼동되지만 금액 계산은 정상**
- **SettlementSession**: totalPoints = fare (CF) / **0** (Manager) ⚠️ STL-02

#### E-5: 외상 콜만 있는 날

- 모든 콜 외상 → cashReceived = 0
- driverShare = totalFare × 40%
- realDeposit = 0 - driverShare = -driverShare (음수)
- **carryOver 발생**: 기사 몫 전액이 미지급
- **Manager**: "현금 정산 대상 없음"으로 표시됨
- **SettlementSession**: totalCredit = 0 ⚠️ STL-01 (실제 외상 전액인데 0으로 기록)

#### E-6: BUG-D12 포인트 이중 적립이 정산에 미치는 영향

- Customer App/Driver App: `pointTransactions` 컬렉션에 적립
- Cloud Functions: `customerPointTransactions` 컬렉션에 적립
- **정산에는 직접 영향 없음**: 포인트 적립은 정산 금액과 무관
- **간접 영향**: 고객 포인트 잔액이 실제보다 높아져 → 다음 콜에서 과도한 포인트 사용 가능 → 그 콜의 cashReceived 감소 → carryOver 증가

---

## 4. 정산 데이터 흐름도

```
[기사: 운행 완료]
    │
    ├──→ calls/{callId} 상태를 AWAITING_SETTLEMENT로 변경
    │
    ▼
[기사: 정산 확인 (confirmAndFinalizeTrip)]
    │
    ├──→ calls/{callId} 업데이트:
    │       status=COMPLETED, fareFinal, paymentMethod,
    │       cashReceived(현금만), creditAmount(현금+포인트만), pointsUsed
    │
    ├──→ 로컬 _todaySettlement StateFlow 즉시 업데이트
    │
    └──→ (CF 트리거) addCallToSettlementSession
            │
            └──→ settlementSessions/{date} 업데이트 (트랜잭션)
                    calls[] 배열에 추가, totals 재계산

[기사: 업무마감 (submitDailySettlement)]
    │
    └──→ drivers/{dId}/dailySettlement 업데이트:
            date, finalDeposit, realDeposit, calculatedCarryOver, status=PENDING_CONFIRM

[Manager: 업무 마감 (finalizeSettlementSession)]
    │
    ├──→ (세션 없으면) createSettlementSessionFromLocalData → 세션 생성
    │        ⚠️ pointsUsed=0 하드코딩, creditAmount=Firestore값(외상=0)
    │
    └──→ (CF) finalizeSettlementAndNotifyDrivers:
            settlementSessions metadata.isFinalized=true
            offices/{oId}.settlementLastCleared = now
            기사에게 SETTLEMENT_FINALIZED FCM

[Manager: 정산 확인 (confirmDailySettlement)]
    │
    └──→ Firestore 트랜잭션:
            drivers/{dId}/dailySettlement.status = CONFIRMED
            drivers/{dId}/carryOver.balance = calculatedCarryOver
            drivers/{dId}/carryOver.status = PENDING/SETTLED

[기사: 저장/초기화 (clearSettlement)]
    │
    └──→ drivers/{dId}.settlementLastCleared = now
         drivers/{dId}.status = OFFLINE
         로컬 상태 초기화
```

---

## 5. 이슈 요약 및 우선순위

| ID | 심각도 | 제목 | 파일 | 영향 범위 |
|----|--------|------|------|----------|
| **STL-09** | **Critical** | processCarryOverOnFinalize ↔ submitDailySettlement 이중 계산 | SettlementViewModel.kt:1431 vs DriverViewModel.kt:1379 | carryOver.balance 불일치 → 미지급금 오류 |
| **STL-01** | **High** | 외상/이체/포인트 결제 시 creditAmount 미기록 | DriverViewModel.kt:648-663 | SettlementSession 감사 데이터 부정확 |
| **STL-02** | **High** | Manager pointsUsed=0 하드코딩 | SettlementViewModel.kt:1139, SettlementData.kt | 포인트 콜이 있는 날 세션 데이터 오류 |
| **STL-03** | **High** | submitDailySettlement이 settlementLastCleared 미갱신 | DriverViewModel.kt:1328-1425 | 재로그인 시 이전 콜 중복 표시 |
| **STL-04** | **Medium** | settlementSessions totalFare ≠ 부분합 | settlement.ts:183-222 | 감사 데이터 불일치 |
| **STL-05** | **Medium** | 수수료 반올림 불일치 (roundToInt vs toInt) | DriverSummaryScreen.kt:92 | Manager UI 1원 차이 |
| **STL-06** | **Medium** | settlementLastCleared 2-Way 경로 불일치 | index.ts:4492, DriverViewModel.kt:1612 | Manager 마감 ≠ 기사 필터링 |
| **STL-07** | **Medium** | 매니저 확인 후 재로그인 시 dailySettlement 누락 | DriverViewModel.kt:1354 | dailySettlement 건수/금액 과소 기록 |
| **STL-10** | **Medium** | 신구 정산 시스템 병존 (settlements vs settlementSessions) | finalizeWorkDay.ts / settlement.ts | 구 시스템 잔존 |
| **STL-11** | **Medium** | 자동 마감(06:10) vs 수동 마감 병존 | index.ts:4182,4440 | settlementLastCleared 타이밍 |
| **STL-08** | **Low** | 현금+포인트 creditAmount 의미 혼동 → 외상 인물 오등록 | SettlementViewModel.kt:342-353 | 외상 관리에서 포인트 결제 오등록 |

### 파일럿 영향도

| 분류 | 이슈 | 이유 |
|------|------|------|
| **즉시 수정** | STL-09 | clearAllTrips 시 PENDING_CONFIRM 기사의 carryOver 이중 처리 → 미지급금 오류 |
| **즉시 수정** | STL-08 | 포인트 결제가 외상 인물로 등록 → 운영 혼동 |
| **나중에** | STL-01, STL-02, STL-04 | settlementSessions 감사 데이터 (라이브 UI 영향 없음) |
| **나중에** | STL-03, STL-06, STL-07 | 기사가 clearSettlement 워크플로우를 지키면 문제 없음 |
| **나중에** | STL-10 | 구 시스템 정리 (사용하지 않으면 제거) |
| **운영 커버** | STL-05, STL-11 | 1원 차이 / 자동마감 isFinalized 체크로 안전 |

---

## 6. 핵심 결론

### 라이브 정산 UI는 정상 (Manager ↔ Driver 일치)

양쪽 모두 `paymentMethod` 기반 로컬 계산을 사용하여 **실시간 표시 금액은 일치**함:
- 총 운행료, 사무실 몫, 기사 몫, 현금 수령, 실 납부액, 미지급금 모두 동일한 공식
- carryOver(이월) 흐름도 정상 동작

### SettlementSession (Firestore 감사 데이터)은 부정확

`creditAmount` 필드 미설정 + `pointsUsed=0` 하드코딩으로 인해:
- `totalCredit`이 실제보다 적게 기록됨 (외상 금액 누락)
- `totalPoints`가 Manager 생성 세션에서 0
- `totalFare ≠ totalCash + totalCard + totalCredit + totalPoints`

### 이월은 단일 금액 관리 (결제 수단별 분리 없음)

`carryOver.balance`는 결제 수단 구분 없이 누적됨. 외상 잔액은 Manager Room DB에서 별도 관리되나 Firestore에는 미저장.
