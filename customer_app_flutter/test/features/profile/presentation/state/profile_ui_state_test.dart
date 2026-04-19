import 'package:customer_app_flutter/features/profile/presentation/state/profile_ui_state.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ProfileUiState', () {
    test('defaults', () {
      const state = ProfileUiState();
      expect(state.customerInfo, null);
      expect(state.officeName, '');
      expect(state.regionName, '');
      expect(state.homeAddress, '');
      expect(state.showHomeAddressDialog, false);
      expect(state.slogan, '당신만의 기사가 모십니다');
      expect(state.provinceId, null);
      expect(state.cityId, null);
      expect(state.officeId, null);
    });

    test('copyWith: 사무실 식별자 null → 값 transition (Chunk 4 FCM 경로 구성용)', () {
      const state = ProfileUiState();
      final loaded = state.copyWith(
        provinceId: 'seoul',
        cityId: 'gangnam',
        officeId: 'office_001',
        officeName: '강남 사무실',
      );
      expect(loaded.provinceId, 'seoul');
      expect(loaded.cityId, 'gangnam');
      expect(loaded.officeId, 'office_001');
      expect(loaded.officeName, '강남 사무실');
      expect(loaded.slogan, '당신만의 기사가 모십니다');
    });
  });
}
