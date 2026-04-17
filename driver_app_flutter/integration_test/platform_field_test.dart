import 'dart:io' show Platform;
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:driver_app_flutter/core/utils/fcm_token_payload.dart';
import 'package:driver_app_flutter/core/constants/app_constants.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('iOS Simulator에서 Platform.isIOS=true이며 payload=ios',
      (tester) async {
    expect(Platform.isIOS, true, reason: 'iOS Simulator 외에서 실행 금지');
    final payload = buildTokenUpdatePayload(token: 'sim-token-test');
    expect(payload[AppConstants.fieldPlatform], AppConstants.platformIos);
    expect(payload[AppConstants.fieldFcmTokenPlatform],
        AppConstants.platformIos);
  });
}
