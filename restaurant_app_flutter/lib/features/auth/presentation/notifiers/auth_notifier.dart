import 'dart:async';
import 'dart:io' show Platform;

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/providers.dart';
import '../state/auth_ui_state.dart';

/// Anonymous Auth 자동 로그인 + FCM 토큰 등록 (식당앱).
///
/// FCM 토큰 저장 위치: `restaurants/{restaurantId}` (Plan 결정 #8).
/// 가입 전(SharedPreferences 미설정)에는 토큰 등록 skip → SignupNotifier가 가입 직후 직접 갱신.
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

  StreamSubscription<String>? _tokenRefreshSub;

  Future<void> _initAnonymousAuth() async {
    final currentUser = auth.currentUser;
    if (currentUser != null) {
      state = state.copyWith(
        isAnonymouslySignedIn: true,
        anonymousUid: currentUser.uid,
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

  void _subscribeFcmToken() {
    _tokenRefreshSub = messaging.onTokenRefresh.listen(_registerFcmToken);
  }

  /// 가입 후 SignupNotifier가 호출 (또는 앱 시작 시 currentUser 있으면 호출).
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

  Future<void> _registerFcmToken(String token) async {
    final prefs = ref.read(sharedPreferencesProvider);
    final provinceId = prefs.getString('provinceId');
    final cityId = prefs.getString('cityId');
    final officeId = prefs.getString('officeId');
    final restaurantId = prefs.getString('restaurantId');
    if (provinceId == null ||
        cityId == null ||
        officeId == null ||
        restaurantId == null) {
      debugPrint('[_registerFcmToken] 가입 전, 스킵');
      return;
    }

    try {
      await firestore
          .collection('provinces')
          .doc(provinceId)
          .collection('cities')
          .doc(cityId)
          .collection('offices')
          .doc(officeId)
          .collection('restaurants')
          .doc(restaurantId)
          .set({
        'fcmToken': token,
        'fcmTokenPlatform': Platform.isIOS ? 'ios' : 'android',
        'fcmTokenUpdatedAt': Timestamp.now(),
      }, SetOptions(merge: true));
      debugPrint('[_registerFcmToken] 저장 완료');
    } catch (e) {
      debugPrint('[_registerFcmToken] 저장 실패: $e');
    }
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
