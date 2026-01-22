import 'package:cloud_firestore/cloud_firestore.dart';
import '../../../../core/constants/app_constants.dart';
import '../../../../core/error/exceptions.dart';
import '../../domain/entities/call.dart';
import '../models/call_model.dart';

/// 콜 관련 원격 데이터 소스 (Firebase)
abstract class CallRemoteDataSource {
  /// 기사에게 배정된 콜 가져오기 (단건)
  Future<CallModel?> getAssignedCall({
    required String driverId,
    required String regionId,
    required String officeId,
  });

  /// 콜 상세 정보 가져오기
  Future<CallModel> getCallById({
    required String callId,
    required String regionId,
    required String officeId,
  });

  /// 콜 수락
  Future<void> acceptCall({
    required String callId,
    required String regionId,
    required String officeId,
  });

  /// 콜 거절
  Future<void> rejectCall({
    required String callId,
    required String regionId,
    required String officeId,
    String? reason,
  });

  /// 콜 완료
  Future<void> completeCall({
    required String callId,
    required String regionId,
    required String officeId,
    required int fare,
    String? pickupLocation,
    String? destination,
    int? pointsUsed,
  });

  /// 콜 상태 업데이트
  Future<void> updateCallStatus({
    required String callId,
    required String regionId,
    required String officeId,
    required CallStatus status,
  });

  /// 콜 리스트 (실시간 스트림)
  Stream<List<CallModel>> watchAssignedCalls({
    required String driverId,
    required String regionId,
    required String officeId,
  });
}

class CallRemoteDataSourceImpl implements CallRemoteDataSource {
  final FirebaseFirestore firestore;

  CallRemoteDataSourceImpl({required this.firestore});

  /// Firestore 콜 참조 헬퍼
  CollectionReference _getCallsCollection(String regionId, String officeId) {
    return firestore
        .collection(AppConstants.collectionRegions)
        .doc(regionId)
        .collection(AppConstants.collectionOffices)
        .doc(officeId)
        .collection(AppConstants.collectionCalls);
  }

  @override
  Future<CallModel?> getAssignedCall({
    required String driverId,
    required String regionId,
    required String officeId,
  }) async {
    try {
      final querySnapshot = await _getCallsCollection(regionId, officeId)
          .where(AppConstants.fieldAssignedDriverId, isEqualTo: driverId)
          .where(AppConstants.fieldStatus, whereIn: [
            AppConstants.callStatusAssigned,
            AppConstants.callStatusAccepted,
          ])
          .orderBy(AppConstants.fieldAssignedTime, descending: true)
          .limit(1)
          .get();

      if (querySnapshot.docs.isEmpty) {
        return null;
      }

      return CallModel.fromFirestore(querySnapshot.docs.first);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '콜 조회 실패');
    }
  }

  @override
  Future<CallModel> getCallById({
    required String callId,
    required String regionId,
    required String officeId,
  }) async {
    try {
      final doc = await _getCallsCollection(regionId, officeId).doc(callId).get();

      if (!doc.exists) {
        throw ServerException('콜을 찾을 수 없습니다.');
      }

      return CallModel.fromFirestore(doc);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '콜 조회 실패');
    }
  }

  @override
  Future<void> acceptCall({
    required String callId,
    required String regionId,
    required String officeId,
  }) async {
    try {
      await _getCallsCollection(regionId, officeId).doc(callId).update({
        AppConstants.fieldStatus: AppConstants.callStatusAccepted,
        AppConstants.fieldAcceptedTime: FieldValue.serverTimestamp(),
      });
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '콜 수락 실패');
    }
  }

  @override
  Future<void> rejectCall({
    required String callId,
    required String regionId,
    required String officeId,
    String? reason,
  }) async {
    try {
      // 콜을 거절하면 배정 정보를 제거하고 PENDING 상태로 되돌림
      final updateData = {
        AppConstants.fieldStatus: AppConstants.callStatusPending,
        AppConstants.fieldAssignedDriverId: FieldValue.delete(),
        AppConstants.fieldAssignedDriverName: FieldValue.delete(),
        AppConstants.fieldAssignedTime: FieldValue.delete(),
      };

      if (reason != null) {
        updateData['rejectionReason'] = reason;
      }

      await _getCallsCollection(regionId, officeId).doc(callId).update(updateData);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '콜 거절 실패');
    }
  }

  @override
  Future<void> completeCall({
    required String callId,
    required String regionId,
    required String officeId,
    required int fare,
    String? pickupLocation,
    String? destination,
    int? pointsUsed,
  }) async {
    try {
      final updateData = {
        AppConstants.fieldStatus: AppConstants.callStatusCompleted,
        AppConstants.fieldCompletedTime: FieldValue.serverTimestamp(),
        AppConstants.fieldFare: fare,
      };

      if (pickupLocation != null) {
        updateData[AppConstants.fieldPickupLocation] = pickupLocation;
      }
      if (destination != null) {
        updateData[AppConstants.fieldDestination] = destination;
      }
      if (pointsUsed != null) {
        updateData[AppConstants.fieldPointsUsed] = pointsUsed;
      }

      await _getCallsCollection(regionId, officeId).doc(callId).update(updateData);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '콜 완료 실패');
    }
  }

  @override
  Future<void> updateCallStatus({
    required String callId,
    required String regionId,
    required String officeId,
    required CallStatus status,
  }) async {
    try {
      await _getCallsCollection(regionId, officeId).doc(callId).update({
        AppConstants.fieldStatus: status.value,
      });
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '콜 상태 업데이트 실패');
    }
  }

  @override
  Stream<List<CallModel>> watchAssignedCalls({
    required String driverId,
    required String regionId,
    required String officeId,
  }) {
    return _getCallsCollection(regionId, officeId)
        .where(AppConstants.fieldAssignedDriverId, isEqualTo: driverId)
        .where(AppConstants.fieldStatus, whereIn: [
          AppConstants.callStatusAssigned,
          AppConstants.callStatusAccepted,
          AppConstants.callStatusPickedUp,
        ])
        .orderBy(AppConstants.fieldAssignedTime, descending: true)
        .snapshots()
        .map((snapshot) {
          return snapshot.docs.map((doc) => CallModel.fromFirestore(doc)).toList();
        });
  }
}
