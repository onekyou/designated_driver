import 'package:customer_app_flutter/core/domain/enums/customer_grade.dart';
import 'package:customer_app_flutter/features/point/domain/entities/customer_points.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CustomerPoints.fromJson round-trip', () {
    test('grade 문자열 ↔ enum 변환 (CustomerGradeConverter)', () {
      final json = <String, Object?>{
        'customerId': 'c1',
        'phoneNumber': '010-1234-5678',
        'currentPoints': 500,
        'totalEarned': 1000,
        'totalUsed': 500,
        'grade': 'SILVER',
        'totalCalls': 15,
      };
      final points = CustomerPoints.fromJson(json);
      expect(points.grade, CustomerGrade.silver);
      expect(points.currentPoints, 500);

      final backToJson = points.toJson();
      expect(backToJson['grade'], 'SILVER');
    });

    test('grade 누락 시 bronze fallback', () {
      final points = CustomerPoints.fromJson({'phoneNumber': '010-0'});
      expect(points.grade, CustomerGrade.bronze);
    });

    test('소문자 grade도 매핑', () {
      final points = CustomerPoints.fromJson({'grade': 'vip'});
      expect(points.grade, CustomerGrade.vip);
    });
  });

  group('CustomerPoints 비즈니스 메서드', () {
    final bronzeUser = CustomerPoints.fromJson({
      'grade': 'BRONZE',
      'totalCalls': 5,
      'currentPoints': 100,
    });
    final silverUser = CustomerPoints.fromJson({
      'grade': 'BRONZE',
      'totalCalls': 15,
      'currentPoints': 200,
    });

    test('shouldUpdateGrade: 콜 수가 등급 경계 넘으면 true', () {
      expect(bronzeUser.shouldUpdateGrade, isFalse); // 5콜 → bronze 그대로
      expect(silverUser.shouldUpdateGrade, isTrue); // 15콜인데 grade=bronze
    });

    test('updatedGrade: 콜 수 기반 실제 등급', () {
      expect(bronzeUser.updatedGrade, CustomerGrade.bronze);
      expect(silverUser.updatedGrade, CustomerGrade.silver);
    });

    test('canUsePoints 경계', () {
      expect(bronzeUser.canUsePoints(100), isTrue);
      expect(bronzeUser.canUsePoints(101), isFalse);
      expect(bronzeUser.canUsePoints(0), isTrue);
    });

    test('calculateEarnPoints: 등급 적립률 적용', () {
      // bronzeUser grade=bronze, 3%
      expect(bronzeUser.calculateEarnPoints(10000), 300);
      // silverUser grade=bronze (field), 3% (grade 기준) — shouldUpdateGrade는 true지만 실제 grade 필드 기반 계산
      expect(silverUser.calculateEarnPoints(10000), 300);
    });

    test('callsToNextGrade: 다음 등급까지 남은 콜', () {
      expect(bronzeUser.callsToNextGrade, 5); // 10 - 5
      // silverUser: grade=bronze(field), totalCalls=15 → 10 - 15 = -5
      expect(silverUser.callsToNextGrade, -5);
    });
  });
}
