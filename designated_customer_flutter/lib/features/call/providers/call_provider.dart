import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/call_model.dart';
import '../models/call_state.dart';
import '../repositories/call_repository.dart';
import '../../auth/providers/auth_provider.dart';
import '../../attribution/providers/attribution_provider.dart';

/// Call Repository Provider
final callRepositoryProvider = Provider<CallRepository>((ref) {
  return CallRepository();
});

/// Call Provider - 콜 요청 및 관리
class CallNotifier extends StateNotifier<AsyncValue<CustomerCall?>> {
  CallNotifier(this._repository, this._ref) : super(const AsyncValue.data(null));

  final CallRepository _repository;
  final Ref _ref;

  /// 콜 요청
  Future<String> requestCall({
    required String currentLocation,
    required String destinationLocation,
    String? notes,
  }) async {
    try {
      // 사용자 정보 가져오기
      final user = _ref.read(authNotifierProvider).value;
      if (user == null || user.phoneNumber == null) {
        throw Exception('사용자 정보가 없습니다. 전화번호를 입력해주세요.');
      }

      // Attribution 정보 가져오기
      final attribution = _ref.read(attributionNotifierProvider).value;
      if (attribution == null) {
        throw Exception('매칭된 사무실이 없습니다. QR 코드를 스캔해주세요.');
      }

      // 콜 생성
      final call = CustomerCall(
        id: '', // Repository에서 생성됨
        phoneNumber: user.phoneNumber!,
        officeId: attribution.officeId,
        regionId: attribution.regionId,
        currentLocation: currentLocation,
        destinationLocation: destinationLocation,
        timestamp: DateTime.now(),
        status: CallState.requested,
        notes: notes,
        customerId: user.uid,
        customerGrade: 'bronze', // 기본 등급
      );

      // Firestore에 저장
      final callId = await _repository.requestCall(
        regionId: attribution.regionId,
        officeId: attribution.officeId,
        call: call,
      );

      // 상태 업데이트
      state = AsyncValue.data(call.copyWith(id: callId));

      return callId;
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
      rethrow;
    }
  }

  /// 콜 취소
  Future<void> cancelCall(String callId) async {
    try {
      final attribution = _ref.read(attributionNotifierProvider).value;
      if (attribution == null) {
        throw Exception('매칭된 사무실이 없습니다.');
      }

      await _repository.cancelCall(
        regionId: attribution.regionId,
        officeId: attribution.officeId,
        callId: callId,
      );

      // 상태 초기화
      state = const AsyncValue.data(null);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
      rethrow;
    }
  }

  /// 활성 콜 조회
  Future<void> loadActiveCall() async {
    try {
      state = const AsyncValue.loading();

      final user = _ref.read(authNotifierProvider).value;
      if (user == null || user.phoneNumber == null) {
        state = const AsyncValue.data(null);
        return;
      }

      final attribution = _ref.read(attributionNotifierProvider).value;
      if (attribution == null) {
        state = const AsyncValue.data(null);
        return;
      }

      final activeCall = await _repository.getActiveCall(
        regionId: attribution.regionId,
        officeId: attribution.officeId,
        phoneNumber: user.phoneNumber!,
      );

      state = AsyncValue.data(activeCall);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  /// 상태 초기화
  void reset() {
    state = const AsyncValue.data(null);
  }
}

/// Call Provider
final callProvider = StateNotifierProvider<CallNotifier, AsyncValue<CustomerCall?>>((ref) {
  final repository = ref.watch(callRepositoryProvider);
  return CallNotifier(repository, ref);
});

/// Call History Provider - 콜 내역 조회
final callHistoryProvider = FutureProvider.autoDispose<List<CustomerCall>>((ref) async {
  final user = ref.watch(authNotifierProvider).value;
  if (user == null || user.phoneNumber == null) {
    return [];
  }

  final attribution = ref.watch(attributionNotifierProvider).value;
  if (attribution == null) {
    return [];
  }

  final repository = ref.watch(callRepositoryProvider);
  return repository.getCallHistory(
    regionId: attribution.regionId,
    officeId: attribution.officeId,
    phoneNumber: user.phoneNumber!,
    limit: 50,
  );
});

/// Call Stream Provider - 실시간 콜 모니터링
final callStreamProvider = StreamProvider.autoDispose<List<CustomerCall>>((ref) {
  final user = ref.watch(authNotifierProvider).value;
  if (user == null || user.phoneNumber == null) {
    return Stream.value([]);
  }

  final attribution = ref.watch(attributionNotifierProvider).value;
  if (attribution == null) {
    return Stream.value([]);
  }

  final repository = ref.watch(callRepositoryProvider);
  return repository.observeCustomerCalls(
    regionId: attribution.regionId,
    officeId: attribution.officeId,
    phoneNumber: user.phoneNumber!,
    limit: 10,
  );
});
