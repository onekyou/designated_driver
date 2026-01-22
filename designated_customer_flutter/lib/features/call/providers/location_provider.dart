import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:location/location.dart' as loc;
import 'package:geocoding/geocoding.dart';

/// Location Service Provider
class LocationService {
  final loc.Location _location = loc.Location();

  /// 위치 권한 확인
  Future<bool> checkLocationPermission() async {
    final permission = await _location.hasPermission();
    return permission == loc.PermissionStatus.granted;
  }

  /// 위치 권한 요청
  Future<bool> requestLocationPermission() async {
    final permission = await _location.requestPermission();
    return permission == loc.PermissionStatus.granted;
  }

  /// 현재 위치 가져오기
  Future<loc.LocationData> getCurrentPosition() async {
    // 위치 서비스 활성화 확인
    bool serviceEnabled = await _location.serviceEnabled();
    if (!serviceEnabled) {
      serviceEnabled = await _location.requestService();
      if (!serviceEnabled) {
        throw Exception('위치 서비스가 비활성화되어 있습니다.');
      }
    }

    // 권한 확인
    loc.PermissionStatus permission = await _location.hasPermission();
    if (permission == loc.PermissionStatus.denied) {
      permission = await _location.requestPermission();
      if (permission != loc.PermissionStatus.granted) {
        throw Exception('위치 권한이 거부되었습니다.');
      }
    }

    // 현재 위치 가져오기
    return await _location.getLocation();
  }

  /// 좌표를 주소로 변환
  /// Native Android LocationService.kt의 getAddressFromCoordinates()와 동일한 방식
  Future<String> getAddressFromCoordinates(double latitude, double longitude) async {
    try {
      // Geocoding 패키지를 사용하여 좌표를 주소로 변환
      List<Placemark> placemarks = await placemarkFromCoordinates(latitude, longitude);

      if (placemarks.isNotEmpty) {
        final placemark = placemarks.first;

        // Native Android와 동일한 형식으로 주소 조합
        // adminArea (시/도) + locality (시/군/구) + thoroughfare (도로명) + name (상세주소)
        final parts = <String>[];

        if (placemark.administrativeArea != null && placemark.administrativeArea!.isNotEmpty) {
          parts.add(placemark.administrativeArea!);
        }
        if (placemark.locality != null && placemark.locality!.isNotEmpty) {
          parts.add(placemark.locality!);
        }
        if (placemark.thoroughfare != null && placemark.thoroughfare!.isNotEmpty) {
          parts.add(placemark.thoroughfare!);
        }
        if (placemark.name != null && placemark.name!.isNotEmpty) {
          parts.add(placemark.name!);
        }

        return parts.join(' ').trim();
      } else {
        return '주소를 찾을 수 없습니다';
      }
    } catch (e) {
      return '주소 변환 중 오류가 발생했습니다';
    }
  }
}

/// Location Service Provider
final locationServiceProvider = Provider<LocationService>((ref) {
  return LocationService();
});

/// Current Location Provider - 현재 위치 정보
final currentLocationProvider = FutureProvider.autoDispose<String>((ref) async {
  final locationService = ref.watch(locationServiceProvider);

  try {
    // 현재 위치 가져오기
    final position = await locationService.getCurrentPosition();

    // 좌표 null 체크
    if (position.latitude == null || position.longitude == null) {
      throw Exception('위치 좌표를 가져올 수 없습니다');
    }

    // 주소로 변환
    final address = await locationService.getAddressFromCoordinates(
      position.latitude!,
      position.longitude!,
    );

    return address;
  } catch (e) {
    throw Exception('위치 정보를 가져올 수 없습니다: $e');
  }
});

/// Location Permission State Provider
class LocationPermissionNotifier extends StateNotifier<AsyncValue<bool>> {
  LocationPermissionNotifier(this._locationService) : super(const AsyncValue.loading()) {
    _checkPermission();
  }

  final LocationService _locationService;

  Future<void> _checkPermission() async {
    state = const AsyncValue.loading();
    try {
      final hasPermission = await _locationService.checkLocationPermission();
      state = AsyncValue.data(hasPermission);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  Future<void> requestPermission() async {
    state = const AsyncValue.loading();
    try {
      final granted = await _locationService.requestLocationPermission();
      state = AsyncValue.data(granted);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
}

/// Location Permission Provider
final locationPermissionProvider = StateNotifierProvider<LocationPermissionNotifier, AsyncValue<bool>>((ref) {
  final locationService = ref.watch(locationServiceProvider);
  return LocationPermissionNotifier(locationService);
});
