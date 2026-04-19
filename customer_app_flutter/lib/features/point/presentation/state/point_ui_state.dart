import 'package:freezed_annotation/freezed_annotation.dart';

import '../../domain/entities/customer_points.dart';

part 'point_ui_state.freezed.dart';

/// PointNotifier UiState (MODELS.md §7.4)
@freezed
class PointUiState with _$PointUiState {
  const factory PointUiState({
    CustomerPoints? customerPoints,
    @Default(false) bool isLoadingPoints,
    @Default(false) bool usePoints,
    @Default(0) int pointsToUse,
    @Default(false) bool showPointsEarnedDialog,
    @Default(0) int earnedPoints,
    @Default(0) int usedPoints,
    @Default(0) int rideCompletedFare,
  }) = _PointUiState;
}
