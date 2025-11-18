import 'package:cloud_firestore/cloud_firestore.dart';
import '../models/call_model.dart';

/// Call Repository - Firestore 연동
class CallRepository {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  /// 콜 요청
  Future<String> requestCall({
    required String regionId,
    required String officeId,
    required CustomerCall call,
  }) async {
    try {
      // calls 컬렉션 참조
      final callsCollection = _firestore
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('calls');

      // 새 document 생성
      final docRef = callsCollection.doc();

      // ID를 포함한 콜 데이터
      final callWithId = call.copyWith(id: docRef.id);

      // Firestore에 저장
      await docRef.set(callWithId.toFirestore());

      return docRef.id;
    } on FirebaseException catch (e) {
      throw CallException(
        '콜 요청 실패: ${e.message}',
        code: e.code,
      );
    } catch (e) {
      throw CallException('콜 요청 중 오류가 발생했습니다: $e');
    }
  }

  /// 콜 취소
  Future<bool> cancelCall({
    required String regionId,
    required String officeId,
    required String callId,
  }) async {
    try {
      await _firestore
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('calls')
          .doc(callId)
          .update({'status': 'CANCELLED'});

      return true;
    } on FirebaseException catch (e) {
      throw CallException(
        '콜 취소 실패: ${e.message}',
        code: e.code,
      );
    } catch (e) {
      throw CallException('콜 취소 중 오류가 발생했습니다: $e');
    }
  }

  /// 고객의 콜 내역 조회
  Future<List<CustomerCall>> getCallHistory({
    required String regionId,
    required String officeId,
    required String phoneNumber,
    int limit = 50,
  }) async {
    try {
      final snapshot = await _firestore
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('calls')
          .where('phoneNumber', isEqualTo: phoneNumber)
          .orderBy('timestamp', descending: true)
          .limit(limit)
          .get();

      return snapshot.docs
          .map((doc) => CustomerCall.fromFirestore(doc.data()))
          .toList();
    } on FirebaseException catch (e) {
      throw CallException(
        '콜 내역 조회 실패: ${e.message}',
        code: e.code,
      );
    } catch (e) {
      throw CallException('콜 내역 조회 중 오류가 발생했습니다: $e');
    }
  }

  /// 고객의 콜 실시간 모니터링 (Stream)
  Stream<List<CustomerCall>> observeCustomerCalls({
    required String regionId,
    required String officeId,
    required String phoneNumber,
    int limit = 50,
  }) {
    try {
      return _firestore
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('calls')
          .where('phoneNumber', isEqualTo: phoneNumber)
          .orderBy('timestamp', descending: true)
          .limit(limit)
          .snapshots()
          .map((snapshot) {
        return snapshot.docs
            .map((doc) => CustomerCall.fromFirestore(doc.data()))
            .toList();
      }).handleError((error) {
        if (error is FirebaseException) {
          throw CallException(
            '콜 모니터링 실패: ${error.message}',
            code: error.code,
          );
        }
        throw CallException('콜 모니터링 중 오류가 발생했습니다: $error');
      });
    } catch (e) {
      // Stream 에러 처리
      return Stream.error(
        CallException('콜 모니터링 스트림 생성 실패: $e'),
      );
    }
  }

  /// 활성 콜 조회 (REQUESTED, ASSIGNED, DRIVER_ARRIVING, IN_PROGRESS)
  Future<CustomerCall?> getActiveCall({
    required String regionId,
    required String officeId,
    required String phoneNumber,
  }) async {
    try {
      final snapshot = await _firestore
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('calls')
          .where('phoneNumber', isEqualTo: phoneNumber)
          .where('status', whereIn: ['REQUESTED', 'ASSIGNED', 'DRIVER_ARRIVING', 'IN_PROGRESS'])
          .orderBy('timestamp', descending: true)
          .limit(1)
          .get();

      if (snapshot.docs.isEmpty) {
        return null;
      }

      return CustomerCall.fromFirestore(snapshot.docs.first.data());
    } on FirebaseException catch (e) {
      throw CallException(
        '활성 콜 조회 실패: ${e.message}',
        code: e.code,
      );
    } catch (e) {
      throw CallException('활성 콜 조회 중 오류가 발생했습니다: $e');
    }
  }

  /// 요금 확정 및 포인트 할인 처리
  Future<bool> completeFareWithPointDiscount({
    required String regionId,
    required String officeId,
    required String callId,
    required int originalFare,
    required int pointsUsed,
  }) async {
    try {
      final discountedFare = (originalFare - pointsUsed).clamp(0, originalFare);

      await _firestore
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('calls')
          .doc(callId)
          .update({
        'fare': originalFare,
        'pointsUsed': pointsUsed,
        'finalFare': discountedFare,
        'discountAmount': pointsUsed,
      });

      return true;
    } on FirebaseException catch (e) {
      throw CallException(
        '요금 확정 실패: ${e.message}',
        code: e.code,
      );
    } catch (e) {
      throw CallException('요금 확정 중 오류가 발생했습니다: $e');
    }
  }
}

/// Call 관련 Exception
class CallException implements Exception {
  final String message;
  final String? code;

  CallException(this.message, {this.code});

  @override
  String toString() => 'CallException: $message${code != null ? ' (code: $code)' : ''}';
}
