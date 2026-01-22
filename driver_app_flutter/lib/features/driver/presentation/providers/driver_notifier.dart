import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/di/injection.dart';
import '../../domain/entities/driver.dart';
import '../../domain/usecases/update_driver_status_usecase.dart';
import '../../../auth/presentation/providers/auth_notifier.dart';
import 'driver_state.dart';

class DriverNotifier extends StateNotifier<DriverState> {
  final UpdateDriverStatusUseCase _updateDriverStatusUseCase;

  DriverNotifier({
    required UpdateDriverStatusUseCase updateDriverStatusUseCase,
  })  : _updateDriverStatusUseCase = updateDriverStatusUseCase,
        super(const DriverState.initial());

  /// 기사 상태 업데이트
  Future<void> updateStatus(DriverStatus status) async {
    state = const DriverState.loading();

    final result = await _updateDriverStatusUseCase(
      UpdateDriverStatusParams(status: status),
    );

    result.fold(
      (failure) => state = DriverState.error(failure.message),
      (driver) => state = DriverState.loaded(driver),
    );
  }

  /// 상태 초기화
  void reset() {
    state = const DriverState.initial();
  }
}

/// DriverNotifier Provider
final driverNotifierProvider =
    StateNotifierProvider<DriverNotifier, DriverState>((ref) {
  return DriverNotifier(
    updateDriverStatusUseCase: sl(),
  );
});

/// Current Driver Provider (편의성)
final currentDriverProvider = Provider<Driver?>((ref) {
  final driverState = ref.watch(driverNotifierProvider);
  return driverState.maybeWhen(
    loaded: (driver) => driver,
    orElse: () => null,
  );
});
