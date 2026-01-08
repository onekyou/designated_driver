import 'package:freezed_annotation/freezed_annotation.dart';
import '../../domain/entities/driver.dart';

part 'driver_state.freezed.dart';

@freezed
class DriverState with _$DriverState {
  const factory DriverState.initial() = _Initial;
  const factory DriverState.loading() = _Loading;
  const factory DriverState.loaded(Driver driver) = _Loaded;
  const factory DriverState.error(String message) = _Error;
}
