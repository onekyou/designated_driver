import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/di/injection.dart';
import '../../../../core/usecases/usecase.dart';
import '../../domain/usecases/start_trip_usecase.dart';
import '../../domain/usecases/complete_trip_usecase.dart';
import '../../domain/usecases/get_trip_history_usecase.dart';
import 'trip_state.dart';

class TripNotifier extends StateNotifier<TripState> {
  final StartTripUseCase _startTripUseCase;
  final CompleteTripUseCase _completeTripUseCase;
  final GetTripHistoryUseCase _getTripHistoryUseCase;

  TripNotifier({
    required StartTripUseCase startTripUseCase,
    required CompleteTripUseCase completeTripUseCase,
    required GetTripHistoryUseCase getTripHistoryUseCase,
  })  : _startTripUseCase = startTripUseCase,
        _completeTripUseCase = completeTripUseCase,
        _getTripHistoryUseCase = getTripHistoryUseCase,
        super(const TripState.initial());

  /// 운행 시작
  Future<void> startTrip({
    required String callId,
    required String departure,
    required String destination,
    String? waypoints,
    required int fare,
  }) async {
    state = const TripState.loading();

    final result = await _startTripUseCase(
      StartTripParams(
        callId: callId,
        departure: departure,
        destination: destination,
        waypoints: waypoints,
        fare: fare,
      ),
    );

    result.fold(
      (failure) => state = TripState.error(failure.message),
      (trip) => state = TripState.loaded(trip),
    );
  }

  /// 운행 완료
  Future<void> completeTrip(String tripId) async {
    state = const TripState.loading();

    final result = await _completeTripUseCase(
      CompleteTripParams(tripId: tripId),
    );

    result.fold(
      (failure) => state = TripState.error(failure.message),
      (trip) => state = TripState.loaded(trip),
    );
  }

  /// 상태 초기화
  void reset() {
    state = const TripState.initial();
  }
}

/// TripNotifier Provider
final tripNotifierProvider =
    StateNotifierProvider<TripNotifier, TripState>((ref) {
  return TripNotifier(
    startTripUseCase: sl(),
    completeTripUseCase: sl(),
    getTripHistoryUseCase: sl(),
  );
});

/// Current Trip Provider (편의성)
final currentTripProvider = Provider((ref) {
  final tripState = ref.watch(tripNotifierProvider);
  return tripState.maybeWhen(
    loaded: (currentTrip) => currentTrip,
    orElse: () => null,
  );
});
