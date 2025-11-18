import 'package:cloud_firestore/cloud_firestore.dart';
import '../models/customer_points.dart';
import '../models/point_transaction.dart';
import '../repositories/point_repository.dart';
import '../../../core/constants/point_constants.dart';
import '../../../core/exceptions/app_exceptions.dart';

/// 포인트 비즈니스 로직 Service
class PointService {
  final PointRepository _repository;
  final FirebaseFirestore _firestore;

  PointService({
    required PointRepository repository,
    FirebaseFirestore? firestore,
  })  : _repository = repository,
        _firestore = firestore ?? FirebaseFirestore.instance;

  /// 고객 포인트 정보 조회 (없으면 생성)
  Future<CustomerPoints> getOrCreateCustomerPoints(String phoneNumber) async {
    try {
      // 기존 포인트 정보 조회
      var points = await _repository.getCustomerPoints(phoneNumber);

      // 없으면 새로 생성
      if (points == null) {
        points = CustomerPoints.create(phoneNumber);
        await _repository.createCustomerPoints(points);
      }

      return points;
    } catch (e) {
      throw AppException('포인트 정보를 가져오는데 실패했습니다: $e');
    }
  }

  /// 포인트 적립 (Firestore 트랜잭션 사용)
  Future<bool> earnPoints({
    required String phoneNumber,
    required String callId,
    required int fare,
    String? description,
  }) async {
    try {
      // 고객 포인트 정보 조회
      final points = await getOrCreateCustomerPoints(phoneNumber);

      // 등급별 적립 포인트 계산
      final earnAmount = points.calculateEarnPoints(fare);
      final newBalance = points.currentPoints + earnAmount;
      final newTotalCalls = points.totalCalls + 1;

      // 등급 자동 업그레이드
      final newGrade = CustomerGrade.fromCallCount(newTotalCalls);

      // Firestore 트랜잭션 (원자성 보장)
      await _firestore.runTransaction((transaction) async {
        // CustomerPoints 문서 참조
        final pointsRef = _firestore
            .collection('regions')
            .doc(_repository.regionId)
            .collection('offices')
            .doc(_repository.officeId)
            .collection('customerPoints')
            .doc(phoneNumber);

        // PointTransaction 문서 참조
        final transactionRef = _firestore
            .collection('regions')
            .doc(_repository.regionId)
            .collection('offices')
            .doc(_repository.officeId)
            .collection('pointTransactions')
            .doc();

        // CustomerPoints 업데이트
        final updatedPoints = points.copyWith(
          currentPoints: newBalance,
          totalEarned: points.totalEarned + earnAmount,
          totalCalls: newTotalCalls,
          grade: newGrade,
        );

        transaction.set(pointsRef, updatedPoints.toFirestore());

        // PointTransaction 기록
        final pointTransaction = PointTransaction.earn(
          id: transactionRef.id,
          customerId: phoneNumber,
          amount: earnAmount,
          balance: newBalance,
          callId: callId,
          fare: fare,
          grade: newGrade.toFirestore(),
        );

        transaction.set(transactionRef, pointTransaction.toFirestore());
      });

      return true;
    } catch (e) {
      throw AppException('포인트 적립에 실패했습니다: $e');
    }
  }

  /// 포인트 사용 (Firestore 트랜잭션 사용)
  Future<bool> usePoints({
    required String phoneNumber,
    required int amount,
    String? callId,
    String description = '포인트 사용',
  }) async {
    try {
      // 고객 포인트 정보 조회
      final points = await getOrCreateCustomerPoints(phoneNumber);

      // 잔액 확인
      if (!points.canUsePoints(amount)) {
        throw AppException('포인트가 부족합니다 (보유: ${points.currentPoints}P, 필요: ${amount}P)');
      }

      final newBalance = points.currentPoints - amount;

      // Firestore 트랜잭션
      await _firestore.runTransaction((transaction) async {
        // CustomerPoints 문서 참조
        final pointsRef = _firestore
            .collection('regions')
            .doc(_repository.regionId)
            .collection('offices')
            .doc(_repository.officeId)
            .collection('customerPoints')
            .doc(phoneNumber);

        // PointTransaction 문서 참조
        final transactionRef = _firestore
            .collection('regions')
            .doc(_repository.regionId)
            .collection('offices')
            .doc(_repository.officeId)
            .collection('pointTransactions')
            .doc();

        // CustomerPoints 업데이트
        final updatedPoints = points.copyWith(
          currentPoints: newBalance,
          totalUsed: points.totalUsed + amount,
        );

        transaction.set(pointsRef, updatedPoints.toFirestore());

        // PointTransaction 기록
        final pointTransaction = PointTransaction.use(
          id: transactionRef.id,
          customerId: phoneNumber,
          amount: amount,
          balance: newBalance,
          callId: callId,
          description: description,
          grade: points.grade.toFirestore(),
        );

        transaction.set(transactionRef, pointTransaction.toFirestore());
      });

      return true;
    } catch (e) {
      if (e is AppException) rethrow;
      throw AppException('포인트 사용에 실패했습니다: $e');
    }
  }

  /// 고객 포인트 정보 실시간 스트림
  Stream<CustomerPoints?> observeCustomerPoints(String phoneNumber) {
    return _repository.observeCustomerPoints(phoneNumber);
  }

  /// 포인트 거래 내역 조회
  Future<List<PointTransaction>> getPointTransactions(
    String phoneNumber, {
    int limit = 50,
  }) async {
    return await _repository.getPointTransactions(phoneNumber, limit: limit);
  }

  /// 포인트 거래 내역 실시간 스트림
  Stream<List<PointTransaction>> observePointTransactions(
    String phoneNumber, {
    int limit = 50,
  }) {
    return _repository.observePointTransactions(phoneNumber, limit: limit);
  }

  /// 특정 기간의 포인트 거래 내역 조회
  Future<List<PointTransaction>> getPointTransactionsByDateRange(
    String phoneNumber,
    DateTime startDate,
    DateTime endDate,
  ) async {
    return await _repository.getPointTransactionsByDateRange(
      phoneNumber,
      startDate,
      endDate,
    );
  }

  /// 포인트 거래 타입별 조회
  Future<List<PointTransaction>> getPointTransactionsByType(
    String phoneNumber,
    TransactionType type, {
    int limit = 50,
  }) async {
    return await _repository.getPointTransactionsByType(
      phoneNumber,
      type,
      limit: limit,
    );
  }

  /// 포인트 적립 미리보기 (실제 적립 없이 금액만 계산)
  Future<Map<String, dynamic>> previewEarnPoints(
    String phoneNumber,
    int fare,
  ) async {
    final points = await getOrCreateCustomerPoints(phoneNumber);
    final earnAmount = points.calculateEarnPoints(fare);
    final newBalance = points.currentPoints + earnAmount;
    final newTotalCalls = points.totalCalls + 1;
    final newGrade = CustomerGrade.fromCallCount(newTotalCalls);

    return {
      'currentPoints': points.currentPoints,
      'earnAmount': earnAmount,
      'newBalance': newBalance,
      'currentGrade': points.grade,
      'newGrade': newGrade,
      'gradeUpgrade': newGrade != points.grade,
    };
  }

  /// 사용 가능한 최대 포인트 조회
  Future<int> getAvailablePoints(String phoneNumber) async {
    final points = await getOrCreateCustomerPoints(phoneNumber);
    return points.currentPoints;
  }

  /// 포인트 사용 가능 여부 확인
  Future<bool> canUsePoints(String phoneNumber, int amount) async {
    final points = await getOrCreateCustomerPoints(phoneNumber);
    return points.canUsePoints(amount);
  }
}
