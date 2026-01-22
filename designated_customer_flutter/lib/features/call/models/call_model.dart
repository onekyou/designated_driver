import 'package:cloud_firestore/cloud_firestore.dart';
import 'call_state.dart';

/// 고객 콜 모델
class CustomerCall {
  final String id;
  final String phoneNumber;
  final String officeId;
  final String regionId;
  final String currentLocation; // 출발지
  final String destinationLocation; // 도착지
  final DateTime timestamp;
  final CallState status;
  final String? driverId;
  final int? estimatedArrivalTime; // 예상 도착 시간 (분)
  final int? fare; // 요금
  final String? notes; // 메모

  // 콜매니저 호환 필드들
  final String createdFrom;
  final String? customerId;
  final String? customerName;
  final String customerGrade; // bronze/silver/gold/vip
  final bool isAppCustomer;
  final int pointsUsed; // 사용된 포인트
  final int? finalFare; // 최종 결제 요금 (할인 적용 후)
  final int discountAmount; // 할인 금액

  const CustomerCall({
    required this.id,
    required this.phoneNumber,
    required this.officeId,
    this.regionId = 'seoul',
    required this.currentLocation,
    required this.destinationLocation,
    required this.timestamp,
    this.status = CallState.requested,
    this.driverId,
    this.estimatedArrivalTime,
    this.fare,
    this.notes,
    this.createdFrom = 'customer_app',
    this.customerId,
    this.customerName,
    this.customerGrade = 'bronze',
    this.isAppCustomer = true,
    this.pointsUsed = 0,
    this.finalFare,
    this.discountAmount = 0,
  });

  /// Firestore에서 변환
  factory CustomerCall.fromFirestore(Map<String, dynamic> data) {
    // timestamp 처리
    DateTime timestamp;
    final ts = data['timestamp'];
    if (ts is Timestamp) {
      timestamp = ts.toDate();
    } else if (ts is int) {
      timestamp = DateTime.fromMillisecondsSinceEpoch(ts);
    } else {
      timestamp = DateTime.now();
    }

    return CustomerCall(
      id: data['id'] as String? ?? '',
      phoneNumber: data['phoneNumber'] as String? ?? '',
      officeId: data['officeId'] as String? ?? '',
      regionId: data['regionId'] as String? ?? 'seoul',
      currentLocation: (data['customerAddress'] as String?) ??
          (data['departure'] as String?) ??
          '',
      destinationLocation: data['destination'] as String? ?? '',
      timestamp: timestamp,
      status: CallState.fromString(data['status'] as String? ?? 'REQUESTED'),
      driverId: data['assignedDriverId'] as String?,
      estimatedArrivalTime: (data['estimatedArrivalTime'] as num?)?.toInt(),
      fare: (data['fare'] as num?)?.toInt(),
      notes: data['notes'] as String?,
      createdFrom: data['createdFrom'] as String? ?? 'customer_app',
      customerId: data['customerId'] as String?,
      customerName: data['customerName'] as String?,
      customerGrade: data['customerGrade'] as String? ?? 'bronze',
      isAppCustomer: data['isAppCustomer'] as bool? ?? true,
      pointsUsed: (data['pointsUsed'] as num?)?.toInt() ?? 0,
      finalFare: (data['finalFare'] as num?)?.toInt(),
      discountAmount: (data['discountAmount'] as num?)?.toInt() ?? 0,
    );
  }

  /// Firestore로 변환
  Map<String, dynamic> toFirestore() {
    return {
      'id': id,
      'phoneNumber': phoneNumber,
      'officeId': officeId,
      'regionId': regionId,
      'customerAddress': currentLocation, // 콜매니저 필드명
      'departure': currentLocation,
      'destination': destinationLocation,
      'timestamp': Timestamp.fromDate(timestamp),
      'status': status.value,
      'assignedDriverId': driverId,
      'estimatedArrivalTime': estimatedArrivalTime,
      'fare': fare,
      'notes': notes,
      'createdFrom': createdFrom,
      'customerId': customerId,
      'customerName': customerName,
      'customerGrade': customerGrade,
      'isAppCustomer': isAppCustomer,
      'pointsUsed': pointsUsed,
      'finalFare': finalFare,
      'discountAmount': discountAmount,
    };
  }

  /// copyWith
  CustomerCall copyWith({
    String? id,
    String? phoneNumber,
    String? officeId,
    String? regionId,
    String? currentLocation,
    String? destinationLocation,
    DateTime? timestamp,
    CallState? status,
    String? driverId,
    int? estimatedArrivalTime,
    int? fare,
    String? notes,
    String? createdFrom,
    String? customerId,
    String? customerName,
    String? customerGrade,
    bool? isAppCustomer,
    int? pointsUsed,
    int? finalFare,
    int? discountAmount,
  }) {
    return CustomerCall(
      id: id ?? this.id,
      phoneNumber: phoneNumber ?? this.phoneNumber,
      officeId: officeId ?? this.officeId,
      regionId: regionId ?? this.regionId,
      currentLocation: currentLocation ?? this.currentLocation,
      destinationLocation: destinationLocation ?? this.destinationLocation,
      timestamp: timestamp ?? this.timestamp,
      status: status ?? this.status,
      driverId: driverId ?? this.driverId,
      estimatedArrivalTime: estimatedArrivalTime ?? this.estimatedArrivalTime,
      fare: fare ?? this.fare,
      notes: notes ?? this.notes,
      createdFrom: createdFrom ?? this.createdFrom,
      customerId: customerId ?? this.customerId,
      customerName: customerName ?? this.customerName,
      customerGrade: customerGrade ?? this.customerGrade,
      isAppCustomer: isAppCustomer ?? this.isAppCustomer,
      pointsUsed: pointsUsed ?? this.pointsUsed,
      finalFare: finalFare ?? this.finalFare,
      discountAmount: discountAmount ?? this.discountAmount,
    );
  }
}
