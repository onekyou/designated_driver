import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/di/injection.dart';
import '../../../../core/usecases/usecase.dart';
import '../../domain/usecases/get_assigned_call_usecase.dart';
import '../../domain/usecases/accept_call_usecase.dart';
import '../../domain/usecases/reject_call_usecase.dart';
import '../../domain/usecases/complete_call_usecase.dart';
import 'call_state.dart';

class CallNotifier extends StateNotifier<CallState> {
  final GetAssignedCallUseCase _getAssignedCallUseCase;
  final AcceptCallUseCase _acceptCallUseCase;
  final RejectCallUseCase _rejectCallUseCase;
  final CompleteCallUseCase _completeCallUseCase;

  CallNotifier({
    required GetAssignedCallUseCase getAssignedCallUseCase,
    required AcceptCallUseCase acceptCallUseCase,
    required RejectCallUseCase rejectCallUseCase,
    required CompleteCallUseCase completeCallUseCase,
  })  : _getAssignedCallUseCase = getAssignedCallUseCase,
        _acceptCallUseCase = acceptCallUseCase,
        _rejectCallUseCase = rejectCallUseCase,
        _completeCallUseCase = completeCallUseCase,
        super(const CallState.initial());

  /// 배정된 콜 조회
  Future<void> fetchAssignedCall() async {
    state = const CallState.loading();

    final result = await _getAssignedCallUseCase(NoParams());

    result.fold(
      (failure) => state = CallState.error(failure.message),
      (call) => state = CallState.loaded(call),
    );
  }

  /// 콜 수락
  Future<void> acceptCall(String callId) async {
    state = const CallState.loading();

    final result = await _acceptCallUseCase(AcceptCallParams(callId: callId));

    result.fold(
      (failure) => state = CallState.error(failure.message),
      (call) => state = CallState.loaded(call),
    );
  }

  /// 콜 거절
  Future<void> rejectCall(String callId, String reason) async {
    state = const CallState.loading();

    final result = await _rejectCallUseCase(
      RejectCallParams(callId: callId, reason: reason),
    );

    result.fold(
      (failure) => state = CallState.error(failure.message),
      (_) => state = const CallState.loaded(null),
    );
  }

  /// 콜 완료
  Future<void> completeCall(String callId) async {
    state = const CallState.loading();

    final result = await _completeCallUseCase(
      CompleteCallParams(callId: callId),
    );

    result.fold(
      (failure) => state = CallState.error(failure.message),
      (call) => state = CallState.loaded(call),
    );
  }

  /// 상태 초기화
  void reset() {
    state = const CallState.initial();
  }
}

/// CallNotifier Provider
final callNotifierProvider =
    StateNotifierProvider<CallNotifier, CallState>((ref) {
  return CallNotifier(
    getAssignedCallUseCase: sl(),
    acceptCallUseCase: sl(),
    rejectCallUseCase: sl(),
    completeCallUseCase: sl(),
  );
});

/// Current Call Provider (편의성)
final currentCallProvider = Provider((ref) {
  final callState = ref.watch(callNotifierProvider);
  return callState.maybeWhen(
    loaded: (assignedCall) => assignedCall,
    orElse: () => null,
  );
});
