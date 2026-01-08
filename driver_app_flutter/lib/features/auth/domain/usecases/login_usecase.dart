import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/usecases/usecase.dart';
import '../entities/user_session.dart';
import '../repositories/auth_repository.dart';

/// 로그인 UseCase
class LoginUseCase implements UseCase<UserSession, LoginParams> {
  final AuthRepository repository;

  LoginUseCase(this.repository);

  @override
  Future<Either<Failure, UserSession>> call(LoginParams params) async {
    return await repository.login(
      email: params.email,
      password: params.password,
      saveCredentials: params.saveCredentials,
    );
  }
}

class LoginParams extends Equatable {
  final String email;
  final String password;
  final bool saveCredentials;

  const LoginParams({
    required this.email,
    required this.password,
    this.saveCredentials = false,
  });

  @override
  List<Object?> get props => [email, password, saveCredentials];
}
