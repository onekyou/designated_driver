import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../domain/repositories/auth_repository.dart';
import 'auth_state.dart';

/// AuthNotifier — Repository 직접 사용 (UseCase 제거, Either 제거)
class AuthNotifier extends StateNotifier<AuthState> {
  final AuthRepository _repository;

  AuthNotifier({required AuthRepository repository})
      : _repository = repository,
        super(const AuthState.initial());

  /// 로그인
  Future<void> login({
    required String email,
    required String password,
    bool saveCredentials = false,
  }) async {
    state = const AuthState.loading();
    try {
      final session = await _repository.login(
        email: email,
        password: password,
        saveCredentials: saveCredentials,
      );
      state = AuthState.authenticated(session);
    } catch (e) {
      state = AuthState.error(e.toString());
    }
  }

  /// 로그아웃
  Future<void> logout() async {
    state = const AuthState.loading();
    try {
      await _repository.logout();
      state = const AuthState.unauthenticated();
    } catch (e) {
      state = AuthState.error(e.toString());
    }
  }

  /// 자동 로그인
  Future<void> autoLogin() async {
    state = const AuthState.loading();
    try {
      final session = await _repository.autoLogin();
      state = AuthState.authenticated(session);
    } catch (e) {
      state = const AuthState.unauthenticated();
    }
  }

  /// 현재 세션 확인
  Future<void> checkCurrentSession() async {
    state = const AuthState.loading();
    try {
      final session = await _repository.getCurrentSession();
      if (session != null) {
        state = AuthState.authenticated(session);
      } else {
        state = const AuthState.unauthenticated();
      }
    } catch (e) {
      state = const AuthState.unauthenticated();
    }
  }
}
