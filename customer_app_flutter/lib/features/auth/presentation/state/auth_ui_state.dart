import 'package:freezed_annotation/freezed_annotation.dart';

part 'auth_ui_state.freezed.dart';

/// AuthNotifier UiState (MODELS.md §7.4, P0 B.1 확정 9필드)
///
/// Anonymous Auth + Phone Auth 통합 (의제 9 linkWithCredential 유지).
@freezed
class AuthUiState with _$AuthUiState {
  const factory AuthUiState({
    @Default(false) bool isLoading,
    @Default('') String phoneNumber,
    @Default('') String verificationCode,
    @Default(false) bool isCodeSent,
    String? error,
    @Default(false) bool isVerified,
    @Default(false) bool isAnonymouslySignedIn,
    String? anonymousUid,
    String? verificationId,
  }) = _AuthUiState;
}
