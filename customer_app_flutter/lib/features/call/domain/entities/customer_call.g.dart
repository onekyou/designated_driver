// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'customer_call.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$CustomerCallImpl _$$CustomerCallImplFromJson(Map<String, dynamic> json) =>
    _$CustomerCallImpl(
      id: json['id'] as String? ?? '',
      phoneNumber: json['phoneNumber'] as String,
      officeId: json['officeId'] as String,
      provinceId: json['provinceId'] as String,
      cityId: json['cityId'] as String,
      currentLocation: json['currentLocation'] as String? ?? '',
      destinationLocation: json['destinationLocation'] as String? ?? '',
      timestamp: (json['timestamp'] as num?)?.toInt() ?? 0,
      status: json['status'] as String? ?? 'REQUESTED',
      driverId: json['assignedDriverId'] as String?,
      estimatedArrivalTime: (json['estimatedArrivalTime'] as num?)?.toInt(),
      fare: (json['fare'] as num?)?.toInt(),
      notes: json['notes'] as String?,
      createdFrom: json['createdFrom'] as String? ?? 'customer_app',
      customerId: json['customerId'] as String?,
      customerName: json['customerName'] as String?,
      customerGrade: json['customerGrade'] as String? ?? 'bronze',
      isAppCustomer: json['isAppCustomer'] as bool? ?? true,
      pointsUsed: (json['pointsUsed'] as num?)?.toInt() ?? 0,
      finalFare: (json['finalFare'] as num?)?.toInt(),
      discountAmount: (json['discountAmount'] as num?)?.toInt() ?? 0,
    );

Map<String, dynamic> _$$CustomerCallImplToJson(_$CustomerCallImpl instance) =>
    <String, dynamic>{
      'id': instance.id,
      'phoneNumber': instance.phoneNumber,
      'officeId': instance.officeId,
      'provinceId': instance.provinceId,
      'cityId': instance.cityId,
      'currentLocation': instance.currentLocation,
      'destinationLocation': instance.destinationLocation,
      'timestamp': instance.timestamp,
      'status': instance.status,
      'assignedDriverId': instance.driverId,
      'estimatedArrivalTime': instance.estimatedArrivalTime,
      'fare': instance.fare,
      'notes': instance.notes,
      'createdFrom': instance.createdFrom,
      'customerId': instance.customerId,
      'customerName': instance.customerName,
      'customerGrade': instance.customerGrade,
      'isAppCustomer': instance.isAppCustomer,
      'pointsUsed': instance.pointsUsed,
      'finalFare': instance.finalFare,
      'discountAmount': instance.discountAmount,
    };
