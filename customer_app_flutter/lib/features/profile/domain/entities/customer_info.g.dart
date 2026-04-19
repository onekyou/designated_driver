// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'customer_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$CustomerInfoImpl _$$CustomerInfoImplFromJson(Map<String, dynamic> json) =>
    _$CustomerInfoImpl(
      id: json['id'] as String? ?? '',
      phoneNumber: json['phoneNumber'] as String? ?? '',
      name: json['name'] as String? ?? '',
      grade: json['grade'] as String? ?? 'bronze',
      points: (json['points'] as num?)?.toInt() ?? 0,
      totalRides: (json['totalRides'] as num?)?.toInt() ?? 0,
      totalSpent: (json['totalSpent'] as num?)?.toInt() ?? 0,
      linkedOfficeId: json['linkedOfficeId'] as String? ?? '',
      primaryOfficeId: json['primaryOfficeId'] as String? ?? '',
      attributionScore: (json['attributionScore'] as num?)?.toInt(),
      attributionSource: json['attributionSource'] as String?,
      registeredAt: const TimestampConverter().fromJson(json['registeredAt']),
      lastRideAt: const TimestampConverter().fromJson(json['lastRideAt']),
      officePhone: json['officePhone'] as String? ?? '',
      bankName: json['bankName'] as String? ?? '',
      accountNumber: json['accountNumber'] as String? ?? '',
      accountHolder: json['accountHolder'] as String? ?? '',
      homeAddress: json['homeAddress'] as String? ?? '',
    );

Map<String, dynamic> _$$CustomerInfoImplToJson(_$CustomerInfoImpl instance) =>
    <String, dynamic>{
      'id': instance.id,
      'phoneNumber': instance.phoneNumber,
      'name': instance.name,
      'grade': instance.grade,
      'points': instance.points,
      'totalRides': instance.totalRides,
      'totalSpent': instance.totalSpent,
      'linkedOfficeId': instance.linkedOfficeId,
      'primaryOfficeId': instance.primaryOfficeId,
      'attributionScore': instance.attributionScore,
      'attributionSource': instance.attributionSource,
      'registeredAt': const TimestampConverter().toJson(instance.registeredAt),
      'lastRideAt': const TimestampConverter().toJson(instance.lastRideAt),
      'officePhone': instance.officePhone,
      'bankName': instance.bankName,
      'accountNumber': instance.accountNumber,
      'accountHolder': instance.accountHolder,
      'homeAddress': instance.homeAddress,
    };
