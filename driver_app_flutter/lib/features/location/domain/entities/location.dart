import 'dart:math' as math;
import 'package:freezed_annotation/freezed_annotation.dart';

part 'location.freezed.dart';

/// 위치 Entity (순수 Dart)
@freezed
class Location with _$Location {
  const factory Location({
    required double latitude,
    required double longitude,
    String? address,
    String? addressDetail,
    DateTime? timestamp,
  }) = _Location;

  const Location._();

  /// 두 위치 사이의 거리 계산 (km)
  double distanceTo(Location other) {
    // Haversine 공식 (간단 버전)
    const double earthRadius = 6371; // km

    final latDiff = _toRadians(other.latitude - latitude);
    final lonDiff = _toRadians(other.longitude - longitude);

    final a = math.sin(latDiff / 2) * math.sin(latDiff / 2) +
        math.cos(_toRadians(latitude)) *
            math.cos(_toRadians(other.latitude)) *
            math.sin(lonDiff / 2) *
            math.sin(lonDiff / 2);

    final c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a));

    return earthRadius * c;
  }

  double _toRadians(double degrees) {
    return degrees * math.pi / 180;
  }

  /// 좌표를 문자열로
  String toCoordinateString() {
    return '${latitude.toStringAsFixed(6)}, ${longitude.toStringAsFixed(6)}';
  }
}
