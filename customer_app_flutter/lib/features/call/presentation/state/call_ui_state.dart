import 'package:freezed_annotation/freezed_annotation.dart';

part 'call_ui_state.freezed.dart';

/// CallNotifier UiState (MODELS.md §7.4)
///
/// 주의: `callStatus` (CallStatus / DriverInfo Freezed) 는 Chunk 4 에서
/// CallNotifier 착수 시 필드 + 모델 동시 추가 예정. 현 Chunk 3 껍데기는 제외.
@freezed
class CallUiState with _$CallUiState {
  const factory CallUiState({
    @Default('') String currentLocation,
    @Default('') String destinationLocation,
    @Default(false) bool isLoadingLocation,
    @Default(false) bool isLoadingCall,
    String? error,
    @Default(false) bool showLocationCard,
  }) = _CallUiState;
}
