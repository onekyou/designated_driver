import 'package:dartz/dartz.dart';
import '../../../../core/error/exceptions.dart';
import '../../../../core/error/failures.dart';
import '../../domain/entities/location.dart';
import '../../domain/repositories/location_repository.dart';
import '../datasources/location_local_datasource.dart';

/// LocationRepository 구현
class LocationRepositoryImpl implements LocationRepository {
  final LocationLocalDataSource localDataSource;

  LocationRepositoryImpl({
    required this.localDataSource,
  });

  @override
  Future<Either<Failure, Location>> getCurrentLocation() async {
    try {
      final locationModel = await localDataSource.getCurrentLocation();
      return Right(locationModel.toEntity());
    } on LocationException catch (e) {
      return Left(LocationFailure(e.message));
    } on UnimplementedException {
      return const Left(UnknownFailure('위치 기능이 아직 구현되지 않았습니다.'));
    } catch (e) {
      return Left(UnknownFailure('현재 위치 가져오기 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, Location>> geocodeAddress(String address) async {
    try {
      // TODO: Geocoding API 연동
      // 예: Google Maps Geocoding API 또는 Kakao Local API
      return const Left(UnknownFailure('Geocoding 기능이 아직 구현되지 않았습니다.'));
    } catch (e) {
      return Left(UnknownFailure('주소 변환 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, String>> reverseGeocode({
    required double latitude,
    required double longitude,
  }) async {
    try {
      // TODO: Reverse Geocoding API 연동
      // 예: Google Maps Geocoding API 또는 Kakao Local API
      return const Left(UnknownFailure('Reverse Geocoding 기능이 아직 구현되지 않았습니다.'));
    } catch (e) {
      return Left(UnknownFailure('좌표 변환 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, bool>> checkLocationPermission() async {
    try {
      final hasPermission = await localDataSource.checkLocationPermission();
      return Right(hasPermission);
    } on LocationException catch (e) {
      return Left(LocationFailure(e.message));
    } on UnimplementedException {
      return const Left(UnknownFailure('위치 권한 기능이 아직 구현되지 않았습니다.'));
    } catch (e) {
      return Left(UnknownFailure('위치 권한 확인 실패: $e'));
    }
  }

  @override
  Future<Either<Failure, bool>> requestLocationPermission() async {
    try {
      final granted = await localDataSource.requestLocationPermission();
      return Right(granted);
    } on LocationException catch (e) {
      return Left(LocationFailure(e.message));
    } on UnimplementedException {
      return const Left(UnknownFailure('위치 권한 요청 기능이 아직 구현되지 않았습니다.'));
    } catch (e) {
      return Left(UnknownFailure('위치 권한 요청 실패: $e'));
    }
  }
}
