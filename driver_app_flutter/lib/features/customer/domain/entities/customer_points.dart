import 'package:freezed_annotation/freezed_annotation.dart';

part 'customer_points.freezed.dart';

/// 고객 등급 Enum
enum CustomerGrade {
  bronze('BRONZE', '브론즈', 0.03),
  silver('SILVER', '실버', 0.05),
  gold('GOLD', '골드', 0.07),
  vip('VIP', 'VIP', 0.09);

  final String value;
  final String displayName;
  final double earningRate;

  const CustomerGrade(this.value, this.displayName, this.earningRate);

  static CustomerGrade fromString(String? grade) {
    if (grade == null) return bronze;
    return CustomerGrade.values.firstWhere(
      (e) => e.value == grade.toUpperCase(),
      orElse: () => bronze,
    );
  }
}

/// 고객 포인트 Entity (순수 Dart, Firebase 독립적)
@freezed
class CustomerPoints with _$CustomerPoints {
  const factory CustomerPoints({
    required String customerId,
    required String phoneNumber,
    required int currentPoints,
    required int totalEarned,
    required int totalUsed,
    required CustomerGrade grade,
    required int totalCalls,
    DateTime? lastUpdated,
  }) = _CustomerPoints;

  const CustomerPoints._();

  /// 포인트 사용 가능 여부
  bool canUsePoints(int amount) {
    return currentPoints >= amount;
  }

  /// 포인트 적립 계산
  int calculateEarnedPoints(int fare) {
    return (fare * grade.earningRate).round();
  }
}
