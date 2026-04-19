import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

import '../../../../core/domain/converters/timestamp_converter.dart';

part 'customer_info.freezed.dart';
part 'customer_info.g.dart';

/// 손님 프로필 정보 (Kotlin `CustomerInfo.kt:8-85` 포팅).
///
/// **2경로 저장 구조**:
/// 1. `provinces/{p}/cities/{c}/offices/{o}/customers/{uid}` — Anonymous Auth uid 기반 프로필 전체
/// 2. `provinces/{p}/cities/{c}/offices/{o}/customerInfo/{phoneNumber}` — 사무실 연락처 + FCM 토큰 (phoneNumber 기반)
///
/// 모델은 단일. 경로 선택은 Notifier(ProfileNotifier / AuthNotifier)에서 처리.
///
/// **legacy fallback**: 기존 사용자는 `address` 필드에 집 주소를 저장했을 수 있음.
/// [fromFirestore]가 `homeAddress` 우선 + `address` fallback 처리 (Kotlin `fromMap` 일치).
@freezed
class CustomerInfo with _$CustomerInfo {
  const CustomerInfo._();

  const factory CustomerInfo({
    @Default('') String id,
    @Default('') String phoneNumber,
    @Default('') String name,
    @Default('bronze') String grade,
    @Default(0) int points,
    @Default(0) int totalRides,
    @Default(0) int totalSpent,
    @Default('') String linkedOfficeId,
    @Default('') String primaryOfficeId,
    int? attributionScore,
    String? attributionSource,
    @TimestampConverter() DateTime? registeredAt,
    @TimestampConverter() DateTime? lastRideAt,
    @Default('') String officePhone,
    @Default('') String bankName,
    @Default('') String accountNumber,
    @Default('') String accountHolder,
    @Default('') String homeAddress,
  }) = _CustomerInfo;

  factory CustomerInfo.fromJson(Map<String, Object?> json) =>
      _$CustomerInfoFromJson(json);

  /// Firestore 문서 → CustomerInfo (Kotlin `fromMap` 포팅).
  ///
  /// `homeAddress` legacy fallback: 비어 있으면 `address` 필드 참조 (기존 사용자 호환).
  factory CustomerInfo.fromFirestore(
      DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};

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
