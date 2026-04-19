import 'package:customer_app_flutter/features/auth/presentation/notifiers/phone_formatter.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('formatKoreanPhoneNumber', () {
    test('010-1234-5678 → +821012345678 (hyphen 제거)', () {
      expect(formatKoreanPhoneNumber('010-1234-5678'), '+821012345678');
    });

    test('01012345678 → +821012345678 (hyphen 없음)', () {
      expect(formatKoreanPhoneNumber('01012345678'), '+821012345678');
    });

    test('010 1234 5678 → +821012345678 (공백 포함)', () {
      expect(formatKoreanPhoneNumber('010 1234 5678'), '+821012345678');
    });

    test('이미 +82 시작 → 그대로 덧붙임 (edge)', () {
      // 현재 구현: 010으로 시작 안 하면 +82 prepend (미처리 edge)
      expect(formatKoreanPhoneNumber('+821012345678'), '+82821012345678');
    });
  });
}
