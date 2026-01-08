import 'package:dartz/dartz.dart';
import '../../../../core/error/failures.dart';
import '../entities/customer_points.dart';

/// 고객 Repository 인터페이스
abstract class CustomerRepository {
  /// 고객 포인트 정보 가져오기
  ///
  /// [phoneNumber] 고객 전화번호
  /// [forceRefresh] 캐시 무시하고 새로 가져오기
  Future<Either<Failure, CustomerPoints?>> getCustomerPoints({
    required String phoneNumber,
    bool forceRefresh = false,
  });

  /// 포인트 사용
  ///
  /// [phoneNumber] 고객 전화번호
  /// [amount] 사용할 포인트
  Future<Either<Failure, void>> usePoints({
    required String phoneNumber,
    required int amount,
  });

  /// 포인트 적립
  ///
  /// [phoneNumber] 고객 전화번호
  /// [fare] 운행 요금
  Future<Either<Failure, int>> earnPoints({
    required String phoneNumber,
    required int fare,
  });

  /// 신규 고객 포인트 생성
  ///
  /// [phoneNumber] 고객 전화번호
  Future<Either<Failure, CustomerPoints>> createCustomerPoints({
    required String phoneNumber,
  });

  /// 캐시 초기화
  void clearCache();

  /// 특정 고객 캐시 무효화
  void invalidateCache(String phoneNumber);
}
