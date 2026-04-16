# 기사앱 Notifiers (Riverpod StateNotifier 매핑)

> **의제 14-A** (Dart 3 sealed class + pattern matching) + **14-B** (Riverpod ^2.5.1) + **12** (sqflite R1, MVP는 shared_preferences) + **11** (Analytics/Crashlytics 신규) **반영**
> **MODELS.md의 Freezed 모델 + ENUMS.md의 sealed class 사용** (DriverStatus 9종, DailySettlementStatus 4종, CarryOverStatus 3종 수정본)
> **원본 기준**: `driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt` (~1,600 LOC)
> **작성일**: 2026-04-16
> **작성자**: kotlin-expert

---

## 1. Notifier 구조 개요

### 1.1 Riverpod 2.x 패턴 선택 근거

**StateNotifierProvider** 사용 (NotifierProvider 아님):
- 의제 14-B 결정: `flutter_riverpod ^2.5.1`
- `riverpod_annotation ^2.3.5` 로 `@riverpod` 코드 생성도 병행 가능 (선택)
- 본 문서는 **수동 StateNotifier**(`StateNotifier<T>` + `StateNotifierProvider<N, T>`)를 기본으로 기술 — Kotlin `ViewModel` 매핑이 가장 직관적이며 riverpod_test와 호환 용이

### 1.2 Provider 명명 규칙

| 대상 | 네이밍 | 예시 |
|------|--------|-----|
| Notifier 클래스 | `XxxNotifier` | `DriverWorkflowNotifier` |
| 상태 클래스 | `XxxState` | `DriverScreenUiState` (MODELS.md §14-A) |
| Provider | `xxxProvider` | `driverWorkflowProvider` |
| Service Provider | `xxxServiceProvider` | `firestoreServiceProvider` |
| autoDispose 여부 | UI 전용은 `autoDispose`, 글로벌 세션 유지는 일반 | 로그인 후 유지 = 일반, 콜 상세는 `autoDispose.family<...,String>` |

### 1.3 ref.watch / ref.read / ref.listen 가이드

- **`ref.watch(provider)`** — 위젯 build 메서드 또는 Notifier 내부에서 반응형 구독. 값 변경 시 리빌드 자동
- **`ref.read(provider)`** — 일회성 값 조회. `onPressed` 콜백 등 이벤트 핸들러 내부 전용. build 메서드 내 호출 금지
- **`ref.listen(provider, callback)`** — side effect 전용 (Snackbar, Navigation, Analytics `logEvent`). 위젯 build 내부에서 1회 등록

### 1.4 autoDispose 정책

| Notifier | autoDispose? | 이유 |
|---------|-------------|------|
| `driverWorkflowProvider` | **No** | 앱 세션 전체 유지 (로그인~로그아웃) |
| `loginProvider` | **No** | 로그인 화면 <-> 홈 전환 시에도 유지 (상태 초기화는 `resetLoginState()` 명시 호출) |
| `signUpProvider` | **Yes** | 회원가입 완료/취소 시 즉시 dispose |
| `presenceProvider` | **No** | 앱 세션 동안 유지 (기존 `presence_service.dart` 재사용) |
| `carryOverProvider` | **No** | 이월금 리스너는 로그인 후 계속 구독 |
| `callDetailsProvider.family(callId)` | **Yes** | 콜 상세 화면 떠날 때 해제 |
| `settlementProvider` | **No** | 정산 화면 왕복 시 유지 |

### 1.5 Notifier 인벤토리

| Notifier | 책임 | Kotlin 원본 매핑 |
|---------|------|-----------------|
| **DriverWorkflowNotifier** | 콜 사이클 + 운행 + 정산 UI state | `DriverViewModel.kt:200-1100` (~900 LOC) |
| **LoginNotifier** | 로그인 + 자동로그인 + 오프라인 로그인 | `LoginViewModel.kt:30-292` |
| **SignUpNotifier** | 회원가입 + 지역/도시/사무실 선택 | `SignUpViewModel.kt` 전체 (미Read, 구조는 SCREENS.md에서) |
| **PresenceNotifier** | Presence 상태 관리 + 네트워크 배너 | `PresenceManager.kt:1-149` + 기존 `presence_service.dart` 재사용 |
| **CarryOverNotifier** | 이월금 실시간 리스너 (유일한 snapshot 구독) | `DriverViewModel.kt:1261-1308` |
| **SettlementNotifier** | 일일 정산 + 통합 제출 + 매니저 반려 재제출 | `DriverViewModel.kt:1348-1600` (submitDailySettlement) |
| **FcmNotifier** (Service) | FCM 6종 라우팅 + Delivery ACK | `driver_app_flutter/lib/services/fcm_service.dart` 확장 |
| **CallDetailsNotifier.family** | 콜 상세 조회 (autoDispose) | `DriverViewModel.kt:329` `_callDetailsState` |

---

## 2. DriverWorkflowNotifier (가장 복잡, ~600 LOC)

### 2.1 State 정의

**MODELS.md §14-A** `DriverScreenUiState` Freezed 사용. 재정의 없이 import.

```dart
// lib/features/driver/notifiers/driver_workflow_notifier.dart

import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../domain/state/driver_screen_ui_state.dart';
import '../../domain/models/call_info.dart';
import '../../domain/enums/driver_status.dart';
import '../../domain/enums/call_status.dart';
// Service providers
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:firebase_analytics/firebase_analytics.dart';
```

### 2.2 Notifier 골격

```dart
class DriverWorkflowNotifier extends StateNotifier<DriverScreenUiState> {
  DriverWorkflowNotifier({
    required this.firestore,
    required this.auth,
    required this.prefs,
    required this.analytics,
  }) : super(const DriverScreenUiState()) {
    _subscribeAuth();
  }

  final FirebaseFirestore firestore;
  final FirebaseAuth auth;
  final SharedPreferences prefs;
  final FirebaseAnalytics analytics;  // 의제 11

  // 중복 클릭 방지 플래그 (Kotlin `_isAccepting`, `_isSubmittingSettlement` 등가)
  bool _isAccepting = false;
  bool _isSubmittingSettlement = false;

  // Handled settlement IDs — 팝업 중복 방지 (Kotlin popupPrefs)
  final Set<String> _handledSettlementIds = <String>{};

  // FCM 토큰 pending (로그인 전 발급된 토큰)
  String? _fcmTokenToRegister;

  StreamSubscription<User?>? _authSub;

  void _subscribeAuth() {
    _authSub = auth.authStateChanges().listen((user) {
      if (user == null) {
        _stopListeners();
        state = const DriverScreenUiState();
      } else {
        _tryAutoInitializeListeners(user.uid);
      }
    });
  }

  @override
  void dispose() {
    _authSub?.cancel();
    _stopListeners();
    super.dispose();
  }

  void _stopListeners() {
    // Firestore listener 구독 해제 (carryOverListener는 별도 Notifier)
  }
}

/// Provider
final driverWorkflowProvider =
    StateNotifierProvider<DriverWorkflowNotifier, DriverScreenUiState>((ref) {
  return DriverWorkflowNotifier(
    firestore: ref.watch(firestoreProvider),
    auth: ref.watch(firebaseAuthProvider),
    prefs: ref.watch(sharedPreferencesProvider),
    analytics: ref.watch(analyticsProvider),
  );
});
```

### 2.3 핵심 메서드 매핑

#### 2.3.1 `acceptCall(String callId)` — 콜 수락

**Kotlin 원본** (`driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt:371-475`):
- 낙관적 UI 업데이트 (즉시 `ACCEPTED` + `driverStatus=ACCEPTED`)
- `runTransaction` (call: ASSIGNED→ACCEPTED + driver: →PREPARING)
- 실패 시 UI 롤백 + `errorMessage` 설정
- `_isAccepting` 플래그로 중복 클릭 차단

**Flutter 매핑**:
```dart
Future<void> acceptCall(String callId) async {
  // 중복 클릭 방지 (Kotlin DriverViewModel.kt:373-377)
  if (_isAccepting) {
    debugPrint('[acceptCall] 이미 진행 중, 무시');
    return;
  }
  _isAccepting = true;

  // 1단계: 즉시 로컬 UI 업데이트 (Kotlin line 389-410)
  final prevState = state;
  final targetCall = state.assignedCalls.firstWhereOrNull((c) => c.id == callId);
  if (targetCall != null) {
    state = state.copyWith(
      assignedCalls: state.assignedCalls
          .map((c) => c.id == callId ? c.copyWith(status: const CallStatusAccepted()) : c)
          .toList(),
      activeCall: targetCall.copyWith(status: const CallStatusAccepted()),
      newCallPopup: null,
      driverStatus: const DriverStatusAccepted(),  // ENUMS.md §2 (추가된 9종 포함)
    );
  } else {
    state = state.copyWith(newCallPopup: null);
  }

  try {
    final (provinceId, cityId, officeId) = _getDriverLocationInfo();
    final driverId = auth.currentUser?.uid;
    if (driverId == null) throw StateError('User not logged in');

    final callRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('calls').doc(callId);

    final driverRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('designated_drivers').doc(driverId);

    // 2단계: Firestore 트랜잭션 (Kotlin line 427-452)
    await firestore.runTransaction((tx) async {
      final callSnap = await tx.get(callRef);
      if (!callSnap.exists) throw StateError('콜 문서를 찾을 수 없습니다.');

      final currentStatus = callSnap.data()?['status'] as String?;
      if (currentStatus != 'ASSIGNED') {
        throw StateError('CALL_NOT_ASSIGNABLE: current status is $currentStatus');
      }

      tx.update(callRef, {'status': 'ACCEPTED'});
      tx.update(driverRef, {'status': 'PREPARING'});
    });

    // 의제 11: Analytics 이벤트
    await analytics.logEvent(
      name: 'call_accepted',
      parameters: {
        'call_id': callId,
        'office_id': officeId,
        'platform': Platform.isIOS ? 'ios' : 'android',
      },
    );

    debugPrint('[acceptCall] 완료: $callId');
  } catch (e, st) {
    debugPrint('[acceptCall] 실패: $e');
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'acceptCall');

    // 3단계: UI 롤백 (Kotlin line 456-473)
    state = state.copyWith(
      assignedCalls: state.assignedCalls
          .map((c) => c.id == callId ? c.copyWith(status: const CallStatusAssigned()) : c)
          .toList(),
      activeCall: null,
      newCallPopup: state.assignedCalls.firstWhereOrNull((c) => c.id == callId),
      driverStatus: const DriverStatusAssigned(),
      errorMessage: e.toString(),
    );
  } finally {
    _isAccepting = false;
  }
}
```

#### 2.3.2 `rejectCall(String callId)` — 콜 거절

**Kotlin 원본** (`DriverViewModel.kt:477-518`):
- `performFirestoreUpdate` 래퍼 사용
- 단일 트랜잭션: call `ASSIGNED→WAITING` + `assignedDriverId=null` + `rejectedByDriver=uid` + driver `→WAITING`

```dart
Future<void> rejectCall(String callId) async {
  await _performFirestoreUpdate(() async {
    final (provinceId, cityId, officeId) = _getDriverLocationInfo();
    final driverId = auth.currentUser?.uid;
    if (driverId == null) throw StateError('User not logged in');

    final callRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('calls').doc(callId);

    final driverRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('designated_drivers').doc(driverId);

    final callUpdates = <String, Object?>{
      'status': 'WAITING',
      'assignedDriverId': null,
      'assignedDriverName': null,
      'assignedDriverPhone': null,
      'rejectedByDriver': driverId,
      'updatedAt': FieldValue.serverTimestamp(),
    };

    await firestore.runTransaction((tx) async {
      tx.update(callRef, callUpdates);
      tx.update(driverRef, {'status': 'WAITING'});
    });

    // UI 정리 (Kotlin line 508-515)
    state = state.copyWith(
      assignedCalls: state.assignedCalls.where((c) => c.id != callId).toList(),
      newCallPopup: null,
      activeCall: state.activeCall?.id == callId ? null : state.activeCall,
      driverStatus: const DriverStatusWaiting(),
    );

    await analytics.logEvent(
      name: 'call_rejected',
      parameters: {'call_id': callId, 'office_id': officeId},
    );
  });
}
```

#### 2.3.3 `startDriving({callId, departure, destination, waypoints, fare})` — 운행 시작

**Kotlin 원본** (`DriverViewModel.kt:580-638`):
- 낙관적 UI 업데이트 (activeCall.copy + `driverStatus=ON_TRIP`)
- `runTransaction`: call `IN_PROGRESS` + departure_set/destination_set/waypoints_set/fare_set/trip_summary + driver `ON_TRIP`
- snake_case Firestore 필드명 유지 (MODELS.md §1 참조)

```dart
Future<void> startDriving({
  required String callId,
  required String departure,
  required String destination,
  required String waypoints,
  required int fare,
}) async {
  await _performFirestoreUpdate(() async {
    final (provinceId, cityId, officeId) = _getDriverLocationInfo();
    final driverId = auth.currentUser?.uid;
    if (driverId == null) throw StateError('User not logged in');

    final tripSummary =
        '출발: $departure, 도착: $destination, 경유: ${waypoints.isEmpty ? "없음" : waypoints}, 요금: $fare 원';

    // 1단계: 낙관적 UI 업데이트 (Kotlin line 593-609)
    state = state.copyWith(
      activeCall: state.activeCall?.copyWith(
        status: const CallStatusInProgress(),
        departureSet: departure,
        destinationSet: destination,
        waypointsSet: waypoints,
        fareSet: fare,
        tripSummary: tripSummary,
      ),
      driverStatus: const DriverStatusOnTrip(),
      assignedCalls: state.assignedCalls
          .map((c) => c.id == callId ? c.copyWith(status: const CallStatusInProgress()) : c)
          .toList(),
    );

    // 2단계: Firestore 트랜잭션
    final callRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('calls').doc(callId);
    final driverRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('designated_drivers').doc(driverId);

    final callUpdates = <String, Object?>{
      'status': 'IN_PROGRESS',
      'departure_set': departure,         // ⚠️ snake_case Firestore 호환 보존
      'destination_set': destination,
      'waypoints_set': waypoints,
      'fare_set': fare,
      'trip_summary': tripSummary,
      'updatedAt': FieldValue.serverTimestamp(),
    };

    await firestore.runTransaction((tx) async {
      tx.update(callRef, callUpdates);
      tx.update(driverRef, {'status': 'ON_TRIP'});
    });

    await analytics.logEvent(
      name: 'trip_started',
      parameters: {'call_id': callId, 'fare': fare},
    );
  });
}
```

#### 2.3.4 `cancelTrip(String callId, {String cancelReason})` — 운행 취소

**Kotlin 원본** (`DriverViewModel.kt:526-565`):
- 단일 트랜잭션: call `CANCELLED_BY_DRIVER` + `assignedDriverId=null` + `cancelReason` + driver `WAITING`
- CLAUDE.md 3/24 변경: HOLD → CANCELLED_BY_DRIVER 확정 취소 (재배차 없음)

```dart
Future<void> cancelTrip(String callId, {String cancelReason = '운행취소'}) async {
  await _performFirestoreUpdate(() async {
    final (provinceId, cityId, officeId) = _getDriverLocationInfo();
    final driverId = auth.currentUser?.uid;
    if (driverId == null) throw StateError('User not logged in');

    final callRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('calls').doc(callId);
    final driverRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('designated_drivers').doc(driverId);

    final callUpdates = <String, Object?>{
      'status': 'CANCELLED_BY_DRIVER',    // CANCELED(관리자) vs CANCELLED_BY_DRIVER(기사) 구분
      'assignedDriverId': null,
      'assignedDriverName': null,
      'assignedDriverPhone': null,
      'cancelReason': cancelReason,
      'cancelledByDriver': true,
      'updatedAt': FieldValue.serverTimestamp(),
    };

    await firestore.runTransaction((tx) async {
      tx.update(callRef, callUpdates);
      tx.update(driverRef, {'status': 'WAITING'});
    });

    state = state.copyWith(
      activeCall: null,
      driverStatus: const DriverStatusWaiting(),
      isLoading: false,
      navigateToHistorySettlement: false,
    );

    await analytics.logEvent(
      name: 'trip_cancelled_by_driver',
      parameters: {'call_id': callId, 'reason': cancelReason},
    );
  });
}
```

#### 2.3.5 `completeCall(String callId)` — 운행 완료

**Kotlin 원본** (`DriverViewModel.kt:640-668`):
- 낙관적 UI 업데이트 (activeCall→null, callForSettlement 설정)
- **트랜잭션이 아닌 단일 update** (기사 상태 변경 없음, call.status=AWAITING_SETTLEMENT만)

```dart
Future<void> completeCall(String callId) async {
  await _performFirestoreUpdate(() async {
    final (provinceId, cityId, officeId) = _getDriverLocationInfo();

    // 1단계: 낙관적 UI 업데이트 (Kotlin line 643-657)
    final completedCall = state.activeCall?.copyWith(status: const CallStatusAwaitingSettlement());
    state = state.copyWith(
      activeCall: null,
      callForSettlement: completedCall,
      assignedCalls: state.assignedCalls
          .map((c) => c.id == callId
              ? c.copyWith(status: const CallStatusAwaitingSettlement())
              : c)
          .toList(),
      isLoading: false,
    );

    // 2단계: 단일 update (Kotlin line 660-665)
    final callRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('calls').doc(callId);
    await callRef.update({'status': 'AWAITING_SETTLEMENT'});
  });
}
```

#### 2.3.6 `confirmAndFinalizeTrip(...)` — 정산 확정

**Kotlin 원본** (`DriverViewModel.kt:670-808`):
- 콜 문서 조회 → isAppCustomer / phoneNumber 확인 (포인트 CF 적립 판정용)
- `runTransaction`: call COMPLETED + fare/paymentMethod/cashReceived/creditAmount/pointsUsed/finalFare/completedAt + driver WAITING
- 로컬 `_todaySettlement` 즉시 증분 업데이트 (SettlementCalc 공식 사용, MODELS.md §14-C)
- `_tripHistoryList` 맨 앞에 새 TripHistoryItem 삽입

```dart
Future<void> confirmAndFinalizeTrip({
  required String callId,
  required String paymentMethod,   // '현금' | '이체' | '외상' | '포인트' | '현금+포인트'
  required int fareToSet,
  required String tripSummaryToSet,
  int? cashAmount,
  int pointsToUse = 0,
}) async {
  state = state.copyWith(isLoading: true, errorMessage: null);
  try {
    final (provinceId, cityId, officeId) = _getDriverLocationInfo();
    final driverId = auth.currentUser?.uid;
    if (driverId == null) throw StateError('User not logged in');

    // 콜 정보 조회 (isAppCustomer 판정)
    final callDoc = await firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('calls').doc(callId)
        .get();

    final isAppCustomer = callDoc.data()?['isAppCustomer'] as bool? ?? false;
    final phoneNumber = callDoc.data()?['phoneNumber'] as String?;

    // tripData 생성 (Kotlin line 698-716)
    final tripData = <String, Object?>{
      'paymentMethod': paymentMethod,
      'status': 'COMPLETED',
      'fareFinal': fareToSet,
      'fare': fareToSet,                      // 고객앱 호환
      'tripSummaryFinal': tripSummaryToSet,
      'completedAt': FieldValue.serverTimestamp(),
      'pointsUsed': pointsToUse,
      'finalFare': fareToSet - pointsToUse,
    };

    if (paymentMethod == '현금' && cashAmount != null) {
      tripData['cashReceived'] = cashAmount;
    } else if (paymentMethod == '현금+포인트' && cashAmount != null) {
      tripData['cashReceived'] = cashAmount;
      final actualCredit = math.max(0, fareToSet - pointsToUse - cashAmount);
      tripData['creditAmount'] = actualCredit;
    } else if (paymentMethod == '외상' || paymentMethod == '이체') {
      tripData['creditAmount'] = fareToSet;
    }

    // 단일 트랜잭션: 콜 COMPLETED + 기사 WAITING
    final callRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('calls').doc(callId);
    final driverRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('designated_drivers').doc(driverId);

    await firestore.runTransaction((tx) async {
      tx.update(callRef, tripData);
      tx.update(driverRef, {'status': 'WAITING'});
    });

    // 로컬 정산 즉시 업데이트 (SettlementCalc 공식, Kotlin line 742-769)
    final ratio = ref.read(depositRatioProvider);  // SettlementNotifier 또는 별도 provider
    final newCashReceived = switch (paymentMethod) {
      '현금' => fareToSet,
      String p when p.startsWith('현금+') => cashAmount ?? 0,
      _ => 0,
    };
    final newOfficeDeposit = (fareToSet * ratio / 100).toInt();
    final newDriverShare = fareToSet - newOfficeDeposit;

    // _todaySettlement 증분 업데이트 (별도 Notifier로 분리 가능)
    ref.read(todaySettlementProvider.notifier).addTrip(
      fare: fareToSet,
      cashReceived: newCashReceived,
      driverShare: newDriverShare,
      pointsUsed: pointsToUse,
      ratio: ratio,
    );

    // TripHistoryItem 추가
    ref.read(tripHistoryProvider.notifier).addTrip(
      TripHistoryItem(
        tripNumber: ref.read(tripHistoryProvider).length + 1,
        customerName: callDoc.data()?['customerName'] as String? ?? '고객',
        departure: callDoc.data()?['departure_set'] as String? ?? '출발지',
        destination: callDoc.data()?['destination_set'] as String? ?? '도착지',
        fare: fareToSet,
        paymentMethod: paymentMethod,
        cashAmount: cashAmount,
        timestamp: DateTime.now().millisecondsSinceEpoch,
      ),
    );

    state = state.copyWith(
      activeCall: null,
      callForSettlement: null,
      driverStatus: const DriverStatusWaiting(),
      navigateToHistorySettlement: true,
      isLoading: false,
    );

    await analytics.logEvent(
      name: 'trip_completed',
      parameters: {
        'call_id': callId,
        'fare': fareToSet,
        'payment_method': paymentMethod,
        'is_app_customer': isAppCustomer,
      },
    );
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'confirmAndFinalizeTrip');
    state = state.copyWith(
      errorMessage: '정산 처리 중 오류: $e',
      isLoading: false,
    );
  }
}
```

#### 2.3.7 `handleCallCancelled(String callId)` — FCM `call_cancelled` 핸들러

**Kotlin 원본** (`DriverViewModel.kt:907-922`):
- assignedCalls에서 해당 콜 제거
- newCallPopup / activeCall 이 해당 콜이면 null
- `driverStatus = WAITING` (clearActive || clearPopup 시)
- `errorMessage = "고객이 콜을 취소했습니다"` (Snackbar 표시용)

```dart
void handleCallCancelled(String callId) {
  debugPrint('[handleCallCancelled] callId=$callId');
  final updatedCalls = state.assignedCalls.where((c) => c.id != callId).toList();
  final clearPopup = state.newCallPopup?.id == callId;
  final clearActive = state.activeCall?.id == callId;

  state = state.copyWith(
    assignedCalls: updatedCalls,
    newCallPopup: clearPopup ? null : state.newCallPopup,
    activeCall: clearActive ? null : state.activeCall,
    driverStatus:
        (clearActive || clearPopup) ? const DriverStatusWaiting() : state.driverStatus,
    errorMessage: '고객이 콜을 취소했습니다',
  );

  analytics.logEvent(
    name: 'call_cancelled_by_customer',
    parameters: {'call_id': callId},
  );
}
```

**호출처**: `FcmNotifier.onMessage` 라우팅에서 `messageType == 'call_cancelled'` 시 이 메서드 호출 (FCM.md 참조).

#### 2.3.8 `loadCurrentActiveCall()` — 앱 시작 시 1회 조회 (비용 최적화)

**Kotlin 원본** (`DriverViewModel.kt:238-326`):
- 리스너 아님, `.get().await()` 1회
- 기사 문서 status 조회 + ASSIGNED/ACCEPTED/IN_PROGRESS/AWAITING_SETTLEMENT `whereIn` 쿼리
- 결과 기반으로 `DriverScreenUiState` 복구

**CLAUDE.md Driver App 기록**: "앱 시작 시 1회 조회 `.get().await()` (리스너 아님, 비용 최적화 ~$414/월 절감)"

```dart
Future<void> loadCurrentActiveCall() async {
  try {
    state = state.copyWith(isLoading: true);
    final (provinceId, cityId, officeId) = _getDriverLocationInfo();
    final driverId = auth.currentUser?.uid;
    if (driverId == null) return;

    // 1. 기사 상태 조회 (1회)
    final driverDoc = await firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('designated_drivers').doc(driverId)
        .get();

    final driverStatusRaw = driverDoc.data()?['status'] as String? ?? 'OFFLINE';
    final driverStatus = DriverStatus.fromString(driverStatusRaw);

    // 2. 배정된 콜 조회 (whereIn 1회, Kotlin DriverViewModel.kt:257-270)
    final assignedCallsSnap = await firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('calls')
        .where('assignedDriverId', isEqualTo: driverId)
        .where('status', whereIn: ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'AWAITING_SETTLEMENT'])
        .get();

    final assignedCalls = assignedCallsSnap.docs
        .map((doc) {
          try {
            return CallInfo.fromFirestore(doc);
          } catch (_) {
            return null;
          }
        })
        .whereType<CallInfo>()
        .toList();

    // 3. UI 상태 업데이트 (Kotlin line 281-300)
    if (assignedCalls.isNotEmpty) {
      final activeCall = assignedCalls.firstWhereOrNull((c) =>
          c.status is CallStatusAccepted || c.status is CallStatusInProgress);
      final newCall = assignedCalls.firstWhereOrNull((c) => c.status is CallStatusAssigned);
      final settlementCall = assignedCalls.firstWhereOrNull((c) =>
          c.status is CallStatusAwaitingSettlement && !_handledSettlementIds.contains(c.id));

      state = state.copyWith(
        driverStatus: driverStatus,
        assignedCalls: assignedCalls,
        activeCall: activeCall,
        newCallPopup: newCall,
        callForSettlement: settlementCall,
        isLoading: false,
      );
    } else {
      state = state.copyWith(driverStatus: driverStatus, isLoading: false);
    }
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'loadCurrentActiveCall');
    state = state.copyWith(isLoading: false, errorMessage: '콜 복구 실패: $e');
  }
}
```

**중요**: 이 메서드는 **앱 resume / 로그인 완료 / FCM `callId` 전달 시**에만 호출. **Firestore 리스너로 전환 금지** (비용).

#### 2.3.9 `updateDriverStatus(DriverStatus newStatus)`

**Kotlin 원본** (`DriverViewModel.kt:846-866`):
- 낙관적 UI 업데이트 + `driverRef.update(FIELD_STATUS, newStatus.value)` 단일 update

```dart
Future<void> updateDriverStatus(DriverStatus newStatus) async {
  await _performFirestoreUpdate(() async {
    final (provinceId, cityId, officeId) = _getDriverLocationInfo();
    final driverId = auth.currentUser?.uid;
    if (driverId == null) throw StateError('User not logged in');

    state = state.copyWith(driverStatus: newStatus);

    await firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('designated_drivers').doc(driverId)
        .update({'status': newStatus.toJson()});
  });
}
```

#### 2.3.10 `refreshActiveCallStatus()` — onResume 재검증

**Kotlin 원본** (`DriverViewModel.kt:929-967`):
- 앱이 백그라운드→포그라운드 전환 시 현재 activeCall + assignedCalls 각각 Firestore 재조회
- 상태가 CANCELED / CANCELLED_BY_CUSTOMER / WAITING / 문서 부재 → `handleCallCancelled` 호출

```dart
Future<void> refreshActiveCallStatus() async {
  final callsToCheck = <String>[];
  if (state.activeCall != null) callsToCheck.add(state.activeCall!.id);
  for (final c in state.assignedCalls) {
    if (c.id != state.activeCall?.id) callsToCheck.add(c.id);
  }
  if (callsToCheck.isEmpty) return;

  try {
    final (provinceId, cityId, officeId) = _getDriverLocationInfo();
    for (final callId in callsToCheck) {
      try {
        final callDoc = await firestore
            .collection('provinces').doc(provinceId)
            .collection('cities').doc(cityId)
            .collection('offices').doc(officeId)
            .collection('calls').doc(callId)
            .get();
        final status = callDoc.data()?['status'] as String?;
        if (!callDoc.exists ||
            status == 'CANCELED' ||
            status == 'CANCELLED_BY_CUSTOMER' ||
            status == 'WAITING') {
          debugPrint('[refreshActive] 콜 $callId 상태=$status → 정리');
          handleCallCancelled(callId);
        }
      } catch (e) {
        debugPrint('[refreshActive] 콜 $callId 조회 실패: $e');
      }
    }
  } catch (e) {
    debugPrint('[refreshActive] 위치 정보 실패: $e');
  }
}
```

**호출처**: `AppLifecycleListener` `onResume` 또는 `WidgetsBindingObserver.didChangeAppLifecycleState == AppLifecycleState.resumed`.

#### 2.3.11 기타 작은 메서드들

**`dismissNewCallPopup()`** (`DriverViewModel.kt:889-901`): 현재 팝업 dismiss 후 다음 ASSIGNED 콜 팝업 표시.

```dart
void dismissNewCallPopup() {
  final currentPopupId = state.newCallPopup?.id;
  final nextCall = state.assignedCalls.firstWhereOrNull(
    (c) => c.id != currentPopupId && c.status is CallStatusAssigned,
  );
  state = state.copyWith(newCallPopup: nextCall);
}
```

**`dismissSettlementPopup()`** (`DriverViewModel.kt:969-976`): `callForSettlement=null` + `_handledSettlementIds`에 추가 (Kotlin은 SharedPreferences `popupPrefs` 저장).

```dart
Future<void> dismissSettlementPopup() async {
  final id = state.callForSettlement?.id;
  state = state.copyWith(callForSettlement: null);
  if (id != null) {
    _handledSettlementIds.add(id);
    await prefs.setStringList('handled_settlement_ids', _handledSettlementIds.toList());
  }
}
```

**`onNavigateToHomeHandled()` / `onNavigateToHistorySettlementHandled()` / `onErrorMessageHandled()`** (`DriverViewModel.kt:988-1002`): 일회성 네비게이션 플래그 리셋. Freezed `copyWith` 로 `false` / `null` 설정.

### 2.4 헬퍼 메서드

```dart
/// (provinceId, cityId, officeId) 튜플 반환 (Kotlin getDriverLocationInfo)
(String, String, String) _getDriverLocationInfo() {
  final provinceId = prefs.getString('pref_province_id');
  final cityId = prefs.getString('pref_city_id');
  final officeId = prefs.getString('pref_office_id');
  if (provinceId == null || cityId == null || officeId == null ||
      provinceId.isEmpty || cityId.isEmpty || officeId.isEmpty) {
    throw StateError('Province ID, City ID or Office ID is not set.');
  }
  return (provinceId, cityId, officeId);
}

/// Kotlin performFirestoreUpdate 래퍼 등가
Future<void> _performFirestoreUpdate(Future<void> Function() block) async {
  state = state.copyWith(errorMessage: null);
  try {
    await block();
  } catch (e, st) {
    debugPrint('[_performFirestoreUpdate] 에러: $e');
    await FirebaseCrashlytics.instance.recordError(e, st);
    state = state.copyWith(errorMessage: e.toString());
  }
}
```

### 2.5 FCM 토큰 등록

**Kotlin 원본** (`DriverViewModel.kt:1029-1049`):
```kotlin
fun setFcmToken(token: String) {
  // sharedPreferences의 provinceId/cityId/officeId 존재 여부로 분기
  if (all exist) registerFcmToken(token)
  else fcmTokenToRegister = token
}
```

```dart
Future<void> setFcmToken(String token) async {
  final provinceId = prefs.getString('pref_province_id');
  final cityId = prefs.getString('pref_city_id');
  final officeId = prefs.getString('pref_office_id');
  if (provinceId != null && cityId != null && officeId != null &&
      provinceId.isNotEmpty && cityId.isNotEmpty && officeId.isNotEmpty) {
    await _registerFcmToken(token);
  } else {
    _fcmTokenToRegister = token;
  }
}

Future<void> _registerFcmToken(String token) async {
  await _performFirestoreUpdate(() async {
    final driverId = auth.currentUser?.uid;
    if (driverId == null) throw StateError('User not logged in');
    final (provinceId, cityId, officeId) = _getDriverLocationInfo();

    await firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('designated_drivers').doc(driverId)
        .update({
      'fcmToken': token,
      'fcmTokenPlatform': Platform.isIOS ? 'ios' : 'android',  // 의제 5 신규
      'platform': Platform.isIOS ? 'ios' : 'android',          // 의제 11 신규
    });
    _fcmTokenToRegister = null;
  });
}
```

---

## 3. LoginNotifier

### 3.1 State

ENUMS.md/MODELS.md `LoginState` sealed union 사용 (`ui/login/LoginViewModel.kt:30-35` 매핑):

```dart
// lib/domain/state/login_state.dart (MODELS.md §14-D 재사용)

@freezed
class LoginState with _$LoginState {
  const factory LoginState.idle() = _Idle;
  const factory LoginState.loading() = _Loading;
  const factory LoginState.success({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String driverId,
    required bool needsTokenUpdate,
  }) = _Success;
  const factory LoginState.error(String message) = _Error;
}
```

### 3.2 Notifier

**Kotlin 원본** (`LoginViewModel.kt:37-292`, 약 ~260 LOC):
- `email`, `password`, `autoLogin` 상태 (Kotlin은 Compose mutableStateOf, Dart는 별도 입력 필드 + form state)
- `init { if (autoLogin && saved credentials) login() }`
- `login()`: Firebase Auth → pending_drivers 체크 → `collectionGroup("designated_drivers") where authUid==uid` → approvalStatus 검증 → SharedPreferences 저장 → FCM 토큰 등록 → `LoginState.Success`
- 오프라인 로그인 fallback: `attemptOfflineLogin()` — `SessionManager.currentSession` 조회
- `toggleAutoLogin` / `logout` / `resetLoginState`

```dart
// lib/features/auth/notifiers/login_notifier.dart

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class LoginFormState {
  final String email;
  final String password;
  final bool autoLogin;
  const LoginFormState({this.email = '', this.password = '', this.autoLogin = false});
  LoginFormState copyWith({String? email, String? password, bool? autoLogin}) =>
      LoginFormState(
        email: email ?? this.email,
        password: password ?? this.password,
        autoLogin: autoLogin ?? this.autoLogin,
      );
}

class LoginNotifier extends StateNotifier<LoginState> {
  LoginNotifier({
    required this.auth,
    required this.firestore,
    required this.prefs,
    required this.secureStorage,
    required this.messaging,
  }) : super(const LoginState.idle()) {
    _initAutoLogin();
  }

  final FirebaseAuth auth;
  final FirebaseFirestore firestore;
  final SharedPreferences prefs;
  final FlutterSecureStorage secureStorage;
  final FirebaseMessaging messaging;

  var _form = const LoginFormState();
  LoginFormState get form => _form;

  void setEmail(String v) => _form = _form.copyWith(email: v);
  void setPassword(String v) => _form = _form.copyWith(password: v);
  void toggleAutoLogin(bool v) {
    _form = _form.copyWith(autoLogin: v);
    // 의제 9: autoLogin 플래그를 flutter_secure_storage에 저장
    secureStorage.write(key: 'auto_login', value: v.toString());
  }

  /// 의제 9: Firebase Auth currentUser 자동 상속 + 자격증명 로드
  Future<void> _initAutoLogin() async {
    // 1) Firebase Auth 세션이 살아있으면 그대로 사용 (재로그인 없이)
    final currentUser = auth.currentUser;
    if (currentUser != null) {
      await _afterLoginFlow(currentUser.uid, emailFallback: currentUser.email ?? '');
      return;
    }

    // 2) 세션 만료된 경우 — 마지막 이메일 복원 (AUTH.md 참조)
    final savedEmail = await secureStorage.read(key: 'last_email');
    if (savedEmail != null) {
      _form = _form.copyWith(email: savedEmail);
    }

    // 3) autoLogin 플래그 + 자격증명 체크 (MVP는 Kotlin 이관 안 함 — AUTH.md 옵션 B)
    final autoLoginStr = await secureStorage.read(key: 'auto_login');
    _form = _form.copyWith(autoLogin: autoLoginStr == 'true');

    // ⚠️ Kotlin은 여기서 saved credentials로 자동 login() 호출 (LoginViewModel.kt:58-66)
    // Flutter MVP: 비밀번호 자동 저장 금지 — 사용자가 명시 입력 (의제 9 결정)
  }

  Future<void> login() async {
    if (_form.email.isEmpty || _form.password.isEmpty) {
      state = const LoginState.error('이메일(또는 전화번호)과 비밀번호를 모두 입력해주세요.');
      return;
    }
    state = const LoginState.loading();

    try {
      // 1. 온라인 로그인
      final credential = await auth.signInWithEmailAndPassword(
        email: _form.email,
        password: _form.password,
      );
      final userId = credential.user?.uid;
      if (userId == null) {
        state = const LoginState.error('로그인 처리 중 오류가 발생했습니다. (UID 누락)');
        return;
      }

      await _afterLoginFlow(userId, emailFallback: _form.email);
    } on FirebaseAuthException catch (e) {
      // 2. 네트워크 오류 → 오프라인 로그인 시도 (AUTH.md 옵션 B)
      if (_isNetworkError(e)) {
        await _attemptOfflineLogin();
      } else {
        state = LoginState.error(e.message ?? '로그인에 실패했습니다.');
      }
    } catch (e) {
      state = LoginState.error('로그인 실패: $e');
    }
  }

  Future<void> _afterLoginFlow(String userId, {required String emailFallback}) async {
    // pending_drivers 체크 (Kotlin LoginViewModel.kt:154-168)
    final pendingDoc = await firestore.collection('pending_drivers').doc(userId).get();
    if (pendingDoc.exists) {
      state = const LoginState.error('관리자 승인 대기 중인 계정입니다.');
      await auth.signOut();
      return;
    }

    // collectionGroup 쿼리 (Kotlin LoginViewModel.kt:170-267)
    final querySnap = await firestore
        .collectionGroup('designated_drivers')
        .where('authUid', isEqualTo: userId)
        .limit(1)
        .get();

    if (querySnap.docs.isEmpty) {
      state = const LoginState.error('등록되지 않은 기사 계정입니다.');
      await auth.signOut();
      return;
    }

    final doc = querySnap.docs.first;
    final data = doc.data();
    final approvalStatus = data['approvalStatus'] as String?;
    final driverName = data['name'] as String? ?? '기사님';
    final serverFcmToken = data['fcmToken'] as String?;

    if (approvalStatus != 'APPROVED') {
      final msg = switch (approvalStatus) {
        'PENDING' => '관리자 승인 대기 중인 계정입니다.',
        'REJECTED' => '가입이 거절된 계정입니다. 관리자에게 문의하세요.',
        _ => '계정 상태를 확인할 수 없습니다.',
      };
      state = LoginState.error(msg);
      await auth.signOut();
      return;
    }

    // 경로 파싱 (Firestore 경로: provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid})
    final refPath = doc.reference.path;
    final (provinceId, cityId, officeId) = _parsePathSegments(refPath);
    if (provinceId.isEmpty || cityId.isEmpty || officeId.isEmpty) {
      state = const LoginState.error('기사 정보(지역/사무실 ID)가 누락되었습니다.');
      await auth.signOut();
      return;
    }

    // SharedPreferences 저장 (Kotlin line 200-206)
    await prefs.setString('pref_province_id', provinceId);
    await prefs.setString('pref_city_id', cityId);
    await prefs.setString('pref_office_id', officeId);
    await prefs.setString('driverId', userId);

    // 의제 9: 이메일 사전 채움용 마지막 이메일 저장
    await secureStorage.write(key: 'last_email', value: emailFallback);

    // FCM 토큰 갱신 필요 여부 확인 (Kotlin line 215-253)
    final localFcmToken = await messaging.getToken();
    final needsUpdate = serverFcmToken == null ||
        serverFcmToken.isEmpty ||
        serverFcmToken != localFcmToken;

    // 기사 상태 ONLINE + lastLoginTime 업데이트 (Kotlin line 237-252)
    await doc.reference.update({
      'status': 'ONLINE',
      'lastLoginTime': Timestamp.now(),
    });

    state = LoginState.success(
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
      driverId: userId,
      needsTokenUpdate: needsUpdate,
    );
  }

  (String, String, String) _parsePathSegments(String path) {
    // Format: provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid}
    final segments = path.split('/');
    if (segments.length < 7) return ('', '', '');
    return (segments[1], segments[3], segments[5]);
  }

  bool _isNetworkError(FirebaseAuthException e) {
    final msg = (e.message ?? '').toLowerCase();
    return msg.contains('network') ||
        msg.contains('timeout') ||
        msg.contains('unable to resolve host') ||
        e.code == 'network-request-failed';
  }

  /// Kotlin attemptOfflineLogin (LoginViewModel.kt:132-152)
  Future<void> _attemptOfflineLogin() async {
    // SessionManager 등가 — shared_preferences에서 마지막 세션 복원
    final session = await SessionStorage().load();
    if (session != null && session.email == _form.email && !session.isExpired) {
      state = LoginState.success(
        provinceId: session.provinceId,
        cityId: session.cityId,
        officeId: session.officeId,
        driverId: session.driverId,
        needsTokenUpdate: false,
      );
    } else {
      state = const LoginState.error(
          '오프라인 상태에서는 이전에 로그인한 계정만 사용할 수 있습니다.');
    }
  }

  /// 의제 9: 비밀번호 찾기
  Future<void> sendPasswordResetEmail(String email) async {
    try {
      await auth.sendPasswordResetEmail(email: email);
    } catch (e) {
      state = LoginState.error('비밀번호 재설정 이메일 전송 실패: $e');
    }
  }

  Future<void> logout() async {
    await auth.signOut();
    await SessionStorage().clear();
    // FCM pending 토큰 제거 (Kotlin line 287-289)
    await prefs.remove('pref_pending_fcm_token');
    state = const LoginState.idle();
  }

  void resetLoginState() => state = const LoginState.idle();
}

final loginProvider = StateNotifierProvider<LoginNotifier, LoginState>((ref) {
  return LoginNotifier(
    auth: ref.watch(firebaseAuthProvider),
    firestore: ref.watch(firestoreProvider),
    prefs: ref.watch(sharedPreferencesProvider),
    secureStorage: ref.watch(secureStorageProvider),
    messaging: ref.watch(firebaseMessagingProvider),
  );
});
```

**상세 구현 가이드**: `AUTH.md` 참조.

---

## 4. CarryOverNotifier (이월금 실시간 리스너)

### 4.1 State

`DriverCarryOver?` (MODELS.md §12) + `DailySettlementStatus` (ENUMS.md §3 수정본 4종).

```dart
@freezed
class CarryOverState with _$CarryOverState {
  const factory CarryOverState({
    DriverCarryOver? carryOver,
    @Default(DailySettlementStatusUnknown('WORKING')) DailySettlementStatus dailySettlementStatus,
  }) = _CarryOverState;
}
```

### 4.2 Notifier

**Kotlin 원본** (`DriverViewModel.kt:1261-1308`):
- **유일한 Firestore 실시간 리스너** (`driverRef.addSnapshotListener`)
- `snapshot.get("carryOver")` Map → `DriverCarryOver.fromMap`
- `snapshot.get("dailySettlement")` Map → `DriverDailySettlement.fromMap`
- `dailySettlement.status == PENDING_CONFIRM` 이면 `calculatedCarryOver` 사용 (통합 제출 시나리오)
- `status == TRANSFERRED` 또는 (balance != 0 && status != SETTLED) 시 표시

```dart
// lib/features/settlement/notifiers/carry_over_notifier.dart

class CarryOverNotifier extends StateNotifier<CarryOverState> {
  CarryOverNotifier({
    required this.firestore,
    required this.auth,
    required this.prefs,
  }) : super(const CarryOverState()) {
    _subscribeAuth();
  }

  final FirebaseFirestore firestore;
  final FirebaseAuth auth;
  final SharedPreferences prefs;

  StreamSubscription<User?>? _authSub;
  StreamSubscription<DocumentSnapshot<Map<String, dynamic>>>? _carryOverSub;

  void _subscribeAuth() {
    _authSub = auth.authStateChanges().listen((user) {
      _stopListener();
      if (user != null) _startListener(user.uid);
    });
  }

  void _startListener(String driverId) {
    final provinceId = prefs.getString('pref_province_id');
    final cityId = prefs.getString('pref_city_id');
    final officeId = prefs.getString('pref_office_id');
    if (provinceId == null || cityId == null || officeId == null) return;

    final driverRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('designated_drivers').doc(driverId);

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

        // 일일 정산 상태 업데이트
        final newDailyStatus = dailySettlement.status;

        // PENDING_CONFIRM 시 calculatedCarryOver 사용 (Kotlin line 1286-1294)
        final effectiveCarryOver =
            newDailyStatus is DailySettlementStatusPendingConfirm
                ? carryOver.copyWith(balance: dailySettlement.calculatedCarryOver)
                : carryOver;

        // 표시 조건 (Kotlin line 1297-1303)
        final shouldShow = effectiveCarryOver.status is CarryOverStatusTransferred ||
            (effectiveCarryOver.balance != 0 &&
                effectiveCarryOver.status is! CarryOverStatusSettled);

        state = state.copyWith(
          carryOver: shouldShow ? effectiveCarryOver : null,
          dailySettlementStatus: newDailyStatus,
        );
      },
      onError: (e, st) {
        debugPrint('[CarryOver listener] error: $e');
        FirebaseCrashlytics.instance.recordError(e, st, reason: 'carryOverListener');
      },
    );
  }

  void _stopListener() {
    _carryOverSub?.cancel();
    _carryOverSub = null;
  }

  @override
  void dispose() {
    _authSub?.cancel();
    _stopListener();
    super.dispose();
  }

  /// 수령확인 (Kotlin DriverViewModel.kt:1314-1342)
  Future<(bool, String)> confirmReceive() async {
    try {
      final driverId = auth.currentUser?.uid;
      if (driverId == null) return (false, '로그인 필요');

      final provinceId = prefs.getString('pref_province_id');
      final cityId = prefs.getString('pref_city_id');
      final officeId = prefs.getString('pref_office_id');
      if (provinceId == null || cityId == null || officeId == null) {
        return (false, '사무실 정보 누락');
      }

      await firestore
          .collection('provinces').doc(provinceId)
          .collection('cities').doc(cityId)
          .collection('offices').doc(officeId)
          .collection('designated_drivers').doc(driverId)
          .update({
        'carryOver.balance': 0,
        'carryOver.status': 'SETTLED',
        'carryOver.transferredAt': null,
        'carryOver.transferredBy': null,
        'carryOver.lastUpdatedAt': Timestamp.now(),
      });

      return (true, '수령 완료');
    } catch (e, st) {
      await FirebaseCrashlytics.instance.recordError(e, st, reason: 'confirmReceiveCarryOver');
      return (false, '수령 확인 실패: $e');
    }
  }
}

final carryOverProvider =
    StateNotifierProvider<CarryOverNotifier, CarryOverState>((ref) {
  return CarryOverNotifier(
    firestore: ref.watch(firestoreProvider),
    auth: ref.watch(firebaseAuthProvider),
    prefs: ref.watch(sharedPreferencesProvider),
  );
});
```

---

## 5. SettlementNotifier

### 5.1 State

- `_todaySettlement: TodaySettlement` (MODELS.md §14-C)
- `_tripHistoryList: List<TripHistoryItem>` (MODELS.md §14-C)
- `_depositRatio: int` (Firestore office.depositRatio)
- `_lastClearedMillis: int` (마지막 마감 시점)
- `_isSubmittingSettlement: bool` (중복 클릭 방지)

```dart
@freezed
class SettlementState with _$SettlementState {
  const factory SettlementState({
    @Default(TodaySettlement()) TodaySettlement todaySettlement,
    @Default(<TripHistoryItem>[]) List<TripHistoryItem> tripHistory,
    @Default(60) int depositRatio,
    @Default(0) int lastClearedMillis,
    @Default(false) bool isSubmittingSettlement,
  }) = _SettlementState;
}
```

### 5.2 Notifier 핵심 메서드

**`loadSettlementData()`** — 기사 문서에서 depositRatio/lastClearedMillis 로드 + calls 기반 오늘 정산 집계.

**`addTrip(...)`** — `confirmAndFinalizeTrip` 내부에서 호출 (§2.3.6 참조). `TodaySettlement` 증분 계산 + `TripHistoryItem` 앞에 삽입.

**`submitDailySettlement(int realDeposit)`** — 업무마감:
- Kotlin 원본 (`DriverViewModel.kt:1348-1600`, ~250 LOC) — 전체 완독 필요하나 본 문서에선 **시그니처 + 핵심 로직 골격**만 제시, 상세는 FIRESTORE.md로 위임
- **통합 제출 (isIntegration)**: 이전 dailySettlement.status == PENDING_CONFIRM 시 원본 값 보존 (`originalCarryOver/originalTripCount/originalTotalFare/originalRealDeposit`)
- `SettlementCalc` 공식 (MODELS.md §SettlementCalc 참조) — `ios/SHARED_LOGIC.md` §4

```dart
Future<(bool, String)> submitDailySettlement(int realDeposit) async {
  if (state.isSubmittingSettlement) return (false, '이미 제출 중');
  state = state.copyWith(isSubmittingSettlement: true);

  try {
    final driverId = ref.read(firebaseAuthProvider).currentUser?.uid;
    if (driverId == null) return (false, '로그인 필요');

    final provinceId = prefs.getString('pref_province_id')!;
    final cityId = prefs.getString('pref_city_id')!;
    final officeId = prefs.getString('pref_office_id')!;

    final driverRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('designated_drivers').doc(driverId);

    final driverDoc = await driverRef.get();
    final existingMap = driverDoc.data()?['dailySettlement'] as Map<String, dynamic>?;
    final prevSettlement = existingMap != null
        ? DriverDailySettlement.fromJson(existingMap)
        : const DriverDailySettlement();

    final isIntegration = prevSettlement.status is DailySettlementStatusPendingConfirm;

    final today = state.todaySettlement;
    final ratio = state.depositRatio;

    // 통합 시 원본 carryOver.balance 사용
    final carryOverState = ref.read(carryOverProvider);
    final originalCarryOver = isIntegration
        ? prevSettlement.originalCarryOver
        : (carryOverState.carryOver?.balance ?? 0);

    // SettlementCalc 공식 (MODELS.md §SettlementCalc)
    final officeDeposit = (today.totalFare * ratio / 100).toInt();
    final finalDeposit = officeDeposit - today.totalCredit;
    final settlementDiff = realDeposit - finalDeposit;
    final calculatedCarryOver = originalCarryOver - finalDeposit + realDeposit;

    // DriverDailySettlement 생성
    final newSettlement = DriverDailySettlement(
      date: DateFormat('yyyy-MM-dd').format(DateTime.now()),
      finalDeposit: finalDeposit,
      realDeposit: realDeposit,
      settlementDiff: settlementDiff,
      totalFare: today.totalFare,
      totalCredit: today.totalCredit,
      tripCount: today.tripCount,
      status: const DailySettlementStatusPendingConfirm(),
      submittedAt: DateTime.now(),
      calculatedCarryOver: calculatedCarryOver,
      originalCarryOver: isIntegration ? prevSettlement.originalCarryOver : originalCarryOver,
      originalTripCount: isIntegration ? prevSettlement.originalTripCount : 0,
      originalTotalFare: isIntegration ? prevSettlement.originalTotalFare : 0,
      originalRealDeposit: isIntegration ? prevSettlement.originalRealDeposit : 0,
    );

    await driverRef.update({'dailySettlement': newSettlement.toJson()});

    await analytics.logEvent(
      name: 'settlement_submitted',
      parameters: {
        'is_integration': isIntegration,
        'trip_count': today.tripCount,
        'real_deposit': realDeposit,
      },
    );

    return (true, isIntegration ? '통합 정산 제출 완료' : '정산 제출 완료');
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'submitDailySettlement');
    return (false, '제출 실패: $e');
  } finally {
    state = state.copyWith(isSubmittingSettlement: false);
  }
}
```

### 5.3 매니저 반려 처리

**Kotlin 원본** (`MyFirebaseMessagingService.kt:218-228`): FCM `SETTLEMENT_REJECTED` 수신 시 LocalBroadcast + 알림 "재제출해주세요".

**Flutter 매핑**: `FcmNotifier`에서 `SETTLEMENT_REJECTED` 수신 → `SettlementNotifier.onManagerRejected()` 호출:

```dart
void onManagerRejected() {
  // dailySettlement 상태가 Firestore 리스너(CarryOverNotifier)로 자동 REJECTED로 갱신됨
  // 여기서는 UI 알림만 처리 (Snackbar / Navigation)
  // 구체 UX는 SCREENS.md SettlementScreen에서
}
```

---

## 6. SignUpNotifier

### 6.1 State 골격 (Kotlin SignUpViewModel 미Read — 시그니처만 제시)

**Kotlin 원본** (`ui/login/SignUpViewModel.kt`): 지역/도시/사무실 드롭다운 + 이름/전화/이메일/비밀번호 입력 + `createUserWithEmailAndPassword` + `pending_drivers/{uid}` 생성.

MODELS.md §14-D 참조한 `SignUpState` sealed union:

```dart
@freezed
class SignUpState with _$SignUpState {
  const factory SignUpState.idle() = _Idle;
  const factory SignUpState.loading() = _Loading;
  const factory SignUpState.success() = _Success;
  const factory SignUpState.error(String message) = _Error;
}

@freezed
class ProvinceItem with _$ProvinceItem {
  const factory ProvinceItem({required String id, required String name}) = _ProvinceItem;
  factory ProvinceItem.fromJson(Map<String, Object?> json) => _$ProvinceItemFromJson(json);
}

@freezed
class CityItem with _$CityItem {
  const factory CityItem({required String id, required String name}) = _CityItem;
  factory CityItem.fromJson(Map<String, Object?> json) => _$CityItemFromJson(json);
}

@freezed
class OfficeItem with _$OfficeItem {
  const factory OfficeItem({required String id, required String name}) = _OfficeItem;
  factory OfficeItem.fromJson(Map<String, Object?> json) => _$OfficeItemFromJson(json);
}
```

### 6.2 Notifier 골격 (SCREENS.md 에서 상세)

```dart
class SignUpNotifier extends StateNotifier<SignUpState> {
  SignUpNotifier({required this.auth, required this.firestore})
      : super(const SignUpState.idle());

  final FirebaseAuth auth;
  final FirebaseFirestore firestore;

  Future<List<ProvinceItem>> loadProvinces() async { /* ... */ }
  Future<List<CityItem>> loadCities(String provinceId) async { /* ... */ }
  Future<List<OfficeItem>> loadOffices(String provinceId, String cityId) async { /* ... */ }

  Future<void> signUp({
    required String email,
    required String password,
    required String name,
    required String phone,
    required String provinceId,
    required String cityId,
    required String officeId,
  }) async {
    state = const SignUpState.loading();
    try {
      final credential = await auth.createUserWithEmailAndPassword(
        email: email, password: password,
      );
      final uid = credential.user?.uid;
      if (uid == null) throw StateError('UID 누락');

      await firestore.collection('pending_drivers').doc(uid).set({
        'authUid': uid,
        'email': email,
        'name': name,
        'phone': phone,
        'provinceId': provinceId,
        'cityId': cityId,
        'officeId': officeId,
        'approvalStatus': 'PENDING',
        'createdAt': FieldValue.serverTimestamp(),
      });

      state = const SignUpState.success();
    } catch (e) {
      state = SignUpState.error('회원가입 실패: $e');
    }
  }
}

final signUpProvider =
    StateNotifierProvider.autoDispose<SignUpNotifier, SignUpState>((ref) {
  return SignUpNotifier(
    auth: ref.watch(firebaseAuthProvider),
    firestore: ref.watch(firestoreProvider),
  );
});
```

---

## 7. PresenceNotifier

### 7.1 기존 `driver_app_flutter/lib/services/presence_service.dart` 재사용 (의제 8)

의제 8 결정: **옵션 C — Kotlin `PresenceManager` 1:1 포팅 이미 완료 + `flutter_foreground_task` iOS no-op**. 본 Phase 6에서는 **신규 작업 없음**.

`PresenceService` → `PresenceNotifier` 전환은 Phase 6 Week 2 이후 (기존 서비스가 Service로 충분, Notifier 래핑은 UI 배너 표시용):

```dart
// lib/features/presence/notifiers/presence_notifier.dart

@freezed
class PresenceState with _$PresenceState {
  const factory PresenceState({
    @Default(true) bool isConnected,           // Realtime DB .info/connected
    @Default(PresenceStatusOffline()) PresenceStatus status,
  }) = _PresenceState;
}

// ENUMS.md 에 `PresenceStatus` 신규 추가 필요 (online/background/offline 3종)
// (PresenceManager.kt:37-41 Status enum 1:1 매핑)

class PresenceNotifier extends StateNotifier<PresenceState> {
  PresenceNotifier({required this.presenceService})
      : super(const PresenceState()) {
    _subscribeConnection();
    _subscribeAppLifecycle();
  }

  final PresenceService presenceService;  // 기존 서비스

  void _subscribeConnection() {
    presenceService.isConnectedStream.listen((connected) {
      state = state.copyWith(isConnected: connected);
    });
  }

  void _subscribeAppLifecycle() {
    // WidgetsBindingObserver 사용 또는 AppLifecycleListener
    // resumed → onAppForeground (ONLINE)
    // paused → onAppBackground (BACKGROUND)
  }
}

final presenceProvider =
    StateNotifierProvider<PresenceNotifier, PresenceState>((ref) {
  return PresenceNotifier(
    presenceService: ref.watch(presenceServiceProvider),
  );
});
```

**ENUMS.md 보완 요청 (추가 1건)**:

```dart
// lib/domain/enums/presence_status.dart (신규)

sealed class PresenceStatus {
  const PresenceStatus();
  static PresenceStatus fromString(String v) => switch (v) {
    'online' => const PresenceStatusOnline(),
    'background' => const PresenceStatusBackground(),
    'offline' => const PresenceStatusOffline(),
    _ => PresenceStatusUnknown(v),
  };
  String toJson();
}

class PresenceStatusOnline extends PresenceStatus {
  const PresenceStatusOnline();
  @override String toJson() => 'online';
}

class PresenceStatusBackground extends PresenceStatus {
  const PresenceStatusBackground();
  @override String toJson() => 'background';
}

class PresenceStatusOffline extends PresenceStatus {
  const PresenceStatusOffline();
  @override String toJson() => 'offline';
}

class PresenceStatusUnknown extends PresenceStatus {
  final String rawValue;
  const PresenceStatusUnknown(this.rawValue);
  @override String toJson() => rawValue;
}
```

---

## 8. FcmNotifier (Service)

### 8.1 역할

FCM 6종 메시지 수신 → 해당 Notifier 메서드 라우팅. **Notifier는 아니지만 Provider로 노출되어 다른 Notifier와 ref.read 상호 참조**.

**Kotlin 원본** (`MyFirebaseMessagingService.kt:122-242` 전수): 앞서 검토 포인트에서 코드 확인 완료.

상세 구현은 **FCM.md (flutter-expert 담당)** 위임. 본 문서는 **라우팅 스펙만** 기술.

### 8.2 라우팅 계약

```dart
// lib/services/fcm_routing_service.dart (FCM.md에서 상세)

class FcmRoutingService {
  FcmRoutingService(this._ref);
  final Ref _ref;

  void onMessage(RemoteMessage message) {
    final type = message.data['type'] as String?;
    final callId = message.data['callId'] as String?;
    final notificationId = message.data['notificationId'] as String?;

    // Delivery ACK (notificationId 있을 때만, 포그라운드 수신 시도)
    if (notificationId != null) {
      _ref.read(fcmAckServiceProvider).acknowledge(notificationId);
    }

    switch (type) {
      case 'call_assigned':
        if (callId != null) {
          _ref.read(driverWorkflowProvider.notifier).loadCurrentActiveCall();
        }
      case 'call_cancelled':
        if (callId != null) {
          _ref.read(driverWorkflowProvider.notifier).handleCallCancelled(callId);
        }
      case 'SETTLEMENT_FINALIZED':
        // 업무마감 대시보드 갱신 (로컬 알림은 별도)
        break;
      case 'SETTLEMENT_CONFIRMED':
        // dailySettlementStatus가 carryOverListener로 자동 갱신됨
        // Snackbar "퇴근할 수 있습니다"
        break;
      case 'SETTLEMENT_REJECTED':
        _ref.read(settlementProvider.notifier).onManagerRejected();
      case 'CARRYOVER_TRANSFERRED':
        // carryOverListener가 자동 갱신
        break;
    }
  }
}
```

---

## 9. CallDetailsNotifier (autoDispose.family)

### 9.1 Kotlin 원본

`DriverViewModel.kt:329` `_callDetailsState: MutableStateFlow<CallInfo?>`. 호출 방식: `loadCallDetails(callId)` → Firestore 1회 조회.

### 9.2 Flutter 매핑

```dart
// lib/features/call/notifiers/call_details_notifier.dart

class CallDetailsNotifier extends StateNotifier<AsyncValue<CallInfo>> {
  CallDetailsNotifier({
    required this.firestore,
    required this.prefs,
    required this.callId,
  }) : super(const AsyncLoading()) {
    _load();
  }

  final FirebaseFirestore firestore;
  final SharedPreferences prefs;
  final String callId;

  Future<void> _load() async {
    try {
      final provinceId = prefs.getString('pref_province_id')!;
      final cityId = prefs.getString('pref_city_id')!;
      final officeId = prefs.getString('pref_office_id')!;
      final doc = await firestore
          .collection('provinces').doc(provinceId)
          .collection('cities').doc(cityId)
          .collection('offices').doc(officeId)
          .collection('calls').doc(callId)
          .get();
      if (!doc.exists) {
        state = AsyncError(StateError('콜 문서 없음'), StackTrace.current);
        return;
      }
      state = AsyncData(CallInfo.fromFirestore(doc));
    } catch (e, st) {
      state = AsyncError(e, st);
    }
  }

  Future<void> refresh() => _load();
}

final callDetailsProvider = StateNotifierProvider.autoDispose
    .family<CallDetailsNotifier, AsyncValue<CallInfo>, String>((ref, callId) {
  return CallDetailsNotifier(
    firestore: ref.watch(firestoreProvider),
    prefs: ref.watch(sharedPreferencesProvider),
    callId: callId,
  );
});
```

**사용**:
```dart
class CallDetailsScreen extends ConsumerWidget {
  final String callId;
  const CallDetailsScreen({required this.callId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(callDetailsProvider(callId));
    return async.when(
      loading: () => const CircularProgressIndicator(),
      error: (e, _) => Text('오류: $e'),
      data: (call) => CallDetailView(call: call),
    );
  }
}
```

---

## 10. 의제 11 측정 이벤트 통합 (Analytics logEvent 위치)

각 Notifier에서 호출하는 Analytics 이벤트 일람:

| Notifier | 메서드 | logEvent name | parameters |
|---------|--------|--------------|-----------|
| DriverWorkflow | acceptCall | `call_accepted` | call_id, office_id, platform |
| DriverWorkflow | rejectCall | `call_rejected` | call_id, office_id |
| DriverWorkflow | startDriving | `trip_started` | call_id, fare |
| DriverWorkflow | cancelTrip | `trip_cancelled_by_driver` | call_id, reason |
| DriverWorkflow | confirmAndFinalizeTrip | `trip_completed` | call_id, fare, payment_method, is_app_customer |
| DriverWorkflow | handleCallCancelled | `call_cancelled_by_customer` | call_id |
| Login | _afterLoginFlow | `login_success` | method (`online` / `offline`) |
| SignUp | signUp | `signup_request` | office_id |
| Settlement | submitDailySettlement | `settlement_submitted` | is_integration, trip_count, real_deposit |
| CarryOver | confirmReceive | `carryover_received` | — |
| Fcm | onMessage | `fcm_received` | type, call_id |
| Fcm | acknowledge | `fcm_delivery_ack` | notification_id |

**수락률 측정 (R1 LOCKSCREEN 발동 기준)**:
- `call_assigned`(FCM 수신) → `call_accepted` 대응 이벤트 비율
- `platform: ios | android` 차원 분리로 집계
- CF `acceptanceEvents` 컬렉션 + Firestore 집계는 **서버 측 별도 과제** (OVERVIEW.md Week 2~3)

---

## 11. 테스트 전략

### 11.1 단위 테스트 (`test/features/driver/notifiers/`)

```dart
// test/features/driver/notifiers/driver_workflow_notifier_test.dart

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
// ...

class MockFirebaseAuth extends Mock implements FirebaseAuth {}

void main() {
  group('DriverWorkflowNotifier.acceptCall', () {
    late ProviderContainer container;
    late FakeFirebaseFirestore firestore;
    late MockFirebaseAuth auth;
    late MockUser user;

    setUp(() {
      firestore = FakeFirebaseFirestore();
      auth = MockFirebaseAuth();
      user = MockUser();
      when(() => user.uid).thenReturn('driver_uid_1');
      when(() => auth.currentUser).thenReturn(user);

      container = ProviderContainer(overrides: [
        firestoreProvider.overrideWithValue(firestore),
        firebaseAuthProvider.overrideWithValue(auth),
        // ...
      ]);
    });

    tearDown(() => container.dispose());

    test('낙관적 UI 업데이트 후 트랜잭션 성공', () async {
      // 1. Firestore 테스트 데이터
      await firestore.collection('provinces').doc('gyeonggi')
          .collection('cities').doc('hongchun')
          .collection('offices').doc('office1')
          .collection('calls').doc('call1')
          .set({
        'id': 'call1',
        'status': 'ASSIGNED',
        'assignedDriverId': 'driver_uid_1',
      });

      // 2. Notifier 호출
      final notifier = container.read(driverWorkflowProvider.notifier);
      await notifier.acceptCall('call1');

      // 3. 검증
      final state = container.read(driverWorkflowProvider);
      expect(state.activeCall?.status, isA<CallStatusAccepted>());
      expect(state.driverStatus, isA<DriverStatusAccepted>());
      expect(state.newCallPopup, isNull);

      // Firestore 상태도 검증
      final callDoc = await firestore
          .collection('provinces').doc('gyeonggi')
          .collection('cities').doc('hongchun')
          .collection('offices').doc('office1')
          .collection('calls').doc('call1').get();
      expect(callDoc.data()?['status'], 'ACCEPTED');
    });

    test('트랜잭션 실패 시 UI 롤백', () async {
      // 콜이 ASSIGNED 아닌 상태에서 acceptCall 시도 → CALL_NOT_ASSIGNABLE
      await firestore.collection('...').doc('call1').set({'status': 'COMPLETED'});

      final notifier = container.read(driverWorkflowProvider.notifier);
      await notifier.acceptCall('call1');

      final state = container.read(driverWorkflowProvider);
      expect(state.errorMessage, contains('CALL_NOT_ASSIGNABLE'));
      // 롤백: ACCEPTED → ASSIGNED
      expect(state.assignedCalls.firstOrNull?.status, isA<CallStatusAssigned>());
    });
  });
}
```

### 11.2 필수 테스트 케이스 (Phase 6 필수)

| Notifier | 메서드 | 최소 테스트 3종 |
|---------|--------|---------------|
| DriverWorkflow | acceptCall | 성공 / 롤백 / 중복 클릭 차단 |
| DriverWorkflow | rejectCall | 성공 / assignedCalls 제거 |
| DriverWorkflow | startDriving | snake_case 필드 저장 확인 / 트랜잭션 원자성 |
| DriverWorkflow | confirmAndFinalizeTrip | 5가지 결제방식 각각 creditAmount 계산 |
| DriverWorkflow | handleCallCancelled | activeCall/newCallPopup/assignedCalls 정리 |
| DriverWorkflow | loadCurrentActiveCall | whereIn 쿼리 결과 UI 반영 |
| Login | login | pending_drivers / approvalStatus 분기 |
| CarryOver | listener | PENDING_CONFIRM 시 calculatedCarryOver 사용 |
| Settlement | submitDailySettlement | isIntegration 감지 + 원본 값 보존 |

### 11.3 riverpod_test + mocktail

```yaml
# pubspec.yaml (dev_dependencies)

dev_dependencies:
  flutter_test:
    sdk: flutter
  mocktail: ^1.0.0
  fake_cloud_firestore: ^2.5.0    # cloud_firestore 5.x 호환 버전 확인 필요
  firebase_auth_mocks: ^0.13.0
  riverpod_test: ^1.0.0           # 존재 시 (커뮤니티 패키지 여부 확인)
```

### 11.4 통합 테스트

- `test/integration/driver_workflow_integration_test.dart`
- 실제 Firebase Emulator (`firebase emulators:start --only firestore,auth`)
- 콜 사이클 1순환 end-to-end 검증 (login → acceptCall → startDriving → completeCall → confirmAndFinalizeTrip → WAITING)

---

## 12. 마이그레이션 작업 순서 (Phase 6)

### Week 1
1. `lib/domain/state/` — UiState Freezed 생성 + build_runner 1회 실행
2. `lib/features/driver/notifiers/driver_workflow_notifier.dart` 작성 (acceptCall/rejectCall/startDriving/completeCall/confirmAndFinalizeTrip/cancelTrip/handleCallCancelled/loadCurrentActiveCall 8개 메서드)
3. 단위 테스트 10건 최소 (fake_cloud_firestore)

### Week 2
4. `CarryOverNotifier` + 리스너 + `confirmReceive`
5. `SettlementNotifier` + `submitDailySettlement` (통합 제출 로직 포함)
6. `LoginNotifier` (AUTH.md와 병행)
7. `SignUpNotifier` (SCREENS.md와 병행)

### Week 3
8. `PresenceNotifier` 래퍼 (기존 `presence_service.dart` 유지)
9. `FcmNotifier` 라우팅 (FCM.md 참조)
10. `CallDetailsNotifier.family`
11. 의제 11 Analytics/Crashlytics 각 메서드 통합

### Week 3~4
12. 통합 테스트 (Firebase Emulator)
13. TestFlight Internal 업로드 → 실기기 검증

---

## 13. 참조

- `flutter/driver_app/MVP/MODELS.md` — 본 Notifier가 소비하는 Freezed 모델
- `flutter/driver_app/MVP/ENUMS.md` — sealed class (수정본 3건 반영)
- `flutter/driver_app/MVP/AUTH.md` — LoginNotifier 상세 + 자동로그인 마이그레이션
- `flutter/driver_app/MVP/FIRESTORE.md` — 트랜잭션 6곳 세부 + Firestore 직접 호출 코드 (차기 작성)
- `flutter/driver_app/MVP/SCREENS.md` — 각 Notifier를 소비하는 12개 화면 (차기 작성)
- `flutter/driver_app/MVP/FCM.md` — FcmNotifier 6종 라우팅 (flutter-expert 담당)
- `driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt` — Kotlin 원본 1,600 LOC
- `driver_app/app/src/main/java/com/designated/driverapp/ui/login/LoginViewModel.kt` — Kotlin 로그인 원본 292 LOC
- `ios/SHARED_LOGIC.md` §4 — 정산 공식 언어 독립 명세

---

## 14. 작성 완료 요약

- **Notifier 8종 스펙 완료**: DriverWorkflow / Login / SignUp / CarryOver / Settlement / Presence / Fcm / CallDetails
- **DriverWorkflowNotifier 핵심 메서드 11종 Dart 코드**: acceptCall/rejectCall/startDriving/cancelTrip/completeCall/confirmAndFinalizeTrip/handleCallCancelled/loadCurrentActiveCall/updateDriverStatus/refreshActiveCallStatus/dismissNewCallPopup/dismissSettlementPopup/setFcmToken
- **Kotlin 원본 라인 번호 100% 인용**
- **의제 11 Analytics 이벤트 12종 명세**
- **테스트 케이스 9종 골격 + riverpod_test 패턴**
- **ENUMS.md 보완 요청 1건**: `PresenceStatus` sealed class 신규 추가 (online/background/offline) — `PresenceManager.kt:37-41` 원본
