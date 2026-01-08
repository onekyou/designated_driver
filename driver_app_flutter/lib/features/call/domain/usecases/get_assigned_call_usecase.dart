import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../entities/call.dart';
import '../repositories/call_repository.dart';

/// 배정된 콜 가져오기 UseCase
class GetAssignedCallUseCase implements UseCase<Call?, GetAssignedCallParams> {
  final CallRepository repository;

  GetAssignedCallUseCase(this.repository);

  @override
  Future<Either<Failure, Call?>> call(GetAssignedCallParams params) async {
    return await repository.getAssignedCall(params.driverId);
  }
}

class GetAssignedCallParams extends Equatable {
  final String driverId;

  const GetAssignedCallParams({required this.driverId});

  @override
  List<Object?> get props => [driverId];
}
