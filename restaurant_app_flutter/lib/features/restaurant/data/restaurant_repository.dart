import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../domain/entities/restaurant_info.dart';
import '../domain/entities/restaurant_transaction.dart';

/// `redeemRestaurantInviteCode` 호출 결과.
class RedeemResult {
  const RedeemResult({
    required this.restaurantId,
    required this.provinceId,
    required this.cityId,
    required this.officeId,
  });

  final String restaurantId;
  final String provinceId;
  final String cityId;
  final String officeId;
}

/// PR 1 callable CF + Firestore 직접 read.
///
/// callable CF (region asia-northeast3):
/// - `redeemRestaurantInviteCode` — 가입
/// - `createSharedCallFromRestaurant` — 호출 (단순/앱)
class RestaurantRepository {
  RestaurantRepository({
    required FirebaseFirestore firestore,
    required FirebaseFunctions functions,
  })  : _firestore = firestore,
        _functions = functions;

  final FirebaseFirestore _firestore;
  final FirebaseFunctions _functions;

  DocumentReference<Map<String, dynamic>> _restaurantRef({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String restaurantId,
  }) =>
      _firestore
          .collection('provinces')
          .doc(provinceId)
          .collection('cities')
          .doc(cityId)
          .collection('offices')
          .doc(officeId)
          .collection('restaurants')
          .doc(restaurantId);

  Future<RedeemResult> redeemInviteCode({
    required String code,
    required String provinceId,
    required String cityId,
    required String officeId,
    required String name,
    required String phone,
    String address = '',
    String taxiPhoneNumber = '',
    String fcmToken = '',
  }) async {
    final callable = _functions.httpsCallable('redeemRestaurantInviteCode');
    final result = await callable.call<Map<Object?, Object?>>({
      'code': code,
      'provinceId': provinceId,
      'cityId': cityId,
      'officeId': officeId,
      'name': name,
      'phone': phone,
      'address': address,
      'taxiPhoneNumber': taxiPhoneNumber,
      'fcmToken': fcmToken,
    });
    final data = Map<String, dynamic>.from(result.data);
    return RedeemResult(
      restaurantId: data['restaurantId'] as String,
      provinceId: data['provinceId'] as String,
      cityId: data['cityId'] as String,
      officeId: data['officeId'] as String,
    );
  }

  /// `paymentMethod` = 'CASH' (default) | 'RESTAURANT_POINT'
  Future<String> createCallFromRestaurant({
    required String restaurantId,
    required String provinceId,
    required String cityId,
    required String officeId,
    String? departure,
    String? destination,
    String paymentMethod = 'CASH',
  }) async {
    final callable = _functions.httpsCallable('createSharedCallFromRestaurant');
    final payload = <String, dynamic>{
      'restaurantId': restaurantId,
      'provinceId': provinceId,
      'cityId': cityId,
      'officeId': officeId,
      'paymentMethod': paymentMethod,
    };
    if (departure != null && departure.isNotEmpty) {
      payload['departure'] = departure;
    }
    if (destination != null && destination.isNotEmpty) {
      payload['destination'] = destination;
    }
    final result = await callable.call<Map<Object?, Object?>>(payload);
    final data = Map<String, dynamic>.from(result.data);
    return data['sharedCallId'] as String;
  }

  Stream<RestaurantInfo?> watchRestaurant({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String restaurantId,
  }) {
    return _restaurantRef(
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
      restaurantId: restaurantId,
    ).snapshots().map((snap) {
      if (!snap.exists) return null;
      return RestaurantInfo.fromJson(snap.data()!);
    });
  }

  /// 거래 내역 stream (timestamp DESC, limit 50).
  Stream<List<RestaurantTransaction>> watchTransactions({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String restaurantId,
    int limit = 50,
  }) {
    return _restaurantRef(
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
      restaurantId: restaurantId,
    )
        .collection('transactions')
        .orderBy('timestamp', descending: true)
        .limit(limit)
        .snapshots()
        .map((snap) => snap.docs
            .map((d) => RestaurantTransaction.fromJson(d.data()))
            .toList());
  }

  Future<void> updateFcmToken({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String restaurantId,
    required String fcmToken,
    required String platform,
  }) async {
    await _restaurantRef(
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
      restaurantId: restaurantId,
    ).set({
      'fcmToken': fcmToken,
      'fcmTokenPlatform': platform,
      'fcmTokenUpdatedAt': Timestamp.now(),
    }, SetOptions(merge: true));
  }
}

final restaurantRepositoryProvider = Provider<RestaurantRepository>((ref) {
  return RestaurantRepository(
    firestore: ref.watch(firestoreProvider),
    functions: ref.watch(cloudFunctionsProvider),
  );
});
