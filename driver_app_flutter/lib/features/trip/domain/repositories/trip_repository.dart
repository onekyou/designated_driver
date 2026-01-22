import 'package:dartz/dartz.dart';
import '../../../../core/error/failures.dart';
import '../entities/trip.dart';

/// 운행 Repository 인터페이스
abstract class TripRepository {
  /// 운행 시작
  ///
  /// [callId] 콜 ID
  /// [driverId] 기사 ID
  /// [pickupLocation] 출발지
  /// [destination] 도착지
  /// [fare] 요금
  Future<Either<Failure, Trip>> startTrip({
    required String callId,
    required String driverId,
    required String pickupLocation,
    required String destination,
    required int fare,
  });

  /// 운행 완료
  ///
  /// [tripId] 운행 ID
  /// [pointsUsed] 사용한 포인트
  Future<Either<Failure, void>> completeTrip({
    required String tripId,
    int? pointsUsed,
  });

  /// 운행 취소
  ///
  /// [tripId] 운행 ID
  /// [reason] 취소 사유
  Future<Either<Failure, void>> cancelTrip({
    required String tripId,
    String? reason,
  });

  /// 현재 운행 중인 Trip 가져오기
  ///
  /// [driverId] 기사 ID
  Future<Either<Failure, Trip?>> getCurrentTrip(String driverId);

  /// 운행 내역 가져오기
  ///
  /// [driverId] 기사 ID
  /// [limit] 가져올 개수
  Future<Either<Failure, List<Trip>>> getTripHistory({
    required String driverId,
    int limit = 20,
  });

  /// 운행 상세 정보
  ///
  /// [tripId] 운행 ID
  Future<Either<Failure, Trip>> getTripById(String tripId);
}
