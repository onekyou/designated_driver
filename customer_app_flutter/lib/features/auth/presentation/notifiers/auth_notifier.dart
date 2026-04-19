import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/providers.dart';
import '../state/auth_ui_state.dart';

/// AuthNotifier 껍데기 (Chunk 3)
///
/// Chunk 4 에서 구현 예정:
/// - 생성자에서 _initAnonymousAuth() + _subscribeFcmToken() 호출
/// - sendVerificationCode / verifyCode / _signInWithCredential (linkWithCredential)
/// - _handleCredentialAlreadyInUse fallback
/// - _subscribeFcmToken / _registerFcmToken / initialFcmTokenRegistration
class AuthNotifier extends StateNotifier<AuthUiState> {
  AuthNotifier({
    required this.ref,
    required this.auth,
    required this.messaging,
    required this.firestore,
  }) : super(const AuthUiState());

  final Ref ref;
  final FirebaseAuth auth;
  final FirebaseMessaging messaging;
  final FirebaseFirestore firestore;

  // ignore: unused_field
  String? _verificationId;
  // ignore: unused_field
  StreamSubscription<String>? _tokenRefreshSub;

  void updatePhoneNumber(String value) {
    state = state.copyWith(phoneNumber: value);
  }

  void updateVerificationCode(String value) {
    state = state.copyWith(verificationCode: value);
  }

  Future<void> sendVerificationCode() async {
    throw UnimplementedError('Chunk 4: Phone Auth sendVerificationCode');
  }

  Future<void> verifyCode() async {
    throw UnimplementedError('Chunk 4: Phone Auth verifyCode');
  }

  Future<void> initialFcmTokenRegistration() async {
    throw UnimplementedError('Chunk 4: FCM 초기 토큰 등록');
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
