# 기사앱 Enum 매핑 (Kotlin → Dart)

> **의제 14-A 결정 반영**: Dart 3 sealed class 적극 활용. Kotlin sealed class ↔ Dart sealed class 1:1 매핑.
> **현재 Flutter 상태**: `driver_app_flutter/lib/core/constants/app_constants.dart`에 String 상수로 정의됨 → sealed class로 점진 마이그레이션 (의제 14-A 추가 식별 조치 #15).
> **작성일**: 2026-04-16

---

## 마이그레이션 원칙

1. **현재 Flutter 코드는 String 상수 방식** (`AppConstants.callStatusWaiting = 'WAITING'` 등). Phase 1 MVP 시점에 **점진적**으로 sealed class로 전환
2. Firestore 직렬화는 **upper-case String 유지** (Kotlin과 100% 동일). `toJson()` / `fromString()` 메서드로 변환
3. switch expression(Dart 3+) 활용으로 컴파일 타임 exhaustiveness 보장
4. `app_constants.dart`의 String 상수는 **deprecated 마킹 후 단계적 제거**, sealed class를 단일 원본으로

---

## 1. CallStatus (콜 상태 11종)

### Kotlin 원본
경로: `driver_app/app/src/main/java/com/designated/driverapp/data/Constants.kt` (또는 enum 정의 파일)

11개 상태값:
- `WAITING`, `ASSIGNED`, `ACCEPTED`, `IN_PROGRESS`, `AWAITING_SETTLEMENT`, `COMPLETED`
- 취소 3종: `CANCELED` (관리자), `CANCELLED_BY_DRIVER` (기사 cancelTrip), `CANCELLED_BY_CUSTOMER` (고객, ACCEPTED/PREPARING까지)
- 기타: `HOLD` (재배차 대기, deprecated 경향), `SHARED_WAITING`, `CLAIMED`

### Flutter Dart 3 sealed class 매핑

```dart
// lib/domain/enums/call_status.dart (신규 권장)

sealed class CallStatus {
  const CallStatus();

  /// Firestore upper-case 문자열 → CallStatus
  static CallStatus fromString(String value) => switch (value) {
        'WAITING' => const CallStatusWaiting(),
        'ASSIGNED' => const CallStatusAssigned(),
        'ACCEPTED' => const CallStatusAccepted(),
        'IN_PROGRESS' => const CallStatusInProgress(),
        'AWAITING_SETTLEMENT' => const CallStatusAwaitingSettlement(),
        'COMPLETED' => const CallStatusCompleted(),
        'CANCELED' => const CallStatusCanceled(),
        'CANCELLED_BY_DRIVER' => const CallStatusCancelledByDriver(),
        'CANCELLED_BY_CUSTOMER' => const CallStatusCancelledByCustomer(),
        'HOLD' => const CallStatusHold(),
        'SHARED_WAITING' => const CallStatusSharedWaiting(),
        'CLAIMED' => const CallStatusClaimed(),
        _ => CallStatusUnknown(value),
      };

  /// Firestore 저장용 upper-case 문자열
  String toJson();

  /// 사용자 표시용 한글
  String get displayName;

  /// 능동 상태 여부 (UI 분기용 — WAITING/ASSIGNED/ACCEPTED/IN_PROGRESS)
  bool get isActive;
}

class CallStatusWaiting extends CallStatus {
  const CallStatusWaiting();
  @override String toJson() => 'WAITING';
  @override String get displayName => '대기 중';
  @override bool get isActive => true;
}

class CallStatusAssigned extends CallStatus {
  const CallStatusAssigned();
  @override String toJson() => 'ASSIGNED';
  @override String get displayName => '배차 됨';
  @override bool get isActive => true;
}

class CallStatusAccepted extends CallStatus {
  const CallStatusAccepted();
  @override String toJson() => 'ACCEPTED';
  @override String get displayName => '수락 완료';
  @override bool get isActive => true;
}

class CallStatusInProgress extends CallStatus {
  const CallStatusInProgress();
  @override String toJson() => 'IN_PROGRESS';
  @override String get displayName => '운행 중';
  @override bool get isActive => true;
}

class CallStatusAwaitingSettlement extends CallStatus {
  const CallStatusAwaitingSettlement();
  @override String toJson() => 'AWAITING_SETTLEMENT';
  @override String get displayName => '정산 대기';
  @override bool get isActive => true;
}

class CallStatusCompleted extends CallStatus {
  const CallStatusCompleted();
  @override String toJson() => 'COMPLETED';
  @override String get displayName => '완료';
  @override bool get isActive => false;
}

class CallStatusCanceled extends CallStatus {
  const CallStatusCanceled();
  @override String toJson() => 'CANCELED';
  @override String get displayName => '관리자 취소';
  @override bool get isActive => false;
}

class CallStatusCancelledByDriver extends CallStatus {
  const CallStatusCancelledByDriver();
  @override String toJson() => 'CANCELLED_BY_DRIVER';
  @override String get displayName => '기사 취소';
  @override bool get isActive => false;
}

class CallStatusCancelledByCustomer extends CallStatus {
  const CallStatusCancelledByCustomer();
  @override String toJson() => 'CANCELLED_BY_CUSTOMER';
  @override String get displayName => '고객 취소';
  @override bool get isActive => false;
}

class CallStatusHold extends CallStatus {
  const CallStatusHold();
  @override String toJson() => 'HOLD';
  @override String get displayName => '재배차 대기';
  @override bool get isActive => false;
}

class CallStatusSharedWaiting extends CallStatus {
  const CallStatusSharedWaiting();
  @override String toJson() => 'SHARED_WAITING';
  @override String get displayName => '공유콜 대기';
  @override bool get isActive => true;
}

class CallStatusClaimed extends CallStatus {
  const CallStatusClaimed();
  @override String toJson() => 'CLAIMED';
  @override String get displayName => '공유콜 수임';
  @override bool get isActive => false;
}

/// Firestore 데이터 손상 또는 신규 값 대응 (forward compatibility)
class CallStatusUnknown extends CallStatus {
  final String rawValue;
  const CallStatusUnknown(this.rawValue);
  @override String toJson() => rawValue;
  @override String get displayName => '알 수 없음';
  @override bool get isActive => false;
}
```

### 사용 예시

```dart
// Firestore 읽기
final statusRaw = doc.data()?['status'] as String? ?? '';
final status = CallStatus.fromString(statusRaw);

// switch expression (Dart 3+)
final action = switch (status) {
  CallStatusWaiting() => '대기 중인 콜',
  CallStatusAssigned() => '수락/거절 화면',
  CallStatusAccepted() => '운행 준비 화면',
  CallStatusInProgress() => '운행 중 화면',
  CallStatusAwaitingSettlement() => '정산 화면',
  CallStatusCompleted() => '완료, 다음 콜 대기',
  CallStatusCanceled() ||
  CallStatusCancelledByDriver() ||
  CallStatusCancelledByCustomer() => '취소 알림 + 홈 이동',
  _ => '기본 처리',
};

// Firestore 쓰기
await callRef.update({'status': status.toJson()});
```

### 상태 전이 다이어그램 (참조)

```
WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED
            ↓          ↓             ↓
        (타임아웃)   (거절)        (운행취소)
        → WAITING   → WAITING     → CANCELLED_BY_DRIVER
```

상세는 `ios/SHARED_LOGIC.md` §2 참조.

---

## 2. DriverStatus (기사 상태 9종) — kotlin-expert MODELS.md 정정 반영

### Kotlin 원본
경로: `driver_app/app/src/main/java/com/designated/driverapp/data/model/DriverStatus.kt:3-12`

9개 상태값 (Kotlin 원본 9종 전부):
- `OFFLINE` (로그아웃 또는 연결 끊김)
- `ONLINE` / `WAITING` (대기 중, 배차 가능)
- `ASSIGNED` (배차됨, 수락 대기)
- **`ACCEPTED`** (수락 직후 중간 상태 — 추가 확인)
- `PREPARING` (수락 후 운행 준비)
- `ON_TRIP` (운행 중)
- **`PENDING_CONFIRM`** (업무마감 후 매니저 확인 대기 — 추가 확인)

### Flutter Dart 매핑

```dart
// lib/domain/enums/driver_status.dart

sealed class DriverStatus {
  const DriverStatus();

  static DriverStatus fromString(String value) => switch (value) {
        'OFFLINE' => const DriverStatusOffline(),
        'ONLINE' => const DriverStatusOnline(),
        'WAITING' => const DriverStatusWaiting(),
        'ASSIGNED' => const DriverStatusAssigned(),
        'PREPARING' => const DriverStatusPreparing(),
        'ON_TRIP' => const DriverStatusOnTrip(),
        _ => DriverStatusUnknown(value),
      };

  String toJson();
  String get displayName;
  /// 배차 수신 가능 여부
  bool get canReceiveCall;
}

class DriverStatusOffline extends DriverStatus {
  const DriverStatusOffline();
  @override String toJson() => 'OFFLINE';
  @override String get displayName => '오프라인';
  @override bool get canReceiveCall => false;
}

class DriverStatusOnline extends DriverStatus {
  const DriverStatusOnline();
  @override String toJson() => 'ONLINE';
  @override String get displayName => '온라인';
  @override bool get canReceiveCall => true;
}

class DriverStatusWaiting extends DriverStatus {
  const DriverStatusWaiting();
  @override String toJson() => 'WAITING';
  @override String get displayName => '대기 중';
  @override bool get canReceiveCall => true;
}

class DriverStatusAssigned extends DriverStatus {
  const DriverStatusAssigned();
  @override String toJson() => 'ASSIGNED';
  @override String get displayName => '배차됨';
  @override bool get canReceiveCall => false;
}

class DriverStatusAccepted extends DriverStatus {
  const DriverStatusAccepted();
  @override String toJson() => 'ACCEPTED';
  @override String get displayName => '수락함';
  @override bool get canReceiveCall => false;
}

class DriverStatusPreparing extends DriverStatus {
  const DriverStatusPreparing();
  @override String toJson() => 'PREPARING';
  @override String get displayName => '운행 준비';
  @override bool get canReceiveCall => false;
}

class DriverStatusOnTrip extends DriverStatus {
  const DriverStatusOnTrip();
  @override String toJson() => 'ON_TRIP';
  @override String get displayName => '운행 중';
  @override bool get canReceiveCall => false;
}

class DriverStatusPendingConfirm extends DriverStatus {
  const DriverStatusPendingConfirm();
  @override String toJson() => 'PENDING_CONFIRM';
  @override String get displayName => '정산 대기';
  @override bool get canReceiveCall => false;
}

class DriverStatusUnknown extends DriverStatus {
  final String rawValue;
  const DriverStatusUnknown(this.rawValue);
  @override String toJson() => rawValue;
  @override String get displayName => '알 수 없음';
  @override bool get canReceiveCall => false;
}
```

### 상태 전이 (참조)

```
ONLINE/WAITING → ASSIGNED → PREPARING → ON_TRIP → WAITING (완료 후 복귀)
                                                  → PENDING_CONFIRM (업무마감 시, DailySettlementStatus)
```

**ONLINE vs WAITING**: Kotlin 원본은 두 값을 거의 동등 취급 (`oncallassigned`가 둘 다 배차 후보로 포함). UI 표시 차이만.

---

## 3. DailySettlementStatus (일일 정산 상태 4종) — kotlin-expert MODELS.md 정정 반영

### Kotlin 원본
경로: `driver_app/.../data/model/SettlementModels.kt:288-293`

4개 상태값 (Kotlin 원본):
- **`WORKING`** (근무 중, 마감 전)
- `PENDING_CONFIRM` (기사 마감 완료, 매니저 확인 대기)
- `CONFIRMED` (매니저 확인 완료)
- **`REJECTED`** (매니저 거절, 재제출 필요)

⚠️ **이전 잘못된 정의** (`TRANSFERRED / SETTLED`)는 `CarryOverStatus` 소속이며 본 enum과 별도. §6 참조.

### Flutter Dart 매핑

```dart
// lib/domain/enums/daily_settlement_status.dart

sealed class DailySettlementStatus {
  const DailySettlementStatus();

  static DailySettlementStatus fromString(String value) => switch (value) {
        'WORKING' => const DailySettlementStatusWorking(),
        'PENDING_CONFIRM' => const DailySettlementStatusPendingConfirm(),
        'CONFIRMED' => const DailySettlementStatusConfirmed(),
        'REJECTED' => const DailySettlementStatusRejected(),
        _ => DailySettlementStatusUnknown(value),
      };

  String toJson();
  String get displayName;
  /// 정산 진행 단계 (UI progress bar용 1~3, REJECTED는 -1)
  int get step;
}

class DailySettlementStatusWorking extends DailySettlementStatus {
  const DailySettlementStatusWorking();
  @override String toJson() => 'WORKING';
  @override String get displayName => '근무 중';
  @override int get step => 0;
}

class DailySettlementStatusPendingConfirm extends DailySettlementStatus {
  const DailySettlementStatusPendingConfirm();
  @override String toJson() => 'PENDING_CONFIRM';
  @override String get displayName => '확인 대기';
  @override int get step => 1;
}

class DailySettlementStatusConfirmed extends DailySettlementStatus {
  const DailySettlementStatusConfirmed();
  @override String toJson() => 'CONFIRMED';
  @override String get displayName => '확인 완료';
  @override int get step => 2;
}

class DailySettlementStatusRejected extends DailySettlementStatus {
  const DailySettlementStatusRejected();
  @override String toJson() => 'REJECTED';
  @override String get displayName => '반려됨';
  @override int get step => -1;
}

class DailySettlementStatusUnknown extends DailySettlementStatus {
  final String rawValue;
  const DailySettlementStatusUnknown(this.rawValue);
  @override String toJson() => rawValue;
  @override String get displayName => '알 수 없음';
  @override int get step => 0;
}
```

### 정산 플로우 (DailySettlementStatus만)

```
Driver 근무 중 → WORKING
Driver submitDailySettlement → PENDING_CONFIRM
Manager confirmDailySettlement → CONFIRMED
Manager 반려 시 → REJECTED → 기사 재제출 → PENDING_CONFIRM
```

이체/수령 후속 플로우는 §6 `CarryOverStatus`로 분리. 상세는 `ios/SHARED_LOGIC.md` §4 참조.

---

## 4. ApprovalStatus (기사 승인 상태 3종)

### Kotlin 원본
경로: `driver_app/.../data/Constants.kt`

3개 상태값:
- `PENDING` (가입 신청, 매니저 승인 대기)
- `APPROVED` (승인 완료, 로그인 가능)
- `REJECTED` (승인 거부, 로그인 차단)

### Flutter Dart 매핑

```dart
// lib/domain/enums/approval_status.dart

enum ApprovalStatus {
  pending('PENDING', '승인 대기'),
  approved('APPROVED', '승인 완료'),
  rejected('REJECTED', '승인 거부');

  const ApprovalStatus(this.json, this.displayName);
  final String json;
  final String displayName;

  static ApprovalStatus fromString(String value) =>
      ApprovalStatus.values.firstWhere(
        (e) => e.json == value,
        orElse: () => ApprovalStatus.pending,
      );

  String toJson() => json;
}
```

**일반 enum 사용 이유**: 상태별 추가 동작/필드 없이 단순 라벨만 필요. sealed class 오버헤드 불필요.

---

## 5. PaymentMethod (결제 방식 5종)

### Kotlin 원본
경로: `driver_app/.../ui/.../*Settlement*.kt`

5개 결제 방식 (`ios/SHARED_LOGIC.md` §4):
- `현금` (cash)
- `이체` (transfer / 무통장 입금)
- `외상` (credit / 다음에 받음)
- `포인트` (points only)
- `현금+포인트` (cash + points)

### Flutter Dart 매핑

```dart
// lib/domain/enums/payment_method.dart

enum PaymentMethod {
  cash('현금'),
  transfer('이체'),
  credit('외상'),
  points('포인트'),
  cashAndPoints('현금+포인트');

  const PaymentMethod(this.displayName);
  final String displayName;

  /// Firestore 저장 시 한글 그대로 (Kotlin 원본과 일치)
  String toJson() => displayName;

  static PaymentMethod fromString(String value) =>
      PaymentMethod.values.firstWhere(
        (e) => e.displayName == value,
        orElse: () => PaymentMethod.cash,
      );

  /// 정산 시 cashReceived 계산 (`ios/SHARED_LOGIC.md` §4 결제 방식별 현금 계산)
  int calculateCashReceived(int fare, {int? userInputCash}) => switch (this) {
        PaymentMethod.cash => fare,
        PaymentMethod.cashAndPoints => userInputCash ?? 0,
        PaymentMethod.transfer || PaymentMethod.credit => 0,
        PaymentMethod.points => 0,
      };

  /// 정산 시 creditAmount 계산
  int calculateCreditAmount(int fare, {int? cashReceived, int? pointsUsed}) =>
      switch (this) {
        PaymentMethod.cash => 0,
        PaymentMethod.cashAndPoints =>
            (fare - (cashReceived ?? 0) - (pointsUsed ?? 0)).clamp(0, fare),
        PaymentMethod.transfer || PaymentMethod.credit => fare,
        PaymentMethod.points => 0,
      };
}
```

**한글 저장 주의**: Kotlin 원본이 Firestore에 한글 그대로 저장(`"현금"`, `"이체"` 등). Flutter 측 `toJson()` 결과도 한글이어야 호환.

---

## 6. CarryOverStatus (이월금 상태 3종) — kotlin-expert MODELS.md 정정 반영

### Kotlin 원본
경로: `driver_app/.../data/model/SettlementModels.kt:279-283`

3개 상태값 (정산 이체 단계, DailySettlementStatus와 별도):
- **`PENDING`** (이월금 발생, 매니저 이체 대기) — `CONFIRMED` 아님
- `TRANSFERRED` (이체 완료, 기사 수령 확인 대기)
- `SETTLED` (기사 수령 확인 → 종료)

### Flutter Dart 매핑

```dart
// lib/domain/enums/carry_over_status.dart

enum CarryOverStatus {
  pending('PENDING', '이체 대기'),
  transferred('TRANSFERRED', '이체 완료'),
  settled('SETTLED', '수령 확인');

  const CarryOverStatus(this.json, this.displayName);
  final String json;
  final String displayName;

  static CarryOverStatus fromString(String value) =>
      CarryOverStatus.values.firstWhere(
        (e) => e.json == value,
        orElse: () => CarryOverStatus.pending,
      );

  String toJson() => json;
}
```

### 이월금 플로우 (CarryOverStatus만)

```
정산 마감 시 이월금 발생 → PENDING
Manager transferCarryOver → TRANSFERRED
Driver confirmReceiveCarryOver → SETTLED
```

`DailySettlementStatus`(§3)와 독립된 별도 워크플로우.

---

## 6.1 PresenceStatus (기사 Presence 상태 4종) — kotlin-expert NOTIFIERS.md 보완 반영

### Kotlin 원본
경로: `driver_app/.../service/PresenceManager.kt:37-41`

4개 상태값:
- `online` (포그라운드, Realtime DB 연결 살아있음)
- `background` (앱 백그라운드, 연결 살아있음)
- `offline` (Realtime DB 연결 끊김 또는 로그아웃)
- `unknown` (초기 상태 또는 데이터 손상 fallback)

### Flutter Dart 매핑

```dart
// lib/domain/enums/presence_status.dart

sealed class PresenceStatus {
  const PresenceStatus();

  static PresenceStatus fromString(String? value) => switch (value) {
        'online' => const PresenceStatusOnline(),
        'background' => const PresenceStatusBackground(),
        'offline' => const PresenceStatusOffline(),
        _ => const PresenceStatusUnknown(),
      };

  String toJson();
  String get displayName;
  /// CF checkAssignedTimeout이 "문제"로 취급하는지 여부
  /// online/background = 정상, offline만 문제
  bool get isProblematic;
}

class PresenceStatusOnline extends PresenceStatus {
  const PresenceStatusOnline();
  @override String toJson() => 'online';
  @override String get displayName => '온라인';
  @override bool get isProblematic => false;
}

class PresenceStatusBackground extends PresenceStatus {
  const PresenceStatusBackground();
  @override String toJson() => 'background';
  @override String get displayName => '백그라운드';
  @override bool get isProblematic => false;
}

class PresenceStatusOffline extends PresenceStatus {
  const PresenceStatusOffline();
  @override String toJson() => 'offline';
  @override String get displayName => '오프라인';
  @override bool get isProblematic => true;
}

class PresenceStatusUnknown extends PresenceStatus {
  const PresenceStatusUnknown();
  @override String toJson() => 'unknown';
  @override String get displayName => '알 수 없음';
  @override bool get isProblematic => false;
}
```

**소문자 표기 주의**: `CallStatus`(대문자) / `DriverStatus`(대문자)와 달리 Presence는 **소문자**. Kotlin 원본 일치.

**저장 위치**: Firebase Realtime DB `presence/drivers/{uid}.status` (Firestore 아님).

---

## 7. NotificationType (FCM 메시지 타입 6종)

### Kotlin 원본
경로: `driver_app/.../service/MyFirebaseMessagingService.kt`

6개 메시지 타입 (`ios/SHARED_LOGIC.md` §5):
- `call_assigned` (배차 알림, 소문자)
- `call_cancelled` (취소 알림, 소문자)
- `SETTLEMENT_FINALIZED` (정산 확정, 대문자)
- `SETTLEMENT_CONFIRMED` (매니저 승인, 대문자)
- `SETTLEMENT_REJECTED` (매니저 반려, 대문자)
- `CARRYOVER_TRANSFERRED` (이월금 이체 완료, 대문자)

**대소문자 혼재**: Kotlin 원본 그대로 보존 (Firestore/CF payload 호환).

### Flutter Dart 매핑

```dart
// lib/domain/enums/notification_type.dart

enum NotificationType {
  callAssigned('call_assigned', '새 배차'),
  callCancelled('call_cancelled', '배차 취소'),
  settlementFinalized('SETTLEMENT_FINALIZED', '정산 확정'),
  settlementConfirmed('SETTLEMENT_CONFIRMED', '정산 승인'),
  settlementRejected('SETTLEMENT_REJECTED', '정산 반려'),
  carryoverTransferred('CARRYOVER_TRANSFERRED', '미수령금 이체');

  const NotificationType(this.json, this.displayName);
  final String json;
  final String displayName;

  static NotificationType? fromString(String value) {
    for (final e in NotificationType.values) {
      if (e.json == value) return e;
    }
    return null;
  }

  /// iOS Time Sensitive 적용 여부 (의제 7)
  bool get isTimeSensitive => this == NotificationType.callAssigned;
}
```

---

## 8. CustomerGrade (손님 등급 4종) — 손님앱 전용 (참조)

손님앱 ENUMS.md에서 별도 정의 예정. 본 기사앱 문서에는 미포함.

```
BRONZE (3% 적립) → SILVER (5%) → GOLD (7%) → VIP (9%)
```

---

## 9. AttributionSource (귀속 소스, 손님앱 전용 — 참조)

손님앱 ATTRIBUTION.md에서 정의 예정.

---

## 마이그레이션 작업 순서

`AppConstants` String 상수 → sealed class 단계적 전환:

1. **Phase 6 Week 1**: 새 `lib/domain/enums/` 디렉토리에 본 문서의 sealed class 8종 작성
2. **Phase 6 Week 2**: `DriverWorkflowNotifier` 등 Notifier에서 `AppConstants.statusXXX` → `DriverStatus.fromString()` 전환
3. **Phase 6 Week 3**: 화면 위젯에서 String 비교 → sealed class switch expression
4. **Phase 6 Week 4**: `AppConstants` String 상수 deprecated 마킹 + 단계적 제거

**주의**: Firestore 저장값은 `toJson()` 결과 String이어야 함. Kotlin과 100% 일치 보장.

---

## 참조

- `flutter/driver_app/MVP/MODELS.md` — 본 enum을 사용하는 데이터 모델 (CallData.status, DriverData.status 등)
- `flutter/driver_app/MVP/FIRESTORE.md` — fromJson/toJson 직렬화 패턴
- `ios/SHARED_LOGIC.md` §2~§5 — 상태 전이·정산 공식 원본
- `driver_app_flutter/lib/core/constants/app_constants.dart` — 현재 String 상수 (점진 마이그레이션 대상)
- `driver_app/app/src/main/java/com/designated/driverapp/data/Constants.kt` — Kotlin 원본
