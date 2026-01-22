// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'trip.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
    'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models');

/// @nodoc
mixin _$Trip {
  String get id => throw _privateConstructorUsedError;
  String get callId => throw _privateConstructorUsedError;
  String get driverId => throw _privateConstructorUsedError;
  String get driverName => throw _privateConstructorUsedError;
  String get phoneNumber => throw _privateConstructorUsedError;
  String? get customerName => throw _privateConstructorUsedError;
  String get pickupLocation => throw _privateConstructorUsedError;
  String get destination => throw _privateConstructorUsedError;
  int get fare => throw _privateConstructorUsedError;
  TripStatus get status => throw _privateConstructorUsedError;
  int? get pointsUsed => throw _privateConstructorUsedError;
  int? get pointsEarned => throw _privateConstructorUsedError;
  DateTime? get startTime => throw _privateConstructorUsedError;
  DateTime? get endTime => throw _privateConstructorUsedError;
  String? get notes => throw _privateConstructorUsedError;

  /// Create a copy of Trip
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $TripCopyWith<Trip> get copyWith => throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $TripCopyWith<$Res> {
  factory $TripCopyWith(Trip value, $Res Function(Trip) then) =
      _$TripCopyWithImpl<$Res, Trip>;
  @useResult
  $Res call(
      {String id,
      String callId,
      String driverId,
      String driverName,
      String phoneNumber,
      String? customerName,
      String pickupLocation,
      String destination,
      int fare,
      TripStatus status,
      int? pointsUsed,
      int? pointsEarned,
      DateTime? startTime,
      DateTime? endTime,
      String? notes});
}

/// @nodoc
class _$TripCopyWithImpl<$Res, $Val extends Trip>
    implements $TripCopyWith<$Res> {
  _$TripCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of Trip
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? callId = null,
    Object? driverId = null,
    Object? driverName = null,
    Object? phoneNumber = null,
    Object? customerName = freezed,
    Object? pickupLocation = null,
    Object? destination = null,
    Object? fare = null,
    Object? status = null,
    Object? pointsUsed = freezed,
    Object? pointsEarned = freezed,
    Object? startTime = freezed,
    Object? endTime = freezed,
    Object? notes = freezed,
  }) {
    return _then(_value.copyWith(
      id: null == id
          ? _value.id
          : id // ignore: cast_nullable_to_non_nullable
              as String,
      callId: null == callId
          ? _value.callId
          : callId // ignore: cast_nullable_to_non_nullable
              as String,
      driverId: null == driverId
          ? _value.driverId
          : driverId // ignore: cast_nullable_to_non_nullable
              as String,
      driverName: null == driverName
          ? _value.driverName
          : driverName // ignore: cast_nullable_to_non_nullable
              as String,
      phoneNumber: null == phoneNumber
          ? _value.phoneNumber
          : phoneNumber // ignore: cast_nullable_to_non_nullable
              as String,
      customerName: freezed == customerName
          ? _value.customerName
          : customerName // ignore: cast_nullable_to_non_nullable
              as String?,
      pickupLocation: null == pickupLocation
          ? _value.pickupLocation
          : pickupLocation // ignore: cast_nullable_to_non_nullable
              as String,
      destination: null == destination
          ? _value.destination
          : destination // ignore: cast_nullable_to_non_nullable
              as String,
      fare: null == fare
          ? _value.fare
          : fare // ignore: cast_nullable_to_non_nullable
              as int,
      status: null == status
          ? _value.status
          : status // ignore: cast_nullable_to_non_nullable
              as TripStatus,
      pointsUsed: freezed == pointsUsed
          ? _value.pointsUsed
          : pointsUsed // ignore: cast_nullable_to_non_nullable
              as int?,
      pointsEarned: freezed == pointsEarned
          ? _value.pointsEarned
          : pointsEarned // ignore: cast_nullable_to_non_nullable
              as int?,
      startTime: freezed == startTime
          ? _value.startTime
          : startTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      endTime: freezed == endTime
          ? _value.endTime
          : endTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      notes: freezed == notes
          ? _value.notes
          : notes // ignore: cast_nullable_to_non_nullable
              as String?,
    ) as $Val);
  }
}

/// @nodoc
abstract class _$$TripImplCopyWith<$Res> implements $TripCopyWith<$Res> {
  factory _$$TripImplCopyWith(
          _$TripImpl value, $Res Function(_$TripImpl) then) =
      __$$TripImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call(
      {String id,
      String callId,
      String driverId,
      String driverName,
      String phoneNumber,
      String? customerName,
      String pickupLocation,
      String destination,
      int fare,
      TripStatus status,
      int? pointsUsed,
      int? pointsEarned,
      DateTime? startTime,
      DateTime? endTime,
      String? notes});
}

/// @nodoc
class __$$TripImplCopyWithImpl<$Res>
    extends _$TripCopyWithImpl<$Res, _$TripImpl>
    implements _$$TripImplCopyWith<$Res> {
  __$$TripImplCopyWithImpl(_$TripImpl _value, $Res Function(_$TripImpl) _then)
      : super(_value, _then);

  /// Create a copy of Trip
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? callId = null,
    Object? driverId = null,
    Object? driverName = null,
    Object? phoneNumber = null,
    Object? customerName = freezed,
    Object? pickupLocation = null,
    Object? destination = null,
    Object? fare = null,
    Object? status = null,
    Object? pointsUsed = freezed,
    Object? pointsEarned = freezed,
    Object? startTime = freezed,
    Object? endTime = freezed,
    Object? notes = freezed,
  }) {
    return _then(_$TripImpl(
      id: null == id
          ? _value.id
          : id // ignore: cast_nullable_to_non_nullable
              as String,
      callId: null == callId
          ? _value.callId
          : callId // ignore: cast_nullable_to_non_nullable
              as String,
      driverId: null == driverId
          ? _value.driverId
          : driverId // ignore: cast_nullable_to_non_nullable
              as String,
      driverName: null == driverName
          ? _value.driverName
          : driverName // ignore: cast_nullable_to_non_nullable
              as String,
      phoneNumber: null == phoneNumber
          ? _value.phoneNumber
          : phoneNumber // ignore: cast_nullable_to_non_nullable
              as String,
      customerName: freezed == customerName
          ? _value.customerName
          : customerName // ignore: cast_nullable_to_non_nullable
              as String?,
      pickupLocation: null == pickupLocation
          ? _value.pickupLocation
          : pickupLocation // ignore: cast_nullable_to_non_nullable
              as String,
      destination: null == destination
          ? _value.destination
          : destination // ignore: cast_nullable_to_non_nullable
              as String,
      fare: null == fare
          ? _value.fare
          : fare // ignore: cast_nullable_to_non_nullable
              as int,
      status: null == status
          ? _value.status
          : status // ignore: cast_nullable_to_non_nullable
              as TripStatus,
      pointsUsed: freezed == pointsUsed
          ? _value.pointsUsed
          : pointsUsed // ignore: cast_nullable_to_non_nullable
              as int?,
      pointsEarned: freezed == pointsEarned
          ? _value.pointsEarned
          : pointsEarned // ignore: cast_nullable_to_non_nullable
              as int?,
      startTime: freezed == startTime
          ? _value.startTime
          : startTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      endTime: freezed == endTime
          ? _value.endTime
          : endTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      notes: freezed == notes
          ? _value.notes
          : notes // ignore: cast_nullable_to_non_nullable
              as String?,
    ));
  }
}

/// @nodoc

class _$TripImpl extends _Trip {
  const _$TripImpl(
      {required this.id,
      required this.callId,
      required this.driverId,
      required this.driverName,
      required this.phoneNumber,
      this.customerName,
      required this.pickupLocation,
      required this.destination,
      required this.fare,
      required this.status,
      this.pointsUsed,
      this.pointsEarned,
      this.startTime,
      this.endTime,
      this.notes})
      : super._();

  @override
  final String id;
  @override
  final String callId;
  @override
  final String driverId;
  @override
  final String driverName;
  @override
  final String phoneNumber;
  @override
  final String? customerName;
  @override
  final String pickupLocation;
  @override
  final String destination;
  @override
  final int fare;
  @override
  final TripStatus status;
  @override
  final int? pointsUsed;
  @override
  final int? pointsEarned;
  @override
  final DateTime? startTime;
  @override
  final DateTime? endTime;
  @override
  final String? notes;

  @override
  String toString() {
    return 'Trip(id: $id, callId: $callId, driverId: $driverId, driverName: $driverName, phoneNumber: $phoneNumber, customerName: $customerName, pickupLocation: $pickupLocation, destination: $destination, fare: $fare, status: $status, pointsUsed: $pointsUsed, pointsEarned: $pointsEarned, startTime: $startTime, endTime: $endTime, notes: $notes)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$TripImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.callId, callId) || other.callId == callId) &&
            (identical(other.driverId, driverId) ||
                other.driverId == driverId) &&
            (identical(other.driverName, driverName) ||
                other.driverName == driverName) &&
            (identical(other.phoneNumber, phoneNumber) ||
                other.phoneNumber == phoneNumber) &&
            (identical(other.customerName, customerName) ||
                other.customerName == customerName) &&
            (identical(other.pickupLocation, pickupLocation) ||
                other.pickupLocation == pickupLocation) &&
            (identical(other.destination, destination) ||
                other.destination == destination) &&
            (identical(other.fare, fare) || other.fare == fare) &&
            (identical(other.status, status) || other.status == status) &&
            (identical(other.pointsUsed, pointsUsed) ||
                other.pointsUsed == pointsUsed) &&
            (identical(other.pointsEarned, pointsEarned) ||
                other.pointsEarned == pointsEarned) &&
            (identical(other.startTime, startTime) ||
                other.startTime == startTime) &&
            (identical(other.endTime, endTime) || other.endTime == endTime) &&
            (identical(other.notes, notes) || other.notes == notes));
  }

  @override
  int get hashCode => Object.hash(
      runtimeType,
      id,
      callId,
      driverId,
      driverName,
      phoneNumber,
      customerName,
      pickupLocation,
      destination,
      fare,
      status,
      pointsUsed,
      pointsEarned,
      startTime,
      endTime,
      notes);

  /// Create a copy of Trip
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$TripImplCopyWith<_$TripImpl> get copyWith =>
      __$$TripImplCopyWithImpl<_$TripImpl>(this, _$identity);
}

abstract class _Trip extends Trip {
  const factory _Trip(
      {required final String id,
      required final String callId,
      required final String driverId,
      required final String driverName,
      required final String phoneNumber,
      final String? customerName,
      required final String pickupLocation,
      required final String destination,
      required final int fare,
      required final TripStatus status,
      final int? pointsUsed,
      final int? pointsEarned,
      final DateTime? startTime,
      final DateTime? endTime,
      final String? notes}) = _$TripImpl;
  const _Trip._() : super._();

  @override
  String get id;
  @override
  String get callId;
  @override
  String get driverId;
  @override
  String get driverName;
  @override
  String get phoneNumber;
  @override
  String? get customerName;
  @override
  String get pickupLocation;
  @override
  String get destination;
  @override
  int get fare;
  @override
  TripStatus get status;
  @override
  int? get pointsUsed;
  @override
  int? get pointsEarned;
  @override
  DateTime? get startTime;
  @override
  DateTime? get endTime;
  @override
  String? get notes;

  /// Create a copy of Trip
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$TripImplCopyWith<_$TripImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
