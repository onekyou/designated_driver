import 'package:dartz/dartz.dart';
import '../../../../core/error/failures.dart';
import '../entities/driver.dart';

/// 기사 Repository 인터페이스
abstract class DriverRepository {
  /// 기사 정보 가져오기
  ///
  /// [driverId] 기사 ID
  Future<Either<Failure, Driver>> getDriverInfo(String driverId);

  /// 기사 상태 업데이트
  ///
  /// [driverId] 기사 ID
  /// [status] 새로운 상태
  Future<Either<Failure, void>> updateDriverStatus({
    required String driverId,
    required DriverStatus status,
  });

  /// FCM 토큰 업데이트
  ///
  /// [driverId] 기사 ID
  /// [fcmToken] FCM 토큰
  Future<Either<Failure, void>> updateFcmToken({
    required String driverId,
    required String fcmToken,
  });

  /// 현재 콜 ID 업데이트
  ///
  /// [driverId] 기사 ID
  /// [callId] 콜 ID (null이면 콜 없음)
  Future<Either<Failure, void>> updateCurrentCall({
    required String driverId,
    String? callId,
  });

  /// 기사 정보 실시간 감시
  ///
  /// [driverId] 기사 ID
  Stream<Either<Failure, Driver>> watchDriverInfo(String driverId);
}
