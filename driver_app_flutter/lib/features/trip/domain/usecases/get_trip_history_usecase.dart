import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../entities/trip.dart';
import '../repositories/trip_repository.dart';

/// 운행 내역 가져오기 UseCase
class GetTripHistoryUseCase implements UseCase<List<Trip>, GetTripHistoryParams> {
  final TripRepository repository;

  GetTripHistoryUseCase(this.repository);

  @override
  Future<Either<Failure, List<Trip>>> call(GetTripHistoryParams params) async {
    return await repository.getTripHistory(
      driverId: params.driverId,
      limit: params.limit,
    );
  }
}

class GetTripHistoryParams extends Equatable {
  final String driverId;
  final int limit;

  const GetTripHistoryParams({
    required this.driverId,
    this.limit = 20,
  });

  @override
  List<Object?> get props => [driverId, limit];
}
