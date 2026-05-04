import 'package:freezed_annotation/freezed_annotation.dart';

import '../../../../core/domain/converters/timestamp_converter.dart';

part 'restaurant_info.freezed.dart';
part 'restaurant_info.g.dart';

/// `provinces/{p}/cities/{c}/offices/{registeredOfficeId}/restaurants/{restaurantId}` doc 모델.
///
/// PR 1 `redeemRestaurantInviteCode` (functions/src/handlers/restaurant.ts) 가 작성하는 필드 셋과 정합.
/// `acquiredBy` = "PLATFORM" (P0) | officeId (P1+ 귀속 모드).
@freezed
class RestaurantInfo with _$RestaurantInfo {
  const factory RestaurantInfo({
    @Default('') String name,
    @Default('') String phone,
    @Default('') String address,
    @Default('') String taxiPhoneNumber,
    @Default(0) int points,
    @Default('') String ownerUid,
    @Default('') String fcmToken,
    @Default('') String fcmTokenPlatform,
    @Default('') String registeredOfficeId,
    @Default('PLATFORM') String acquiredBy,
    @TimestampConverter() DateTime? createdAt,
    @TimestampConverter() DateTime? fcmTokenUpdatedAt,
  }) = _RestaurantInfo;

  factory RestaurantInfo.fromJson(Map<String, dynamic> json) =>
      _$RestaurantInfoFromJson(json);
}
