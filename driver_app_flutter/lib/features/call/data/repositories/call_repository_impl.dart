import 'package:dartz/dartz.dart';
import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/network/network_info.dart';
import '../../../auth/data/datasources/auth_local_datasource.dart';
import '../../domain/entities/call.dart';
import '../../domain/repositories/call_repository.dart';
import '../datasources/call_remote_datasource.dart';

/// CallRepository 구현
class CallRepositoryImpl implements CallRepository {
  final CallRemoteDataSource remoteDataSource;
  final AuthLocalDataSource authLocalDataSource;
  final NetworkInfo networkInfo;

  CallRepositoryImpl({
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
  Future<Either<Failure, Call?>> getAssignedCall(String driverId) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      final sessionInfoResult = await _getSessionInfo();
      return sessionInfoResult.fold(
        (failure) => Left(failure),
        (sessionInfo) async {
          try {
            final callModel = await remoteDataSource.getAssignedCall(
              driverId: driverId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
            );

            return Right(callModel?.toEntity());
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('콜 조회 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, Call>> getCallById(String callId) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      final sessionInfoResult = await _getSessionInfo();
      return sessionInfoResult.fold(
        (failure) => Left(failure),
        (sessionInfo) async {
          try {
            final callModel = await remoteDataSource.getCallById(
              callId: callId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
            );

            return Right(callModel.toEntity());
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('콜 조회 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> acceptCall({
    required String callId,
    required String driverId,
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
            await remoteDataSource.acceptCall(
              callId: callId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
            );

            return const Right(null);
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('콜 수락 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> rejectCall({
    required String callId,
    required String driverId,
    String? reason,
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
            await remoteDataSource.rejectCall(
              callId: callId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
              reason: reason,
            );

            return const Right(null);
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('콜 거절 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> completeCall({
    required String callId,
    required int fare,
    String? pickupLocation,
    String? destination,
    int? pointsUsed,
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
            await remoteDataSource.completeCall(
              callId: callId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
              fare: fare,
              pickupLocation: pickupLocation,
              destination: destination,
              pointsUsed: pointsUsed,
            );

            return const Right(null);
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('콜 완료 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> updateCallStatus({
    required String callId,
    required CallStatus status,
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
            await remoteDataSource.updateCallStatus(
              callId: callId,
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
      return Left(UnknownFailure('콜 상태 업데이트 실패: $e'));
    }
  }

  @override
  Stream<Either<Failure, List<Call>>> watchAssignedCalls(String driverId) async* {
    try {
      final sessionInfoResult = await _getSessionInfo();
      yield* sessionInfoResult.fold(
        (failure) => Stream.value(Left(failure)),
        (sessionInfo) {
          return remoteDataSource
              .watchAssignedCalls(
                driverId: driverId,
                regionId: sessionInfo['regionId']!,
                officeId: sessionInfo['officeId']!,
              )
              .map((callModels) {
                final calls = callModels.map((model) => model.toEntity()).toList();
                return Right<Failure, List<Call>>(calls);
              })
              .handleError((error) {
            if (error is ServerException) {
              return Left<Failure, List<Call>>(ServerFailure(error.message));
            }
            return Left<Failure, List<Call>>(UnknownFailure('콜 감시 실패: $error'));
          });
        },
      );
    } catch (e) {
      yield Left(UnknownFailure('콜 감시 실패: $e'));
    }
  }
}
