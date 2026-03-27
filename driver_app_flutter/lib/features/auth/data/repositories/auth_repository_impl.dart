import '../../../../core/error/exceptions.dart';
import '../../../../core/network/network_info.dart';
import '../../domain/entities/user_session.dart';
import '../../domain/repositories/auth_repository.dart';
import '../datasources/auth_local_datasource.dart';
import '../datasources/auth_remote_datasource.dart';
import '../models/user_session_model.dart';

/// AuthRepository 구현 (Either 제거, try-catch 방식)
class AuthRepositoryImpl implements AuthRepository {
  final AuthRemoteDataSource remoteDataSource;
  final AuthLocalDataSource localDataSource;
  final NetworkInfo networkInfo;

  AuthRepositoryImpl({
    required this.remoteDataSource,
    required this.localDataSource,
    required this.networkInfo,
  });

  @override
  Future<UserSession> login({
    required String email,
    required String password,
    bool saveCredentials = false,
  }) async {
    if (await networkInfo.isConnected) {
      try {
        final sessionModel = await remoteDataSource.login(
          email: email,
          password: password,
        );

        await localDataSource.saveSession(sessionModel);

        if (saveCredentials) {
          await localDataSource.saveCredentials(
            email: email,
            password: password,
          );
        }

        return sessionModel.toEntity();
      } on AuthException {
        // 인증 실패 시 오프라인 시도
        return _attemptOfflineLogin(email);
      }
    } else {
      return _attemptOfflineLogin(email);
    }
  }

  Future<UserSession> _attemptOfflineLogin(String email) async {
    final cachedSession = await localDataSource.getSession();

    if (cachedSession != null && cachedSession.email == email) {
      if (!cachedSession.isExpired()) {
        final updatedSession = cachedSession.copyWith(isOnline: false);
        await localDataSource.saveSession(updatedSession);
        return updatedSession.toEntity();
      } else {
        throw AuthException('세션이 만료되었습니다. 온라인 상태에서 다시 로그인해주세요.');
      }
    } else {
      throw AuthException('오프라인 상태에서는 이전에 로그인한 계정만 사용할 수 있습니다.');
    }
  }

  @override
  Future<void> logout() async {
    if (await networkInfo.isConnected) {
      await remoteDataSource.logout();
    }
    await localDataSource.clearSession();
    await localDataSource.clearCredentials();
  }

  @override
  Future<UserSession> autoLogin() async {
    final credentials = await localDataSource.getCredentials();

    if (credentials == null) {
      throw AuthException('저장된 로그인 정보가 없습니다.');
    }

    return await login(
      email: credentials['email']!,
      password: credentials['password']!,
      saveCredentials: true,
    );
  }

  @override
  Future<UserSession?> getCurrentSession() async {
    final session = await localDataSource.getSession();
    return session?.toEntity();
  }

  @override
  Future<void> updateFcmToken(String token) async {
    final session = await getCurrentSession();
    if (session == null) {
      throw AuthException('세션이 없습니다.');
    }

    if (await networkInfo.isConnected) {
      await remoteDataSource.updateFcmToken(
        driverId: session.driverId,
        provinceId: session.provinceId,
        cityId: session.cityId,
        officeId: session.officeId,
        token: token,
      );
    }

    final sessionModel = UserSessionModel.fromEntity(session);
    final updatedSession = sessionModel.copyWith(fcmToken: token);
    await localDataSource.saveSession(updatedSession);
  }

  @override
  Future<void> resetPassword(String email) async {
    if (!await networkInfo.isConnected) {
      throw NetworkException('네트워크 연결을 확인해주세요.');
    }
    await remoteDataSource.resetPassword(email);
  }
}
