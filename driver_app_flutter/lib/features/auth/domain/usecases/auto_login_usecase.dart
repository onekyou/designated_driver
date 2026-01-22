import 'package:dartz/dartz.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../entities/user_session.dart';
import '../repositories/auth_repository.dart';

/// 자동 로그인 UseCase
class AutoLoginUseCase implements UseCase<UserSession, NoParams> {
  final AuthRepository repository;

  AutoLoginUseCase(this.repository);

  @override
  Future<Either<Failure, UserSession>> call(NoParams params) async {
    return await repository.autoLogin();
  }
}
