import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/providers.dart';
import '../../data/restaurant_repository.dart';
import '../../domain/entities/restaurant_ids.dart';
import '../state/signup_ui_state.dart';
import 'restaurant_ids_notifier.dart';

/// 가입 흐름:
/// 1. 사용자가 코드·식당명·전화·주소·콜택시번호 입력
/// 2. submit() → FCM 토큰 조회 → `redeemRestaurantInviteCode` CF
/// 3. 결과 IDs를 RestaurantIdsNotifier에 저장 → home으로 이동
class SignupNotifier extends StateNotifier<SignupUiState> {
  SignupNotifier({
    required this.ref,
    required this.repository,
    required this.messaging,
  }) : super(const SignupUiState());

  final Ref ref;
  final RestaurantRepository repository;
  final FirebaseMessaging messaging;

  void updateCode(String value) =>
      state = state.copyWith(code: value.trim().toUpperCase());
  void updateProvinceId(String value) =>
      state = state.copyWith(provinceId: value.trim());
  void updateCityId(String value) =>
      state = state.copyWith(cityId: value.trim());
  void updateOfficeId(String value) =>
      state = state.copyWith(officeId: value.trim());
  void updateName(String value) => state = state.copyWith(name: value.trim());
  void updatePhone(String value) => state = state.copyWith(phone: value.trim());
  void updateAddress(String value) =>
      state = state.copyWith(address: value.trim());
  void updateTaxiPhoneNumber(String value) =>
      state = state.copyWith(taxiPhoneNumber: value.trim());

  Future<void> submit() async {
    if (state.isLoading) return;
    if (state.code.isEmpty ||
        state.provinceId.isEmpty ||
        state.cityId.isEmpty ||
        state.officeId.isEmpty ||
        state.name.isEmpty ||
        state.phone.isEmpty) {
      state = state.copyWith(error: '필수 항목을 입력해주세요.');
      return;
    }

    state = state.copyWith(isLoading: true, error: null);

    try {
      final fcmToken = await messaging.getToken();
      final result = await repository.redeemInviteCode(
        code: state.code,
        provinceId: state.provinceId,
        cityId: state.cityId,
        officeId: state.officeId,
        name: state.name,
        phone: state.phone,
        address: state.address,
        taxiPhoneNumber: state.taxiPhoneNumber,
        fcmToken: fcmToken ?? '',
      );

      await ref.read(restaurantIdsProvider.notifier).save(
            RestaurantIds(
              provinceId: result.provinceId,
              cityId: result.cityId,
              officeId: result.officeId,
              restaurantId: result.restaurantId,
            ),
          );

      state = state.copyWith(
        isLoading: false,
        isCompleted: true,
        restaurantId: result.restaurantId,
      );
      debugPrint('[SignupNotifier] 가입 완료: ${result.restaurantId}');
    } catch (e) {
      state = state.copyWith(isLoading: false, error: '가입 실패: $e');
      debugPrint('[SignupNotifier] 실패: $e');
    }
  }
}

final signupNotifierProvider =
    StateNotifierProvider.autoDispose<SignupNotifier, SignupUiState>((ref) {
  return SignupNotifier(
    ref: ref,
    repository: ref.watch(restaurantRepositoryProvider),
    messaging: ref.watch(firebaseMessagingProvider),
  );
});
