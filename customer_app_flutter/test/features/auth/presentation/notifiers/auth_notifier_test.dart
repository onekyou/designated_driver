import 'package:customer_app_flutter/core/providers.dart';
import 'package:customer_app_flutter/features/auth/presentation/notifiers/auth_notifier.dart';
import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_auth_mocks/firebase_auth_mocks.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mock_exceptions/mock_exceptions.dart';
import 'package:mocktail/mocktail.dart';

class _MockFirebaseMessaging extends Mock implements FirebaseMessaging {}

_MockFirebaseMessaging _buildMessagingMock() {
  final m = _MockFirebaseMessaging();
  when(() => m.onTokenRefresh).thenAnswer((_) => const Stream<String>.empty());
  when(() => m.getToken()).thenAnswer((_) async => null);
  return m;
}

ProviderContainer _makeContainer({
  MockFirebaseAuth? auth,
  FakeFirebaseFirestore? firestore,
  FirebaseMessaging? messaging,
}) {
  final authInst = auth ?? MockFirebaseAuth();
  final firestoreInst = firestore ?? FakeFirebaseFirestore();
  final messagingInst = messaging ?? _buildMessagingMock();

  return ProviderContainer(overrides: [
    firebaseAuthProvider.overrideWithValue(authInst),
    firestoreProvider.overrideWithValue(firestoreInst),
    firebaseMessagingProvider.overrideWithValue(messagingInst),
  ]);
}

/// 생성자에서 `_initAnonymousAuth()` 가 async → 완료 대기.
/// `await Future<void>.delayed(Duration.zero)` 로 마이크로태스크 큐 flush.
Future<void> _waitInit() async {
  await Future<void>.delayed(Duration.zero);
  await Future<void>.delayed(Duration.zero);
}

void main() {
  group('AuthNotifier — Anonymous Auth', () {
    test('1. currentUser 존재 시 상속 (signInAnonymously 호출 안 함)', () async {
      final mockUser = MockUser(uid: 'existing_uid', isAnonymous: true);
      final auth = MockFirebaseAuth(signedIn: true, mockUser: mockUser);

      final container = _makeContainer(auth: auth);
      addTearDown(container.dispose);

      container.read(authNotifierProvider.notifier);
      await _waitInit();

      final state = container.read(authNotifierProvider);
      expect(state.isAnonymouslySignedIn, true);
      expect(state.anonymousUid, 'existing_uid');
    });

    test('2. currentUser null → signInAnonymously 자동 실행', () async {
      final auth = MockFirebaseAuth();
      final container = _makeContainer(auth: auth);
      addTearDown(container.dispose);

      container.read(authNotifierProvider.notifier);
      await _waitInit();

      final state = container.read(authNotifierProvider);
      expect(state.isAnonymouslySignedIn, true);
      expect(state.anonymousUid, isNotNull);
    });

    test('3. signInAnonymously 실패 시 error state', () async {
      final auth = MockFirebaseAuth();
      whenCalling(Invocation.method(#signInAnonymously, null))
          .on(auth)
          .thenThrow(FirebaseAuthException(
            code: 'network-request-failed',
            message: 'Network error',
          ));

      final container = _makeContainer(auth: auth);
      addTearDown(container.dispose);

      container.read(authNotifierProvider.notifier);
      await _waitInit();

      final state = container.read(authNotifierProvider);
      expect(state.isAnonymouslySignedIn, false);
      expect(state.error, contains('Anonymous 인증 실패'));
    });
  });

  group('AuthNotifier — Phone Auth', () {
    test('4. sendVerificationCode 빈 전화번호 → error', () async {
      final auth = MockFirebaseAuth();
      final container = _makeContainer(auth: auth);
      addTearDown(container.dispose);
      final notifier = container.read(authNotifierProvider.notifier);
      await _waitInit();

      await notifier.sendVerificationCode();

      expect(
        container.read(authNotifierProvider).error,
        contains('전화번호'),
      );
    });

    test('5. sendVerificationCode 성공 → isCodeSent + verificationId', () async {
      final auth = MockFirebaseAuth();
      final container = _makeContainer(auth: auth);
      addTearDown(container.dispose);
      final notifier = container.read(authNotifierProvider.notifier);
      await _waitInit();

      notifier.updatePhoneNumber('010-1234-5678');
      await notifier.sendVerificationCode();

      final state = container.read(authNotifierProvider);
      expect(state.isCodeSent, true);
      expect(state.verificationId, 'verification-id');
      expect(state.isLoading, false);
    });

    // ⚠️ 알려진 제약: firebase_auth_mocks 0.14.2 의 MockUser.linkWithCredential
    //    은 내부 MockUserCredential(false, mockUser: anonymous_this) 에서
    //    assert 실패 (https://github.com/atn832/fake_cloud_firestore issue 유사).
    //    → Anonymous linkWithCredential happy path 는 단위 테스트 불가.
    //    우회: 비-익명 currentUser 시나리오로 signInWithCredential 경로 검증.

    test('6. verifyCode (non-anonymous currentUser) → signInWithCredential 성공', () async {
      final mockUser = MockUser(
        uid: 'user_uid',
        isAnonymous: false,
        phoneNumber: '+821012345678',
      );
      final auth = MockFirebaseAuth(signedIn: true, mockUser: mockUser);
      final container = _makeContainer(auth: auth);
      addTearDown(container.dispose);
      final notifier = container.read(authNotifierProvider.notifier);
      await _waitInit();

      notifier.updatePhoneNumber('010-1234-5678');
      await notifier.sendVerificationCode();
      notifier.updateVerificationCode('123456');
      await notifier.verifyCode();

      final state = container.read(authNotifierProvider);
      expect(state.isVerified, true);
      expect(state.anonymousUid, 'user_uid');
      expect(state.phoneNumber, '+821012345678');
    });

    test('7. verifyCode — verificationId 없을 때 error', () async {
      final auth = MockFirebaseAuth();
      final container = _makeContainer(auth: auth);
      addTearDown(container.dispose);
      final notifier = container.read(authNotifierProvider.notifier);
      await _waitInit();

      // sendVerificationCode 건너뛰고 바로 verifyCode
      notifier.updateVerificationCode('123456');
      await notifier.verifyCode();

      expect(
        container.read(authNotifierProvider).error,
        contains('인증 세션'),
      );
    });

    test('8. verifyCode 빈 코드 → error', () async {
      final auth = MockFirebaseAuth();
      final container = _makeContainer(auth: auth);
      addTearDown(container.dispose);
      final notifier = container.read(authNotifierProvider.notifier);
      await _waitInit();

      await notifier.verifyCode();

      expect(
        container.read(authNotifierProvider).error,
        contains('인증 코드'),
      );
    });
  });

  group('AuthNotifier — FCM 토큰', () {
    test('9. _registerFcmToken 은 phone 없으면 스킵 (Firestore 접근 0회)', () async {
      final firestore = FakeFirebaseFirestore();
      final container = _makeContainer(firestore: firestore);
      addTearDown(container.dispose);
      final notifier = container.read(authNotifierProvider.notifier);
      await _waitInit();

      // initialFcmTokenRegistration 호출 — messaging.getToken()은 null 반환
      await notifier.initialFcmTokenRegistration();

      // FakeFirestore 에 아무 쓰기 없음
      final snap = await firestore.collection('provinces').get();
      expect(snap.docs, isEmpty);
    });

    test('10. getToken 성공 + phone 있어도 Chunk 4는 저장 안 함 (TODO 주석)', () async {
      final mockUser = MockUser(uid: 'anon_uid', isAnonymous: true);
      final auth = MockFirebaseAuth(signedIn: true, mockUser: mockUser);
      final firestore = FakeFirebaseFirestore();
      final messaging = _MockFirebaseMessaging();
      when(() => messaging.onTokenRefresh)
          .thenAnswer((_) => const Stream<String>.empty());
      when(() => messaging.getToken()).thenAnswer((_) async => 'fake_token');

      final container = _makeContainer(
        auth: auth,
        firestore: firestore,
        messaging: messaging,
      );
      addTearDown(container.dispose);
      final notifier = container.read(authNotifierProvider.notifier);
      await _waitInit();

      notifier.updatePhoneNumber('+821012345678');
      await notifier.initialFcmTokenRegistration();

      // Chunk 4: 저장 TODO 블록 비활성화 상태 → FakeFirestore empty
      final snap = await firestore.collection('provinces').get();
      expect(snap.docs, isEmpty);
    });
  });

  group('AuthNotifier — utility', () {
    test('11. updatePhoneNumber / updateVerificationCode', () async {
      final container = _makeContainer();
      addTearDown(container.dispose);
      final notifier = container.read(authNotifierProvider.notifier);
      await _waitInit();

      notifier.updatePhoneNumber('010-1111-2222');
      expect(
        container.read(authNotifierProvider).phoneNumber,
        '010-1111-2222',
      );

      notifier.updateVerificationCode('999999');
      expect(
        container.read(authNotifierProvider).verificationCode,
        '999999',
      );
    });
  });
}
