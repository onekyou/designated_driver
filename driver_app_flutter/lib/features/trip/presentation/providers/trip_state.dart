import 'package:freezed_annotation/freezed_annotation.dart';
import '../../domain/entities/trip.dart';

part 'trip_state.freezed.dart';

@freezed
class TripState with _$TripState {
  const factory TripState.initial() = _Initial;
  const factory TripState.loading() = _Loading;
  const factory TripState.loaded(Trip? currentTrip) = _Loaded;
  const factory TripState.error(String message) = _Error;
}
