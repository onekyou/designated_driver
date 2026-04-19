import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:customer_app_flutter/features/call/domain/entities/customer_call.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CustomerCall.fromJson — @JsonKey assignedDriverId 매핑', () {
    test('Firestore assignedDriverId → Dart driverId', () {
      final json = <String, Object?>{
        'phoneNumber': '010-1111-2222',
        'officeId': 'o1',
        'provinceId': 'p1',
        'cityId': 'c1',
        'assignedDriverId': 'driverX',
      };
      final call = CustomerCall.fromJson(json);
      expect(call.driverId, 'driverX');

      // toJson은 assignedDriverId로 역매핑
      final back = call.toJson();
      expect(back['assignedDriverId'], 'driverX');
      expect(back.containsKey('driverId'), isFalse);
    });

    test('assignedDriverId 누락 시 driverId == null', () {
      final call = CustomerCall.fromJson({
        'phoneNumber': '010-1',
        'officeId': 'o',
        'provinceId': 'p',
        'cityId': 'c',
      });
      expect(call.driverId, isNull);
    });
  });

  group('CustomerCall.toFirestore — 3중복 필드 저장', () {
    test('currentLocation → customerAddress + departure + departure_set', () {
      final call = CustomerCall(
        phoneNumber: '010-1',
        officeId: 'o',
        provinceId: 'p',
        cityId: 'c',
        currentLocation: '서울시 강남구',
        destinationLocation: '서울시 종로구',
      );
      final map = call.toFirestore();
      expect(map['customerAddress'], '서울시 강남구');
      expect(map['departure'], '서울시 강남구');
      expect(map['departure_set'], '서울시 강남구');
      expect(map['destination'], '서울시 종로구');
      expect(map['destination_set'], '서울시 종로구');
    });

    test('assignedDriverId 키로 저장 (driverId 키 안 씀)', () {
      final call = CustomerCall(
        phoneNumber: '010-1',
        officeId: 'o',
        provinceId: 'p',
        cityId: 'c',
        driverId: 'driverY',
      );
      final map = call.toFirestore();
      expect(map['assignedDriverId'], 'driverY');
      expect(map.containsKey('driverId'), isFalse);
    });

    test('timestamp int → Firestore Timestamp(seconds, nanoseconds)', () {
      // 2024-01-01 00:00:00 UTC = 1704067200 초 = 1704067200000 ms
      final call = CustomerCall(
        phoneNumber: '010-1',
        officeId: 'o',
        provinceId: 'p',
        cityId: 'c',
        timestamp: 1704067200500, // .5초 오프셋
      );
      final map = call.toFirestore();
      final ts = map['timestamp'] as Timestamp;
      expect(ts.seconds, 1704067200);
      expect(ts.nanoseconds, 500000000); // 0.5초 = 5억 나노초
    });
  });

  group('CustomerCall 기본값 (Kotlin 일치)', () {
    test('빈 JSON (필수 필드 제외) Defaults', () {
      final call = CustomerCall.fromJson({
        'phoneNumber': '010-1',
        'officeId': 'o',
        'provinceId': 'p',
        'cityId': 'c',
      });
      expect(call.id, '');
      expect(call.currentLocation, '');
      expect(call.destinationLocation, '');
      expect(call.timestamp, 0);
      expect(call.status, 'REQUESTED');
      expect(call.driverId, isNull);
      expect(call.estimatedArrivalTime, isNull);
      expect(call.fare, isNull);
      expect(call.notes, isNull);
      expect(call.createdFrom, 'customer_app');
      expect(call.customerGrade, 'bronze');
      expect(call.isAppCustomer, isTrue);
      expect(call.pointsUsed, 0);
      expect(call.finalFare, isNull);
      expect(call.discountAmount, 0);
    });
  });

  group('CustomerCall 부호 유지 — 환불 케이스', () {
    test('pointsUsed 양수 저장 + discountAmount', () {
      final call = CustomerCall(
        phoneNumber: '010-1',
        officeId: 'o',
        provinceId: 'p',
        cityId: 'c',
        fare: 15000,
        pointsUsed: 500,
        discountAmount: 1000,
        finalFare: 13500, // 15000 - 500 - 1000
      );
      final map = call.toFirestore();
      expect(map['fare'], 15000);
      expect(map['pointsUsed'], 500);
      expect(map['discountAmount'], 1000);
      expect(map['finalFare'], 13500);
    });
  });
}
