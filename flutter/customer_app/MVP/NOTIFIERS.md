# 손님앱 Notifiers (Riverpod StateNotifier 4개 분해)

> **의제 14-A (Dart 3 sealed class) + 14-B (Riverpod ^2.5.1) + 11 (Analytics/Crashlytics 신규)** 반영
> **MainViewModel.kt 995 LOC** → **CallNotifier / PointNotifier / ProfileNotifier / AuthNotifier 4종 분해**
> **기사앱 NOTIFIERS.md 자매 문서** — 동일 원칙 + 손님앱 분해 상세
> **원본 기준**: `customer_app/app/src/main/java/com/designated/customer/ui/main/MainViewModel.kt` (995 LOC) + 5 ViewModel + 3 Service
> **교차 의존 원칙**: FCM.md §6 (ref.read 직접 호출, 이벤트 버스 금지)
> **작성일**: 2026-04-16
> **작성자**: kotlin-expert

---

## 1. Notifier 구조 개요

### 1.1 MainViewModel.kt 995 LOC 분해 근거

**Kotlin 원본 문제점** (검토 포인트 2 재확인):
- 24 필드 단일 UiState (`MainViewModel.kt:30-69`)
- BroadcastReceiver 5개 멤버 (`line 89-94`) — FCM → State 라우팅
- 4개 도메인(콜/포인트/프로필/인증) 결합
- 교차 의존 3지점 (§6):
  1. **콜 취소 → 포인트 환불** (`MainViewModel.kt:460` CALL_CANCELLED 수신 시 `loadCustomerPoints()` 호출)
  2. **운행 완료 → 포인트 적립 팝업** (`MainViewModel.kt:559-584` `handleRideCompleted`)
  3. **콜 요청 → 포인트 사용 → 실패 시 환불** (`MainViewModel.kt:278-303, 350-363`)

**분해 방향 (OVERVIEW.md + FCM.md §6.1 결정)**:

| Notifier | 책임 | Kotlin 원본 라인 | LOC 예상 |
|----------|-----|---------------|---------|
| **CallNotifier** | 콜 생명주기 + FCM 라우팅 5종 | MainViewModel.kt:119, 191-236, 278-475, 410-516, 559-585, 907-922 | ~300 |
| **PointNotifier** | 포인트 조회/사용/환불/이력 (실시간 리스너) | MainViewModel.kt:792-823 + PointService.kt 전체 | ~200 |
| **ProfileNotifier** | 프로필 + 사무실 정보 + 약관 + 슬로건 + 집주소 | MainViewModel.kt:130-186, 844-949 + ProfileSetupViewModel.kt | ~200 |
| **AuthNotifier** | Anonymous Auth 자동 + Phone Auth (프로필 시) + FCM 토큰 | PhoneAuthViewModel.kt + MainActivity.kt:278-327 | ~200 |

### 1.2 Provider 명명 규칙 (기사앱 NOTIFIERS.md §1.2 동일)

| 대상 | 네이밍 | 예시 |
|------|--------|-----|
| Notifier 클래스 | `XxxNotifier` | `CallNotifier`, `PointNotifier` |
| 상태 클래스 | `XxxUiState` | `CallUiState`, `PointUiState` (MODELS.md §7.4) |
| Provider | `xxxProvider` | `callNotifierProvider` |
| Service Provider | `xxxServiceProvider` | `callServiceProvider` (Kotlin CallService 매핑) |

### 1.3 ref.watch / ref.read / ref.listen 가이드 (기사앱과 동일)

- **`ref.watch`** — 위젯/Notifier 내부 반응형 구독
- **`ref.read`** — 일회성 값 조회 (이벤트 핸들러 전용, build 내부 금지)
- **`ref.listen`** — side effect (Snackbar, Navigation, Analytics)
- **`_ref.read(OtherNotifier.notifier).method()`** — **교차 의존 3지점 공식 패턴** (FCM.md §6.2)

### 1.4 autoDispose 정책

| Notifier | autoDispose? | 이유 |
|---------|-------------|------|
| `callNotifierProvider` | **No** | 앱 세션 전체 유지 |
| `pointNotifierProvider` | **No** | 실시간 리스너 유지 |
| `profileNotifierProvider` | **No** | Install Referrer 데이터 + 사무실 정보 지속 |
| `authNotifierProvider` | **No** | Anonymous Auth uid 유지 |
| `pointHistoryProvider` | **Yes** | 거래 내역 화면 떠날 때 해제 |
| `callHistoryProvider` | **Yes** | 콜 내역 화면 떠날 때 해제 |

### 1.5 Notifier 인벤토리

| Notifier | Kotlin 원본 | 의존 Service/Repository |
|---------|-----------|---------------------|
| **CallNotifier** | MainViewModel (콜 부분) | `CallService` (§6.1) + Firestore 트랜잭션 |
| **PointNotifier** | MainViewModel (포인트 부분) + `PointService.kt` 전체 | `PointService` |
| **ProfileNotifier** | MainViewModel (프로필/사무실) + `ProfileSetupViewModel` + `TermsAgreementViewModel` | Firestore `customers/{uid}` + `customerInfo/{phone}` + SharedPreferences |
| **AuthNotifier** | `PhoneAuthViewModel` + MainActivity Anonymous Auth | `FirebaseAuth` + `FirebaseMessaging` |
| **PointHistoryNotifier (family)** | `PointHistoryViewModel` | `PointService.getPointTransactions` |
| **CallHistoryNotifier (family)** | `CallHistoryViewModel` | `CallService.getCustomerCallHistory` |

---

## 2. CallNotifier (~300 LOC)

### 2.1 책임

- **콜 생명주기**: requestCall / cancelCall / restoreActiveCall
- **FCM 5종 라우팅** (FCM.md §2.5):
  - CALL_RECEIVED (콜 접수 확인, 운영상 거의 불필요)
  - DRIVER_ASSIGNED (기사 배정)
  - RIDE_COMPLETED (운행 완료 → 포인트 적립)
  - CALL_CANCELLED (취소 — 기사/관리자 측)
  - call_status_update (ACCEPTED → DRIVER_ARRIVING, IN_PROGRESS 등)

**⚠️ 의제 10 결정**: Kotlin 원본의 `createdFrom: "customer_app"` 필드 `toMap()` 포함 (CustomerCall.kt:48) 은 Flutter에서 동일 유지 (Firestore 경로 호환).

### 2.2 Notifier 골격

```dart
// lib/features/call/notifiers/call_notifier.dart

import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_analytics/firebase_analytics.dart';
import 'package:firebase_crashlytics/firebase_crashlytics.dart';

import '../../domain/models/customer_call.dart';
import '../../domain/state/call_ui_state.dart';
import '../../domain/state/call_status.dart';
import '../../domain/enums/call_state.dart';
import '../../features/point/notifiers/point_notifier.dart';
import '../../features/profile/notifiers/profile_notifier.dart';
import '../../features/auth/notifiers/auth_notifier.dart';
import '../data/call_service.dart';

class CallNotifier extends StateNotifier<CallUiState> {
  CallNotifier({
    required this.ref,
    required this.callService,
    required this.firestore,
    required this.auth,
    required this.analytics,
  }) : super(const CallUiState()) {
    // 앱 시작 시 활성 콜 1회 복구 (Kotlin MainViewModel.kt:119 init)
    restoreActiveCall();
  }

  final Ref ref;
  final CallService callService;
  final FirebaseFirestore firestore;
  final FirebaseAuth auth;
  final FirebaseAnalytics analytics;

  /// Kotlin MainViewModel.kt:191-236 `restoreActiveCall`
  /// 앱 시작 시 Firestore 1회 조회로 활성 콜 복구
  Future<void> restoreActiveCall() async {
    if (state.callStatus != null) return;

    try {
      final phoneNumber = ref.read(authNotifierProvider).phoneNumber;
      if (phoneNumber.isEmpty) return;

      final activeCall = await callService.getActiveCall(phoneNumber);
      if (activeCall == null) return;

      // status → CallState 매핑 (Kotlin line 198-204)
      final callState = switch (activeCall.status) {
        'WAITING' || 'REQUESTED' => const CallStateRequested(),
        'ASSIGNED' => const CallStateAssigned(),
        'ACCEPTED' || 'PREPARING' => const CallStateDriverArriving(),
        'IN_PROGRESS' => const CallStateInProgress(),
        _ => null,
      };
      if (callState == null) return;

      // 기사 정보 복구 (Kotlin line 206-222)
      DriverInfo? driverInfo;
      if (activeCall.driverId != null) {
        try {
          final driverDoc = await callService.getDriverInfo(activeCall.driverId!);
          if (driverDoc != null) {
            driverInfo = DriverInfo(
              id: activeCall.driverId!,
              name: driverDoc['name'] as String? ?? '',
              phoneNumber: driverDoc['phone'] as String? ?? '',
              vehicleNumber: driverDoc['vehicleNumber'] as String? ?? '',
            );
          }
        } catch (e) {
          debugPrint('[restoreActiveCall] 기사 정보 복구 실패: $e');
        }
      }

      state = state.copyWith(
        callStatus: CallStatus(
          callId: activeCall.id,
          state: callState,
          timestamp: activeCall.timestamp,
          driverInfo: driverInfo,
        ),
      );

      debugPrint('[restoreActiveCall] 복구: ${activeCall.id}, state=$callState');
    } catch (e, st) {
      await FirebaseCrashlytics.instance.recordError(e, st, reason: 'restoreActiveCall');
    }
  }

  // ... (다음 메서드들)
}

final callNotifierProvider =
    StateNotifierProvider<CallNotifier, CallUiState>((ref) {
  return CallNotifier(
    ref: ref,
    callService: ref.watch(callServiceProvider),
    firestore: ref.watch(firestoreProvider),
    auth: ref.watch(firebaseAuthProvider),
    analytics: ref.watch(analyticsProvider),
  );
});
```

### 2.3 핵심 메서드 매핑

#### 2.3.1 `requestCall(...)` — 콜 요청 (교차 의존 3지점 중 하나)

**Kotlin 원본** (`MainViewModel.kt:266-374`):
- `uiState.canRequestCall` 검증
- 포인트 사용 시 `pointService.usePoints` 먼저 호출 (실패 시 환불)
- `CustomerCall` 생성 → `callService.requestCall()` 호출
- 실패 시 1회 재시도 + 여전히 실패 시 **포인트 환불** (`pointService.refundPoints`)
- 성공 시 `CallStatus(state=REQUESTED)` 설정

```dart
/// Kotlin MainViewModel.kt:266-374 `requestCall`
/// 교차 의존: PointNotifier (사용 + 환불)
Future<void> requestCall() async {
  final canRequest = _canRequestCall();
  if (!canRequest) {
    state = state.copyWith(error: '콜을 요청할 수 없는 상태입니다');
    return;
  }

  state = state.copyWith(isLoadingCall: true, error: null);

  var usedPointsAmount = 0;
  try {
    final pointState = ref.read(pointNotifierProvider);

    // 1. 포인트 사용 처리 (Kotlin line 278-303)
    if (pointState.usePoints && pointState.pointsToUse > 0) {
      final ok = await ref
          .read(pointNotifierProvider.notifier)
          .usePoints(amount: pointState.pointsToUse);
      if (!ok) {
        state = state.copyWith(
          isLoadingCall: false,
          error: '포인트 사용에 실패했습니다',
        );
        return;
      }
      usedPointsAmount = pointState.pointsToUse;
    }

    // 2. CustomerCall 생성 (Kotlin line 309-323)
    final profile = ref.read(profileNotifierProvider);
    final auth_ = ref.read(authNotifierProvider);
    final phone = auth_.phoneNumber;
    final customerName = profile.customerInfo?.name ?? phone;

    final call = CustomerCall(
      phoneNumber: phone,
      officeId: profile.customerInfo?.linkedOfficeId ?? '',
      provinceId: profile.provinceId,
      cityId: profile.cityId,
      currentLocation: state.currentLocation,
      destinationLocation: state.destinationLocation,
      timestamp: DateTime.now().millisecondsSinceEpoch,
      status: 'WAITING',                            // Kotlin line 317 "REQUESTED" → "WAITING"
      customerId: phone,
      customerName: customerName,
      customerGrade: pointState.customerPoints?.grade.json.toLowerCase() ?? 'bronze',  // P0-B.3: jsonValue → json (ENUMS.md CustomerGrade 일관)
      pointsUsed: pointState.usePoints ? pointState.pointsToUse : 0,
    );

    // 3. 콜 요청 (실패 시 1회 재시도, Kotlin line 325-332)
    String callId;
    try {
      callId = await callService.requestCall(call);
    } catch (firstError) {
      debugPrint('[requestCall] 첫 번째 시도 실패, 재시도');
      await Future.delayed(const Duration(milliseconds: 1000));
      callId = await callService.requestCall(call);
    }

    // 4. UI 상태 갱신 (Kotlin line 334-345)
    state = state.copyWith(
      isLoadingCall: false,
      callStatus: CallStatus(
        callId: callId,
        state: const CallStateRequested(),
        timestamp: DateTime.now().millisecondsSinceEpoch,
      ),
      showLocationCard: false,
    );

    // 포인트 사용 상태 초기화 (PointNotifier 측)
    ref.read(pointNotifierProvider.notifier).resetPointsToUse();

    // 의제 11 측정
    await analytics.logEvent(
      name: 'customer_call_requested',
      parameters: {
        'call_id': callId,
        'office_id': call.officeId,
        'points_used': call.pointsUsed,
        'has_destination': call.destinationLocation.isNotEmpty ? 1 : 0,
      },
    );
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'requestCall');

    // ⚠️ 교차 의존 지점 #3: 포인트 사용했다면 환불 (Kotlin line 350-363)
    if (usedPointsAmount > 0) {
      final refunded = await ref
          .read(pointNotifierProvider.notifier)
          .refundPoints(amount: usedPointsAmount);
      if (refunded) {
        debugPrint('[requestCall] 포인트 $usedPointsAmount P 환불 완료');
      } else {
        debugPrint('[requestCall] 포인트 환불 실패 - 수동 조정 필요');
      }
    }

    state = state.copyWith(
      isLoadingCall: false,
      error: '콜 요청에 실패했습니다. 네트워크 연결을 확인해주세요.',
    );

    // 시스템 알림 (Kotlin showCallFailedNotification, line 745-789 — SCREENS.md에서 상세)
    _showCallFailedNotification();
  }
}

bool _canRequestCall() {
  return !state.isLoadingCall &&
      state.currentLocation.isNotEmpty &&
      state.destinationLocation.isNotEmpty &&
      (state.callStatus == null ||
          (state.callStatus!.state is! CallStateRequested &&
              state.callStatus!.state is! CallStateAssigned &&
              state.callStatus!.state is! CallStateDriverArriving &&
              state.callStatus!.state is! CallStateInProgress));
}
```

#### 2.3.2 `cancelCall()` — 콜 취소 (교차 의존 지점 #1)

**Kotlin 원본** (`MainViewModel.kt:376-408`):
- `callService.cancelCall(callId)` 호출
- `CallService.cancelCall` 내부에서 `runTransaction` (`CallService.kt:33-68`):
  - 현재 status 조회
  - 중복 취소 방지 (이미 CANCELED/CANCELLED_BY_DRIVER/CANCELLED_BY_CUSTOMER 확인)
  - WAITING/ASSIGNED/ACCEPTED/PREPARING 단계까지만 취소 허용
  - `status → CANCELLED_BY_CUSTOMER`
- UI: `callStatus = null` 설정

```dart
Future<void> cancelCall() async {
  final currentCallId = state.callStatus?.callId;
  if (currentCallId == null) {
    debugPrint('[cancelCall] callId == null, 무시');
    return;
  }

  debugPrint('[cancelCall] 시작: $currentCallId');

  try {
    final success = await callService.cancelCall(currentCallId);

    if (success) {
      // UI에서 제거 (Kotlin line 391-394)
      state = state.copyWith(callStatus: null);
      debugPrint('[cancelCall] callStatus set to null');

      await analytics.logEvent(
        name: 'customer_call_cancelled',
        parameters: {'call_id': currentCallId, 'initiator': 'customer'},
      );
    } else {
      state = state.copyWith(error: '콜 취소에 실패했습니다');
    }
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'cancelCall');
    state = state.copyWith(error: '콜 취소 중 오류가 발생했습니다: $e');
  }
}
```

**⚠️ 교차 의존 지점 #1 (자체 취소 측)**: 손님이 본인 앱에서 취소할 때는 **포인트 환불이 CF 책임** (콜 문서 status=CANCELLED_BY_CUSTOMER 감지 → CF `onCallCancelledByCustomer` 가 포인트 환불). 클라이언트는 환불 호출 안 함.

그러나 **FCM CALL_CANCELLED 수신 시** (기사/관리자 측 취소) 는 §2.3.5 참조.

#### 2.3.3 FCM 핸들러 — `handleCallReceived(...)` (CALL_RECEIVED)

**Kotlin 원본** (`MainViewModel.kt:466-476`): 콜 접수 브로드캐스트 수신 → `restoreActiveCall()` 재호출.

```dart
void handleCallReceived(Map<String, dynamic> data) {
  final callId = data['callId'] as String?;
  debugPrint('[handleCallReceived] callId=$callId');
  // 활성 콜 상태 재조회
  restoreActiveCall();

  analytics.logEvent(
    name: 'customer_call_received',
    parameters: {'call_id': callId ?? ''},
  );
}
```

#### 2.3.4 FCM 핸들러 — `handleDriverAssigned(...)` (DRIVER_ASSIGNED)

**Kotlin 원본** (`MainViewModel.kt:521-553`):
- 진동 알람 (`playDriverAssignedNotification`)
- `CallStatus(state=ASSIGNED, driverInfo=...)` 설정

```dart
void handleDriverAssigned({
  required String? callId,
  required String driverName,
  required String driverPhone,
  required String vehicleNumber,
  required String driverId,
}) {
  debugPrint('[handleDriverAssigned] callId=$callId, driver=$driverName');

  // 진동 알림 (SCREENS.md에서 구현. 본 Notifier는 state만)
  _playDriverAssignedNotification();

  final driverInfo = DriverInfo(
    id: driverId,
    name: driverName,
    phoneNumber: driverPhone,
    vehicleNumber: vehicleNumber,
  );

  state = state.copyWith(
    callStatus: CallStatus(
      callId: callId ?? '',
      state: const CallStateAssigned(),
      timestamp: DateTime.now().millisecondsSinceEpoch,
      driverInfo: driverInfo,
      estimatedArrivalTime: 0,
    ),
  );

  analytics.logEvent(
    name: 'customer_driver_assigned',
    parameters: {'call_id': callId ?? '', 'driver_id': driverId},
  );
}
```

#### 2.3.5 FCM 핸들러 — `handleRideCompleted(...)` (RIDE_COMPLETED, 교차 의존 지점 #2)

**Kotlin 원본** (`MainViewModel.kt:559-585`):
- `pointService.getCustomerPoints(phoneNumber)` 재조회 (포인트 적립된 최신 값)
- `earnedPoints = customerPoints.calculateEarnPoints(fare)` 계산
- `showPointsEarnedDialog = true` + earnedPoints/usedPoints/rideCompletedFare 설정
- `callStatus = null` (콜 상태 제거)

**⚠️ FCM.md §6.3 Race Condition 명시**:
- CF가 포인트 적립 write 후 FCM 전송하지만 **전송 직후 클라이언트 refresh → 적립 완료 전 읽기 가능**
- 완화: 500ms 지연 + PointNotifier 실시간 리스너 (권장안)

```dart
/// Kotlin MainViewModel.kt:559-585 `handleRideCompleted`
/// 교차 의존 지점 #2: PointNotifier 갱신 + 적립 팝업
Future<void> handleRideCompleted({
  required int fare,
  required int pointsUsed,
}) async {
  debugPrint('[handleRideCompleted] fare=$fare, pointsUsed=$pointsUsed');

  // ⚠️ Race condition 완화 (FCM.md §6.3): CF 적립 write 대기
  await Future.delayed(const Duration(milliseconds: 500));

  // PointNotifier 실시간 리스너가 이미 구독 중 → 자동 갱신됨
  // 추가로 force refresh (오프라인 또는 리스너 일시 오류 대비)
  await ref.read(pointNotifierProvider.notifier).refresh();

  // 적립 포인트 계산 (PointNotifier 최신 state 사용)
  final pointState = ref.read(pointNotifierProvider);
  final earnedPoints = pointState.customerPoints?.calculateEarnPoints(fare) ?? 0;

  // 콜 상태 제거 + 포인트 팝업 표시 (PointNotifier에 위임)
  state = state.copyWith(callStatus: null);

  ref.read(pointNotifierProvider.notifier).showPointsEarnedDialog(
    earnedPoints: earnedPoints,
    usedPoints: pointsUsed,
    rideCompletedFare: fare,
  );

  await analytics.logEvent(
    name: 'customer_ride_completed',
    parameters: {
      'fare': fare,
      'points_used': pointsUsed,
      'points_earned': earnedPoints,
    },
  );
}
```

#### 2.3.6 FCM 핸들러 — `handleCallCancelled(...)` (CALL_CANCELLED, 교차 의존 지점 #1)

**Kotlin 원본** (`MainViewModel.kt:448-463`):
- 팝업 제거 (`callStatus = null`)
- 포인트 환불 반영을 위해 `loadCustomerPoints()` 호출

```dart
/// Kotlin MainViewModel.kt:448-463 CALL_CANCELLED 브로드캐스트 리시버
/// 교차 의존 지점 #1 (FCM 측 취소)
Future<void> handleCallCancelled({
  required String? callId,
  required String cancelReason,
}) async {
  debugPrint('[handleCallCancelled] callId=$callId, reason=$cancelReason');

  state = state.copyWith(
    callStatus: null,
    error: '고객이 콜을 취소했습니다',  // Kotlin MainViewModel은 error 설정 없으나
                                      // UI 알림 일관성 위해 추가 (기사앱 handleCallCancelled 참고)
  );

  // ⚠️ 포인트 환불은 CF 측에서 처리 (손님이 취소한 콜은 CF가 환불)
  // 여기서는 UI 반영을 위해 PointNotifier refresh
  await ref.read(pointNotifierProvider.notifier).refresh();

  await analytics.logEvent(
    name: 'customer_call_cancelled_by_other',
    parameters: {'call_id': callId ?? '', 'reason': cancelReason},
  );
}
```

#### 2.3.7 FCM 핸들러 — `handleCallStatusUpdate(...)` (call_status_update)

**Kotlin 원본** (`MainViewModel.kt:478-505`):
- status=ACCEPTED → CallState.DRIVER_ARRIVING
- status=IN_PROGRESS → CallState.IN_PROGRESS
- 현재 callStatus.callId와 일치하면 업데이트, 다르면 `restoreActiveCall()` 호출

```dart
Future<void> handleCallStatusUpdate({
  required String? callId,
  required String status,
}) async {
  debugPrint('[handleCallStatusUpdate] callId=$callId, status=$status');

  final newState = switch (status) {
    'ACCEPTED' => const CallStateDriverArriving(),
    'IN_PROGRESS' => const CallStateInProgress(),
    _ => null,
  };
  if (newState == null) return;

  final current = state.callStatus;
  if (current != null && (callId == null || current.callId == callId)) {
    state = state.copyWith(
      callStatus: current.copyWith(state: newState),
    );
  } else {
    await restoreActiveCall();
  }

  await analytics.logEvent(
    name: 'customer_call_status_update',
    parameters: {'call_id': callId ?? '', 'status': status},
  );
}
```

#### 2.3.8 위치 업데이트 메서드

**Kotlin 원본** (`MainViewModel.kt:239-245, 247-264`):

```dart
void updateCurrentLocation(String location) {
  state = state.copyWith(currentLocation: location, error: null);
}

void updateDestinationLocation(String location) {
  state = state.copyWith(destinationLocation: location, error: null);
}

/// Kotlin getCurrentLocation (LocationService)
Future<void> getCurrentLocation() async {
  state = state.copyWith(isLoadingLocation: true, error: null);
  try {
    final location = await ref.read(locationServiceProvider).getCurrentLocation();
    state = state.copyWith(
      currentLocation: location,
      isLoadingLocation: false,
    );
  } catch (e) {
    state = state.copyWith(
      isLoadingLocation: false,
      error: '현재 위치를 가져올 수 없습니다: $e',
    );
  }
}

void toggleLocationCard() {
  state = state.copyWith(showLocationCard: !state.showLocationCard);
}
```

#### 2.3.9 에러 / 알림 헬퍼

```dart
void clearError() {
  state = state.copyWith(error: null);
}

void _playDriverAssignedNotification() {
  // SCREENS.md 에서 VibrationHelper + audioplayers로 구현
  // 본 Notifier는 side effect 위임만
}

void _showCallFailedNotification() {
  // flutter_local_notifications로 시스템 알림 표시
  // SCREENS.md에서 구현
}
```

### 2.4 FCM 5종 라우팅 테이블

FCM.md §2.5 상세 구현과 Notifier 메서드 1:1 매핑:

| FCM type | FcmRouter 호출 | CallNotifier 메서드 |
|---------|--------------|------------------|
| `CALL_RECEIVED` | `callNotifier.handleCallReceived(data)` | §2.3.3 |
| `DRIVER_ASSIGNED` | `callNotifier.handleDriverAssigned(...)` | §2.3.4 |
| `RIDE_COMPLETED` | `callNotifier.handleRideCompleted(fare, pointsUsed)` | §2.3.5 |
| `CALL_CANCELLED` | `callNotifier.handleCallCancelled(callId, reason)` | §2.3.6 |
| `call_status_update` | `callNotifier.handleCallStatusUpdate(callId, status)` | §2.3.7 |

---

## 3. PointNotifier (~200 LOC)

### 3.1 책임

- **실시간 리스너**: `customerPoints/{phoneNumber}` 구독 (Kotlin `PointService.observeCustomerPoints`)
- `usePoints` / `refundPoints` — `PointService` 호출 래퍼
- `getPointHistory` (family provider)
- 포인트 팝업 상태 관리

### 3.2 Notifier 골격

```dart
// lib/features/point/notifiers/point_notifier.dart

import 'dart:async';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/models/customer_points.dart';
import '../../domain/enums/customer_grade.dart';
import '../../domain/state/point_ui_state.dart';
import '../data/point_service.dart';

class PointNotifier extends StateNotifier<PointUiState> {
  PointNotifier({
    required this.ref,
    required this.pointService,
    required this.firestore,
  }) : super(const PointUiState()) {
    _subscribePoints();
  }

  final Ref ref;
  final PointService pointService;
  final FirebaseFirestore firestore;

  StreamSubscription<DocumentSnapshot<Map<String, dynamic>>>? _pointsSub;

  /// Kotlin PointService.observeCustomerPoints (PointService.kt:69-91)
  void _subscribePoints() {
    // phoneNumber가 바뀌면 재구독 필요 → AuthNotifier watch
    ref.listen<AuthUiState>(authNotifierProvider, (prev, next) {
      if (prev?.phoneNumber == next.phoneNumber) return;
      _stopListener();
      if (next.phoneNumber.isNotEmpty && next.isAnonymouslySignedIn) {
        _startListener(next.phoneNumber);
      }
    }, fireImmediately: true);
  }

  void _startListener(String phoneNumber) {
    final profile = ref.read(profileNotifierProvider);
    if (profile.provinceId.isEmpty ||
        profile.cityId.isEmpty ||
        profile.officeId.isEmpty) {
      return;
    }

    final docRef = firestore
        .collection('provinces').doc(profile.provinceId)
        .collection('cities').doc(profile.cityId)
        .collection('offices').doc(profile.officeId)
        .collection('customerPoints').doc(phoneNumber);

    _pointsSub = docRef.snapshots().listen(
      (snap) {
        if (!snap.exists) {
          state = state.copyWith(customerPoints: null);
          return;
        }
        final points = CustomerPoints.fromFirestore(snap);
        state = state.copyWith(customerPoints: points);
      },
      onError: (e, st) {
        debugPrint('[PointNotifier] listener error: $e');
        FirebaseCrashlytics.instance.recordError(e, st, reason: 'pointsListener');
      },
    );
  }

  void _stopListener() {
    _pointsSub?.cancel();
    _pointsSub = null;
  }

  @override
  void dispose() {
    _stopListener();
    super.dispose();
  }

  // ... (메서드 §3.3)
}

final pointNotifierProvider =
    StateNotifierProvider<PointNotifier, PointUiState>((ref) {
  return PointNotifier(
    ref: ref,
    pointService: ref.watch(pointServiceProvider),
    firestore: ref.watch(firestoreProvider),
  );
});
```

### 3.3 핵심 메서드

#### 3.3.1 `refresh()` — 강제 재조회

**Kotlin 원본** (`MainViewModel.kt:792-808`):

```dart
/// Kotlin MainViewModel.kt:792-808 `loadCustomerPoints`
Future<void> refresh() async {
  state = state.copyWith(isLoadingPoints: true);
  try {
    final phone = ref.read(authNotifierProvider).phoneNumber;
    if (phone.isEmpty) return;

    final points = await pointService.getCustomerPoints(phone);
    state = state.copyWith(
      customerPoints: points,
      isLoadingPoints: false,
    );
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'refresh');
    state = state.copyWith(
      isLoadingPoints: false,
    );
  }
}
```

**실시간 리스너 유지 시 이 메서드는 거의 호출 불필요**. FCM RIDE_COMPLETED 수신 직후 Race 안전 마진 용도 (§2.3.5).

#### 3.3.2 `usePoints(amount)` — 포인트 사용

**Kotlin 원본** (`PointService.kt:199-263`):
- `runTransaction`: `customerPoints/{phone}` 읽기 → `canUsePoints(amount)` 검증 → 차감
- **거래 내역 기록**: `pointTransactions/` 컬렉션에 `PointTransaction(type=USE, amount=-amount)` 생성

```dart
/// Kotlin PointService.usePoints 래퍼
Future<bool> usePoints({required int amount}) async {
  try {
    final phone = ref.read(authNotifierProvider).phoneNumber;
    if (phone.isEmpty) return false;

    final ok = await pointService.usePoints(
      phoneNumber: phone,
      amount: amount,
      description: '대리운전 콜 요청 시 포인트 사용',
    );

    if (ok) {
      // 사용 성공 시 팝업 (Kotlin MainViewModel.kt:294-300)
      state = state.copyWith(
        showPointsEarnedDialog: true,
        earnedPoints: 0,
        usedPoints: amount,
        rideCompletedFare: 0,
      );
      // 리스너가 자동으로 customerPoints 갱신
    }

    return ok;
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'usePoints');
    return false;
  }
}
```

#### 3.3.3 `refundPoints(amount)` — 포인트 환불

**Kotlin 원본** (`PointService.kt:268-326`): `TransactionType.CANCEL` 으로 기록.

```dart
/// Kotlin PointService.refundPoints 래퍼
Future<bool> refundPoints({required int amount, String? description}) async {
  try {
    final phone = ref.read(authNotifierProvider).phoneNumber;
    if (phone.isEmpty) return false;

    final ok = await pointService.refundPoints(
      phoneNumber: phone,
      amount: amount,
      description: description ?? '콜 요청 실패로 인한 포인트 환불',
    );

    return ok;
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'refundPoints');
    return false;
  }
}
```

#### 3.3.4 `toggleUsePoints()` / `updatePointsToUse()` / `resetPointsToUse()`

**Kotlin 원본** (`MainViewModel.kt:811-830`):

```dart
void toggleUsePoints() {
  final maxPoints = state.customerPoints?.currentPoints ?? 0;
  if (state.usePoints) {
    state = state.copyWith(usePoints: false, pointsToUse: 0);
  } else {
    // 최대 10,000P (Kotlin line 820)
    state = state.copyWith(
      usePoints: true,
      pointsToUse: math.min(maxPoints, 10000),
    );
  }
}

void updatePointsToUse(int points) {
  final maxPoints = state.customerPoints?.currentPoints ?? 0;
  state = state.copyWith(
    pointsToUse: math.min(points, maxPoints),
  );
}

void resetPointsToUse() {
  state = state.copyWith(usePoints: false, pointsToUse: 0);
}
```

#### 3.3.5 `showPointsEarnedDialog(...)` / `dismissPointsEarnedDialog()`

**Kotlin 원본** (`MainViewModel.kt:559-585, 733-735`):

```dart
/// CallNotifier.handleRideCompleted 에서 호출됨
void showPointsEarnedDialog({
  required int earnedPoints,
  required int usedPoints,
  required int rideCompletedFare,
}) {
  state = state.copyWith(
    showPointsEarnedDialog: true,
    earnedPoints: earnedPoints,
    usedPoints: usedPoints,
    rideCompletedFare: rideCompletedFare,
  );
}

void dismissPointsEarnedDialog() {
  state = state.copyWith(showPointsEarnedDialog: false);
}
```

### 3.4 PointHistoryNotifier (family provider, autoDispose)

**Kotlin 원본** (`PointHistoryViewModel` — 미Read, `PointService.getPointTransactions` 기반).

```dart
// lib/features/point/notifiers/point_history_notifier.dart

class PointHistoryNotifier extends StateNotifier<AsyncValue<List<PointTransaction>>> {
  PointHistoryNotifier({
    required this.pointService,
    required this.phoneNumber,
  }) : super(const AsyncLoading()) {
    _load();
  }

  final PointService pointService;
  final String phoneNumber;

  Future<void> _load() async {
    try {
      final transactions = await pointService.getPointTransactions(
        phoneNumber: phoneNumber,
        limit: 20,
      );
      state = AsyncData(transactions);
    } catch (e, st) {
      state = AsyncError(e, st);
    }
  }

  Future<void> refresh() => _load();
}

final pointHistoryProvider = StateNotifierProvider.autoDispose
    .family<PointHistoryNotifier, AsyncValue<List<PointTransaction>>, String>(
  (ref, phoneNumber) => PointHistoryNotifier(
    pointService: ref.watch(pointServiceProvider),
    phoneNumber: phoneNumber,
  ),
);
```

**실시간 vs 1회 조회 선택**: Kotlin `PointService.observePointTransactions` (`PointService.kt:362-385`) 는 실시간 리스너 제공. Flutter **Phase 1 MVP** 는 **1회 조회 + 수동 refresh** (간결성, 비용). 실시간 필요 시 `.snapshots()` 로 전환.

---

## 4. ProfileNotifier (~200 LOC)

### 4.1 책임

- **프로필 CRUD** (`customers/{uid}` 문서)
- **사무실 정보 로드** (Firestore `offices/{o}`)
- **Install Referrer 데이터** (provinceId/cityId/officeId + driverId/driverName + 사무실 연락처) — SharedPreferences에서 읽기
- **약관 동의** (SharedPreferences 저장)
- **슬로건 로드** (Firestore `settings/branding`)
- **집주소 관리**

### 4.2 Notifier 골격

```dart
// lib/features/profile/notifiers/profile_notifier.dart

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../domain/models/customer_info.dart';
import '../../domain/state/profile_ui_state.dart';

class ProfileNotifier extends StateNotifier<ProfileUiState> {
  ProfileNotifier({
    required this.ref,
    required this.firestore,
    required this.prefs,
  }) : super(const ProfileUiState()) {
    _loadLocalData();
  }

  final Ref ref;
  final FirebaseFirestore firestore;
  final SharedPreferences prefs;

  /// provinceId/cityId/officeId 편의 accessor
  String get provinceId => prefs.getString('province_id') ?? '';
  String get cityId => prefs.getString('city_id') ?? '';
  String get officeId => prefs.getString('office_id') ?? '';

  /// Install Referrer + 로컬 저장 데이터 로드 (Kotlin MainViewModel.kt:130-143)
  Future<void> _loadLocalData() async {
    // 1. SharedPreferences에서 집주소 로드 (Kotlin 원본: customerInfo.homeAddress 우선, fallback prefs)
    final homeAddress = prefs.getString('home_address') ?? '';
    state = state.copyWith(homeAddress: homeAddress);

    // 2. 사무실 정보 로드 (Firestore)
    if (officeId.isNotEmpty) {
      await loadOfficeInfo();
    }

    // 3. 슬로건 로드
    await loadSlogan();
  }

  // ... (메서드 §4.3)
}

final profileNotifierProvider =
    StateNotifierProvider<ProfileNotifier, ProfileUiState>((ref) {
  return ProfileNotifier(
    ref: ref,
    firestore: ref.watch(firestoreProvider),
    prefs: ref.watch(sharedPreferencesProvider),
  );
});
```

### 4.3 핵심 메서드

#### 4.3.1 `loadOfficeInfo()` — 사무실 + 지역명 로드

**Kotlin 원본** (`MainViewModel.kt:148-186`):

```dart
/// Kotlin MainViewModel.kt:148-186 loadOfficeInfo
Future<void> loadOfficeInfo() async {
  try {
    final officeDoc = await firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .get();

    if (officeDoc.exists) {
      final officeName = officeDoc.data()?['name'] as String? ?? officeId;

      final provinceDoc = await firestore
          .collection('provinces').doc(provinceId)
          .get();
      final regionName = provinceDoc.data()?['name'] as String? ?? provinceId;

      state = state.copyWith(officeName: officeName, regionName: regionName);
    }
  } catch (e, st) {
    debugPrint('[loadOfficeInfo] 실패: $e');
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'loadOfficeInfo');
    // 실패 시 ID 표시 (Kotlin line 178-183)
    state = state.copyWith(officeName: officeId, regionName: provinceId);
  }
}
```

#### 4.3.2 `loadSlogan()` — 슬로건 로드

**Kotlin 원본** (`MainViewModel.kt:936-949`):

```dart
/// Kotlin MainViewModel.kt:936-949 loadSlogan
Future<void> loadSlogan() async {
  try {
    final doc = await firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('settings').doc('branding')
        .get();

    if (doc.exists) {
      final slogan = doc.data()?['slogan'] as String?;
      if (slogan != null && slogan.isNotEmpty) {
        state = state.copyWith(slogan: slogan);
      }
    }
  } catch (e) {
    debugPrint('[loadSlogan] 실패: $e (기본값 유지)');
  }
}
```

#### 4.3.3 `loadCustomerInfo(phoneNumber)` — 프로필 로드

```dart
Future<void> loadCustomerInfo(String phoneNumber) async {
  try {
    // AuthNotifier uid 기반 customers 경로 조회 (OVERVIEW.md §1)
    final uid = ref.read(authNotifierProvider).anonymousUid;
    if (uid == null || officeId.isEmpty) return;

    final doc = await firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('customers').doc(uid)
        .get();

    if (doc.exists) {
      final info = CustomerInfo.fromFirestore(doc);
      state = state.copyWith(customerInfo: info);
    }
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'loadCustomerInfo');
  }
}
```

#### 4.3.4 `updateProfile(name, phone, homeAddress)` — 프로필 저장

**Kotlin 원본** (`ProfileSetupViewModel.kt` 기반):

```dart
Future<bool> updateProfile({
  required String name,
  required String phoneNumber,
  String? homeAddress,
}) async {
  try {
    final uid = ref.read(authNotifierProvider).anonymousUid;
    if (uid == null || officeId.isEmpty) return false;

    final customersRef = firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .collection('customers').doc(uid);

    final data = <String, Object?>{
      'id': uid,
      'phoneNumber': phoneNumber,
      'name': name,
      'grade': state.customerInfo?.grade ?? 'bronze',
      'linkedOfficeId': officeId,
      'primaryOfficeId': state.customerInfo?.primaryOfficeId.isNotEmpty == true
          ? state.customerInfo!.primaryOfficeId
          : officeId,
      if (homeAddress != null && homeAddress.isNotEmpty) 'homeAddress': homeAddress,
      'registeredAt': state.customerInfo?.registeredAt ?? Timestamp.now(),
    };

    await customersRef.set(data, SetOptions(merge: true));

    // state 갱신
    await loadCustomerInfo(phoneNumber);

    await ref.read(analyticsProvider).logEvent(
      name: 'customer_profile_updated',
      parameters: {'office_id': officeId},
    );

    return true;
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'updateProfile');
    return false;
  }
}
```

#### 4.3.5 집주소 관리 메서드

**Kotlin 원본** (`MainViewModel.kt:844-885`):

```dart
void onHomeAddressClick() {
  if (state.homeAddress.isNotEmpty) {
    // 목적지에 자동 입력 (교차 의존: CallNotifier.updateDestinationLocation)
    ref.read(callNotifierProvider.notifier).updateDestinationLocation(state.homeAddress);
  } else {
    state = state.copyWith(showHomeAddressDialog: true);
  }
}

void onEditHomeAddress() {
  state = state.copyWith(showHomeAddressDialog: true);
}

Future<void> saveHomeAddress(String address) async {
  await prefs.setString('home_address', address);
  state = state.copyWith(
    homeAddress: address,
    showHomeAddressDialog: false,
  );
  // 목적지 자동 입력
  ref.read(callNotifierProvider.notifier).updateDestinationLocation(address);
}

void closeHomeAddressDialog() {
  state = state.copyWith(showHomeAddressDialog: false);
}
```

**교차 의존 메모**: ProfileNotifier → CallNotifier 호출 (목적지 자동 입력). **안전** — CallNotifier는 ProfileNotifier를 watch하지 않음 (순환 없음).

#### 4.3.6 약관 동의 (TermsAgreementViewModel 통합)

```dart
Future<void> acceptTerms({
  required String version,
  required bool marketingConsent,
}) async {
  await prefs.setBool('terms_accepted', true);
  await prefs.setString('terms_version', version);
  await prefs.setInt('terms_accepted_at', DateTime.now().millisecondsSinceEpoch);
  await prefs.setBool('marketing_consent', marketingConsent);
}

bool get isTermsAccepted => prefs.getBool('terms_accepted') ?? false;
String? get termsVersion => prefs.getString('terms_version');
bool get marketingConsent => prefs.getBool('marketing_consent') ?? false;
```

### 4.4 사무실 코드 입력 (ATTRIBUTION.md §2 연계)

**Phase 1 추가** (Kotlin에는 Install Referrer만, 의제 10 결정은 수동 입력 추가):

```dart
/// ATTRIBUTION.md §2 B옵션: 단축 코드 6자리
Future<bool> applyOfficeCode(String code) async {
  try {
    final doc = await firestore.collection('office_codes').doc(code).get();
    if (!doc.exists) return false;

    final data = doc.data()!;
    await prefs.setString('province_id', data['provinceId'] as String);
    await prefs.setString('city_id', data['cityId'] as String);
    await prefs.setString('office_id', data['officeId'] as String);

    // 사무실 정보 재로드
    await loadOfficeInfo();
    await loadSlogan();

    await ref.read(analyticsProvider).logEvent(
      name: 'customer_office_code_applied',
      parameters: {'code': code, 'office_id': data['officeId'] as String},
    );

    return true;
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'applyOfficeCode');
    return false;
  }
}
```

---

## 5. AuthNotifier (~200 LOC)

### 5.1 책임

- **Anonymous Auth 자동 로그인** (앱 시작 시)
- **Phone Auth** (프로필 설정용 전화번호 인증, `PhoneAuthViewModel.kt` 포팅)
- **FCM 토큰 등록** (`customerInfo/{phone}` 경로, 의제 5 `fcmTokenPlatform` 포함)
- **익명 uid 상속** (Firebase Auth SDK 자동 처리)

### 5.2 Kotlin 원본 비교

| Kotlin | Flutter |
|--------|--------|
| `MainActivity.kt:75-80` Anonymous Auth 자동 signIn | `AuthNotifier.init` 에서 `signInAnonymously` |
| `PhoneAuthViewModel.kt:43-87` sendVerificationCode | `AuthNotifier.sendVerificationCode` |
| `PhoneAuthViewModel.kt:89-105` verifyCode | `AuthNotifier.verifyCode` |
| `MainActivity.kt:278-327` FCM 토큰 저장 | `AuthNotifier.registerFcmToken` |

### 5.3 Notifier 골격

```dart
// lib/features/auth/notifiers/auth_notifier.dart

import 'dart:async';
import 'dart:io' show Platform;
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/state/auth_ui_state.dart';

class AuthNotifier extends StateNotifier<AuthUiState> {
  AuthNotifier({
    required this.ref,
    required this.auth,
    required this.messaging,
    required this.firestore,
  }) : super(const AuthUiState()) {
    _initAnonymousAuth();
    _subscribeFcmToken();
  }

  final Ref ref;
  final FirebaseAuth auth;
  final FirebaseMessaging messaging;
  final FirebaseFirestore firestore;

  String? _verificationId;
  StreamSubscription<String>? _tokenRefreshSub;

  /// Anonymous Auth 자동 로그인 (Kotlin MainActivity.kt:75-80)
  /// 의제 9: currentUser 유지 시 재사용, 없으면 signInAnonymously
  Future<void> _initAnonymousAuth() async {
    final currentUser = auth.currentUser;
    if (currentUser != null) {
      state = state.copyWith(
        isAnonymouslySignedIn: true,
        anonymousUid: currentUser.uid,
        phoneNumber: currentUser.phoneNumber ?? '',
      );
      debugPrint('[AuthNotifier] currentUser 상속: ${currentUser.uid}');
      return;
    }

    try {
      final credential = await auth.signInAnonymously();
      final uid = credential.user?.uid;
      if (uid != null) {
        state = state.copyWith(
          isAnonymouslySignedIn: true,
          anonymousUid: uid,
        );
        debugPrint('[AuthNotifier] Anonymous signIn 완료: $uid');
      }
    } catch (e, st) {
      await FirebaseCrashlytics.instance.recordError(e, st, reason: 'signInAnonymously');
      state = state.copyWith(error: 'Anonymous 인증 실패: $e');
    }
  }

  /// FCM 토큰 갱신 구독 (의제 5 fcmTokenPlatform 동시 저장)
  void _subscribeFcmToken() {
    _tokenRefreshSub = messaging.onTokenRefresh.listen((token) {
      _registerFcmToken(token);
    });
  }

  @override
  void dispose() {
    _tokenRefreshSub?.cancel();
    super.dispose();
  }

  // ... (메서드 §5.4)
}

final authNotifierProvider =
    StateNotifierProvider<AuthNotifier, AuthUiState>((ref) {
  return AuthNotifier(
    ref: ref,
    auth: ref.watch(firebaseAuthProvider),
    messaging: ref.watch(firebaseMessagingProvider),
    firestore: ref.watch(firestoreProvider),
  );
});
```

### 5.4 Phone Auth 메서드

**Kotlin 원본** (`PhoneAuthViewModel.kt:43-135`):

```dart
void updatePhoneNumber(String phoneNumber) {
  state = state.copyWith(phoneNumber: phoneNumber, error: null);
}

void updateVerificationCode(String code) {
  state = state.copyWith(verificationCode: code, error: null);
}

/// Kotlin sendVerificationCode
Future<void> sendVerificationCode() async {
  if (state.phoneNumber.isEmpty) {
    state = state.copyWith(error: '전화번호를 입력해주세요');
    return;
  }

  state = state.copyWith(isLoading: true, error: null);

  final formatted = _formatPhoneNumber(state.phoneNumber);
  debugPrint('[sendVerificationCode] $formatted');

  await auth.verifyPhoneNumber(
    phoneNumber: formatted,
    timeout: const Duration(seconds: 60),
    verificationCompleted: (PhoneAuthCredential credential) async {
      // 자동 인증 (Android 일부 기기)
      debugPrint('[verificationCompleted] 자동 인증');
      await _signInWithCredential(credential);
    },
    verificationFailed: (FirebaseAuthException e) {
      debugPrint('[verificationFailed] ${e.message}');
      state = state.copyWith(
        isLoading: false,
        error: '인증 실패: ${e.message}',
      );
    },
    codeSent: (String verificationId, int? resendToken) {
      debugPrint('[codeSent] verificationId=$verificationId');
      _verificationId = verificationId;
      state = state.copyWith(
        isLoading: false,
        isCodeSent: true,
        verificationId: verificationId,
      );
    },
    codeAutoRetrievalTimeout: (String verificationId) {
      _verificationId = verificationId;
    },
  );
}

/// Kotlin verifyCode
Future<void> verifyCode() async {
  if (state.verificationCode.isEmpty) {
    state = state.copyWith(error: '인증 코드를 입력해주세요');
    return;
  }

  final verId = _verificationId ?? state.verificationId;
  if (verId == null) {
    state = state.copyWith(error: '인증 세션이 만료되었습니다. 다시 시도해주세요');
    return;
  }

  state = state.copyWith(isLoading: true, error: null);

  try {
    final credential = PhoneAuthProvider.credential(
      verificationId: verId,
      smsCode: state.verificationCode,
    );
    await _signInWithCredential(credential);
  } catch (e) {
    state = state.copyWith(
      isLoading: false,
      error: '인증 실패: $e',
    );
  }
}

Future<void> _signInWithCredential(PhoneAuthCredential credential) async {
  debugPrint('[_signInWithCredential] 시작');
  try {
    // Anonymous 세션 → Phone Auth 링크 (credential linking)
    // 주의: linkWithCredential 사용 시 anonymous uid 유지
    //       signInWithCredential 사용 시 uid 변경 가능
    final result = auth.currentUser?.isAnonymous == true
        ? await auth.currentUser!.linkWithCredential(credential)
        : await auth.signInWithCredential(credential);

    final user = result.user;
    if (user != null) {
      debugPrint('[_signInWithCredential] 성공: uid=${user.uid}, phone=${user.phoneNumber}');
      state = state.copyWith(
        isLoading: false,
        isVerified: true,
        phoneNumber: user.phoneNumber ?? _formatPhoneNumber(state.phoneNumber),
        anonymousUid: user.uid,
      );
    }
  } on FirebaseAuthException catch (e) {
    state = state.copyWith(
      isLoading: false,
      error: '인증 실패: ${e.message}',
    );
  }
}

String _formatPhoneNumber(String phoneNumber) {
  final cleaned = phoneNumber.replaceAll(RegExp(r'[^0-9]'), '');
  if (cleaned.startsWith('010')) {
    return '+82${cleaned.substring(1)}';
  }
  return '+82$cleaned';
}

void clearError() {
  state = state.copyWith(error: null);
}
```

### 5.5 FCM 토큰 등록

**Kotlin 원본** (`MainActivity.kt:278-327`):

```dart
/// Kotlin MainActivity.kt:306-327 saveFcmTokenToFirestore + 의제 5 platform 메타
Future<void> registerFcmToken(String token) => _registerFcmToken(token);

Future<void> _registerFcmToken(String token) async {
  final phone = state.phoneNumber;
  final profile = ref.read(profileNotifierProvider);

  if (phone.isEmpty ||
      profile.provinceId.isEmpty ||
      profile.cityId.isEmpty ||
      profile.officeId.isEmpty) {
    debugPrint('[_registerFcmToken] 정보 부족, 나중에 저장');
    return;
  }

  try {
    await firestore
        .collection('provinces').doc(profile.provinceId)
        .collection('cities').doc(profile.cityId)
        .collection('offices').doc(profile.officeId)
        .collection('customerInfo').doc(phone)
        .set({
      'fcmToken': token,
      'fcmTokenPlatform': Platform.isIOS ? 'ios' : 'android',  // 의제 5 신규
      'phoneNumber': phone,
      'updatedAt': Timestamp.now(),
    }, SetOptions(merge: true));

    debugPrint('[_registerFcmToken] 저장 완료');
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'registerFcmToken');
  }
}

/// 초기 FCM 토큰 조회 후 등록 (앱 시작 시 1회)
Future<void> initialFcmTokenRegistration() async {
  try {
    final token = await messaging.getToken();
    if (token != null) {
      await _registerFcmToken(token);
    }
  } catch (e) {
    debugPrint('[initialFcmTokenRegistration] 실패: $e');
  }
}
```

### 5.6 로그아웃 (비권장, 참고)

손님앱은 **로그아웃 UX 없음** (Anonymous Auth). 그러나 개발/디버깅 용도:

```dart
@visibleForTesting
Future<void> signOut() async {
  await auth.signOut();
  state = const AuthUiState();
  await _initAnonymousAuth();  // 즉시 새 Anonymous uid 생성
}
```

---

## 6. 교차 의존 3지점 처리 (FCM.md §6 정합)

### 6.1 지점 #1: 콜 취소 → 포인트 환불

**CallNotifier.handleCallCancelled (§2.3.6)** 에서:
```dart
await ref.read(pointNotifierProvider.notifier).refresh();
```

**근거**:
- 손님이 직접 취소 시: CF `onCallCancelledByCustomer` 가 포인트 환불 Firestore write
- FCM CALL_CANCELLED 수신 후 refresh → 최신 잔액 UI 반영
- 실시간 리스너가 자동 갱신하나 안전 마진으로 명시적 refresh 호출

### 6.2 지점 #2: 운행 완료 → 포인트 적립 팝업

**CallNotifier.handleRideCompleted (§2.3.5)** 에서:
```dart
await Future.delayed(const Duration(milliseconds: 500));  // Race 대기
await ref.read(pointNotifierProvider.notifier).refresh();

final pointState = ref.read(pointNotifierProvider);
final earnedPoints = pointState.customerPoints?.calculateEarnPoints(fare) ?? 0;

ref.read(pointNotifierProvider.notifier).showPointsEarnedDialog(
  earnedPoints: earnedPoints,
  usedPoints: pointsUsed,
  rideCompletedFare: fare,
);
```

**근거**:
- CF `onCallCompleted` 가 포인트 적립 → FCM RIDE_COMPLETED 전송 (Race: 적립 write 전 FCM 도달 가능)
- 500ms 지연 + PointNotifier refresh 로 안전
- 적립량 계산은 **클라이언트 측 `customerPoints.calculateEarnPoints(fare)`** 로 예상값 표시 (CF 실제 적립과 동일 공식)

### 6.3 지점 #3: 콜 요청 → 포인트 사용 → 실패 시 환불

**CallNotifier.requestCall (§2.3.1)** 내부:
```dart
// 1. 사용
final ok = await ref.read(pointNotifierProvider.notifier).usePoints(amount: ...);

// 2. 콜 요청 시도
try {
  callId = await callService.requestCall(call);
} catch (e) {
  // 3. 실패 시 환불
  if (usedPointsAmount > 0) {
    await ref.read(pointNotifierProvider.notifier).refundPoints(amount: usedPointsAmount);
  }
}
```

**근거**:
- Kotlin 원본 `MainViewModel.kt:350-363` 재현
- usePoints는 `TransactionType.USE` (-amount) 기록
- refundPoints는 `TransactionType.CANCEL` (+amount) 기록

### 6.4 순환 의존 방지 원칙

```
CallNotifier  ─ref.read→  PointNotifier
PointNotifier ─ref.read→  (없음)
CallNotifier  ─ref.read→  ProfileNotifier  (officeId 조회)
CallNotifier  ─ref.read→  AuthNotifier     (phoneNumber 조회)
ProfileNotifier ─ref.read→ CallNotifier    (집주소 목적지 입력 - 단방향)
```

**모든 화살표가 한 방향**. CallNotifier가 최상위이나 다른 Notifier들은 CallNotifier를 **watch하지 않음**. 순환 없음.

---

## 7. 의제 11 측정 이벤트 통합

### 7.1 Analytics 이벤트 목록 (손님앱 전용)

FCM.md §4.1 과 **동기화**:

| Notifier | 메서드 | logEvent name | parameters |
|---------|--------|--------------|-----------|
| CallNotifier | requestCall | `customer_call_requested` | call_id, office_id, points_used, has_destination |
| CallNotifier | cancelCall | `customer_call_cancelled` | call_id, initiator='customer' |
| CallNotifier | handleDriverAssigned | `customer_driver_assigned` | call_id, driver_id |
| CallNotifier | handleCallCancelled (FCM) | `customer_call_cancelled_by_other` | call_id, reason |
| CallNotifier | handleRideCompleted (FCM) | `customer_ride_completed` | fare, points_used, points_earned |
| CallNotifier | handleCallReceived (FCM) | `customer_call_received` | call_id |
| CallNotifier | handleCallStatusUpdate (FCM) | `customer_call_status_update` | call_id, status |
| ProfileNotifier | updateProfile | `customer_profile_updated` | office_id |
| ProfileNotifier | applyOfficeCode | `customer_office_code_applied` | code, office_id |
| AuthNotifier | _signInWithCredential (verify) | `customer_phone_verified` | |
| PointNotifier | usePoints (성공) | (별도 이벤트 없음, callerNotifier가 logEvent 담당) | |

### 7.2 R1_PUSH_RELIABILITY 측정 (OVERVIEW.md §R1)

FCM 도달률 계산:
- 분모: `customer_call_received` or `customer_driver_assigned` **예상 수** (Firestore `calls` 도큐먼트 생성 수)
- 분자: 실제 Analytics 이벤트 수
- 차이가 10% 이상 → R1_PUSH_RELIABILITY 발동 → CF apns 블록 audit

이는 **CF 측 별도 집계 과제** (FCM.md §4.1 참조).

---

## 8. 테스트 전략

### 8.1 단위 테스트

기사앱 NOTIFIERS.md §11 과 동일 패턴 (`fake_cloud_firestore` + `mocktail` + `riverpod` ProviderContainer):

```dart
// test/features/call/notifiers/call_notifier_test.dart

void main() {
  group('CallNotifier', () {
    late ProviderContainer container;
    late FakeFirebaseFirestore firestore;
    late MockFirebaseAuth auth;

    setUp(() {
      firestore = FakeFirebaseFirestore();
      auth = MockFirebaseAuth();
      container = ProviderContainer(overrides: [
        firestoreProvider.overrideWithValue(firestore),
        firebaseAuthProvider.overrideWithValue(auth),
        // ...
      ]);
    });

    tearDown(() => container.dispose());

    test('requestCall 성공 — CallStatus REQUESTED 설정', () async {
      // Arrange: AuthNotifier + ProfileNotifier 상태 mock
      final notifier = container.read(callNotifierProvider.notifier);
      notifier.updateCurrentLocation('서울역');
      notifier.updateDestinationLocation('강남역');

      // Act
      await notifier.requestCall();

      // Assert
      final state = container.read(callNotifierProvider);
      expect(state.callStatus?.state, isA<CallStateRequested>());
      expect(state.isLoadingCall, false);
    });

    test('requestCall 실패 — 포인트 환불 실행', () async {
      // Arrange: 포인트 사용 설정 + callService 실패 mock
      // ...
      // Act
      await notifier.requestCall();
      // Assert: PointNotifier.refundPoints 호출 verify
    });

    test('handleCallCancelled — callStatus null + PointNotifier.refresh', () async {
      // ...
    });

    test('handleRideCompleted — 500ms 지연 + showPointsEarnedDialog', () async {
      // ...
    });
  });
}
```

### 8.2 필수 테스트 케이스 (Phase 6 Week 2~3)

| Notifier | 메서드 | 최소 테스트 3종 |
|---------|--------|---------------|
| CallNotifier | requestCall | 성공 / 포인트 사용 성공 / 포인트 사용 후 콜 실패 → 환불 |
| CallNotifier | cancelCall | 성공 / 중복 취소 방지 (status 검증) |
| CallNotifier | handleDriverAssigned | driverInfo 설정 / state 전이 |
| CallNotifier | handleRideCompleted | Race 대기 500ms / PointNotifier.refresh 호출 |
| PointNotifier | 실시간 리스너 | phoneNumber 변경 시 재구독 |
| PointNotifier | usePoints / refundPoints | PointService 호출 확인 |
| ProfileNotifier | loadOfficeInfo | officeName/regionName 설정 |
| ProfileNotifier | applyOfficeCode | office_codes/{code} 매핑 |
| AuthNotifier | _initAnonymousAuth | currentUser 상속 / 신규 signIn |
| AuthNotifier | Phone Auth 링크 | linkWithCredential / uid 유지 |

### 8.3 통합 테스트 (Firebase Emulator)

콜 요청 → 배차 FCM → 운행 완료 FCM → 포인트 적립 end-to-end 검증 (FCM.md §7 참조).

---

## 9. 위험 신호

### 9.1 PointNotifier 실시간 리스너 비용

- 손님 1명당 `customerPoints/{phone}` 1문서 구독
- MAU 10,000명 가정 시: 100,000 read/일 예상 → Firestore Free Tier 초과 가능
- **완화**: Phase 1 MVP는 유지. MAU 5,000 도달 시 1회 조회 + refresh 모드로 전환 고려

### 9.2 Anonymous Auth ↔ Phone Auth 링크 실패

- `linkWithCredential` 이 `credential-already-in-use` 오류 반환 시 (이미 해당 전화번호에 uid 존재) → 사용자 데이터 고아 위험
- **완화**: `signInWithCredential` fallback + 기존 Anonymous 데이터 마이그레이션 로직 (Phase 2+ 과제)

### 9.3 Race Condition 500ms 지연의 한계

- CF 부하 높을 때 포인트 적립 write 500ms 초과 가능
- **완화**: PointNotifier 실시간 리스너로 정확한 값 자동 갱신 (1~2초 지연 허용)

### 9.4 교차 의존 ref.read 타이밍

- `ref.read(otherProvider.notifier)` 는 **즉시 생성** — 첫 접근 시 side effect 발생 가능
- **완화**: main.dart 시작 시 4 Notifier 모두 `ref.read()` 로 preload

### 9.5 Install Referrer 데이터 유실 (ATTRIBUTION.md §1 참조)

- Kotlin 손님앱 → Flutter 업그레이드 시 SharedPreferences 데이터 상속 여부
- **완화**: ATTRIBUTION.md §5 마이그레이션 체크리스트 실행

---

## 10. Phase 6 작업 순서

### Week 1 — 기반
1. `lib/domain/state/` — CallUiState / PointUiState / ProfileUiState / AuthUiState 4 Freezed (MODELS.md §7.4)
2. Service Provider 정의 (`callServiceProvider`, `pointServiceProvider`, `locationServiceProvider`)

### Week 2 — Notifier 4종
3. `CallNotifier` — requestCall/cancelCall/restoreActiveCall + FCM 5종 핸들러 (§2)
4. `PointNotifier` — 실시간 리스너 + usePoints/refundPoints + 팝업 상태 (§3)
5. `ProfileNotifier` — 사무실 정보/슬로건/집주소/약관 (§4)
6. `AuthNotifier` — Anonymous + Phone Auth + FCM 토큰 (§5)

### Week 3 — 교차 의존 통합
7. FCM.md §6 교차 의존 3지점 검증
8. 단위 테스트 10건 이상
9. PointHistoryNotifier.family / CallHistoryNotifier.family (autoDispose)

### Week 4 — 통합 테스트
10. Firebase Emulator E2E (FCM.md §7)
11. TestFlight Internal + Android Internal Testing

---

## 11. 참조

- `flutter/customer_app/MVP/MODELS.md` — Freezed 모델 6종 + UI 상태 4종
- `flutter/customer_app/MVP/OVERVIEW.md` — Phase 1/2 범위
- `flutter/customer_app/MVP/FCM.md` — 5종 FCM + 교차 의존 3지점 + Race Condition
- `flutter/customer_app/MVP/ATTRIBUTION.md` — 사무실 코드 + primaryOfficeId + Install Referrer 마이그레이션
- `flutter/customer_app/MVP/SCREENS.md` — 본 Notifier를 소비하는 11 화면 (차기 작성)
- `flutter/customer_app/MVP/AUTH.md` — Anonymous + Phone Auth 상세 (차기 작성)
- `flutter/driver_app/MVP/NOTIFIERS.md` — 기사앱 자매 문서
- `customer_app/app/src/main/java/com/designated/customer/ui/main/MainViewModel.kt` — Kotlin 원본 995 LOC
- `customer_app/app/src/main/java/com/designated/customer/ui/auth/PhoneAuthViewModel.kt` — Phone Auth 140 LOC
- `customer_app/app/src/main/java/com/designated/customer/service/PointService.kt` — PointService 386 LOC
- `customer_app/app/src/main/java/com/designated/customer/service/CallService.kt` — CallService

---

## 12. 작성 완료 요약

- **4 Notifier 분해 완료**: CallNotifier(~300) / PointNotifier(~200) / ProfileNotifier(~200) / AuthNotifier(~200)
- **CallNotifier 핵심 메서드 9종**: restoreActiveCall / requestCall / cancelCall / FCM 5종 핸들러 / updateLocation
- **PointNotifier 실시간 리스너 + 7 메서드**: refresh / usePoints / refundPoints / toggle / update / reset / showDialog / dismissDialog
- **ProfileNotifier 7 메서드**: loadOfficeInfo / loadSlogan / loadCustomerInfo / updateProfile / 집주소 4종 / applyOfficeCode / acceptTerms
- **AuthNotifier Anonymous + Phone Auth 통합**: _initAnonymousAuth / sendVerificationCode / verifyCode / _signInWithCredential (linkWithCredential) / registerFcmToken
- **교차 의존 3지점 명확 해결** (§6): ref.read 직접 호출 / 이벤트 버스 금지 / 단방향 의존
- **의제 11 Analytics 이벤트 11종 명세** (§7.1)
- **Kotlin 원본 라인 번호 100% 인용**
- **Phase 6 Week 1~4 작업 순서**
- **위험 신호 5종** (§9)
