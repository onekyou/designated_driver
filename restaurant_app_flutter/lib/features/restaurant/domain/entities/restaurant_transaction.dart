import 'package:freezed_annotation/freezed_annotation.dart';

import '../../../../core/domain/converters/timestamp_converter.dart';

part 'restaurant_transaction.freezed.dart';
part 'restaurant_transaction.g.dart';

/// `restaurants/{rid}/transactions/{txId}` doc 모델.
///
/// PR 1 `processRestaurantCallPayout` (functions/src/handlers/points.ts) 가 작성:
/// - type: "EARN_FROM_CALL" (CASH 결제 운행 완료, +1,000)
/// - type: "PAYMENT_AND_EARN" (RESTAURANT_POINT 결제 운행 완료, -fare + 1,000)
/// 멱등성 키: `call_${sharedCallId}`
@freezed
class RestaurantTransaction with _$RestaurantTransaction {
  const factory RestaurantTransaction({
    @Default('') String type,
    @Default(0) int amount,
    @Default(0) int pointsAfter,
    @Default('') String description,
    String? callId,
    String? paymentMethod,
    @TimestampConverter() DateTime? timestamp,
    @Default('') String createdBy,
  }) = _RestaurantTransaction;

  factory RestaurantTransaction.fromJson(Map<String, dynamic> json) =>
      _$RestaurantTransactionFromJson(json);
}
