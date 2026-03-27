import 'package:freezed_annotation/freezed_annotation.dart';

part 'driver.freezed.dart';

/// 기사 상태 Enum (Kotlin DriverStatus와 일치)
enum DriverStatus {
  offline('OFFLINE', '오프라인'),
  online('ONLINE', '온라인'),
  waiting('WAITING', '대기중'),
  assigned('ASSIGNED', '배정됨'),
  accepted('ACCEPTED', '수락'),
  preparing('PREPARING', '준비중'),
  onTrip('ON_TRIP', '운행중'),
  inProgress('IN_PROGRESS', '운행중'),
  awaitingSettlement('AWAITING_SETTLEMENT', '정산대기'),
  completed('COMPLETED', '완료');

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

/// 기사 승인 상태 Enum
enum DriverApprovalStatus {
  pending('PENDING', '승인대기'),
  approved('APPROVED', '승인됨'),
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

/// 기사 Entity (순수 Dart, Firebase 독립적)
@freezed
class Driver with _$Driver {
  const factory Driver({
    required String id,
    required String authUid,
    required String name,
    required String email,
    required String phoneNumber,
    required String provinceId,
    required String cityId,
    required String officeId,
    required DriverStatus status,
    required DriverApprovalStatus approvalStatus,
    String? fcmToken,
    String? currentCallId,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) = _Driver;

  const Driver._();

  /// 콜 수락 가능 상태인지
  bool canAcceptCall() {
    return status == DriverStatus.online || status == DriverStatus.waiting;
  }

  /// 운행 가능 상태인지
  bool canStartTrip() {
    return status == DriverStatus.accepted;
  }

  /// 승인된 기사인지
  bool isApproved() {
    return approvalStatus == DriverApprovalStatus.approved;
  }
}
