import 'package:customer_app_flutter/features/point/presentation/state/point_ui_state.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('PointUiState', () {
    test('defaults', () {
      const state = PointUiState();
      expect(state.customerPoints, null);
      expect(state.isLoadingPoints, false);
      expect(state.usePoints, false);
      expect(state.pointsToUse, 0);
      expect(state.showPointsEarnedDialog, false);
      expect(state.earnedPoints, 0);
      expect(state.usedPoints, 0);
      expect(state.rideCompletedFare, 0);
    });

    test('copyWith', () {
      const state = PointUiState();
      final updated = state.copyWith(
        usePoints: true,
        pointsToUse: 500,
        earnedPoints: 100,
      );
      expect(updated.usePoints, true);
      expect(updated.pointsToUse, 500);
      expect(updated.earnedPoints, 100);
      expect(updated.isLoadingPoints, false);
    });
  });
}
