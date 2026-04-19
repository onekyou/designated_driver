import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:customer_app_flutter/core/utils/fcm_token_payload.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('buildCustomerFcmPayload', () {
    test('isIos=true → fcmTokenPlatform="ios"', () {
      final payload = buildCustomerFcmPayload(
        token: 'tok_abc',
        phoneNumber: '+821012345678',
        isIos: true,
      );
      expect(payload['fcmTokenPlatform'], 'ios');
      expect(payload['fcmToken'], 'tok_abc');
      expect(payload['phoneNumber'], '+821012345678');
    });

    test('isIos=false → fcmTokenPlatform="android"', () {
      final payload = buildCustomerFcmPayload(
        token: 'tok_def',
        phoneNumber: '+821099998888',
        isIos: false,
      );
      expect(payload['fcmTokenPlatform'], 'android');
      expect(payload['fcmToken'], 'tok_def');
      expect(payload['phoneNumber'], '+821099998888');
    });

    test('4 필드 전부 존재 + updatedAt은 Timestamp', () {
      final payload = buildCustomerFcmPayload(
        token: 'tok_xyz',
        phoneNumber: '+8210',
        isIos: false,
      );
      expect(payload.keys, containsAll(['fcmToken', 'fcmTokenPlatform', 'phoneNumber', 'updatedAt']));
      expect(payload['updatedAt'], isA<Timestamp>());
    });

    test('phoneNumber 원본 보존 (변환 없음)', () {
      final payload = buildCustomerFcmPayload(
        token: 'tok',
        phoneNumber: '010-1234-5678',  // hyphen 포함도 그대로
        isIos: false,
      );
      expect(payload['phoneNumber'], '010-1234-5678');
    });
  });
}
