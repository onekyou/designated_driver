import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/di/injection.dart';
import '../../../../core/usecases/usecase.dart';
import '../../domain/entities/user_session.dart';
import '../../domain/usecases/login_usecase.dart';
import '../../domain/usecases/logout_usecase.dart';
import '../../domain/usecases/auto_login_usecase.dart';
import '../../domain/usecases/get_current_session_usecase.dart';
import 'auth_state.dart';

class AuthNotifier extends StateNotifier<AuthState> {
  final LoginUseCase _loginUseCase;
  final LogoutUseCase _logoutUseCase;
  final AutoLoginUseCase _autoLoginUseCase;
  final GetCurrentSessionUseCase _getCurrentSessionUseCase;

  AuthNotifier({
    required LoginUseCase loginUseCase,
    required LogoutUseCase logoutUseCase,
    required AutoLoginUseCase autoLoginUseCase,
    required GetCurrentSessionUseCase getCurrentSessionUseCase,
  })  : _loginUseCase = loginUseCase,
        _logoutUseCase = logoutUseCase,
        _autoLoginUseCase = autoLoginUseCase,
        _getCurrentSessionUseCase = getCurrentSessionUseCase,
        super(const AuthState.initial());

  /// 로그인
  Future<void> login({
    required String email,
    required String password,
    bool saveCredentials = false,
  }) async {
    state = const AuthState.loading();

    final result = await _loginUseCase(
      LoginParams(
        email: email,
        password: password,
        saveCredentials: saveCredentials,
      ),
    );

    result.fold(
      (failure) => state = AuthState.error(failure.message),
      (session) => state = AuthState.authenticated(session),
    );
  }

  /// 로그아웃
  Future<void> logout() async {
    state = const AuthState.loading();

    final result = await _logoutUseCase(NoParams());

    result.fold(
      (failure) => state = AuthState.error(failure.message),
      (_) => state = const AuthState.unauthenticated(),
    );
  }

  /// 자동 로그인
  Future<void> autoLogin() async {
    state = const AuthState.loading();

    final result = await _autoLoginUseCase(NoParams());

    result.fold(
      (failure) => state = const AuthState.unauthenticated(),
      (session) => state = AuthState.authenticated(session),
    );
  }

  /// 현재 세션 확인
  Future<void> checkCurrentSession() async {
    state = const AuthState.loading();

    final result = await _getCurrentSessionUseCase(NoParams());

    result.fold(
      (failure) => state = const AuthState.unauthenticated(),
      (session) {
        if (session != null) {
          state = AuthState.authenticated(session);
        } else {
          state = const AuthState.unauthenticated();
        }
      },
    );
  }
}

/// AuthNotifier Provider
final authNotifierProvider =
    StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(
    loginUseCase: sl(),
    logoutUseCase: sl(),
    autoLoginUseCase: sl(),
    getCurrentSessionUseCase: sl(),
  );
});

/// Current Session Provider (편의성)
final currentSessionProvider = Provider<UserSession?>((ref) {
  final authState = ref.watch(authNotifierProvider);
  return authState.maybeWhen(
    authenticated: (session) => session,
    orElse: () => null,
  );
});

/// Is Authenticated Provider (편의성)
final isAuthenticatedProvider = Provider<bool>((ref) {
  final authState = ref.watch(authNotifierProvider);
  return authState.maybeWhen(
    authenticated: (_) => true,
    orElse: () => false,
  );
});
