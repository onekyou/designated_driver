import 'package:dartz/dartz.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../entities/user_session.dart';
import '../repositories/auth_repository.dart';

/// 현재 세션 조회 UseCase
class GetCurrentSessionUseCase implements UseCase<UserSession?, NoParams> {
  final AuthRepository repository;

  GetCurrentSessionUseCase(this.repository);

  @override
  Future<Either<Failure, UserSession?>> call(NoParams params) async {
    return await repository.getCurrentSession();
  }
}
