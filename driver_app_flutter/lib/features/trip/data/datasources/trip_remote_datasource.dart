import 'package:cloud_firestore/cloud_firestore.dart';
import '../../../../core/constants/app_constants.dart';
import '../../../../core/error/exceptions.dart';
import '../../domain/entities/trip.dart';
import '../models/trip_model.dart';

/// 운행 관련 원격 데이터 소스 (Firebase)
abstract class TripRemoteDataSource {
  /// 운행 시작
  Future<TripModel> startTrip({
    required String callId,
    required String driverId,
    required String driverName,
    required String phoneNumber,
    String? customerName,
    required String pickupLocation,
    required String destination,
    required int fare,
    required String regionId,
    required String officeId,
  });

  /// 운행 완료
  Future<void> completeTrip({
    required String tripId,
    required String regionId,
    required String officeId,
    int? pointsUsed,
    int? pointsEarned,
  });

  /// 운행 취소
  Future<void> cancelTrip({
    required String tripId,
    required String regionId,
    required String officeId,
    String? reason,
  });

  /// 현재 운행 중인 Trip 가져오기
  Future<TripModel?> getCurrentTrip({
    required String driverId,
    required String regionId,
    required String officeId,
  });

  /// 운행 내역 가져오기
  Future<List<TripModel>> getTripHistory({
    required String driverId,
    required String regionId,
    required String officeId,
    int limit = 20,
  });

  /// 운행 상세 정보
  Future<TripModel> getTripById({
    required String tripId,
    required String regionId,
    required String officeId,
  });
}

class TripRemoteDataSourceImpl implements TripRemoteDataSource {
  final FirebaseFirestore firestore;

  TripRemoteDataSourceImpl({required this.firestore});

  /// Firestore 운행 참조 헬퍼
  CollectionReference _getTripsCollection(String regionId, String officeId) {
    return firestore
        .collection(AppConstants.collectionRegions)
        .doc(regionId)
        .collection(AppConstants.collectionOffices)
        .doc(officeId)
        .collection('trips'); // trips 컬렉션
  }

  @override
  Future<TripModel> startTrip({
    required String callId,
    required String driverId,
    required String driverName,
    required String phoneNumber,
    String? customerName,
    required String pickupLocation,
    required String destination,
    required int fare,
    required String regionId,
    required String officeId,
  }) async {
    try {
      final tripData = {
        'callId': callId,
        'driverId': driverId,
        'driverName': driverName,
        'phoneNumber': phoneNumber,
        'customerName': customerName,
        'pickupLocation': pickupLocation,
        'destination': destination,
        'fare': fare,
        'status': TripStatus.inProgress.value,
        'startTime': FieldValue.serverTimestamp(),
        'pointsUsed': null,
        'pointsEarned': null,
        'endTime': null,
        'notes': null,
      };

      final docRef = await _getTripsCollection(regionId, officeId).add(tripData);
      final doc = await docRef.get();

      return TripModel.fromFirestore(doc);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '운행 시작 실패');
    }
  }

  @override
  Future<void> completeTrip({
    required String tripId,
    required String regionId,
    required String officeId,
    int? pointsUsed,
    int? pointsEarned,
  }) async {
    try {
      final updateData = {
        'status': TripStatus.completed.value,
        'endTime': FieldValue.serverTimestamp(),
      };

      if (pointsUsed != null) {
        updateData['pointsUsed'] = pointsUsed;
      }
      if (pointsEarned != null) {
        updateData['pointsEarned'] = pointsEarned;
      }

      await _getTripsCollection(regionId, officeId).doc(tripId).update(updateData);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '운행 완료 실패');
    }
  }

  @override
  Future<void> cancelTrip({
    required String tripId,
    required String regionId,
    required String officeId,
    String? reason,
  }) async {
    try {
      final updateData = {
        'status': TripStatus.cancelled.value,
        'endTime': FieldValue.serverTimestamp(),
      };

      if (reason != null) {
        updateData['notes'] = reason;
      }

      await _getTripsCollection(regionId, officeId).doc(tripId).update(updateData);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '운행 취소 실패');
    }
  }

  @override
  Future<TripModel?> getCurrentTrip({
    required String driverId,
    required String regionId,
    required String officeId,
  }) async {
    try {
      final querySnapshot = await _getTripsCollection(regionId, officeId)
          .where('driverId', isEqualTo: driverId)
          .where('status', isEqualTo: TripStatus.inProgress.value)
          .orderBy('startTime', descending: true)
          .limit(1)
          .get();

      if (querySnapshot.docs.isEmpty) {
        return null;
      }

      return TripModel.fromFirestore(querySnapshot.docs.first);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '현재 운행 조회 실패');
    }
  }

  @override
  Future<List<TripModel>> getTripHistory({
    required String driverId,
    required String regionId,
    required String officeId,
    int limit = 20,
  }) async {
    try {
      final querySnapshot = await _getTripsCollection(regionId, officeId)
          .where('driverId', isEqualTo: driverId)
          .where('status', isEqualTo: TripStatus.completed.value)
          .orderBy('endTime', descending: true)
          .limit(limit)
          .get();

      return querySnapshot.docs
          .map((doc) => TripModel.fromFirestore(doc))
          .toList();
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '운행 내역 조회 실패');
    }
  }

  @override
  Future<TripModel> getTripById({
    required String tripId,
    required String regionId,
    required String officeId,
  }) async {
    try {
      final doc = await _getTripsCollection(regionId, officeId).doc(tripId).get();

      if (!doc.exists) {
        throw ServerException('운행 정보를 찾을 수 없습니다.');
      }

      return TripModel.fromFirestore(doc);
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? '운행 조회 실패');
    }
  }
}
