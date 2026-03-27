import 'package:freezed_annotation/freezed_annotation.dart';

part 'call.freezed.dart';

/// 콜 상태 Enum (Kotlin CallStatus와 완전 일치)
enum CallStatus {
  waiting('WAITING', '대기중'),
  assigned('ASSIGNED', '배차완료'),
  accepted('ACCEPTED', '수락'),
  inProgress('IN_PROGRESS', '운행중'),
  awaitingSettlement('AWAITING_SETTLEMENT', '정산대기'),
  completed('COMPLETED', '완료'),
  canceled('CANCELED', '관리자취소'),
  cancelledByDriver('CANCELLED_BY_DRIVER', '기사취소'),
  cancelledByCustomer('CANCELLED_BY_CUSTOMER', '고객취소'),
  hold('HOLD', '재배차대기'),
  sharedWaiting('SHARED_WAITING', '공유대기'),
  claimed('CLAIMED', '수임');

  final String value;
  final String displayName;

  const CallStatus(this.value, this.displayName);

  static CallStatus fromString(String? status) {
    if (status == null) return waiting;
    return CallStatus.values.firstWhere(
      (e) => e.value == status.toUpperCase(),
      orElse: () => waiting,
    );
  }

  bool get isCancelled =>
      this == canceled ||
      this == cancelledByDriver ||
      this == cancelledByCustomer;
}

/// 콜 Entity (순수 Dart, Firebase 독립적)
@freezed
class Call with _$Call {
  const factory Call({
    required String id,
    required String provinceId,
    required String cityId,
    required String officeId,
    required String phoneNumber,
    String? customerName,
    String? pickupLocation,
    String? destination,
    required CallStatus status,
    String? assignedDriverId,
    String? assignedDriverName,
    DateTime? callTime,
    DateTime? assignedTime,
    DateTime? acceptedTime,
    DateTime? startedTime,
    DateTime? completedTime,
    int? fare,
    int? cashReceived,
    String? paymentMethod,
    int? pointsUsed,
    int? pointsEarned,
    String? notes,
  }) = _Call;

  const Call._();

  /// 운행 진행 시간 계산
  Duration? getElapsedTime() {
    final startTime = acceptedTime ?? assignedTime;
    if (startTime == null) return null;

    final endTime = completedTime ?? DateTime.now();
    return endTime.difference(startTime);
  }

  /// 포맷된 시간 문자열
  String getFormattedElapsedTime() {
    final duration = getElapsedTime();
    if (duration == null) return '-';

    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    if (hours > 0) {
      return '$hours시간 $minutes분';
    } else {
      return '$minutes분';
    }
  }

  /// 내가 배정받은 콜인지 확인
  bool isAssignedTo(String driverId) {
    return assignedDriverId == driverId;
  }

  /// 콜 수락 가능 여부
  bool canAccept(String driverId) {
    return status == CallStatus.assigned && isAssignedTo(driverId);
  }

  /// 콜 완료 가능 여부
  bool canComplete() {
    return status == CallStatus.accepted || status == CallStatus.inProgress;
  }
}
