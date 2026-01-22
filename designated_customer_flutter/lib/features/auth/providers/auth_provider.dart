import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_auth/firebase_auth.dart';
import '../repositories/auth_repository.dart';
import '../models/user_model.dart';

/// AuthRepository Provider
final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository();
});

/// Firebase Auth State 변화 스트림
final authStateChangesProvider = StreamProvider<User?>((ref) {
  final authRepository = ref.watch(authRepositoryProvider);
  return authRepository.authStateChanges;
});

/// 현재 사용자 Provider
final currentUserProvider = Provider<User?>((ref) {
  final authRepository = ref.watch(authRepositoryProvider);
  return authRepository.currentUser;
});

/// 사용자 데이터 Provider
final userDataProvider = FutureProvider.family<UserModel?, String>((ref, uid) async {
  final authRepository = ref.watch(authRepositoryProvider);
  return await authRepository.getUserData(uid);
});

/// 인증 상태 Provider
class AuthNotifier extends StateNotifier<AsyncValue<UserModel?>> {
  final AuthRepository _authRepository;

  AuthNotifier(this._authRepository) : super(const AsyncValue.loading()) {
    _initialize();
  }

  /// 초기화
  Future<void> _initialize() async {
    state = const AsyncValue.loading();
    try {
      // 현재 Firebase Auth 사용자 확인
      final currentUser = _authRepository.currentUser;

      if (currentUser != null) {
        // 인증된 경우 - 저장된 전화번호 확인
        final savedPhoneNumber = await _authRepository.getSavedPhoneNumber();

        // UserModel 생성
        final userData = UserModel(
          uid: currentUser.uid,
          isAnonymous: currentUser.isAnonymous,
          phoneNumber: savedPhoneNumber,
          email: currentUser.email,
          createdAt: currentUser.metadata.creationTime ?? DateTime.now(),
          updatedAt: DateTime.now(),
        );

        state = AsyncValue.data(userData);
      } else {
        // 인증되지 않은 경우
        state = const AsyncValue.data(null);
      }
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  /// 익명 로그인 (전화번호와 함께)
  Future<void> signInAnonymously({String? phoneNumber}) async {
    state = const AsyncValue.loading();
    try {
      final userData = await _authRepository.signInAnonymously(phoneNumber: phoneNumber);
      state = AsyncValue.data(userData);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  /// 로그아웃
  Future<void> signOut() async {
    state = const AsyncValue.loading();
    try {
      await _authRepository.signOut();
      state = const AsyncValue.data(null);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  /// 사용자 정보 새로고침
  Future<void> refresh() async {
    final currentUser = _authRepository.currentUser;
    if (currentUser == null) {
      state = const AsyncValue.data(null);
      return;
    }

    try {
      final userData = await _authRepository.getUserData(currentUser.uid);
      state = AsyncValue.data(userData);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  /// 전화번호 다시 로드 (ProfileSetupScreen에서 프로필 저장 후 호출)
  Future<void> reloadPhoneNumber() async {
    final currentUser = _authRepository.currentUser;
    if (currentUser == null) {
      state = const AsyncValue.data(null);
      return;
    }

    try {
      // SharedPreferences에서 전화번호 다시 로드
      final savedPhoneNumber = await _authRepository.getSavedPhoneNumber();

      // 현재 state의 UserModel을 업데이트
      final currentData = state.value;
      if (currentData != null) {
        final updatedData = currentData.copyWith(
          phoneNumber: savedPhoneNumber,
        );
        state = AsyncValue.data(updatedData);
      }
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
}

/// AuthNotifier Provider
final authNotifierProvider = StateNotifierProvider<AuthNotifier, AsyncValue<UserModel?>>((ref) {
  final authRepository = ref.watch(authRepositoryProvider);
  return AuthNotifier(authRepository);
});
