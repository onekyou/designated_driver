import 'package:cloud_firestore/cloud_firestore.dart';
import '../../domain/entities/driver.dart';

/// Driver Model (Entity ↔ JSON 변환)
class DriverModel {
  final String id;
  final String authUid;
  final String name;
  final String email;
  final String phoneNumber;
  final String regionId;
  final String officeId;
  final DriverStatus status;
  final DriverApprovalStatus approvalStatus;
  final String? fcmToken;
  final String? currentCallId;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const DriverModel({
    required this.id,
    required this.authUid,
    required this.name,
    required this.email,
    required this.phoneNumber,
    required this.regionId,
    required this.officeId,
    required this.status,
    required this.approvalStatus,
    this.fcmToken,
    this.currentCallId,
    this.createdAt,
    this.updatedAt,
  });

  /// Entity에서 Model로 변환
  factory DriverModel.fromEntity(Driver driver) {
    return DriverModel(
      id: driver.id,
      authUid: driver.authUid,
      name: driver.name,
      email: driver.email,
      phoneNumber: driver.phoneNumber,
      regionId: driver.regionId,
      officeId: driver.officeId,
      status: driver.status,
      approvalStatus: driver.approvalStatus,
      fcmToken: driver.fcmToken,
      currentCallId: driver.currentCallId,
      createdAt: driver.createdAt,
      updatedAt: driver.updatedAt,
    );
  }

  /// JSON에서 Model로 변환
  factory DriverModel.fromJson(Map<String, dynamic> json) {
    return DriverModel(
      id: json['id'] as String,
      authUid: json['authUid'] as String,
      name: json['name'] as String,
      email: json['email'] as String,
      phoneNumber: json['phoneNumber'] as String,
      regionId: json['regionId'] as String,
      officeId: json['officeId'] as String,
      status: DriverStatus.fromString(json['status'] as String?),
      approvalStatus: DriverApprovalStatus.fromString(json['approvalStatus'] as String?),
      fcmToken: json['fcmToken'] as String?,
      currentCallId: json['currentCallId'] as String?,
      createdAt: _parseDateTime(json['createdAt']),
      updatedAt: _parseDateTime(json['updatedAt']),
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
      'id': id,
      'authUid': authUid,
      'name': name,
      'email': email,
      'phoneNumber': phoneNumber,
      'regionId': regionId,
      'officeId': officeId,
      'status': status.value,
      'approvalStatus': approvalStatus.value,
      'fcmToken': fcmToken,
      'currentCallId': currentCallId,
      'createdAt': createdAt?.millisecondsSinceEpoch,
      'updatedAt': updatedAt?.millisecondsSinceEpoch,
    };
  }

  /// Firestore에서 변환
  factory DriverModel.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;
    return DriverModel.fromJson({
      ...data,
      'id': doc.id,
    });
  }

  /// Firestore에 저장할 Map
  Map<String, dynamic> toFirestore() {
    final json = toJson();
    json.remove('id'); // Firestore document ID는 별도 관리
    return json;
  }

  /// Model을 Entity로 변환
  Driver toEntity() {
    return Driver(
      id: id,
      authUid: authUid,
      name: name,
      email: email,
      phoneNumber: phoneNumber,
      regionId: regionId,
      officeId: officeId,
      status: status,
      approvalStatus: approvalStatus,
      fcmToken: fcmToken,
      currentCallId: currentCallId,
      createdAt: createdAt,
      updatedAt: updatedAt,
    );
  }
}
