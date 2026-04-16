# 기사앱 Data 모델 매핑 (Kotlin → Dart Freezed)

> **의제 14-A (Dart 3 sealed class) + 14-G (Freezed `required` + nullable `?` + `@Default()` + `late` 금지) 반영**
> **ENUMS.md의 sealed class 사용** (CallStatus, DriverStatus, DailySettlementStatus 등)
> **원본 기준**: `driver_app/app/src/main/java/com/designated/driverapp/` Kotlin 코드
> **작성일**: 2026-04-16
> **작성자**: kotlin-expert

---

## Dart 3 + Freezed 스타일 가이드 (5줄)

1. **모든 data class는 `@freezed` + `fromJson/toJson` 지원** (build_runner로 자동 생성)
2. **nullable은 명시 `?`**, default는 `@Default()` 어노테이션 사용. Kotlin `= ""` 기본값 → `@Default('')`
3. **`late` 키워드 금지**. 초기화 미확정 값은 nullable로 설계
4. **Firestore `fromJson` 시 안전 파싱**: `as String? ?? ''`, `as int? ?? 0`, `(json['field'] as num?)?.toInt() ?? 0` 패턴 엄수
5. **enum 필드는 ENUMS.md sealed class 사용**: Firestore read 시 `CallStatus.fromString(raw)`, write 시 `status.toJson()`

### Kotlin → Dart 매핑 원칙 (5줄)

| Kotlin | Dart / Freezed | 비고 |
|--------|---------------|-----|
| `val x: String = ""` | `@Default('') String x` | 기본값 보존 |
| `val x: String? = null` | `String? x` | nullable 유지 |
| `Timestamp?` | `DateTime?` | Firestore fromJson 시 `(v as Timestamp?)?.toDate()` |
| `Long` / `Int` | `int` | Dart int는 64bit, Long/Int 구분 없음 |
| `enum class` / Kotlin `sealed` | ENUMS.md sealed class | Firestore String 직렬화 유지 |

---

## 모델 인벤토리 (13종)

| # | 모델명 | Kotlin 파일 | Firestore 경로 | 분류 |
|---|--------|------------|---------------|------|
| 1 | **CallInfo** | `model/CallInfo.kt:28` | `provinces/{p}/cities/{c}/offices/{o}/calls/{callId}` | B (Phase 1 필수) |
| 2 | **Driver** | `model/DriverModel.kt:15` | `provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid}` | B |
| 3 | **PickupDriver** | `model/DriverModel.kt:29` | `provinces/{p}/cities/{c}/offices/{o}/pickup_drivers/{uid}` | D (Phase 2) |
| 4 | **UserSession** | `data/model/UserSession.kt:6` | SharedPreferences (로컬, Firestore 아님) | B |
| 5 | **CustomerPoints** | `data/model/CustomerPoints.kt:6` | `provinces/{p}/cities/{c}/offices/{o}/customerPoints/{phone}` | B (기사앱 읽기 전용) |
| 6 | **SettlementSession** | `data/settlement/SettlementModels.kt:12` | `provinces/{p}/cities/{c}/offices/{o}/settlementSessions/{date}` | B |
| 7 | **SettlementMetadata** | `data/settlement/SettlementModels.kt:54` | SettlementSession.metadata 필드 | B |
| 8 | **SettlementTotals** | `data/settlement/SettlementModels.kt:88` | SettlementSession.totals 필드 | B |
| 9 | **CallSettlement** | `data/settlement/SettlementModels.kt:159` | SettlementSession.calls 배열 요소 | B |
| 10 | **DriverSettlementSummary** | `data/settlement/SettlementModels.kt:249` | 순수 계산 결과 (Firestore 저장 없음) | B |
| 11 | **DriverDailySettlement** | `data/settlement/SettlementModels.kt:299` | `designated_drivers/{uid}.dailySettlement` 서브필드 | B |
| 12 | **DriverCarryOver** | `data/settlement/SettlementModels.kt:369` | `designated_drivers/{uid}.carryOver` 서브필드 | B |
| 13 | **AddressSearchResult** | `data/AddressSearchResult.kt:3` | 외부 API 응답 (Kakao) | B |

**부가 구조체 (로컬 UI 상태, 본 문서에도 포함)**:
- **DriverScreenUiState** (`ui/state/DriverScreenUiState.kt:10`): 홈 화면 단일 UI 상태
- **TodaySettlement** (`viewmodel/DriverViewModel.kt:104`): 오늘 정산 집계 (calls 기반 실시간 계산)
- **TripHistoryItem** (`viewmodel/DriverViewModel.kt:118`): 운행 내역 카드 1건

**Room Entity (로컬 DB, MVP R1로 이관됨)**:
- `PendingSyncEntity` (`data/local/PendingSyncEntity.kt`) — R1 도입 시 Freezed 매핑
- `SettlementCacheEntity` (`data/local/SettlementCacheEntity.kt`) — R1 도입 시 sqflite 테이블

---

## 1. CallInfo (콜 엔티티 — **가장 중요**)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/model/CallInfo.kt:27-76`)

```kotlin
@Parcelize
data class CallInfo(
    var id: String = "",
    val customerName: String = "",
    val phoneNumber: String = "",
    val customerAddress: String? = null,
    val destination: String? = null,
    val detectedTimestamp: Timestamp? = null,
    val timestamp: Timestamp? = null,
    var status: String = Constants.STATUS_WAITING,
    val fare: Int? = null,
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val assignedDriverPhone: String? = null,
    val assignedTimestamp: Timestamp? = null,
    val assignedPickupDriverId: String? = null,
    val deviceName: String? = null,
    val officeId: String = "",
    val provinceId: String = "",
    val cityId: String = "",
    val callType: String? = null,
    val isAppCustomer: Boolean = false,
    val memo: String = "",
    val departure_set: String? = null,
    val destination_set: String? = null,
    val waypoints_set: String? = null,
    val fare_set: Int? = null,
    val trip_summary: String? = null,
    val paymentMethod: String = "",
    val cashAmount: Int? = null,
    val cashReceived: Int? = null,
    val creditAmount: Int? = null,
    val isSummaryConfirmed: Boolean = false,
    val summaryConfirmedTimestamp: Timestamp? = null,
    var settlementStatus: String = Constants.SETTLEMENT_STATUS_PENDING,
    var settlementId: String? = null,
    var claimedDriverId: String? = null,
    var sourceSharedCallId: String? = null,
    val tripSummaryFinal: String? = null,
    val finalFare: Int? = null,
    val fareFinal: Int? = null
) : Parcelable {
    @get:Exclude
    val statusEnum: CallStatus
        get() = CallStatus.fromFirestoreValue(status)
}
```

### Firestore 경로
`provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}`

### Flutter Freezed 매핑

```dart
// lib/domain/models/call_info.dart

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import '../enums/call_status.dart';

part 'call_info.freezed.dart';
part 'call_info.g.dart';

@freezed
class CallInfo with _$CallInfo {
  const CallInfo._();

  const factory CallInfo({
    // 식별자 (필수)
    @Default('') String id,
    @Default('') String officeId,
    @Default('') String provinceId,
    @Default('') String cityId,

    // 고객 정보
    @Default('') String customerName,
    @Default('') String phoneNumber,
    String? customerAddress,
    String? destination,

    // 시간 (Kotlin Timestamp? → Dart DateTime?)
    @TimestampConverter() DateTime? detectedTimestamp,
    @TimestampConverter() DateTime? timestamp,
    @TimestampConverter() DateTime? assignedTimestamp,
    @TimestampConverter() DateTime? summaryConfirmedTimestamp,

    // 상태 (ENUMS.md CallStatus sealed class)
    @CallStatusConverter() @Default(CallStatusWaiting()) CallStatus status,

    // 요금·배차
    int? fare,
    String? assignedDriverId,
    String? assignedDriverName,
    String? assignedDriverPhone,
    String? assignedPickupDriverId,
    String? deviceName,
    String? callType,
    @Default(false) bool isAppCustomer,
    @Default('') String memo,

    // 운행 세부 (기사 입력, snake_case 필드명 Kotlin 원본 보존)
    @JsonKey(name: 'departure_set') String? departureSet,
    @JsonKey(name: 'destination_set') String? destinationSet,
    @JsonKey(name: 'waypoints_set') String? waypointsSet,
    @JsonKey(name: 'fare_set') int? fareSet,
    @JsonKey(name: 'trip_summary') String? tripSummary,

    // 결제·정산
    @Default('') String paymentMethod,
    int? cashAmount,
    int? cashReceived,
    int? creditAmount,
    @Default(false) bool isSummaryConfirmed,
    @Default('PENDING') String settlementStatus,
    String? settlementId,

    // 공유콜 흐름
    String? claimedDriverId,
    String? sourceSharedCallId,

    // 완료 시점 최종 정보 (CF에서 저장)
    String? tripSummaryFinal,
    int? finalFare,
    int? fareFinal,
  }) = _CallInfo;

  factory CallInfo.fromJson(Map<String, Object?> json) =>
      _$CallInfoFromJson(json);

  /// Firestore DocumentSnapshot → CallInfo
  factory CallInfo.fromFirestore(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};
    return CallInfo.fromJson({...data, 'id': doc.id});
  }

  /// UI 표시용 파생 속성 (statusEnum 대체)
  bool get isActive => status.isActive;
}
```

### 필드별 매핑 노트

| Kotlin 필드 | Dart 필드 | 변환 노트 |
|------------|----------|----------|
| `var id: String = ""` | `@Default('') String id` | Kotlin `var`는 Freezed에서 immutable로 보존 (`copyWith` 사용) |
| `val status: String` | `@CallStatusConverter() CallStatus status` | ENUMS.md sealed class로 승격. Firestore read 시 `CallStatus.fromString(raw)`, write 시 `status.toJson()` |
| `val fare: Int? = null` | `int? fare` | nullable 유지. Dart int = Long 등가 |
| `val detectedTimestamp: Timestamp?` | `@TimestampConverter() DateTime?` | Firestore Timestamp ↔ DateTime 변환기 필요 (하단 TimestampConverter 섹션) |
| `departure_set: String?` | `@JsonKey(name: 'departure_set') String? departureSet` | **Firestore 키는 snake_case 유지**, Dart 필드는 camelCase |
| `@get:Exclude val statusEnum` | Dart `isActive` getter | Firestore 저장 안 되는 파생 속성. `const CallInfo._()` 생성자로 private getter 정의 |
| `settlementStatus: String = "PENDING"` | `@Default('PENDING') String settlementStatus` | 별도 enum 불필요 (단일 상태라 단순 String) |

### TimestampConverter (별도 파일)

```dart
// lib/domain/models/converters/timestamp_converter.dart

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:json_annotation/json_annotation.dart';

class TimestampConverter implements JsonConverter<DateTime?, Object?> {
  const TimestampConverter();

  @override
  DateTime? fromJson(Object? json) {
    if (json == null) return null;
    if (json is Timestamp) return json.toDate();
    if (json is DateTime) return json;
    if (json is int) return DateTime.fromMillisecondsSinceEpoch(json);
    return null;
  }

  @override
  Object? toJson(DateTime? object) =>
      object == null ? null : Timestamp.fromDate(object);
}

/// 서버 시간으로 자동 설정 (쓰기 시 `FieldValue.serverTimestamp()` 사용)
class ServerTimestampConverter implements JsonConverter<DateTime?, Object?> {
  const ServerTimestampConverter();

  @override
  DateTime? fromJson(Object? json) =>
      const TimestampConverter().fromJson(json);

  @override
  Object? toJson(DateTime? object) => FieldValue.serverTimestamp();
}
```

### CallStatusConverter

```dart
// lib/domain/models/converters/call_status_converter.dart

import 'package:json_annotation/json_annotation.dart';
import '../../enums/call_status.dart';

class CallStatusConverter implements JsonConverter<CallStatus, String> {
  const CallStatusConverter();

  @override
  CallStatus fromJson(String json) => CallStatus.fromString(json);

  @override
  String toJson(CallStatus object) => object.toJson();
}
```

### 주의사항

1. **`id` 필드 특수 처리**: Firestore 문서 ID는 `data()`에 포함되지 않음 → `fromFirestore` 팩토리에서 `{...data, 'id': doc.id}` 머지 필수
2. **snake_case 5필드 (`departure_set`, `destination_set`, `waypoints_set`, `fare_set`, `trip_summary`)**: Kotlin 원본이 snake_case로 Firestore 저장 → **반드시 `@JsonKey(name: ...)` 유지**. 무단 camelCase 변환 금지 (Firestore 호환 깨짐)
3. **`memo` 필드 기본값 빈 문자열**: Firestore null 가능성 → `as String? ?? ''` fromJson 안전 처리 자동 생성 확인 필요
4. **`isActive` getter**: `statusEnum` Kotlin getter를 Dart에서는 sealed class의 `isActive` 파생 속성으로 직접 노출 (ENUMS.md §1 정의)

---

## 2. Driver (기사 엔티티)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/model/DriverModel.kt:15-27`)

```kotlin
data class Driver(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    var status: DriverStatus = DriverStatus.OFFLINE,
    val currentCallId: String? = null,
    val rating: Float = 0f,
    val totalTrips: Int = 0,
    val registrationDate: Timestamp? = null,
    val isActive: Boolean = true,
    var approvalStatus: DriverApprovalStatus = DriverApprovalStatus.PENDING
)
```

### Firestore 경로
`provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/{uid}`

**추가 필드** (Kotlin Driver 모델엔 없지만 Firestore에는 저장됨 — CF·정산·인증 코드에서 직접 접근):
- `authUid: String` — Firebase Auth uid (로그인용, `LoginViewModel.kt:70-266`)
- `fcmToken: String?` — FCM 토큰 (`MyFirebaseMessagingService.kt:55`)
- `fcmTokenPlatform: String?` — **의제 5 신규**: "android" | "ios"
- `platform: String?` — **의제 11 신규**: 플랫폼 라벨링 (이벤트 집계용)
- `carryOver: Map` — `DriverCarryOver` 구조체 (하단 §12)
- `dailySettlement: Map` — `DriverDailySettlement` 구조체 (하단 §11)
- `associatedOfficeId: String?` — 관리자 문서와 상호 참조
- `vehicleNumber: String?` — 차량번호 (정산 표시용)

### Flutter Freezed 매핑

```dart
// lib/domain/models/driver.dart

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import '../enums/driver_status.dart';
import '../enums/approval_status.dart';
import 'driver_carry_over.dart';
import 'driver_daily_settlement.dart';
import 'converters/timestamp_converter.dart';

part 'driver.freezed.dart';
part 'driver.g.dart';

@freezed
class Driver with _$Driver {
  const Driver._();

  const factory Driver({
    @Default('') String id,
    @Default('') String name,
    @Default('') String phone,
    @Default('') String email,

    /// DriverStatus sealed class (ENUMS.md §2)
    @DriverStatusConverter() @Default(DriverStatusOffline()) DriverStatus status,

    String? currentCallId,
    @Default(0.0) double rating,  // Kotlin Float → Dart double
    @Default(0) int totalTrips,
    @TimestampConverter() DateTime? registrationDate,
    @Default(true) bool isActive,
    @Default(ApprovalStatus.pending) ApprovalStatus approvalStatus,

    // Firestore 추가 필드 (Kotlin 모델 비선언)
    String? authUid,
    String? fcmToken,
    String? fcmTokenPlatform,   // 의제 5 신규
    String? platform,           // 의제 11 신규
    String? associatedOfficeId,
    String? vehicleNumber,

    // 정산 서브필드 (nullable — 신규 기사는 null)
    DriverCarryOver? carryOver,
    DriverDailySettlement? dailySettlement,
  }) = _Driver;

  factory Driver.fromJson(Map<String, Object?> json) =>
      _$DriverFromJson(json);

  factory Driver.fromFirestore(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};
    return Driver.fromJson({...data, 'id': doc.id});
  }

  /// Presence/status 파생
  bool get isAvailableForCall => status.canReceiveCall;
}
```

### DriverStatusConverter

```dart
// lib/domain/models/converters/driver_status_converter.dart

class DriverStatusConverter implements JsonConverter<DriverStatus, String> {
  const DriverStatusConverter();

  @override
  DriverStatus fromJson(String json) => DriverStatus.fromString(json);

  @override
  String toJson(DriverStatus object) => object.toJson();
}
```

### 필드별 매핑 노트

| Kotlin 필드 | Dart 필드 | 변환 노트 |
|------------|----------|----------|
| `val rating: Float = 0f` | `@Default(0.0) double rating` | Dart는 `float` 타입 없음 → `double`로 통일 |
| `var status: DriverStatus` | `@DriverStatusConverter() DriverStatus status` | ENUMS.md §2 9종 상태 전수 (**PENDING_CONFIRM/UNKNOWN 포함**) |
| `var approvalStatus: DriverApprovalStatus` | `@Default(ApprovalStatus.pending) ApprovalStatus approvalStatus` | 일반 enum (ENUMS.md §4) |
| (누락) `authUid` | `String? authUid` | Kotlin data class에는 없으나 Firestore 필수 필드 — 반드시 추가 |
| (누락) `carryOver: Map` | `DriverCarryOver? carryOver` | Firestore nested Map → 중첩 Freezed 객체 |

### ⚠️ DriverStatus 불일치 발견

**ENUMS.md §2는 7종 + Unknown**이지만 **Kotlin 원본은 9종** (`model/DriverStatus.kt:3-12`):
- `ONLINE`, `OFFLINE`, `WAITING`, `ASSIGNED`, `ACCEPTED`, `PREPARING`, `ON_TRIP`, `PENDING_CONFIRM`, `UNKNOWN`

**누락된 2종**:
- **`PENDING_CONFIRM`** (line 11): "마감 제출 후 매니저 확인 대기" — 업무마감 플로우에서 필수
- **`ACCEPTED`** (line 8): 기사가 콜 수락한 직후 중간 상태 (ENUMS.md는 PREPARING만 있음)

**조치 권고**: ENUMS.md §2 `DriverStatus`에 **`DriverStatusAccepted` + `DriverStatusPendingConfirm` 2종 추가** 필요. 중재자 검토 후 ENUMS.md 패치.

---

## 3. PickupDriver (픽업기사 엔티티 — D 분류)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/model/DriverModel.kt:29-39`)

```kotlin
data class PickupDriver(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    var status: DriverStatus = DriverStatus.OFFLINE,
    val currentCallId: String? = null,
    val vehicleInfo: String = "",
    val isActive: Boolean = true,
    var approvalStatus: DriverApprovalStatus = DriverApprovalStatus.PENDING
)
```

### Firestore 경로
`provinces/{p}/cities/{c}/offices/{o}/pickup_drivers/{uid}`

### Flutter Freezed 매핑 — **Phase 1 MVP 제외** (`memory/plan_pickup_driver_app.md` 별도 플랜 참조)

Phase 1에서는 기사앱(designated)만 대상. Pickup 기능은 별도 플랜 + Phase 2 이후. 모델 정의만 참고로 포함하되 **실제 구현 유보**.

```dart
// lib/domain/models/pickup_driver.dart (Phase 2 이후)
@freezed
class PickupDriver with _$PickupDriver {
  const factory PickupDriver({
    @Default('') String id,
    @Default('') String name,
    @Default('') String phone,
    @Default('') String email,
    @DriverStatusConverter() @Default(DriverStatusOffline()) DriverStatus status,
    String? currentCallId,
    @Default('') String vehicleInfo,
    @Default(true) bool isActive,
    @Default(ApprovalStatus.pending) ApprovalStatus approvalStatus,
  }) = _PickupDriver;

  factory PickupDriver.fromJson(Map<String, Object?> json) =>
      _$PickupDriverFromJson(json);
}
```

---

## 4. UserSession (로컬 세션 캐시)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/data/model/UserSession.kt:6-67`)

```kotlin
data class UserSession(
    val userId: String,
    val email: String,
    val provinceId: String,
    val cityId: String,
    val officeId: String,
    val driverId: String,
    val driverName: String,
    val fcmToken: String?,
    val lastLoginTime: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true
) {
    fun isExpired(): Boolean {
        val sessionValidityMs = 7 * 24 * 60 * 60 * 1000L // 7일
        return System.currentTimeMillis() - lastLoginTime > sessionValidityMs
    }
    // toMap / fromMap ...
}
```

### 저장소
- **Firestore 아님**. SharedPreferences JSON 블랍 1건 (`LoginViewModel` 경로)
- Flutter: `shared_preferences` 또는 `flutter_secure_storage` (비밀번호 포함 시 후자)

### Flutter Freezed 매핑

```dart
// lib/domain/models/user_session.dart

import 'package:freezed_annotation/freezed_annotation.dart';

part 'user_session.freezed.dart';
part 'user_session.g.dart';

@freezed
class UserSession with _$UserSession {
  const UserSession._();

  const factory UserSession({
    required String userId,           // Kotlin 필수 필드 보존
    required String email,
    required String provinceId,
    required String cityId,
    required String officeId,
    required String driverId,
    @Default('') String driverName,
    String? fcmToken,
    @Default(0) int lastLoginTime,    // epoch millis
    @Default(true) bool isOnline,
  }) = _UserSession;

  factory UserSession.fromJson(Map<String, Object?> json) =>
      _$UserSessionFromJson(json);

  /// 7일 세션 만료 검사
  bool get isExpired {
    const sessionValidityMs = 7 * 24 * 60 * 60 * 1000;
    return DateTime.now().millisecondsSinceEpoch - lastLoginTime >
        sessionValidityMs;
  }

  /// 현재 시간으로 생성 (Kotlin `lastLoginTime = System.currentTimeMillis()` 등가)
  factory UserSession.createNow({
    required String userId,
    required String email,
    required String provinceId,
    required String cityId,
    required String officeId,
    required String driverId,
    String driverName = '',
    String? fcmToken,
    bool isOnline = true,
  }) {
    return UserSession(
      userId: userId,
      email: email,
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
      driverId: driverId,
      driverName: driverName,
      fcmToken: fcmToken,
      lastLoginTime: DateTime.now().millisecondsSinceEpoch,
      isOnline: isOnline,
    );
  }
}
```

### 필드별 매핑 노트

| Kotlin 필드 | Dart 필드 | 변환 노트 |
|------------|----------|----------|
| 생성자 인자 7개 `required` (기본값 없음) | Dart `required` 파라미터 | Freezed `required` 키워드 (의제 14-G) |
| `val lastLoginTime: Long = System.currentTimeMillis()` | `@Default(0) int lastLoginTime` + `createNow` 팩토리 | Freezed 생성자는 시간 동적 기본값 불가 → 팩토리 분리 (late 금지, 의제 14-G) |
| `fun isExpired(): Boolean` | `bool get isExpired` | Dart getter로 이관. `const UserSession._()` 필수 |

### 저장/로딩 패턴

```dart
// lib/services/session_storage.dart

import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../domain/models/user_session.dart';

class SessionStorage {
  static const _key = 'user_session_json';

  Future<void> save(UserSession session) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, jsonEncode(session.toJson()));
  }

  Future<UserSession?> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw == null) return null;
    try {
      final json = jsonDecode(raw) as Map<String, dynamic>;
      return UserSession.fromJson(json);
    } catch (_) {
      return null;
    }
  }

  Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_key);
  }
}
```

---

## 5. CustomerPoints (손님 포인트 — 기사앱은 읽기 전용)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/data/model/CustomerPoints.kt:6-53`)

```kotlin
data class CustomerPoints(
    val customerId: String = "",
    val phoneNumber: String = "",
    val currentPoints: Int = 0,
    val totalEarned: Int = 0,
    val totalUsed: Int = 0,
    val grade: String = "BRONZE",
    val totalCalls: Int = 0,
    val lastUpdated: Any? = null,
    val createdAt: Any? = null
)
```

### Firestore 경로
`provinces/{p}/cities/{c}/offices/{o}/customerPoints/{phoneNumber}` (기사앱은 **정산 시 참조만**)

### Flutter Freezed 매핑

```dart
// lib/domain/models/customer_points.dart

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'converters/timestamp_converter.dart';

part 'customer_points.freezed.dart';
part 'customer_points.g.dart';

@freezed
class CustomerPoints with _$CustomerPoints {
  const factory CustomerPoints({
    @Default('') String customerId,
    @Default('') String phoneNumber,
    @Default(0) int currentPoints,
    @Default(0) int totalEarned,
    @Default(0) int totalUsed,
    @Default('BRONZE') String grade,   // 손님앱에서 enum, 기사앱에서 String 표시만
    @Default(0) int totalCalls,
    @TimestampConverter() DateTime? lastUpdated,
    @TimestampConverter() DateTime? createdAt,
  }) = _CustomerPoints;

  factory CustomerPoints.fromJson(Map<String, Object?> json) =>
      _$CustomerPointsFromJson(json);

  factory CustomerPoints.fromFirestore(
      DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};
    return CustomerPoints.fromJson(data);
  }
}
```

### 필드별 매핑 노트

| Kotlin 필드 | Dart 필드 | 변환 노트 |
|------------|----------|----------|
| `val lastUpdated: Any? = null` | `@TimestampConverter() DateTime?` | Kotlin `Any?`는 Timestamp 허용 타입. Dart는 타입 강제 → TimestampConverter로 파싱 |
| `grade: String = "BRONZE"` | `@Default('BRONZE') String grade` | 기사앱은 표시만. 손님앱 `CustomerGrade` enum과 별도 (loose typing) |

**주의**: 기사앱에서 `CustomerPoints`는 **조회만** (정산 화면에서 포인트 사용 표시). 업데이트는 **CF가 처리** (CLAUDE.md 3/24 BUG-D12). Flutter 측도 `update()` 호출 없음 원칙.

---

## 6. SettlementSession (정산 세션 상위)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/data/settlement/SettlementModels.kt:12-49`)

```kotlin
data class SettlementSession(
    val metadata: SettlementMetadata = SettlementMetadata(),
    val totals: SettlementTotals = SettlementTotals(),
    val calls: List<CallSettlement> = emptyList()
) {
    companion object {
        fun fromDocument(doc: DocumentSnapshot): SettlementSession? { ... }
    }
    fun toMap(): Map<String, Any?> = mapOf(...)
}
```

### Firestore 경로
`provinces/{p}/cities/{c}/offices/{o}/settlementSessions/{YYYY-MM-DD}` — **한 사무실 당 일 1건**

### Flutter Freezed 매핑

```dart
// lib/domain/models/settlement_session.dart

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'settlement_metadata.dart';
import 'settlement_totals.dart';
import 'call_settlement.dart';

part 'settlement_session.freezed.dart';
part 'settlement_session.g.dart';

@freezed
class SettlementSession with _$SettlementSession {
  const SettlementSession._();

  const factory SettlementSession({
    @Default(SettlementMetadata()) SettlementMetadata metadata,
    @Default(SettlementTotals()) SettlementTotals totals,
    @Default(<CallSettlement>[]) List<CallSettlement> calls,
  }) = _SettlementSession;

  factory SettlementSession.fromJson(Map<String, Object?> json) =>
      _$SettlementSessionFromJson(json);

  factory SettlementSession.fromFirestore(
      DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data();
    if (data == null) return const SettlementSession();
    return SettlementSession.fromJson(data);
  }
}
```

### 필드별 매핑 노트

| Kotlin | Dart | 비고 |
|--------|------|-----|
| `val metadata: SettlementMetadata = SettlementMetadata()` | `@Default(SettlementMetadata()) SettlementMetadata metadata` | **Freezed가 중첩 const 객체 기본값 지원** (생성자 모두 `const`) |
| `val calls: List<CallSettlement>` | `@Default(<CallSettlement>[]) List<CallSettlement> calls` | const empty list 초기화 |

---

## 7. SettlementMetadata (정산 메타데이터)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/data/settlement/SettlementModels.kt:54-83`)

```kotlin
data class SettlementMetadata(
    val version: Long = 0,
    val lastUpdatedAt: Timestamp? = null,
    val lastUpdatedBy: String = "",       // "driver_app" | "call_manager"
    val depositRatio: Int = 60,           // 납입비율 (기본 60%)
    val createdAt: Timestamp? = null,
    val isFinalized: Boolean = false      // 일일 마감 여부
)
```

### Flutter Freezed 매핑

```dart
// lib/domain/models/settlement_metadata.dart

import 'package:freezed_annotation/freezed_annotation.dart';
import 'converters/timestamp_converter.dart';

part 'settlement_metadata.freezed.dart';
part 'settlement_metadata.g.dart';

@freezed
class SettlementMetadata with _$SettlementMetadata {
  const factory SettlementMetadata({
    @Default(0) int version,
    @TimestampConverter() DateTime? lastUpdatedAt,
    @Default('') String lastUpdatedBy,
    @Default(60) int depositRatio,
    @TimestampConverter() DateTime? createdAt,
    @Default(false) bool isFinalized,
  }) = _SettlementMetadata;

  factory SettlementMetadata.fromJson(Map<String, Object?> json) =>
      _$SettlementMetadataFromJson(json);
}
```

### 필드별 매핑 노트

| Kotlin 필드 | Dart 필드 | 변환 노트 |
|------------|----------|----------|
| `val version: Long = 0` | `@Default(0) int version` | Dart int가 Long 등가. optimistic locking용 |
| `val depositRatio: Int = 60` | `@Default(60) int depositRatio` | 사무실별 기본 60%. `ios/SHARED_LOGIC.md` §4 정산 공식 입력값 |
| `val lastUpdatedBy: String` | `@Default('') String lastUpdatedBy` | 값은 `"driver_app"` | `"call_manager"` (Kotlin 관용어 보존) |

---

## 8. SettlementTotals (정산 집계 데이터)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/data/settlement/SettlementModels.kt:88-154`)

```kotlin
data class SettlementTotals(
    val totalFare: Long = 0,           // 총 운행료
    val totalDeposit: Long = 0,        // 총 납입액 (사무실 몫)
    val totalDriverShare: Long = 0,    // 총 기사 몫
    val totalCash: Long = 0,
    val totalCard: Long = 0,
    val totalCredit: Long = 0,
    val totalPoints: Long = 0,
    val callCount: Int = 0
) {
    fun addCall(call: CallSettlement, depositRatio: Int): SettlementTotals { ... }
}
```

### Flutter Freezed 매핑

```dart
// lib/domain/models/settlement_totals.dart

import 'package:freezed_annotation/freezed_annotation.dart';
import 'call_settlement.dart';

part 'settlement_totals.freezed.dart';
part 'settlement_totals.g.dart';

@freezed
class SettlementTotals with _$SettlementTotals {
  const SettlementTotals._();

  const factory SettlementTotals({
    @Default(0) int totalFare,          // Kotlin Long → Dart int
    @Default(0) int totalDeposit,
    @Default(0) int totalDriverShare,
    @Default(0) int totalCash,
    @Default(0) int totalCard,
    @Default(0) int totalCredit,
    @Default(0) int totalPoints,
    @Default(0) int callCount,
  }) = _SettlementTotals;

  factory SettlementTotals.fromJson(Map<String, Object?> json) =>
      _$SettlementTotalsFromJson(json);

  /// 새 콜 추가 시 집계 재계산 (Kotlin addCall 이관)
  SettlementTotals addCall(CallSettlement call, int depositRatio) {
    final newTotalFare = totalFare + call.fare;
    final newTotalDeposit = (newTotalFare * depositRatio / 100).toInt();
    final newTotalDriverShare = newTotalFare - newTotalDeposit;

    final cashAmount = () {
      if (call.paymentMethod == '현금') return call.fare;
      if (call.paymentMethod.startsWith('현금+')) return call.cashReceived;
      return 0;
    }();

    final cardAmount = switch (call.paymentMethod) {
      '이체' || '카드' => call.fare,
      _ => 0,
    };

    return SettlementTotals(
      totalFare: newTotalFare,
      totalDeposit: newTotalDeposit,
      totalDriverShare: newTotalDriverShare,
      totalCash: totalCash + cashAmount,
      totalCard: totalCard + cardAmount,
      totalCredit: totalCredit + call.creditAmount,
      totalPoints: totalPoints + call.pointsUsed,
      callCount: callCount + 1,
    );
  }
}
```

### 필드별 매핑 노트

| Kotlin Long 필드 | Dart int | 변환 노트 |
|-----------------|---------|----------|
| 모든 `total*` 필드 | `@Default(0) int` | Kotlin은 메모리 명시 `Long`, Dart는 자동 64bit — **Firestore write 시 `int`가 정확히 Long으로 직렬화됨 (cloud_firestore SDK 기본)** |
| `addCall(call, depositRatio)` 메서드 | `SettlementTotals addCall(...)` | switch expression은 Dart 3+ 문법 (의제 14-A) |

### 계산 순서 주의

`ios/SHARED_LOGIC.md` §4 정산 공식과 일치:
1. `newTotalFare = totalFare + call.fare`
2. `newTotalDeposit = newTotalFare × depositRatio / 100` (**소수점 절삭**)
3. `newTotalDriverShare = newTotalFare - newTotalDeposit`

Kotlin `(newTotalFare * depositRatio / 100)` Long 연산은 자동 절삭. Dart는 `.toInt()` 명시 필요.

---

## 9. CallSettlement (개별 콜 정산 정보)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/data/settlement/SettlementModels.kt:159-219`)

```kotlin
data class CallSettlement(
    val callId: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val departure: String = "",
    val destination: String = "",
    val fare: Long = 0,
    val paymentMethod: String = "",    // "현금" | "이체" | "외상" | "현금+포인트" | "포인트"
    val cashReceived: Long = 0,
    val creditAmount: Long = 0,
    val pointsUsed: Long = 0,
    val completedAt: Timestamp? = null,
    val confirmedByOffice: Boolean = false,
    val syncedAt: Timestamp? = null
)
```

### Flutter Freezed 매핑

```dart
// lib/domain/models/call_settlement.dart

import 'package:freezed_annotation/freezed_annotation.dart';
import 'converters/timestamp_converter.dart';

part 'call_settlement.freezed.dart';
part 'call_settlement.g.dart';

@freezed
class CallSettlement with _$CallSettlement {
  const factory CallSettlement({
    @Default('') String callId,
    @Default('') String driverId,
    @Default('') String driverName,
    @Default('') String customerName,
    @Default('') String customerPhone,
    @Default('') String departure,
    @Default('') String destination,
    @Default(0) int fare,
    @Default('') String paymentMethod,
    @Default(0) int cashReceived,
    @Default(0) int creditAmount,
    @Default(0) int pointsUsed,
    @TimestampConverter() DateTime? completedAt,
    @Default(false) bool confirmedByOffice,
    @TimestampConverter() DateTime? syncedAt,
  }) = _CallSettlement;

  factory CallSettlement.fromJson(Map<String, Object?> json) =>
      _$CallSettlementFromJson(json);
}
```

### 필드별 매핑 노트

| Kotlin 필드 | Dart 필드 | 변환 노트 |
|------------|----------|----------|
| `fare: Long` | `@Default(0) int fare` | 가격 단위는 원화(KRW) 정수. Long/int 동일 |
| `paymentMethod: String` | `@Default('') String paymentMethod` | **ENUMS.md §5 `PaymentMethod` enum 사용 고려**. 단 Firestore 저장은 한글 그대로 (`"현금"`, `"현금+포인트"`) |

### ⚠️ PaymentMethod enum 사용 여부 결정 필요

ENUMS.md §5에 `PaymentMethod` enum이 정의되어 있으나, **현재 Kotlin 원본은 `String` 그대로 저장·비교** (예: `call.paymentMethod.startsWith("현금+")`). Flutter에서 enum 사용 시:
- **옵션 A**: String 유지 (Kotlin 관용 보존). Firestore 호환 100%
- **옵션 B**: PaymentMethod converter 추가 (타입 안전). `startsWith("현금+")` 같은 prefix 검사가 enum에서는 `PaymentMethod.cashAndPoints` 단일 비교로 대체 가능

**권장**: **옵션 A** (Phase 1 MVP). 옵션 B는 Kotlin 원본 coding convention 재검토 필요 — 현재 수용 범위 밖.

---

## 10. DriverSettlementSummary (UI 표시용 정산 요약)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/data/settlement/SettlementModels.kt:249-274`)

```kotlin
data class DriverSettlementSummary(
    val totalFare: Long,
    val depositAmount: Long,       // 납부액
    val myIncome: Long,            // 내 수익
    val unpaidAmount: Long,        // 미납금 (외상)
    val actualDeposit: Long,       // 실 납부액
    val callCount: Int,
    val depositRatio: Int
) {
    companion object {
        fun fromSession(session: SettlementSession): DriverSettlementSummary {
            val totals = session.totals
            val depositRatio = session.metadata.depositRatio
            return DriverSettlementSummary(
                totalFare = totals.totalFare,
                depositAmount = totals.totalDeposit,
                myIncome = totals.totalDriverShare,
                unpaidAmount = totals.totalCredit,
                actualDeposit = totals.totalDeposit - totals.totalCredit,
                callCount = totals.callCount,
                depositRatio = depositRatio
            )
        }
    }
}
```

### 저장소
**Firestore 저장 없음**. `SettlementSession`에서 순수 함수로 파생되는 UI 표시용 구조체.

### Flutter Freezed 매핑

```dart
// lib/domain/models/driver_settlement_summary.dart

import 'package:freezed_annotation/freezed_annotation.dart';
import 'settlement_session.dart';

part 'driver_settlement_summary.freezed.dart';
part 'driver_settlement_summary.g.dart';

@freezed
class DriverSettlementSummary with _$DriverSettlementSummary {
  const DriverSettlementSummary._();

  const factory DriverSettlementSummary({
    required int totalFare,
    required int depositAmount,     // 납부액 (사무실 몫)
    required int myIncome,          // 내 수익
    required int unpaidAmount,      // 미납금 (외상)
    required int actualDeposit,     // 실 납부액
    required int callCount,
    required int depositRatio,
  }) = _DriverSettlementSummary;

  factory DriverSettlementSummary.fromJson(Map<String, Object?> json) =>
      _$DriverSettlementSummaryFromJson(json);

  /// SettlementSession에서 파생 (Kotlin fromSession 등가)
  factory DriverSettlementSummary.fromSession(SettlementSession session) {
    final totals = session.totals;
    final depositRatio = session.metadata.depositRatio;
    return DriverSettlementSummary(
      totalFare: totals.totalFare,
      depositAmount: totals.totalDeposit,
      myIncome: totals.totalDriverShare,
      unpaidAmount: totals.totalCredit,
      actualDeposit: totals.totalDeposit - totals.totalCredit,
      callCount: totals.callCount,
      depositRatio: depositRatio,
    );
  }
}
```

### 필드별 매핑 노트

| Kotlin | Dart | 비고 |
|--------|------|-----|
| 모든 필드 기본값 없음 (생성자 인자 필수) | `required` 키워드 | Freezed `required` (의제 14-G) |
| `companion object fun fromSession` | `factory DriverSettlementSummary.fromSession` | Dart 팩토리 생성자로 이관 |

**주의**: 이 구조체는 **UI 표시 전용**. `toJson`은 Freezed 자동 생성되지만 실제 Firestore 저장 호출 경로 없음.

---

## 11. DriverDailySettlement (일일 정산)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/data/settlement/SettlementModels.kt:299-363`)

```kotlin
data class DriverDailySettlement(
    val date: String = "",                 // YYYY-MM-DD
    val finalDeposit: Long = 0,            // 최종 납입액
    val realDeposit: Long = 0,             // 실납입
    val settlementDiff: Long = 0,          // 정산 차액
    val totalFare: Long = 0,
    val totalCredit: Long = 0,
    val tripCount: Int = 0,
    val status: DailySettlementStatus = DailySettlementStatus.WORKING,
    val submittedAt: Timestamp? = null,
    val confirmedAt: Timestamp? = null,
    val confirmedBy: String? = null,
    val calculatedCarryOver: Long = 0,
    val originalCarryOver: Long = 0,
    val originalTripCount: Int = 0,
    val originalTotalFare: Long = 0,
    val originalRealDeposit: Long = 0
)
```

### Firestore 경로
`provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid}.dailySettlement` (**Driver 문서의 nested 필드**)

### Flutter Freezed 매핑

```dart
// lib/domain/models/driver_daily_settlement.dart

import 'package:freezed_annotation/freezed_annotation.dart';
import '../enums/daily_settlement_status.dart';
import 'converters/timestamp_converter.dart';

part 'driver_daily_settlement.freezed.dart';
part 'driver_daily_settlement.g.dart';

@freezed
class DriverDailySettlement with _$DriverDailySettlement {
  const factory DriverDailySettlement({
    @Default('') String date,
    @Default(0) int finalDeposit,
    @Default(0) int realDeposit,
    @Default(0) int settlementDiff,
    @Default(0) int totalFare,
    @Default(0) int totalCredit,
    @Default(0) int tripCount,

    /// ENUMS.md §3 DailySettlementStatus sealed class 사용
    /// ⚠️ Kotlin enum은 WORKING/PENDING_CONFIRM/CONFIRMED/REJECTED 4종 — ENUMS.md와 다름
    @DailySettlementStatusConverter()
    @Default(DailySettlementStatusUnknown('WORKING'))
    DailySettlementStatus status,

    @TimestampConverter() DateTime? submittedAt,
    @TimestampConverter() DateTime? confirmedAt,
    String? confirmedBy,

    @Default(0) int calculatedCarryOver,
    @Default(0) int originalCarryOver,
    @Default(0) int originalTripCount,
    @Default(0) int originalTotalFare,
    @Default(0) int originalRealDeposit,
  }) = _DriverDailySettlement;

  factory DriverDailySettlement.fromJson(Map<String, Object?> json) =>
      _$DriverDailySettlementFromJson(json);
}
```

### ⚠️ DailySettlementStatus 불일치 발견 (ENUMS.md 재검토 필요)

**Kotlin 원본 (`SettlementModels.kt:288-293`)**: 4종
- `WORKING` — 근무 중 (아직 마감 안 함)
- `PENDING_CONFIRM` — 마감 완료, 매니저 확인 대기
- `CONFIRMED` — 매니저 확인 완료
- `REJECTED` — 매니저 거절 (재제출 필요)

**ENUMS.md §3**: 4종 (다른 값 구성)
- `PENDING_CONFIRM`, `CONFIRMED`, `TRANSFERRED`, `SETTLED`

**원인 분석**:
- ENUMS.md §3은 **이월금(CarryOver) 플로우 상태**를 `DailySettlementStatus`로 기술 — 실제 Kotlin `DailySettlementStatus`와 불일치
- Kotlin `CarryOverStatus` (SettlementModels.kt:279-283)는 **`PENDING / TRANSFERRED / SETTLED` 3종** — ENUMS.md §6 `CarryOverStatus`는 **`CONFIRMED / TRANSFERRED / SETTLED`** (PENDING 누락)

**조치 권고** (중재자 승인 필요):
1. **ENUMS.md §3 `DailySettlementStatus`를 Kotlin 원본 4종으로 수정**: `WORKING / PENDING_CONFIRM / CONFIRMED / REJECTED`
2. **ENUMS.md §6 `CarryOverStatus`에 `PENDING` 추가** (Kotlin `SettlementModels.kt:279` 원본)
3. MODELS.md는 **수정 후 ENUMS.md를 준거로 사용** (임시로 `DailySettlementStatusUnknown('WORKING')` fallback 사용)

### 필드별 매핑 노트

| Kotlin 필드 | Dart 필드 | 변환 노트 |
|------------|----------|----------|
| `date: String` YYYY-MM-DD | `@Default('') String date` | Kotlin은 DateFormat String. Dart도 String 유지 (Firestore 일관) |
| `originalTripCount / originalTotalFare / originalRealDeposit` | `@Default(0) int` 3종 | **통합 제출 로직**(isIntegration) 전용 필드. 재제출 시 1차 마감 값 보존 |

---

## 12. DriverCarryOver (이월금)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/data/settlement/SettlementModels.kt:369-403`)

```kotlin
data class DriverCarryOver(
    val balance: Long = 0,                    // 누적 미수령금
    val status: CarryOverStatus = CarryOverStatus.PENDING,
    val lastUpdatedAt: Timestamp? = null,
    val transferredAt: Timestamp? = null,
    val transferredBy: String? = null,
    val todayAmount: Long = 0
)
```

### Firestore 경로
`provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid}.carryOver` (**Driver 문서 nested 필드**)

### Flutter Freezed 매핑

```dart
// lib/domain/models/driver_carry_over.dart

import 'package:freezed_annotation/freezed_annotation.dart';
import '../enums/carry_over_status.dart';
import 'converters/timestamp_converter.dart';

part 'driver_carry_over.freezed.dart';
part 'driver_carry_over.g.dart';

@freezed
class DriverCarryOver with _$DriverCarryOver {
  const factory DriverCarryOver({
    @Default(0) int balance,
    @CarryOverStatusConverter()
    @Default(CarryOverStatus.pending)   // ENUMS.md §6 PENDING 추가 필요
    CarryOverStatus status,
    @TimestampConverter() DateTime? lastUpdatedAt,
    @TimestampConverter() DateTime? transferredAt,
    String? transferredBy,
    @Default(0) int todayAmount,
  }) = _DriverCarryOver;

  factory DriverCarryOver.fromJson(Map<String, Object?> json) =>
      _$DriverCarryOverFromJson(json);
}
```

### CarryOverStatusConverter

```dart
class CarryOverStatusConverter implements JsonConverter<CarryOverStatus, String> {
  const CarryOverStatusConverter();

  @override
  CarryOverStatus fromJson(String json) => CarryOverStatus.fromString(json);

  @override
  String toJson(CarryOverStatus object) => object.toJson();
}
```

### 필드별 매핑 노트

| Kotlin 필드 | Dart 필드 | 변환 노트 |
|------------|----------|----------|
| `balance: Long` | `@Default(0) int balance` | **양수 = 내가 받아야 할 미수령금**, 음수 = 미납금 (부호 의미 `SettlementCalc.kt:34-42` 참조) |
| `status: CarryOverStatus` | `@CarryOverStatusConverter() CarryOverStatus status` | ENUMS.md §6 **PENDING/TRANSFERRED/SETTLED 3종 확정 필요** (위 §11 참조) |
| `todayAmount: Long` | `@Default(0) int todayAmount` | 오늘 발생한 미수령금 (UI 표시용) |

---

## 13. AddressSearchResult (주소 검색 결과)

### Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/data/AddressSearchResult.kt:3-10`)

```kotlin
data class AddressSearchResult(
    val address: String,
    val roadAddress: String? = null,
    val jibunAddress: String? = null,
    val placeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
```

### 저장소
**Firestore 아님**. 외부 주소 검색 API (Kakao) 응답 1건.

### Flutter Freezed 매핑

```dart
// lib/domain/models/address_search_result.dart

import 'package:freezed_annotation/freezed_annotation.dart';

part 'address_search_result.freezed.dart';
part 'address_search_result.g.dart';

@freezed
class AddressSearchResult with _$AddressSearchResult {
  const factory AddressSearchResult({
    required String address,          // 전체 주소 (필수)
    String? roadAddress,              // 도로명
    String? jibunAddress,             // 지번
    String? placeName,                // 장소명
    double? latitude,
    double? longitude,
  }) = _AddressSearchResult;

  factory AddressSearchResult.fromJson(Map<String, Object?> json) =>
      _$AddressSearchResultFromJson(json);

  /// Kakao API 응답 → AddressSearchResult 어댑터
  /// lib/services/address_search_service.dart에서 Kakao JSON 응답 매핑 시 사용
  factory AddressSearchResult.fromKakao(Map<String, dynamic> kakao) {
    final road = kakao['road_address'] as Map<String, dynamic>?;
    final jibun = kakao['address'] as Map<String, dynamic>?;
    return AddressSearchResult(
      address: (road?['address_name'] ?? jibun?['address_name'] ?? '') as String,
      roadAddress: road?['address_name'] as String?,
      jibunAddress: jibun?['address_name'] as String?,
      placeName: kakao['place_name'] as String?,
      latitude: (kakao['y'] as String?)?.let((s) => double.tryParse(s)),
      longitude: (kakao['x'] as String?)?.let((s) => double.tryParse(s)),
    );
  }
}

// Dart에는 Kotlin `?.let`이 없어 확장 함수 필요
extension _NullableLet<T> on T? {
  R? let<R>(R Function(T) block) {
    final v = this;
    return v == null ? null : block(v);
  }
}
```

### 필드별 매핑 노트

| Kotlin | Dart | 비고 |
|--------|------|-----|
| `val address: String` (필수) | `required String address` | 기본값 없음 — required 유지 |
| Kakao `x`/`y` 좌표 String | `double? latitude/longitude` | Kakao API 응답은 String → `double.tryParse` |

---

## 부가 UI 상태 구조체 (NOTIFIERS.md에서 재사용)

### 14-A. DriverScreenUiState

Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/ui/state/DriverScreenUiState.kt:10-23`):

```kotlin
data class DriverScreenUiState(
    val driverStatus: DriverStatus = DriverStatus.OFFLINE,
    val assignedCalls: List<CallInfo> = emptyList(),
    val completedCalls: List<CallInfo> = emptyList(),
    val activeCall: CallInfo? = null,
    val callForSettlement: CallInfo? = null,
    val newCallPopup: CallInfo? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val locationFetchStatus: LocationFetchStatus = LocationFetchStatus.Idle,
    val navigateToHome: Boolean = false,
    val navigateToHistorySettlement: Boolean = false,
    val isServiceBound: Boolean = false
)
```

Flutter Freezed (Riverpod Notifier의 state 타입):

```dart
// lib/domain/state/driver_screen_ui_state.dart

import 'package:freezed_annotation/freezed_annotation.dart';
import '../models/call_info.dart';
import '../enums/driver_status.dart';
import 'location_fetch_status.dart';

part 'driver_screen_ui_state.freezed.dart';

@freezed
class DriverScreenUiState with _$DriverScreenUiState {
  const factory DriverScreenUiState({
    @Default(DriverStatusOffline()) DriverStatus driverStatus,
    @Default(<CallInfo>[]) List<CallInfo> assignedCalls,
    @Default(<CallInfo>[]) List<CallInfo> completedCalls,
    CallInfo? activeCall,
    CallInfo? callForSettlement,
    CallInfo? newCallPopup,
    @Default(false) bool isLoading,
    String? errorMessage,
    @Default(LocationFetchStatus.idle()) LocationFetchStatus locationFetchStatus,
    @Default(false) bool navigateToHome,
    @Default(false) bool navigateToHistorySettlement,
    @Default(false) bool isServiceBound,
  }) = _DriverScreenUiState;
}
```

**Freezed union은 `@freezed` 내부 `@JsonSerializable` 없이** — UI state는 Firestore 저장 안 됨. `toJson` 불필요.

### 14-B. LocationFetchStatus (sealed class)

Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/ui/state/DriverScreenUiState.kt:28-33`):

```kotlin
sealed class LocationFetchStatus {
    object Idle : LocationFetchStatus()
    object Loading : LocationFetchStatus()
    data class Success(val address: String) : LocationFetchStatus()
    data class Error(val message: String) : LocationFetchStatus()
}
```

Flutter Freezed union:

```dart
// lib/domain/state/location_fetch_status.dart

import 'package:freezed_annotation/freezed_annotation.dart';

part 'location_fetch_status.freezed.dart';

@freezed
class LocationFetchStatus with _$LocationFetchStatus {
  const factory LocationFetchStatus.idle() = _Idle;
  const factory LocationFetchStatus.loading() = _Loading;
  const factory LocationFetchStatus.success(String address) = _Success;
  const factory LocationFetchStatus.error(String message) = _Error;
}
```

**사용 패턴** (Dart 3 pattern matching):

```dart
final message = switch (uiState.locationFetchStatus) {
  _Idle() => null,
  _Loading() => '위치 확인 중...',
  _Success(:final address) => address,
  _Error(:final message) => '오류: $message',
};
```

### 14-C. TodaySettlement / TripHistoryItem

Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt:104-113, 118-141`):

```kotlin
data class TodaySettlement(
    val totalFare: Int = 0,
    val driverShare: Int = 0,
    val cashReceived: Int = 0,
    val realDeposit: Int = 0,
    val tripCount: Int = 0,
    val totalCredit: Int = 0,
    val officeDeposit: Int = 0,
    val pointsUsed: Int = 0
)

data class TripHistoryItem(
    val tripNumber: Int,
    val customerName: String,
    val departure: String,
    val destination: String,
    val fare: Int,
    val paymentMethod: String,
    val cashAmount: Int?,
    val timestamp: Long
) {
    fun toDisplayString(): String { ... }
}
```

Flutter Freezed:

```dart
// lib/domain/state/today_settlement.dart

import 'package:freezed_annotation/freezed_annotation.dart';

part 'today_settlement.freezed.dart';

@freezed
class TodaySettlement with _$TodaySettlement {
  const factory TodaySettlement({
    @Default(0) int totalFare,
    @Default(0) int driverShare,
    @Default(0) int cashReceived,
    @Default(0) int realDeposit,
    @Default(0) int tripCount,
    @Default(0) int totalCredit,
    @Default(0) int officeDeposit,
    @Default(0) int pointsUsed,
  }) = _TodaySettlement;
}

// lib/domain/state/trip_history_item.dart

@freezed
class TripHistoryItem with _$TripHistoryItem {
  const TripHistoryItem._();

  const factory TripHistoryItem({
    required int tripNumber,
    required String customerName,
    required String departure,
    required String destination,
    required int fare,
    required String paymentMethod,
    int? cashAmount,
    required int timestamp,   // epoch millis
  }) = _TripHistoryItem;

  /// Kotlin toDisplayString() 이관
  String toDisplayString() {
    final formattedFare = fare.toString().replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+(?!\d))'),
      (m) => '${m[1]},',
    );
    final paymentString = () {
      if (paymentMethod == '현금') return '현금';
      if (paymentMethod == '외상') return '외상';
      if (paymentMethod == '이체') return '이체';
      if (paymentMethod.startsWith('현금+') && cashAmount != null) {
        final formattedCash = cashAmount!.toString().replaceAllMapped(
          RegExp(r'(\d)(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]},',
        );
        return '현금+포인트($formattedCash원 현금)';
      }
      if (paymentMethod == '포인트') return '포인트';
      return paymentMethod;
    }();
    return '$tripNumber. $customerName, $departure→$destination, $formattedFare원, $paymentString';
  }
}
```

### 14-D. LoginState (sealed class)

Kotlin 원본 (`driver_app/app/src/main/java/com/designated/driverapp/ui/login/LoginViewModel.kt:30-35`):

```kotlin
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(
        val provinceId: String,
        val cityId: String,
        val officeId: String,
        val driverId: String,
        val needsTokenUpdate: Boolean
    ) : LoginState()
    data class Error(val message: String) : LoginState()
}
```

Flutter Freezed union:

```dart
// lib/domain/state/login_state.dart

import 'package:freezed_annotation/freezed_annotation.dart';

part 'login_state.freezed.dart';

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

---

## 빌드 설정 (의제 14-G 반영)

### pubspec.yaml 추가 의존성

```yaml
dependencies:
  freezed_annotation: ^2.4.4   # 이미 추가됨
  json_annotation: ^4.9.0      # 신규 추가 필요

dev_dependencies:
  build_runner: ^2.4.12        # 이미 추가됨
  freezed: ^2.5.7              # 이미 추가됨
  json_serializable: ^6.8.0    # 신규 추가 필요
```

### 빌드 명령

```bash
# 코드 생성 1회 실행
flutter pub run build_runner build --delete-conflicting-outputs

# 감시 모드 (개발 중 자동 재생성)
flutter pub run build_runner watch --delete-conflicting-outputs
```

### 생성 파일 .gitignore

```gitignore
# build_runner 자동 생성 파일 (체크인하지 않음)
**/*.freezed.dart
**/*.g.dart
```

---

## 마이그레이션 작업 순서 (Phase 6 Week 1~2)

1. **Week 1 — 기반 구조**:
   - `lib/domain/models/converters/` 디렉토리 + TimestampConverter / CallStatusConverter / DriverStatusConverter / CarryOverStatusConverter / DailySettlementStatusConverter 작성
   - `lib/domain/enums/` — ENUMS.md 수정본(위 ⚠️ 2종 반영) 확정 후 sealed class 7종 작성

2. **Week 1 — 핵심 모델 5종**:
   - `CallInfo` (§1) — **가장 복잡**, 실기기 검증 우선
   - `Driver` + 서브필드 `DriverCarryOver` / `DriverDailySettlement` (§2, §12, §11)
   - `UserSession` (§4)

3. **Week 2 — 정산 모델 5종**:
   - `SettlementSession` / `Metadata` / `Totals` / `CallSettlement` / `DriverSettlementSummary` (§6~§10)

4. **Week 2 — UI 상태 구조체 4종**:
   - `DriverScreenUiState` / `LocationFetchStatus` / `TodaySettlement` / `TripHistoryItem` / `LoginState`

5. **Week 2 — 부가 모델 3종**:
   - `CustomerPoints` / `AddressSearchResult` / `PickupDriver` (유보)

**검증**:
- 각 Freezed 모델 작성 후 `flutter pub run build_runner build` 성공 확인
- Firestore 실제 문서 샘플 1건씩 `fromJson` 파싱 테스트 (`driver_app_flutter/test/models/`)
- `toJson()` 결과가 Kotlin Firestore 저장 포맷과 100% 일치하는지 `diff` 검증

---

## ⚠️ 중재자 판단 요청 사항

본 문서 작성 중 발견된 **ENUMS.md 수정 필요 2건**:

### 1. ENUMS.md §2 `DriverStatus` — Kotlin 원본 9종으로 확장

**현재 ENUMS.md §2**: 7종 (`OFFLINE / ONLINE / WAITING / ASSIGNED / PREPARING / ON_TRIP / Unknown`)

**Kotlin 원본 (`model/DriverStatus.kt:3-12`)**: 9종
- 누락 1: **`ACCEPTED`** (기사 수락 직후 중간 상태)
- 누락 2: **`PENDING_CONFIRM`** (업무마감 후 매니저 확인 대기)

**권장 패치**:
```dart
class DriverStatusAccepted extends DriverStatus {
  const DriverStatusAccepted();
  @override String toJson() => 'ACCEPTED';
  @override String get displayName => '수락함';
  @override bool get canReceiveCall => false;
}

class DriverStatusPendingConfirm extends DriverStatus {
  const DriverStatusPendingConfirm();
  @override String toJson() => 'PENDING_CONFIRM';
  @override String get displayName => '정산대기';
  @override bool get canReceiveCall => false;
}
```

### 2. ENUMS.md §3 `DailySettlementStatus` — Kotlin 원본으로 수정

**현재 ENUMS.md §3**: 4종 (`PENDING_CONFIRM / CONFIRMED / TRANSFERRED / SETTLED`) — **이월금 플로우와 혼동**

**Kotlin 원본 (`SettlementModels.kt:288-293`)**: 4종
- `WORKING` (근무 중, 마감 전)
- `PENDING_CONFIRM` (마감 완료, 매니저 확인 대기)
- `CONFIRMED` (매니저 확인 완료)
- `REJECTED` (매니저 거절, 재제출 필요)

**권장 패치**: ENUMS.md §3을 Kotlin 원본 4종으로 교체. `TRANSFERRED / SETTLED` 는 **§6 `CarryOverStatus`** 소속.

### 3. ENUMS.md §6 `CarryOverStatus` — `PENDING` 추가

**현재 ENUMS.md §6**: 3종 (`CONFIRMED / TRANSFERRED / SETTLED`)

**Kotlin 원본 (`SettlementModels.kt:279-283`)**: 3종 — **`PENDING / TRANSFERRED / SETTLED`** (CONFIRMED 없음)

**권장 패치**: ENUMS.md §6 첫 항목을 `CONFIRMED` → `PENDING`으로 교체.

---

## 참조

- `flutter/driver_app/MVP/ENUMS.md` — 본 문서가 사용하는 sealed class (⚠️ 위 3건 수정 필요)
- `flutter/driver_app/MVP/OVERVIEW.md` — Phase 6 실행 사양
- `flutter/driver_app/MVP/NOTIFIERS.md` — 본 모델을 소비하는 Riverpod Notifier 스펙 (차기 작성)
- `flutter/driver_app/MVP/FIRESTORE.md` — 트랜잭션 6곳 + `fromFirestore`/`toFirestore` 호출 패턴 (차기 작성)
- `flutter/WORKING_DOC.md` §5 — 의제 결정 전문
- `ios/SHARED_LOGIC.md` §2~§5 — 상태 전이·정산 공식 원본
- `driver_app/app/src/main/java/com/designated/driverapp/` — Kotlin 원본 (모든 모델 경로)
- `driver_app_flutter/lib/core/constants/app_constants.dart` — 현재 String 상수 (점진 마이그레이션 대상)

---

## 작성 완료 요약

- **모델 13종 Freezed 매핑 완료** (§1~§13)
- **부가 UI 상태 4종** 포함 (§14-A~§14-D)
- **Converter 5종** 정의 (Timestamp / CallStatus / DriverStatus / CarryOverStatus / DailySettlementStatus)
- **⚠️ ENUMS.md 3건 수정 요청** 기록 (중재자 검토 필수)
- **Kotlin 원본 라인 번호 100% 인용**
- **Phase 6 Week 1~2 작업 순서 제시**
