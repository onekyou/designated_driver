import '../../../../core/error/exceptions.dart';
import '../models/location_model.dart';

/// 위치 관련 로컬 데이터 소스 (디바이스 위치 정보)
abstract class LocationLocalDataSource {
  /// 현재 위치 가져오기
  Future<LocationModel> getCurrentLocation();

  /// 위치 권한 확인
  Future<bool> checkLocationPermission();

  /// 위치 권한 요청
  Future<bool> requestLocationPermission();
}

class LocationLocalDataSourceImpl implements LocationLocalDataSource {
  // Geolocator 패키지는 런타임에 동적으로 사용
  // 실제 구현은 Presentation Layer에서 처리하거나
  // 또는 여기서는 인터페이스만 정의하고
  // 실제 구현은 나중에 추가

  @override
  Future<LocationModel> getCurrentLocation() async {
    try {
      // TODO: geolocator 패키지를 사용한 실제 구현
      // final position = await Geolocator.getCurrentPosition(
      //   desiredAccuracy: LocationAccuracy.high,
      // );
      // return LocationModel.fromPosition(position);

      // 현재는 더미 데이터 반환 (실제 구현 시 제거)
      throw UnimplementedException('getCurrentLocation not implemented yet');
    } catch (e) {
      throw LocationException('현재 위치 가져오기 실패: $e');
    }
  }

  @override
  Future<bool> checkLocationPermission() async {
    try {
      // TODO: geolocator 패키지를 사용한 실제 구현
      // final permission = await Geolocator.checkPermission();
      // return permission == LocationPermission.always ||
      //     permission == LocationPermission.whileInUse;

      // 현재는 더미 데이터 반환
      throw UnimplementedException('checkLocationPermission not implemented yet');
    } catch (e) {
      throw LocationException('위치 권한 확인 실패: $e');
    }
  }

  @override
  Future<bool> requestLocationPermission() async {
    try {
      // TODO: geolocator 패키지를 사용한 실제 구현
      // final permission = await Geolocator.requestPermission();
      // return permission == LocationPermission.always ||
      //     permission == LocationPermission.whileInUse;

      // 현재는 더미 데이터 반환
      throw UnimplementedException('requestLocationPermission not implemented yet');
    } catch (e) {
      throw LocationException('위치 권한 요청 실패: $e');
    }
  }
}
