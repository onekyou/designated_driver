import '../entities/user_session.dart';

/// 인증 Repository 인터페이스
abstract class AuthRepository {
  /// 로그인 (실패 시 throw)
  Future<UserSession> login({
    required String email,
    required String password,
    bool saveCredentials = false,
  });

  /// 로그아웃
  Future<void> logout();

  /// 자동 로그인 (저장된 credentials 사용)
  Future<UserSession> autoLogin();

  /// 현재 세션 가져오기
  Future<UserSession?> getCurrentSession();

  /// FCM 토큰 업데이트
  Future<void> updateFcmToken(String token);

  /// 비밀번호 재설정
  Future<void> resetPassword(String email);
}
