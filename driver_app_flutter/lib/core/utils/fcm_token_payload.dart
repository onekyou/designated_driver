import 'dart:io' show Platform;
import 'package:cloud_firestore/cloud_firestore.dart';
import '../constants/app_constants.dart';

/// FCM 토큰 Firestore write payload 생성 (iOS/Android 분기 포함).
/// [isIos]를 명시적으로 주입하면 테스트 시 Platform 의존 제거 가능.
Map<String, dynamic> buildTokenUpdatePayload({
  required String token,
  bool includeUpdatedAt = true,
  bool? isIos,
}) {
  final ios = isIos ?? Platform.isIOS;
  final platformValue =
      ios ? AppConstants.platformIos : AppConstants.platformAndroid;
  return {
    AppConstants.fieldFcmToken: token,
    AppConstants.fieldFcmTokenPlatform: platformValue,
    AppConstants.fieldPlatform: platformValue,
    if (includeUpdatedAt) 'fcmTokenUpdatedAt': FieldValue.serverTimestamp(),
  };
}
