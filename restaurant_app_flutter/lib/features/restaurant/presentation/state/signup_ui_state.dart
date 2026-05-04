import 'package:freezed_annotation/freezed_annotation.dart';

part 'signup_ui_state.freezed.dart';

/// SignupNotifier UiState.
///
/// Plan §6 PR 2 작업 list 5: 가입 코드 입력 → `redeemRestaurantInviteCode` CF →
/// SharedPreferences (provinceId, cityId, officeId, restaurantId) 저장.
@freezed
class SignupUiState with _$SignupUiState {
  const factory SignupUiState({
    @Default('') String code,
    @Default('') String provinceId,
    @Default('') String cityId,
    @Default('') String officeId,
    @Default('') String name,
    @Default('') String phone,
    @Default('') String address,
    @Default('') String taxiPhoneNumber,
    @Default(false) bool isLoading,
    @Default(false) bool isCompleted,
    String? error,
    String? restaurantId,
  }) = _SignupUiState;
}
