import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../entities/driver.dart';
import '../repositories/driver_repository.dart';

/// 기사 상태 업데이트 UseCase
class UpdateDriverStatusUseCase implements UseCase<void, UpdateDriverStatusParams> {
  final DriverRepository repository;

  UpdateDriverStatusUseCase(this.repository);

  @override
  Future<Either<Failure, void>> call(UpdateDriverStatusParams params) async {
    return await repository.updateDriverStatus(
      driverId: params.driverId,
      status: params.status,
    );
  }
}

class UpdateDriverStatusParams extends Equatable {
  final String driverId;
  final DriverStatus status;

  const UpdateDriverStatusParams({
    required this.driverId,
    required this.status,
  });

  @override
  List<Object?> get props => [driverId, status];
}
