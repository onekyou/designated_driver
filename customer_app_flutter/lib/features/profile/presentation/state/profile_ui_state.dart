import 'package:freezed_annotation/freezed_annotation.dart';

import '../../domain/entities/customer_info.dart';

part 'profile_ui_state.freezed.dart';

/// ProfileNotifier UiState (MODELS.md §7.4)
///
/// provinceId/cityId/officeId 3필드는 NOTIFIERS.md §5.5 _registerFcmToken 경로 구성용
/// — MVP 원본 6필드에서 3 추가. Chunk 4에서 Firestore 로드 시 null→값 transition.
@freezed
class ProfileUiState with _$ProfileUiState {
  const factory ProfileUiState({
    CustomerInfo? customerInfo,
    @Default('') String officeName,
    @Default('') String regionName,
    @Default('') String homeAddress,
    @Default(false) bool showHomeAddressDialog,
    @Default('당신만의 기사가 모십니다') String slogan,
    String? provinceId,
    String? cityId,
    String? officeId,
  }) = _ProfileUiState;
}
