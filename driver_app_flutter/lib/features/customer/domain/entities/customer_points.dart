import 'package:freezed_annotation/freezed_annotation.dart';
import '../../../../core/constants/app_constants.dart';

part 'customer_points.freezed.dart';

/// 고객 등급 Enum
enum CustomerGrade {
  bronze('BRONZE', '브론즈', AppConstants.bronzeEarningRate),
  silver('SILVER', '실버', AppConstants.silverEarningRate),
  gold('GOLD', '골드', AppConstants.goldEarningRate),
  vip('VIP', 'VIP', AppConstants.vipEarningRate);

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

  /// 다음 등급까지 필요한 콜 수
  int? callsToNextGrade() {
    switch (grade) {
      case CustomerGrade.bronze:
        return 10 - totalCalls; // 10번이면 실버
      case CustomerGrade.silver:
        return 30 - totalCalls; // 30번이면 골드
      case CustomerGrade.gold:
        return 100 - totalCalls; // 100번이면 VIP
      case CustomerGrade.vip:
        return null; // 최고 등급
    }
  }
}
