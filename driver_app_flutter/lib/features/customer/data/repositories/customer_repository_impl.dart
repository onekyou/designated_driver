import 'package:dartz/dartz.dart';
import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/network/network_info.dart';
import '../../domain/entities/customer_points.dart';
import '../../domain/repositories/customer_repository.dart';
import '../datasources/customer_local_datasource.dart';
import '../datasources/customer_remote_datasource.dart';

/// CustomerRepository 구현
class CustomerRepositoryImpl implements CustomerRepository {
  final CustomerRemoteDataSource remoteDataSource;
  final CustomerLocalDataSource localDataSource;
  final NetworkInfo networkInfo;

  CustomerRepositoryImpl({
    required this.remoteDataSource,
    required this.localDataSource,
    required this.networkInfo,
  });

  @override
  Future<Either<Failure, CustomerPoints?>> getCustomerPoints({
    required String phoneNumber,
    bool forceRefresh = false,
  }) async {
    try {
      // 1. 캐시 확인 (forceRefresh가 아닌 경우)
      if (!forceRefresh) {
        try {
          final cachedPoints =
              await localDataSource.getCachedCustomerPoints(phoneNumber);
          if (cachedPoints != null) {
            return Right(cachedPoints.toEntity());
          }
        } on CacheException {
          // 캐시 오류는 무시하고 서버에서 가져옴
        }
      }

      // 2. 네트워크 연결 확인
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      // 3. 서버에서 가져오기
      final customerPointsModel =
          await remoteDataSource.getCustomerPoints(phoneNumber);

      // 4. 캐시에 저장
      if (customerPointsModel != null) {
        try {
          await localDataSource.cacheCustomerPoints(customerPointsModel);
        } on CacheException {
          // 캐시 저장 실패는 무시
        }
      }

      return Right(customerPointsModel?.toEntity());
    } on ServerException catch (e) {
      return Left(ServerFailure(e.message));
    } catch (e) {
      return Left(UnknownFailure('고객 포인트 조회 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, void>> usePoints({
    required String phoneNumber,
    required int amount,
  }) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      // 1. 고객 정보 먼저 가져오기
      final customerPointsResult =
          await getCustomerPoints(phoneNumber: phoneNumber);

      return customerPointsResult.fold(
        (failure) => Left(failure),
        (customerPoints) async {
          if (customerPoints == null) {
            return const Left(ServerFailure('고객 정보를 찾을 수 없습니다.'));
          }

          if (!customerPoints.canUsePoints(amount)) {
            return const Left(ServerFailure('포인트가 부족합니다.'));
          }

          try {
            await remoteDataSource.usePoints(
              customerId: customerPoints.customerId,
              amount: amount,
            );

            // 캐시 무효화
            invalidateCache(phoneNumber);

            return const Right(null);
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('포인트 사용 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, int>> earnPoints({
    required String phoneNumber,
    required int fare,
  }) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      // 1. 고객 정보 먼저 가져오기
      final customerPointsResult =
          await getCustomerPoints(phoneNumber: phoneNumber);

      return customerPointsResult.fold(
        (failure) => Left(failure),
        (customerPoints) async {
          if (customerPoints == null) {
            return const Left(ServerFailure('고객 정보를 찾을 수 없습니다.'));
          }

          try {
            final earnedPoints = await remoteDataSource.earnPoints(
              customerId: customerPoints.customerId,
              fare: fare,
              grade: customerPoints.grade,
            );

            // 캐시 무효화
            invalidateCache(phoneNumber);

            return Right(earnedPoints);
          } on ServerException catch (e) {
            return Left(ServerFailure(e.message));
          }
        },
      );
    } catch (e) {
      return Left(UnknownFailure('포인트 적립 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, CustomerPoints>> createCustomerPoints({
    required String phoneNumber,
  }) async {
    try {
      if (!await networkInfo.isConnected) {
        return const Left(NetworkFailure('네트워크 연결을 확인해주세요.'));
      }

      final customerPointsModel =
          await remoteDataSource.createCustomerPoints(phoneNumber);

      // 캐시에 저장
      try {
        await localDataSource.cacheCustomerPoints(customerPointsModel);
      } on CacheException {
        // 캐시 저장 실패는 무시
      }

      return Right(customerPointsModel.toEntity());
    } on ServerException catch (e) {
      return Left(ServerFailure(e.message));
    } catch (e) {
      return Left(UnknownFailure('고객 포인트 생성 실패: $e'));
    }
  }

  @override
  void clearCache() {
    try {
      localDataSource.clearCache();
    } on CacheException {
      // 캐시 삭제 실패는 무시
    }
  }

  @override
  void invalidateCache(String phoneNumber) {
    try {
      localDataSource.deleteCachedCustomerPoints(phoneNumber);
    } on CacheException {
      // 캐시 삭제 실패는 무시
    }
  }
}
