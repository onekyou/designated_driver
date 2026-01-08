import 'package:dartz/dartz.dart';
import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/network/network_info.dart';
import '../../../auth/data/datasources/auth_local_datasource.dart';
import '../../domain/entities/driver.dart';
import '../../domain/repositories/driver_repository.dart';
import '../datasources/driver_remote_datasource.dart';

/// DriverRepository 구현
class DriverRepositoryImpl implements DriverRepository {
  final DriverRemoteDataSource remoteDataSource;
  final AuthLocalDataSource authLocalDataSource;
  final NetworkInfo networkInfo;

  DriverRepositoryImpl({
    required this.remoteDataSource,
    required this.authLocalDataSource,
    required this.networkInfo,
  });

  /// 세션 정보 가져오기 헬퍼 (regionId, officeId 추출)
  Future<Either<Failure, Map<String, String>>> _getSessionInfo() async {
    try {
      final session = await authLocalDataSource.getSession();
      if (session == null) {
        return const Left(AuthFailure('세션이 없습니다. 다시 로그인해주세요.'));
      }
      return Right({
        'regionId': session.regionId,
        'officeId': session.officeId,
      });
    } on CacheException catch (e) {
      return Left(CacheFailure(e.message));
    }
  }

  @override
  Future<Either<Failure, Driver>> getDriverInfo(String driverId) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      final sessionInfoResult = await _getSessionInfo();
      return sessionInfoResult.fold(
        (failure) => Left(failure),
        (sessionInfo) async {
          try {
            final driverModel = await remoteDataSource.getDriverInfo(
              driverId: driverId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
            );

            return Right(driverModel.toEntity());
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('기사 정보 조회 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> updateDriverStatus({
    required String driverId,
    required DriverStatus status,
  }) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      final sessionInfoResult = await _getSessionInfo();
      return sessionInfoResult.fold(
        (failure) => Left(failure),
        (sessionInfo) async {
          try {
            await remoteDataSource.updateDriverStatus(
              driverId: driverId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
              status: status,
            );

            return const Right(null);
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('기사 상태 업데이트 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> updateFcmToken({
    required String driverId,
    required String fcmToken,
  }) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      final sessionInfoResult = await _getSessionInfo();
      return sessionInfoResult.fold(
        (failure) => Left(failure),
        (sessionInfo) async {
          try {
            await remoteDataSource.updateFcmToken(
              driverId: driverId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
              fcmToken: fcmToken,
            );

            return const Right(null);
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('FCM 토큰 업데이트 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> updateCurrentCall({
    required String driverId,
    String? callId,
  }) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      final sessionInfoResult = await _getSessionInfo();
      return sessionInfoResult.fold(
        (failure) => Left(failure),
        (sessionInfo) async {
          try {
            await remoteDataSource.updateCurrentCall(
              driverId: driverId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
              callId: callId,
            );

            return const Right(null);
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('현재 콜 ID 업데이트 실패: $e'));
    }
  }

  @override
  Stream<Either<Failure, Driver>> watchDriverInfo(String driverId) async* {
    try {
      final sessionInfoResult = await _getSessionInfo();
      yield* sessionInfoResult.fold(
        (failure) => Stream.value(Left(failure)),
        (sessionInfo) {
          return remoteDataSource
              .watchDriverInfo(
                driverId: driverId,
                regionId: sessionInfo['regionId']!,
                officeId: sessionInfo['officeId']!,
              )
              .map((driverModel) {
                return Right<Failure, Driver>(driverModel.toEntity());
              })
              .handleError((error) {
            if (error is ServerException) {
              return Left<Failure, Driver>(ServerFailure(error.message));
            }
            return Left<Failure, Driver>(UnknownFailure('기사 정보 감시 실패: $error'));
          });
        },
      );
    } catch (e) {
      yield Left(UnknownFailure('기사 정보 감시 실패: $e'));
    }
  }
}
