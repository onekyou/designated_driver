import 'package:freezed_annotation/freezed_annotation.dart';

part 'position.freezed.dart';

/// 위치 정보 Entity
@freezed
class Position with _$Position {
  const factory Position({
    required double latitude,
    required double longitude,
    double? altitude,
    double? accuracy,
    double? heading,
    double? speed,
    DateTime? timestamp,
  }) = _Position;

  const Position._();

  /// 두 위치 간 거리 계산 (대략적인 km 단위)
  double distanceTo(Position other) {
    const double earthRadiusKm = 6371.0;

    final lat1 = latitude * (3.14159 / 180);
    final lat2 = other.latitude * (3.14159 / 180);
    final dLat = (other.latitude - latitude) * (3.14159 / 180);
    final dLon = (other.longitude - longitude) * (3.14159 / 180);

    final a = (dLat / 2) * (dLat / 2) +
        (dLon / 2) * (dLon / 2) * (lat1) * (lat2);
    final c = 2 * (a / (1 - a));

    return earthRadiusKm * c;
  }
}
