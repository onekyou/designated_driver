import '../../domain/entities/location.dart';

/// Location Model (Entity ↔ JSON 변환)
class LocationModel {
  final double latitude;
  final double longitude;
  final String? address;
  final String? addressDetail;
  final DateTime? timestamp;

  const LocationModel({
    required this.latitude,
    required this.longitude,
    this.address,
    this.addressDetail,
    this.timestamp,
  });

  /// Entity에서 Model로 변환
  factory LocationModel.fromEntity(Location location) {
    return LocationModel(
      latitude: location.latitude,
      longitude: location.longitude,
      address: location.address,
      addressDetail: location.addressDetail,
      timestamp: location.timestamp,
    );
  }

  /// JSON에서 Model로 변환
  factory LocationModel.fromJson(Map<String, dynamic> json) {
    return LocationModel(
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      address: json['address'] as String?,
      addressDetail: json['addressDetail'] as String?,
      timestamp: json['timestamp'] != null
          ? DateTime.fromMillisecondsSinceEpoch(json['timestamp'] as int)
          : null,
    );
  }

  /// Model을 JSON으로 변환
  Map<String, dynamic> toJson() {
    return {
      'latitude': latitude,
      'longitude': longitude,
      'address': address,
      'addressDetail': addressDetail,
      'timestamp': timestamp?.millisecondsSinceEpoch,
    };
  }

  /// Model을 Entity로 변환
  Location toEntity() {
    return Location(
      latitude: latitude,
      longitude: longitude,
      address: address,
      addressDetail: addressDetail,
      timestamp: timestamp,
    );
  }

  /// Geolocator Position에서 변환
  factory LocationModel.fromPosition(dynamic position) {
    return LocationModel(
      latitude: position.latitude as double,
      longitude: position.longitude as double,
      timestamp: position.timestamp as DateTime?,
    );
  }
}
