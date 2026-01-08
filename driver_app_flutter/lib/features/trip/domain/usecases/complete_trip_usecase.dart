import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../repositories/trip_repository.dart';

/// 운행 완료 UseCase
class CompleteTripUseCase implements UseCase<void, CompleteTripParams> {
  final TripRepository repository;

  CompleteTripUseCase(this.repository);

  @override
  Future<Either<Failure, void>> call(CompleteTripParams params) async {
    return await repository.completeTrip(
      tripId: params.tripId,
      pointsUsed: params.pointsUsed,
    );
  }
}

class CompleteTripParams extends Equatable {
  final String tripId;
  final int? pointsUsed;

  const CompleteTripParams({
    required this.tripId,
    this.pointsUsed,
  });

  @override
  List<Object?> get props => [tripId, pointsUsed];
}
