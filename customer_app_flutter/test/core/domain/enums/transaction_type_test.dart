import 'package:customer_app_flutter/core/domain/enums/transaction_type.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('TransactionType.fromString — Firestore 문자열 매핑', () {
    test('5종 대문자 매핑', () {
      expect(TransactionType.fromString('EARN'), TransactionType.earn);
      expect(TransactionType.fromString('USE'), TransactionType.use);
      expect(TransactionType.fromString('EXPIRE'), TransactionType.expire);
      expect(TransactionType.fromString('CANCEL'), TransactionType.cancel);
      expect(TransactionType.fromString('ADMIN'), TransactionType.admin);
    });

    test('소문자도 매핑', () {
      expect(TransactionType.fromString('earn'), TransactionType.earn);
    });

    test('null/미매칭은 earn fallback', () {
      expect(TransactionType.fromString(null), TransactionType.earn);
      expect(TransactionType.fromString(''), TransactionType.earn);
      expect(TransactionType.fromString('UNKNOWN'), TransactionType.earn);
    });
  });

  group('TransactionType.toJson — round-trip', () {
    test('json 필드 값 반환', () {
      expect(TransactionType.earn.toJson(), 'EARN');
      expect(TransactionType.admin.toJson(), 'ADMIN');
    });

    test('fromString(toJson()) round-trip', () {
      for (final type in TransactionType.values) {
        expect(TransactionType.fromString(type.toJson()), type);
      }
    });
  });

  group('TransactionType.isValidAmount', () {
    test('EARN/CANCEL (sign=+1)은 양수만 유효', () {
      expect(TransactionType.earn.isValidAmount(100), isTrue);
      expect(TransactionType.earn.isValidAmount(0), isFalse);
      expect(TransactionType.earn.isValidAmount(-100), isFalse);
      expect(TransactionType.cancel.isValidAmount(50), isTrue);
    });

    test('USE/EXPIRE (sign=-1)는 음수만 유효', () {
      expect(TransactionType.use.isValidAmount(-100), isTrue);
      expect(TransactionType.use.isValidAmount(0), isFalse);
      expect(TransactionType.use.isValidAmount(100), isFalse);
      expect(TransactionType.expire.isValidAmount(-50), isTrue);
    });

    test('ADMIN (sign=0)은 양/음/0 모두 유효', () {
      expect(TransactionType.admin.isValidAmount(100), isTrue);
      expect(TransactionType.admin.isValidAmount(-100), isTrue);
      expect(TransactionType.admin.isValidAmount(0), isTrue);
    });
  });

  group('TransactionType.formatAmount — UI 부호 표시', () {
    test('EARN/CANCEL → 항상 +', () {
      expect(TransactionType.earn.formatAmount(300), '+300');
      expect(TransactionType.cancel.formatAmount(200), '+200');
    });

    test('USE/EXPIRE → 항상 -', () {
      // Kotlin PointService는 USE amount를 음수로 저장. abs().
      expect(TransactionType.use.formatAmount(-500), '-500');
      expect(TransactionType.expire.formatAmount(-100), '-100');
      // 저장이 양수여도 UI는 -로 표시
      expect(TransactionType.use.formatAmount(500), '-500');
    });

    test('ADMIN → amount 부호에 따라 +/-', () {
      expect(TransactionType.admin.formatAmount(100), '+100');
      expect(TransactionType.admin.formatAmount(-100), '-100');
      expect(TransactionType.admin.formatAmount(0), '+0');
    });
  });

  group('TransactionType 필드 무결성', () {
    test('모든 필드 non-empty + sign ∈ {-1, 0, 1}', () {
      for (final type in TransactionType.values) {
        expect(type.json, isNotEmpty);
        expect(type.displayName, isNotEmpty);
        expect([-1, 0, 1], contains(type.sign));
      }
    });
  });
}
