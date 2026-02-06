# 앱 간 연계 흐름 아키텍처

> 이 문서는 손님앱, 콜매니저, 기사앱 간의 데이터 흐름과 상태 전이를 정리한 것입니다.
> Claude가 빠르게 시스템을 이해할 수 있도록 작성되었습니다.

---

## 1. 전체 시스템 구조

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   손님앱        │     │   콜매니저       │     │   기사앱        │
│ (customer_app) │     │ (call_manager)  │     │  (driver_app)   │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         │    Firestore Write    │    Firestore Read     │
         ├──────────────────────►│◄──────────────────────┤
         │                       │                       │
         │                       │    FCM (배차 알림)     │
         │                       ├──────────────────────►│
         │                       │                       │
         │    FCM (배정 알림)     │                       │
         │◄──────────────────────┤                       │
         │                       │                       │
         └───────────────────────┴───────────────────────┘
                                 │
                         ┌───────┴────────┐
                         │   Firestore    │
                         │  Cloud Func    │
                         │   RTDB         │
                         └────────────────┘
```

---

## 2. 콜 상태 흐름 (Call Status Flow)

```
WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED
   │          │          │           │                │
   │          │          │           │                └── 기사앱: 정산 입력
   │          │          │           └── 기사앱: 운행 완료 버튼
   │          │          └── 기사앱: 콜 수락 버튼
   │          └── 콜매니저: 배차 버튼
   └── 손님앱/콜디텍터: 콜 생성
```

### 상태별 설명

| 상태 | 의미 | 트리거 |
|------|------|--------|
| `WAITING` | 배차 대기 중 | 손님앱 콜 요청 / 콜디텍터 전화 감지 |
| `ASSIGNED` | 기사에게 배차됨 | 콜매니저 → 배차 버튼 |
| `ACCEPTED` | 기사가 수락함 | 기사앱 → 수락 버튼 |
| `IN_PROGRESS` | 운행 중 | 기사앱 → 운행 시작 버튼 |
| `AWAITING_SETTLEMENT` | 운행 완료, 정산 대기 | 기사앱 → 운행 완료 버튼 |
| `COMPLETED` | 정산 완료 | 기사앱 → 정산 입력 완료 |
| `CANCELED` | 취소됨 | 손님앱/콜매니저/기사앱 → 취소 |

---

## 3. 앱별 핵심 로직

### 3.1 손님앱 (customer_app)

**핵심 파일:**
- `service/CallService.kt` - 콜 생성/취소/조회
- `ui/main/MainViewModel.kt` - 콜 요청 UI 로직

**콜 요청 흐름:**
```kotlin
// CallService.kt:18-31
suspend fun requestCall(call: CustomerCall): String {
    val callsCollection = firestore
        .collection("provinces").document(provinceId)
        .collection("cities").document(cityId)
        .collection("offices").document(officeId)
        .collection("calls")

    val documentRef = callsCollection.document()
    val callWithId = call.copy(id = documentRef.id)
    documentRef.set(callWithId.toMap()).await()
    return documentRef.id
}
```

**알림 수신 (FCM):**
- `DRIVER_ASSIGNED` → 기사 배정 팝업 표시
- `RIDE_COMPLETED` → 운행 완료 팝업, 포인트 적립
- `CALL_CANCELLED` → 콜 상태 초기화

**특징:**
- Firestore 리스너 **사용 안 함** (FCM 기반)
- LocalBroadcastManager로 FCM → UI 전달

---

### 3.2 콜매니저 (call_manager)

**핵심 파일:**
- `ui/dashboard/DashboardViewModel.kt` - 배차 로직
- `ui/settlement/SettlementViewModel.kt` - 정산 관리
- `data/repository/CallRepository.kt` - 콜 데이터 관리

**배차 흐름:**
```kotlin
// DashboardViewModel.kt:666-766
fun assignCallToDriver(callInfo: CallInfo, driverId: String) {
    // 1. 중복 클릭 방지
    if (_isAssigning.value) return
    _isAssigning.value = true

    // 2. Firestore 콜 상태 업데이트
    val callUpdates = mapOf(
        "assignedDriverId" to driverAuthUid,
        "assignedDriverName" to driverInfo.name,
        "status" to "ASSIGNED",
        "assignedTimestamp" to Timestamp.now()
    )
    callRef.update(callUpdates).await()

    // 3. 로컬 Room DB 업데이트 (FCM 지연 대비)
    callRepository.updateAssignment(...)

    // 4. 기사 상태 업데이트
    driverRef.update("status", "ASSIGNED").await()

    // 5. Cloud Functions로 FCM 전송
    functions.getHttpsCallable("notifyDriverAssignment")
        .call(data)
}
```

**정산 확인 흐름:**
```kotlin
// SettlementViewModel.kt - confirmDailySettlement
// 1. 기사 dailySettlement 상태 → CONFIRMED
// 2. carryOver.balance 업데이트
// 3. 로컬 리스트 즉시 반영
```

**특징:**
- Room DB로 로컬 캐싱 (오프라인/지연 대비)
- Firestore 리스너 사용 (COMPLETED 콜 실시간 감지)
- `callsListener` + `callsListener2` (updatedAt/completedAt 모두 감지)

---

### 3.3 기사앱 (driver_app)

**핵심 파일:**
- `viewmodel/DriverViewModel.kt` - 모든 운행 로직
- `MyFirebaseMessagingService.kt` - FCM 수신
- `service/PresenceManager.kt` - 온라인 상태 관리

**콜 수락 흐름:**
```kotlin
// DriverViewModel.kt:366-396
fun acceptCall(callId: String) {
    // 1. 중복 클릭 방지
    if (_isAccepting.value) return
    _isAccepting.value = true

    // 2. 즉시 로컬 UI 업데이트 (리스너 기다리지 않음)
    _uiState.update { currentState ->
        currentState.copy(
            activeCall = call.copy(status = "ACCEPTED"),
            newCallPopup = null
        )
    }

    // 3. Firestore 업데이트 (백그라운드)
    callRef.update("status", "ACCEPTED").await()
}
```

**운행 완료 흐름:**
```kotlin
// DriverViewModel.kt:579-596
fun completeCall(callId: String) {
    // 1. 로컬 UI: AWAITING_SETTLEMENT로 변경
    _uiState.update {
        it.copy(
            activeCall = null,
            callForSettlement = completedCall
        )
    }

    // 2. Firestore 업데이트
    callRef.update("status", "AWAITING_SETTLEMENT").await()
}
```

**정산 입력 흐름:**
```kotlin
// DriverViewModel.kt:521-570
fun startDriving(callId, departure, destination, waypoints, fare) {
    // 콜에 운행 정보 저장 후 IN_PROGRESS로 변경
}

// 정산 완료 시: status → COMPLETED
```

**FCM 수신:**
```kotlin
// MyFirebaseMessagingService.kt:83-111
if (messageType == "call_assigned") {
    // 1. 도착 ACK 전송
    sendDeliveryAck(notificationId)

    // 2. ForegroundService에 콜 정보 전달
    DriverForegroundService.newCallAssignedIntent(...)

    // 3. 앱 상태에 따라 처리
    if (isAppInForeground()) {
        LocalBroadcastManager.sendBroadcast(...)  // 다이얼로그
    } else {
        showNotification(...)  // 시스템 알림
    }
}
```

**특징:**
- `isInForeground` → ProcessLifecycleOwner로 정확한 상태 관리
- 로컬 UI 먼저 업데이트 → Firestore 백그라운드 동기화
- PresenceManager로 RTDB 온라인 상태 관리

---

## 4. 데이터 동기화 방식

### 4.1 현재 아키텍처

| 앱 | 데이터 수신 방식 | 백업 |
|----|-----------------|------|
| 손님앱 | FCM 알림 | - |
| 콜매니저 | Firestore 리스너 + Room | FCM |
| 기사앱 | FCM 알림 | Firestore 폴링 (로그인 시) |

### 4.2 FCM 알림 종류

| type | 발신 | 수신 | 내용 |
|------|------|------|------|
| `call_assigned` | 콜매니저 → Cloud Func | 기사앱 | 배차 알림 |
| `DRIVER_ASSIGNED` | Cloud Func | 손님앱 | 기사 배정 알림 |
| `RIDE_COMPLETED` | Cloud Func | 손님앱 | 운행 완료 알림 |
| `SETTLEMENT_FINALIZED` | 콜매니저 | 기사앱 | 업무 마감 알림 |

---

## 5. Firestore 문서 구조

```
provinces/{provinceId}/
  cities/{cityId}/
    offices/{officeId}/
      calls/{callId}           # 콜 정보
      designated_drivers/{id}  # 기사 정보
      drivers/{authUid}        # 기사 앱 데이터 (dailySettlement, carryOver)
```

### 5.1 콜 문서 (calls/{callId})
```javascript
{
  id: string,
  phoneNumber: string,
  customerName: string,
  status: "WAITING" | "ASSIGNED" | "ACCEPTED" | "IN_PROGRESS" | "AWAITING_SETTLEMENT" | "COMPLETED",
  assignedDriverId: string,
  assignedDriverName: string,
  fare_set: number,           // 기사가 입력한 요금
  departure_set: string,      // 기사가 입력한 출발지
  destination_set: string,    // 기사가 입력한 목적지
  paymentMethod: string,      // "현금", "카드", "이체", "현금+이체"
  cashReceived: number,       // 현금 수령액
  completedAt: Timestamp,
  updatedAt: Timestamp
}
```

### 5.2 기사 정산 문서 (drivers/{authUid})
```javascript
{
  dailySettlement: {
    date: string,
    status: "PENDING_CONFIRM" | "CONFIRMED",
    tripCount: number,
    totalFare: number,
    totalCredit: number,
    realDeposit: number,
    calculatedCarryOver: number,  // 다음 세션 carryOver 기준값
    originalCarryOver: number     // 최초 세션의 carryOver (통합용)
  },
  carryOver: {
    balance: number,
    status: "REFUND" | "UNPAID" | "SETTLED"
  }
}
```

---

## 6. 최악의 시나리오 대비 및 현재 보완 시스템

### 6.1 FCM 실패 시 - ✅ 보완 완료

#### 기사앱 FCM 미도착 대응 (Cloud Functions 구현됨)

```
[배차 알림 전송 흐름]

콜매니저 배차
    │
    ▼
Cloud Functions: notifyDriverAssignment()
    │
    ├── FCM 전송
    └── Firestore: notifications/{id} 생성
            status: "pending"
            createdAt: timestamp
            retryCount: 0
    │
    ▼
기사앱 수신
    │
    ▼
Cloud Functions: acknowledgeNotification()
    │
    └── Firestore: notifications/{id}
            status: "delivered"  ← 변경

[자동 재전송 시스템]

retryPendingNotifications (매 1분 실행)
    │
    ├── 조건: status="pending" AND 10초 경과 AND retryCount < 2
    │
    └── 동작:
        1. FCM 재전송
        2. retryCount 증가
        3. 최대 2회 재시도

[실패 시 알림 (RTDB Presence 연동)]

handleFailedNotifications (매 1분 실행)
    │
    ├── 조건: status="pending" AND retryCount >= 2
    │
    ├── RTDB Presence 확인:
    │   └── presence/drivers/{driverId}/status
    │
    └── 동작:
        1. 콜매니저에 실패 알림 전송
        2. status: "failed" 변경
```

**핵심 파일:**
- `functions/src/index.ts:114-273` - ACK, 재전송, 실패 처리

| 상황 | 현재 대응 | 상태 |
|------|----------|------|
| 기사 FCM 미도착 | ACK 시스템 + 자동 재전송(2회) + 실패 알림 | ✅ 완료 |
| 손님 FCM 미도착 | 없음 | ⚠️ 추후 보완 필요 |

### 6.2 네트워크 불안정

| 상황 | 현재 대응 |
|------|----------|
| 콜매니저 오프라인 | Room DB 캐싱 |
| 기사앱 오프라인 | 로컬 UI 먼저 업데이트 |
| Firestore 쓰기 실패 | 스낵바 에러 메시지 |

### 6.3 앱 강제 종료

| 앱 | 대응 |
|----|------|
| 기사앱 | ForegroundService (START_STICKY) |
| 콜매니저 | CallManagerService (START_STICKY) |

### 6.4 RTDB 활용 현황

| 용도 | 경로 | 설명 |
|------|------|------|
| 기사 온라인 상태 | `presence/drivers/{uid}` | online/background/offline |
| 정산 백업 | `settlementBackup/...` | Firestore 장애 대비 |
| FCM 실패 판단 | Presence 참조 | 기사 오프라인 여부 확인 |

---

## 7. 주요 동기화 포인트

```
[손님앱 콜 요청]
    │
    ▼ Firestore Write
[Firestore: calls/{id} 생성, status=WAITING]
    │
    ▼ 콜매니저 리스너 감지
[콜매니저: 대기 콜 목록에 표시]
    │
    ▼ 배차 버튼 클릭
[Firestore: status=ASSIGNED + Cloud Func 호출]
    │
    ├──▶ [FCM → 기사앱: call_assigned]
    │         │
    │         ▼
    │    [기사앱: 팝업/알림 표시]
    │
    └──▶ [FCM → 손님앱: DRIVER_ASSIGNED]
              │
              ▼
         [손님앱: 기사 정보 표시]

[기사앱: 수락 → 운행시작 → 운행완료 → 정산입력]
    │
    ▼ Firestore Write
[Firestore: status=COMPLETED]
    │
    ├──▶ [콜매니저 리스너: 정산 목록에 반영]
    │
    └──▶ [FCM → 손님앱: RIDE_COMPLETED]
              │
              ▼
         [손님앱: 포인트 적립 팝업]
```

---

*마지막 업데이트: 2026-02-06*

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-02-06 | FCM 백업 시스템 상세 문서화 (ACK, 재전송, 실패 처리) |
| 2024-02 | 최초 작성 |
