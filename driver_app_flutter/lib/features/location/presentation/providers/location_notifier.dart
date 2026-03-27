import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../domain/entities/location.dart';
import '../../domain/repositories/location_repository.dart';
import 'location_state.dart';

/// LocationNotifier — Repository 직접 사용 (UseCase 제거, Either 제거)
class LocationNotifier extends StateNotifier<LocationState> {
  final LocationRepository _repository;

  LocationNotifier({required LocationRepository repository})
      : _repository = repository,
        super(const LocationState.initial());

  /// 현재 위치 조회
  Future<void> getCurrentPosition() async {
    state = const LocationState.loading();
    try {
      final location = await _repository.getCurrentLocation();
      state = LocationState.loaded(location);
    } catch (e) {
      state = LocationState.error(e.toString());
    }
  }

  /// 상태 초기화
  void reset() {
    state = const LocationState.initial();
  }
}

/// LocationNotifier Provider — Phase 1.4에서 실제 DI 연결 예정
final locationNotifierProvider =
    StateNotifierProvider<LocationNotifier, LocationState>((ref) {
  throw UnimplementedError('Phase 1.4에서 providers.dart로 이동 예정');
});
