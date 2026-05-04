import 'package:freezed_annotation/freezed_annotation.dart';

part 'call_ui_state.freezed.dart';

/// 호출 모드.
/// - simple: 식당 주소 자동 (출발·목적지 입력 X) → CF 측에서 식당 주소 채움
/// - app: 출발·목적지 직접 입력
enum CallSubmitMode { simple, app }

@freezed
class CallUiState with _$CallUiState {
  const factory CallUiState({
    @Default(CallSubmitMode.simple) CallSubmitMode mode,
    @Default('') String departure,
    @Default('') String destination,
    /// 'CASH' (default) | 'RESTAURANT_POINT'
    @Default('CASH') String paymentMethod,
    @Default(false) bool isLoading,
    @Default(false) bool isSubmitted,
    String? sharedCallId,
    String? error,
  }) = _CallUiState;
}
