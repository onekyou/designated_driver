import 'package:customer_app_flutter/features/profile/domain/entities/customer_info.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CustomerInfo.fromJson round-trip', () {
    test('기본 필드 직렬화/역직렬화', () {
      final json = <String, Object?>{
        'id': 'uid1',
        'phoneNumber': '010-1234-5678',
        'name': '홍길동',
        'grade': 'silver',
        'points': 3000,
        'totalRides': 15,
        'totalSpent': 500000,
        'linkedOfficeId': 'o1',
        'primaryOfficeId': 'o1',
        'officePhone': '02-1234-5678',
        'bankName': '국민은행',
        'accountNumber': '123-456',
        'accountHolder': '사무실',
        'homeAddress': '서울시 강남구',
      };
      final info = CustomerInfo.fromJson(json);
      expect(info.name, '홍길동');
      expect(info.grade, 'silver');
      expect(info.homeAddress, '서울시 강남구');

      final back = info.toJson();
      expect(back['name'], '홍길동');
      expect(back['homeAddress'], '서울시 강남구');
    });

    test('빈 JSON에서 Defaults (Kotlin 일치)', () {
      final info = CustomerInfo.fromJson({});
      expect(info.id, '');
      expect(info.phoneNumber, '');
      expect(info.name, '');
      expect(info.grade, 'bronze');
      expect(info.points, 0);
      expect(info.totalRides, 0);
      expect(info.totalSpent, 0);
      expect(info.linkedOfficeId, '');
      expect(info.primaryOfficeId, '');
      expect(info.attributionScore, isNull);
      expect(info.attributionSource, isNull);
      expect(info.registeredAt, isNull);
      expect(info.lastRideAt, isNull);
      expect(info.officePhone, '');
      expect(info.bankName, '');
      expect(info.homeAddress, '');
    });
  });

  // Note: legacy address → homeAddress fallback은 fromFirestore 에서만 처리됨
  // (fromJson은 JSON 표준 매핑이라 address 키 무시).
  // fromFirestore 테스트는 fake_cloud_firestore 등 DocumentSnapshot mock 필요하여
  // 별도 integration test로 이관.

  group('CustomerInfo attribution 필드', () {
    test('attributionSource 3종', () {
      for (final source in ['landing', 'qr_scan', 'referral']) {
        final info = CustomerInfo.fromJson({'attributionSource': source});
        expect(info.attributionSource, source);
      }
    });

    test('attributionScore nullable', () {
      final info =
          CustomerInfo.fromJson({'attributionScore': 85});
      expect(info.attributionScore, 85);

      final infoNoScore = CustomerInfo.fromJson({});
      expect(infoNoScore.attributionScore, isNull);
    });
  });

  group('CustomerInfo 사무실 연락처 (Phase 1 핵심)', () {
    test('officePhone/bankName/accountNumber/accountHolder 4필드', () {
      final info = CustomerInfo.fromJson({
        'officePhone': '02-1234-5678',
        'bankName': '신한은행',
        'accountNumber': '110-123-456789',
        'accountHolder': '대리운전',
      });
      expect(info.officePhone, '02-1234-5678');
      expect(info.bankName, '신한은행');
      expect(info.accountNumber, '110-123-456789');
      expect(info.accountHolder, '대리운전');
    });
  });
}
