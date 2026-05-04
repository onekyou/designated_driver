import 'package:freezed_annotation/freezed_annotation.dart';

part 'auth_ui_state.freezed.dart';

/// AuthNotifier UiState (식당앱 P0).
///
/// Plan 결정 #8: 익명 인증 + 식당 ID 매핑. Phone Auth X.
@freezed
class AuthUiState with _$AuthUiState {
  const factory AuthUiState({
    @Default(false) bool isLoading,
    String? error,
    @Default(false) bool isAnonymouslySignedIn,
    String? anonymousUid,
  }) = _AuthUiState;
}
