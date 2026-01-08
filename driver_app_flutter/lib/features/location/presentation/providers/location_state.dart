import 'package:freezed_annotation/freezed_annotation.dart';
import '../../domain/entities/position.dart';

part 'location_state.freezed.dart';

@freezed
class LocationState with _$LocationState {
  const factory LocationState.initial() = _Initial;
  const factory LocationState.loading() = _Loading;
  const factory LocationState.loaded(Position position) = _Loaded;
  const factory LocationState.error(String message) = _Error;
}
