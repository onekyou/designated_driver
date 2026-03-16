# Driver App Settlement Code Analysis Report

## 📋 Executive Summary

The Driver App (driver_app) implements a **dual-layer settlement system**:

1. **Firestore Settlement Sessions** - Shared office-level settlement documents (Manager + Driver App synchronized)
2. **CallInfo-based Local Settlement** - Real-time calculation from completed calls on the device

The app handles settlements in two modes:
- **Individual Mode**: Each call completion → immediate settlement data update
- **Batch Mode**: Daily settlement submission (업무마감) with carry-over tracking

---

## 📁 Related Files and Their Roles

### Core Settlement Models & Data Layer
| File | Role | Key Classes |
|------|------|------------|
| `data/settlement/SettlementModels.kt` | **Settlement data structure** | `SettlementSession`, `SettlementTotals`, `CallSettlement`, `DriverDailySettlement`, `DriverCarryOver` |
| `data/repository/SettlementRepository.kt` | **Firestore ↔ Room DB sync** | Manages pending syncs, cache, Firestore transactions |
| `data/local/SettlementCacheEntity.kt` | **Local settlement cache** | Room entity for offline support |
| `data/local/SettlementDao.kt` | **Room DAO** | DB queries for pending syncs & cache |
| `data/local/PendingSyncEntity.kt` | **Offline pending data** | Stores unsync'd settlement data |
| `worker/SettlementSyncWorker.kt` | **Background sync worker** | Retries pending syncs when network available |

### UI & ViewModel
| File | Role |
|------|------|
| `ui/home/HistorySettlementScreen.kt` | Settlement history display, carry-over UI, daily settlement submission |
| `viewmodel/DriverViewModel.kt` | **Core settlement logic** - calculates settlement from calls, manages UI state |
| `ui/details/CallDetailsScreen.kt` | Call detail input (read-only in settlement context) |

### Data Models
| File | Key Models |
|------|-----------|
| `model/CallInfo.kt` | Call with settlement-related fields: `paymentMethod`, `cashReceived`, `creditAmount`, `settlementStatus` |
| `data/Constants.kt` | Field names: `FIELD_PAYMENT_METHOD`, `FIELD_CASH_RECEIVED`, `STATUS_AWAITING_SETTLEMENT` etc. |

---

## 🔄 Key Functions & Call Hierarchy

### ViewModel Settlement Functions
```
initializeListenersWithInfo()
├── startCarryOverListener()          [Line 1245] - Real-time carry-over monitoring
├── loadSettlementData()              [Line 1434] - Load ratio + trip data
│   └── loadTodaySettlement()         [Line 1473] - Calculate from COMPLETED calls
└── loadCurrentActiveCall()           [Line 233]  - Check for AWAITING_SETTLEMENT calls

confirmAndFinalizeTrip()              [Line 609]  - Mark call as COMPLETED + save settlement
├── processCustomerPoints()           [Line ~1150]- Handle customer points (app customers)
└── Updates todaySettlement state     - Real-time calculation

submitDailySettlement()               [Line 1328] - Daily settlement submission (업무마감)
├── Reads current carryOver           - Get current balance
└── Creates DriverDailySettlement     - Write to drivers/{driverId}/dailySettlement

confirmReceiveCarryOver()             [Line 1294] - Mark carry-over as SETTLED

refreshSettlementData()               [Line 1571] - Refresh after call completion
clearSettlement()                     [Line 1587] - Reset after daily settlement
```

### Repository Functions
```
saveCallSettlement()                  [Line 57]   - Save to local + sync to Firestore
├── Saves to PendingSyncEntity        - Local Room storage
└── syncCallToFirestore()             - Firestore transaction

syncCallToFirestore()                 [Line 112]  - Transactional update
├── Reads SettlementSession           - Get current shared doc
├── Adds new call + recalculates      - Add CallSettlement to calls list
└── Updates metadata/totals/calls     - Write entire session

checkAndSync()                        [Line 183]  - Version check & fetch latest
syncAllPending()                      [Line 263]  - Retry pending syncs
```

---

## 💾 Firestore Document Structure

### 1. Shared Settlement Session (Manager + Driver Share)
**Path**: `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/settlementSessions/{date}`

```kotlin
data class SettlementSession(
    metadata: SettlementMetadata = {          // 세션 메타데이터
        version: Long,                        // Firestore 버전 (동기화용)
        lastUpdatedAt: Timestamp,             // 마지막 업데이트 시간
        lastUpdatedBy: String,                // "driver_app" | "call_manager"
        depositRatio: Int = 60,               // 납입비율 (%)
        createdAt: Timestamp,
        isFinalized: Boolean                  // 일일 마감 여부
    },
    totals: SettlementTotals = {             // 집계 데이터
        totalFare: Long,                      // 총 운행료
        totalDeposit: Long,                   // 총 납입액 (사무실 몫)
        totalDriverShare: Long,               // 총 기사 몫
        totalCash: Long,                      // 총 현금
        totalCard: Long,                      // 총 카드/이체
        totalCredit: Long,                    // 총 외상 (creditAmount 합)
        totalPoints: Long,                    // 총 포인트 사용량
        callCount: Int                        // 총 콜 수
    },
    calls: List<CallSettlement> = [          // 개별 콜 정산 목록
        {
            callId: String,
            driverId: String,
            driverName: String,
            customerName: String,
            customerPhone: String,
            departure: String,
            destination: String,
            fare: Long,                       // 최종 요금
            paymentMethod: String,            // "현금" | "이체" | "외상" | "현금+포인트" | "포인트"
            cashReceived: Long,               // 현금 수령액 (현금+포인트일 때만)
            creditAmount: Long,               // 외상액 (외상이 있을 때만)
            pointsUsed: Long,                 // 사용한 포인트
            completedAt: Timestamp,
            confirmedByOffice: Boolean,       // 사무실 확인 여부
            syncedAt: Timestamp
        }
    ]
)
```

**Key Field Calculation**:
```kotlin
SettlementTotals.addCall():
- newTotalDeposit = totalFare * depositRatio / 100
- newTotalDriverShare = totalFare - newTotalDeposit
- cashAmount = "현금" ? fare : "현금+" ? cashReceived : 0
- cardAmount = "이체"|"카드" ? fare : 0
```

### 2. Driver Document - Daily Settlement (기사별 마감)
**Path**: `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}`

**Field**: `dailySettlement` (DriverDailySettlement)
```kotlin
data class DriverDailySettlement(
    date: String,                        // YYYY-MM-DD (6시 이전 = 전날)
    finalDeposit: Long,                  // 최종 납입액 = 사무실몫 - 외상
    realDeposit: Long,                   // 기사가 실제 낸 금액 (from UI input)
    settlementDiff: Long,                // 정산 차액 = 실납입 - 최종납입 + 원본carryOver
    totalFare: Long,                     // 총 운행료 (통합 시 이전값 포함)
    totalCredit: Long,                   // 총 외상
    tripCount: Int,                      // 운행 횟수
    status: DailySettlementStatus,       // WORKING | PENDING_CONFIRM | CONFIRMED
    submittedAt: Timestamp,              // 기사 제출 시간
    confirmedAt: Timestamp,              // 매니저 확인 시간
    confirmedBy: String,                 // 확인한 매니저 ID
    calculatedCarryOver: Long,           // **계산된 남은 미수령금** (실납입 - 최종납입 + 원본)
    originalCarryOver: Long              // **원본 carryOver** (통합/신규 결정)
)
```

### 3. Driver Document - Carry-Over (미수령금)
**Path**: `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}`

**Field**: `carryOver` (DriverCarryOver)
```kotlin
data class DriverCarryOver(
    balance: Long,                       // 누적 미수령금 (양수 = 내가 받아야 함)
    status: CarryOverStatus,             // PENDING | TRANSFERRED | SETTLED
    lastUpdatedAt: Timestamp,
    transferredAt: Timestamp,            // 사무실에서 이체한 시점
    transferredBy: String,               // 이체 처리한 매니저 ID
    todayAmount: Long                    // 오늘 발생한 미수령금
)
```

**Status Enum**:
```kotlin
enum class CarryOverStatus {
    PENDING,      // 미수령 상태
    TRANSFERRED,  // 이체됨 (수령 확인 대기)
    SETTLED       // 수령 완료
}
```

### 4. Call Document - Settlement-Related Fields
**Path**: `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}`

**Settlement Fields**:
```
status: String                          // "COMPLETED" when settlement finalized
paymentMethod: String                   // "현금" | "이체" | "외상" | "현금+포인트" | "포인트"
fareFinal: Long                         // ⭐ Final fare (포인트 제외)
fare: Long                              // ⭐ (added for customer app compatibility)
finalFare: Long                         // ⭐ Payable amount = fareFinal - pointsUsed
cashReceived: Long                      // ⭐ Cash amount received (if 현금 or 현금+포인트)
creditAmount: Long                      // ⭐ Credit/unsettled amount (외상)
pointsUsed: Long                        // ⭐ Points used
completedAt: Timestamp                  // Completion timestamp
tripSummaryFinal: String                // Trip details (departure→destination etc.)
settlementStatus: String                // "PENDING" | "SETTLED"
```

---

## 🔌 Payment Method Processing Flow

### In `SettlementTotals.addCall()`:
```kotlin
val cashAmount = when {
    call.paymentMethod == "현금" -> call.fare           // Full amount is cash
    call.paymentMethod.startsWith("현금+") -> call.cashReceived  // Cash portion
    else -> 0L                                           // Not cash
}

val cardAmount = when (call.paymentMethod) {
    "이체", "카드" -> call.fare                          // Full amount is card/transfer
    else -> 0L                                           // Not card
}

totalCash += cashAmount
totalCard += cardAmount
totalCredit += call.creditAmount                        // Add credit amount
totalPoints += call.pointsUsed                          // Add points used
```

### Payment Methods Supported:
| Method | Cash Field | Credit Field | Points | Note |
|--------|-----------|--------------|--------|------|
| "현금" | `fare` | 0 | 0 | Full cash |
| "이체" | 0 | `fare` | 0 | Transfer/card (counted as credit in settlement) |
| "외상" | 0 | `fare` | 0 | Credit only |
| "현금+포인트" | `cashReceived` | `creditAmount` or `fare - cashReceived` | `pointsUsed` | Mixed payment |
| "포인트" | 0 | 0 | `fare` | Points only |

---

## 📊 Settlement Calculation Flow

### 1. Call Completion → Settlement Finalization (`confirmAndFinalizeTrip`)

```
Input: callId, paymentMethod, cashAmount, fareToSet, tripSummaryToSet, pointsToUse

1. Get call info from Firestore
2. Process customer points (if app customer)
3. Update call document:
   {
     status: "COMPLETED",
     paymentMethod: paymentMethod,
     fareFinal: fareToSet,
     fare: fareToSet,          // For customer app
     finalFare: fareToSet - pointsUsed,
     pointsUsed: pointsUsed,
     cashReceived: cashAmount (if cash),
     creditAmount: fare - cashAmount (if 현금+포인트),
     tripSummaryFinal: tripSummaryToSet,
     completedAt: serverTimestamp()
   }
4. Set driver status to WAITING
5. (Cloud Function handles settlementSessions update)
6. Update local todaySettlement immediately:
   - Calculate: newCashReceived, newDriverShare
   - Update: totalFare, cashReceived, driverShare, tripCount
7. Add to tripHistoryList
```

### 2. Local Settlement Calculation (`loadTodaySettlement`)

```
Input: Ratio from offices, COMPLETED calls after settlementLastCleared

For each completed call:
  fare = fareFinal or fare_set (fallback)
  cashReceived = (paymentMethod == "현금") ? fare : (paymentMethod.startsWith("현금+")) ? cashReceived : 0

  totalFare += fare
  totalCashReceived += cashReceived
  tripCount++

Final calculations:
  officeDeposit = totalFare * ratio / 100              // 사무실 몫
  driverShare = totalFare * (100 - ratio) / 100       // 기사 몫
  realDeposit = totalCashReceived - driverShare        // 실 납부액
  totalCredit = totalFare - totalCashReceived          // 외상액
```

### 3. Firestore Settlement Session Sync (`syncCallToFirestore` - Repository)

```
Transaction:
1. Get current SettlementSession from Firestore
2. Check for duplicate calls (by callId)
3. Add new CallSettlement to calls list
4. Recalculate totals using SettlementTotals.addCall()
5. Increment metadata.version
6. Update metadata.lastUpdatedBy = "driver_app"
7. Write updated session back

If session doesn't exist:
- Create new SettlementSession with version=1
- Set depositRatio from offices or default 60%
```

---

## 🎯 Carry-Over (이월) Logic

### Initial Carry-Over (From Manager App)
- Manager app writes to `drivers/{driverId}/carryOver`
- Contains accumulated unsettled amounts

### Carry-Over in Daily Settlement (`submitDailySettlement`)

```
1. Read current carryOver.balance (or use originalCarryOver if PENDING_CONFIRM)
2. Calculate:
   - mergedFinalDeposit = officeDeposit - totalCredit
   - remainingCarryOver = originalCarryOver - mergedFinalDeposit + realDeposit

   Example:
   - originalCarryOver = 100,000 (previous session)
   - mergedFinalDeposit = 50,000 (office's cut this session)
   - realDeposit = 40,000 (what driver actually paid)
   - remainingCarryOver = 100,000 - 50,000 + 40,000 = 90,000

3. Write DriverDailySettlement:
   - calculatedCarryOver = remainingCarryOver
   - originalCarryOver = originalCarryOverBalance (preserve original)
   - status = PENDING_CONFIRM

4. Driver UI shows: "내 미수령금: calculatedCarryOver"
```

### Carry-Over Display Logic (`startCarryOverListener`)

```
1. Listen to drivers/{driverId} document
2. Read carryOver field
3. If dailySettlement.status == PENDING_CONFIRM:
   - Use dailySettlement.calculatedCarryOver (updated amount after session)
   - This reflects the new balance after current settlement
4. Else:
   - Use carryOver.balance (baseline amount)
5. Show if balance > 0 AND status != SETTLED
```

### Carry-Over Settlement Confirmation (`confirmReceiveCarryOver`)

```
Driver receives carry-over → marks as SETTLED:
  carryOver.balance = 0
  carryOver.status = SETTLED
  carryOver.transferredAt = null
  carryOver.transferredBy = null
  carryOver.lastUpdatedAt = now()
```

---

## 🔐 AWAITING_SETTLEMENT State Handling

**When**: After `completeRide()` is called (Sets status to AWAITING_SETTLEMENT)

**Flow**:
```
1. completeRide()
   └─ Update call status: AWAITING_SETTLEMENT

2. loadCurrentActiveCall() checks for AWAITING_SETTLEMENT calls
   └─ If found & not in handledSettlementIds → show popup

3. settlementPopup shows call details
   └─ User enters payment info

4. confirmAndFinalizeTrip()
   └─ Validates input
   └─ Updates call: status = COMPLETED + settlement fields
   └─ Removes from handledSettlementIds (popup dismissed)
```

**Handled Settlement IDs**: Stored in SharedPreferences to prevent duplicate popups:
```kotlin
handledSettlementIds: Set<String>  // Track which settlement popups were shown
```

---

## 🚀 Integration Points with Manager App

### Shared Data (Firestore)
1. **settlementSessions** - Both read/write
   - Driver: Writes new CallSettlement entries via Repository
   - Manager: Reads aggregated totals, confirms settlements

2. **dailySettlement** - Driver writes, Manager reads & confirms
   - Driver submits: status = PENDING_CONFIRM
   - Manager confirms: status = CONFIRMED, confirmedAt, confirmedBy

3. **carryOver** - Manager writes, Driver reads
   - Manager: Creates/updates balance after settlement
   - Driver: Displays balance, marks SETTLED on receipt

### Manager Confirmation Workflow
```
Driver submitDailySettlement()
└─ Writes: dailySettlement with PENDING_CONFIRM status

Manager App listens to dailySettlement status
└─ Reviews the numbers
└─ Calls Cloud Function to confirm
└─ Sets: status=CONFIRMED, confirmedAt, confirmedBy
└─ Updates: carryOver.balance for next session
```

---

## 🐛 Potential Issues & Edge Cases

### 1. Version Conflicts in Settlement Session
**Issue**: Multiple drivers writing to same settlementSessions document simultaneously
**Mitigation**: Using Firestore transactions (`runTransaction`) in `syncCallToFirestore()`
**Risk**: High contention office → potential transaction failures

### 2. Cash vs. Credit Mismatch
**Issue**: `cashReceived` field might not match `paymentMethod`
**Example**: paymentMethod="현금", cashReceived=null → calculation breaks
**Current Code**: Falls back to 0 but doesn't log mismatches

### 3. Double-counting in Integration Mode
**Issue**: When PENDING_CONFIRM → new trips added
**Current Logic**: Preserves previous settlement values, merges new trips
**Risk**: If same trip recorded twice across sessions

### 4. Carry-Over Calculation Race Condition
**Issue**: Driver reads carryOver, then Manager updates it, then Driver submits settlement
**Current**: Uses last-read value at submission time (could be stale)
**Better**: Manager should lock carry-over during driver settlement submission

### 5. No Validation of Payment Method
**Issue**: paymentMethod can be any string; misspellings cause 0 values
**Current**: Handled by `when` expressions defaulting to 0
**Better**: Enum validation at write time

### 6. Point Processing Isolated
**Issue**: processCustomerPoints() is async and independent
**Risk**: Call marked COMPLETED before point processing completes
**Current**: Logs error but continues (eventual consistency)
**Better**: Should be transactional with call update

### 7. Offline Sync Loss Scenario
**Issue**: PendingSync data might be stuck if device never reconnects
**Current**: SettlementSyncWorker retries up to 3 times with exponential backoff
**Limit**: No manual retry UI for users to force sync

### 8. Settlement Ratio Change Mid-Session
**Issue**: If office changes depositRatio during day, old calls use old ratio
**Current**: No versioning of ratio per call
**Mitigation**: Ratio read once at session start, applied to all calls

---

## 📝 Key Data Flows Summary

### Flow 1: Single Trip Settlement (Individual Mode)
```
Driver accepts call → completes route → inputs final fare/payment
  ↓
confirmAndFinalizeTrip()
  ├─ Process points (if app customer)
  ├─ Update call: status=COMPLETED + settlement fields
  ├─ Update local todaySettlement immediately
  ├─ Add to tripHistoryList
  └─ Cloud Function triggers → updates settlementSessions

UI shows updated settlement totals instantly
```

### Flow 2: Daily Settlement Submission (Batch Mode)
```
End of day: Driver clicks "업무마감"
  ↓
submitDailySettlement(realDeposit)
  ├─ Read current carryOver
  ├─ Read existing dailySettlement (check if PENDING_CONFIRM)
  ├─ If integrating: merge previous + current trips
  ├─ Calculate: finalDeposit, settlementDiff, calculatedCarryOver
  ├─ Write: dailySettlement with PENDING_CONFIRM status
  ├─ Update: settlementLastCleared timestamp
  └─ Clear local: todaySettlement, tripHistoryList

Manager app notified → reviews numbers → confirms or rejects
```

### Flow 3: Carry-Over Reception
```
Manager app transfers carry-over amount
  ↓
Manager writes: carryOver.status = TRANSFERRED

Driver sees notification: "미수령금 이체됨"
  ↓
Driver clicks "수령 완료"
  ↓
confirmReceiveCarryOver()
  ├─ Update: carryOver.balance=0, status=SETTLED
  └─ Clear UI notification
```

---

## 📚 Related Constants & Paths

### Firestore Paths
```
Shared Session: provinces/{id}/cities/{id}/offices/{id}/settlementSessions/{date}
Driver Status: provinces/{id}/cities/{id}/offices/{id}/designated_drivers/{id}
```

### Status Enums
```kotlin
CallStatus.AWAITING_SETTLEMENT = "AWAITING_SETTLEMENT"
CallStatus.COMPLETED = "COMPLETED"

DailySettlementStatus.WORKING, PENDING_CONFIRM, CONFIRMED

CarryOverStatus.PENDING, TRANSFERRED, SETTLED

SyncStatus.PENDING, SYNCING, SYNCED, FAILED
```

### Constants (from Constants.kt)
```kotlin
FIELD_STATUS = "status"
FIELD_PAYMENT_METHOD = "paymentMethod"
FIELD_CASH_RECEIVED = "cashReceived"
FIELD_FARE_FINAL = "fareFinal"
FIELD_COMPLETED_AT = "completedAt"
STATUS_AWAITING_SETTLEMENT = "AWAITING_SETTLEMENT"
```

---

## ✅ Verification Checklist

- [x] Settlement models match Manager app concepts
- [x] Firestore paths documented with full hierarchy
- [x] Payment method logic covers all cases (현금, 이체, 외상, 혼합, 포인트)
- [x] Carry-over calculation formula verified
- [x] AWAITING_SETTLEMENT handled with popup deduplication
- [x] Local/Remote sync strategy documented (Room + Firestore transactions)
- [x] Daily settlement submission flow (업무마감) mapped
- [x] Integration mode (PENDING_CONFIRM) logic clarified
- [x] Points processing isolated (async, eventual consistency)
- [x] Offline support via PendingSyncEntity + Worker
- [x] Version mismatch handling in settlementSessions

