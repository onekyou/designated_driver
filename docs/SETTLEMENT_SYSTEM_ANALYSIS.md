# 정산 시스템 전체 분석서

## 1. 시스템 개요

### 1.1 정산 데이터 흐름

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              정산 데이터 흐름도                                   │
└─────────────────────────────────────────────────────────────────────────────────┘

[기사앱]                    [Cloud Functions]                    [콜매니저]
   │                              │                                  │
   │ 1. 운행완료                  │                                  │
   │    (confirmAndFinalizeTrip)  │                                  │
   │                              │                                  │
   ├──▶ Firestore calls 업데이트 ─┼──▶ onCallCompletedUpdateSettlement
   │    - status: COMPLETED       │         │                        │
   │    - paymentMethod           │         │                        │
   │    - fareFinal               │         ▼                        │
   │    - cashReceived            │   addCallToSettlementSession     │
   │    - creditAmount            │         │                        │
   │    - pointsUsed              │         ▼                        │
   │                              │   settlementSessions/{date}      │
   │                              │         │                        │
   │ 2. 로컬 정산 저장            │         │                        │
   │    (saveToSettlementSession) │         │                        │
   │                              │         │                        │
   │                              │         │                        │
   │                              │         │    실시간 리스너        │
   │                              │         └───────────────────────▶│
   │                              │                                  │
   │                              │                    로컬 Room DB   │
   │                              │                    저장 및 UI 표시│
   │                              │                                  │
   │ 3. 일일 마감                 │                                  │
   │                              │◀────── finalizeSettlementSession │
   │                              │                                  │
   │◀──── FCM 마감 알림 ──────────│                                  │
   │      (SETTLEMENT_FINALIZED)  │                                  │
```

---

## 2. 결제 방법 (PaymentMethod)

### 2.1 지원 결제 방식

| 결제 방식 | 코드값 | 설명 | 현금수령 | 외상 |
|----------|--------|------|---------|------|
| 현금 | `"현금"` | 전액 현금 결제 | fare 전액 | 0 |
| 이체 | `"이체"` | 계좌이체 (사전 결제) | 0 | 0 |
| 카드 | `"카드"` | 카드 결제 | 0 | 0 |
| 외상 | `"외상"` | 전액 외상 (후불) | 0 | fare 전액 |
| 현금+포인트 | `"현금+포인트"` | 현금 일부 + 포인트/외상 | cashAmount | fare - cashAmount |
| 포인트 | `"포인트"` | 전액 포인트 결제 | 0 | 0 |

### 2.2 결제 방식별 필드 매핑

```typescript
// Cloud Functions (settlement.ts)
switch (call.paymentMethod) {
  case "현금":
    totalCash += call.fare;           // 총 현금에 전액 합산
    break;
  case "이체":
  case "카드":
    totalCard += call.fare;           // 총 카드/이체에 전액 합산
    break;
  case "현금+포인트":
    totalCash += call.cashReceived;   // 현금 수령액만 합산
    // creditAmount = fare - cashReceived (외상/포인트 금액)
    break;
}
```

### 2.3 기사앱에서 결제 처리

```kotlin
// DriverViewModel.kt - confirmAndFinalizeTrip()
val tripData = hashMapOf<String, Any>(
    "paymentMethod" to paymentMethod,
    "status" to "COMPLETED",
    "fareFinal" to fareToSet,
    "completedAt" to FieldValue.serverTimestamp(),
    "pointsUsed" to pointsToUse,
    "finalFare" to (fareToSet - pointsToUse)  // 최종 결제 금액
)

// 현금 또는 현금+포인트인 경우
if (paymentMethod == "현금" && cashAmount != null) {
    tripData["cashReceived"] = cashAmount
} else if (paymentMethod == "현금+포인트" && cashAmount != null) {
    tripData["cashReceived"] = cashAmount
    tripData["creditAmount"] = fareToSet - cashAmount  // 외상 금액
}
```

---

## 3. 수익 분배 방식

### 3.1 분배 비율 (depositRatio)

| 항목 | 계산식 | 기본값 |
|------|--------|--------|
| 사무실 몫 (납입금) | `totalFare × depositRatio / 100` | 60% |
| 기사 몫 | `totalFare - 납입금` | 40% |

### 3.2 분배 계산 예시

```
운행료: 15,000원
납입비율: 60%

사무실 몫 = 15,000 × 60% = 9,000원
기사 몫   = 15,000 - 9,000 = 6,000원
```

### 3.3 결제 방식별 정산 계산

#### 현금 결제 시
```
요금: 15,000원 (현금 수령)
───────────────────────────────
기사가 현금 15,000원 수령
  → 사무실에 9,000원 납입 (60%)
  → 기사 수익 6,000원 (40%)
```

#### 이체/카드 결제 시
```
요금: 15,000원 (사전 입금)
───────────────────────────────
사무실에 15,000원 입금됨
  → 기사에게 6,000원 지급 (40%)
  → 사무실 수익 9,000원 (60%)
```

#### 외상 결제 시
```
요금: 15,000원 (후불)
───────────────────────────────
기사 현금 수령: 0원
외상 금액: 15,000원
  → 기사 납입액: -9,000원 (마이너스)
  → 외상 회수 시 정산
```

#### 현금+포인트 결제 시
```
요금: 15,000원
현금 수령: 10,000원
포인트/외상: 5,000원
───────────────────────────────
기사 현금 10,000원 수령
  → 사무실 납입 9,000원 (60%)
  → 기사 실수익 1,000원
외상 5,000원은 별도 관리
```

---

## 4. 기사 입금액이 마이너스인 경우

### 4.1 마이너스 발생 조건

```kotlin
// SettlementModels.kt
actualDeposit = depositAmount - creditAmount
```

**마이너스 발생 시나리오:**
1. 외상 결제가 많은 경우
2. 이체/카드 결제만 있는 경우 (현금 수령 없음)
3. 포인트 전액 결제

### 4.2 마이너스 입금액 처리

```
예시: 기사 A의 일일 정산
───────────────────────────────────────
운행 1: 15,000원 (외상)
운행 2: 20,000원 (이체)
운행 3: 10,000원 (현금)
───────────────────────────────────────
총 운행료: 45,000원
총 납입액: 27,000원 (60%)
총 외상:   15,000원
───────────────────────────────────────
기사 현금 수령: 10,000원
실 납입액 = 27,000 - 15,000 = 12,000원

결과: 기사가 2,000원 추가 납입 필요
     (수령 10,000원 - 실납입 12,000원 = -2,000원)
```

### 4.3 시스템 대응

현재 시스템에서는 마이너스 입금액에 대한 별도 경고나 처리 로직이 없습니다.

**권장 개선사항:**
1. 마이너스 발생 시 콜매니저에 경고 표시
2. 기사앱에서 마이너스 상태 알림
3. 외상 누적 한도 설정

---

## 5. 일일 결산 로직

### 5.1 결산 시점

| 구분 | 시점 | 트리거 |
|------|------|--------|
| 자동 마감 | 매일 새벽 6:10 | Cloud Functions 스케줄러 |
| 수동 마감 | 관리자 버튼 클릭 | finalizeSettlementAndNotifyDrivers |

### 5.2 근무일 계산

```typescript
// settlement.ts
function calculateWorkDate(timestamp: Date): string {
  const date = new Date(timestamp);
  if (date.getHours() < 6) {
    // 새벽 6시 이전이면 전날 근무일로 처리
    date.setDate(date.getDate() - 1);
  }
  return date.toISOString().substring(0, 10); // YYYY-MM-DD
}
```

**예시:**
- 2026-02-02 오전 2:30 완료 → 2026-02-01 근무일
- 2026-02-02 오전 7:00 완료 → 2026-02-02 근무일

### 5.3 마감 처리 흐름

```
[콜매니저]                      [Cloud Functions]                    [기사앱]
    │                                 │                                 │
    │ 1. 마감 버튼 클릭               │                                 │
    │    finalizeSettlementSession()  │                                 │
    │                                 │                                 │
    ├───────────────────────────────▶│ finalizeSettlementAndNotifyDrivers
    │                                 │        │                        │
    │                                 │        ▼                        │
    │                                 │  세션 마감 처리                  │
    │                                 │  - isFinalized = true           │
    │                                 │  - version++                    │
    │                                 │        │                        │
    │                                 │        ▼                        │
    │                                 │  온라인 기사 조회               │
    │                                 │  (status != OFFLINE)            │
    │                                 │        │                        │
    │                                 │        ▼                        │
    │                                 │  FCM 알림 전송 ───────────────▶│
    │                                 │  (SETTLEMENT_FINALIZED)         │
    │                                 │                                 │
    │◀─────── 결과 반환 ─────────────│                                 │
    │  - sent: 알림 전송 수           │                                 │
    │  - skipped: 스킵 수             │                                 │
```

### 5.4 자동 마감 스케줄러

```typescript
// index.ts
export const autoFinalizeSettlements = onSchedule({
  schedule: "10 6 * * *",  // 매일 새벽 6시 10분
  timeZone: "Asia/Seoul",
  region: "asia-northeast3"
}, async () => {
  // 전날 근무일의 모든 세션 마감 처리
  await autoFinalizeSettlementSessions();
});
```

---

## 6. 외상 처리 시스템

### 6.1 외상 데이터 구조

```kotlin
// CreditPersonEntity (Room DB)
data class CreditPersonEntity(
    val id: String,           // 고객 ID
    val name: String,         // 고객 이름
    val phone: String,        // 전화번호
    val memo: String,         // 메모
    val totalAmount: Int      // 총 외상 금액
)

// CreditEntryEntity (외상 내역)
data class CreditEntryEntity(
    val personId: String,     // 고객 ID
    val date: String,         // 발생일
    val departure: String,    // 출발지
    val destination: String,  // 도착지
    val amount: Int           // 금액
)
```

### 6.2 외상 발생 흐름

```
[기사앱]                          [콜매니저]
    │                                 │
    │ 1. 운행완료 (결제: 외상)        │
    │    creditAmount = 15,000        │
    │                                 │
    ├─────▶ Firestore 저장 ─────────▶│
    │                                 │
    │                                 │ 2. 외상 감지
    │                                 │    if (creditAmount > 0)
    │                                 │
    │                                 │ 3. 외상 고객 등록
    │                                 │    addOrIncrementCredit()
    │                                 │
    │                                 │ 4. 외상 내역 저장
    │                                 │    CreditEntry 추가
```

### 6.3 외상 정산 처리

```kotlin
// SettlementViewModel.kt
fun reduceCredit(id: String, reduceAmount: Int) {
    viewModelScope.launch {
        // 외상 금액 차감
        creditDao.decrementCreditAmount(id, reduceAmount)

        // 외상이 0이 되면 고객 정보 삭제
        val person = creditDao.getAllCreditPersons().first().find { it.id == id }
        if (person?.totalAmount == 0) {
            creditDao.deleteCreditPersonById(id)
        }
    }
}
```

### 6.4 외상 관리 UI (콜매니저)

```
┌─────────────────────────────────────┐
│         외상 고객 관리               │
├─────────────────────────────────────┤
│ 홍길동 (010-1234-5678)              │
│   총 외상: 45,000원                 │
│   ├─ 01/15 강남역→삼성동 15,000원   │
│   ├─ 01/18 역삼역→논현동 20,000원   │
│   └─ 01/20 선릉역→대치동 10,000원   │
│                                     │
│   [정산하기] [메모 추가]            │
└─────────────────────────────────────┘
```

---

## 7. Firestore 데이터 구조

### 7.1 콜 데이터 (calls)

```
provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}
├── status: "COMPLETED"
├── customerName: "홍길동"
├── phoneNumber: "010-1234-5678"
├── assignedDriverId: "driver123"
├── assignedDriverName: "김기사"
├── departure_set: "강남역"
├── destination_set: "삼성동"
├── fareFinal: 15000
├── paymentMethod: "현금"
├── cashReceived: 15000
├── creditAmount: 0
├── pointsUsed: 0
├── completedAt: Timestamp
└── updatedAt: Timestamp
```

### 7.2 정산 세션 (settlementSessions)

```
provinces/{provinceId}/cities/{cityId}/offices/{officeId}/settlementSessions/{date}
├── metadata
│   ├── version: 5
│   ├── lastUpdatedAt: Timestamp
│   ├── lastUpdatedBy: "driver_app" | "call_manager" | "cloud_function"
│   ├── depositRatio: 60
│   ├── createdAt: Timestamp
│   └── isFinalized: false
├── totals
│   ├── totalFare: 450000
│   ├── totalDeposit: 270000
│   ├── totalDriverShare: 180000
│   ├── totalCash: 200000
│   ├── totalCard: 150000
│   ├── totalCredit: 100000
│   ├── totalPoints: 0
│   └── callCount: 30
└── calls: [
    {
        callId: "call123",
        driverId: "driver123",
        driverName: "김기사",
        customerName: "홍길동",
        customerPhone: "010-1234-5678",
        departure: "강남역",
        destination: "삼성동",
        fare: 15000,
        paymentMethod: "현금",
        cashReceived: 15000,
        creditAmount: 0,
        pointsUsed: 0,
        completedAt: Timestamp,
        confirmedByOffice: true,
        syncedAt: Timestamp
    },
    ...
]
```

---

## 8. 콜매니저-기사앱 연동 로직

### 8.1 데이터 동기화 방식

| 방향 | 방식 | 데이터 |
|------|------|--------|
| 기사앱 → 서버 | Firestore 직접 쓰기 | calls 업데이트, settlementSessions 저장 |
| 서버 → 콜매니저 | Firestore 리스너 | COMPLETED 콜 실시간 감지 |
| 서버 → 기사앱 | FCM 알림 | 마감 알림, 상태 변경 알림 |

### 8.2 동기화 충돌 방지

```typescript
// settlement.ts - 트랜잭션으로 원자적 처리
await db.runTransaction(async (transaction) => {
  const sessionDoc = await transaction.get(sessionRef);

  // 중복 체크
  const existingCallIndex = session.calls?.findIndex(c => c.callId === callId) ?? -1;
  if (existingCallIndex >= 0) {
    logger.info(`Call ${callId} already exists`);
    return;  // 중복 시 스킵
  }

  // 버전 증가
  transaction.update(sessionRef, {
    "metadata.version": (session.metadata?.version || 0) + 1,
    ...
  });
});
```

### 8.3 버전 기반 동기화

```kotlin
// SettlementViewModel.kt
fun checkAndSyncFromSharedSession() {
    sessionRef.get().addOnSuccessListener { doc ->
        val serverVersion = doc.getLong("metadata.version") ?: 0L
        val localVersion = _sharedSessionVersion.value

        if (serverVersion > localVersion) {
            // 새 버전이 있음 - 동기화 필요
            processSharedSession(session, sessionDate)
            _sharedSessionVersion.value = serverVersion
        }
    }
}
```

---

## 9. 잠재적 문제점 및 경쟁 조건

### 9.1 동시 업데이트 문제

**시나리오:**
1. 기사 A가 운행완료 처리
2. 동시에 기사 B도 운행완료 처리
3. 두 트랜잭션이 동시에 settlementSessions 업데이트

**현재 대응:**
- Firestore 트랜잭션 사용으로 원자적 처리
- 중복 체크 로직 (`existingCallIndex >= 0`)

**잠재적 위험:**
- 트랜잭션 재시도 시 지연 발생 가능
- 대량 동시 완료 시 성능 저하

### 9.2 오프라인 동기화 문제

**시나리오:**
1. 기사앱이 오프라인 상태에서 운행완료
2. 로컬에 저장 후 나중에 동기화 시도
3. 이미 콜매니저에서 수동 입력한 경우 충돌

**현재 대응:**
- `PendingSync` 데이터 클래스로 오프라인 데이터 관리
- callId 기반 중복 체크

**잠재적 위험:**
- 오프라인 기간이 길면 데이터 불일치 가능
- 동기화 실패 시 재시도 로직 필요

### 9.3 외상 금액 불일치

**시나리오:**
1. 기사앱에서 외상 15,000원 설정
2. 콜매니저에서 다른 금액으로 수정
3. 양쪽 데이터 불일치

**현재 대응:**
- calls 컬렉션의 creditAmount가 정본
- 콜매니저는 리스너로 실시간 반영

**잠재적 위험:**
- 콜매니저 로컬 DB와 Firestore 불일치 가능
- 외상 고객 목록이 부정확해질 수 있음

### 9.4 마감 타이밍 문제

**시나리오:**
1. 콜매니저에서 마감 버튼 클릭
2. 동시에 기사앱에서 운행완료 처리
3. 마감된 세션에 새 콜 추가 시도

**현재 대응:**
- `isFinalized` 플래그로 마감 여부 확인
- 마감된 세션에는 추가 불가 (Cloud Function에서 체크)

**잠재적 위험:**
- 마감 직전 완료된 콜이 누락될 수 있음
- 재마감 기능 필요

### 9.5 결제 방식별 집계 오류

**시나리오:**
```
"현금+포인트" 결제에서:
- fare: 15,000원
- cashReceived: 10,000원
- 기대: totalCash += 10,000
- 실제: Cloud Function 로직 확인 필요
```

**현재 코드 검증:**
```typescript
case "현금+포인트":
  totalCash += call.cashReceived;  // ✅ 정확함
  break;
```

---

## 10. 권장 개선사항

### 10.1 데이터 무결성

1. **정산 불일치 자동 감지**
   - 매일 오전 7시 스케줄러로 불일치 검사 (✅ 구현됨)
   - 불일치 발견 시 관리자에게 FCM 알림 (✅ 구현됨)

2. **오프라인 동기화 강화**
   - 재시도 로직 개선 (지수 백오프)
   - 동기화 실패 알림

### 10.2 사용자 경험

1. **마이너스 입금액 경고**
   - 콜매니저에 경고 배지 표시
   - 기사앱에 알림

2. **외상 한도 설정**
   - 고객별 외상 한도 설정
   - 한도 초과 시 경고

### 10.3 성능 최적화

1. **대량 데이터 처리**
   - 아카이브 로직 (✅ 구현됨 - 7일 이상 50개 초과 시)
   - 페이지네이션

2. **리스너 최적화**
   - 필요한 필드만 가져오기
   - 쿼리 인덱스 최적화

---

## 11. 테스트 시나리오

### 11.1 정상 케이스

| 시나리오 | 기대 결과 |
|---------|----------|
| 현금 결제 운행완료 | 콜매니저에 즉시 반영, 현금 집계 정확 |
| 이체 결제 운행완료 | 콜매니저에 즉시 반영, 카드/이체 집계 정확 |
| 외상 결제 운행완료 | 콜매니저에 외상 고객 자동 등록 |
| 일일 마감 | 기사에게 FCM 알림, 세션 마감 처리 |

### 11.2 경계 케이스

| 시나리오 | 기대 결과 |
|---------|----------|
| 새벽 5:59 운행완료 | 전날 근무일로 처리 |
| 새벽 6:01 운행완료 | 당일 근무일로 처리 |
| 동시 운행완료 2건 | 두 건 모두 정확히 기록 |
| 마감 직전 운행완료 | 마감 전 완료 건 포함 |

### 11.3 오류 케이스

| 시나리오 | 기대 결과 |
|---------|----------|
| 기사앱 오프라인 운행완료 | 로컬 저장, 온라인 시 동기화 |
| 네트워크 오류로 마감 실패 | 재시도 가능, 에러 메시지 표시 |
| 외상 금액 수정 | 양쪽 동기화, 정확한 금액 반영 |

---

## 12. 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-02-02 | 1.0 | 최초 작성 |
