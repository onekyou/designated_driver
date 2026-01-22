import 'package:dartz/dartz.dart';
import '../../../../core/error/failures.dart';
import '../entities/call.dart';

/// 콜 Repository 인터페이스
abstract class CallRepository {
  /// 내게 배정된 콜 가져오기
  ///
  /// [driverId] 기사 ID
  /// Returns Either<Failure, Call?>
  Future<Either<Failure, Call?>> getAssignedCall(String driverId);

  /// 콜 상세 정보 가져오기
  ///
  /// [callId] 콜 ID
  Future<Either<Failure, Call>> getCallById(String callId);

  /// 콜 수락
  ///
  /// [callId] 콜 ID
  /// [driverId] 기사 ID
  Future<Either<Failure, void>> acceptCall({
    required String callId,
    required String driverId,
  });

  /// 콜 거절
  ///
  /// [callId] 콜 ID
  /// [driverId] 기사 ID
  /// [reason] 거절 사유 (선택)
  Future<Either<Failure, void>> rejectCall({
    required String callId,
    required String driverId,
    String? reason,
  });

  /// 콜 완료
  ///
  /// [callId] 콜 ID
  /// [fare] 요금
  /// [pickupLocation] 출발지
  /// [destination] 도착지
  /// [pointsUsed] 사용한 포인트
  Future<Either<Failure, void>> completeCall({
    required String callId,
    required int fare,
    String? pickupLocation,
    String? destination,
    int? pointsUsed,
  });

  /// 콜 상태 업데이트
  ///
  /// [callId] 콜 ID
  /// [status] 새로운 상태
  Future<Either<Failure, void>> updateCallStatus({
    required String callId,
    required CallStatus status,
  });

  /// 콜 리스트 (실시간 스트림)
  ///
  /// [driverId] 기사 ID
  /// Returns Stream<Either<Failure, List<Call>>>
  Stream<Either<Failure, List<Call>>> watchAssignedCalls(String driverId);
}
