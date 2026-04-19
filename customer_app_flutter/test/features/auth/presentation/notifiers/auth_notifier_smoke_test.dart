import 'package:customer_app_flutter/core/providers.dart';
import 'package:customer_app_flutter/features/auth/presentation/notifiers/auth_notifier.dart';
import 'package:customer_app_flutter/features/auth/presentation/state/auth_ui_state.dart';
import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:firebase_auth_mocks/firebase_auth_mocks.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class _MockFirebaseMessaging extends Mock implements FirebaseMessaging {}

ProviderContainer _makeContainer() {
  final mockAuth = MockFirebaseAuth();
  final fakeFirestore = FakeFirebaseFirestore();
  final mockMessaging = _MockFirebaseMessaging();

  return ProviderContainer(overrides: [
    firebaseAuthProvider.overrideWithValue(mockAuth),
    firestoreProvider.overrideWithValue(fakeFirestore),
    firebaseMessagingProvider.overrideWithValue(mockMessaging),
  ]);
}

void main() {
  group('AuthNotifier smoke (Chunk 3 껍데기)', () {
    test('provider 배선 + 초기 state 기본값', () {
      final container = _makeContainer();
      addTearDown(container.dispose);

      final state = container.read(authNotifierProvider);
      expect(state, const AuthUiState());
      expect(state.isAnonymouslySignedIn, false);
      expect(state.anonymousUid, null);
      expect(state.phoneNumber, '');
    });

    test('updatePhoneNumber / updateVerificationCode 동작', () {
      final container = _makeContainer();
      addTearDown(container.dispose);

      final notifier = container.read(authNotifierProvider.notifier);
      notifier.updatePhoneNumber('010-1234-5678');
      expect(container.read(authNotifierProvider).phoneNumber, '010-1234-5678');

      notifier.updateVerificationCode('654321');
      expect(container.read(authNotifierProvider).verificationCode, '654321');
    });

    test('Chunk 4 미구현 메서드 UnimplementedError', () async {
      final container = _makeContainer();
      addTearDown(container.dispose);

      final notifier = container.read(authNotifierProvider.notifier);
      expect(
        notifier.sendVerificationCode,
        throwsA(isA<UnimplementedError>()),
      );
      expect(
        notifier.verifyCode,
        throwsA(isA<UnimplementedError>()),
      );
      expect(
        notifier.initialFcmTokenRegistration,
        throwsA(isA<UnimplementedError>()),
      );
    });
  });
}
