import 'package:cloud_firestore/cloud_firestore.dart';

enum DriverStatus {
  online('ONLINE', '대기중'),
  offline('OFFLINE', '오프라인'),
  busy('BUSY', '운행중'),
  waiting('WAITING', '배차대기');

  final String value;
  final String displayName;

  const DriverStatus(this.value, this.displayName);

  static DriverStatus fromString(String? status) {
    if (status == null) return offline;
    return DriverStatus.values.firstWhere(
      (e) => e.value == status.toUpperCase(),
      orElse: () => offline,
    );
  }
}

enum DriverApprovalStatus {
  pending('PENDING', '승인대기'),
  approved('APPROVED', '승인완료'),
  rejected('REJECTED', '거절됨');

  final String value;
  final String displayName;

  const DriverApprovalStatus(this.value, this.displayName);

  static DriverApprovalStatus fromString(String? status) {
    if (status == null) return pending;
    return DriverApprovalStatus.values.firstWhere(
      (e) => e.value == status.toUpperCase(),
      orElse: () => pending,
    );
  }
}

class DriverInfo {
  final String id;
  final String authUid;
  final String name;
  final String phone;
  final String regionId;
  final String officeId;
  final DriverStatus status;
  final DriverApprovalStatus approvalStatus;
  final String? fcmToken;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  DriverInfo({
    required this.id,
    required this.authUid,
    required this.name,
    required this.phone,
    required this.regionId,
    required this.officeId,
    required this.status,
    required this.approvalStatus,
    this.fcmToken,
    this.createdAt,
    this.updatedAt,
  });

  /// Firestore에서 복원
  factory DriverInfo.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;
    return DriverInfo(
      id: doc.id,
      authUid: data['authUid'] as String,
      name: data['name'] as String,
      phone: data['phone'] as String,
      regionId: data['regionId'] as String,
      officeId: data['officeId'] as String,
      status: DriverStatus.fromString(data['status'] as String?),
      approvalStatus: DriverApprovalStatus.fromString(data['approvalStatus'] as String?),
      fcmToken: data['fcmToken'] as String?,
      createdAt: (data['createdAt'] as Timestamp?)?.toDate(),
      updatedAt: (data['updatedAt'] as Timestamp?)?.toDate(),
    );
  }

  /// Firestore에 저장할 Map
  Map<String, dynamic> toMap() {
    return {
      'authUid': authUid,
      'name': name,
      'phone': phone,
      'regionId': regionId,
      'officeId': officeId,
      'status': status.value,
      'approvalStatus': approvalStatus.value,
      'fcmToken': fcmToken,
      'updatedAt': FieldValue.serverTimestamp(),
    };
  }

  /// Copy with
  DriverInfo copyWith({
    String? id,
    String? authUid,
    String? name,
    String? phone,
    String? regionId,
    String? officeId,
    DriverStatus? status,
    DriverApprovalStatus? approvalStatus,
    String? fcmToken,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return DriverInfo(
      id: id ?? this.id,
      authUid: authUid ?? this.authUid,
      name: name ?? this.name,
      phone: phone ?? this.phone,
      regionId: regionId ?? this.regionId,
      officeId: officeId ?? this.officeId,
      status: status ?? this.status,
      approvalStatus: approvalStatus ?? this.approvalStatus,
      fcmToken: fcmToken ?? this.fcmToken,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}
