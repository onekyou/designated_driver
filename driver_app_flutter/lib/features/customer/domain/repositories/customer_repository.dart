import '../entities/customer_points.dart';

/// 고객 Repository 인터페이스 (Either 제거)
abstract class CustomerRepository {
  /// 고객 포인트 정보 가져오기
  Future<CustomerPoints?> getCustomerPoints({
    required String phoneNumber,
    bool forceRefresh = false,
  });

  /// 포인트 사용
  Future<void> usePoints({
    required String phoneNumber,
    required int amount,
  });

  /// 포인트 적립
  Future<int> earnPoints({
    required String phoneNumber,
    required int fare,
  });

  /// 캐시 초기화
  void clearCache();
}
