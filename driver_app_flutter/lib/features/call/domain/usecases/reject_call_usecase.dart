import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../repositories/call_repository.dart';

/// 콜 거절 UseCase
class RejectCallUseCase implements UseCase<void, RejectCallParams> {
  final CallRepository repository;

  RejectCallUseCase(this.repository);

  @override
  Future<Either<Failure, void>> call(RejectCallParams params) async {
    return await repository.rejectCall(
      callId: params.callId,
      driverId: params.driverId,
      reason: params.reason,
    );
  }
}

class RejectCallParams extends Equatable {
  final String callId;
  final String driverId;
  final String? reason;

  const RejectCallParams({
    required this.callId,
    required this.driverId,
    this.reason,
  });

  @override
  List<Object?> get props => [callId, driverId, reason];
}
