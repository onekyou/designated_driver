import 'package:json_annotation/json_annotation.dart';

import '../enums/transaction_type.dart';

/// `TransactionType` ↔ `String` 변환.
///
/// 미매칭 문자열은 `TransactionType.earn` fallback (PointTransaction.kt `valueOf` 기본값 일치).
class TransactionTypeConverter
    implements JsonConverter<TransactionType, String> {
  const TransactionTypeConverter();

  @override
  TransactionType fromJson(String json) => TransactionType.fromString(json);

  @override
  String toJson(TransactionType object) => object.toJson();
}
