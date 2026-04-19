import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

import '../../../../core/domain/converters/customer_grade_converter.dart';
import '../../../../core/domain/converters/timestamp_converter.dart';
import '../../../../core/domain/enums/customer_grade.dart';

part 'customer_points.freezed.dart';
part 'customer_points.g.dart';

/// 손님 포인트 정보 (Kotlin `CustomerPoints.kt:8-84` 포팅).
///
/// Firestore 경로: `provinces/{p}/cities/{c}/offices/{o}/customerPoints/{phoneNumber}`
/// 문서 ID는 `phoneNumber`. 기사앱과 read-only 공유 경로.
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
    @Default(CustomerGrade.bronze)
    CustomerGrade grade,
    @Default(0) int totalCalls,
    @TimestampConverter() DateTime? lastUpdated,
    @TimestampConverter() DateTime? createdAt,
  }) = _CustomerPoints;

  factory CustomerPoints.fromJson(Map<String, Object?> json) =>
      _$CustomerPointsFromJson(json);

  /// Firestore 문서 → CustomerPoints.
  factory CustomerPoints.fromFirestore(
      DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};
    return CustomerPoints.fromJson(data);
  }

  /// 등급 업데이트 필요 여부 (Kotlin `shouldUpdateGrade` 포팅).
  bool get shouldUpdateGrade =>
      grade != CustomerGrade.fromCallCount(totalCalls);

  /// 갱신된 등급 (Kotlin `getUpdatedGrade` 포팅).
  CustomerGrade get updatedGrade => CustomerGrade.fromCallCount(totalCalls);

  /// 포인트 사용 가능 여부 (Kotlin `canUsePoints` 포팅).
  bool canUsePoints(int amount) => currentPoints >= amount;

  /// 포인트 적립 계산 (Kotlin `calculateEarnPoints` 포팅).
  int calculateEarnPoints(int fare) => grade.calculatePoints(fare);

  /// 다음 등급까지 필요 콜 수 (Kotlin `getCallsToNextGrade` 포팅).
  int? get callsToNextGrade => grade.callsToNext(totalCalls);
}
