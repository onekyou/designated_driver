import 'package:dartz/dartz.dart';
import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/network/network_info.dart';
import '../../domain/entities/user_session.dart';
import '../../domain/repositories/auth_repository.dart';
import '../datasources/auth_local_datasource.dart';
import '../datasources/auth_remote_datasource.dart';
import '../models/user_session_model.dart';

/// AuthRepository 구현
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
  Future<Either<Failure, UserSession>> login({
    required String email,
    required String password,
    bool saveCredentials = false,
  }) async {
    try {
      // 1. 온라인 로그인 시도
      if (await networkInfo.isConnected) {
        try {
          final sessionModel = await remoteDataSource.login(
            email: email,
            password: password,
          );

          // 2. 로컬에 세션 저장
          await localDataSource.saveSession(sessionModel);

          // 3. 자동 로그인 정보 저장 (선택)
          if (saveCredentials) {
            await localDataSource.saveCredentials(
              email: email,
              password: password,
            );
          }

          return Right(sessionModel.toEntity());
        } on AuthException catch (e) {
          // 인증 실패 - 오프라인 로그인 시도
          return _attemptOfflineLogin(email);
        } on ServerException catch (e) {
          return Left(ServerFailure(e.message));
        }
      } else {
        // 오프라인 - 캐시된 세션으로 로그인
        return _attemptOfflineLogin(email);
      }
    } on NetworkException catch (e) {
      return Left(NetworkFailure(e.message));
    } catch (e) {
      return Left(UnknownFailure('로그인 실패: $e'));
    }
  }

  /// 오프라인 로그인 시도
  Future<Either<Failure, UserSession>> _attemptOfflineLogin(String email) async {
    try {
      final cachedSession = await localDataSource.getSession();

      if (cachedSession != null && cachedSession.email == email) {
        // 캐시된 세션의 이메일이 일치하면 허용
        if (!cachedSession.isExpired()) {
          // 세션이 유효함
          final updatedSession = cachedSession.copyWith(isOnline: false);
          await localDataSource.saveSession(updatedSession);
          return Right(updatedSession.toEntity());
        } else {
          // 세션이 만료됨
          return const Left(AuthFailure('세션이 만료되었습니다. 온라인 상태에서 다시 로그인해주세요.'));
        }
      } else {
        return const Left(AuthFailure('오프라인 상태에서는 이전에 로그인한 계정만 사용할 수 있습니다.'));
      }
    } on CacheException catch (e) {
      return Left(CacheFailure(e.message));
    }
  }

  @override
  Future<Either<Failure, void>> logout() async {
    try {
      // 1. Firebase 로그아웃
      if (await networkInfo.isConnected) {
        await remoteDataSource.logout();
      }

      // 2. 로컬 세션 삭제
      await localDataSource.clearSession();

      // 3. 자동 로그인 정보 삭제
      await localDataSource.clearCredentials();

      return const Right(null);
    } on AuthException catch (e) {
      return Left(AuthFailure(e.message));
    } on CacheException catch (e) {
      return Left(CacheFailure(e.message));
    } catch (e) {
      return Left(UnknownFailure('로그아웃 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, UserSession>> autoLogin() async {
    try {
      // 1. 저장된 자동 로그인 정보 확인
      final credentials = await localDataSource.getCredentials();

      if (credentials == null) {
        return const Left(AuthFailure('저장된 로그인 정보가 없습니다.'));
      }

      // 2. 저장된 정보로 로그인
      return await login(
        email: credentials['email']!,
        password: credentials['password']!,
        saveCredentials: true,
      );
    } on CacheException catch (e) {
      return Left(CacheFailure(e.message));
    } catch (e) {
      return Left(UnknownFailure('자동 로그인 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, UserSession?>> getCurrentSession() async {
    try {
      final session = await localDataSource.getSession();
      return Right(session?.toEntity());
    } on CacheException catch (e) {
      return Left(CacheFailure(e.message));
    } catch (e) {
      return Left(UnknownFailure('세션 조회 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> updateFcmToken(String token) async {
    try {
      // 현재 세션 가져오기
      final sessionResult = await getCurrentSession();

      return sessionResult.fold(
        (failure) => Left(failure),
        (session) async {
          if (session == null) {
            return const Left(AuthFailure('세션이 없습니다.'));
          }

          if (await networkInfo.isConnected) {
            await remoteDataSource.updateFcmToken(
              driverId: session.driverId,
              regionId: session.regionId,
              officeId: session.officeId,
              token: token,
            );
          }

          // 로컬 세션 업데이트
          final sessionModel = UserSessionModel.fromEntity(session);
          final updatedSession = sessionModel.copyWith(fcmToken: token);
          await localDataSource.saveSession(updatedSession);

          return const Right(null);
        },
      );
    } on ServerException catch (e) {
      return Left(ServerFailure(e.message));
    } catch (e) {
      return Left(UnknownFailure('FCM 토큰 업데이트 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> resetPassword(String email) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      await remoteDataSource.resetPassword(email);
      return const Right(null);
    } on AuthException catch (e) {
      return Left(AuthFailure(e.message));
    } catch (e) {
      return Left(UnknownFailure('비밀번호 재설정 실패: $e'));
    }
  }
}
