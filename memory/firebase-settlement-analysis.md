# Firebase 정산(Settlement) 시스템 상세 분석

분석일시: 2026-02-24
분석 범위: Cloud Functions + Firestore Rules + RTDB Rules
분석 대상 브랜치: firestore-migration-backup

---

## 1️⃣ Cloud Functions - 정산 관련 함수 전체 목록

### 1.1 정산 세션 관리 (handlers/settlement.ts)

#### 🔹 `addCallToSettlementSession()`
- **역할**: 콜 완료 시 정산 세션에 자동 추가
- **트리거**: `onCallCompletedUpdateSettlement` (index.ts line 4145)
- **동작**:
  - 완료된 콜의 `completedAt` 기반으로 근무일 계산 (새벽 6시 이전은 전날로 처리)
  - 정산 세션 경로: `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/settlementSessions/{workDate}`
  - Transaction 사용 (중복 콜 방지)
  - 새 세션 생성 시 depositRatio 기본값 60% 설정
  - 콜 정산 데이터 집계 (`recalculateTotals()` 호출)
- **입력 데이터**:
  ```typescript
  interface CallSettlement {
    callId: string;
    driverId: string;
    driverName: string;
    customerName: string;
    customerPhone: string;
    departure: string;
    destination: string;
    fare: number;
    paymentMethod: string;  // "현금", "이체", "카드", "현금+포인트"
    cashReceived: number;
    creditAmount: number;
    pointsUsed: number;
    completedAt: Timestamp;
    confirmedByOffice: boolean;
    syncedAt: Timestamp;
  }
  ```

#### 🔹 `recalculateTotals()`
- **역할**: 정산 집계 재계산
- **계산 로직**:
  - totalFare: 모든 콜 요금 합계
  - totalCash: "현금", "현금+포인트" 기준 요금 합계
  - totalCard: "이체", "카드" 기준 요금 합계
  - totalCredit: creditAmount 합계
  - totalPoints: pointsUsed 합계
  - **totalDeposit = floor(totalFare × depositRatio / 100)** (서버 계산)
  - **totalDriverShare = totalFare - totalDeposit** (서버 계산)
  - callCount: 콜 개수

#### 🔹 `getTodayWorkDate()`
- **역할**: 한국 시간 기준 오늘 근무일 계산
- **내보냄**: index.ts에서 사용 (finalizeSettlementAndNotifyDrivers)

#### 🔹 `autoFinalizeSettlementSessions()`
- **역할**: 매일 새벽 6시 10분 자동 실행되는 정산 마감
- **동작**:
  - 어제 근무일 기준 모든 사무실의 정산 세션 조회
  - `metadata.isFinalized = false`인 세션만 마감 처리
  - `metadata` 업데이트:
    - isFinalized: true
    - version 증가
    - lastUpdatedAt: 현재 시각
    - lastUpdatedBy: "auto_finalize"
- **스케줄**: `schedule: "10 6 * * *"`, timeZone: "Asia/Seoul"

#### 🔹 `checkSettlementDiscrepancies()`
- **역할**: 정산 데이터 불일치 검사
- **동작**:
  1. settlementSession 문서 조회
  2. 원본 calls 컬렉션에서 해당 날짜의 COMPLETED 콜 조회
  3. 근무일 필터링으로 실제 콜 수 계산
  4. 불일치 항목 확인:
     - 콜 수 불일치
     - 총 요금 불일치
     - 누락된 콜 확인
- **반환값**:
  ```typescript
  { hasDiscrepancy: boolean; details: string[] }
  ```

#### 🔹 `notifyDriversSettlementFinalized()`
- **역할**: 정산 마감 시 로그인 상태 기사에게 FCM 알림 전송
- **동작**:
  - 사무실의 `designated_drivers` 컬렉션에서 status ≠ "OFFLINE" 기사만 필터
  - FCM 토큰 확인 후 multicast 발송
  - payload:
    ```javascript
    {
      notification: {
        title: "업무 마감 안내",
        body: `오늘 업무가 마감되었습니다. 총 ${totals.callCount}건, ${totals.totalFare}원`
      },
      data: {
        type: "SETTLEMENT_FINALIZED",
        sessionDate,
        totalCount,
        totalFare,
        totalDeposit
      },
      android: { priority: "high" }
    }
    ```

#### 🔹 `notifySettlementDiscrepancy()`
- **역할**: 불일치 발견 시 관리자(Call Manager) FCM 알림
- **동작**:
  - `managerTokens` 컬렉션에서 관리자 FCM 토큰 조회
  - 불일치 상세 내역을 data에 포함해 전송

---

### 1.2 정산 Callable 함수 (index.ts)

#### 🔹 `onCallCompletedUpdateSettlement` (line 4145)
- **트리거**: `onDocumentUpdated` - call status → COMPLETED 감지
- **동작**: `addCallToSettlementSession()` 호출
- **문서 경로**: `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}`

#### 🔹 `autoFinalizeSettlements` (line 4182)
- **트리거**: 매일 새벽 6시 10분 scheduled
- **동작**: `autoFinalizeSettlementSessions()` 호출 및 로그 기록

#### 🔹 `checkSettlementDiscrepanciesScheduled` (line 4210)
- **트리거**: 매일 오전 7시 scheduled
- **동작**:
  - 어제 근무일 기준으로 모든 사무실의 불일치 검사
  - 불일치 발견 시 `notifySettlementDiscrepancy()` 호출

#### 🔹 `manualCheckSettlementDiscrepancy` (line 4274)
- **타입**: Callable Function (HTTP)
- **권한**: 인증된 사용자만 호출 가능
- **입력**: provinceId, cityId, officeId, sessionDate
- **반환**: `{ hasDiscrepancy, details }`

#### 🔹 `finalizeSettlementAndNotifyDrivers` (line 4440)
- **타입**: Callable Function (HTTP)
- **호출처**: Call Manager 앱에서 수동 마감 시 호출
- **동작**:
  1. settlementSessions 문서 조회
  2. 마감 처리 (재마감 허용):
     - `metadata.isFinalized = true`
     - version 증가
     - `metadata.lastUpdatedBy = "call_manager_finalize"`
  3. offices 문서 업데이트: `settlementLastCleared` 필드 설정 (기사앱 필터링용)
  4. `notifyDriversSettlementFinalized()` 호출
- **반환값**:
  ```typescript
  {
    success: boolean;
    sessionDate: string;
    sent: number;              // 알림 발송 성공 건수
    skipped: number;           // 건너뛴 기사 수
    totals: SettlementTotals;
    wasRefinalized: boolean;   // 재마감 여부
  }
  ```

---

### 1.3 기타 정산 관련 함수

#### 🔹 `finalizeWorkDay` (finalizeWorkDay.ts, 예전 방식)
- **타입**: Callable Function
- **동작** (현재는 사용 가능성 낮음):
  - `settlements` 컬렉션에서 `isFinalized == false`인 문서 조회
  - batch update로 `isFinalized = true` 설정
  - `dailySettlements/{today}/sessions` 컬렉션에 기록
- ⚠️ **주의**: `settlementSessions`(신규)와 `settlements`(구) 두 컬렉션 혼재 상태

---

## 2️⃣ 포인트 시스템 (handlers/points.ts)

### 2.1 사무실 포인트 (공유콜 수수료)

#### 🔹 `processSharedCallPoints()`
- **역할**: 공유콜 완료 시 포인트 분배 (10% 수수료)
- **동작** (Transaction):
  1. 중복 처리 방지: 이미 처리된 공유콜인지 확인
     - source office의 `point_transactions` where type="SHARED_CALL_RECEIVE"
     - target office의 `point_transactions` where type="SHARED_CALL_SEND"
  2. 포인트 잔액 업데이트:
     - sourceBalance += pointAmount (10% 계산)
     - targetBalance -= pointAmount
  3. 거래 내역 기록 (사무실별 서브컬렉션):
     - source: `point_transactions` 문서 생성, type="SHARED_CALL_RECEIVE"
     - target: `point_transactions` 문서 생성, type="SHARED_CALL_SEND"
- **컬렉션 경로**:
  ```
  provinces/{provinceId}/cities/{cityId}/offices/{officeId}/points/points
  provinces/{provinceId}/cities/{cityId}/offices/{officeId}/point_transactions/{docId}
  ```

#### 🔹 `getPointBalance()`
- **역할**: 사무실 포인트 잔액 조회
- **반환**: number (balance)

#### 🔹 `initializePoints()`
- **역할**: 포인트 초기화 (테스트용)

---

### 2.2 고객 포인트 (보상 적립)

#### 🔹 `processCustomerPointsOnComplete()`
- **역할**: 운행 완료 시 고객 포인트 적립
- **트리거**: `notifyCustomerOnComplete` (index.ts line 1541) → isAppCustomer인 경우만 호출
- **등급 및 적립률**:
  ```typescript
  BRONZE:  0 이상       → 3% 적립
  SILVER:  10회 이상    → 5% 적립
  GOLD:    30회 이상    → 7% 적립
  VIP:     50회 이상    → 9% 적립
  ```
- **동작** (Transaction):
  1. 중복 체크: 이 콜에 대한 포인트 이미 적립?
     - `customerPointTransactions` where phoneNumber, callId, type="EARN"
  2. 새 등급 계산:
     - newTotalCalls = totalCalls + 1
     - 등급 재결정
  3. 데이터 업데이트:
     - `customerPoints/{phoneNumber}` 문서 업데이트:
       - currentPoints += pointsEarned
       - totalEarned += pointsEarned
       - totalCalls += 1
       - grade 업데이트
     - `customerPointTransactions/{docId}` 생성:
       - type: "EARN"
       - 적립 내역 기록
     - 등급 업그레이드 시 추가 기록:
       - type: "GRADE_UPGRADE"
- **반환값**:
  ```typescript
  interface CustomerPointsResult {
    success: boolean;
    pointsEarned: number;
    newBalance: number;
    grade: string;
    gradeUpgraded: boolean;
    previousGrade?: string;
    error?: string;
  }
  ```

#### 🔹 `getCustomerPointBalance()`
- **역할**: 고객 포인트 잔액 조회
- **입력**: phoneNumber (정규화됨, 하이픈 제거)
- **반환**:
  ```typescript
  {
    balance: number;
    grade: string;
    totalCalls: number;
  }
  ```

---

## 3️⃣ Firestore 보안 규칙 (firestore.rules) - 정산/포인트 규칙

### 3.1 점수 시스템 관련 규칙

#### 사무실 포인트 (line 177-181)
```firestore
match /points/{pointsId} {
  allow read: if isOfficeAdmin(...) || isAuthenticated();
  allow write: if request.auth == null || isOfficeAdmin(...);  // Cloud Functions or Admin
}
```

#### 사무실 포인트 거래 내역 (line 184-189)
```firestore
match /point_transactions/{transactionId} {
  allow read: if isOfficeAdmin(...) || isAuthenticated();
  allow write: if request.auth == null || isOfficeAdmin(...);
}
```

#### 정산 세션 (line 325-328)
```firestore
match /settlementSessions/{sessionDate} {
  allow read: if isOfficeAdmin(...) || isAuthenticated();
  allow write: if request.auth == null || isOfficeAdmin(...);
}
```

#### 고객 포인트 (line 258-263)
```firestore
match /customerPoints/{phoneNumber} {
  allow read: if isAuthenticated() || isOfficeAdmin(...);
  allow write: if isAuthenticated() || isOfficeAdmin(...) || request.auth == null;
}
```

#### 고객 포인트 거래 (line 268-275)
```firestore
match /pointTransactions/{transactionId} {
  allow read: if isAuthenticated() || isOfficeAdmin(...);
  allow create: if isAuthenticated() || isOfficeAdmin(...) || request.auth == null;
  allow update, delete: if false;  // 이력 보존, 수정/삭제 불가
}
```

### 3.2 공유 콜 관련 규칙 (line 332-355)

```firestore
match /shared_calls/{sharedCallId} {
  allow read: if isAnyAdmin();
  allow create: if isAnyAdmin();
  allow update: if
    (isAnyAdmin() && resource.data.status == "OPEN" && request.resource.data.status == "CLAIMED") ||
    (request.auth == null);  // Cloud Functions 상태 변경
  allow delete: if request.auth == null;
}
```

---

## 4️⃣ 데이터 구조 전체 맵

### 정산 관련 컬렉션

```
provinces/{provinceId}/cities/{cityId}/offices/{officeId}/
├── settlementSessions/{workDate}
│   ├── metadata
│   │   ├── version: number
│   │   ├── lastUpdatedAt: Timestamp
│   │   ├── lastUpdatedBy: string ("cloud_function", "auto_finalize", "call_manager_finalize")
│   │   ├── depositRatio: number (기본 60)
│   │   ├── createdAt: Timestamp
│   │   └── isFinalized: boolean
│   ├── totals
│   │   ├── totalFare: number
│   │   ├── totalDeposit: number
│   │   ├── totalDriverShare: number
│   │   ├── totalCash: number
│   │   ├── totalCard: number
│   │   ├── totalCredit: number
│   │   ├── totalPoints: number
│   │   └── callCount: number
│   └── calls: CallSettlement[]
│
├── settlements/ (구 방식, 현재 혼재)
│   └── {docId}
│       └── isFinalized: boolean
│
└── dailySettlements/{dateId}  (예전 방식)
    └── sessions/{sessionId}
        ├── endAt: Timestamp
        ├── totalTrips: number
        └── totalFare: number
```

### 포인트 관련 컬렉션

```
provinces/{provinceId}/cities/{cityId}/offices/{officeId}/
├── points/points
│   ├── balance: number
│   └── updatedAt: Timestamp
│
├── point_transactions/{transactionId}
│   ├── type: "SHARED_CALL_RECEIVE" | "SHARED_CALL_SEND"
│   ├── amount: number (양수 또는 음수)
│   ├── description: string
│   ├── timestamp: Timestamp
│   ├── createdBy: string ("system")
│   └── relatedSharedCallId: string
│
├── customerPoints/{phoneNumber}
│   ├── phoneNumber: string
│   ├── customerName: string
│   ├── currentPoints: number
│   ├── totalEarned: number
│   ├── totalUsed: number
│   ├── totalCalls: number
│   ├── grade: "BRONZE" | "SILVER" | "GOLD" | "VIP"
│   ├── lastUpdated: Timestamp
│   └── createdAt: Timestamp
│
└── customerPointTransactions/{transactionId}
    ├── phoneNumber: string
    ├── customerName: string
    ├── type: "EARN" | "GRADE_UPGRADE"
    ├── amount: number
    ├── balance: number
    ├── description: string
    ├── callId: string
    ├── fare: number
    ├── grade: string
    ├── previousGrade?: string  (GRADE_UPGRADE 타입만)
    ├── newGrade?: string       (GRADE_UPGRADE 타입만)
    ├── totalCalls?: number     (GRADE_UPGRADE 타입만)
    ├── timestamp: Timestamp
    └── createdBy: string ("system")
```

---

## 5️⃣ 서버(Cloud Functions) vs 클라이언트 계산 분담

### 서버에서 계산하는 항목

| 항목 | 계산식 | 함수 | 적용 시점 |
|------|--------|------|----------|
| **수수료(depositRatio)** | floor(totalFare × 60 / 100) | `recalculateTotals()` | 콜 완료 시 정산 세션 업데이트 |
| **기사몫** | totalFare - totalDeposit | `recalculateTotals()` | 콜 완료 시 정산 세션 업데이트 |
| **고객 포인트 적립** | floor(fare × gradeRate) | `processCustomerPointsOnComplete()` | 운행 완료 후 |
| **고객 등급** | calculateGrade(totalCalls + 1) | `processCustomerPointsOnComplete()` | 포인트 적립 시 |
| **공유콜 포인트** | round(fare × 0.1) | `processSharedCallPoints()` | 공유콜 완료 시 |

### 클라이언트에서 입력하는 항목

| 항목 | 저장 필드 | 비고 |
|------|---------|------|
| 요금(최종) | `fare_set`, `finalFare`, `fare` | 중복 필드 (우선순위: fare_set > finalFare > fare) |
| 결제 방식 | `paymentMethod` | "현금", "카드", "이체", "현금+포인트" |
| 현금 수령 | `cashReceived` | paymentMethod가 "현금+포인트"일 때 사용 |
| 신용 금액 | `creditAmount` | 신용카드 등 외상 금액 |
| 포인트 사용 | `pointsUsed` | 고객이 사용한 포인트 |

### ⚠️ 문제점

1. **fare 필드 혼재**:
   - `fare_set` (기사 입력), `finalFare` (시스템 계산), `fare` (초기값)
   - 우선순위가 `handlers/settlement.ts`에서만 명시됨

2. **현금/카드 판정 로직의 단순화**:
   - paymentMethod 기반이지만, case 처리가 제한적
   - "현금+포인트"에서 fare는 총액인데, totalCash는 cashReceived만 계산

---

## 6️⃣ Firestore 정산 시스템의 흐름도

```
[Call Status → COMPLETED 변경]
           ↓
[onCallCompletedUpdateSettlement 트리거]
           ↓
[notifyCustomerOnComplete (고객 포인트 적립)]
           ↓
[addCallToSettlementSession (정산 세션에 콜 추가)]
           ├─ work date 계산 (새벽 6시 기준)
           ├─ 중복 체크 (Transaction)
           ├─ recalculateTotals() (집계 재계산)
           │  └─ totalDeposit, totalDriverShare 계산
           └─ settlementSessions/{workDate} 업데이트 또는 생성

[공유콜 완료 시: onSharedCallCompleted]
           ↓
[processSharedCallPoints 호출]
           ├─ source office: poins.balance += 10%
           ├─ target office: points.balance -= 10%
           └─ 거래 내역 기록 (point_transactions)

[매일 새벽 6시 10분: autoFinalizeSettlements]
           ├─ 어제 근무일 계산
           └─ autoFinalizeSettlementSessions() 호출
              └─ 모든 사무실의 isFinalized = true 설정

[매일 오전 7시: checkSettlementDiscrepanciesScheduled]
           ├─ 어제 근무일의 불일치 검사
           └─ 불일치 발견 시 관리자 FCM 알림

[수동 마감 (Call Manager에서):]
finalizeSettlementAndNotifyDrivers() 호출
           ├─ settlementSessions 마감 처리
           ├─ offices.settlementLastCleared 설정
           └─ 로그인 기사에게 FCM 알림
```

---

## 7️⃣ 발견된 잠재적 문제점

### 🔴 **심각한 문제**

1. **두 개의 정산 컬렉션 혼재**:
   - `settlements` (구 방식) vs `settlementSessions` (신 방식)
   - `finalizeWorkDay.ts`에서 구 방식 사용 중
   - Migration 미완료 상태

2. **depositRatio 하드코딩**:
   - `settlement.ts` line 154, 162: depositRatio = 60으로 하드코딩
   - 사무실별로 다를 수 있는데 미반영
   - 기사몫 계산이 서버에서 고정되어 있음

3. **근무일 계산의 시간대 호환성**:
   - `calculateWorkDate()`: 새벽 6시 기준 UTC 기반
   - 한국 시간대 처리가 완벽하지 않을 수 있음 (timezone issues)

### 🟡 **경고 수준 문제**

4. **fare 필드 혼재 및 우선순위 불명확**:
   - `callData.fareFinal` (정의되지 않은 필드?)
   - `callData.fare_set` (기사 입력값)
   - `callData.fare` (초기 설정값)
   - 우선순위: fare_set > finalFare > fare_set > fare

5. **포인트 거래 중복 방지의 취약성**:
   - `point_transactions` 쿼리로 중복 체크하는데, 쿼리 실패 시 중복 적립 가능성
   - Transaction 안에서 쿼리를 사용하므로 일관성은 있지만, 성능 이슈 가능

6. **고객 정보 정규화 불완전**:
   - `phoneNumber`는 정규화 (하이픈 제거)하지만, 초기화 시점이 명확하지 않음
   - 기존 데이터와 호환성 이슈 가능

7. **settled 데이터 구조 누락**:
   - SettlementSession의 calls 배열이 full 크기로 저장됨
   - 대량 콜이 있을 시 문서 크기 제한(1MB) 이슈 가능

### 🟢 **개선 권장사항**

8. **FCM 토큰 실패 처리**:
   - `notifyDriversSettlementFinalized()`에서 토큰 없으면 조용히 스킵
   - 실패한 기사 목록 기록 필요

9. **공유콜 포인트 불일치 가능성**:
   - 원본 office와 대상 office의 포인트 계산이 불일치할 수 있음
   - 별도의 감시 메커니즘 필요

10. **정산 세션 수정 불가**:
    - 한 번 생성된 정산 세션을 수정할 수 없음
    - 오류 발견 시 복구 메커니즘 필요

---

## 8️⃣ 명시적으로 클라이언트에서 처리하는 항목

### Call 문서에서

| 필드 | 생성처 | 용도 |
|------|--------|------|
| `status` | Call Detector / Call Manager | 상태 머신 관리 |
| `fare_set` | Driver App 또는 Call Manager | 최종 요금 설정 |
| `paymentMethod` | Driver App 또는 Call Manager | 결제 방식 |
| `cashReceived` | Driver App 또는 Call Manager | 현금 수령액 |
| `creditAmount` | Driver App 또는 Call Manager | 신용금액 |
| `pointsUsed` | Driver App 또는 Call Manager | 사용한 포인트 |
| `departure_set` | Driver App 또는 Call Manager | 최종 출발지 |
| `destination_set` | Driver App 또는 Call Manager | 최종 도착지 |

---

## 📋 결론

### 아키텍처 특징

1. **서버 주도 정산**: depositRatio, 기사몫 모두 서버에서 계산
2. **Real-time Sync**: Firestore Realtime Listener로 정산 세션 실시간 반영
3. **자동화**: 매일 새벽 6:10 자동 마감, 매일 오전 7시 불일치 검사
4. **거래 기록 보존**: 모든 포인트 거래는 삭제/수정 불가능

### 주의사항

- **Migration 미완료**: 구 `settlements` 컬렉션과 신 `settlementSessions` 혼재
- **Hardcoded Values**: depositRatio 60% 고정 (사무실별 차이 미지원)
- **Fare 필드 복잡성**: 3개 필드가 혼재, 우선순위 불명확
- **Timezone**: 한국 시간대 처리에 주의 필요
