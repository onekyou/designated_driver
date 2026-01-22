import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../entities/customer_points.dart';
import '../repositories/customer_repository.dart';

/// 고객 포인트 조회 UseCase
class GetCustomerPointsUseCase implements UseCase<CustomerPoints?, GetCustomerPointsParams> {
  final CustomerRepository repository;

  GetCustomerPointsUseCase(this.repository);

  @override
  Future<Either<Failure, CustomerPoints?>> call(GetCustomerPointsParams params) async {
    return await repository.getCustomerPoints(
      phoneNumber: params.phoneNumber,
      forceRefresh: params.forceRefresh,
    );
  }
}

class GetCustomerPointsParams extends Equatable {
  final String phoneNumber;
  final bool forceRefresh;

  const GetCustomerPointsParams({
    required this.phoneNumber,
    this.forceRefresh = false,
  });

  @override
  List<Object?> get props => [phoneNumber, forceRefresh];
}
