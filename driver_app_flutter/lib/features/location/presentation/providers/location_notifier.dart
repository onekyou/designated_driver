import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/di/injection.dart';
import '../../../../core/usecases/usecase.dart';
import '../../domain/entities/position.dart';
import '../../domain/usecases/get_current_location_usecase.dart';
import 'location_state.dart';

class LocationNotifier extends StateNotifier<LocationState> {
  final GetCurrentLocationUseCase _getCurrentLocationUseCase;

  LocationNotifier({
    required GetCurrentLocationUseCase getCurrentLocationUseCase,
  })  : _getCurrentLocationUseCase = getCurrentLocationUseCase,
        super(const LocationState.initial());

  /// 현재 위치 조회
  Future<Position?> getCurrentPosition() async {
    state = const LocationState.loading();

    final result = await _getCurrentLocationUseCase(NoParams());

    return result.fold(
      (failure) {
        state = LocationState.error(failure.message);
        return null;
      },
      (position) {
        state = LocationState.loaded(position);
        return position;
      },
    );
  }

  /// 상태 초기화
  void reset() {
    state = const LocationState.initial();
  }
}

/// LocationNotifier Provider
final locationNotifierProvider =
    StateNotifierProvider<LocationNotifier, LocationState>((ref) {
  return LocationNotifier(
    getCurrentLocationUseCase: sl(),
  );
});

/// Current Position Provider (편의성)
final currentPositionProvider = Provider<Position?>((ref) {
  final locationState = ref.watch(locationNotifierProvider);
  return locationState.maybeWhen(
    loaded: (position) => position,
    orElse: () => null,
  );
});
