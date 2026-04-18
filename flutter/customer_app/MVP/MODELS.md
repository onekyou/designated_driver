# 손님앱 Data 모델 매핑 (Kotlin → Dart Freezed)

> **의제 14-A (Dart 3 sealed class) + 14-G (Freezed `required` + nullable + `@Default()` + late 금지) 반영**
> **기사앱 MODELS.md 자매 문서** (`flutter/driver_app/MVP/MODELS.md`) — 동일 원칙 + 손님앱 차이점 명시
> **원본 기준**: `customer_app/app/src/main/java/com/designated/customer/data/model/` 6개 + `ui/main/CallStatus.kt` + `ui/auth/PhoneAuthViewModel.kt`
> **작성일**: 2026-04-16
> **작성자**: kotlin-expert

---

## Dart 3 + Freezed 스타일 가이드 (기사앱 MODELS.md §가이드와 동일)

1. **모든 data class는 `@freezed` + `fromJson/toJson` 지원** (build_runner 자동 생성)
2. **nullable은 명시 `?`**, default는 `@Default()` 어노테이션. Kotlin `= ""` 기본값 → `@Default('')`
3. **`late` 키워드 금지**. 초기화 미확정 값은 nullable로 설계
4. **Firestore `fromJson` 시 안전 파싱**: `as String? ?? ''`, `(json['field'] as num?)?.toInt() ?? 0`
5. **enum 필드는 ENUMS.md sealed class 사용** (CallState, CustomerGrade, TransactionType)

### Kotlin → Dart 매핑 원칙 (기사앱 MODELS.md 동일)

| Kotlin | Dart / Freezed | 비고 |
|--------|---------------|-----|
| `val x: String = ""` | `@Default('') String x` | 기본값 보존 |
| `val x: String? = null` | `String? x` | nullable 유지 |
| `Timestamp?` | `DateTime?` | TimestampConverter 사용 (기사앱 MODELS.md §1) |
| `Long timestamp (epoch ms)` | `int timestamp` + `DateTime.fromMillisecondsSinceEpoch` helper | **CustomerCall 특수** (§1) |
| `Long` / `Int` | `int` | Dart int = 64bit |
| `enum class` | ENUMS.md sealed class 또는 Dart enum | loose typing 허용 시 String |

---

## 손님앱 vs 기사앱 차이점 (필독)

| 차이 항목 | 기사앱 | 손님앱 |
|----------|--------|-------|
| 인증 | Firebase Email/Password Auth | **Anonymous Auth 자동 + Phone Auth (프로필 시)** |
| 문서 ID 기준 | `authUid` | **`phoneNumber`** (customerInfo, customerPoints) |
| 실시간 리스너 | carryOver 1개 | **customerPoints + pointTransactions 2개** |
| FCM 메시지 타입 | 6종 (call_assigned 등) | **5종** (CALL_RECEIVED / DRIVER_ASSIGNED / RIDE_COMPLETED / CALL_CANCELLED / call_status_update) |
| 데이터 모델 수 | 13종 (정산 포함) | **6종** (+ UI 상태 구조체) |
| Presence | Realtime DB 필수 | **불필요** (손님앱 presence 기능 없음) |
| 사무실 정보 저장 | Firestore + SharedPreferences | **SharedPreferences만** (Install Referrer로 받은 데이터) |
| 상태 전이 | 9종 DriverStatus | **6종 CallState** (REQUESTED/ASSIGNED/DRIVER_ARRIVING/IN_PROGRESS/COMPLETED/CANCELLED) |

---

## 모델 인벤토리 (6종 + UI 상태 6종)

| # | 모델명 | Kotlin 파일 (라인) | Firestore 경로 | 분류 |
|---|--------|------------------|---------------|------|
| 1 | **CustomerCall** | `data/model/CustomerCall.kt:5-94` | `provinces/{p}/cities/{c}/offices/{o}/calls/{callId}` | B (Phase 1 필수) |
| 2 | **CustomerInfo** | `data/model/CustomerInfo.kt:8-85` | `provinces/{p}/cities/{c}/offices/{o}/customers/{uid}` + `customerInfo/{phone}` | B |
| 3 | **CustomerPoints** | `data/model/CustomerPoints.kt:8-84` | `provinces/{p}/cities/{c}/offices/{o}/customerPoints/{phone}` | B |
| 4 | **CustomerGrade** (enum) | `data/model/CustomerGrade.kt:7-95` | (코드 전용) | B (ENUMS.md §C1로 승격 권장) |
| 5 | **PointTransaction** | `data/model/PointTransaction.kt:8-51` | `provinces/{p}/cities/{c}/offices/{o}/pointTransactions/{autoId}` | B |
| 6 | **BannerAdData** | `data/model/BannerAdData.kt:9-61` | `provinces/{p}/cities/{c}/offices/{o}/banners/{id}` 또는 전역 | R2 (배너 광고) |

**UI 상태 구조체** (MainViewModel 분해 대응, NOTIFIERS.md 4 Notifier 소비):
- **CallStatus + DriverInfo** (`ui/main/CallStatus.kt:3-37`) — CallNotifier state
- **CallUiState** (MainUiState의 콜 관련 서브셋) — CallNotifier state
- **PointUiState** (MainUiState의 포인트 관련 서브셋) — PointNotifier state
- **ProfileUiState** (MainUiState의 프로필/사무실 관련 서브셋) — ProfileNotifier state
- **AuthUiState** (PhoneAuthViewModel `PhoneAuthUiState` + 익명 인증 상태) — AuthNotifier state
- **TermsUiState** (TermsAgreementViewModel 기반) — ProfileNotifier 또는 AuthNotifier 내부

---

## 1. CustomerCall (콜 엔티티, 18 필드)

### Kotlin 원본 (`customer_app/app/src/main/java/com/designated/customer/data/model/CustomerCall.kt:5-94`)

```kotlin
data class CustomerCall(
    val id: String = "",
    val phoneNumber: String,
    val officeId: String,
    val provinceId: String,
    val cityId: String,
    val currentLocation: String,
    val destinationLocation: String,
    val timestamp: Long,                // epoch millis
    val status: String,                 // REQUESTED/ASSIGNED/DRIVER_ARRIVING/IN_PROGRESS/COMPLETED/CANCELLED
    val driverId: String? = null,
    val estimatedArrivalTime: Int? = null,
    val fare: Int? = null,
    val notes: String? = null,
    val createdFrom: String = "customer_app",
    val customerId: String? = null,
    val customerName: String? = null,
    val customerGrade: String? = "bronze",
    val isAppCustomer: Boolean = true,
    val pointsUsed: Int = 0,
    val finalFare: Int? = null,
    val discountAmount: Int = 0
)
```

### Firestore 경로 + 저장 구조 (Kotlin `toMap` 분석)

**경로**: `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}`

**⚠️ 필드명 매핑 (Kotlin `CustomerCall.kt:29-57` `toMap`)**:

| Dart 필드 | Firestore 필드 | 비고 |
|----------|---------------|-----|
| `currentLocation` | `customerAddress` + `departure` + `departure_set` | **Kotlin이 3개 필드에 동일 값 중복 저장** (기사앱 호환용) |
| `destinationLocation` | `destination` + `destination_set` | **동일 값 중복 저장** |
| `timestamp` | `Timestamp(seconds, nanoseconds)` | Kotlin Long → Firestore Timestamp 변환 |
| `driverId` | `assignedDriverId` | Kotlin은 `driverId` 이름, Firestore는 `assignedDriverId` |

### Flutter Freezed 매핑

```dart
// lib/features/call/domain/entities/customer_call.dart

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'converters/timestamp_converter.dart';

part 'customer_call.freezed.dart';
part 'customer_call.g.dart';

@freezed
class CustomerCall with _$CustomerCall {
  const CustomerCall._();

  const factory CustomerCall({
    @Default('') String id,
    required String phoneNumber,
    required String officeId,
    required String provinceId,
    required String cityId,
    @Default('') String currentLocation,          // Firestore: customerAddress/departure/departure_set
    @Default('') String destinationLocation,      // Firestore: destination/destination_set
    @Default(0) int timestamp,                    // epoch millis
    @Default('REQUESTED') String status,          // ENUMS.md §C0 CallState sealed class 매핑
    String? driverId,                             // Firestore: assignedDriverId
    int? estimatedArrivalTime,
    int? fare,
    String? notes,
    @Default('customer_app') String createdFrom,
    String? customerId,
    String? customerName,
    @Default('bronze') String? customerGrade,     // ENUMS.md §C1 CustomerGrade enum과 연동 (loose typing 보존)
    @Default(true) bool isAppCustomer,
    @Default(0) int pointsUsed,
    int? finalFare,
    @Default(0) int discountAmount,
  }) = _CustomerCall;

  factory CustomerCall.fromJson(Map<String, Object?> json) =>
      _$CustomerCallFromJson(json);

  /// Firestore 문서 → CustomerCall (Kotlin fromMap 등가)
  factory CustomerCall.fromFirestore(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};

    // timestamp — Kotlin은 Timestamp ↔ Long epoch millis 양방향 지원
    final tsRaw = data['timestamp'];
    final tsMillis = switch (tsRaw) {
      Timestamp() => tsRaw.seconds * 1000 + tsRaw.nanoseconds ~/ 1000000,
      num() => tsRaw.toInt(),
      _ => DateTime.now().millisecondsSinceEpoch,
    };

    return CustomerCall(
      id: doc.id,
      phoneNumber: data['phoneNumber'] as String? ?? '',
      officeId: data['officeId'] as String? ?? '',
      provinceId: data['provinceId'] as String? ?? data['regionId'] as String? ?? '',
      cityId: data['cityId'] as String? ?? '',
      currentLocation: (data['customerAddress'] as String?) ??
          (data['departure'] as String?) ?? '',
      destinationLocation: data['destination'] as String? ?? '',
      timestamp: tsMillis,
      status: data['status'] as String? ?? 'REQUESTED',
      driverId: data['assignedDriverId'] as String?,   // ⚠️ Firestore 키는 assignedDriverId
      estimatedArrivalTime: (data['estimatedArrivalTime'] as num?)?.toInt(),
      fare: (data['fare'] as num?)?.toInt(),
      notes: data['notes'] as String?,
      createdFrom: data['createdFrom'] as String? ?? 'customer_app',
      customerId: data['customerId'] as String?,
      customerName: data['customerName'] as String?,
      customerGrade: (data['customerGrade'] as String?) ?? 'bronze',
      isAppCustomer: data['isAppCustomer'] as bool? ?? true,
      pointsUsed: (data['pointsUsed'] as num?)?.toInt() ?? 0,
      finalFare: (data['finalFare'] as num?)?.toInt(),
      discountAmount: (data['discountAmount'] as num?)?.toInt() ?? 0,
    );
  }

  /// Firestore 저장용 Map (Kotlin toMap 등가)
  /// ⚠️ Kotlin 원본의 필드 중복 저장 정확히 재현
  Map<String, Object?> toFirestore() {
    final tsFirestore = Timestamp(timestamp ~/ 1000, ((timestamp % 1000) * 1000000));
    return {
      'id': id,
      'phoneNumber': phoneNumber,
      'officeId': officeId,
      'provinceId': provinceId,
      'cityId': cityId,
      'customerAddress': currentLocation,        // 콜매니저 호환
      'departure': currentLocation,
      'departure_set': currentLocation,          // 기사앱 호환 (snake_case 보존)
      'destination': destinationLocation,
      'destination_set': destinationLocation,    // 기사앱 호환 (snake_case 보존)
      'timestamp': tsFirestore,
      'status': status,
      'assignedDriverId': driverId,              // ⚠️ Firestore 키는 assignedDriverId
      'estimatedArrivalTime': estimatedArrivalTime,
      'fare': fare,
      'notes': notes,
      'createdFrom': createdFrom,
      'customerId': customerId,
      'customerName': customerName,
      'customerGrade': customerGrade,
      'isAppCustomer': isAppCustomer,
      'pointsUsed': pointsUsed,
      'finalFare': finalFare,
      'discountAmount': discountAmount,
    };
  }
}
```

### 필드별 매핑 노트 (중요 이슈 정리)

| Kotlin 필드 | Dart 필드 | 변환 노트 |
|------------|----------|----------|
| `val currentLocation: String` | `@Default('') String currentLocation` | **Firestore는 3개 필드 중복 저장** (customerAddress + departure + departure_set). Dart fromFirestore에서 **fallback 순서** `customerAddress → departure` |
| `val destinationLocation: String` | 동일 | Firestore 2개 중복 (destination + destination_set) |
| `val timestamp: Long` (epoch ms) | `@Default(0) int timestamp` | Kotlin은 epoch ms, Firestore는 Timestamp → 변환 명시 |
| `val status: String` | `@Default('REQUESTED') String status` | **ENUMS.md §C0 CallState sealed class로 승격 권장** (기사앱 MODELS.md와 달리 현재는 String 유지 — 손님앱은 Firestore 호환 타 앱과 공유하므로 loose typing이 안전) |
| `val driverId: String?` | `String? driverId` (Dart 내부) | **Firestore 키는 `assignedDriverId`** (기사앱 CallInfo와 동일 키). fromFirestore/toFirestore 매핑 |
| `val customerGrade: String? = "bronze"` | `@Default('bronze') String? customerGrade` | 소문자 Firestore 저장. ENUMS.md `CustomerGrade` enum과 **대소문자 불일치** — toString 변환 주의 |
| `val pointsUsed: Int = 0` | `@Default(0) int pointsUsed` | 포인트 사용액 |
| `val finalFare: Int?` | `int? finalFare` | `fare - pointsUsed - discountAmount` 결과 (CF가 계산) |

### 기사앱 `CallInfo` (MODELS.md §1) 와의 차이

| 필드 | CallInfo (기사앱) | CustomerCall (손님앱) |
|------|-----------------|--------------------|
| id | `var id: String = ""` | `val id: String = ""` |
| 위치 | `customerAddress` + `destination` | `currentLocation` + `destinationLocation` (Dart), Firestore는 동일 |
| timestamp | `Timestamp?` (nullable) | `Long` epoch millis (non-null) |
| 상태 | 11종 CallStatus (WAITING/ASSIGNED/.../CLAIMED) | **6종 CallState** (REQUESTED/ASSIGNED/DRIVER_ARRIVING/IN_PROGRESS/COMPLETED/CANCELLED) |
| 고객 정보 | `customerName/phoneNumber` (기사가 읽음) | `customerName/phoneNumber/customerGrade/customerId` (고객 자신 기록) |
| `assignedDriver*` 3종 | Dart 필드 직접 매핑 | **CustomerCall에는 없음** (DriverInfo를 CallStatus.driverInfo로 별도 관리) |
| `departure_set/destination_set/waypoints_set/fare_set` | Dart `@JsonKey(name:)` 로 snake_case 보존 | **CustomerCall은 `departure_set/destination_set`만 기록** (fare는 기사앱이 설정) |
| `isAppCustomer` | 기본값 `false` | **기본값 `true`** (손님앱 출신 100%) |
| `pointsUsed/finalFare/discountAmount` | 없음 (기사앱은 별도 fare 계산) | **손님앱 전용 3필드** |

**핵심**: 손님앱 CustomerCall은 **콜 요청 시점의 스냅샷** + 기사앱/CF가 업데이트하는 필드 (`assignedDriverId`, `status`, `fare_set` 등) 가 섞인 공유 문서. Dart 측에서 `toFirestore()` 호출은 **최초 `requestCall()` 시점에만** 사용. 이후 업데이트는 각 Notifier의 `update()` 메서드로 개별 필드만 수정.

---

## 2. CustomerInfo (고객 정보, 17 필드)

### Kotlin 원본 (`data/model/CustomerInfo.kt:8-85`)

```kotlin
data class CustomerInfo(
    val id: String = "",
    val phoneNumber: String = "",
    val name: String = "",
    val grade: String = "bronze",
    val points: Int = 0,
    val totalRides: Int = 0,
    val totalSpent: Long = 0L,
    val linkedOfficeId: String = "",
    val primaryOfficeId: String = "",         // 최초 연결 사무실 (불변)
    val attributionScore: Int? = null,
    val attributionSource: String? = null,    // landing/qr_scan/referral
    val registeredAt: Timestamp = Timestamp.now(),
    val lastRideAt: Timestamp? = null,

    // 사무실 연락처
    val officePhone: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val accountHolder: String = "",

    // 주소
    val homeAddress: String = ""
)
```

### Firestore 경로 (복합)

**두 경로에 저장됨** (CLAUDE.md Customer App 기록 + Kotlin 코드 분석):

1. **`provinces/{p}/cities/{c}/offices/{o}/customers/{uid}`** — **프로필 상세** (customers 컬렉션, uid가 문서 ID)
   - 이 경로는 Anonymous Auth uid 기반 저장
2. **`provinces/{p}/cities/{c}/offices/{o}/customerInfo/{phoneNumber}`** — **FCM 토큰 + 사무실 연락처** (customerInfo, phoneNumber가 문서 ID)
   - 손님앱 `MainActivity.kt:306-327` 가 저장하는 경로

**⚠️ 중요**: Flutter 측도 **이 2경로 구조 유지 필수**. CustomerInfo 모델 하나로 양쪽 문서 읽기/쓰기 가능하나 **경로 선택은 저장 시점에 따라**:
- 프로필 설정 완료 → `customers/{uid}.set()`
- FCM 토큰 갱신 → `customerInfo/{phone}.set({ fcmToken, fcmTokenPlatform }, merge)`

### Flutter Freezed 매핑

```dart
// lib/features/profile/domain/entities/customer_info.dart

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'converters/timestamp_converter.dart';

part 'customer_info.freezed.dart';
part 'customer_info.g.dart';

@freezed
class CustomerInfo with _$CustomerInfo {
  const CustomerInfo._();

  const factory CustomerInfo({
    @Default('') String id,
    @Default('') String phoneNumber,
    @Default('') String name,
    @Default('bronze') String grade,      // ENUMS.md §C1 CustomerGrade와 loose coupling
    @Default(0) int points,
    @Default(0) int totalRides,
    @Default(0) int totalSpent,           // Kotlin Long → Dart int
    @Default('') String linkedOfficeId,
    @Default('') String primaryOfficeId,  // 최초 연결 사무실 (불변)
    int? attributionScore,
    String? attributionSource,            // 'landing' | 'qr_scan' | 'referral'
    @TimestampConverter() DateTime? registeredAt,
    @TimestampConverter() DateTime? lastRideAt,

    // 사무실 연락처
    @Default('') String officePhone,
    @Default('') String bankName,
    @Default('') String accountNumber,
    @Default('') String accountHolder,

    // 주소
    @Default('') String homeAddress,
  }) = _CustomerInfo;

  factory CustomerInfo.fromJson(Map<String, Object?> json) =>
      _$CustomerInfoFromJson(json);

  /// Firestore 문서 → CustomerInfo (Kotlin fromMap 등가)
  factory CustomerInfo.fromFirestore(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};
    // homeAddress — 기존 사용자는 'address' 필드 사용했을 수 있음 (Kotlin fromMap fallback)
    final homeAddr = (data['homeAddress'] as String?)?.isNotEmpty == true
        ? data['homeAddress'] as String
        : (data['address'] as String?) ?? '';

    return CustomerInfo(
      id: data['id'] as String? ?? '',
      phoneNumber: data['phoneNumber'] as String? ?? '',
      name: data['name'] as String? ?? '',
      grade: data['grade'] as String? ?? 'bronze',
      points: (data['points'] as num?)?.toInt() ?? 0,
      totalRides: (data['totalRides'] as num?)?.toInt() ?? 0,
      totalSpent: (data['totalSpent'] as num?)?.toInt() ?? 0,
      linkedOfficeId: data['linkedOfficeId'] as String? ?? '',
      primaryOfficeId: data['primaryOfficeId'] as String? ?? '',
      attributionScore: (data['attributionScore'] as num?)?.toInt(),
      attributionSource: data['attributionSource'] as String?,
      registeredAt: (data['registeredAt'] as Timestamp?)?.toDate(),
      lastRideAt: (data['lastRideAt'] as Timestamp?)?.toDate(),
      officePhone: data['officePhone'] as String? ?? '',
      bankName: data['bankName'] as String? ?? '',
      accountNumber: data['accountNumber'] as String? ?? '',
      accountHolder: data['accountHolder'] as String? ?? '',
      homeAddress: homeAddr,
    );
  }
}
```

### 필드별 주의사항

| Kotlin 필드 | Dart 필드 | 비고 |
|------------|----------|-----|
| `primaryOfficeId: String` | `@Default('') String primaryOfficeId` | **불변 (최초 연결)**. Phase 2 App Clip 귀속 핵심 (ATTRIBUTION.md §1) |
| `linkedOfficeId: String` | `@Default('') String linkedOfficeId` | 현재 연결 사무실 (변경 가능) |
| `attributionSource: String?` | `String? attributionSource` | `'landing' \| 'qr_scan' \| 'referral'` loose typing |
| `totalSpent: Long` | `@Default(0) int totalSpent` | 원화 집계, int 수용 가능 |
| `registeredAt: Timestamp` (non-null Kotlin) | `DateTime? registeredAt` | Dart에서 nullable 처리 (fromFirestore 실패 대비) |
| `homeAddress: String` | `@Default('') String homeAddress` | fromFirestore에서 `address` fallback (legacy 호환) |

### 기사앱 `Driver` (MODELS.md §2) 와의 차이

- **문서 ID**: 기사앱 = `authUid` / 손님앱 = `phoneNumber` 또는 `uid` (경로에 따라)
- **승인 플로우**: 기사앱 = `pending_drivers` → `designated_drivers` / 손님앱 = **승인 불필요** (Anonymous Auth 즉시 사용)
- **상태 필드**: 기사앱 = `status: DriverStatus` / 손님앱 = **없음** (상태 개념 없음, `lastRideAt` 만 있음)

---

## 3. CustomerPoints (포인트 정보, 9 필드)

### Kotlin 원본 (`data/model/CustomerPoints.kt:8-84`)

```kotlin
data class CustomerPoints(
    val customerId: String = "",
    val phoneNumber: String = "",
    val currentPoints: Int = 0,
    val totalEarned: Int = 0,
    val totalUsed: Int = 0,
    val grade: CustomerGrade = CustomerGrade.BRONZE,
    val totalCalls: Int = 0,
    val lastUpdated: Timestamp = Timestamp.now(),
    val createdAt: Timestamp = Timestamp.now()
) {
    fun shouldUpdateGrade(): Boolean
    fun getUpdatedGrade(): CustomerGrade
    fun canUsePoints(amount: Int): Boolean
    fun getCallsToNextGrade(): Int?
    fun calculateEarnPoints(fare: Int): Int
}
```

### Firestore 경로

`provinces/{p}/cities/{c}/offices/{o}/customerPoints/{phoneNumber}`

**문서 ID는 phoneNumber**. 기사앱 CustomerPoints (MODELS.md §5) 와 **동일 경로** (driver_app도 정산 시 이 경로 read-only 조회).

### 기사앱 MODELS.md §5 CustomerPoints와의 차이

기사앱은 **읽기 전용** + Kotlin `grade: String` / 손님앱은 **Kotlin `grade: CustomerGrade` enum**. 저장 시 `grade.name` (대문자 "BRONZE")으로 Firestore 기록.

**⚠️ 손님앱 Firestore 저장은 대문자 "BRONZE"/"SILVER"/"GOLD"/"VIP"**, `CustomerCall.customerGrade`는 소문자 `"bronze"` 등 — 일관성 불일치. Flutter 측도 Kotlin 관용 보존.

### Flutter Freezed 매핑

```dart
// lib/features/point/domain/entities/customer_points.dart (손님앱 버전)

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import '../enums/customer_grade.dart';   // ENUMS.md §C1 (신규)
import 'converters/timestamp_converter.dart';
import 'converters/customer_grade_converter.dart';

part 'customer_points.freezed.dart';
part 'customer_points.g.dart';

@freezed
class CustomerPoints with _$CustomerPoints {
  const CustomerPoints._();

  const factory CustomerPoints({
    @Default('') String customerId,
    @Default('') String phoneNumber,
    @Default(0) int currentPoints,
    @Default(0) int totalEarned,
    @Default(0) int totalUsed,
    @CustomerGradeConverter()
    @Default(CustomerGrade.bronze) CustomerGrade grade,
    @Default(0) int totalCalls,
    @TimestampConverter() DateTime? lastUpdated,
    @TimestampConverter() DateTime? createdAt,
  }) = _CustomerPoints;

  factory CustomerPoints.fromJson(Map<String, Object?> json) =>
      _$CustomerPointsFromJson(json);

  factory CustomerPoints.fromFirestore(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};
    return CustomerPoints.fromJson(data);
  }

  /// Kotlin shouldUpdateGrade 등가
  bool get shouldUpdateGrade => grade != CustomerGrade.fromCallCount(totalCalls);

  /// Kotlin getUpdatedGrade 등가
  CustomerGrade get updatedGrade => CustomerGrade.fromCallCount(totalCalls);

  /// Kotlin canUsePoints 등가
  bool canUsePoints(int amount) => currentPoints >= amount;

  /// Kotlin calculateEarnPoints 등가
  int calculateEarnPoints(int fare) => grade.calculatePoints(fare);

  /// 다음 등급까지 필요 콜 수 (null = 최고 등급)
  int? get callsToNextGrade => grade.callsToNext(totalCalls);
}
```

### CustomerGradeConverter (신규)

```dart
// lib/core/domain/converters/customer_grade_converter.dart

import 'package:json_annotation/json_annotation.dart';
import '../../enums/customer_grade.dart';

class CustomerGradeConverter implements JsonConverter<CustomerGrade, String?> {
  const CustomerGradeConverter();

  @override
  CustomerGrade fromJson(String? json) => CustomerGrade.fromString(json ?? 'BRONZE');

  @override
  String toJson(CustomerGrade object) => object.toJson();
}
```

---

## 4. CustomerGrade (enum → Dart enum with behavior)

> **단일 원본**: `flutter/customer_app/MVP/ENUMS.md §2` 참조.
>
> 구현 위치: `lib/core/domain/enums/customer_grade.dart` (6 필드 enhanced enum: `json` / `displayName` / `icon` / `pointRate` / `minCalls` / `colorArgb` + 메서드 `fromCallCount` / `fromString` / `toJson` / `calculatePoints` / `nextGrade` / `callsToNext`).
>
> MODELS에서는 import만:
> ```dart
> import '../enums/customer_grade.dart';
> ```

### Kotlin 원본 요약 (`data/model/CustomerGrade.kt:7-94`)

- 4단계: BRONZE(3%, 0+) / SILVER(5%, 10+) / GOLD(7%, 30+) / VIP(9%, 50+)
- 필드: displayName, icon(이모지), pointRate, minCalls, color(Long ARGB)
- 메서드: fromCallCount, fromString, nextGrade, getCallsToNextGrade, calculatePoints
- Firestore 저장: Kotlin enum `.name` 그대로 (별도 매핑 필드 없음)

### 기사앱 MODELS.md §5 `CustomerPoints.grade: String` 과의 차이

기사앱은 **표시용만** (`'BRONZE'` 문자열 그대로 쓰는 loose typing). 손님앱은 **비즈니스 로직 (적립률 계산)** 필수 → enum으로 타입 안전성 확보.

---

## 5. PointTransaction (거래 내역, 9 필드 + TransactionType enum)

### Kotlin 원본 (`data/model/PointTransaction.kt:8-62`)

```kotlin
data class PointTransaction(
    val id: String = "",
    val customerId: String = "",
    val type: TransactionType = TransactionType.EARN,
    val amount: Int = 0,
    val balance: Int = 0,           // 거래 후 잔액
    val description: String = "",
    val callId: String? = null,
    val timestamp: Timestamp = Timestamp.now(),
    val fare: Int? = null,          // 적립 시 운행 요금
    val grade: String = "BRONZE"    // 거래 시점 등급
)

enum class TransactionType { EARN, USE, EXPIRE, CANCEL, ADMIN }
```

### Firestore 경로

`provinces/{p}/cities/{c}/offices/{o}/pointTransactions/{autoId}`

- autoGenerated document ID (Kotlin `PointService.kt:183` `collection.document()` 호출)
- `customerId = phoneNumber` (Kotlin PointService 관용)
- **`whereEqualTo("customerId", phoneNumber)` + `orderBy("timestamp", DESC)`** 쿼리로 개별 손님 거래 조회

### Flutter Freezed 매핑

```dart
// lib/features/point/domain/entities/point_transaction.dart

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import '../enums/transaction_type.dart';      // ENUMS.md §C2 (신규)
import 'converters/timestamp_converter.dart';

part 'point_transaction.freezed.dart';
part 'point_transaction.g.dart';

@freezed
class PointTransaction with _$PointTransaction {
  const factory PointTransaction({
    @Default('') String id,
    @Default('') String customerId,           // = phoneNumber
    @TransactionTypeConverter()
    @Default(TransactionType.earn) TransactionType type,
    @Default(0) int amount,                    // EARN 양수 / USE 음수
    @Default(0) int balance,                   // 거래 후 잔액
    @Default('') String description,
    String? callId,
    @TimestampConverter() DateTime? timestamp,
    int? fare,                                 // 적립 시 운행 요금
    @Default('BRONZE') String grade,
  }) = _PointTransaction;

  factory PointTransaction.fromJson(Map<String, Object?> json) =>
      _$PointTransactionFromJson(json);

  factory PointTransaction.fromFirestore(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};
    return PointTransaction.fromJson({...data, 'id': doc.id});
  }
}
```

### TransactionType enum + Converter

> **단일 원본**: `flutter/customer_app/MVP/ENUMS.md §3` 참조.
>
> 구현 위치: `lib/core/domain/enums/transaction_type.dart` (3 필드 enhanced enum: `json` / `displayName` / `sign` + 메서드 `fromString` / `toJson` / `isValidAmount` / `formatAmount`). `sign` 필드는 Kotlin 원본에 없는 Flutter 확장 (amount 부호 UI 포맷팅 용).

```dart
// lib/core/domain/converters/transaction_type_converter.dart

import 'package:json_annotation/json_annotation.dart';
import '../../enums/transaction_type.dart';

class TransactionTypeConverter implements JsonConverter<TransactionType, String> {
  const TransactionTypeConverter();

  @override
  TransactionType fromJson(String json) => TransactionType.fromString(json);

  @override
  String toJson(TransactionType object) => object.toJson();
}
```

### amount 부호 관용구 (Kotlin PointService 분석)

- **EARN**: `amount = +earnPoints` (양수)
- **USE**: `amount = -usedPoints` (**음수로 저장**, `PointService.kt:239`)
- **CANCEL** (환불): `amount = +refundAmount` (양수, `PointService.kt:301`)
- **EXPIRE/ADMIN**: 정책에 따라

**UI 표시 시**: `amount.abs()` 사용 + `TransactionType`에 따라 + / - 기호 분리 표시.

---

## 6. BannerAdData (R2, 배너 광고)

### Kotlin 원본 (`data/model/BannerAdData.kt:9-61`)

```kotlin
data class BannerAdData(
    val id: String = "",
    val text: String = "스마트 대리운전 통합 시스템",
    val imageUrl: String = "",
    val linkUrl: String = "",
    val backgroundColor1: String = "#FF6B35",
    val backgroundColor2: String = "#F7931E",
    val textColor: String = "#FFFFFF",
    val isActive: Boolean = true,
    val priority: Int = 0,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)
```

### Firestore 경로

**미확정** — Kotlin 원본 코드 확인으로는 `BannerAdService.kt` 가 경로 결정. 본 MVP에서는 **R2 분류**로 Phase 1 제외 (OVERVIEW.md §R2).

### Flutter Freezed 매핑 (R2)

```dart
// lib/features/ad/domain/entities/banner_ad_data.dart (R2 — 선택)

@freezed
class BannerAdData with _$BannerAdData {
  const factory BannerAdData({
    @Default('') String id,
    @Default('스마트 대리운전 통합 시스템') String text,
    @Default('') String imageUrl,
    @Default('') String linkUrl,
    @Default('#FF6B35') String backgroundColor1,
    @Default('#F7931E') String backgroundColor2,
    @Default('#FFFFFF') String textColor,
    @Default(true) bool isActive,
    @Default(0) int priority,
    @TimestampConverter() DateTime? createdAt,
    @TimestampConverter() DateTime? updatedAt,
  }) = _BannerAdData;

  factory BannerAdData.fromJson(Map<String, Object?> json) =>
      _$BannerAdDataFromJson(json);

  factory BannerAdData.fromFirestore(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};
    return BannerAdData.fromJson({...data, 'id': doc.id});
  }
}
```

**R2 근거**: OVERVIEW.md §R2 "R2_BANNER_AD: 배너 광고". Phase 1 범위 제외. 구현은 `BannerAdService.kt` 상세 분석 후 진행.

---

## 7. UI 상태 구조체 (MainViewModel 분해 대응)

### 7.1 CallStatus + DriverInfo (CallNotifier state 핵심)

**Kotlin 원본** (`customer_app/app/src/main/java/com/designated/customer/ui/main/CallStatus.kt:1-37`):

```kotlin
data class CallStatus(
    val callId: String,
    val state: CallState,
    val timestamp: Long,
    val driverInfo: DriverInfo? = null,
    val estimatedArrivalTime: Int = 0
)

enum class CallState {
    REQUESTED,
    ASSIGNED,
    DRIVER_ARRIVING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

data class DriverInfo(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val vehicleNumber: String,
    val rating: Float = 0f
)
```

### 7.2 ENUMS.md §C0 `CallState` sealed class (신규 승격 권장)

```dart
// lib/core/domain/enums/call_state.dart (ENUMS.md §C0 신규)

sealed class CallState {
  const CallState();

  static CallState fromString(String value) => switch (value) {
    'REQUESTED' => const CallStateRequested(),
    'ASSIGNED' => const CallStateAssigned(),
    'DRIVER_ARRIVING' => const CallStateDriverArriving(),
    'IN_PROGRESS' => const CallStateInProgress(),
    'COMPLETED' => const CallStateCompleted(),
    'CANCELLED' => const CallStateCancelled(),
    _ => CallStateUnknown(value),
  };

  String toJson();
  String get displayName;

  /// 취소 가능 여부 (REQUESTED/ASSIGNED/DRIVER_ARRIVING 단계)
  bool get canCancel;
}

class CallStateRequested extends CallState {
  const CallStateRequested();
  @override String toJson() => 'REQUESTED';
  @override String get displayName => '콜 요청됨';
  @override bool get canCancel => true;
}

class CallStateAssigned extends CallState {
  const CallStateAssigned();
  @override String toJson() => 'ASSIGNED';
  @override String get displayName => '기사 배정됨';
  @override bool get canCancel => true;
}

class CallStateDriverArriving extends CallState {
  const CallStateDriverArriving();
  @override String toJson() => 'DRIVER_ARRIVING';
  @override String get displayName => '기사 이동 중';
  @override bool get canCancel => true;
}

class CallStateInProgress extends CallState {
  const CallStateInProgress();
  @override String toJson() => 'IN_PROGRESS';
  @override String get displayName => '운행 중';
  @override bool get canCancel => false;
}

class CallStateCompleted extends CallState {
  const CallStateCompleted();
  @override String toJson() => 'COMPLETED';
  @override String get displayName => '운행 완료';
  @override bool get canCancel => false;
}

class CallStateCancelled extends CallState {
  const CallStateCancelled();
  @override String toJson() => 'CANCELLED';
  @override String get displayName => '취소됨';
  @override bool get canCancel => false;
}

class CallStateUnknown extends CallState {
  final String rawValue;
  const CallStateUnknown(this.rawValue);
  @override String toJson() => rawValue;
  @override String get displayName => '알 수 없음';
  @override bool get canCancel => false;
}
```

### 7.3 CallStatus + DriverInfo Freezed

```dart
// lib/features/call/presentation/state/call_status.dart

import 'package:freezed_annotation/freezed_annotation.dart';
import '../enums/call_state.dart';

part 'call_status.freezed.dart';

@freezed
class CallStatus with _$CallStatus {
  const CallStatus._();

  const factory CallStatus({
    required String callId,
    required CallState state,
    required int timestamp,            // epoch millis
    DriverInfo? driverInfo,
    @Default(0) int estimatedArrivalTime,
  }) = _CallStatus;

  /// UI 헬퍼 (Kotlin getStatusText 등가)
  String get statusText => state.displayName;
}

@freezed
class DriverInfo with _$DriverInfo {
  const factory DriverInfo({
    required String id,
    required String name,
    required String phoneNumber,
    required String vehicleNumber,
    @Default(0.0) double rating,
  }) = _DriverInfo;
}
```

### 7.4 4 Notifier별 UiState 구조체 (분해 근거)

MainUiState (`ui/main/MainViewModel.kt:30-69`, 24 필드) → **4 Notifier 분해**:

#### CallUiState (CallNotifier)

```dart
@freezed
class CallUiState with _$CallUiState {
  const factory CallUiState({
    @Default('') String currentLocation,
    @Default('') String destinationLocation,
    @Default(false) bool isLoadingLocation,
    @Default(false) bool isLoadingCall,
    CallStatus? callStatus,
    String? error,
    @Default(false) bool showLocationCard,      // 앱호출 버튼 상태
  }) = _CallUiState;
}
```

#### PointUiState (PointNotifier)

```dart
@freezed
class PointUiState with _$PointUiState {
  const factory PointUiState({
    CustomerPoints? customerPoints,
    @Default(false) bool isLoadingPoints,
    @Default(false) bool usePoints,
    @Default(0) int pointsToUse,
    @Default(false) bool showPointsEarnedDialog,
    @Default(0) int earnedPoints,
    @Default(0) int usedPoints,
    @Default(0) int rideCompletedFare,
  }) = _PointUiState;
}
```

#### ProfileUiState (ProfileNotifier)

```dart
@freezed
class ProfileUiState with _$ProfileUiState {
  const factory ProfileUiState({
    CustomerInfo? customerInfo,
    @Default('') String officeName,
    @Default('') String regionName,
    @Default('') String homeAddress,
    @Default(false) bool showHomeAddressDialog,
    @Default('당신만의 기사가 모십니다') String slogan,
  }) = _ProfileUiState;
}
```

#### AuthUiState (AuthNotifier)

**Kotlin 원본** (`customer_app/.../ui/auth/PhoneAuthViewModel.kt:18-25`):

```kotlin
data class PhoneAuthUiState(
    val isLoading: Boolean = false,
    val phoneNumber: String = "",
    val verificationCode: String = "",
    val isCodeSent: Boolean = false,
    val error: String? = null,
    val isVerified: Boolean = false
)
```

**Flutter 확장** (Anonymous Auth + Phone Auth 통합):

```dart
@freezed
class AuthUiState with _$AuthUiState {
  const factory AuthUiState({
    @Default(false) bool isLoading,
    @Default('') String phoneNumber,
    @Default('') String verificationCode,
    @Default(false) bool isCodeSent,
    String? error,
    @Default(false) bool isVerified,                  // Phone Auth 검증 완료
    @Default(false) bool isAnonymouslySignedIn,       // Anonymous Auth 세션
    String? anonymousUid,                             // Anonymous Auth uid (Firestore customers/{uid} 키)
    String? verificationId,                            // Kotlin verificationId 등가
  }) = _AuthUiState;
}
```

### 7.5 TermsUiState (옵션)

**Kotlin 원본** (`TermsAgreementViewModel.kt` — 본 MVP에서 직접 확인 안 함. 약관 동의 상태 관리 + SharedPreferences 저장).

**Phase 1 MVP**: ProfileNotifier 내부에 통합 (별도 Notifier 불필요).

---

## 8. 음성입력 상태 (R2)

**Kotlin 원본** (`MainViewModel.kt:51-52` + `util/VoiceInputHelper.kt`):
```kotlin
val isRecordingDeparture: Boolean = false,
val isRecordingDestination: Boolean = false,
```

**Phase 1 제외 (R2)**. OVERVIEW.md §R2 `R2_VOICE_INPUT` 참조. 구현 시 CallUiState에 필드 추가.

---

## 9. 마이그레이션 작업 순서 (Phase 6)

### Week 1 — 기반 구조
1. `lib/core/domain/converters/` — TimestampConverter (기사앱 공유) / CustomerGradeConverter / TransactionTypeConverter / CallStateConverter (옵션) 4종
2. `lib/core/domain/enums/customer_grade.dart` + `transaction_type.dart` + `call_state.dart` (ENUMS.md §C0~C2)

### Week 1~2 — 6 Freezed 모델
3. `CustomerCall` — 복합 필드 매핑 최우선 (§1 ⚠️ 필드 중복 저장 주의)
4. `CustomerInfo` — 2경로 저장 구조 숙지
5. `CustomerPoints` + `PointTransaction`
6. `CustomerGrade` enum (enhanced Dart enum)
7. `BannerAdData` (R2, 뼈대만)

### Week 2 — UI 상태 구조체 4종
8. `CallStatus` + `DriverInfo`
9. `CallUiState` / `PointUiState` / `ProfileUiState` / `AuthUiState`

### Week 3 — 검증
10. `build_runner build --delete-conflicting-outputs` 성공 확인
11. Firestore 실제 샘플 1건씩 `fromFirestore` 파싱 테스트 (`customer_app_flutter/test/models/`)
12. `toFirestore()` 결과가 Kotlin Firestore 포맷과 **key-level 완전 일치** 검증 (jq diff)

---

## 10. ⚠️ ENUMS.md 추가 요청 (3건)

본 문서 작성 중 필요한 **ENUMS.md 손님앱 섹션 신규**:

### C0. CallState (손님앱 6종)
REQUESTED / ASSIGNED / DRIVER_ARRIVING / IN_PROGRESS / COMPLETED / CANCELLED

상세: §7.2. sealed class 패턴 (기사앱 ENUMS.md §1 CallStatus와 동일 패턴).

### C1. CustomerGrade (4종 + behavior)
BRONZE / SILVER / GOLD / VIP

상세: §4. enhanced Dart enum (enum class with fields + methods). Kotlin enum class와 가장 유사한 매핑.

### C2. TransactionType (5종)
EARN / USE / EXPIRE / CANCEL / ADMIN

상세: §5 (TransactionTypeConverter) + `ENUMS.md §3` (enum 정의). amount 부호 관용구 주의.

---

## 11. 기사앱 MODELS.md 와의 공유 자산

기사앱 MODELS.md (`flutter/driver_app/MVP/MODELS.md`) 와 **동일 파일 공유** (복제 금지):

- `lib/core/domain/converters/timestamp_converter.dart` — TimestampConverter
- `lib/core/domain/converters/server_timestamp_converter.dart` — ServerTimestampConverter

손님앱 전용 converter:
- `customer_grade_converter.dart`
- `transaction_type_converter.dart`
- `call_state_converter.dart` (sealed class용)

**공유 방식**: 두 앱이 독립 pubspec이므로 **복제** 불가피. 향후 `packages/common_models/` melos workspace 분리 시 일원화 (Phase 2+).

---

## 12. 참조

- `flutter/driver_app/MVP/MODELS.md` — 기사앱 자매 문서 (동일 원칙, 가이드 공유)
- `flutter/driver_app/MVP/ENUMS.md` — 공통 sealed class 패턴
- `flutter/customer_app/MVP/OVERVIEW.md` — 손님앱 Phase 1/2 범위
- `flutter/customer_app/MVP/FCM.md` — 5종 FCM + 교차 의존 3지점
- `flutter/customer_app/MVP/ATTRIBUTION.md` — CustomerInfo primaryOfficeId / attributionSource 연계
- `flutter/customer_app/MVP/NOTIFIERS.md` — 본 모델을 소비하는 4 Notifier (차기 작성)
- `customer_app/app/src/main/java/com/designated/customer/data/model/` — Kotlin 원본 6개
- `customer_app/app/src/main/java/com/designated/customer/ui/main/CallStatus.kt` — UI 상태
- `customer_app/app/src/main/java/com/designated/customer/ui/auth/PhoneAuthViewModel.kt` — Phone Auth UiState
- `ios/SHARED_LOGIC.md` §1 Firestore 경로 (손님앱 customers/customerInfo 경로 공유)

---

## 13. 작성 완료 요약

- **6 Freezed 모델 + UI 상태 구조체 6종** 매핑 완료
- **Kotlin 원본 라인 번호 100% 인용**
- **CustomerCall 필드 중복 저장** 명시 (customerAddress/departure/departure_set 3중복)
- **CustomerInfo 2경로 저장 구조** (customers/{uid} + customerInfo/{phone}) 명확화
- **CustomerGrade enhanced Dart enum** — 비즈니스 로직 (적립률 계산) 타입 안전
- **TransactionType amount 부호 관용구** (EARN 양수 / USE 음수 / CANCEL 양수)
- **MainViewModel 분해 4 Notifier 매핑**: CallUiState / PointUiState / ProfileUiState / AuthUiState
- **⚠️ ENUMS.md 추가 요청 3건** (§10): CallState / CustomerGrade / TransactionType
- **기사앱 MODELS.md 와의 차이 8종** 명확히 기재 (§손님앱 vs 기사앱 차이점)
- **Phase 6 Week 1~3 작업 순서** (§9)
