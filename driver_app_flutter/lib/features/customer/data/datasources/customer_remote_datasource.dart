import 'package:cloud_firestore/cloud_firestore.dart';
import '../../../../core/constants/app_constants.dart';
import '../../../../core/error/exceptions.dart';
import '../../domain/entities/customer_points.dart';
import '../models/customer_points_model.dart';

/// 고객 포인트 관련 원격 데이터 소스 (Firebase)
abstract class CustomerRemoteDataSource {
  /// 고객 포인트 정보 가져오기
  Future<CustomerPointsModel?> getCustomerPoints(String phoneNumber);

  /// 포인트 사용
  Future<void> usePoints({
    required String customerId,
    required int amount,
  });

  /// 포인트 적립
  Future<int> earnPoints({
    required String customerId,
    required int fare,
    required CustomerGrade grade,
  });

  /// 신규 고객 포인트 생성
  Future<CustomerPointsModel> createCustomerPoints(String phoneNumber);
}

class CustomerRemoteDataSourceImpl implements CustomerRemoteDataSource {
  final FirebaseFirestore firestore;

  CustomerRemoteDataSourceImpl({required this.firestore});

  /// Firestore 고객 포인트 컬렉션 참조
  CollectionReference get _customerPointsCollection {
    return firestore.collection(AppConstants.collectionCustomerPoints);
  }

  @override
  Future<CustomerPointsModel?> getCustomerPoints(String phoneNumber) async {
    try {
      final querySnapshot = await _customerPointsCollection
          .where(AppConstants.fieldPhoneNumber, isEqualTo: phoneNumber)
          .limit(1)
          .get();

      if (querySnapshot.docs.isEmpty) {
        return null;
      }

      return CustomerPointsModel.fromFirestore(querySnapshot.docs.first);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '고객 포인트 조회 실패');
    }
  }

  @override
  Future<void> usePoints({
    required String customerId,
    required int amount,
  }) async {
    try {
      await firestore.runTransaction((transaction) async {
        final docRef = _customerPointsCollection.doc(customerId);
        final snapshot = await transaction.get(docRef);

        if (!snapshot.exists) {
          throw ServerException('고객 포인트 정보를 찾을 수 없습니다.');
        }

        final data = snapshot.data() as Map<String, dynamic>;
        final currentPoints = data[AppConstants.fieldCurrentPoints] as int;
        final totalUsed = data['totalUsed'] as int? ?? 0;

        if (currentPoints < amount) {
          throw ServerException('포인트가 부족합니다.');
        }

        transaction.update(docRef, {
          AppConstants.fieldCurrentPoints: currentPoints - amount,
          'totalUsed': totalUsed + amount,
          'lastUpdated': FieldValue.serverTimestamp(),
        });
      });
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '포인트 사용 실패');
    }
  }

  @override
  Future<int> earnPoints({
    required String customerId,
    required int fare,
    required CustomerGrade grade,
  }) async {
    try {
      final earnedPoints = (fare * grade.earningRate).round();

      await firestore.runTransaction((transaction) async {
        final docRef = _customerPointsCollection.doc(customerId);
        final snapshot = await transaction.get(docRef);

        if (!snapshot.exists) {
          throw ServerException('고객 포인트 정보를 찾을 수 없습니다.');
        }

        final data = snapshot.data() as Map<String, dynamic>;
        final currentPoints = data[AppConstants.fieldCurrentPoints] as int;
        final totalEarned = data['totalEarned'] as int? ?? 0;
        final totalCalls = data[AppConstants.fieldTotalCalls] as int? ?? 0;

        // 등급 업데이트 (콜 수에 따라)
        final newTotalCalls = totalCalls + 1;
        String newGrade = data[AppConstants.fieldGrade] as String;

        if (newTotalCalls >= 100) {
          newGrade = AppConstants.gradeVip;
        } else if (newTotalCalls >= 30) {
          newGrade = AppConstants.gradeGold;
        } else if (newTotalCalls >= 10) {
          newGrade = AppConstants.gradeSilver;
        }

        transaction.update(docRef, {
          AppConstants.fieldCurrentPoints: currentPoints + earnedPoints,
          'totalEarned': totalEarned + earnedPoints,
          AppConstants.fieldTotalCalls: newTotalCalls,
          AppConstants.fieldGrade: newGrade,
          'lastUpdated': FieldValue.serverTimestamp(),
        });
      });

      return earnedPoints;
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '포인트 적립 실패');
    }
  }

  @override
  Future<CustomerPointsModel> createCustomerPoints(String phoneNumber) async {
    try {
      final docRef = await _customerPointsCollection.add({
        AppConstants.fieldPhoneNumber: phoneNumber,
        AppConstants.fieldCurrentPoints: 0,
        'totalEarned': 0,
        'totalUsed': 0,
        AppConstants.fieldGrade: AppConstants.gradeBronze,
        AppConstants.fieldTotalCalls: 0,
        'lastUpdated': FieldValue.serverTimestamp(),
      });

      final doc = await docRef.get();
      return CustomerPointsModel.fromFirestore(doc);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '고객 포인트 생성 실패');
    }
  }
}
