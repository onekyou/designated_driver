// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'point_transaction.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$PointTransactionImpl _$$PointTransactionImplFromJson(
  Map<String, dynamic> json,
) => _$PointTransactionImpl(
  id: json['id'] as String? ?? '',
  customerId: json['customerId'] as String? ?? '',
  type: json['type'] == null
      ? TransactionType.earn
      : const TransactionTypeConverter().fromJson(json['type'] as String),
  amount: (json['amount'] as num?)?.toInt() ?? 0,
  balance: (json['balance'] as num?)?.toInt() ?? 0,
  description: json['description'] as String? ?? '',
  callId: json['callId'] as String?,
  timestamp: const TimestampConverter().fromJson(json['timestamp']),
  fare: (json['fare'] as num?)?.toInt(),
  grade: json['grade'] as String? ?? 'BRONZE',
);

Map<String, dynamic> _$$PointTransactionImplToJson(
  _$PointTransactionImpl instance,
) => <String, dynamic>{
  'id': instance.id,
  'customerId': instance.customerId,
  'type': const TransactionTypeConverter().toJson(instance.type),
  'amount': instance.amount,
  'balance': instance.balance,
  'description': instance.description,
  'callId': instance.callId,
  'timestamp': const TimestampConverter().toJson(instance.timestamp),
  'fare': instance.fare,
  'grade': instance.grade,
};
