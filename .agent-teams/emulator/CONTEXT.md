# Firebase Emulator 테스트 컨텍스트

## 프로젝트 개요

대리운전 통합 플랫폼. 4개 앱 + Cloud Functions.
- call_detector: 전화 수신 감지 → Firestore 콜 생성
- call_manager: 배차, 취소, 정산 관리
- driver_app: 수락, 운행, 정산 제출
- customer_app: 콜 요청, 취소
- functions: 42개 Cloud Functions (Firestore 트리거 + Callable + Scheduler)

## 환경

- Firebase CLI: 15.7.0
- Java: JDK 21 (`/c/Program Files/Android/Android Studio/jbr`)
- 프로젝트: calldetector-5d61e
- CF region: asia-northeast3
- Node engine: 22

---

## CF 트리거 맵 (핵심)

### Firestore 트리거 (자동 발동)

| 함수 | 트리거 | 경로 | 조건 |
|------|--------|------|------|
| oncallassigned | onDocumentWritten | calls/{callId} | assignedDriverId 변경 시 |
| sendNewCallNotification | onDocumentCreated | calls/{callId} | 새 콜 생성 |
| onCallStatusChanged | onDocumentUpdated | calls/{callId} | status 변경 |
| onCallCompletedUpdateSettlement | onDocumentUpdated | calls/{callId} | → COMPLETED |
| onCallCancelledByDriver | onDocumentUpdated | calls/{callId} | → CANCELLED_BY_DRIVER |
| notifyCustomerOnComplete | onDocumentUpdated | calls/{callId} | → COMPLETED |
| notifyCustomerOnPhoneCall | onDocumentCreated | calls/{callId} | fromCallDetector=true |
| onSharedCallCreated | onDocumentCreated | shared_calls/{callId} | 공유콜 생성 |
| notifyCustomerOnOfficeClosed | onDocumentCreated | shared_calls/{callId} | 마감 알림 |
| onSharedCallClaimed | onDocumentUpdated | shared_calls/{callId} | OPEN→CLAIMED |
| onSharedCallCancelledByDriver | onDocumentUpdated | shared_calls/{callId} | CLAIMED→취소 |
| onSharedCallStatusSync | onDocumentUpdated | shared_calls/{callId} | 상태 동기화 |
| onSharedCallCompleted | onDocumentUpdated | shared_calls/{callId} | → COMPLETED |
| onDriverStatusChange | onDocumentUpdated | designated_drivers/{driverId} | status 변경 |

### Callable (수동 호출)

| 함수 | 용도 |
|------|------|
| finalizeSettlementAndNotifyDrivers | 정산 마감 + 알림 |
| manualCheckSettlementDiscrepancy | 정산 불일치 검사 |
| acknowledgeNotification | ACK |
| notifyDriverAssignment | 배정 재알림 |
| notifyDriverCancellation | 취소 알림 |

### Scheduler (수동 트리거)

| 함수 | 스케줄 | 용도 |
|------|--------|------|
| autoFinalizeSettlements | 매일 06:00 | 어제 세션 자동 마감 |
| checkSettlementDiscrepanciesScheduled | 매일 06:30 | 불일치 검사 |
| checkAssignedTimeout | 매 1분 | ASSIGNED 60초 초과 재알림 |

---

## Firestore 데이터 구조

### 콜 (calls/{callId})

```
phoneNumber, customerName, customerAddress, status, timestamp,
provinceId, cityId, officeId, deviceName, callType,
isAppCustomer, customerId, fromCallDetector, createdFrom,
assignedDriverId, assignedDriverName, assignedDriverPhone,
assignedTimestamp, updatedAt,
fare, fare_set, fare_final, paymentMethod, cashReceived, creditAmount,
pointsUsed, finalFare, completedAt, tripSummary_final,
departure_set, destination_set, waypoints_set,
sourceSharedCallId, rejectedByDriver
```

### 콜 상태 전이

```
WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED
                                                                   → CANCELED (관리자)
                                                                   → CANCELLED_BY_DRIVER
         → CANCELLED_BY_CUSTOMER (고객, WAITING/ASSIGNED에서만)
ASSIGNED → WAITING (거절 시, HOLD 아님)
```

### 기사 (designated_drivers/{driverId})

```
name, phoneNumber, fcmToken, status, authUid,
dailySettlement: { date, finalDeposit, realDeposit, settlementDiff,
  totalFare, totalCredit, tripCount, status, submittedAt,
  calculatedCarryOver, originalCarryOver },
carryOver: { balance, status, lastUpdatedAt }
```

### 기사 상태: WAITING, ONLINE, ASSIGNED, ACCEPTED, PREPARING, ON_TRIP

### 정산 세션 (settlementSessions/{workDate})

```
metadata: { depositRatio, officeId, provinceId, cityId, workDate, isFinalized },
totals: { totalFare, totalDeposit, totalDriverShare, callCount, ... },
calls: [{ callId, fare, deposit, driverShare, paymentMethod, driverName, ... }]
```

### 공유콜 (shared_calls/{callId})

```
phoneNumber, sourceProvinceId, sourceCityId, sourceOfficeId,
targetProvinceId, targetCityId, status (OPEN/CLAIMED/COMPLETED),
callType (AFTER_HOURS/AFTER_HOURS_QUICK),
claimedOfficeId, claimedAt, claimedDriverId, claimedDriverAuthUid,
fare, departure, destination, timestamp
```

### 포인트

사무실 포인트: `points/points` → { balance }
사무실 포인트 거래: `point_transactions/{txId}`
- 멱등성 키: `shared_receive_{callId}`, `shared_send_{callId}`

고객 포인트: `customerPoints/{normalizedPhone}` → { currentPoints, totalEarned, totalUsed, totalCalls, grade }
고객 포인트 거래: `customerPointTransactions/{txId}`
- 멱등성 키: `earn_{phone}_{callId}`, `refund_{phone}_{callId}`
- 등급: BRONZE(0건, 3%), SILVER(10건, 5%), GOLD(30건, 7%), VIP(50건, 9%)

### 관리자 (admins/{adminId})

```
associatedProvinceId, associatedCityId, associatedOfficeId, role (ADMIN/HEAD_MANAGER/SUPER_ADMIN)
```

### 알림 (notifications/{notificationId})

```
type, targetId, targetType, callId, officeId, status (pending/delivered/failed),
sentAt, retryCount, fcmToken, payload
```

---

## 핵심 로직 참조

### 정산 (settlement.ts)
- `addCallToSettlementSession()`: 완료 콜 → 정산 세션 추가, depositRatio 동적 조회, 중복 방지
- `calculateWorkDate()`: UTC→KST, 새벽 6시 이전은 전날
- `recalculateTotals()`: 콜 배열로 총계 재계산
- `autoFinalizeSettlementSessions()`: 어제 세션 자동 마감

### 포인트 (points.ts)
- `processSharedCallPoints()`: 공유콜 포인트 ±10%, 멱등성 키
- `processCustomerPointsOnComplete()`: 등급별 적립 (3~9%)
- `refundCustomerPointsOnCancel()`: 취소 시 환불, 멱등성
