import 'package:freezed_annotation/freezed_annotation.dart';

part 'trip.freezed.dart';

/// 운행 상태 Enum
enum TripStatus {
  preparing('PREPARING', '준비중'),
  inProgress('IN_PROGRESS', '운행중'),
  completed('COMPLETED', '완료'),
  cancelled('CANCELLED', '취소됨');

  final String value;
  final String displayName;

  const TripStatus(this.value, this.displayName);

  static TripStatus fromString(String? status) {
    if (status == null) return preparing;
    return TripStatus.values.firstWhere(
      (e) => e.value == status.toUpperCase(),
      orElse: () => preparing,
    );
  }
}

/// 운행 Entity (순수 Dart, Firebase 독립적)
@freezed
class Trip with _$Trip {
  const factory Trip({
    required String id,
    required String callId,
    required String driverId,
    required String driverName,
    required String phoneNumber,
    String? customerName,
    required String pickupLocation,
    required String destination,
    required int fare,
    required TripStatus status,
    int? pointsUsed,
    int? pointsEarned,
    DateTime? startTime,
    DateTime? endTime,
    String? notes,
  }) = _Trip;

  const Trip._();

  /// 운행 소요 시간 계산
  Duration? getDuration() {
    if (startTime == null) return null;
    final end = endTime ?? DateTime.now();
    return end.difference(startTime!);
  }

  /// 포맷된 소요 시간
  String getFormattedDuration() {
    final duration = getDuration();
    if (duration == null) return '-';

    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    if (hours > 0) {
      return '$hours시간 $minutes분';
    } else {
      return '$minutes분';
    }
  }

  /// 실제 수입 (요금 - 사용 포인트)
  int getActualIncome() {
    return fare - (pointsUsed ?? 0);
  }

  /// 완료 가능 여부
  bool canComplete() {
    return status == TripStatus.inProgress;
  }
}
