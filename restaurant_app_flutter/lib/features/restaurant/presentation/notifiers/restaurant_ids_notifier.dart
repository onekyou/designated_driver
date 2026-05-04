import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../../core/providers.dart';
import '../../domain/entities/restaurant_ids.dart';

/// SharedPreferences-backed 식당 ID 관리.
/// 가입 직후 `save()` 호출 → state 갱신 → restaurantStreamProvider/transactionsStreamProvider가 자동 활성화.
class RestaurantIdsNotifier extends StateNotifier<RestaurantIds?> {
  RestaurantIdsNotifier(this._prefs) : super(_load(_prefs));

  final SharedPreferences _prefs;

  static RestaurantIds? _load(SharedPreferences prefs) {
    final pid = prefs.getString('provinceId');
    final cid = prefs.getString('cityId');
    final oid = prefs.getString('officeId');
    final rid = prefs.getString('restaurantId');
    if (pid == null || cid == null || oid == null || rid == null) return null;
    return RestaurantIds(
      provinceId: pid,
      cityId: cid,
      officeId: oid,
      restaurantId: rid,
    );
  }

  Future<void> save(RestaurantIds ids) async {
    await _prefs.setString('provinceId', ids.provinceId);
    await _prefs.setString('cityId', ids.cityId);
    await _prefs.setString('officeId', ids.officeId);
    await _prefs.setString('restaurantId', ids.restaurantId);
    state = ids;
  }

  Future<void> clear() async {
    await _prefs.remove('provinceId');
    await _prefs.remove('cityId');
    await _prefs.remove('officeId');
    await _prefs.remove('restaurantId');
    state = null;
  }
}

final restaurantIdsProvider =
    StateNotifierProvider<RestaurantIdsNotifier, RestaurantIds?>((ref) {
  return RestaurantIdsNotifier(ref.watch(sharedPreferencesProvider));
});
