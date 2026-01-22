import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../repositories/call_repository.dart';

/// 콜 수락 UseCase
class AcceptCallUseCase implements UseCase<void, AcceptCallParams> {
  final CallRepository repository;

  AcceptCallUseCase(this.repository);

  @override
  Future<Either<Failure, void>> call(AcceptCallParams params) async {
    return await repository.acceptCall(
      callId: params.callId,
      driverId: params.driverId,
    );
  }
}

class AcceptCallParams extends Equatable {
  final String callId;
  final String driverId;

  const AcceptCallParams({
    required this.callId,
    required this.driverId,
  });

  @override
  List<Object?> get props => [callId, driverId];
}
