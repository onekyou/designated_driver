import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/restaurant_repository.dart';
import '../state/call_ui_state.dart';
import 'restaurant_ids_notifier.dart';

/// 호출 화면 상태 + `createSharedCallFromRestaurant` CF 호출.
/// PR 4: paymentMethod 라디오는 호출 화면에 통합 (현재는 CASH/RESTAURANT_POINT 둘 다 받음, UI는 PR 4).
class CallNotifier extends StateNotifier<CallUiState> {
  CallNotifier({
    required this.ref,
    required this.repository,
  }) : super(const CallUiState());

  final Ref ref;
  final RestaurantRepository repository;

  void setMode(CallSubmitMode mode) => state = state.copyWith(mode: mode);
  void updateDeparture(String v) =>
      state = state.copyWith(departure: v.trim());
  void updateDestination(String v) =>
      state = state.copyWith(destination: v.trim());
  void setPaymentMethod(String v) {
    if (v == 'CASH' || v == 'RESTAURANT_POINT') {
      state = state.copyWith(paymentMethod: v);
    }
  }

  void reset() => state = const CallUiState();

  Future<void> submit() async {
    if (state.isLoading) return;
    final ids = ref.read(restaurantIdsProvider);
    if (ids == null) {
      state = state.copyWith(error: '식당 정보가 없습니다. 다시 가입해주세요.');
      return;
    }

    state = state.copyWith(isLoading: true, error: null);
    try {
      final id = await repository.createCallFromRestaurant(
        restaurantId: ids.restaurantId,
        provinceId: ids.provinceId,
        cityId: ids.cityId,
        officeId: ids.officeId,
        departure: state.mode == CallSubmitMode.app ? state.departure : null,
        destination:
            state.mode == CallSubmitMode.app ? state.destination : null,
        paymentMethod: state.paymentMethod,
      );
      state = state.copyWith(
        isLoading: false,
        isSubmitted: true,
        sharedCallId: id,
      );
      debugPrint('[CallNotifier] 호출 완료: $id');
    } catch (e) {
      state = state.copyWith(isLoading: false, error: '호출 실패: $e');
      debugPrint('[CallNotifier] 실패: $e');
    }
  }
}

final callNotifierProvider =
    StateNotifierProvider.autoDispose<CallNotifier, CallUiState>((ref) {
  return CallNotifier(
    ref: ref,
    repository: ref.watch(restaurantRepositoryProvider),
  );
});
