import 'package:freezed_annotation/freezed_annotation.dart';

part 'call.freezed.dart';

/// 콜 상태 Enum
enum CallStatus {
  pending('PENDING', '대기중'),
  assigned('ASSIGNED', '배차완료'),
  accepted('ACCEPTED', '수락'),
  pickedUp('PICKED_UP', '픽업완료'),
  completed('COMPLETED', '완료'),
  cancelled('CANCELLED', '취소됨');

  final String value;
  final String displayName;

  const CallStatus(this.value, this.displayName);

  static CallStatus fromString(String? status) {
    if (status == null) return pending;
    return CallStatus.values.firstWhere(
      (e) => e.value == status.toUpperCase(),
      orElse: () => pending,
    );
  }
}

/// 콜 Entity (순수 Dart, Firebase 독립적)
@freezed
class Call with _$Call {
  const factory Call({
    required String id,
    required String regionId,
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
    DateTime? pickedUpTime,
    DateTime? completedTime,
    int? fare,
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
    return status == CallStatus.accepted || status == CallStatus.pickedUp;
  }
}
