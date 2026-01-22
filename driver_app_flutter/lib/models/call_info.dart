import 'package:cloud_firestore/cloud_firestore.dart';

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

class CallInfo {
  final String id;
  final String regionId;
  final String officeId;
  final String phoneNumber;
  final String? customerName;
  final String? pickupLocation;
  final String? destination;
  final CallStatus status;
  final String? assignedDriverId;
  final String? assignedDriverName;
  final DateTime? callTime;
  final DateTime? assignedTime;
  final DateTime? acceptedTime;
  final DateTime? pickedUpTime;
  final DateTime? completedTime;
  final int? fare;
  final String? notes;

  CallInfo({
    required this.id,
    required this.regionId,
    required this.officeId,
    required this.phoneNumber,
    this.customerName,
    this.pickupLocation,
    this.destination,
    required this.status,
    this.assignedDriverId,
    this.assignedDriverName,
    this.callTime,
    this.assignedTime,
    this.acceptedTime,
    this.pickedUpTime,
    this.completedTime,
    this.fare,
    this.notes,
  });

  /// Firestore에서 복원
  factory CallInfo.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;
    return CallInfo(
      id: doc.id,
      regionId: data['regionId'] as String,
      officeId: data['officeId'] as String,
      phoneNumber: data['phoneNumber'] as String,
      customerName: data['customerName'] as String?,
      pickupLocation: data['pickupLocation'] as String?,
      destination: data['destination'] as String?,
      status: CallStatus.fromString(data['status'] as String?),
      assignedDriverId: data['assignedDriverId'] as String?,
      assignedDriverName: data['assignedDriverName'] as String?,
      callTime: (data['callTime'] as Timestamp?)?.toDate(),
      assignedTime: (data['assignedTime'] as Timestamp?)?.toDate(),
      acceptedTime: (data['acceptedTime'] as Timestamp?)?.toDate(),
      pickedUpTime: (data['pickedUpTime'] as Timestamp?)?.toDate(),
      completedTime: (data['completedTime'] as Timestamp?)?.toDate(),
      fare: data['fare'] as int?,
      notes: data['notes'] as String?,
    );
  }

  /// Firestore에 저장할 Map
  Map<String, dynamic> toMap() {
    return {
      'regionId': regionId,
      'officeId': officeId,
      'phoneNumber': phoneNumber,
      'customerName': customerName,
      'pickupLocation': pickupLocation,
      'destination': destination,
      'status': status.value,
      'assignedDriverId': assignedDriverId,
      'assignedDriverName': assignedDriverName,
      'callTime': callTime != null ? Timestamp.fromDate(callTime!) : null,
      'assignedTime': assignedTime != null ? Timestamp.fromDate(assignedTime!) : null,
      'acceptedTime': acceptedTime != null ? Timestamp.fromDate(acceptedTime!) : null,
      'pickedUpTime': pickedUpTime != null ? Timestamp.fromDate(pickedUpTime!) : null,
      'completedTime': completedTime != null ? Timestamp.fromDate(completedTime!) : null,
      'fare': fare,
      'notes': notes,
    };
  }

  /// Copy with
  CallInfo copyWith({
    String? id,
    String? regionId,
    String? officeId,
    String? phoneNumber,
    String? customerName,
    String? pickupLocation,
    String? destination,
    CallStatus? status,
    String? assignedDriverId,
    String? assignedDriverName,
    DateTime? callTime,
    DateTime? assignedTime,
    DateTime? acceptedTime,
    DateTime? pickedUpTime,
    DateTime? completedTime,
    int? fare,
    String? notes,
  }) {
    return CallInfo(
      id: id ?? this.id,
      regionId: regionId ?? this.regionId,
      officeId: officeId ?? this.officeId,
      phoneNumber: phoneNumber ?? this.phoneNumber,
      customerName: customerName ?? this.customerName,
      pickupLocation: pickupLocation ?? this.pickupLocation,
      destination: destination ?? this.destination,
      status: status ?? this.status,
      assignedDriverId: assignedDriverId ?? this.assignedDriverId,
      assignedDriverName: assignedDriverName ?? this.assignedDriverName,
      callTime: callTime ?? this.callTime,
      assignedTime: assignedTime ?? this.assignedTime,
      acceptedTime: acceptedTime ?? this.acceptedTime,
      pickedUpTime: pickedUpTime ?? this.pickedUpTime,
      completedTime: completedTime ?? this.completedTime,
      fare: fare ?? this.fare,
      notes: notes ?? this.notes,
    );
  }

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
}
