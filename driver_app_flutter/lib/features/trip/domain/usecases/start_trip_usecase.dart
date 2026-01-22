import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../entities/trip.dart';
import '../repositories/trip_repository.dart';

/// 운행 시작 UseCase
class StartTripUseCase implements UseCase<Trip, StartTripParams> {
  final TripRepository repository;

  StartTripUseCase(this.repository);

  @override
  Future<Either<Failure, Trip>> call(StartTripParams params) async {
    return await repository.startTrip(
      callId: params.callId,
      driverId: params.driverId,
      pickupLocation: params.pickupLocation,
      destination: params.destination,
      fare: params.fare,
    );
  }
}

class StartTripParams extends Equatable {
  final String callId;
  final String driverId;
  final String pickupLocation;
  final String destination;
  final int fare;

  const StartTripParams({
    required this.callId,
    required this.driverId,
    required this.pickupLocation,
    required this.destination,
    required this.fare,
  });

  @override
  List<Object?> get props => [callId, driverId, pickupLocation, destination, fare];
}
