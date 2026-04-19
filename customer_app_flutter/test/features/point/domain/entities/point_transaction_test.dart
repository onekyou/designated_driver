import 'package:customer_app_flutter/core/domain/enums/transaction_type.dart';
import 'package:customer_app_flutter/features/point/domain/entities/point_transaction.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('PointTransaction.fromJson round-trip', () {
    test('type 문자열 ↔ enum (TransactionTypeConverter)', () {
      final json = <String, Object?>{
        'id': 'tx1',
        'customerId': 'c1',
        'type': 'USE',
        'amount': -500,
        'balance': 1000,
        'description': '콜 결제',
      };
      final tx = PointTransaction.fromJson(json);
      expect(tx.type, TransactionType.use);
      expect(tx.amount, -500); // 음수 유지 (Kotlin PointService.kt:239 관용)

      final backToJson = tx.toJson();
      expect(backToJson['type'], 'USE');
      expect(backToJson['amount'], -500);
    });

    test('모든 타입 round-trip', () {
      for (final type in TransactionType.values) {
        final json = {'type': type.json, 'amount': type.sign * 100};
        final tx = PointTransaction.fromJson(json);
        expect(tx.type, type);
        expect(tx.toJson()['type'], type.json);
      }
    });

    test('type 누락 시 earn fallback', () {
      final tx = PointTransaction.fromJson({'amount': 100});
      expect(tx.type, TransactionType.earn);
    });
  });

  group('PointTransaction amount 부호 관용구', () {
    test('EARN: 양수 유지', () {
      final tx = PointTransaction.fromJson({'type': 'EARN', 'amount': 300});
      expect(tx.amount, 300);
      expect(tx.type.formatAmount(tx.amount), '+300');
    });

    test('USE: 음수 유지 → UI에서 -로 표시', () {
      final tx = PointTransaction.fromJson({'type': 'USE', 'amount': -500});
      expect(tx.amount, -500);
      expect(tx.type.formatAmount(tx.amount), '-500');
    });

    test('CANCEL (환불): 양수 유지', () {
      final tx =
          PointTransaction.fromJson({'type': 'CANCEL', 'amount': 200});
      expect(tx.amount, 200);
      expect(tx.type.formatAmount(tx.amount), '+200');
    });
  });

  group('PointTransaction 기본값', () {
    test('빈 JSON에서 Defaults 적용', () {
      final tx = PointTransaction.fromJson({});
      expect(tx.id, '');
      expect(tx.customerId, '');
      expect(tx.type, TransactionType.earn);
      expect(tx.amount, 0);
      expect(tx.balance, 0);
      expect(tx.grade, 'BRONZE');
      expect(tx.callId, isNull);
      expect(tx.timestamp, isNull);
      expect(tx.fare, isNull);
    });
  });
}
