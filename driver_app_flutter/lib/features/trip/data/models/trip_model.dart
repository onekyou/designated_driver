import 'package:cloud_firestore/cloud_firestore.dart';
import '../../domain/entities/trip.dart';

/// Trip Model (Entity ↔ JSON 변환)
class TripModel {
  final String id;
  final String callId;
  final String driverId;
  final String driverName;
  final String phoneNumber;
  final String? customerName;
  final String pickupLocation;
  final String destination;
  final int fare;
  final TripStatus status;
  final int? pointsUsed;
  final int? pointsEarned;
  final DateTime? startTime;
  final DateTime? endTime;
  final String? notes;

  const TripModel({
    required this.id,
    required this.callId,
    required this.driverId,
    required this.driverName,
    required this.phoneNumber,
    this.customerName,
    required this.pickupLocation,
    required this.destination,
    required this.fare,
    required this.status,
    this.pointsUsed,
    this.pointsEarned,
    this.startTime,
    this.endTime,
    this.notes,
  });

  /// Entity에서 Model로 변환
  factory TripModel.fromEntity(Trip trip) {
    return TripModel(
      id: trip.id,
      callId: trip.callId,
      driverId: trip.driverId,
      driverName: trip.driverName,
      phoneNumber: trip.phoneNumber,
      customerName: trip.customerName,
      pickupLocation: trip.pickupLocation,
      destination: trip.destination,
      fare: trip.fare,
      status: trip.status,
      pointsUsed: trip.pointsUsed,
      pointsEarned: trip.pointsEarned,
      startTime: trip.startTime,
      endTime: trip.endTime,
      notes: trip.notes,
    );
  }

  /// JSON에서 Model로 변환
  factory TripModel.fromJson(Map<String, dynamic> json) {
    return TripModel(
      id: json['id'] as String,
      callId: json['callId'] as String,
      driverId: json['driverId'] as String,
      driverName: json['driverName'] as String,
      phoneNumber: json['phoneNumber'] as String,
      customerName: json['customerName'] as String?,
      pickupLocation: json['pickupLocation'] as String,
      destination: json['destination'] as String,
      fare: json['fare'] as int,
      status: TripStatus.fromString(json['status'] as String?),
      pointsUsed: json['pointsUsed'] as int?,
      pointsEarned: json['pointsEarned'] as int?,
      startTime: _parseDateTime(json['startTime']),
      endTime: _parseDateTime(json['endTime']),
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
      'callId': callId,
      'driverId': driverId,
      'driverName': driverName,
      'phoneNumber': phoneNumber,
      'customerName': customerName,
      'pickupLocation': pickupLocation,
      'destination': destination,
      'fare': fare,
      'status': status.value,
      'pointsUsed': pointsUsed,
      'pointsEarned': pointsEarned,
      'startTime': startTime?.millisecondsSinceEpoch,
      'endTime': endTime?.millisecondsSinceEpoch,
      'notes': notes,
    };
  }

  /// Firestore에서 변환
  factory TripModel.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;
    return TripModel.fromJson({
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
  Trip toEntity() {
    return Trip(
      id: id,
      callId: callId,
      driverId: driverId,
      driverName: driverName,
      phoneNumber: phoneNumber,
      customerName: customerName,
      pickupLocation: pickupLocation,
      destination: destination,
      fare: fare,
      status: status,
      pointsUsed: pointsUsed,
      pointsEarned: pointsEarned,
      startTime: startTime,
      endTime: endTime,
      notes: notes,
    );
  }
}
