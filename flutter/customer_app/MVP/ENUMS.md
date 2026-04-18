# 손님앱 Enum 매핑 (Kotlin → Dart)

> **기사앱 ENUMS.md와 자매 문서**. 손님앱 전용 enum 3종 (기사앱과 상태값·용도 상이).
> **의제 14-A 결정 반영**: Dart 3 sealed class + enum with behavior.
> **작성일**: 2026-04-16 (kotlin-expert MODELS.md §10 발견 반영)

---

## 손님앱 vs 기사앱 Enum 차이

| 항목 | 기사앱 ENUMS.md | 손님앱 ENUMS.md |
|------|---------------|----------------|
| 서버 상태 | CallStatus 11종 (Firestore 원본) | — (기사앱 상태 공유) |
| UI 상태 | — | **CallState 6종** (손님 UI 분기용) |
| 기사 상태 | DriverStatus 9종 | — |
| 정산 | DailySettlementStatus / CarryOverStatus | — |
| 결제 | PaymentMethod 5종 | — |
| 승인 | ApprovalStatus | — |
| Presence | PresenceStatus 4종 | — |
| FCM 타입 | NotificationType 6종 | — (손님앱 5종 FCM.md 참조) |
| **손님 고유** | — | **CustomerGrade, TransactionType** |

손님앱은 **기사앱 enum을 공유하지 않음** — CallState는 손님 UI 관점의 별개 상태 머신 (`CustomerCall.status` 파싱 후 매핑).

---

## 1. CallState (손님 UI 콜 상태 6종)

### Kotlin 원본
경로: `customer_app/app/src/main/java/com/designated/customer/ui/main/MainViewModel.kt:46-54` (추정 — MODELS.md §10 참조)

손님 UI 분기용 6개 상태값. **기사앱 `CallStatus`와 다름** (서버 Firestore 상태가 아닌, 손님 앱 UI 관점 집계 상태):
- `REQUESTED` (콜 요청 제출 직후, 배차 대기)
- `ASSIGNED` (기사 배차됨, 수락 대기)
- `DRIVER_ARRIVING` (기사 수락 + ACCEPTED/PREPARING, 픽업 대기)
- `IN_PROGRESS` (운행 중)
- `COMPLETED` (운행 완료 + 정산 완료)
- `CANCELLED` (취소 — 관리자/기사/손님/타임아웃 통합)

### Firestore 원본 매핑

Firestore `calls/{callId}.status`는 기사앱 관점 11종(CallStatus)이나, 손님 UI는 6종으로 **축소 매핑**:

```dart
/// Firestore 원본 status → 손님 UI CallState 매핑
CallState mapFirestoreStatusToUi(String firestoreStatus) => switch (firestoreStatus) {
      'WAITING' || 'SHARED_WAITING' => CallState.requested,
      'ASSIGNED' => CallState.assigned,
      'ACCEPTED' || 'PREPARING' => CallState.driverArriving,
      'IN_PROGRESS' => CallState.inProgress,
      'AWAITING_SETTLEMENT' || 'COMPLETED' => CallState.completed,
      'CANCELED' ||
      'CANCELLED_BY_DRIVER' ||
      'CANCELLED_BY_CUSTOMER' ||
      'HOLD' ||
      'CLAIMED' => CallState.cancelled,
      _ => CallState.cancelled,  // 안전 기본값
    };
```

### Flutter Dart sealed class

```dart
// lib/core/domain/enums/call_state.dart

sealed class CallState {
  const CallState();

  const factory CallState.requested() = _Requested;
  const factory CallState.assigned() = _Assigned;
  const factory CallState.driverArriving() = _DriverArriving;
  const factory CallState.inProgress() = _InProgress;
  const factory CallState.completed() = _Completed;
  const factory CallState.cancelled() = _Cancelled;

  static CallState fromFirestoreStatus(String firestoreStatus) =>
      switch (firestoreStatus) {
        'WAITING' || 'SHARED_WAITING' => const CallState.requested(),
        'ASSIGNED' => const CallState.assigned(),
        'ACCEPTED' || 'PREPARING' => const CallState.driverArriving(),
        'IN_PROGRESS' => const CallState.inProgress(),
        'AWAITING_SETTLEMENT' || 'COMPLETED' => const CallState.completed(),
        'CANCELED' ||
        'CANCELLED_BY_DRIVER' ||
        'CANCELLED_BY_CUSTOMER' ||
        'HOLD' ||
        'CLAIMED' => const CallState.cancelled(),
        _ => const CallState.cancelled(),
      };

  /// UI 표시 한글
  String get displayName => switch (this) {
        _Requested() => '배차 요청 중',
        _Assigned() => '기사 배차됨',
        _DriverArriving() => '기사 오는 중',
        _InProgress() => '운행 중',
        _Completed() => '완료',
        _Cancelled() => '취소됨',
      };

  /// 취소 가능 여부 (CANCELLED_BY_CUSTOMER 허용 단계)
  /// ACCEPTED/PREPARING까지 허용 (CLAUDE.md 3/24)
  bool get isCancellable => switch (this) {
        _Requested() || _Assigned() || _DriverArriving() => true,
        _ => false,
      };

  /// 최종 상태 여부 (UI 플로우 종료)
  bool get isFinal => switch (this) {
        _Completed() || _Cancelled() => true,
        _ => false,
      };
}

class _Requested extends CallState { const _Requested(); }
class _Assigned extends CallState { const _Assigned(); }
class _DriverArriving extends CallState { const _DriverArriving(); }
class _InProgress extends CallState { const _InProgress(); }
class _Completed extends CallState { const _Completed(); }
class _Cancelled extends CallState { const _Cancelled(); }
```

### 사용 예시

```dart
// FCM call_status_update 수신 시
final firestoreStatus = fcmData['status'] as String;
final uiState = CallState.fromFirestoreStatus(firestoreStatus);

ref.read(callNotifierProvider.notifier).updateState(uiState);

// UI 분기
final state = ref.watch(callNotifierProvider).callState;
final view = switch (state) {
  _Requested() => RequestedView(),
  _Assigned() => AssignedView(),
  _DriverArriving() => DriverArrivingView(),
  _InProgress() => InProgressView(),
  _Completed() || _Cancelled() => HistoryView(),
};

// 취소 버튼 표시 조건
if (state.isCancellable) {
  return CancelButton();
}
```

---

## 2. CustomerGrade (손님 등급 4종)

### Kotlin 원본
경로: `customer_app/app/src/main/java/com/designated/customer/data/model/CustomerGrade.kt:7-94`

Kotlin `enum class` 6 필드 보유:
- **4개 등급**: `BRONZE`(0+, 3%) / `SILVER`(10+, 5%) / `GOLD`(30+, 7%) / `VIP`(50+, 9%)
- **필드**: `displayName` (한글), `icon` (이모지), `pointRate` (Double), `minCalls` (Int), `color` (Long ARGB)
- **메서드**: `fromCallCount`, `fromString`, `nextGrade`, `getCallsToNextGrade`, `calculatePoints`
- **직렬화**: Firestore 저장은 Kotlin enum `.name` 그대로 (별도 매핑 필드 없음)

### Flutter Dart enhanced enum

> Dart enum 값 이름(`bronze`)과 Firestore 저장값(`BRONZE`) 차이 때문에 `json` 매핑 필드 추가. 이외 필드는 Kotlin과 1:1 대응.

```dart
// lib/core/domain/enums/customer_grade.dart

enum CustomerGrade {
  bronze(
    json: 'BRONZE',
    displayName: '브론즈',
    icon: '🥉',
    pointRate: 0.03,
    minCalls: 0,
    colorArgb: 0xFFCD7F32,
  ),
  silver(
    json: 'SILVER',
    displayName: '실버',
    icon: '🥈',
    pointRate: 0.05,
    minCalls: 10,
    colorArgb: 0xFFC0C0C0,
  ),
  gold(
    json: 'GOLD',
    displayName: '골드',
    icon: '🥇',
    pointRate: 0.07,
    minCalls: 30,
    colorArgb: 0xFFFFD700,
  ),
  vip(
    json: 'VIP',
    displayName: 'VIP',
    icon: '⭐',
    pointRate: 0.09,
    minCalls: 50,
    colorArgb: 0xFFFF6B6B,
  );

  const CustomerGrade({
    required this.json,
    required this.displayName,
    required this.icon,
    required this.pointRate,
    required this.minCalls,
    required this.colorArgb,
  });

  /// Firestore 저장 문자열 (Kotlin enum `.name`과 동일, 대문자)
  final String json;
  /// UI 표시 한글
  final String displayName;
  /// UI 표시 이모지 (Kotlin icon)
  final String icon;
  /// 포인트 적립률 (fare × pointRate, Kotlin과 동일 네이밍)
  final double pointRate;
  /// 승격 기준 최소 콜 수
  final int minCalls;
  /// 등급별 테마 색상 (ARGB Long, Kotlin color와 동일 값)
  final int colorArgb;

  /// Firestore 저장 문자열 → enum (Kotlin fromString 포팅, null/미매칭은 BRONZE)
  static CustomerGrade fromString(String? value) =>
      CustomerGrade.values.firstWhere(
        (e) => e.json == value?.toUpperCase(),
        orElse: () => CustomerGrade.bronze,
      );

  String toJson() => json;

  /// 콜 수 기반 등급 계산 (Kotlin `fromCallCount` 포팅)
  static CustomerGrade fromCallCount(int totalCalls) {
    if (totalCalls >= 50) return CustomerGrade.vip;
    if (totalCalls >= 30) return CustomerGrade.gold;
    if (totalCalls >= 10) return CustomerGrade.silver;
    return CustomerGrade.bronze;
  }

  /// 포인트 적립 계산 (Kotlin `calculatePoints` 포팅)
  int calculatePoints(int fare) => (fare * pointRate).toInt();

  /// 다음 등급 (Kotlin `nextGrade` 포팅, VIP는 null)
  CustomerGrade? get nextGrade => switch (this) {
        CustomerGrade.bronze => CustomerGrade.silver,
        CustomerGrade.silver => CustomerGrade.gold,
        CustomerGrade.gold => CustomerGrade.vip,
        CustomerGrade.vip => null,
      };

  /// 다음 등급까지 남은 콜 수 (Kotlin `getCallsToNextGrade` 포팅, VIP는 null)
  int? callsToNext(int currentCalls) {
    final next = nextGrade;
    if (next == null) return null;
    return next.minCalls - currentCalls;
  }
}
```

### 사용 예시

```dart
// 포인트 계산
final grade = CustomerGrade.fromCallCount(customer.totalCalls);
final earnedPoints = grade.calculatePoints(callFare);

// UI 표시 (등급 뱃지)
Text('${grade.icon} ${grade.displayName}'); // "🥈 실버"
Text('${(grade.pointRate * 100).toInt()}% 적립'); // "5% 적립"
Container(color: Color(grade.colorArgb));

// 승격 유도 UX
final remaining = grade.callsToNext(customer.totalCalls);
if (remaining != null && remaining <= 3) {
  showUpgradeBanner('다음 등급까지 $remaining회 남았어요');
}
```

**중요**: 실제 **포인트 적립은 CF가 처리** (`onCallCompletedUpdateSettlement`). 클라이언트는 표시용 계산만. Race condition 방지 (FCM.md §6.3 RIDE_COMPLETED 500ms 지연 정합).

---

## 3. TransactionType (포인트 거래 유형 5종)

### Kotlin 원본
경로: `customer_app/app/src/main/java/com/designated/customer/data/model/PointTransaction.kt:40-62`

5개 유형 + amount 부호 관용구:
- `EARN` (적립, +): 콜 완료 시
- `USE` (사용, -): 콜 요청 시 포인트 결제
- `EXPIRE` (만료, -): 배치 처리
- `CANCEL` (취소 환불, +): 콜 취소 시 USE 역방향
- `ADMIN` (관리자 조정, ±): 양쪽 부호 가능

### Flutter Dart enum

```dart
// lib/core/domain/enums/transaction_type.dart

enum TransactionType {
  earn('EARN', '적립', 1),
  use('USE', '사용', -1),
  expire('EXPIRE', '만료', -1),
  cancel('CANCEL', '취소 환불', 1),
  admin('ADMIN', '관리자 조정', 0);  // 양쪽 가능

  const TransactionType(this.json, this.displayName, this.sign);

  final String json;
  final String displayName;
  /// amount 예상 부호 (+1: 증가, -1: 감소, 0: 양쪽 가능)
  final int sign;

  static TransactionType fromString(String value) =>
      TransactionType.values.firstWhere(
        (e) => e.json == value,
        orElse: () => TransactionType.admin,
      );

  String toJson() => json;

  /// amount 입력 검증 (클라이언트 측, CF 측에서도 재검증)
  bool isValidAmount(int amount) => switch (sign) {
        1 => amount > 0,    // EARN, CANCEL
        -1 => amount < 0,   // USE, EXPIRE (Kotlin 원본은 양수 저장 후 UI에서 부호 표시 — 확인 필요)
        0 => true,          // ADMIN
        _ => false,
      };

  /// UI 표시 시 부호 포함
  String formatAmount(int amount) {
    final absAmount = amount.abs();
    return switch (this) {
      TransactionType.earn || TransactionType.cancel => '+$absAmount',
      TransactionType.use || TransactionType.expire => '-$absAmount',
      TransactionType.admin => amount >= 0 ? '+$absAmount' : '-$absAmount',
    };
  }
}
```

**주의 사항** (MODELS.md §7.2 기반):
- Kotlin `PointTransaction.amount`는 **부호 없는 Int (항상 양수)** 저장 가능성 있음. Dart 포팅 시 `sign` 활용 여부 확인 필요.
- 또는 DB에 부호 포함 저장 가능. `customer_app/.../PointService.kt` 실제 저장 로직 검증.

---

## 4. 기존 enum 공유 여부 (기사앱 ENUMS.md 참조)

손님앱에서도 필요하나 **기사앱 ENUMS.md 그대로 사용**:

| enum | 경로 | 용도 |
|------|------|------|
| `CallStatus` (11종) | `driver_app/MVP/ENUMS.md` §1 | CF/Firestore 상태 파싱 (CallState 매핑 전 단계) |
| `PaymentMethod` (5종) | `driver_app/MVP/ENUMS.md` §5 | 손님이 콜 요청 시 결제 방식 선택 |

**공유 원칙**: enum은 `lib/core/domain/enums/` 단일 위치. 기사앱/손님앱 각 Flutter 프로젝트에서 상대 경로 import.

**중복 금지**: `CustomerGrade`, `TransactionType`, `CallState`는 **손님앱 전용** — 기사앱 ENUMS.md에 추가하지 않음.

---

## 5. 구현 배치 (Phase 6 Week 1~2)

손님앱 Flutter 프로젝트(`customer_app_flutter/`) 신규 생성 시:

```
customer_app_flutter/
└── lib/
    └── domain/
        └── enums/
            ├── call_state.dart            # 본 문서 §1
            ├── customer_grade.dart        # 본 문서 §2
            ├── transaction_type.dart      # 본 문서 §3
            ├── call_status.dart           # 기사앱 ENUMS.md §1 공유
            ├── payment_method.dart        # 기사앱 ENUMS.md §5 공유
            └── notification_type.dart     # 손님앱용 5종 (FCM.md §2)
```

---

## 6. 참조

- `flutter/driver_app/MVP/ENUMS.md` — 기사앱 자매 문서 (공유 enum)
- `flutter/customer_app/MVP/MODELS.md` §4, §5, §7.2 — CustomerGrade/TransactionType 상세 사용 패턴
- `flutter/customer_app/MVP/FCM.md` §2 — 5종 FCM 핸들링 (NotificationType 손님앱용)
- `flutter/customer_app/MVP/NOTIFIERS.md` — 4 Notifier에서 본 enum 사용
- Kotlin 원본:
  - `customer_app/.../data/model/CustomerGrade.kt:13-40`
  - `customer_app/.../data/model/PointTransaction.kt:40-62`
  - `customer_app/.../ui/main/MainViewModel.kt:46-54` (CallState 추정)
