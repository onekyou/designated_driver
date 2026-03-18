# Call Manager 정산 코드 상세 분석 보고서

**작성**: 2026-02-24
**분석자**: AI Code Analyst
**프로젝트**: 대리운전 통합 플랫폼 (Call Manager 앱)

---

## 📋 Executive Summary

콜매니저 정산 시스템은 다음과 같은 구조로 운영됩니다:

1. **정산 세션**: Firestore의 `settlementSessions/{date}` 문서에 일일 정산 데이터 저장
2. **결제 수단 처리**: 현금/이체/포인트 등 5가지 결제방식별로 다르게 계산
3. **미지급금(CarryOver)**: 사무실이 기사에게 줘야 할 돈을 매일 누적하여 관리
4. **정산 확인**: 개별 콜 확인 → 전체 마감 → 기사별 일일 정산 확인

---

## 🗂️ 파일 구조 및 역할

### 데이터 레이어
| 파일 | 역할 |
|------|------|
| `SettlementModels.kt` | Firestore 문서 구조 정의 (SettlementSession, CallSettlement, etc.) |
| `SettlementData.kt` | 로컬 정산 데이터 모델 |
| `SettlementEntity.kt` | Room DB 엔티티 (로컬 캐시) |
| `SettlementDao.kt` | Room DB 쿼리 인터페이스 |

### 비즈니스 로직
| 파일 | 주요 함수 |
|------|---------|
| `SettlementViewModel.kt` | confirmSettlement, finalizeSettlementSession, confirmDailySettlement, transferCarryOver, processCarryOverOnFinalize |
| `settlement.ts` (Cloud Function) | addCallToSettlementSession, recalculateTotals, autoFinalizeSettlementSessions, notifyDriversSettlementFinalized |

### UI/프레젠테이션
| 파일 | 용도 |
|------|------|
| `AllTripsScreen.kt` | 전체 콜 조회 + 기사별 미지급금 현황 |
| `PendingSettlementsScreen.kt` | 이체/외상 콜 처리 (0번 탭) |
| `CreditManagementScreen.kt` | 외상(고객) 관리 |
| `DriverSummaryScreen.kt` | 기사별 정산 현황 + 이체 기능 |

---

## 💾 Firestore 문서 구조

### 1. 정산 세션 문서
**경로**: `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/settlementSessions/{date}`

```json
{
  "metadata": {
    "version": 1,                          // 버전 관리 (변경시마다 증가)
    "lastUpdatedAt": "2026-02-24T...",     // 마지막 수정 시간
    "lastUpdatedBy": "call_manager",       // "call_manager" | "driver_app" | "cloud_function"
    "depositRatio": 60,                    // 사무실 수수료 비율 (%)
    "createdAt": "2026-02-24T...",
    "isFinalized": false                   // 일일 마감 완료 여부
  },
  "totals": {
    "totalFare": 500000,                   // 총 운행료
    "totalDeposit": 300000,                // 총 납입액 (사무실 몫: 60%)
    "totalDriverShare": 200000,            // 총 기사 몫 (40%)
    "totalCash": 200000,                   // 현금 수령액
    "totalCard": 100000,                   // 카드/이체 수령액
    "totalCredit": 200000,                 // 총 외상액
    "totalPoints": 0,                      // 총 포인트 사용액
    "callCount": 5
  },
  "calls": [
    {
      "callId": "CALL-2026-02-24-001",
      "driverId": "DRIVER-001",
      "driverName": "김기사",
      "customerName": "이고객",
      "customerPhone": "010-1234-5678",
      "departure": "서울역",
      "destination": "인천공항",
      "fare": 100000,
      "paymentMethod": "현금",             // 결제 수단
      "cashReceived": 100000,              // 현금 수령액
      "creditAmount": 0,                   // 외상액
      "pointsUsed": 0,                     // 포인트 사용액
      "completedAt": "2026-02-24T14:30:00Z",
      "confirmedByOffice": true,           // 사무실 확인 여부
      "syncedAt": "2026-02-24T14:35:00Z"
    }
  ]
}
```

### 2. 기사 일일 정산 문서
**경로**: `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}`

```json
{
  "dailySettlement": {
    "date": "2026-02-24",
    "finalDeposit": 300000,                // 최종 납입액 (사무실 몫 - 외상)
    "realDeposit": 250000,                 // 실 납입액 (기사가 실제로 낸 금액)
    "settlementDiff": -50000,              // 정산 차액 (음수면 사무실이 줄 금액)
    "totalFare": 500000,                   // 총 운행료
    "totalCredit": 200000,                 // 총 외상
    "tripCount": 5,
    "status": "CONFIRMED",                 // "WORKING" | "PENDING_CONFIRM" | "CONFIRMED"
    "submittedAt": "2026-02-24T22:00:00Z", // 기사 업무마감 시간
    "confirmedAt": "2026-02-24T22:05:00Z", // 매니저 확인 시간
    "confirmedBy": "MANAGER-001",          // 확인한 매니저
    "calculatedCarryOver": 50000,          // 이월 미환급금 (기사앱 계산)
    "originalCarryOver": 30000             // 원본 이월 미환급금
  },
  "carryOver": {
    "balance": 50000,                      // 누적 미지급금
    "status": "PENDING",                   // "PENDING" | "TRANSFERRED" | "SETTLED"
    "lastUpdatedAt": "2026-02-24T22:05:00Z",
    "transferredAt": null,                 // 이체 시간 (아직 미이체)
    "transferredBy": null,
    "todayAmount": 20000                   // 오늘 발생한 미지급금
  }
}
```

---

## 💰 결제 수단별 처리 로직

### 처리 방식 매트릭스

| 결제수단 | paymentMethod | cashReceived | creditAmount | 사무실 몫 처리 |
|---------|---------------|--------------|--------------|--------------|
| 현금 | "현금" | fare | 0 | 현금으로 회수 (fare × ratio) |
| 이체 | "이체" | 0 | fare | 외상 (fare × ratio) |
| 카드 | "카드" | 0 | fare | 외상 (fare × ratio) |
| 현금+포인트 | "현금+포인트" | 현금부분 | fare - cashReceived | 일부 외상 |
| 포인트 | "포인트" | 0 | fare | 외상 (fare × ratio) |
| 외상 | "외상" | 0 | 미수금액 | CreditManagementScreen에서 관리 |

### 코드 예시: 결제 수단별 처리

**SettlementModels.kt - recalculateTotals()**
```kotlin
fun recalculateTotals(calls: CallSettlement[], depositRatio: Int): SettlementTotals {
    var totalCash = 0L
    var totalCard = 0L
    var totalCredit = 0L

    for (call in calls) {
        when (call.paymentMethod) {
            "현금" -> totalCash += call.fare          // 전액 현금
            "이체", "카드" -> totalCard += call.fare  // 이체/카드는 외상 처리
            "현금+포인트" -> totalCash += call.cashReceived  // 현금부분만
            "포인트" -> {/* 별도 처리 */}
            "외상" -> {/* creditAmount 사용 */}
        }

        totalCredit += call.creditAmount  // 모든 외상 누적
    }

    val totalFare = calls.sumOf { it.fare }
    val totalDeposit = totalFare * depositRatio / 100
    val totalDriverShare = totalFare - totalDeposit

    return SettlementTotals(
        totalFare = totalFare,
        totalDeposit = totalDeposit,
        totalDriverShare = totalDriverShare,
        totalCash = totalCash,
        totalCard = totalCard,
        totalCredit = totalCredit,
        callCount = calls.size
    )
}
```

### 기사 미지급금 계산

**AllTripsScreen.kt**
```kotlin
// 기사별 오늘 미지급금 계산
val todayUnpaidByDriver = remember(trips, ratio) {
    trips.groupBy { it.driverId }
        .filter { it.key.isNotBlank() }
        .mapValues { (_, driverTrips) ->
            val fareSum = driverTrips.sumOf { it.fare }

            // 외상액 계산 (포인트 포함)
            val totalCredit = driverTrips.sumOf { trip ->
                when {
                    trip.paymentMethod == "현금" -> 0
                    trip.paymentMethod == "현금+포인트" -> {
                        val cash = trip.cashAmount ?: 0
                        if (cash > 0) trip.fare - cash else trip.fare
                    }
                    else -> trip.fare  // 이체, 외상, 포인트
                }
            }

            val deposit = (fareSum * ratio / 100.0).toInt()
            val rawFinalDeposit = deposit - totalCredit

            // 음수면 기사가 받을 돈 (미지급금)
            if (rawFinalDeposit < 0) -rawFinalDeposit else 0
        }
}
```

---

## 🔄 이월(CarryOver) 정산 상세 로직

### 이월 정산 흐름도

```
마감 전 상태:
  Driver에 carryOver.balance = 30,000원 (이전날 미지급금)

마감 시 계산:
  오늘 미지급금 = 20,000원
  총 미지급금 = 30,000 + 20,000 = 50,000원

매니저가 "이체" 클릭:
  carryOver.balance = 50,000
  carryOver.status = "TRANSFERRED"
  carryOver.transferredAt = now()
  carryOver.transferredBy = "MANAGER-001"
  FCM 알림 전송

기사가 "수령완료" 클릭 (기사앱):
  carryOver.status = "SETTLED"

다음날 마감:
  carryOver.balance 유지 (초기화 안 함)
  새로운 todayAmount만 추가
```

### transferCarryOver() 함수 상세

**SettlementViewModel.kt**
```kotlin
fun transferCarryOver(
    driverId: String,
    driverName: String,
    carryOverBalance: Long,  // 기존 누적 미지급금
    todayUnpaid: Long,       // 오늘 발생한 미지급금
    onResult: (Boolean, String) -> Unit
) {
    // 1. Firestore 경로 구성
    val driverRef = firestore
        .collection("provinces").document(currentProvinceId!!)
        .collection("cities").document(currentCityId!!)
        .collection("offices").document(currentOfficeId!!)
        .collection("designated_drivers").document(driverId)

    // 2. 총 미지급금 = 기존 이월분 + 오늘분
    val totalBalance = carryOverBalance + todayUnpaid

    // 3. 이체 데이터 구성
    val carryOverData = mapOf(
        "carryOver" to mapOf(
            "balance" to totalBalance,
            "todayAmount" to todayUnpaid,
            "status" to CarryOverStatus.TRANSFERRED.name,
            "transferredAt" to Timestamp.now(),
            "transferredBy" to adminId,
            "lastUpdatedAt" to Timestamp.now()
        )
    )

    // 4. Firestore 업데이트 (merge: 기존 필드 유지)
    viewModelScope.launch {
        driverRef.set(carryOverData, SetOptions.merge())
            .addOnSuccessListener {
                // 5. 로컬 UI 즉시 업데이트
                updateLocalCarryOver(
                    driverId, driverName, totalBalance,
                    todayUnpaid, CarryOverStatus.TRANSFERRED
                )

                // 6. FCM 알림 전송
                sendCarryOverNotification(
                    currentProvinceId!!, currentCityId!!, currentOfficeId!!,
                    driverId, driverName, totalBalance
                )

                onResult(true, "이체 완료")
            }
            .addOnFailureListener { e ->
                onResult(false, "이체 실패: ${e.message}")
            }
    }
}
```

### processCarryOverOnFinalize() - 마감 시 자동 처리

**SettlementViewModel.kt**
```kotlin
fun processCarryOverOnFinalize(driverId: String, todayResult: Long) {
    // todayResult: 양수 = 기사가 납부한 금액, 음수 = 사무실이 줄 금액

    firestore.runTransaction { transaction ->
        val doc = transaction.get(driverRef)

        // CONFIRMED 상태면 이미 처리된 기사이므로 건너뜀
        val dailySettlementStatus = doc.get("dailySettlement.status") as? String
        if (dailySettlementStatus == DailySettlementStatus.CONFIRMED.name) {
            return@runTransaction null
        }

        // 기존 이월분 조회
        val carryOverMap = doc.get("carryOver") as? Map<String, Any?>
        val currentBalance = (carryOverMap?.get("balance") as? Long) ?: 0L

        // 새 잔액 = 기존 이월분 - 오늘 납부 + 오늘 미지급
        val newBalance = currentBalance - todayResult

        // 상태 결정: 음수면 기사가 우리에게 줄 돈, 양수면 우리가 줄 돈
        val newStatus = if (newBalance > 0) {
            CarryOverStatus.PENDING.name
        } else {
            CarryOverStatus.SETTLED.name
        }

        // 업데이트
        transaction.update(driverRef, mapOf(
            "carryOver.balance" to newBalance,
            "carryOver.status" to newStatus,
            "carryOver.todayAmount" to todayResult,
            "carryOver.lastUpdatedAt" to Timestamp.now()
        ))
    }
}
```

---

## ✅ 정산 확인 흐름

### 1단계: 개별 콜 확인 (confirmSettlement)

**언제**: 마감 전, 매니저가 각 콜을 검토할 때
**코드**:
```kotlin
fun confirmSettlement(callId: String, onResult: (Boolean, String) -> Unit) {
    val sessionRef = firestore
        .collection("provinces").document(currentProvinceId!!)
        .collection("cities").document(currentCityId!!)
        .collection("offices").document(currentOfficeId!!)
        .collection("settlementSessions").document(getTodaySessionDate())

    firestore.runTransaction { transaction ->
        val doc = transaction.get(sessionRef)
        val session = SettlementSession.fromDocument(doc)!!

        // 콜 찾기 및 확인 표시
        val callIndex = session.calls.indexOfFirst { it.callId == callId }
        val updatedCalls = session.calls.toMutableList()
        updatedCalls[callIndex] = updatedCalls[callIndex].copy(
            confirmedByOffice = true,
            syncedAt = Timestamp.now()
        )

        // 메타데이터 업데이트
        val updatedMetadata = session.metadata.copy(
            version = session.metadata.version + 1,
            lastUpdatedAt = Timestamp.now(),
            lastUpdatedBy = "call_manager"
        )

        // Firestore 트랜잭션으로 원자적 업데이트
        transaction.update(sessionRef, mapOf(
            "calls" to updatedCalls.map { it.toMap() },
            "metadata" to updatedMetadata.toMap()
        ))
    }
        .addOnSuccessListener { onResult(true, "확인 완료") }
        .addOnFailureListener { e -> onResult(false, e.message ?: "실패") }
}
```

**Firestore 변경**:
```
Before:
  settlementSessions/{date}/calls[0] = {confirmedByOffice: false}

After:
  settlementSessions/{date}/calls[0] = {
    confirmedByOffice: true,
    syncedAt: Timestamp.now()
  }
  settlementSessions/{date}/metadata.version += 1
```

### 2단계: 전체 세션 마감 (finalizeSettlementSession)

**언제**: 마감 버튼 클릭할 때
**처리**:
```kotlin
fun finalizeSettlementSession(onResult: (Boolean, String) -> Unit) {
    // 1. 세션이 없으면 로컬 데이터로 생성
    val sessionRef = firestore.collection(...)
        .collection("settlementSessions").document(getTodaySessionDate())

    sessionRef.get().addOnSuccessListener { doc ->
        if (!doc.exists()) {
            // 로컬 data로부터 세션 생성
            val newSession = createSettlementSessionFromLocalData(getTodaySessionDate())
            sessionRef.set(newSession.toMap())
        }

        // 2. Cloud Function 호출
        val functions = Firebase.functions("asia-northeast3")
        functions.getHttpsCallable("finalizeSettlementAndNotifyDrivers")
            .call(mapOf(
                "provinceId" to currentProvinceId,
                "cityId" to currentCityId,
                "officeId" to currentOfficeId,
                "sessionDate" to getTodaySessionDate()
            ))
            .addOnSuccessListener { result ->
                val response = result.data as Map<*, *>
                val sent = response["sent"] as? Number
                onResult(true, "마감 완료! ${sent}명의 기사에게 알림 전송됨")
            }
    }
}
```

**Cloud Function 처리** (settlement.ts):
```typescript
export async function autoFinalizeSettlementSessions() {
    // 어제 근무일의 모든 사무실 조회
    const yesterday = calculateWorkDate(new Date());

    for (각 사무실) {
        const sessionRef = db.collection("...").doc(yesterday);
        const session = await sessionRef.get();

        if (session.exists && !session.data.metadata.isFinalized) {
            // 마감 처리
            await sessionRef.update({
                "metadata.isFinalized": true,
                "metadata.version": session.data.metadata.version + 1,
                "metadata.lastUpdatedAt": admin.firestore.Timestamp.now()
            });
        }
    }
}
```

### 3단계: 기사별 일일 정산 확인 (confirmDailySettlement)

**언제**: 마감 후, 각 기사의 정산을 매니저가 확인할 때
**코드**:
```kotlin
fun confirmDailySettlement(
    driverId: String,
    settlementDiff: Long,  // 기사앱에서 계산한 차액
    onResult: (Boolean, String) -> Unit
) {
    val driverRef = firestore
        .collection("designated_drivers").document(driverId)

    firestore.runTransaction { transaction ->
        val doc = transaction.get(driverRef)

        // 기사앱의 계산값 사용
        val dailySettlement = doc.get("dailySettlement") as Map<String, Any?>
        val newBalance = (dailySettlement["calculatedCarryOver"] as? Long) ?: 0L

        // 상태 결정
        val newCarryOverStatus = if (newBalance > 0) {
            CarryOverStatus.PENDING.name
        } else {
            CarryOverStatus.SETTLED.name
        }

        // 업데이트
        transaction.update(driverRef, mapOf(
            "dailySettlement.status" to DailySettlementStatus.CONFIRMED.name,
            "dailySettlement.confirmedAt" to Timestamp.now(),
            "dailySettlement.confirmedBy" to adminId,
            "carryOver.balance" to newBalance,
            "carryOver.status" to newCarryOverStatus,
            "carryOver.lastUpdatedAt" to Timestamp.now()
        ))
    }
        .addOnSuccessListener { onResult(true, "확인 완료") }
}
```

**Firestore 변경**:
```
Before:
  designated_drivers/{driverId} = {
    dailySettlement: {status: "PENDING_CONFIRM", ...},
    carryOver: {balance: 30000, status: "PENDING", ...}
  }

After:
  designated_drivers/{driverId} = {
    dailySettlement: {
      status: "CONFIRMED",
      confirmedAt: Timestamp.now(),
      confirmedBy: "MANAGER-001"
    },
    carryOver: {
      balance: 50000,  // calculatedCarryOver 값
      status: "PENDING",
      lastUpdatedAt: Timestamp.now()
    }
  }
```

---

## ⚠️ 잠재적 문제점 상세 분석

### 문제 1: 이월 미지급금 중복 계산

**발생 지점**:
- `processCarryOverOnFinalize()` - 마감 중 호출
- `confirmDailySettlement()` - 마감 후 호출

**코드 분석**:
```kotlin
// processCarryOverOnFinalize (마감 중)
val newBalance = currentBalance - todayResult

// confirmDailySettlement (마감 후)
val newBalance = dailySettlement.calculatedCarryOver
```

→ 같은 기사에 대해 두 가지 로직으로 계산 가능

**보호 메커니즘**:
```kotlin
if (dailySettlementStatus == DailySettlementStatus.CONFIRMED.name) {
    return@runTransaction null  // 이미 확인된 기사는 건너뜀
}
```

**현재 상태**: CONFIRMED 상태인 경우만 보호됨
→ 다른 상태의 기사는 노출 가능성 있음

**권장 해결책**:
```kotlin
// 마감 로직에서 미리 체크
if (dailySettlement.status == DailySettlementStatus.CONFIRMED) {
    return  // 마감 대상에서 제외
}

// 또는 순서 강제 (마감 후에만 confirmDailySettlement 허용)
if (!session.metadata.isFinalized) {
    throw Exception("마감 후에 기사별 정산 확인이 가능합니다")
}
```

### 문제 2: 동기화 지연

**상황**:
```kotlin
// transferCarryOver에서
driverRef.set(carryOverData, SetOptions.merge())
    .addOnSuccessListener {
        // 로컬 즉시 업데이트
        updateLocalCarryOver(...)

        // FCM 알림
        sendCarryOverNotification(...)
    }
```

→ Firestore 리스너와의 동기화 타이밍 차이 가능

**증상**:
- UI에서 로컬 값을 표시하다가 리스너에서 다른 값으로 변경
- 깜빡이는 듯한 UI 현상

**해결책**:
```kotlin
// 방법 1: 리스너가 업데이트 대기
viewModelScope.launch {
    val updated = carryOverListener가 새 값 감지할 때까지 대기
    // 실제 구현은 더 복잡함
}

// 방법 2: 낙관적 업데이트 + 확인
updateLocalCarryOver(...)  // 즉시 업데이트

// 방법 3: 별도 timestamp로 충돌 감지
if (localTimestamp > remoteTimestamp) {
    remoteData 무시
} else {
    remoteData 반영
}
```

### 문제 3: 정산 차액 검증 부재

**현재 코드**:
```kotlin
// confirmDailySettlement에서
val newBalance = (dailySettlement["calculatedCarryOver"] as? Long) ?: 0L

// 검증 없이 바로 업데이트
transaction.update(driverRef, mapOf(
    "carryOver.balance" to newBalance  // ← 검증 안 함
))
```

**문제점**:
- 기사앱의 계산이 잘못되어도 그대로 반영
- 사무실앱에서 재검증하지 않음

**개선 방안**:
```kotlin
// 사무실앱에서도 계산하여 비교
val officeCalculated = calculateCarryOver(driverId, sessionDate)

if (officeCalculated != dailySettlement.calculatedCarryOver) {
    // 불일치 처리
    logger.warn("CarryOver mismatch: office=$officeCalculated, driver=${dailySettlement.calculatedCarryOver}")

    // 사무실 계산값 사용하거나 관리자 승인 필요
    val finalBalance = officeCalculated  // 또는 officeCalculated

    transaction.update(driverRef, mapOf(
        "carryOver.balance" to finalBalance,
        "carryOver.discrepancy" to (dailySettlement.calculatedCarryOver - officeCalculated)
    ))
}
```

### 문제 4: 외상(Credit) 처리의 모호성

**코드 혼란**:
```kotlin
// PendingSettlementsScreen
val pending = trips.filter { it.paymentMethod in listOf("이체", "외상") }

// SettlementModels
when (call.paymentMethod) {
    "이체" -> totalCard += call.fare
    "외상" -> // 따로 처리?
}

// 실제로는 둘 다 creditAmount로 저장
```

**명확화 필요**:
- "이체": 계좌 이체 결제 (고객)
- "외상": 미수금 (고객이 아직 안 냄)
- 둘 다 creditAmount = fare이므로 "응수금" 취급

**권장**:
```kotlin
// 결제 수단과 회수 상태를 분리
data class CallSettlement(
    val paymentMethod: String,     // "현금" | "이체" | "포인트" | "현금+포인트"
    val collectionStatus: String,  // "RECEIVED" | "PENDING" | "WRITTEN_OFF"

    // 또는
    val paymentChannel: String,    // 실제 결제 수단
    val receivableStatus: String   // 수금 상태
)
```

---

## 📊 정산 데이터 흐름도

```
Call Complete (driver_app에서 완료)
    ↓
Cloud Function: addCallToSettlementSession
    ↓
settlementSessions/{date}/calls[] 추가
    ↓
Call Manager 앱에서 조회
    ↓
매니저가 개별 콜 확인 (confirmSettlement)
    ├─ settlementSessions/{date}/calls[]/confirmedByOffice = true
    └─ version 증가
    ↓
"마감" 버튼 클릭 (finalizeSettlementSession)
    ├─ Cloud Function 호출
    ├─ metadata.isFinalized = true
    └─ Driver app에게 알림 전송
    ↓
Driver app에서 업무마감 (calculateCarryOver 계산)
    ├─ dailySettlement 생성
    └─ calculatedCarryOver 저장
    ↓
Call Manager에서 기사별 정산 확인 (confirmDailySettlement)
    ├─ dailySettlement.status = "CONFIRMED"
    ├─ carryOver.balance = calculatedCarryOver
    └─ carryOver.status = PENDING/SETTLED
    ↓
필요시 "이체" 버튼 (transferCarryOver)
    ├─ carryOver.status = "TRANSFERRED"
    ├─ carryOver.transferredAt, transferredBy 기록
    └─ Driver app에게 FCM 알림
```

---

## 🔍 테스트 권장사항

1. **정산 정확성 검증**
   - 현금/이체/포인트 결제별 금액 계산 정확성
   - 외상 금액 누적 정확성
   - 기사 미지급금 계산 정확성

2. **동시성 테스트**
   - 마감 중 개별 콜 확인 시도
   - 기사별 정산 확인 중 이체 시도
   - 중복 클릭 시뮬레이션

3. **불일치 검사**
   - 사무실앱 계산값 vs 기사앱 계산값 비교
   - Firestore 문서 구조 검증
   - 트랜잭션 실패 시 복구 테스트

---

## 📝 결론

Call Manager의 정산 시스템은 **정교한 구조**이지만, 다음을 개선하면 더욱 견고해질 것입니다:

✅ **강점**
- Firestore 트랜잭션으로 원자성 보장
- 다층 검증 로직
- Cloud Function으로 복잡 로직 서버 처리

⚠️ **개선 사항**
- 중복 계산 제거
- 정산 검증 강화
- 동기화 지연 처리
- 결제수단 정의 명확화

