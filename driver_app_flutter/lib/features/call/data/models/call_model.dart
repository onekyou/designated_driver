import 'package:cloud_firestore/cloud_firestore.dart';
import '../../domain/entities/call.dart';

/// Call Model (Entity ↔ JSON 변환)
class CallModel {
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
  final int? pointsUsed;
  final int? pointsEarned;
  final String? notes;

  const CallModel({
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
    this.pointsUsed,
    this.pointsEarned,
    this.notes,
  });

  /// Entity에서 Model로 변환
  factory CallModel.fromEntity(Call call) {
    return CallModel(
      id: call.id,
      regionId: call.regionId,
      officeId: call.officeId,
      phoneNumber: call.phoneNumber,
      customerName: call.customerName,
      pickupLocation: call.pickupLocation,
      destination: call.destination,
      status: call.status,
      assignedDriverId: call.assignedDriverId,
      assignedDriverName: call.assignedDriverName,
      callTime: call.callTime,
      assignedTime: call.assignedTime,
      acceptedTime: call.acceptedTime,
      pickedUpTime: call.pickedUpTime,
      completedTime: call.completedTime,
      fare: call.fare,
      pointsUsed: call.pointsUsed,
      pointsEarned: call.pointsEarned,
      notes: call.notes,
    );
  }

  /// JSON에서 Model로 변환
  factory CallModel.fromJson(Map<String, dynamic> json) {
    return CallModel(
      id: json['id'] as String,
      regionId: json['regionId'] as String,
      officeId: json['officeId'] as String,
      phoneNumber: json['phoneNumber'] as String,
      customerName: json['customerName'] as String?,
      pickupLocation: json['pickupLocation'] as String?,
      destination: json['destination'] as String?,
      status: CallStatus.fromString(json['status'] as String?),
      assignedDriverId: json['assignedDriverId'] as String?,
      assignedDriverName: json['assignedDriverName'] as String?,
      callTime: _parseDateTime(json['callTime']),
      assignedTime: _parseDateTime(json['assignedTime']),
      acceptedTime: _parseDateTime(json['acceptedTime']),
      pickedUpTime: _parseDateTime(json['pickedUpTime']),
      completedTime: _parseDateTime(json['completedTime']),
      fare: json['fare'] as int?,
      pointsUsed: json['pointsUsed'] as int?,
      pointsEarned: json['pointsEarned'] as int?,
      notes: json['notes'] as String?,
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
      'regionId': regionId,
      'officeId': officeId,
      'phoneNumber': phoneNumber,
      'customerName': customerName,
      'pickupLocation': pickupLocation,
      'destination': destination,
      'status': status.value,
      'assignedDriverId': assignedDriverId,
      'assignedDriverName': assignedDriverName,
      'callTime': callTime?.millisecondsSinceEpoch,
      'assignedTime': assignedTime?.millisecondsSinceEpoch,
      'acceptedTime': acceptedTime?.millisecondsSinceEpoch,
      'pickedUpTime': pickedUpTime?.millisecondsSinceEpoch,
      'completedTime': completedTime?.millisecondsSinceEpoch,
      'fare': fare,
      'pointsUsed': pointsUsed,
      'pointsEarned': pointsEarned,
      'notes': notes,
    };
  }

  /// Firestore에서 변환
  factory CallModel.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;
    return CallModel.fromJson({
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
  Call toEntity() {
    return Call(
      id: id,
      regionId: regionId,
      officeId: officeId,
      phoneNumber: phoneNumber,
      customerName: customerName,
      pickupLocation: pickupLocation,
      destination: destination,
      status: status,
      assignedDriverId: assignedDriverId,
      assignedDriverName: assignedDriverName,
      callTime: callTime,
      assignedTime: assignedTime,
      acceptedTime: acceptedTime,
      pickedUpTime: pickedUpTime,
      completedTime: completedTime,
      fare: fare,
      pointsUsed: pointsUsed,
      pointsEarned: pointsEarned,
      notes: notes,
    );
  }
}
