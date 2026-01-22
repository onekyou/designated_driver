import 'package:cloud_firestore/cloud_firestore.dart';
import '../../domain/entities/customer_points.dart';

/// CustomerPoints Model (Entity ↔ JSON 변환)
class CustomerPointsModel {
  final String customerId;
  final String phoneNumber;
  final int currentPoints;
  final int totalEarned;
  final int totalUsed;
  final CustomerGrade grade;
  final int totalCalls;
  final DateTime? lastUpdated;

  const CustomerPointsModel({
    required this.customerId,
    required this.phoneNumber,
    required this.currentPoints,
    required this.totalEarned,
    required this.totalUsed,
    required this.grade,
    required this.totalCalls,
    this.lastUpdated,
  });

  /// Entity에서 Model로 변환
  factory CustomerPointsModel.fromEntity(CustomerPoints customerPoints) {
    return CustomerPointsModel(
      customerId: customerPoints.customerId,
      phoneNumber: customerPoints.phoneNumber,
      currentPoints: customerPoints.currentPoints,
      totalEarned: customerPoints.totalEarned,
      totalUsed: customerPoints.totalUsed,
      grade: customerPoints.grade,
      totalCalls: customerPoints.totalCalls,
      lastUpdated: customerPoints.lastUpdated,
    );
  }

  /// JSON에서 Model로 변환
  factory CustomerPointsModel.fromJson(Map<String, dynamic> json) {
    return CustomerPointsModel(
      customerId: json['customerId'] as String,
      phoneNumber: json['phoneNumber'] as String,
      currentPoints: json['currentPoints'] as int,
      totalEarned: json['totalEarned'] as int,
      totalUsed: json['totalUsed'] as int,
      grade: CustomerGrade.fromString(json['grade'] as String?),
      totalCalls: json['totalCalls'] as int,
      lastUpdated: _parseDateTime(json['lastUpdated']),
    );
  }

  /// DateTime 파싱 헬퍼 (Timestamp 또는 int milliseconds)
  static DateTime? _parseDateTime(dynamic value) {
    if (value == null) return null;
    if (value is Timestamp) return value.toDate();
    if (value is int) return DateTime.fromMillisecondsSinceEpoch(value);
    return null;
  }

  /// Model을 JSON으로 변환
  Map<String, dynamic> toJson() {
    return {
      'customerId': customerId,
      'phoneNumber': phoneNumber,
      'currentPoints': currentPoints,
      'totalEarned': totalEarned,
      'totalUsed': totalUsed,
      'grade': grade.value,
      'totalCalls': totalCalls,
      'lastUpdated': lastUpdated?.millisecondsSinceEpoch,
    };
  }

  /// Firestore에서 변환
  factory CustomerPointsModel.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;
    return CustomerPointsModel.fromJson({
      ...data,
      'customerId': doc.id,
    });
  }

  /// Firestore에 저장할 Map
  Map<String, dynamic> toFirestore() {
    final json = toJson();
    json.remove('customerId'); // Firestore document ID는 별도 관리
    return json;
  }

  /// Model을 Entity로 변환
  CustomerPoints toEntity() {
    return CustomerPoints(
      customerId: customerId,
      phoneNumber: phoneNumber,
      currentPoints: currentPoints,
      totalEarned: totalEarned,
      totalUsed: totalUsed,
      grade: grade,
      totalCalls: totalCalls,
      lastUpdated: lastUpdated,
    );
  }
}
