# Call Manager 정산(Settlement) 코드 정밀 분석

**분석일**: 2026-02-24
**프로젝트**: 대리운전 통합 플랫폼 - Call Manager
**분석 범위**: call_manager/ 폴더 전체

---

## 1. 관련 파일 목록 및 역할

### 데이터 모델
- **SettlementModels.kt**: 정산 데이터 모델 (SettlementSession, CallSettlement, DriverDailySettlement, DriverCarryOver 등)
- **SettlementData.kt**: 로컬 정산 데이터
- **SettlementBackup.kt**: Firebase RTDB 백업용 모델

### 로컬 저장소
- **SettlementEntity.kt**: Room DB 엔티티
- **SettlementDao.kt**: Room DB 쿼리
- **SettlementRepository.kt**: 로컬 저장소 레포지토리
- **SettlementCacheRepository.kt**: 캐시 레포지토리 (creditEntity 포함)
- **SettlementDatabase.kt**: Room DB 설정

### ViewModel
- **SettlementViewModel.kt**: 정산 비즈니스 로직 (약 1620줄)
  - 콜 정산 확인 (confirmSettlement)
  - 정산 세션 마감 (finalizeSettlementSession)
  - 기사별 일일 정산 확인 (confirmDailySettlement)
  - 이월 정산 처리 (transferCarryOver, cancelTransfer, processCarryOverOnFinalize)
  - 외상 관리 (credit management)

### UI 화면
- **SettlementTabHost.kt**: 정산 탭 호스트
- **AllTripsScreen.kt**: 전체 콜 조회 + 기사별 미지급금 현황
- **PendingSettlementsScreen.kt**: 이체/외상 처리 대기 콜
- **CreditManagementScreen.kt**: 외상 관리 (고객별)
- **DriverSummaryScreen.kt**: 기사별 정산 요약 + 미지급금 이체
- **DailySessionScreen.kt**: 일일 정산 세션 조회
- **TripDetailDialog.kt**: 콜 상세정보 다이얼로그
- **TripListTable.kt**: 콜 목록 테이블

### Cloud Functions (TypeScript)
- **settlement.ts**: 정산 관련 Cloud Functions
  - addCallToSettlementSession: 콜 추가
  - recalculateTotals: 집계 재계산
  - autoFinalizeSettlementSessions: 자동 마감
  - notifyDriversSettlementFinalized: 기사 알림
  - checkSettlementDiscrepancies: 불일치 검사

---

## 2. Firestore 문서 구조

### 2.1 정산 세션 문서
```
provinces/
  ├─ {provinceId}/
    ├─ cities/
      ├─ {cityId}/
        ├─ offices/
          ├─ {officeId}/
            ├─ settlementSessions/
              └─ {date}  (YYYY-MM-DD 형식, 근무일 기준)
                ├─ metadata
                │  ├─ version: Long                 # 버전 관리
                │  ├─ lastUpdatedAt: Timestamp      # 마지막 업데이트 시간
                │  ├─ lastUpdatedBy: String         # "call_manager" | "driver_app" | "cloud_function"
                │  ├─ depositRatio: Int             # 사무실 수수료 비율 (기본 60%)
                │  ├─ createdAt: Timestamp
                │  └─ isFinalized: Boolean          # 일일 마감 여부
                ├─ totals
                │  ├─ totalFare: Long               # 총 운행료
                │  ├─ totalDeposit: Long            # 총 납입액 (사무실 몫)
                │  ├─ totalDriverShare: Long        # 총 기사 몫
                │  ├─ totalCash: Long               # 현금 수령액
                │  ├─ totalCard: Long               # 카드/이체 수령액
                │  ├─ totalCredit: Long             # 총 외상액
                │  ├─ totalPoints: Long             # 포인트 사용액
                │  └─ callCount: Int                # 총 콜 수
                └─ calls: Array<CallSettlement>
                  ├─ callId: String
                  ├─ driverId: String
                  ├─ driverName: String
                  ├─ customerName: String
                  ├─ customerPhone: String
                  ├─ departure: String
                  ├─ destination: String
                  ├─ fare: Long
                  ├─ paymentMethod: String          # "현금" | "이체" | "카드" | "외상" | "현금+포인트" | "포인트"
                  ├─ cashReceived: Long             # 현금+포인트에서 현금 부분
                  ├─ creditAmount: Long             # 외상액 (미수금)
                  ├─ pointsUsed: Long               # 포인트 사용액
                  ├─ completedAt: Timestamp
                  ├─ confirmedByOffice: Boolean     # 사무실 확인 여부
                  └─ syncedAt: Timestamp
```

### 2.2 기사 정보 문서
```
provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId}
├─ dailySettlement (Map)
│  ├─ date: String                          # YYYY-MM-DD
│  ├─ finalDeposit: Long                    # 최종 납입액 (사무실 몫 - 외상)
│  ├─ realDeposit: Long                     # 실 납입액 (기사가 실제로 낸 금액)
│  ├─ settlementDiff: Long                  # 정산 차액
│  ├─ totalFare: Long                       # 총 운행료
│  ├─ totalCredit: Long                     # 총 외상
│  ├─ tripCount: Int
│  ├─ status: String                        # "WORKING" | "PENDING_CONFIRM" | "CONFIRMED"
│  ├─ submittedAt: Timestamp                # 기사 마감 시간
│  ├─ confirmedAt: Timestamp                # 매니저 확인 시간
│  ├─ confirmedBy: String                   # 확인한 매니저 ID
│  ├─ calculatedCarryOver: Long             # 업무마감 시 계산된 이월미환급금
│  └─ originalCarryOver: Long               # 원본 이월미환급금
├─ carryOver (Map)
│  ├─ balance: Long                         # 누적 미지급금 (양수=기사가 받아야 함)
│  ├─ status: String                        # "PENDING" | "TRANSFERRED" | "SETTLED"
│  ├─ lastUpdatedAt: Timestamp
│  ├─ transferredAt: Timestamp              # 이체 처리 시간
│  ├─ transferredBy: String                 # 이체한 매니저 ID
│  └─ todayAmount: Long                     # 오늘 발생한 미지급금
```

---

## 3. 결제 수단별 처리 로직

### 3.1 결제 수단 종류
1. **현금** (paymentMethod = "현금")
   - cashReceived = fare (전액 현금)
   - creditAmount = 0
   - 사무실 몫: fare × ratio / 100 (현금으로 회수)

2. **이체/카드** (paymentMethod = "이체" or "카드")
   - cashReceived = 0
   - creditAmount = fare (전액 외상)
   - 사무실 몫: fare × ratio / 100 (미수금)

3. **현금+포인트** (paymentMethod = "현금+포인트")
   - cashReceived = 현금 부분
   - creditAmount = fare - cashReceived (포인트 부분)
   - 사무실 몫: fare × ratio / 100 (일부 미수금)

4. **포인트만** (paymentMethod = "포인트")
   - cashReceived = 0
   - creditAmount = fare (전액 포인트 = 미수금 처리)

5. **외상** (paymentMethod = "외상")
   - CreditManagementScreen에서 따로 관리
   - creditAmount = 고객이 아직 내지 않은 금액

### 3.2 수수료 계산
```kotlin
// AllTripsScreen.kt 기준
val totalCredit = driverTrips.sumOf { trip ->
    when {
        trip.paymentMethod == "현금" -> 0
        trip.paymentMethod == "현금+포인트" -> {
            val cash = trip.cashAmount ?: 0
            if (cash > 0) trip.fare - cash else trip.fare
        }
        else -> trip.fare // 이체, 외상은 전액 외상
    }
}

val deposit = (fareSum * ratio / 100.0).toInt()      // 사무실 몫
val rawFinalDeposit = deposit - totalCredit           // 최종 수수료
```

### 3.3 미지급금 판단
```kotlin
// 오늘 미지급금 계산 (AllTripsScreen.kt)
if (rawFinalDeposit < 0) {
    // 음수면 미지급금 발생 (사무실이 기사에게 줘야 할 돈)
    todayUnpaid = -rawFinalDeposit
} else {
    todayUnpaid = 0
}

// 예: fare=100,000원, ratio=60%, 이체결제면
// deposit = 60,000 (사무실 몫)
// totalCredit = 100,000 (외상)
// rawFinalDeposit = 60,000 - 100,000 = -40,000
// todayUnpaid = 40,000 (사무실이 기사에게 줄 돈)
```

---

## 4. 기사 몫 및 수수료 계산

### 4.1 총액 계산
```kotlin
// SettlementTotals.toMap()에서 저장
totalFare = 모든 콜의 fare 합
totalDeposit = totalFare × depositRatio / 100        // 사무실 몫 (수수료)
totalDriverShare = totalFare - totalDeposit          // 기사 몫
totalCash = 현금 결제 콜들의 fare 합
totalCard = 이체/카드 결제 콜들의 fare 합
totalCredit = 모든 외상액 합
totalPoints = 포인트 사용액 합
```

### 4.2 기사별 정산 계산 (DriverSettlementStat)
```kotlin
totalFare = 기사의 모든 콜 fare 합
depositAmount = totalFare × depositRatio / 100       // 기사가 납입할 금액
creditAmount = 기사의 외상액 합
actualDeposit = depositAmount - creditAmount         // 실제 납입액
```

### 4.3 사무실 관점의 정산 (OfficeSettlementSummary)
```kotlin
totalSales = 총 매출
commissionIncome = 사무실 몫
driverShare = 기사 몫
expectedDeposit = 기사에게 받을 현금 금액
receivables = 미수금 (아직 못 받은 금액)
actualIncome = 수수료 수익 - 미수금 + 카드/이체 수령액
```

---

## 5. 이월(CarryOver) 로직

### 5.1 이월 정산 개요
- **목적**: 일일 정산 후 기사에게 줘야 할 돈이 있으면 이월로 누적
- **저장위치**: `designated_drivers/{driverId}` 문서의 carryOver 필드
- **상태 흐름**: PENDING → TRANSFERRED → SETTLED

### 5.2 이월 상태 정의
```kotlin
enum class CarryOverStatus {
    PENDING,       // 미지급 상태 (기사에게 줄 돈이 있음)
    TRANSFERRED,   // 이체 완료 (기사가 수령 확인 대기)
    SETTLED        // 정산 완료 (기사가 수령 확인함)
}
```

### 5.3 이월 저장 구조
```kotlin
data class DriverCarryOver(
    val balance: Long = 0,                    // 누적 미지급금
    val status: CarryOverStatus = PENDING,
    val lastUpdatedAt: Timestamp? = null,
    val transferredAt: Timestamp? = null,     // 이체 시간
    val transferredBy: String? = null,        // 이체한 매니저 ID
    val todayAmount: Long = 0                 // 오늘 발생한 미지급금
)
```

### 5.4 이월 업데이트 흐름

#### 단계 1: 마감 시 미지급금 계산
```kotlin
// AllTripsScreen.kt - todayUnpaidByDriver 계산
val todayUnpaid = if (rawFinalDeposit < 0) -rawFinalDeposit else 0
```

#### 단계 2: 이체 처리 (transferCarryOver)
```kotlin
// UI에서 "이체" 버튼 클릭
fun transferCarryOver(
    driverId: String,
    carryOverBalance: Long,      // 기존 이월분
    todayUnpaid: Long,           // 오늘분
    onResult: (Boolean, String) -> Unit
)

// 처리 내용:
val totalBalance = carryOverBalance + todayUnpaid

driverRef.set(
    mapOf(
        "carryOver" to mapOf(
            "balance" to totalBalance,
            "todayAmount" to todayUnpaid,
            "status" to "TRANSFERRED",
            "transferredAt" to Timestamp.now(),
            "transferredBy" to adminId,
            "lastUpdatedAt" to Timestamp.now()
        )
    ),
    SetOptions.merge()
)

// 로컬 즉시 업데이트 + FCM 알림
```

#### 단계 3: 기사 확인 후 (기사 앱에서)
- 기사가 수령확인 버튼 클릭
- 기사앱이 carryOver.status → SETTLED로 업데이트

#### 단계 4: 마감 시 재처리 (processCarryOverOnFinalize)
```kotlin
// 마감 중에 확인되지 않은 기사만 처리
if (dailySettlement.status != CONFIRMED) {
    // carryOver 재계산 및 저장
}
```

### 5.5 이월 리셋 시점
- **매일 새벽 6시 자동 마감** (autoFinalizeSettlementSessions)
  - metadata.isFinalized = true
  - 하지만 carryOver는 초기화 안 함 (누적 유지)

- **이월 조회 화면**에서 TRANSFERRED 상태만 표시

---

## 6. 정산 완료/최종 확인 흐름

### 6.1 개별 콜 확인 (confirmSettlement)
**목표**: 개별 콜의 정산 내역을 사무실에서 확인

```kotlin
fun confirmSettlement(callId: String, onResult: (Boolean, String) -> Unit)
// 트랜잭션으로 처리:
// 1. settlementSession/{date}/calls[] 배열에서 콜 찾기
// 2. confirmedByOffice = true로 업데이트
// 3. syncedAt = now() 설정
// 4. metadata.version 증가
```

**Firestore 업데이트**:
```
settlements/{sessionDate}/calls/{callIndex}
└─ confirmedByOffice: true
└─ syncedAt: Timestamp.now()
```

### 6.2 전체 세션 마감 (finalizeSettlementSession)
**목표**: 오늘의 모든 정산을 마감하고 기사에게 알림

```kotlin
fun finalizeSettlementSession(onResult: (Boolean, String) -> Unit)
// 1. 기존 세션 확인 (없으면 로컬 데이터로 생성)
// 2. Cloud Function 호출: finalizeSettlementAndNotifyDrivers
// 3. 기사 알림 전송 (로그인 상태만)
```

**Cloud Function 처리**:
```typescript
export async function autoFinalizeSettlementSessions()
// 1. 어제 근무일의 모든 사무실 찾기
// 2. 각 settlementSession 확인
// 3. metadata.isFinalized = true 업데이트
// 4. 버전 증가
```

**Firestore 업데이트**:
```
settlements/{date}/metadata
├─ isFinalized: true
├─ version: version + 1
├─ lastUpdatedAt: Timestamp.now()
└─ lastUpdatedBy: "cloud_function"
```

### 6.3 기사별 일일 정산 확인 (confirmDailySettlement)
**목표**: 기사가 업무마감한 정산을 사무실에서 최종 확인

```kotlin
fun confirmDailySettlement(
    driverId: String,
    settlementDiff: Long,    // 정산 차액 (기사앱에서 계산)
    onResult: (Boolean, String) -> Unit
)

// 트랜잭션으로 처리:
val newBalance = dailySettlementMap?.get("calculatedCarryOver") as? Long

transaction.update(driverRef, mapOf(
    "dailySettlement.status" to "CONFIRMED",
    "dailySettlement.confirmedAt" to Timestamp.now(),
    "dailySettlement.confirmedBy" to adminId,
    "carryOver.balance" to newBalance,
    "carryOver.status" to (if (newBalance > 0) "PENDING" else "SETTLED"),
    "carryOver.lastUpdatedAt" to Timestamp.now()
))
```

**Firestore 업데이트**:
```
designated_drivers/{driverId}
├─ dailySettlement.status: "CONFIRMED"
├─ dailySettlement.confirmedAt: Timestamp.now()
├─ dailySettlement.confirmedBy: adminId
├─ carryOver.balance: calculatedCarryOver (기사 계산값)
└─ carryOver.status: "PENDING" or "SETTLED"
```

### 6.4 정산 흐름 다이어그램
```
마감 전:
  ├─ 기사들이 각각 업무마감 (기사앱)
  │  └─ dailySettlement 생성
  └─ 매니저가 개별 콜 확인 가능

마감 시:
  ├─ finalizeSettlementSession() 호출
  ├─ metadata.isFinalized = true
  └─ 기사들에게 알림

마감 후:
  ├─ 기사별 일일 정산 확인 화면 표시
  ├─ confirmDailySettlement() 호출
  ├─ carryOver 업데이트
  └─ 이월 정산 화면에 표시
```

---

## 7. 코드에서 발견된 잠재적 문제점

### 7.1 이월 미지급금 중복계산 위험
**위치**: SettlementViewModel.processCarryOverOnFinalize() vs confirmDailySettlement()

**문제**:
- `processCarryOverOnFinalize()`는 마감 중에 호출되어 carryOver 계산
- `confirmDailySettlement()`도 나중에 호출되어 carryOver 업데이트
- 같은 기사에 대해 두 번 처리될 수 있음

**현재 보호 메커니즘**:
```kotlin
if (dailySettlementStatus == DailySettlementStatus.CONFIRMED.name) {
    return@runTransaction null  // 건너뜀
}
```
→ CONFIRMED 상태인 기사만 보호 (다른 케이스는 노출)

**권장사항**:
- 마감 완료 후 carryOver 계산은 한 번만 수행
- processCarryOverOnFinalize() 호출 전에 상태 검증

### 7.2 동기화 지연 가능성
**위치**: transferCarryOver() 함수

**문제**:
```kotlin
// 1. Firestore 업데이트
driverRef.set(carryOverData, SetOptions.merge())
    .addOnSuccessListener {
        // 2. 로컬 즉시 업데이트
        updateLocalCarryOver(...)

        // 3. FCM 알림
        sendCarryOverNotification(...)
    }
```

→ Firestore 리스너가 있으면 데이터 충돌 가능

**권장사항**:
- 로컬 업데이트 후 리스너 동기화 대기
- 또는 낙관적 업데이트 패턴 적용

### 7.3 외상(Credit) 처리의 불명확성
**위치**: PendingSettlementsScreen.kt, CreditManagementScreen.kt

**문제**:
```kotlin
// PendingSettlementsScreen - 결제방식 필터
val pending = trips.filter { it.paymentMethod in listOf("이체", "외상") }

// CreditManagementScreen - 외상 관리
// 그런데 "이체" 결제 콜도 외상 처리되는가?
```

→ paymentMethod = "이체"와 "외상"의 차이가 명확하지 않음
→ 둘 다 creditAmount로 저장되어 처리

**개선 필요**:
- "이체" vs "외상"의 의미 명확화
- 처리 흐름 통일

### 7.4 정산 차액 검증 부재
**위치**: confirmDailySettlement() 함수

**문제**:
```kotlin
// 기사앱에서 계산한 값을 그냥 사용
val newBalance = (dailySettlementMap?.get("calculatedCarryOver") as? Long) ?: 0L

// 사무실앱에서 검증하지 않음
transaction.update(driverRef, mapOf(
    "carryOver.balance" to newBalance,  // ← 검증 없음
))
```

→ 기사앱의 계산이 잘못되어도 그대로 반영

**권장사항**:
- 사무실앱에서도 calculatedCarryOver 검증
- 또는 양쪽 계산값이 일치하는지 확인 후 업데이트

### 7.5 포인트 처리의 복잡성
**위치**: AllTripsScreen.kt, SettlementModels.kt

**문제**:
```kotlin
// "현금+포인트"에서 포인트 부분 계산
trip.paymentMethod == "현금+포인트" -> {
    val cash = trip.cashAmount ?: 0
    if (cash > 0) trip.fare - cash else trip.fare  // ← trip.fare 전체?
}

// vs SettlementTotals.addCall()
"현금+포인트" -> call.cashReceived  // ← cashReceived만?
```

→ 포인트 부분 계산 로직이 일치하지 않을 수 있음

---

## 8. 핵심 함수 호출 관계

```
SettlementViewModel
├─ confirmSettlement()
│  └─ Firestore Transaction: calls[].confirmedByOffice = true
│
├─ finalizeSettlementSession()
│  ├─ createSettlementSessionFromLocalData()
│  └─ callFinalizeFunction()
│     └─ Cloud Function: finalizeSettlementAndNotifyDrivers
│
├─ confirmDailySettlement()
│  ├─ Firestore Transaction
│  ├─ dailySettlement.status = "CONFIRMED"
│  └─ carryOver.balance = calculatedCarryOver
│
├─ transferCarryOver()
│  ├─ Firestore Update: carryOver.status = "TRANSFERRED"
│  ├─ updateLocalCarryOver()
│  └─ sendCarryOverNotification()
│
└─ processCarryOverOnFinalize()
   ├─ Firestore Transaction (CONFIRMED 상태 제외)
   └─ carryOver 자동 계산 및 저장

Cloud Functions (settlement.ts)
├─ addCallToSettlementSession()
│  ├─ recalculateTotals()
│  └─ Transaction: calls[] + totals + metadata 업데이트
│
├─ autoFinalizeSettlementSessions()
│  └─ metadata.isFinalized = true
│
└─ notifyDriversSettlementFinalized()
   ├─ 로그인 상태 기사만 필터링
   └─ FCM 일괄 전송
```

---

## 9. 정산 데이터 저장 경로 정리

| 항목 | 저장 경로 | 필드 | 업데이트 주체 |
|------|---------|------|--------------|
| 정산 세션 | `settlementSessions/{date}` | metadata, totals, calls[] | Cloud Function / ViewModel |
| 개별 콜 확인 | `settlementSessions/{date}/calls[]/confirmedByOffice` | boolean | ViewModel |
| 기사 일일 정산 | `designated_drivers/{driverId}/dailySettlement` | status, confirmedAt 등 | 기사앱 / ViewModel |
| 기사 이월 정산 | `designated_drivers/{driverId}/carryOver` | balance, status, transferredAt 등 | ViewModel |
| 외상 관리 | 로컬 DB (CreditEntity) + UI 표시 | driverId, amount 등 | ViewModel + 사용자 입력 |

---

## 10. 결론 및 권장사항

### 강점
✅ Firestore 트랜잭션으로 원자성 보장
✅ 다층 검증 로직 존재 (discrepancy check 등)
✅ 이월 정산 상태 관리 명확함
✅ Cloud Function으로 복잡한 로직 서버에서 처리

### 개선 필요
⚠️ 미지급금 중복계산 위험 제거
⚠️ 정산 차액 검증 강화
⚠️ 포인트 처리 로직 통일
⚠️ 외상 vs 이체 정의 명확화
⚠️ 동기화 지연 처리 개선

