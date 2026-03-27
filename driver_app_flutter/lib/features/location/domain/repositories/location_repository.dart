import '../entities/location.dart';

/// 위치 Repository 인터페이스 (Either 제거)
abstract class LocationRepository {
  /// 현재 위치 가져오기
  Future<Location> getCurrentLocation();

  /// 주소를 좌표로 변환 (Geocoding)
  Future<Location> geocodeAddress(String address);

  /// 좌표를 주소로 변환 (Reverse Geocoding)
  Future<String> reverseGeocode({
    required double latitude,
    required double longitude,
  });

  /// 위치 권한 확인
  Future<bool> checkLocationPermission();

  /// 위치 권한 요청
  Future<bool> requestLocationPermission();
}
