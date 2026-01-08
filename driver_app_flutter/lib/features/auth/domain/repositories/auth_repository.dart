import 'package:dartz/dartz.dart';
import '../../../../core/error/failures.dart';
import '../entities/user_session.dart';

/// 인증 Repository 인터페이스
/// Data Layer에서 구현
abstract class AuthRepository {
  /// 로그인
  ///
  /// [email] 이메일 또는 전화번호
  /// [password] 비밀번호
  /// [saveCredentials] 자동 로그인 여부
  ///
  /// Returns Either<Failure, UserSession>
  Future<Either<Failure, UserSession>> login({
    required String email,
    required String password,
    bool saveCredentials = false,
  });

  /// 로그아웃
  Future<Either<Failure, void>> logout();

  /// 자동 로그인 (저장된 credentials 사용)
  Future<Either<Failure, UserSession>> autoLogin();

  /// 현재 세션 가져오기
  Future<Either<Failure, UserSession?>> getCurrentSession();

  /// FCM 토큰 업데이트
  Future<Either<Failure, void>> updateFcmToken(String token);

  /// 비밀번호 재설정
  Future<Either<Failure, void>> resetPassword(String email);
}
