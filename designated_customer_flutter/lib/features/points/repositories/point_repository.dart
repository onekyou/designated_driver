import 'package:cloud_firestore/cloud_firestore.dart';
import '../models/customer_points.dart';
import '../models/point_transaction.dart';
import '../../../core/exceptions/app_exceptions.dart';

/// 포인트 데이터 Repository
class PointRepository {
  final FirebaseFirestore _firestore;
  final String regionId;
  final String officeId;

  PointRepository({
    FirebaseFirestore? firestore,
    required this.regionId,
    required this.officeId,
  }) : _firestore = firestore ?? FirebaseFirestore.instance;

  /// CustomerPoints 컬렉션 참조
  CollectionReference get _customerPointsCollection {
    return _firestore
        .collection('regions')
        .doc(regionId)
        .collection('offices')
        .doc(officeId)
        .collection('customerPoints');
  }

  /// PointTransactions 컬렉션 참조
  CollectionReference get _pointTransactionsCollection {
    return _firestore
        .collection('regions')
        .doc(regionId)
        .collection('offices')
        .doc(officeId)
        .collection('pointTransactions');
  }

  /// 고객 포인트 정보 조회
  Future<CustomerPoints?> getCustomerPoints(String phoneNumber) async {
    try {
      final doc = await _customerPointsCollection.doc(phoneNumber).get();

      if (!doc.exists) {
        return null;
      }

      return CustomerPoints.fromFirestore(doc);
    } catch (e) {
      throw AppException('포인트 정보를 가져오는데 실패했습니다: $e');
    }
  }

  /// 고객 포인트 정보 생성
  Future<void> createCustomerPoints(CustomerPoints points) async {
    try {
      await _customerPointsCollection
          .doc(points.phoneNumber)
          .set(points.toFirestore());
    } catch (e) {
      throw AppException('포인트 정보를 생성하는데 실패했습니다: $e');
    }
  }

  /// 고객 포인트 정보 업데이트
  Future<void> updateCustomerPoints(CustomerPoints points) async {
    try {
      await _customerPointsCollection
          .doc(points.phoneNumber)
          .update(points.toFirestore());
    } catch (e) {
      throw AppException('포인트 정보를 업데이트하는데 실패했습니다: $e');
    }
  }

  /// 포인트 거래 내역 추가
  Future<String> addPointTransaction(PointTransaction transaction) async {
    try {
      final docRef = await _pointTransactionsCollection.add(
        transaction.toFirestore(),
      );
      return docRef.id;
    } catch (e) {
      throw AppException('포인트 거래 내역을 추가하는데 실패했습니다: $e');
    }
  }

  /// 고객의 포인트 거래 내역 조회 (최신순)
  Future<List<PointTransaction>> getPointTransactions(
    String phoneNumber, {
    int limit = 50,
  }) async {
    try {
      final querySnapshot = await _pointTransactionsCollection
          .where('customerId', isEqualTo: phoneNumber)
          .orderBy('timestamp', descending: true)
          .limit(limit)
          .get();

      return querySnapshot.docs
          .map((doc) => PointTransaction.fromFirestore(doc))
          .toList();
    } catch (e) {
      throw AppException('포인트 거래 내역을 가져오는데 실패했습니다: $e');
    }
  }

  /// 고객의 포인트 거래 내역 스트림 (실시간 업데이트)
  Stream<List<PointTransaction>> observePointTransactions(
    String phoneNumber, {
    int limit = 50,
  }) {
    try {
      return _pointTransactionsCollection
          .where('customerId', isEqualTo: phoneNumber)
          .orderBy('timestamp', descending: true)
          .limit(limit)
          .snapshots()
          .map((snapshot) => snapshot.docs
              .map((doc) => PointTransaction.fromFirestore(doc))
              .toList());
    } catch (e) {
      throw AppException('포인트 거래 내역 스트림을 생성하는데 실패했습니다: $e');
    }
  }

  /// 고객 포인트 정보 스트림 (실시간 업데이트)
  Stream<CustomerPoints?> observeCustomerPoints(String phoneNumber) {
    try {
      return _customerPointsCollection.doc(phoneNumber).snapshots().map(
        (doc) {
          if (!doc.exists) return null;
          return CustomerPoints.fromFirestore(doc);
        },
      );
    } catch (e) {
      throw AppException('포인트 정보 스트림을 생성하는데 실패했습니다: $e');
    }
  }

  /// 특정 기간의 포인트 거래 내역 조회
  Future<List<PointTransaction>> getPointTransactionsByDateRange(
    String phoneNumber,
    DateTime startDate,
    DateTime endDate,
  ) async {
    try {
      final querySnapshot = await _pointTransactionsCollection
          .where('customerId', isEqualTo: phoneNumber)
          .where('timestamp', isGreaterThanOrEqualTo: Timestamp.fromDate(startDate))
          .where('timestamp', isLessThanOrEqualTo: Timestamp.fromDate(endDate))
          .orderBy('timestamp', descending: true)
          .get();

      return querySnapshot.docs
          .map((doc) => PointTransaction.fromFirestore(doc))
          .toList();
    } catch (e) {
      throw AppException('기간별 포인트 거래 내역을 가져오는데 실패했습니다: $e');
    }
  }

  /// 포인트 거래 타입별 조회
  Future<List<PointTransaction>> getPointTransactionsByType(
    String phoneNumber,
    TransactionType type, {
    int limit = 50,
  }) async {
    try {
      final querySnapshot = await _pointTransactionsCollection
          .where('customerId', isEqualTo: phoneNumber)
          .where('type', isEqualTo: type.code)
          .orderBy('timestamp', descending: true)
          .limit(limit)
          .get();

      return querySnapshot.docs
          .map((doc) => PointTransaction.fromFirestore(doc))
          .toList();
    } catch (e) {
      throw AppException('타입별 포인트 거래 내역을 가져오는데 실패했습니다: $e');
    }
  }
}
