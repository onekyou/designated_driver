import 'package:dartz/dartz.dart';
import '../../../../core/error/failures.dart';
import '../entities/location.dart';

/// 위치 Repository 인터페이스
abstract class LocationRepository {
  /// 현재 위치 가져오기
  Future<Either<Failure, Location>> getCurrentLocation();

  /// 주소를 좌표로 변환 (Geocoding)
  ///
  /// [address] 주소 문자열
  Future<Either<Failure, Location>> geocodeAddress(String address);

  /// 좌표를 주소로 변환 (Reverse Geocoding)
  ///
  /// [latitude] 위도
  /// [longitude] 경도
  Future<Either<Failure, String>> reverseGeocode({
    required double latitude,
    required double longitude,
  });

  /// 위치 권한 확인
  Future<Either<Failure, bool>> checkLocationPermission();

  /// 위치 권한 요청
  Future<Either<Failure, bool>> requestLocationPermission();
}
