import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/restaurant_repository.dart';
import '../../domain/entities/restaurant_info.dart';
import '../../domain/entities/restaurant_transaction.dart';
import 'restaurant_ids_notifier.dart';

/// 식당 doc stream — 잔액 + fcmToken 변동 실시간.
/// ids null (가입 전) → null 한 번만 emit.
final restaurantStreamProvider = StreamProvider<RestaurantInfo?>((ref) {
  final ids = ref.watch(restaurantIdsProvider);
  if (ids == null) return Stream.value(null);
  return ref.watch(restaurantRepositoryProvider).watchRestaurant(
        provinceId: ids.provinceId,
        cityId: ids.cityId,
        officeId: ids.officeId,
        restaurantId: ids.restaurantId,
      );
});

/// 식당 거래 내역 stream (최근 50건, timestamp DESC).
final restaurantTransactionsStreamProvider =
    StreamProvider<List<RestaurantTransaction>>((ref) {
  final ids = ref.watch(restaurantIdsProvider);
  if (ids == null) return Stream.value(<RestaurantTransaction>[]);
  return ref.watch(restaurantRepositoryProvider).watchTransactions(
        provinceId: ids.provinceId,
        cityId: ids.cityId,
        officeId: ids.officeId,
        restaurantId: ids.restaurantId,
      );
});
