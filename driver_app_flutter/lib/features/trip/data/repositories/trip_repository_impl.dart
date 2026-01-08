import 'package:dartz/dartz.dart';
import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/network/network_info.dart';
import '../../../auth/data/datasources/auth_local_datasource.dart';
import '../../domain/entities/trip.dart';
import '../../domain/repositories/trip_repository.dart';
import '../datasources/trip_remote_datasource.dart';

/// TripRepository 구현
class TripRepositoryImpl implements TripRepository {
  final TripRemoteDataSource remoteDataSource;
  final AuthLocalDataSource authLocalDataSource;
  final NetworkInfo networkInfo;

  TripRepositoryImpl({
    required this.remoteDataSource,
    required this.authLocalDataSource,
    required this.networkInfo,
  });

  /// 세션 정보 가져오기 헬퍼 (regionId, officeId, driverName, phoneNumber 추출)
  Future<Either<Failure, Map<String, String>>> _getSessionInfo() async {
    try {
      final session = await authLocalDataSource.getSession();
      if (session == null) {
        return const Left(AuthFailure('세션이 없습니다. 다시 로그인해주세요.'));
      }
      return Right({
        'regionId': session.regionId,
        'officeId': session.officeId,
        'driverName': session.driverName,
        'driverId': session.driverId,
      });
    } on CacheException catch (e) {
      return Left(CacheFailure(e.message));
    }
  }

  @override
  Future<Either<Failure, Trip>> startTrip({
    required String callId,
    required String driverId,
    required String pickupLocation,
    required String destination,
    required int fare,
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
            // phoneNumber와 customerName은 Call에서 가져와야 하지만,
            // 여기서는 간단히 처리 (실제로는 Call 정보를 먼저 조회해야 함)
            final tripModel = await remoteDataSource.startTrip(
              callId: callId,
              driverId: driverId,
              driverName: sessionInfo['driverName']!,
              phoneNumber: '', // TODO: Call에서 가져오기
              customerName: null,
              pickupLocation: pickupLocation,
              destination: destination,
              fare: fare,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
            );

            return Right(tripModel.toEntity());
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('운행 시작 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> completeTrip({
    required String tripId,
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
            await remoteDataSource.completeTrip(
              tripId: tripId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
              pointsUsed: pointsUsed,
              pointsEarned: null, // TODO: 포인트 계산 로직
            );

            return const Right(null);
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('운행 완료 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> cancelTrip({
    required String tripId,
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
            await remoteDataSource.cancelTrip(
              tripId: tripId,
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
      return Left(UnknownFailure('운행 취소 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, Trip?>> getCurrentTrip(String driverId) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      final sessionInfoResult = await _getSessionInfo();
      return sessionInfoResult.fold(
        (failure) => Left(failure),
        (sessionInfo) async {
          try {
            final tripModel = await remoteDataSource.getCurrentTrip(
              driverId: driverId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
            );

            return Right(tripModel?.toEntity());
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('현재 운행 조회 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, List<Trip>>> getTripHistory({
    required String driverId,
    int limit = 20,
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
            final tripModels = await remoteDataSource.getTripHistory(
              driverId: driverId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
              limit: limit,
            );

            final trips = tripModels.map((model) => model.toEntity()).toList();
            return Right(trips);
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('운행 내역 조회 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, Trip>> getTripById(String tripId) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      final sessionInfoResult = await _getSessionInfo();
      return sessionInfoResult.fold(
        (failure) => Left(failure),
        (sessionInfo) async {
          try {
            final tripModel = await remoteDataSource.getTripById(
              tripId: tripId,
              regionId: sessionInfo['regionId']!,
              officeId: sessionInfo['officeId']!,
            );

            return Right(tripModel.toEntity());
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('운행 조회 실패: $e'));
    }
  }
}
