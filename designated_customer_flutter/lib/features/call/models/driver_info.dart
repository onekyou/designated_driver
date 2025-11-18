/// 기사 정보
class DriverInfo {
  final String id;
  final String name;
  final String phoneNumber;
  final String vehicleNumber;
  final double rating;

  const DriverInfo({
    required this.id,
    required this.name,
    required this.phoneNumber,
    required this.vehicleNumber,
    this.rating = 0.0,
  });

  /// Firestore에서 변환
  factory DriverInfo.fromMap(Map<String, dynamic> data) {
    return DriverInfo(
      id: data['id'] as String? ?? '',
      name: data['name'] as String? ?? '',
      phoneNumber: data['phoneNumber'] as String? ?? '',
      vehicleNumber: data['vehicleNumber'] as String? ?? '',
      rating: (data['rating'] as num?)?.toDouble() ?? 0.0,
    );
  }

  /// Firestore로 변환
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'phoneNumber': phoneNumber,
      'vehicleNumber': vehicleNumber,
      'rating': rating,
    };
  }

  /// copyWith
  DriverInfo copyWith({
    String? id,
    String? name,
    String? phoneNumber,
    String? vehicleNumber,
    double? rating,
  }) {
    return DriverInfo(
      id: id ?? this.id,
      name: name ?? this.name,
      phoneNumber: phoneNumber ?? this.phoneNumber,
      vehicleNumber: vehicleNumber ?? this.vehicleNumber,
      rating: rating ?? this.rating,
    );
  }
}
