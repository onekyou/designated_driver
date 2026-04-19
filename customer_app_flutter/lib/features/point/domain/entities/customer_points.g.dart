// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'customer_points.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$CustomerPointsImpl _$$CustomerPointsImplFromJson(Map<String, dynamic> json) =>
    _$CustomerPointsImpl(
      customerId: json['customerId'] as String? ?? '',
      phoneNumber: json['phoneNumber'] as String? ?? '',
      currentPoints: (json['currentPoints'] as num?)?.toInt() ?? 0,
      totalEarned: (json['totalEarned'] as num?)?.toInt() ?? 0,
      totalUsed: (json['totalUsed'] as num?)?.toInt() ?? 0,
      grade: json['grade'] == null
          ? CustomerGrade.bronze
          : const CustomerGradeConverter().fromJson(json['grade'] as String?),
      totalCalls: (json['totalCalls'] as num?)?.toInt() ?? 0,
      lastUpdated: const TimestampConverter().fromJson(json['lastUpdated']),
      createdAt: const TimestampConverter().fromJson(json['createdAt']),
    );

Map<String, dynamic> _$$CustomerPointsImplToJson(
  _$CustomerPointsImpl instance,
) => <String, dynamic>{
  'customerId': instance.customerId,
  'phoneNumber': instance.phoneNumber,
  'currentPoints': instance.currentPoints,
  'totalEarned': instance.totalEarned,
  'totalUsed': instance.totalUsed,
  'grade': const CustomerGradeConverter().toJson(instance.grade),
  'totalCalls': instance.totalCalls,
  'lastUpdated': const TimestampConverter().toJson(instance.lastUpdated),
  'createdAt': const TimestampConverter().toJson(instance.createdAt),
};
