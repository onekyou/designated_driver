# Firestore 접근 패턴 (트랜잭션 6곳 + 리스너 1곳 + Repository)

> **의제 4 (CF apns 블록 + data 보존) + 의제 5 (`fcmToken` + `fcmTokenPlatform` 메타) + 의제 11 (`acceptanceEvents` 컬렉션) + 의제 12 (Firestore 자동 offline persistence, MVP)** 반영
> **MODELS.md Freezed + ENUMS.md sealed class + NOTIFIERS.md Notifier** 사용
> **원본 기준**: `driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt` + `data/repository/`
> **작성일**: 2026-04-16
> **작성자**: kotlin-expert

---

## 1. Firestore 경로 (`ios/SHARED_LOGIC.md` §1 참조)

### 1.1 기사앱이 직접 접근하는 경로

```
provinces/{provinceId}
  └─ cities/{cityId}
     └─ offices/{officeId}
        ├─ calls/{callId}                          [read/update — 콜 사이클]
        ├─ designated_drivers/{uid}                [read/update — 기사 본인]
        ├─ customerPoints/{phone}                  [read only — 정산 시]
        ├─ customerInfo/{phone}                    [read only — 손님 FCM 경로]
        ├─ settings/branding                       [read only — 슬로건]
        └─ settlementSessions/{YYYY-MM-DD}         [read only — CF가 생성]

pending_drivers/{uid}                              [create — 회원가입]
                                                   [read — 승인 대기 체크]
                                                   [delete — 관리자 승인 시 CF가 처리]

acceptanceEvents/{eventId}                         [create only — 의제 11 신규]
                                                   (CF가 주로 생성, 클라이언트 create 미권장)
```

### 1.2 Realtime Database (Presence 전용)

```
presence/drivers/{uid}                             [read/write — PresenceManager]
  ├─ status: "online" | "background" | "offline"
  └─ lastSeen: ServerValue.TIMESTAMP

.info/connected                                    [read only — 연결 상태]
```

### 1.3 경로 관용구 (Kotlin 원본과 100% 일치)

**Kotlin Constants.kt:5-26** 상수:
```kotlin
COLLECTION_PROVINCES = "provinces"
COLLECTION_CITIES = "cities"
COLLECTION_OFFICES = "offices"
COLLECTION_CALLS = "calls"
COLLECTION_DRIVERS = "designated_drivers"
COLLECTION_PENDING_DRIVERS = "pending_drivers"
FIELD_STATUS = "status"
FIELD_ASSIGNED_DRIVER_ID = "assignedDriverId"
FIELD_FCM_TOKEN = "fcmToken"
FIELD_UPDATED_AT = "updatedAt"
```

**Flutter 대응** (`lib/core/constants/firestore_paths.dart` 신규 권장):

```dart
// lib/core/constants/firestore_paths.dart

class FirestorePaths {
  FirestorePaths._();

  // Collections
  static const provinces = 'provinces';
  static const cities = 'cities';
  static const offices = 'offices';
  static const calls = 'calls';
  static const designatedDrivers = 'designated_drivers';
  static const pickupDrivers = 'pickup_drivers';
  static const pendingDrivers = 'pending_drivers';
  static const customerPoints = 'customerPoints';
  static const customerInfo = 'customerInfo';
  static const settlementSessions = 'settlementSessions';
  static const settings = 'settings';
  static const acceptanceEvents = 'acceptanceEvents';  // 의제 11 신규

  // Fields
  static const status = 'status';
  static const assignedDriverId = 'assignedDriverId';
  static const fcmToken = 'fcmToken';
  static const fcmTokenPlatform = 'fcmTokenPlatform';  // 의제 5 신규
  static const platform = 'platform';                  // 의제 11 신규
  static const updatedAt = 'updatedAt';
  static const authUid = 'authUid';
  static const approvalStatus = 'approvalStatus';
}
```

---

## 2. 트랜잭션 6곳 (Kotlin `runTransaction` 매핑)

### 2.1 acceptCall — 콜 수락

**Kotlin 원본**: `DriverViewModel.kt:427-452`

**사전 검증**:
- call 문서 존재 여부 (`tx.get(callRef).exists`)
- call.status == `ASSIGNED` (다른 상태면 예외)

**업데이트 필드** (**camelCase 100% 일치**):
- `calls/{callId}.status` → `'ACCEPTED'`
- `designated_drivers/{uid}.status` → `'PREPARING'`

**실패 예외**:
- `콜 문서를 찾을 수 없습니다.` (문서 없음)
- `CALL_NOT_ASSIGNABLE: current status is <X>` (상태 불일치)

**낙관적 UI + 롤백**: NOTIFIERS.md §2.3.1 참조

#### Kotlin 원본 코드

```kotlin
// DriverViewModel.kt:427-452
firestore.runTransaction { transaction ->
    val callSnapshot = transaction.get(callRef)
    if (!callSnapshot.exists()) throw Exception("콜 문서를 찾을 수 없습니다.")

    val currentStatus = callSnapshot.getString(Constants.FIELD_STATUS)
    if (currentStatus == Constants.STATUS_ASSIGNED) {
        transaction.update(callRef, Constants.FIELD_STATUS, Constants.STATUS_ACCEPTED)
        transaction.update(driverRef, Constants.FIELD_STATUS, DriverStatus.PREPARING.value)
    } else {
        throw IllegalStateException("CALL_NOT_ASSIGNABLE: current status is $currentStatus")
    }
}.await()
```

#### Flutter Dart 매핑

```dart
// CallRepository.acceptCall (NOTIFIERS.md §2.3.1 호출 대상)

Future<void> acceptCall({
  required String callId,
  required String driverId,
  required String provinceId,
  required String cityId,
  required String officeId,
}) async {
  final callRef = firestore
      .collection(FirestorePaths.provinces).doc(provinceId)
      .collection(FirestorePaths.cities).doc(cityId)
      .collection(FirestorePaths.offices).doc(officeId)
      .collection(FirestorePaths.calls).doc(callId);
  final driverRef = firestore
      .collection(FirestorePaths.provinces).doc(provinceId)
      .collection(FirestorePaths.cities).doc(cityId)
      .collection(FirestorePaths.offices).doc(officeId)
      .collection(FirestorePaths.designatedDrivers).doc(driverId);

  await firestore.runTransaction<void>((tx) async {
    final callSnap = await tx.get(callRef);
    if (!callSnap.exists) {
      throw StateError('콜 문서를 찾을 수 없습니다.');
    }

    final currentStatus = callSnap.data()?[FirestorePaths.status] as String?;
    if (currentStatus != 'ASSIGNED') {
      throw StateError('CALL_NOT_ASSIGNABLE: current status is $currentStatus');
    }

    tx.update(callRef, {FirestorePaths.status: 'ACCEPTED'});
    tx.update(driverRef, {FirestorePaths.status: 'PREPARING'});
  });
}
```

### 2.2 rejectCall — 콜 거절

**Kotlin 원본**: `DriverViewModel.kt:501-505` (트랜잭션 본체)

**사전 검증**: 없음 (Kotlin 원본도 조건 검사 없이 덮어쓰기)

**업데이트 필드**:
```
calls/{callId}:
  status: 'WAITING'
  assignedDriverId: null
  assignedDriverName: null
  assignedDriverPhone: null
  rejectedByDriver: <driverUid>
  updatedAt: serverTimestamp

designated_drivers/{uid}:
  status: 'WAITING'
```

#### Kotlin 원본 코드

```kotlin
// DriverViewModel.kt:492-505
val callUpdates = mapOf(
    Constants.FIELD_STATUS to Constants.STATUS_WAITING,
    "assignedDriverId" to null,
    "assignedDriverName" to null,
    "assignedDriverPhone" to null,
    "rejectedByDriver" to driverId,
    Constants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
)
firestore.runTransaction { transaction ->
    transaction.update(callRef, callUpdates)
    transaction.update(driverRef, Constants.FIELD_STATUS, DriverStatus.WAITING.value)
}.await()
```

#### Flutter Dart 매핑

```dart
Future<void> rejectCall({
  required String callId,
  required String driverId,
  required String provinceId,
  required String cityId,
  required String officeId,
}) async {
  final callRef = _callRef(provinceId, cityId, officeId, callId);
  final driverRef = _driverRef(provinceId, cityId, officeId, driverId);

  final callUpdates = <String, Object?>{
    FirestorePaths.status: 'WAITING',
    'assignedDriverId': null,
    'assignedDriverName': null,
    'assignedDriverPhone': null,
    'rejectedByDriver': driverId,
    FirestorePaths.updatedAt: FieldValue.serverTimestamp(),
  };

  await firestore.runTransaction<void>((tx) async {
    tx.update(callRef, callUpdates);
    tx.update(driverRef, {FirestorePaths.status: 'WAITING'});
  });
}
```

### 2.3 startDriving — 운행 시작

**Kotlin 원본**: `DriverViewModel.kt:632-635` (트랜잭션 본체) + `:617-625` (callUpdates)

**⚠️ 중요 — snake_case 필드명 Firestore 호환 보존**:
- `departure_set` / `destination_set` / `waypoints_set` / `fare_set` / `trip_summary`
- Kotlin 원본이 snake_case로 Firestore 저장 → **Flutter에서도 무단 camelCase 변환 금지**

**업데이트 필드**:
```
calls/{callId}:
  status: 'IN_PROGRESS'
  departure_set: <str>       ← snake_case!
  destination_set: <str>     ← snake_case!
  waypoints_set: <str>       ← snake_case!
  fare_set: <int>            ← snake_case!
  trip_summary: <str>        ← snake_case!
  updatedAt: serverTimestamp

designated_drivers/{uid}:
  status: 'ON_TRIP'
```

#### Kotlin 원본 코드

```kotlin
// DriverViewModel.kt:617-635
val callUpdates = mapOf(
    Constants.FIELD_STATUS to Constants.STATUS_IN_PROGRESS,
    "departure_set" to departure,
    "destination_set" to destination,
    "waypoints_set" to waypoints,
    "fare_set" to fare,
    "trip_summary" to tripSummary,
    Constants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
)
firestore.runTransaction { transaction ->
    transaction.update(callRef, callUpdates)
    transaction.update(driverRef, Constants.FIELD_STATUS, DriverStatus.ON_TRIP.value)
}.await()
```

#### Flutter Dart 매핑

```dart
Future<void> startDriving({
  required String callId,
  required String driverId,
  required String provinceId,
  required String cityId,
  required String officeId,
  required String departure,
  required String destination,
  required String waypoints,
  required int fare,
  required String tripSummary,
}) async {
  final callRef = _callRef(provinceId, cityId, officeId, callId);
  final driverRef = _driverRef(provinceId, cityId, officeId, driverId);

  // ⚠️ Firestore 필드명은 snake_case 유지 (Kotlin 원본 호환)
  final callUpdates = <String, Object?>{
    FirestorePaths.status: 'IN_PROGRESS',
    'departure_set': departure,
    'destination_set': destination,
    'waypoints_set': waypoints,
    'fare_set': fare,
    'trip_summary': tripSummary,
    FirestorePaths.updatedAt: FieldValue.serverTimestamp(),
  };

  await firestore.runTransaction<void>((tx) async {
    tx.update(callRef, callUpdates);
    tx.update(driverRef, {FirestorePaths.status: 'ON_TRIP'});
  });
}
```

### 2.4 cancelTrip — 운행 취소

**Kotlin 원본**: `DriverViewModel.kt:552-555` (트랜잭션 본체) + `:541-549` (callUpdates)

**CLAUDE.md 3/24 변경**: HOLD → `CANCELLED_BY_DRIVER` 확정 취소 (재배차 없음)

**업데이트 필드**:
```
calls/{callId}:
  status: 'CANCELLED_BY_DRIVER'
  assignedDriverId: null
  assignedDriverName: null
  assignedDriverPhone: null
  cancelReason: <str>
  cancelledByDriver: true
  updatedAt: serverTimestamp

designated_drivers/{uid}:
  status: 'WAITING'
```

#### Flutter Dart 매핑

```dart
Future<void> cancelTrip({
  required String callId,
  required String driverId,
  required String provinceId,
  required String cityId,
  required String officeId,
  required String cancelReason,
}) async {
  final callRef = _callRef(provinceId, cityId, officeId, callId);
  final driverRef = _driverRef(provinceId, cityId, officeId, driverId);

  final callUpdates = <String, Object?>{
    FirestorePaths.status: 'CANCELLED_BY_DRIVER',
    'assignedDriverId': null,
    'assignedDriverName': null,
    'assignedDriverPhone': null,
    'cancelReason': cancelReason,
    'cancelledByDriver': true,
    FirestorePaths.updatedAt: FieldValue.serverTimestamp(),
  };

  await firestore.runTransaction<void>((tx) async {
    tx.update(callRef, callUpdates);
    tx.update(driverRef, {FirestorePaths.status: 'WAITING'});
  });
}
```

### 2.5 confirmAndFinalizeTrip — 정산 확정

**Kotlin 원본**: `DriverViewModel.kt:729-732` (트랜잭션 본체) + `:698-716` (tripData 구성)

**사전 조회 (트랜잭션 밖)**:
- `calls/{callId}.isAppCustomer` / `phoneNumber` — 포인트 CF 적립 판정용

**업데이트 필드** (결제방식별 조건부 필드 포함):
```
calls/{callId}:
  paymentMethod: <str>                         ← '현금' | '이체' | '외상' | '포인트' | '현금+포인트'
  status: 'COMPLETED'
  fareFinal: <int>                             ← Kotlin FIELD_FARE_FINAL
  fare: <int>                                  ← 고객앱 호환성
  tripSummaryFinal: <str>                      ← Kotlin FIELD_TRIP_SUMMARY_FINAL
  completedAt: serverTimestamp                 ← Kotlin FIELD_COMPLETED_AT
  pointsUsed: <int>
  finalFare: <int>                             ← fareToSet - pointsToUse

  // 조건부 필드 (paymentMethod에 따라)
  cashReceived: <int>                          ← '현금' 또는 '현금+포인트' 일 때
  creditAmount: <int>                          ← '외상' | '이체' 은 전액, '현금+포인트' 는 max(0, fare - points - cash)

designated_drivers/{uid}:
  status: 'WAITING'
```

#### Kotlin 원본 코드

```kotlin
// DriverViewModel.kt:698-716
val tripData = hashMapOf<String, Any>(
    Constants.FIELD_PAYMENT_METHOD to paymentMethod,
    Constants.FIELD_STATUS to CallStatus.COMPLETED.firestoreValue,
    Constants.FIELD_FARE_FINAL to fareToSet,
    "fare" to fareToSet,
    Constants.FIELD_TRIP_SUMMARY_FINAL to tripSummaryToSet,
    Constants.FIELD_COMPLETED_AT to FieldValue.serverTimestamp(),
    "pointsUsed" to pointsToUse,
    "finalFare" to (fareToSet - pointsToUse)
)
if (paymentMethod == "현금" && cashAmount != null) {
    tripData[Constants.FIELD_CASH_RECEIVED] = cashAmount
} else if (paymentMethod == "현금+포인트" && cashAmount != null) {
    tripData[Constants.FIELD_CASH_RECEIVED] = cashAmount
    val actualCredit = maxOf(0, fareToSet - pointsToUse - cashAmount)
    tripData["creditAmount"] = actualCredit
} else if (paymentMethod == "외상" || paymentMethod == "이체") {
    tripData["creditAmount"] = fareToSet
}

firestore.runTransaction { transaction ->
    transaction.update(callRef, tripData)
    transaction.update(driverRef, Constants.FIELD_STATUS, DriverStatus.WAITING.value)
}.await()
```

#### Flutter Dart 매핑

```dart
Future<void> confirmAndFinalizeTrip({
  required String callId,
  required String driverId,
  required String provinceId,
  required String cityId,
  required String officeId,
  required String paymentMethod,  // '현금' | '이체' | '외상' | '포인트' | '현금+포인트'
  required int fareToSet,
  required String tripSummaryToSet,
  int? cashAmount,
  int pointsToUse = 0,
}) async {
  final callRef = _callRef(provinceId, cityId, officeId, callId);
  final driverRef = _driverRef(provinceId, cityId, officeId, driverId);

  // tripData 구성 (Kotlin line 698-716 등가)
  final tripData = <String, Object?>{
    'paymentMethod': paymentMethod,
    FirestorePaths.status: 'COMPLETED',
    'fareFinal': fareToSet,
    'fare': fareToSet,                       // 고객앱 호환
    'tripSummaryFinal': tripSummaryToSet,
    'completedAt': FieldValue.serverTimestamp(),
    'pointsUsed': pointsToUse,
    'finalFare': fareToSet - pointsToUse,
  };

  // 결제방식별 조건부 필드
  if (paymentMethod == '현금' && cashAmount != null) {
    tripData['cashReceived'] = cashAmount;
  } else if (paymentMethod == '현금+포인트' && cashAmount != null) {
    tripData['cashReceived'] = cashAmount;
    final actualCredit = math.max(0, fareToSet - pointsToUse - cashAmount);
    tripData['creditAmount'] = actualCredit;
  } else if (paymentMethod == '외상' || paymentMethod == '이체') {
    tripData['creditAmount'] = fareToSet;
  }

  await firestore.runTransaction<void>((tx) async {
    tx.update(callRef, tripData);
    tx.update(driverRef, {FirestorePaths.status: 'WAITING'});
  });
}
```

**주의**: 포인트 적립은 **CF `notifyCustomerOnComplete`** 에서 처리 (CLAUDE.md 3/24 BUG-D12). **Flutter/Kotlin 기사앱은 포인트 적립 트랜잭션 호출 금지**.

### 2.6 confirmReceiveCarryOver — 이월금 수령 확인

**Kotlin 원본**: `DriverViewModel.kt:1325-1333` (단일 update, 트랜잭션 아님)

> 본 섹션은 엄밀히는 **단일 update()** 이나 이월금 플로우 완결을 위해 트랜잭션 6곳과 함께 배치.

**업데이트 필드**:
```
designated_drivers/{uid}:
  carryOver.balance: 0
  carryOver.status: 'SETTLED'
  carryOver.transferredAt: null
  carryOver.transferredBy: null
  carryOver.lastUpdatedAt: Timestamp.now()
```

#### Kotlin 원본 코드

```kotlin
// DriverViewModel.kt:1325-1333
driverRef.update(
    mapOf(
        "carryOver.balance" to 0L,
        "carryOver.status" to CarryOverStatus.SETTLED.name,
        "carryOver.transferredAt" to null,
        "carryOver.transferredBy" to null,
        "carryOver.lastUpdatedAt" to Timestamp.now()
    )
).await()
```

#### Flutter Dart 매핑

```dart
Future<void> confirmReceiveCarryOver({
  required String driverId,
  required String provinceId,
  required String cityId,
  required String officeId,
}) async {
  final driverRef = _driverRef(provinceId, cityId, officeId, driverId);

  // dot notation으로 nested field 업데이트 (Kotlin 원본과 동일)
  await driverRef.update({
    'carryOver.balance': 0,
    'carryOver.status': 'SETTLED',
    'carryOver.transferredAt': null,
    'carryOver.transferredBy': null,
    'carryOver.lastUpdatedAt': Timestamp.now(),
  });
}
```

**Firestore dot notation 주의**: `cloud_firestore` Flutter SDK 도 Kotlin SDK와 동일하게 `'carryOver.status'` dot 표기법 지원. 전체 carryOver 객체 덮어쓰기가 아니라 **서브필드 partial update**로 동작 — transferredAt/transferredBy 외 필드는 영향 없음.

### 2.7 트랜잭션 공통 주의사항

#### 2.7.1 오프라인 동작

**Firestore 트랜잭션은 오프라인에서 실패**할 수 있음 (SDK 문서):
- 일반 `update()` 는 offline persistence 큐에 쌓임 → 온라인 복구 시 flush
- `runTransaction` 은 **읽기가 필요하므로 오프라인에서 즉시 실패** 가능성

**대응 전략**:
- **Phase 1 MVP**: Firestore offline persistence 기본값 활성 (Flutter `cloud_firestore` 자동)
- **오류 발생 시** NOTIFIERS.md `_performFirestoreUpdate` 에서 Crashlytics 기록 + errorMessage Snackbar
- **R1 PendingSync** 도입 시 (의제 12): 오프라인 감지 → sqflite 큐 → 복구 시 재시도 (OVERVIEW.md §R1)

#### 2.7.2 트랜잭션 성능

- Kotlin/Flutter 모두 SDK가 최대 5회 자동 재시도 (read-write conflict 시)
- 트랜잭션 내 **write만 4개 이하** 권장 (Firestore 제한 X, 성능 관점)
- 본 문서 모든 트랜잭션은 read 0~1개 + write 2개 → 정상 범위

#### 2.7.3 낙관적 UI 패턴

모든 트랜잭션은 NOTIFIERS.md §2.3 의 **낙관적 UI 업데이트 후 트랜잭션 성공 시 유지 / 실패 시 롤백** 패턴 준수. 본 문서는 **Repository 레이어 트랜잭션 자체만 기술**, UI 반영은 Notifier 책임.

---

## 3. 실시간 리스너 (이월금만 — 유일한 snapshot 구독)

### 3.1 carryOverListener

**Kotlin 원본**: `DriverViewModel.kt:1261-1308` (~50 LOC)

**핵심 설계 원칙** (CLAUDE.md Driver App 기록):
> **이월금 유일한 Firestore 실시간 리스너** — 비용 최적화 (`carryOverListener`)

기사 문서 하나의 실시간 snapshot. 다른 모든 데이터(`calls`, `assignedCalls`, `settlementSessions`) 는 **1회 조회 + FCM 트리거 재조회** 조합으로 처리.

#### 3.1.1 리스너 등록 패턴

```kotlin
// DriverViewModel.kt:1269-1307
carryOverListener = driverRef.addSnapshotListener { snapshot, e ->
    if (e != null) { /* error handling */ return@addSnapshotListener }

    if (snapshot != null && snapshot.exists()) {
        val carryOverMap = snapshot.get("carryOver") as? Map<String, Any?>
        val carryOver = DriverCarryOver.fromMap(carryOverMap)

        val dailySettlementMap = snapshot.get("dailySettlement") as? Map<String, Any?>
        val dailySettlement = DriverDailySettlement.fromMap(dailySettlementMap)

        _dailySettlementStatus.value = dailySettlement.status

        val effectiveCarryOver = if (dailySettlement.status == DailySettlementStatus.PENDING_CONFIRM) {
            carryOver.copy(balance = dailySettlement.calculatedCarryOver)
        } else { carryOver }

        if (effectiveCarryOver.status == CarryOverStatus.TRANSFERRED ||
            (effectiveCarryOver.balance != 0L && effectiveCarryOver.status != CarryOverStatus.SETTLED)) {
            _carryOver.value = effectiveCarryOver
        } else {
            _carryOver.value = null
        }
    } else {
        _carryOver.value = null
    }
}
```

#### 3.1.2 Flutter StreamSubscription 매핑 (NOTIFIERS.md §4 참조)

```dart
// CarryOverNotifier._startListener (NOTIFIERS.md §4)

_carryOverSub = driverRef.snapshots().listen(
  (snap) {
    if (!snap.exists) {
      state = state.copyWith(carryOver: null);
      return;
    }

    final data = snap.data() ?? <String, dynamic>{};

    // carryOver Map 파싱
    final carryOverMap = data['carryOver'] as Map<String, dynamic>?;
    final carryOver = carryOverMap != null
        ? DriverCarryOver.fromJson(carryOverMap)
        : const DriverCarryOver();

    // dailySettlement Map 파싱
    final dailySettlementMap = data['dailySettlement'] as Map<String, dynamic>?;
    final dailySettlement = dailySettlementMap != null
        ? DriverDailySettlement.fromJson(dailySettlementMap)
        : const DriverDailySettlement();

    // PENDING_CONFIRM 시 calculatedCarryOver 사용
    final effectiveCarryOver =
        dailySettlement.status is DailySettlementStatusPendingConfirm
            ? carryOver.copyWith(balance: dailySettlement.calculatedCarryOver)
            : carryOver;

    // 표시 조건
    final shouldShow = effectiveCarryOver.status is CarryOverStatusTransferred ||
        (effectiveCarryOver.balance != 0 &&
            effectiveCarryOver.status is! CarryOverStatusSettled);

    state = state.copyWith(
      carryOver: shouldShow ? effectiveCarryOver : null,
      dailySettlementStatus: dailySettlement.status,
    );
  },
  onError: (e, st) {
    debugPrint('[CarryOver listener] error: $e');
    FirebaseCrashlytics.instance.recordError(e, st, reason: 'carryOverListener');
  },
);
```

### 3.2 ScenePhase 백그라운드 시 pause (iOS 비용 최적화)

**Android Kotlin 원본**: ScenePhase 개념 없음. `onCleared()` (line 192-197) 에서만 해제.

**iOS Flutter 권장 패턴** (iOS 배터리 정책):

```dart
// CarryOverNotifier에 lifecycle 관찰 추가

class CarryOverNotifier extends StateNotifier<CarryOverState> with WidgetsBindingObserver {
  CarryOverNotifier(/*...*/) : super(const CarryOverState()) {
    WidgetsBinding.instance.addObserver(this);
    _subscribeAuth();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final user = auth.currentUser;
    if (user == null) return;

    switch (state) {
      case AppLifecycleState.resumed:
        if (_carryOverSub == null) _startListener(user.uid);
      case AppLifecycleState.paused:
        _stopListener();  // iOS suspended 전 해제
      default:
        break;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _authSub?.cancel();
    _stopListener();
    super.dispose();
  }
}
```

**주의**:
- iOS 는 suspended 시 네트워크 소켓 자동 종료 — onDisconnect 트리거 → Presence offline
- Firestore SDK 는 자동 reconnection — `resumed` 시 `snapshots()` 호출 시점에 재연결 지연 가능
- **대안**: 항상 구독 유지 + iOS SDK가 알아서 관리 — MVP는 이 방식 권장 (복잡성 최소화)
- **권장 MVP**: 명시적 pause 코드 없이 `StateNotifier.dispose` 만 처리. iOS suspended 시 자동 처리 신뢰

### 3.3 비용 최적화 근거 (CLAUDE.md)

> **Driver App: "앱 시작 시 1회 조회 `.get().await()` (리스너 아님, 비용 최적화 ~$414/월 절감)"**

**계산 근거** (추정):
- 기사 200명 × 활성 시간 12시간/일 × 30일 = 72,000시간/월
- `calls` 리스너 구독 시 Firestore `document read` 월 수십만건 발생 가능
- `carryOver` 만 리스너 유지 → 기사 본인 1문서만 구독 (read 이벤트 발생 시 1회 charge)

**Phase 6에서 이 설계 유지 필수**: NOTIFIERS.md `loadCurrentActiveCall` 는 `.get()` 1회, `carryOverProvider` 만 `.snapshots()`.

---

## 4. 1회 조회 (loadCurrentActiveCall, Kotlin 핵심 패턴)

### 4.1 Kotlin 원본

**파일**: `DriverViewModel.kt:238-326` (~90 LOC)

**로직**:
1. 기사 문서 `.get()` → status 조회 (1회)
2. `calls` `whereEqualTo(assignedDriverId, driverId) + whereIn(status, [ASSIGNED, ACCEPTED, IN_PROGRESS, AWAITING_SETTLEMENT])` → `.get()` (1회)
3. 결과 기반 UI state 복구

**호출 시점**:
- `initializeListenersWithInfo` (line 210-231): 로그인 성공 후 1회
- FCM `call_assigned` 수신 시 (리스너 대체)
- 앱 resume 시 (옵션, 현재 Kotlin은 `refreshActiveCallStatus` 로 별도 처리)

### 4.2 Flutter 매핑

NOTIFIERS.md §2.3.8 `loadCurrentActiveCall` 코드 재사용. 본 문서에서는 **Repository 레이어 분리** 관점으로 기술:

```dart
// CallRepository.getActiveCalls

Future<List<CallInfo>> getActiveCalls({
  required String driverId,
  required String provinceId,
  required String cityId,
  required String officeId,
}) async {
  final snap = await firestore
      .collection(FirestorePaths.provinces).doc(provinceId)
      .collection(FirestorePaths.cities).doc(cityId)
      .collection(FirestorePaths.offices).doc(officeId)
      .collection(FirestorePaths.calls)
      .where('assignedDriverId', isEqualTo: driverId)
      .where('status', whereIn: ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'AWAITING_SETTLEMENT'])
      .get();

  return snap.docs
      .map((doc) {
        try {
          return CallInfo.fromFirestore(doc);
        } catch (e) {
          debugPrint('[getActiveCalls] fromFirestore 실패: $e');
          return null;
        }
      })
      .whereType<CallInfo>()
      .toList();
}

Future<Driver?> getDriver({
  required String driverId,
  required String provinceId,
  required String cityId,
  required String officeId,
}) async {
  final snap = await firestore
      .collection(FirestorePaths.provinces).doc(provinceId)
      .collection(FirestorePaths.cities).doc(cityId)
      .collection(FirestorePaths.offices).doc(officeId)
      .collection(FirestorePaths.designatedDrivers).doc(driverId)
      .get();

  if (!snap.exists) return null;
  return Driver.fromFirestore(snap);
}
```

### 4.3 Firestore offline persistence 자동 활성 (의제 12)

`cloud_firestore: ^5.4.4` 는 Flutter 앱에서 **기본값으로 offline persistence ON** (`PersistenceSettings(persistenceEnabled: true)`).

**명시적 설정 (필요 시 `main.dart`)**:

```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();

  // MVP는 기본값 유지. 변경 불필요.
  // FirebaseFirestore.instance.settings = const Settings(
  //   persistenceEnabled: true,
  //   cacheSizeBytes: Settings.CACHE_SIZE_UNLIMITED,
  // );

  runApp(const ProviderScope(child: DriverApp()));
}
```

**offline 동작**:
- `.get()` → 네트워크 오프라인 시 로컬 캐시에서 반환 (source=CACHE)
- `.update()` → 로컬 큐에 쌓임, 복구 시 자동 flush
- `runTransaction` → 즉시 실패 (§2.7.1)
- `.snapshots()` → 캐시 기반 즉시 emit + 네트워크 복구 후 서버 데이터 emit

---

## 5. Repository 패턴

### 5.1 전체 구조

```
lib/
├── features/
│   ├── driver/
│   │   ├── data/
│   │   │   └── driver_repository.dart         ← 기사 문서 + presence
│   │   └── notifiers/
│   │       └── driver_workflow_notifier.dart  ← Repository 호출
│   ├── call/
│   │   └── data/
│   │       └── call_repository.dart           ← calls 컬렉션 전용
│   └── settlement/
│       └── data/
│           └── settlement_repository.dart     ← carryOver + settlementSessions
└── core/
    └── providers.dart                          ← Repository Provider 정의
```

### 5.2 CallRepository

```dart
// lib/features/call/data/call_repository.dart

import 'package:cloud_firestore/cloud_firestore.dart';
import '../../../core/constants/firestore_paths.dart';
import '../../../domain/models/call_info.dart';

class CallRepository {
  CallRepository({required this.firestore});
  final FirebaseFirestore firestore;

  DocumentReference<Map<String, dynamic>> _callRef(
    String provinceId, String cityId, String officeId, String callId) {
    return firestore
        .collection(FirestorePaths.provinces).doc(provinceId)
        .collection(FirestorePaths.cities).doc(cityId)
        .collection(FirestorePaths.offices).doc(officeId)
        .collection(FirestorePaths.calls).doc(callId);
  }

  /// 활성 콜 1회 조회 (§4.2)
  Future<List<CallInfo>> getActiveCalls(...);

  /// 콜 상세 조회 (CallDetailsNotifier.family)
  Future<CallInfo?> getCallDetails({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String callId,
  }) async {
    final doc = await _callRef(provinceId, cityId, officeId, callId).get();
    if (!doc.exists) return null;
    return CallInfo.fromFirestore(doc);
  }

  /// 콜 수락 트랜잭션 (§2.1)
  Future<void> acceptCall(...);

  /// 콜 거절 트랜잭션 (§2.2)
  Future<void> rejectCall(...);

  /// 운행 시작 트랜잭션 (§2.3)
  Future<void> startDriving(...);

  /// 운행 취소 트랜잭션 (§2.4)
  Future<void> cancelTrip(...);

  /// 운행 완료 — 트랜잭션 아님, 단일 update (§2.5 전 단계)
  Future<void> completeCall({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String callId,
  }) async {
    await _callRef(provinceId, cityId, officeId, callId)
        .update({FirestorePaths.status: 'AWAITING_SETTLEMENT'});
  }

  /// 정산 확정 트랜잭션 (§2.5)
  Future<void> confirmAndFinalizeTrip(...);

  /// onResume 재검증 — Kotlin refreshActiveCallStatus
  Future<String?> getCallStatus({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String callId,
  }) async {
    final doc = await _callRef(provinceId, cityId, officeId, callId).get();
    return doc.data()?[FirestorePaths.status] as String?;
  }
}

final callRepositoryProvider = Provider<CallRepository>((ref) {
  return CallRepository(firestore: ref.watch(firestoreProvider));
});
```

### 5.3 DriverRepository

```dart
// lib/features/driver/data/driver_repository.dart

class DriverRepository {
  DriverRepository({required this.firestore});
  final FirebaseFirestore firestore;

  DocumentReference<Map<String, dynamic>> _driverRef(
    String provinceId, String cityId, String officeId, String driverId) {
    return firestore
        .collection(FirestorePaths.provinces).doc(provinceId)
        .collection(FirestorePaths.cities).doc(cityId)
        .collection(FirestorePaths.offices).doc(officeId)
        .collection(FirestorePaths.designatedDrivers).doc(driverId);
  }

  /// 기사 문서 1회 조회
  Future<Driver?> getDriver({...}) async { ... }

  /// 기사 상태 업데이트 (Kotlin updateDriverStatus, DriverViewModel.kt:846-866)
  Future<void> updateDriverStatus({
    required String driverId,
    required String provinceId,
    required String cityId,
    required String officeId,
    required DriverStatus newStatus,
  }) async {
    await _driverRef(provinceId, cityId, officeId, driverId)
        .update({FirestorePaths.status: newStatus.toJson()});
  }

  /// FCM 토큰 등록 — 의제 5 `fcmTokenPlatform` 포함
  /// 의제 11 `platform` 필드 동시 저장
  Future<void> updateFcmToken({
    required String driverId,
    required String provinceId,
    required String cityId,
    required String officeId,
    required String token,
  }) async {
    await _driverRef(provinceId, cityId, officeId, driverId).update({
      FirestorePaths.fcmToken: token,
      FirestorePaths.fcmTokenPlatform: Platform.isIOS ? 'ios' : 'android',
      FirestorePaths.platform: Platform.isIOS ? 'ios' : 'android',  // 의제 11
    });
  }

  /// collectionGroup 쿼리 (AUTH.md §2.2.1)
  Future<QuerySnapshot<Map<String, dynamic>>> findByAuthUid(String uid) async {
    return firestore
        .collectionGroup(FirestorePaths.designatedDrivers)
        .where(FirestorePaths.authUid, isEqualTo: uid)
        .limit(1)
        .get();
  }

  /// pending_drivers 체크 (AUTH.md §2.2)
  Future<bool> isPendingDriver(String uid) async {
    final doc = await firestore
        .collection(FirestorePaths.pendingDrivers).doc(uid)
        .get();
    return doc.exists;
  }

  /// 회원가입 — pending_drivers 생성
  Future<void> createPendingDriver({
    required String uid,
    required String email,
    required String name,
    required String phone,
    required String provinceId,
    required String cityId,
    required String officeId,
  }) async {
    await firestore.collection(FirestorePaths.pendingDrivers).doc(uid).set({
      FirestorePaths.authUid: uid,
      'email': email,
      'name': name,
      'phone': phone,
      'provinceId': provinceId,
      'cityId': cityId,
      'officeId': officeId,
      FirestorePaths.approvalStatus: 'PENDING',
      'createdAt': FieldValue.serverTimestamp(),
    });
  }

  /// 기사 status + lastLoginTime 업데이트 (로그인 성공 시, AUTH.md §2.2.5)
  Future<void> setOnlineOnLogin(DocumentReference<Map<String, dynamic>> driverRef) async {
    await driverRef.update({
      FirestorePaths.status: 'ONLINE',
      'lastLoginTime': Timestamp.now(),
    });
  }
}

final driverRepositoryProvider = Provider<DriverRepository>((ref) {
  return DriverRepository(firestore: ref.watch(firestoreProvider));
});
```

### 5.4 SettlementRepository

```dart
// lib/features/settlement/data/settlement_repository.dart

class SettlementRepository {
  SettlementRepository({required this.firestore});
  final FirebaseFirestore firestore;

  /// carryOver 실시간 리스너 Stream (NOTIFIERS.md §4)
  Stream<DocumentSnapshot<Map<String, dynamic>>> watchDriver({
    required String driverId,
    required String provinceId,
    required String cityId,
    required String officeId,
  }) {
    return firestore
        .collection(FirestorePaths.provinces).doc(provinceId)
        .collection(FirestorePaths.cities).doc(cityId)
        .collection(FirestorePaths.offices).doc(officeId)
        .collection(FirestorePaths.designatedDrivers).doc(driverId)
        .snapshots();
  }

  /// 수령확인 — carryOver.status = SETTLED (§2.6)
  Future<void> confirmReceiveCarryOver(...);

  /// 업무마감 — dailySettlement 필드 업데이트 (NOTIFIERS.md §5.2)
  Future<void> submitDailySettlement({
    required String driverId,
    required String provinceId,
    required String cityId,
    required String officeId,
    required DriverDailySettlement settlement,
  }) async {
    final driverRef = firestore
        .collection(FirestorePaths.provinces).doc(provinceId)
        .collection(FirestorePaths.cities).doc(cityId)
        .collection(FirestorePaths.offices).doc(officeId)
        .collection(FirestorePaths.designatedDrivers).doc(driverId);
    await driverRef.update({'dailySettlement': settlement.toJson()});
  }

  /// 오늘 정산 세션 조회 (settlementSessions는 CF가 생성, 기사앱은 read only)
  Future<SettlementSession?> getTodaySession({
    required String provinceId,
    required String cityId,
    required String officeId,
  }) async {
    final today = DateFormat('yyyy-MM-dd').format(DateTime.now());
    final doc = await firestore
        .collection(FirestorePaths.provinces).doc(provinceId)
        .collection(FirestorePaths.cities).doc(cityId)
        .collection(FirestorePaths.offices).doc(officeId)
        .collection(FirestorePaths.settlementSessions).doc(today)
        .get();
    if (!doc.exists) return null;
    return SettlementSession.fromFirestore(doc);
  }

  /// 사무실 depositRatio 조회 (office.depositRatio)
  Future<int> getDepositRatio({
    required String provinceId,
    required String cityId,
    required String officeId,
  }) async {
    final doc = await firestore
        .collection(FirestorePaths.provinces).doc(provinceId)
        .collection(FirestorePaths.cities).doc(cityId)
        .collection(FirestorePaths.offices).doc(officeId)
        .get();
    return (doc.data()?['depositRatio'] as num?)?.toInt() ?? 60;
  }
}

final settlementRepositoryProvider = Provider<SettlementRepository>((ref) {
  return SettlementRepository(firestore: ref.watch(firestoreProvider));
});
```

### 5.5 Repository → Notifier 주입

NOTIFIERS.md 의 Notifier 들은 **FirebaseFirestore 를 직접 의존**하는 형태로 작성됨. Phase 6 Week 2 중 **Repository 레이어 분리 리팩토링** 권장:

```dart
// Before (NOTIFIERS.md §2.3.1 현재)
class DriverWorkflowNotifier extends StateNotifier<DriverScreenUiState> {
  final FirebaseFirestore firestore;  // ❌ 직접 의존
  // acceptCall 내부에 runTransaction 직접 호출
}

// After (Repository 분리)
class DriverWorkflowNotifier extends StateNotifier<DriverScreenUiState> {
  final CallRepository callRepository;
  final DriverRepository driverRepository;

  Future<void> acceptCall(String callId) async {
    // ... UI 업데이트 ...
    await callRepository.acceptCall(
      callId: callId,
      driverId: driverId,
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
    );
  }
}
```

**이점**:
- 단위 테스트 시 Repository mock 만 주입 (FakeFirebaseFirestore 불필요)
- Firestore 경로 로직 단일 위치
- 오류 로깅/Crashlytics 연동 Repository 레이어에서 일관 처리

---

## 6. 의제 4 — CF `oncallassigned` apns 블록 audit 체크리스트

### 6.1 현황

**Kotlin DriverViewModel / Flutter Notifier 는 CF payload 를 직접 생성하지 않음**. CF `oncallassigned` 가 기사 문서 `fcmToken` 조회 후 payload 구성 → `admin.messaging().send()`.

**의제 4 결정** (`flutter/WORKING_DOC.md` §5):
- CF `oncallassigned` payload 에 `apns` 블록 추가 (기존 `android: {priority, ttl}` 유지 + `data: {...}` 보존)
- **최상위 `notification` 키 금지** (Android FCM 이중 표시 회귀 방지)

### 6.2 수정 대상 CF 함수 (Phase 6 Week 2~3 별도 과제)

**Grep 결과 요약** (`functions/src/index.ts` 기준, 이전 검토에서 확인):
- **`admin.messaging().send()` 호출**: 28곳+
- **`android: {priority: "high"}` 블록은 다수**, **`apns:` 블록은 0곳** (전무)

**우선순위 3단 분류**:

| 우선순위 | CF 함수 | 라인 (index.ts) | 대상 수신자 |
|---------|---------|---------------|-----------|
| **1 (필수)** | `oncallassigned` | 589 | 기사 (배차 알림) |
| 1 (필수) | `onCallStatusChanged` — call_cancelled | 1810+ | 기사 (취소) |
| 1 (필수) | `notifyCustomerOnComplete` | 1743+, 1783+ | 손님 |
| 2 (배송 기능) | `onDriverStatusChange` | 4668+ | 매니저 |
| 2 | `retryPendingNotifications` | 310+ (재전송) | 기사 |
| 3 (부수) | 매니저 FCM 28곳 | 848/976/1218/... | 매니저 |

### 6.3 apns 블록 추가 패턴 (의제 4 결정)

**Kotlin 원본 핸들러가 소비하는 data 블록 100% 보존**:
```typescript
// functions/src/index.ts (수정 전, DriverViewModel.kt:148 호환)
const driverPayload = {
    data: {
        callId: callId,
        notificationId: notificationId,
        type: "call_assigned",
        title: "새로운 콜 배정",
        body: "새로운 콜이 배정되었습니다."
    },
    android: {
        priority: "high" as const,
        ttl: 30000,
    },
    token: driverFcmToken,
};
```

**수정 후 (의제 4 결정 apns 추가)**:
```typescript
const driverPayload = {
    data: {  // 기존 data 블록 100% 보존 — Kotlin 핸들러 호환
        callId: callId,
        notificationId: notificationId,
        type: "call_assigned",
        title: "새로운 콜 배정",
        body: "새로운 콜이 배정되었습니다."
    },
    android: {
        priority: "high" as const,
        ttl: 30000,
    },
    apns: {
        headers: {
            "apns-priority": "10",
            "apns-push-type": "alert",
        },
        payload: {
            aps: {
                alert: {
                    title: "새로운 콜 배정",
                    body: "새로운 콜이 배정되었습니다.",
                },
                sound: "default",
                "interruption-level": "time-sensitive",  // iOS 15+ 의제 1 결정
                "mutable-content": 1,
                "content-available": 1,
            }
        }
    },
    token: driverFcmToken,
};
```

### 6.4 Audit 체크리스트 (Phase 6 Week 2~3 CF 수정 과제)

- [ ] `oncallassigned` (line 589) — 기사 call_assigned apns 블록 추가
- [ ] `oncallassigned` (line 636) — 손님 DRIVER_ASSIGNED apns 블록 추가
- [ ] `onCallStatusChanged` (line 1925+) — 손님 상태변경 FCM
- [ ] `notifyCustomerOnComplete` (line 1743+) — RIDE_COMPLETED + POINTS_EARNED
- [ ] `onCallCancelledByDriver` — 기사/손님 취소 FCM (3/24 추가)
- [ ] `transferCarryOverNotification` — 이월금 이체 FCM
- [ ] `onSettlementFinalized/Confirmed/Rejected` — 정산 FCM 3종
- [ ] `notifyDriverCancellation` callable — 관리자 취소 FCM
- [ ] **Firebase Emulator 테스트**: Android/iOS 토큰 각각으로 실제 수신 검증

**Admin SDK 자동 분기**: `admin.messaging().send()` 는 토큰 플랫폼 자동 판별 → `android`/`apns` 블록 중 매칭만 적용. **Kotlin 핸들러에 영향 없음** (이전 검토 포인트 4 답변).

---

## 7. 의제 5 — `fcmToken` + `fcmTokenPlatform` 메타

### 7.1 결정 요약

**단일 `fcmToken` 필드 유지** + **`fcmTokenPlatform` 메타 필드 추가** (dual 필드 반대, 검토 포인트 3 답변).

### 7.2 Flutter 구현 (DriverRepository §5.3)

```dart
Future<void> updateFcmToken({
  required String driverId,
  required String provinceId,
  required String cityId,
  required String officeId,
  required String token,
}) async {
  await _driverRef(provinceId, cityId, officeId, driverId).update({
    FirestorePaths.fcmToken: token,
    FirestorePaths.fcmTokenPlatform: Platform.isIOS ? 'ios' : 'android',
    FirestorePaths.platform: Platform.isIOS ? 'ios' : 'android',  // 의제 11 동시 저장
  });
}
```

### 7.3 Kotlin 기사앱도 동일 필드 추가 필요 (Android 측 Kotlin 수정)

**별도 과제** (Phase 6 Week 0~1):
```kotlin
// driver_app/.../MyFirebaseMessagingService.kt:55 수정
driverRef.update(mapOf(
    Constants.FIELD_FCM_TOKEN to token,
    "fcmTokenPlatform" to "android",   // 신규
    "platform" to "android",           // 의제 11 신규
)).addOnSuccessListener { ... }
```

### 7.4 마이그레이션 — 기존 기사 문서

**기존 기사 200명 문서에 `platform` 필드 없음** → CF 에서 이벤트 기록 시 `platform: undefined` 방지 위해 **1회성 스크립트** 실행 권장:

```js
// functions/scripts/backfill-platform.js (신규)

const admin = require('firebase-admin');
admin.initializeApp();
const db = admin.firestore();

async function backfill() {
  const provinces = await db.collection('provinces').get();
  for (const p of provinces.docs) {
    const cities = await p.ref.collection('cities').get();
    for (const c of cities.docs) {
      const offices = await c.ref.collection('offices').get();
      for (const o of offices.docs) {
        const drivers = await o.ref.collection('designated_drivers').get();
        for (const d of drivers.docs) {
          const data = d.data();
          if (data.fcmToken && !data.fcmTokenPlatform) {
            await d.ref.update({
              fcmTokenPlatform: 'android',  // 기존은 전부 Android 전제
              platform: 'android',
            });
            console.log(`Backfilled: ${d.ref.path}`);
          }
        }
      }
    }
  }
}

backfill().then(() => process.exit(0));
```

---

## 8. 의제 11 — `acceptanceEvents` 컬렉션

### 8.1 컬렉션 스키마

```
/acceptanceEvents/{eventId}:
  callId: string
  assignedDriverId: string
  platform: 'ios' | 'android'
  assignedAt: Timestamp
  acceptedAt: Timestamp?       // 수락 시 설정
  rejectedAt: Timestamp?       // 거절 시 설정
  timeoutAt: Timestamp?        // 타임아웃 시 설정
  latencyMs: number?           // acceptedAt - assignedAt (수락 시만)
  outcome: 'accepted' | 'rejected' | 'timeout'
  provinceId: string
  cityId: string
  officeId: string
```

### 8.2 생성 주체: CF (서버)

**클라이언트(Flutter/Kotlin) 에서 `acceptanceEvents` 생성 금지**. CF 가 권한 신뢰 소스:

- `onCallStatusChanged` CF 에서 status 전이 시 이벤트 기록
  - ASSIGNED → ACCEPTED: `{outcome: 'accepted', latencyMs: 계산}`
  - ASSIGNED → WAITING (거절): `{outcome: 'rejected'}`
- `checkAssignedTimeout` CF 에서 타임아웃 발생 시 `{outcome: 'timeout'}`

**Phase 6 Week 2~3 별도 과제** (OVERVIEW.md §Week 2~3 CF 작업).

### 8.3 클라이언트 read 권한 없음

Firestore 보안 규칙 — 기사/관리자 모두 `acceptanceEvents` **read 금지** (admin SDK 전용).

```js
// firestore.rules (신규 블록)

match /acceptanceEvents/{eventId} {
  allow read: if false;   // 클라이언트 접근 전면 금지
  allow write: if false;  // CF admin SDK 만 write
}
```

### 8.4 월별 집계 스케줄러 (CF, Phase 6 Week 3)

```typescript
// functions/src/index.ts (신규)

export const aggregateMonthlyStats = onSchedule(
  {
    schedule: "0 0 1 * *",  // 매월 1일 0시
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
  },
  async () => {
    const lastMonth = /* 전월 계산 */;
    const events = await db.collection('acceptanceEvents')
        .where('assignedAt', '>=', lastMonthStart)
        .where('assignedAt', '<', thisMonthStart)
        .get();

    // 플랫폼별 집계
    const stats = {
      ios: {total: 0, accepted: 0, rejected: 0, timeout: 0, avgLatencyMs: 0},
      android: {total: 0, accepted: 0, rejected: 0, timeout: 0, avgLatencyMs: 0},
    };
    for (const doc of events.docs) {
      const d = doc.data();
      const p = d.platform as 'ios' | 'android';
      if (!stats[p]) continue;
      stats[p].total += 1;
      stats[p][d.outcome] += 1;
      if (d.outcome === 'accepted' && d.latencyMs) {
        stats[p].avgLatencyMs += d.latencyMs;
      }
    }
    // 평균 계산
    for (const p of ['ios', 'android'] as const) {
      if (stats[p].accepted > 0) {
        stats[p].avgLatencyMs = stats[p].avgLatencyMs / stats[p].accepted;
      }
      stats[p].acceptanceRate = stats[p].total > 0
          ? stats[p].accepted / stats[p].total : 0;
    }

    await db.collection('monthlyStats').doc(lastMonthYYYYMM).set({
      month: lastMonthYYYYMM,
      platforms: stats,
      generatedAt: Timestamp.now(),
    });
  }
);
```

### 8.5 R1 발동 기준 측정 (의제 7 LockScreen)

`TRIGGERS.md` R1_LOCKSCREEN 발동 조건:
> iOS 기사 `acceptanceRate` 가 Android 대비 5%p 이상 낮음 (월 100콜 이상)

→ `/monthlyStats/{YYYY-MM}.platforms.ios.acceptanceRate` vs `android.acceptanceRate` 월별 비교로 **자동 판정 가능**.

---

## 9. 보안 규칙 업데이트

### 9.1 변경 사항 요약

| 필드/컬렉션 | 현재 권한 | 변경 후 권한 |
|-----------|---------|-----------|
| `designated_drivers/{uid}.fcmToken` | 기사 본인 write | 동일 (변경 없음) |
| `designated_drivers/{uid}.fcmTokenPlatform` | — (신규) | **기사 본인 write** |
| `designated_drivers/{uid}.platform` | — (신규) | **기사 본인 write** |
| `acceptanceEvents/*` | — (신규) | **클라이언트 전면 금지** |
| `monthlyStats/*` | — (신규) | **클라이언트 read 금지 (관리자 대시보드 별도)** |

### 9.2 firestore.rules 변경 예시

```js
// firestore.rules (주요 변경 부분만)

match /provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{driverId} {
  allow read: if request.auth != null;
  // 기존 write 규칙 유지 + 신규 필드 허용
  allow update: if request.auth != null &&
      request.auth.uid == driverId &&
      // 허용 필드 화이트리스트에 fcmTokenPlatform + platform 추가
      request.resource.data.diff(resource.data).affectedKeys()
          .hasOnly([
            'status', 'lastLoginTime', 'fcmToken',
            'fcmTokenPlatform', 'platform',        // 의제 5 + 11 신규
            'carryOver', 'dailySettlement',
          ]);
}

// 의제 11: acceptanceEvents 완전 차단
match /acceptanceEvents/{eventId} {
  allow read: if false;
  allow write: if false;  // CF admin SDK 만
}

match /monthlyStats/{yyyyMm} {
  allow read: if false;   // 추후 관리자 role 기반 허용
  allow write: if false;
}
```

### 9.3 배포 순서 (Phase 6 Week 0~1 선행)

1. Kotlin 기사앱 먼저 배포 (platform 필드 저장 시작)
2. Flutter 기사앱 빌드 시점에는 이미 platform 필드 허용 상태
3. 순서 역전 시 기존 기사 업데이트 → platform 필드 write 거부 → login 실패 리스크

---

## 10. 테스트 전략

### 10.1 단위 테스트 — `fake_cloud_firestore`

```dart
// test/features/call/data/call_repository_test.dart

import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CallRepository.acceptCall', () {
    late FakeFirebaseFirestore firestore;
    late CallRepository repository;

    setUp(() {
      firestore = FakeFirebaseFirestore();
      repository = CallRepository(firestore: firestore);
    });

    test('정상 수락 — call.status ASSIGNED → ACCEPTED + driver PREPARING', () async {
      // Arrange
      await firestore
          .collection('provinces').doc('gg')
          .collection('cities').doc('hc')
          .collection('offices').doc('o1')
          .collection('calls').doc('call1')
          .set({'status': 'ASSIGNED'});
      await firestore
          .collection('provinces').doc('gg')
          .collection('cities').doc('hc')
          .collection('offices').doc('o1')
          .collection('designated_drivers').doc('driver1')
          .set({'status': 'WAITING'});

      // Act
      await repository.acceptCall(
        callId: 'call1', driverId: 'driver1',
        provinceId: 'gg', cityId: 'hc', officeId: 'o1',
      );

      // Assert
      final callDoc = await firestore
          .collection('provinces').doc('gg')
          .collection('cities').doc('hc')
          .collection('offices').doc('o1')
          .collection('calls').doc('call1').get();
      expect(callDoc.data()?['status'], 'ACCEPTED');

      final driverDoc = await firestore
          .collection('provinces').doc('gg')
          .collection('cities').doc('hc')
          .collection('offices').doc('o1')
          .collection('designated_drivers').doc('driver1').get();
      expect(driverDoc.data()?['status'], 'PREPARING');
    });

    test('상태 불일치 시 CALL_NOT_ASSIGNABLE 예외', () async {
      await firestore.collection('...').doc('call1').set({'status': 'COMPLETED'});

      expect(
        () => repository.acceptCall(callId: 'call1', ...),
        throwsA(isA<StateError>().having(
          (e) => e.message,
          'message',
          contains('CALL_NOT_ASSIGNABLE'),
        )),
      );
    });

    test('콜 문서 없음 시 예외', () async {
      expect(
        () => repository.acceptCall(callId: 'nonexistent', ...),
        throwsA(isA<StateError>()),
      );
    });
  });
}
```

### 10.2 통합 테스트 — Firebase Emulator

```bash
# Firebase Emulator 시작
firebase emulators:start --only firestore,auth

# Flutter 테스트 실행 시 emulator 연결
firebase_test_lab \
  --project my-project \
  --test integration_test/call_workflow_test.dart
```

```dart
// integration_test/call_workflow_test.dart

void main() {
  setUpAll(() async {
    await Firebase.initializeApp();
    FirebaseFirestore.instance.useFirestoreEmulator('localhost', 8080);
    FirebaseAuth.instance.useAuthEmulator('localhost', 9099);
  });

  testWidgets('콜 사이클 1순환 End-to-End', (tester) async {
    // 로그인 → 배차 수신 → 수락 → 운행 시작 → 완료 → 정산
    // ...
  });
}
```

### 10.3 필수 테스트 케이스 (Phase 6 Week 2~3)

| Repository | 메서드 | 테스트 3종 |
|-----------|--------|-----------|
| CallRepository | acceptCall | 정상 / CALL_NOT_ASSIGNABLE / 문서 없음 |
| CallRepository | rejectCall | call + driver 동시 업데이트 확인 |
| CallRepository | startDriving | **snake_case 필드 저장 검증** |
| CallRepository | confirmAndFinalizeTrip | 5가지 결제방식 creditAmount 각각 |
| CallRepository | getActiveCalls | whereIn 쿼리 결과 개수 |
| DriverRepository | updateFcmToken | fcmTokenPlatform / platform 동시 저장 |
| DriverRepository | findByAuthUid | collectionGroup 결과 |
| DriverRepository | isPendingDriver | pending_drivers 존재/부재 |
| SettlementRepository | watchDriver | Stream emit 순서 + dispose |
| SettlementRepository | submitDailySettlement | dailySettlement 필드 구조 |
| SettlementRepository | confirmReceiveCarryOver | dot notation partial update |

---

## 11. 마이그레이션 작업 순서 (Phase 6)

### Week 1 — 기반
1. `lib/core/constants/firestore_paths.dart` 작성 (§1.3)
2. `lib/features/driver/data/driver_repository.dart` (§5.3)
3. `lib/features/call/data/call_repository.dart` (§5.2) — 트랜잭션 6곳
4. `lib/features/settlement/data/settlement_repository.dart` (§5.4)

### Week 2 — Notifier 연동
5. NOTIFIERS.md Notifier 들이 Repository 주입 받도록 리팩토링 (§5.5)
6. `fake_cloud_firestore` 기반 단위 테스트 10건 (§10.1)

### Week 3 — 서버측 별도 과제 (중재자 또는 사용자)
7. **CF `oncallassigned` apns 블록 추가** (§6.4 체크리스트)
8. **CF `acceptanceEvents` 컬렉션 + 월 집계 스케줄러** (§8)
9. **`firestore.rules` 업데이트** (§9.2)
10. **Kotlin 기사앱 `platform` 필드 저장 수정** (§7.3)
11. **backfill-platform.js 스크립트 1회 실행** (§7.4)

### Week 4 — 통합 테스트
12. Firebase Emulator E2E (§10.2)
13. TestFlight Internal 업로드 + 실기기 콜 사이클 검증

---

## 12. 참조

- `flutter/driver_app/MVP/OVERVIEW.md` — Phase 6 실행 사양
- `flutter/driver_app/MVP/MODELS.md` — Freezed 모델 (CallInfo.fromFirestore 등)
- `flutter/driver_app/MVP/ENUMS.md` — sealed class (CallStatus, DriverStatus, ...)
- `flutter/driver_app/MVP/NOTIFIERS.md` §2 — DriverWorkflowNotifier 코드 (Repository 호출 위치)
- `flutter/driver_app/MVP/AUTH.md` — 로그인 시 Firestore 접근 (collectionGroup 쿼리)
- `flutter/driver_app/MVP/SCREENS.md` — 화면별 Firestore 호출 상위 경로
- `flutter/WORKING_DOC.md` §5 의제 4/5/11/12 결정 전문
- `ios/SHARED_LOGIC.md` §1 Firestore 경로 + §2 상태 전이 + §4 정산 공식
- `driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt` — Kotlin 원본 1,600 LOC (트랜잭션 6곳)
- `driver_app/app/src/main/java/com/designated/driverapp/data/Constants.kt` — Kotlin 상수 62 LOC (FirestorePaths 대응)
- `functions/src/index.ts` — CF 41개 함수 (의제 4 audit 대상)

---

## 13. 작성 완료 요약

- **트랜잭션 6곳 완전 매핑**: acceptCall / rejectCall / startDriving / cancelTrip / confirmAndFinalizeTrip / confirmReceiveCarryOver
- **Kotlin 원본 코드 + Flutter Dart 코드 병기** (각 트랜잭션)
- **snake_case Firestore 필드 보존 원칙 강조** (§2.3)
- **carryOverListener** — 유일한 실시간 리스너 + 비용 최적화 근거
- **loadCurrentActiveCall** — 1회 조회 + whereIn 쿼리 패턴
- **Repository 3종**: CallRepository / DriverRepository / SettlementRepository
- **의제 4 CF apns audit 체크리스트 8건**
- **의제 5 `fcmTokenPlatform` 메타 + Kotlin 동시 수정 + backfill 스크립트**
- **의제 11 `acceptanceEvents` 스키마 + 월 집계 + R1 발동 기준 자동화**
- **보안 규칙 업데이트 + 배포 순서** (§9)
- **테스트 전략 — fake_cloud_firestore + Firebase Emulator E2E**
- **Phase 6 Week 1~4 작업 순서 명시**
