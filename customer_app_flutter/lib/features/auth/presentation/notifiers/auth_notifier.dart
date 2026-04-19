import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/providers.dart';
import '../state/auth_ui_state.dart';
import 'phone_formatter.dart';

/// Anonymous Auth 자동 로그인 + Phone Auth linkWithCredential + FCM 토큰 등록.
///
/// Kotlin 원본: MainActivity.kt:75-80 / 397-411 + PhoneAuthViewModel.kt (140 LOC).
/// 의제 9 핵심: `linkWithCredential` 사용으로 Anonymous uid 유지 (Kotlin `signInWithCredential` 대비 개선).
class AuthNotifier extends StateNotifier<AuthUiState> {
  AuthNotifier({
    required this.ref,
    required this.auth,
    required this.messaging,
    required this.firestore,
  }) : super(const AuthUiState()) {
    _initAnonymousAuth();
    _subscribeFcmToken();
  }

  final Ref ref;
  final FirebaseAuth auth;
  final FirebaseMessaging messaging;
  final FirebaseFirestore firestore;

  String? _verificationId;
  StreamSubscription<String>? _tokenRefreshSub;

  // ───── Anonymous Auth ─────

  Future<void> _initAnonymousAuth() async {
    final currentUser = auth.currentUser;
    if (currentUser != null) {
      state = state.copyWith(
        isAnonymouslySignedIn: true,
        anonymousUid: currentUser.uid,
        phoneNumber: currentUser.phoneNumber ?? '',
      );
      debugPrint('[AuthNotifier] currentUser 상속: ${currentUser.uid}');
      return;
    }

    try {
      final credential = await auth.signInAnonymously();
      final uid = credential.user?.uid;
      if (uid != null) {
        state = state.copyWith(
          isAnonymouslySignedIn: true,
          anonymousUid: uid,
        );
        debugPrint('[AuthNotifier] Anonymous signIn 완료: $uid');
      }
    } catch (e) {
      debugPrint('[AuthNotifier] signInAnonymously 실패: $e');
      state = state.copyWith(error: 'Anonymous 인증 실패: $e');
    }
  }

  // ───── Phone Auth ─────

  void updatePhoneNumber(String value) {
    state = state.copyWith(phoneNumber: value);
  }

  void updateVerificationCode(String value) {
    state = state.copyWith(verificationCode: value);
  }

  Future<void> sendVerificationCode() async {
    if (state.phoneNumber.isEmpty) {
      state = state.copyWith(error: '전화번호를 입력해주세요');
      return;
    }
    state = state.copyWith(isLoading: true, error: null);

    final formatted = formatKoreanPhoneNumber(state.phoneNumber);

    await auth.verifyPhoneNumber(
      phoneNumber: formatted,
      timeout: const Duration(seconds: 60),
      verificationCompleted: (PhoneAuthCredential credential) async {
        await _signInWithCredential(credential);
      },
      verificationFailed: (FirebaseAuthException e) {
        state = state.copyWith(
          isLoading: false,
          error: '인증 실패: ${e.message}',
        );
      },
      codeSent: (String verificationId, int? resendToken) {
        _verificationId = verificationId;
        state = state.copyWith(
          isLoading: false,
          isCodeSent: true,
          verificationId: verificationId,
        );
      },
      codeAutoRetrievalTimeout: (String verificationId) {
        _verificationId = verificationId;
      },
    );
  }

  Future<void> verifyCode() async {
    if (state.verificationCode.isEmpty) {
      state = state.copyWith(error: '인증 코드를 입력해주세요');
      return;
    }
    final verId = _verificationId ?? state.verificationId;
    if (verId == null) {
      state = state.copyWith(error: '인증 세션이 만료되었습니다. 다시 시도해주세요');
      return;
    }

    state = state.copyWith(isLoading: true, error: null);
    try {
      final credential = PhoneAuthProvider.credential(
        verificationId: verId,
        smsCode: state.verificationCode,
      );
      await _signInWithCredential(credential);
    } catch (e) {
      state = state.copyWith(isLoading: false, error: '인증 실패: $e');
    }
  }

  Future<void> _signInWithCredential(PhoneAuthCredential credential) async {
    try {
      final user = auth.currentUser;
      late final UserCredential result;

      if (user?.isAnonymous == true) {
        // 의제 9 핵심: Anonymous uid 유지 + Phone credential 병합
        result = await user!.linkWithCredential(credential);
      } else {
        result = await auth.signInWithCredential(credential);
      }

      final signedInUser = result.user;
      if (signedInUser != null) {
        state = state.copyWith(
          isLoading: false,
          isVerified: true,
          phoneNumber: signedInUser.phoneNumber ??
              formatKoreanPhoneNumber(state.phoneNumber),
          anonymousUid: signedInUser.uid,
        );
      }
    } on FirebaseAuthException catch (e) {
      if (e.code == 'credential-already-in-use') {
        await _handleCredentialAlreadyInUse(credential);
      } else {
        state = state.copyWith(
          isLoading: false,
          error: '인증 실패: ${e.message}',
        );
      }
    }
  }

  Future<void> _handleCredentialAlreadyInUse(
      PhoneAuthCredential credential) async {
    try {
      final result = await auth.signInWithCredential(credential);
      final user = result.user;
      if (user != null) {
        state = state.copyWith(
          isLoading: false,
          isVerified: true,
          phoneNumber: user.phoneNumber ??
              formatKoreanPhoneNumber(state.phoneNumber),
          anonymousUid: user.uid,
        );
        debugPrint(
            '[_handleCredentialAlreadyInUse] 기존 uid 로그인: ${user.uid}');
      }
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        error: '이전 계정 복구 실패: $e',
      );
    }
  }

  // ───── FCM 토큰 ─────

  void _subscribeFcmToken() {
    _tokenRefreshSub = messaging.onTokenRefresh.listen((token) {
      _registerFcmToken(token);
    });
  }

  /// 앱 시작 시 초기 FCM 토큰 조회 + 등록.
  /// SplashScreen._bootstrap / ProfileSetupScreen._onSubmit 에서 호출.
  Future<void> initialFcmTokenRegistration() async {
    try {
      final token = await messaging.getToken();
      if (token != null) {
        await _registerFcmToken(token);
      }
    } catch (e) {
      debugPrint('[initialFcmTokenRegistration] 실패: $e');
    }
  }

  /// Firestore `customerInfo/{phone}` 에 토큰 저장.
  ///
  /// Chunk 4 한정: phone 체크까지만. 사무실 정보(provinceId/cityId/officeId)는
  /// Chunk 5 ProfileNotifier 완성 후 ref.read 로 읽어서 실제 저장 블록 활성화.
  Future<void> _registerFcmToken(String token) async {
    final phone = state.phoneNumber;
    if (phone.isEmpty) {
      debugPrint('[_registerFcmToken] phoneNumber 없음, 스킵');
      return;
    }

    // TODO(chunk5): ProfileNotifier 완성 후 provinceId/cityId/officeId 읽어서
    //   firestore.collection('provinces').doc(...).collection('cities').doc(...)
    //     .collection('offices').doc(...)
    //     .collection('customerInfo').doc(phone)
    //     .set(buildCustomerFcmPayload(token: token, phoneNumber: phone),
    //          SetOptions(merge: true));
    debugPrint(
        '[_registerFcmToken] Chunk 5 ProfileNotifier 완성 후 Firestore 저장 활성화');
  }

  @override
  void dispose() {
    _tokenRefreshSub?.cancel();
    super.dispose();
  }
}

final authNotifierProvider =
    StateNotifierProvider<AuthNotifier, AuthUiState>((ref) {
  return AuthNotifier(
    ref: ref,
    auth: ref.watch(firebaseAuthProvider),
    messaging: ref.watch(firebaseMessagingProvider),
    firestore: ref.watch(firestoreProvider),
  );
});
