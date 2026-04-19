import 'package:customer_app_flutter/features/auth/presentation/state/auth_ui_state.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AuthUiState', () {
    test('defaults (9 fields)', () {
      const state = AuthUiState();
      expect(state.isLoading, false);
      expect(state.phoneNumber, '');
      expect(state.verificationCode, '');
      expect(state.isCodeSent, false);
      expect(state.error, null);
      expect(state.isVerified, false);
      expect(state.isAnonymouslySignedIn, false);
      expect(state.anonymousUid, null);
      expect(state.verificationId, null);
    });

    test('copyWith: Anonymous Auth 성공', () {
      const state = AuthUiState();
      final signed = state.copyWith(
        isAnonymouslySignedIn: true,
        anonymousUid: 'anon_uid_abc',
      );
      expect(signed.isAnonymouslySignedIn, true);
      expect(signed.anonymousUid, 'anon_uid_abc');
      expect(signed.phoneNumber, '');
      expect(signed.isVerified, false);
    });

    test('copyWith: Phone Auth 코드 전송 → 인증', () {
      const state = AuthUiState();
      final codeSent = state.copyWith(
        isCodeSent: true,
        verificationId: 'verId_xyz',
        phoneNumber: '+821012345678',
      );
      expect(codeSent.isCodeSent, true);
      expect(codeSent.verificationId, 'verId_xyz');
      expect(codeSent.phoneNumber, '+821012345678');

      final verified = codeSent.copyWith(
        verificationCode: '123456',
        isVerified: true,
      );
      expect(verified.isVerified, true);
      expect(verified.verificationCode, '123456');
      expect(verified.verificationId, 'verId_xyz');
    });
  });
}
