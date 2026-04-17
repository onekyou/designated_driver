import 'package:flutter_test/flutter_test.dart';
import 'package:driver_app_flutter/core/utils/fcm_token_payload.dart';
import 'package:driver_app_flutter/core/constants/app_constants.dart';

void main() {
  group('buildTokenUpdatePayload', () {
    test('isIos=true → platform/fcmTokenPlatform 모두 "ios"', () {
      final payload = buildTokenUpdatePayload(token: 'tok', isIos: true);
      expect(payload[AppConstants.fieldPlatform], AppConstants.platformIos);
      expect(payload[AppConstants.fieldFcmTokenPlatform],
          AppConstants.platformIos);
      expect(payload[AppConstants.fieldFcmToken], 'tok');
      expect(payload.containsKey('fcmTokenUpdatedAt'), true);
    });

    test('isIos=false → "android"', () {
      final p = buildTokenUpdatePayload(token: 'tok', isIos: false);
      expect(p[AppConstants.fieldPlatform], AppConstants.platformAndroid);
      expect(p[AppConstants.fieldFcmTokenPlatform],
          AppConstants.platformAndroid);
    });

    test('includeUpdatedAt=false → 3필드만', () {
      final p = buildTokenUpdatePayload(
          token: 'tok', isIos: true, includeUpdatedAt: false);
      expect(p.length, 3);
    });
  });
}
