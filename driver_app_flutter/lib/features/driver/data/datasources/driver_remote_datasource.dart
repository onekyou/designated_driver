import 'package:cloud_firestore/cloud_firestore.dart';
import '../../../../core/constants/app_constants.dart';
import '../../../../core/error/exceptions.dart';
import '../../domain/entities/driver.dart';
import '../models/driver_model.dart';

/// 기사 관련 원격 데이터 소스 (Firebase)
abstract class DriverRemoteDataSource {
  /// 기사 정보 가져오기
  Future<DriverModel> getDriverInfo({
    required String driverId,
    required String regionId,
    required String officeId,
  });

  /// 기사 상태 업데이트
  Future<void> updateDriverStatus({
    required String driverId,
    required String regionId,
    required String officeId,
    required DriverStatus status,
  });

  /// FCM 토큰 업데이트
  Future<void> updateFcmToken({
    required String driverId,
    required String regionId,
    required String officeId,
    required String fcmToken,
  });

  /// 현재 콜 ID 업데이트
  Future<void> updateCurrentCall({
    required String driverId,
    required String regionId,
    required String officeId,
    String? callId,
  });

  /// 기사 정보 실시간 감시
  Stream<DriverModel> watchDriverInfo({
    required String driverId,
    required String regionId,
    required String officeId,
  });
}

class DriverRemoteDataSourceImpl implements DriverRemoteDataSource {
  final FirebaseFirestore firestore;

  DriverRemoteDataSourceImpl({required this.firestore});

  /// Firestore 기사 문서 참조 헬퍼
  DocumentReference _getDriverDoc(String regionId, String officeId, String driverId) {
    return firestore
        .collection(AppConstants.collectionRegions)
        .doc(regionId)
        .collection(AppConstants.collectionOffices)
        .doc(officeId)
        .collection(AppConstants.collectionDrivers)
        .doc(driverId);
  }

  @override
  Future<DriverModel> getDriverInfo({
    required String driverId,
    required String regionId,
    required String officeId,
  }) async {
    try {
      final doc = await _getDriverDoc(regionId, officeId, driverId).get();

      if (!doc.exists) {
        throw ServerException('기사 정보를 찾을 수 없습니다.');
      }

      return DriverModel.fromFirestore(doc);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '기사 정보 조회 실패');
    }
  }

  @override
  Future<void> updateDriverStatus({
    required String driverId,
    required String regionId,
    required String officeId,
    required DriverStatus status,
  }) async {
    try {
      await _getDriverDoc(regionId, officeId, driverId).update({
        AppConstants.fieldStatus: status.value,
        'updatedAt': FieldValue.serverTimestamp(),
      });
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '기사 상태 업데이트 실패');
    }
  }

  @override
  Future<void> updateFcmToken({
    required String driverId,
    required String regionId,
    required String officeId,
    required String fcmToken,
  }) async {
    try {
      await _getDriverDoc(regionId, officeId, driverId).update({
        AppConstants.fieldFcmToken: fcmToken,
        'updatedAt': FieldValue.serverTimestamp(),
      });
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? 'FCM 토큰 업데이트 실패');
    }
  }

  @override
  Future<void> updateCurrentCall({
    required String driverId,
    required String regionId,
    required String officeId,
    String? callId,
  }) async {
    try {
      final updateData = <String, dynamic>{
        'updatedAt': FieldValue.serverTimestamp(),
      };

      if (callId != null) {
        updateData['currentCallId'] = callId;
      } else {
        updateData['currentCallId'] = FieldValue.delete();
      }

      await _getDriverDoc(regionId, officeId, driverId).update(updateData);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '현재 콜 ID 업데이트 실패');
    }
  }

  @override
  Stream<DriverModel> watchDriverInfo({
    required String driverId,
    required String regionId,
    required String officeId,
  }) {
    return _getDriverDoc(regionId, officeId, driverId)
        .snapshots()
        .map((doc) {
      if (!doc.exists) {
        throw ServerException('기사 정보를 찾을 수 없습니다.');
      }
      return DriverModel.fromFirestore(doc);
    });
  }
}
