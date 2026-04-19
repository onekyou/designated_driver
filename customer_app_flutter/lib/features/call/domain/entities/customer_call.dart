// ignore_for_file: invalid_annotation_target
// ^ @JsonKey on Freezed constructor parameter — 공식 Freezed 2.x + json_annotation 4.x 권장 패턴
//   (Dart analyzer lint 경고 false positive. Freezed 2.5.7 + json_annotation 4.9.0 기준 정상 동작)

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'customer_call.freezed.dart';
part 'customer_call.g.dart';

/// 손님 콜 엔티티 (Kotlin `CustomerCall.kt:5-94` 포팅).
///
/// Firestore 경로: `provinces/{p}/cities/{c}/offices/{o}/calls/{callId}`
///
/// **⚠️ 필드 중복 저장 주의**:
/// - Dart `currentLocation` → Firestore 3필드 동일 값 저장 (`customerAddress` + `departure` + `departure_set`)
/// - Dart `destinationLocation` → Firestore 2필드 동일 값 (`destination` + `destination_set`)
/// - Dart `driverId` ↔ Firestore `assignedDriverId` 필드명 불일치 (`@JsonKey` 매핑)
/// - Dart `timestamp` (int epoch ms) ↔ Firestore `Timestamp(seconds, nanoseconds)` 양방향 변환
///
/// [fromFirestore] / [toFirestore]에서 위 4가지 특수 케이스 처리.
@freezed
class CustomerCall with _$CustomerCall {
  const CustomerCall._();

  const factory CustomerCall({
    @Default('') String id,
    required String phoneNumber,
    required String officeId,
    required String provinceId,
    required String cityId,
    @Default('') String currentLocation,
    @Default('') String destinationLocation,
    @Default(0) int timestamp,
    @Default('REQUESTED') String status,
    @JsonKey(name: 'assignedDriverId') String? driverId,
    int? estimatedArrivalTime,
    int? fare,
    String? notes,
    @Default('customer_app') String createdFrom,
    String? customerId,
    String? customerName,
    @Default('bronze') String? customerGrade,
    @Default(true) bool isAppCustomer,
    @Default(0) int pointsUsed,
    int? finalFare,
    @Default(0) int discountAmount,
  }) = _CustomerCall;

  factory CustomerCall.fromJson(Map<String, Object?> json) =>
      _$CustomerCallFromJson(json);

  /// Firestore 문서 → CustomerCall (Kotlin `fromMap` 포팅).
  ///
  /// 3중복 필드 fallback 순서 유지 (Kotlin: `customerAddress` ?? `departure` ?? "").
  /// `timestamp`는 Firestore Timestamp 또는 Number 모두 수용.
  /// `provinceId`는 legacy `regionId` fallback.
  factory CustomerCall.fromFirestore(
      DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};

    // timestamp: Firestore Timestamp ↔ int epoch ms 양방향
    final tsRaw = data['timestamp'];
    final tsMillis = tsRaw is Timestamp
        ? tsRaw.seconds * 1000 + tsRaw.nanoseconds ~/ 1000000
        : (tsRaw is num
            ? tsRaw.toInt()
            : DateTime.now().millisecondsSinceEpoch);

    // currentLocation: customerAddress 우선 → departure fallback
    final currentLoc = (data['customerAddress'] as String?)?.isNotEmpty == true
        ? data['customerAddress'] as String
        : (data['departure'] as String?) ?? '';

    // destinationLocation: destination 단일
    final destLoc = (data['destination'] as String?) ?? '';

    // provinceId: legacy regionId fallback
    final province = (data['provinceId'] as String?)?.isNotEmpty == true
        ? data['provinceId'] as String
        : (data['regionId'] as String?) ?? '';

    return CustomerCall(
      id: doc.id,
      phoneNumber: data['phoneNumber'] as String? ?? '',
      officeId: data['officeId'] as String? ?? '',
      provinceId: province,
      cityId: data['cityId'] as String? ?? '',
      currentLocation: currentLoc,
      destinationLocation: destLoc,
      timestamp: tsMillis,
      status: data['status'] as String? ?? 'REQUESTED',
      driverId: data['assignedDriverId'] as String?,
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

  /// Firestore 저장용 Map (Kotlin `toMap` 1:1 포팅).
  ///
  /// 3중복 필드 (`customerAddress` + `departure` + `departure_set`)와
  /// 2중복 필드 (`destination` + `destination_set`)를 정확히 재현.
  /// `timestamp`는 Kotlin 관용 `Timestamp(seconds, nanoseconds)` 생성자 사용.
  Map<String, Object?> toFirestore() {
    final tsSeconds = timestamp ~/ 1000;
    final tsNanos = ((timestamp % 1000) * 1000000).toInt();
    return {
      'id': id,
      'phoneNumber': phoneNumber,
      'officeId': officeId,
      'provinceId': provinceId,
      'cityId': cityId,
      // 3중복 저장 (콜매니저/기사앱 호환)
      'customerAddress': currentLocation,
      'departure': currentLocation,
      'departure_set': currentLocation,
      // 2중복 저장
      'destination': destinationLocation,
      'destination_set': destinationLocation,
      'timestamp': Timestamp(tsSeconds, tsNanos),
      'status': status,
      'assignedDriverId': driverId,
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
