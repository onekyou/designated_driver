import 'package:customer_app_flutter/core/domain/enums/customer_grade.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CustomerGrade.fromString — Firestore 문자열 매핑', () {
    test('대문자 문자열 매핑', () {
      expect(CustomerGrade.fromString('BRONZE'), CustomerGrade.bronze);
      expect(CustomerGrade.fromString('SILVER'), CustomerGrade.silver);
      expect(CustomerGrade.fromString('GOLD'), CustomerGrade.gold);
      expect(CustomerGrade.fromString('VIP'), CustomerGrade.vip);
    });

    test('소문자도 매핑 (Kotlin uppercase 로직)', () {
      expect(CustomerGrade.fromString('bronze'), CustomerGrade.bronze);
      expect(CustomerGrade.fromString('vip'), CustomerGrade.vip);
    });

    test('null/미매칭은 bronze fallback', () {
      expect(CustomerGrade.fromString(null), CustomerGrade.bronze);
      expect(CustomerGrade.fromString(''), CustomerGrade.bronze);
      expect(CustomerGrade.fromString('PLATINUM'), CustomerGrade.bronze);
    });
  });

  group('CustomerGrade.toJson — round-trip', () {
    test('json 필드 값 반환', () {
      expect(CustomerGrade.bronze.toJson(), 'BRONZE');
      expect(CustomerGrade.silver.toJson(), 'SILVER');
      expect(CustomerGrade.gold.toJson(), 'GOLD');
      expect(CustomerGrade.vip.toJson(), 'VIP');
    });

    test('fromString(toJson()) round-trip', () {
      for (final grade in CustomerGrade.values) {
        expect(CustomerGrade.fromString(grade.toJson()), grade);
      }
    });
  });

  group('CustomerGrade.fromCallCount — 등급 계산', () {
    test('경계값 정확히 매핑', () {
      expect(CustomerGrade.fromCallCount(0), CustomerGrade.bronze);
      expect(CustomerGrade.fromCallCount(9), CustomerGrade.bronze);
      expect(CustomerGrade.fromCallCount(10), CustomerGrade.silver);
      expect(CustomerGrade.fromCallCount(29), CustomerGrade.silver);
      expect(CustomerGrade.fromCallCount(30), CustomerGrade.gold);
      expect(CustomerGrade.fromCallCount(49), CustomerGrade.gold);
      expect(CustomerGrade.fromCallCount(50), CustomerGrade.vip);
      expect(CustomerGrade.fromCallCount(1000), CustomerGrade.vip);
    });
  });

  group('CustomerGrade.calculatePoints — 적립률 계산', () {
    test('등급별 적립 (Kotlin pointRate 일치)', () {
      expect(CustomerGrade.bronze.calculatePoints(10000), 300); // 3%
      expect(CustomerGrade.silver.calculatePoints(10000), 500); // 5%
      expect(CustomerGrade.gold.calculatePoints(10000), 700); // 7%
      expect(CustomerGrade.vip.calculatePoints(10000), 900); // 9%
    });

    test('소수점 버림 (toInt)', () {
      expect(CustomerGrade.bronze.calculatePoints(333), 9); // 9.99 → 9
      expect(CustomerGrade.vip.calculatePoints(555), 49); // 49.95 → 49
    });
  });

  group('CustomerGrade.nextGrade + callsToNext', () {
    test('nextGrade 체인', () {
      expect(CustomerGrade.bronze.nextGrade, CustomerGrade.silver);
      expect(CustomerGrade.silver.nextGrade, CustomerGrade.gold);
      expect(CustomerGrade.gold.nextGrade, CustomerGrade.vip);
      expect(CustomerGrade.vip.nextGrade, isNull);
    });

    test('callsToNext — 현재 콜 수 기준 남은 콜', () {
      expect(CustomerGrade.bronze.callsToNext(0), 10);
      expect(CustomerGrade.bronze.callsToNext(7), 3);
      expect(CustomerGrade.silver.callsToNext(15), 15); // 30 - 15
      expect(CustomerGrade.gold.callsToNext(45), 5); // 50 - 45
      expect(CustomerGrade.vip.callsToNext(100), isNull); // 최고 등급
    });
  });

  group('CustomerGrade 필드 무결성', () {
    test('모든 필드 non-empty', () {
      for (final grade in CustomerGrade.values) {
        expect(grade.json, isNotEmpty);
        expect(grade.displayName, isNotEmpty);
        expect(grade.icon, isNotEmpty);
        expect(grade.pointRate, greaterThan(0));
        expect(grade.minCalls, greaterThanOrEqualTo(0));
        expect(grade.colorArgb, greaterThan(0));
      }
    });
  });
}
