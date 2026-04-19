import 'dart:io' show Platform;

import 'package:cloud_firestore/cloud_firestore.dart';

/// customerInfo/{phone} 에 저장할 FCM 토큰 페이로드 생성.
///
/// 의제 5: `fcmTokenPlatform` ('ios' | 'android') 메타 포함.
/// [isIos] 주입으로 Platform 의존 테스트 양방향 검증 가능 (기사앱 패턴).
Map<String, dynamic> buildCustomerFcmPayload({
  required String token,
  required String phoneNumber,
  bool? isIos,
}) {
  final ios = isIos ?? Platform.isIOS;
  return {
    'fcmToken': token,
    'fcmTokenPlatform': ios ? 'ios' : 'android',
    'phoneNumber': phoneNumber,
    'updatedAt': Timestamp.now(),
  };
}
