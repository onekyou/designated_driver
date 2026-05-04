import 'package:freezed_annotation/freezed_annotation.dart';

part 'restaurant_ids.freezed.dart';

/// 가입 완료 후 SharedPreferences에 저장되는 식별자 묶음.
/// 콜 호출·잔액 stream·FCM 토큰 등록 모두 이 4개 ID를 키로 사용.
@freezed
class RestaurantIds with _$RestaurantIds {
  const factory RestaurantIds({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String restaurantId,
  }) = _RestaurantIds;
}
