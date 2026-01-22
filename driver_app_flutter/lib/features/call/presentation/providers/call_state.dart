import 'package:freezed_annotation/freezed_annotation.dart';
import '../../domain/entities/call.dart';

part 'call_state.freezed.dart';

@freezed
class CallState with _$CallState {
  const factory CallState.initial() = _Initial;
  const factory CallState.loading() = _Loading;
  const factory CallState.loaded(Call? assignedCall) = _Loaded;
  const factory CallState.error(String message) = _Error;
}
