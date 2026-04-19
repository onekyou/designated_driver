// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'call_ui_state.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

/// @nodoc
mixin _$CallUiState {
  String get currentLocation => throw _privateConstructorUsedError;
  String get destinationLocation => throw _privateConstructorUsedError;
  bool get isLoadingLocation => throw _privateConstructorUsedError;
  bool get isLoadingCall => throw _privateConstructorUsedError;
  String? get error => throw _privateConstructorUsedError;
  bool get showLocationCard => throw _privateConstructorUsedError;

  /// Create a copy of CallUiState
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $CallUiStateCopyWith<CallUiState> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $CallUiStateCopyWith<$Res> {
  factory $CallUiStateCopyWith(
    CallUiState value,
    $Res Function(CallUiState) then,
  ) = _$CallUiStateCopyWithImpl<$Res, CallUiState>;
  @useResult
  $Res call({
    String currentLocation,
    String destinationLocation,
    bool isLoadingLocation,
    bool isLoadingCall,
    String? error,
    bool showLocationCard,
  });
}

/// @nodoc
class _$CallUiStateCopyWithImpl<$Res, $Val extends CallUiState>
    implements $CallUiStateCopyWith<$Res> {
  _$CallUiStateCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of CallUiState
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? currentLocation = null,
    Object? destinationLocation = null,
    Object? isLoadingLocation = null,
    Object? isLoadingCall = null,
    Object? error = freezed,
    Object? showLocationCard = null,
  }) {
    return _then(
      _value.copyWith(
            currentLocation: null == currentLocation
                ? _value.currentLocation
                : currentLocation // ignore: cast_nullable_to_non_nullable
                      as String,
            destinationLocation: null == destinationLocation
                ? _value.destinationLocation
                : destinationLocation // ignore: cast_nullable_to_non_nullable
                      as String,
            isLoadingLocation: null == isLoadingLocation
                ? _value.isLoadingLocation
                : isLoadingLocation // ignore: cast_nullable_to_non_nullable
                      as bool,
            isLoadingCall: null == isLoadingCall
                ? _value.isLoadingCall
                : isLoadingCall // ignore: cast_nullable_to_non_nullable
                      as bool,
            error: freezed == error
                ? _value.error
                : error // ignore: cast_nullable_to_non_nullable
                      as String?,
            showLocationCard: null == showLocationCard
                ? _value.showLocationCard
                : showLocationCard // ignore: cast_nullable_to_non_nullable
                      as bool,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$CallUiStateImplCopyWith<$Res>
    implements $CallUiStateCopyWith<$Res> {
  factory _$$CallUiStateImplCopyWith(
    _$CallUiStateImpl value,
    $Res Function(_$CallUiStateImpl) then,
  ) = __$$CallUiStateImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    String currentLocation,
    String destinationLocation,
    bool isLoadingLocation,
    bool isLoadingCall,
    String? error,
    bool showLocationCard,
  });
}

/// @nodoc
class __$$CallUiStateImplCopyWithImpl<$Res>
    extends _$CallUiStateCopyWithImpl<$Res, _$CallUiStateImpl>
    implements _$$CallUiStateImplCopyWith<$Res> {
  __$$CallUiStateImplCopyWithImpl(
    _$CallUiStateImpl _value,
    $Res Function(_$CallUiStateImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of CallUiState
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? currentLocation = null,
    Object? destinationLocation = null,
    Object? isLoadingLocation = null,
    Object? isLoadingCall = null,
    Object? error = freezed,
    Object? showLocationCard = null,
  }) {
    return _then(
      _$CallUiStateImpl(
        currentLocation: null == currentLocation
            ? _value.currentLocation
            : currentLocation // ignore: cast_nullable_to_non_nullable
                  as String,
        destinationLocation: null == destinationLocation
            ? _value.destinationLocation
            : destinationLocation // ignore: cast_nullable_to_non_nullable
                  as String,
        isLoadingLocation: null == isLoadingLocation
            ? _value.isLoadingLocation
            : isLoadingLocation // ignore: cast_nullable_to_non_nullable
                  as bool,
        isLoadingCall: null == isLoadingCall
            ? _value.isLoadingCall
            : isLoadingCall // ignore: cast_nullable_to_non_nullable
                  as bool,
        error: freezed == error
            ? _value.error
            : error // ignore: cast_nullable_to_non_nullable
                  as String?,
        showLocationCard: null == showLocationCard
            ? _value.showLocationCard
            : showLocationCard // ignore: cast_nullable_to_non_nullable
                  as bool,
      ),
    );
  }
}

/// @nodoc

class _$CallUiStateImpl implements _CallUiState {
  const _$CallUiStateImpl({
    this.currentLocation = '',
    this.destinationLocation = '',
    this.isLoadingLocation = false,
    this.isLoadingCall = false,
    this.error,
    this.showLocationCard = false,
  });

  @override
  @JsonKey()
  final String currentLocation;
  @override
  @JsonKey()
  final String destinationLocation;
  @override
  @JsonKey()
  final bool isLoadingLocation;
  @override
  @JsonKey()
  final bool isLoadingCall;
  @override
  final String? error;
  @override
  @JsonKey()
  final bool showLocationCard;

  @override
  String toString() {
    return 'CallUiState(currentLocation: $currentLocation, destinationLocation: $destinationLocation, isLoadingLocation: $isLoadingLocation, isLoadingCall: $isLoadingCall, error: $error, showLocationCard: $showLocationCard)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$CallUiStateImpl &&
            (identical(other.currentLocation, currentLocation) ||
                other.currentLocation == currentLocation) &&
            (identical(other.destinationLocation, destinationLocation) ||
                other.destinationLocation == destinationLocation) &&
            (identical(other.isLoadingLocation, isLoadingLocation) ||
                other.isLoadingLocation == isLoadingLocation) &&
            (identical(other.isLoadingCall, isLoadingCall) ||
                other.isLoadingCall == isLoadingCall) &&
            (identical(other.error, error) || other.error == error) &&
            (identical(other.showLocationCard, showLocationCard) ||
                other.showLocationCard == showLocationCard));
  }

  @override
  int get hashCode => Object.hash(
    runtimeType,
    currentLocation,
    destinationLocation,
    isLoadingLocation,
    isLoadingCall,
    error,
    showLocationCard,
  );

  /// Create a copy of CallUiState
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$CallUiStateImplCopyWith<_$CallUiStateImpl> get copyWith =>
      __$$CallUiStateImplCopyWithImpl<_$CallUiStateImpl>(this, _$identity);
}

abstract class _CallUiState implements CallUiState {
  const factory _CallUiState({
    final String currentLocation,
    final String destinationLocation,
    final bool isLoadingLocation,
    final bool isLoadingCall,
    final String? error,
    final bool showLocationCard,
  }) = _$CallUiStateImpl;

  @override
  String get currentLocation;
  @override
  String get destinationLocation;
  @override
  bool get isLoadingLocation;
  @override
  bool get isLoadingCall;
  @override
  String? get error;
  @override
  bool get showLocationCard;

  /// Create a copy of CallUiState
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$CallUiStateImplCopyWith<_$CallUiStateImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
