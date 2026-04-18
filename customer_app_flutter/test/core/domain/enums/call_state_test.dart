import 'package:customer_app_flutter/core/domain/enums/call_state.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CallState.fromFirestoreStatus — 11종 Firestore 상태 매핑', () {
    test('WAITING/SHARED_WAITING → requested', () {
      expect(CallState.fromFirestoreStatus('WAITING'),
          isA<CallState>().having((s) => s.displayName, 'displayName', '배차 요청 중'));
      expect(CallState.fromFirestoreStatus('SHARED_WAITING').displayName,
          '배차 요청 중');
    });

    test('ASSIGNED → assigned', () {
      expect(CallState.fromFirestoreStatus('ASSIGNED').displayName, '기사 배차됨');
    });

    test('ACCEPTED/PREPARING → driverArriving', () {
      expect(CallState.fromFirestoreStatus('ACCEPTED').displayName, '기사 오는 중');
      expect(CallState.fromFirestoreStatus('PREPARING').displayName, '기사 오는 중');
    });

    test('IN_PROGRESS → inProgress', () {
      expect(CallState.fromFirestoreStatus('IN_PROGRESS').displayName, '운행 중');
    });

    test('AWAITING_SETTLEMENT/COMPLETED → completed', () {
      expect(CallState.fromFirestoreStatus('AWAITING_SETTLEMENT').displayName,
          '완료');
      expect(CallState.fromFirestoreStatus('COMPLETED').displayName, '완료');
    });

    test('모든 CANCELED/CANCELLED/HOLD/CLAIMED → cancelled', () {
      expect(CallState.fromFirestoreStatus('CANCELED').displayName, '취소됨');
      expect(CallState.fromFirestoreStatus('CANCELLED_BY_DRIVER').displayName,
          '취소됨');
      expect(CallState.fromFirestoreStatus('CANCELLED_BY_CUSTOMER').displayName,
          '취소됨');
      expect(CallState.fromFirestoreStatus('HOLD').displayName, '취소됨');
      expect(CallState.fromFirestoreStatus('CLAIMED').displayName, '취소됨');
    });

    test('알 수 없는 상태 → cancelled 안전 기본값', () {
      expect(CallState.fromFirestoreStatus('UNKNOWN').displayName, '취소됨');
      expect(CallState.fromFirestoreStatus('').displayName, '취소됨');
    });
  });

  group('CallState.isCancellable', () {
    test('REQUESTED/ASSIGNED/DRIVER_ARRIVING은 true', () {
      expect(const CallState.requested().isCancellable, isTrue);
      expect(const CallState.assigned().isCancellable, isTrue);
      expect(const CallState.driverArriving().isCancellable, isTrue);
    });

    test('IN_PROGRESS/COMPLETED/CANCELLED는 false', () {
      expect(const CallState.inProgress().isCancellable, isFalse);
      expect(const CallState.completed().isCancellable, isFalse);
      expect(const CallState.cancelled().isCancellable, isFalse);
    });
  });

  group('CallState.isFinal', () {
    test('COMPLETED/CANCELLED만 true', () {
      expect(const CallState.completed().isFinal, isTrue);
      expect(const CallState.cancelled().isFinal, isTrue);
    });

    test('그 외는 false', () {
      expect(const CallState.requested().isFinal, isFalse);
      expect(const CallState.assigned().isFinal, isFalse);
      expect(const CallState.driverArriving().isFinal, isFalse);
      expect(const CallState.inProgress().isFinal, isFalse);
    });
  });
}
