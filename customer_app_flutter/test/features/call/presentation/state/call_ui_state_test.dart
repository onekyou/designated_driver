import 'package:customer_app_flutter/features/call/presentation/state/call_ui_state.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CallUiState', () {
    test('defaults', () {
      const state = CallUiState();
      expect(state.currentLocation, '');
      expect(state.destinationLocation, '');
      expect(state.isLoadingLocation, false);
      expect(state.isLoadingCall, false);
      expect(state.error, null);
      expect(state.showLocationCard, false);
    });

    test('copyWith', () {
      const state = CallUiState();
      final updated = state.copyWith(
        currentLocation: '서울역',
        isLoadingLocation: true,
      );
      expect(updated.currentLocation, '서울역');
      expect(updated.isLoadingLocation, true);
      expect(updated.destinationLocation, '');
    });
  });
}
