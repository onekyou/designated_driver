import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../repositories/call_repository.dart';

/// 콜 완료 UseCase
class CompleteCallUseCase implements UseCase<void, CompleteCallParams> {
  final CallRepository repository;

  CompleteCallUseCase(this.repository);

  @override
  Future<Either<Failure, void>> call(CompleteCallParams params) async {
    return await repository.completeCall(
      callId: params.callId,
      fare: params.fare,
      pickupLocation: params.pickupLocation,
      destination: params.destination,
      pointsUsed: params.pointsUsed,
    );
  }
}

class CompleteCallParams extends Equatable {
  final String callId;
  final int fare;
  final String? pickupLocation;
  final String? destination;
  final int? pointsUsed;

  const CompleteCallParams({
    required this.callId,
    required this.fare,
    this.pickupLocation,
    this.destination,
    this.pointsUsed,
  });

  @override
  List<Object?> get props => [callId, fare, pickupLocation, destination, pointsUsed];
}
