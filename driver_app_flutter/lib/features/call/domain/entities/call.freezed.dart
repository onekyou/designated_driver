// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'call.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
    'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models');

/// @nodoc
mixin _$Call {
  String get id => throw _privateConstructorUsedError;
  String get regionId => throw _privateConstructorUsedError;
  String get officeId => throw _privateConstructorUsedError;
  String get phoneNumber => throw _privateConstructorUsedError;
  String? get customerName => throw _privateConstructorUsedError;
  String? get pickupLocation => throw _privateConstructorUsedError;
  String? get destination => throw _privateConstructorUsedError;
  CallStatus get status => throw _privateConstructorUsedError;
  String? get assignedDriverId => throw _privateConstructorUsedError;
  String? get assignedDriverName => throw _privateConstructorUsedError;
  DateTime? get callTime => throw _privateConstructorUsedError;
  DateTime? get assignedTime => throw _privateConstructorUsedError;
  DateTime? get acceptedTime => throw _privateConstructorUsedError;
  DateTime? get pickedUpTime => throw _privateConstructorUsedError;
  DateTime? get completedTime => throw _privateConstructorUsedError;
  int? get fare => throw _privateConstructorUsedError;
  int? get pointsUsed => throw _privateConstructorUsedError;
  int? get pointsEarned => throw _privateConstructorUsedError;
  String? get notes => throw _privateConstructorUsedError;

  /// Create a copy of Call
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $CallCopyWith<Call> get copyWith => throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $CallCopyWith<$Res> {
  factory $CallCopyWith(Call value, $Res Function(Call) then) =
      _$CallCopyWithImpl<$Res, Call>;
  @useResult
  $Res call(
      {String id,
      String regionId,
      String officeId,
      String phoneNumber,
      String? customerName,
      String? pickupLocation,
      String? destination,
      CallStatus status,
      String? assignedDriverId,
      String? assignedDriverName,
      DateTime? callTime,
      DateTime? assignedTime,
      DateTime? acceptedTime,
      DateTime? pickedUpTime,
      DateTime? completedTime,
      int? fare,
      int? pointsUsed,
      int? pointsEarned,
      String? notes});
}

/// @nodoc
class _$CallCopyWithImpl<$Res, $Val extends Call>
    implements $CallCopyWith<$Res> {
  _$CallCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of Call
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? regionId = null,
    Object? officeId = null,
    Object? phoneNumber = null,
    Object? customerName = freezed,
    Object? pickupLocation = freezed,
    Object? destination = freezed,
    Object? status = null,
    Object? assignedDriverId = freezed,
    Object? assignedDriverName = freezed,
    Object? callTime = freezed,
    Object? assignedTime = freezed,
    Object? acceptedTime = freezed,
    Object? pickedUpTime = freezed,
    Object? completedTime = freezed,
    Object? fare = freezed,
    Object? pointsUsed = freezed,
    Object? pointsEarned = freezed,
    Object? notes = freezed,
  }) {
    return _then(_value.copyWith(
      id: null == id
          ? _value.id
          : id // ignore: cast_nullable_to_non_nullable
              as String,
      regionId: null == regionId
          ? _value.regionId
          : regionId // ignore: cast_nullable_to_non_nullable
              as String,
      officeId: null == officeId
          ? _value.officeId
          : officeId // ignore: cast_nullable_to_non_nullable
              as String,
      phoneNumber: null == phoneNumber
          ? _value.phoneNumber
          : phoneNumber // ignore: cast_nullable_to_non_nullable
              as String,
      customerName: freezed == customerName
          ? _value.customerName
          : customerName // ignore: cast_nullable_to_non_nullable
              as String?,
      pickupLocation: freezed == pickupLocation
          ? _value.pickupLocation
          : pickupLocation // ignore: cast_nullable_to_non_nullable
              as String?,
      destination: freezed == destination
          ? _value.destination
          : destination // ignore: cast_nullable_to_non_nullable
              as String?,
      status: null == status
          ? _value.status
          : status // ignore: cast_nullable_to_non_nullable
              as CallStatus,
      assignedDriverId: freezed == assignedDriverId
          ? _value.assignedDriverId
          : assignedDriverId // ignore: cast_nullable_to_non_nullable
              as String?,
      assignedDriverName: freezed == assignedDriverName
          ? _value.assignedDriverName
          : assignedDriverName // ignore: cast_nullable_to_non_nullable
              as String?,
      callTime: freezed == callTime
          ? _value.callTime
          : callTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      assignedTime: freezed == assignedTime
          ? _value.assignedTime
          : assignedTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      acceptedTime: freezed == acceptedTime
          ? _value.acceptedTime
          : acceptedTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      pickedUpTime: freezed == pickedUpTime
          ? _value.pickedUpTime
          : pickedUpTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      completedTime: freezed == completedTime
          ? _value.completedTime
          : completedTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      fare: freezed == fare
          ? _value.fare
          : fare // ignore: cast_nullable_to_non_nullable
              as int?,
      pointsUsed: freezed == pointsUsed
          ? _value.pointsUsed
          : pointsUsed // ignore: cast_nullable_to_non_nullable
              as int?,
      pointsEarned: freezed == pointsEarned
          ? _value.pointsEarned
          : pointsEarned // ignore: cast_nullable_to_non_nullable
              as int?,
      notes: freezed == notes
          ? _value.notes
          : notes // ignore: cast_nullable_to_non_nullable
              as String?,
    ) as $Val);
  }
}

/// @nodoc
abstract class _$$CallImplCopyWith<$Res> implements $CallCopyWith<$Res> {
  factory _$$CallImplCopyWith(
          _$CallImpl value, $Res Function(_$CallImpl) then) =
      __$$CallImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call(
      {String id,
      String regionId,
      String officeId,
      String phoneNumber,
      String? customerName,
      String? pickupLocation,
      String? destination,
      CallStatus status,
      String? assignedDriverId,
      String? assignedDriverName,
      DateTime? callTime,
      DateTime? assignedTime,
      DateTime? acceptedTime,
      DateTime? pickedUpTime,
      DateTime? completedTime,
      int? fare,
      int? pointsUsed,
      int? pointsEarned,
      String? notes});
}

/// @nodoc
class __$$CallImplCopyWithImpl<$Res>
    extends _$CallCopyWithImpl<$Res, _$CallImpl>
    implements _$$CallImplCopyWith<$Res> {
  __$$CallImplCopyWithImpl(_$CallImpl _value, $Res Function(_$CallImpl) _then)
      : super(_value, _then);

  /// Create a copy of Call
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? regionId = null,
    Object? officeId = null,
    Object? phoneNumber = null,
    Object? customerName = freezed,
    Object? pickupLocation = freezed,
    Object? destination = freezed,
    Object? status = null,
    Object? assignedDriverId = freezed,
    Object? assignedDriverName = freezed,
    Object? callTime = freezed,
    Object? assignedTime = freezed,
    Object? acceptedTime = freezed,
    Object? pickedUpTime = freezed,
    Object? completedTime = freezed,
    Object? fare = freezed,
    Object? pointsUsed = freezed,
    Object? pointsEarned = freezed,
    Object? notes = freezed,
  }) {
    return _then(_$CallImpl(
      id: null == id
          ? _value.id
          : id // ignore: cast_nullable_to_non_nullable
              as String,
      regionId: null == regionId
          ? _value.regionId
          : regionId // ignore: cast_nullable_to_non_nullable
              as String,
      officeId: null == officeId
          ? _value.officeId
          : officeId // ignore: cast_nullable_to_non_nullable
              as String,
      phoneNumber: null == phoneNumber
          ? _value.phoneNumber
          : phoneNumber // ignore: cast_nullable_to_non_nullable
              as String,
      customerName: freezed == customerName
          ? _value.customerName
          : customerName // ignore: cast_nullable_to_non_nullable
              as String?,
      pickupLocation: freezed == pickupLocation
          ? _value.pickupLocation
          : pickupLocation // ignore: cast_nullable_to_non_nullable
              as String?,
      destination: freezed == destination
          ? _value.destination
          : destination // ignore: cast_nullable_to_non_nullable
              as String?,
      status: null == status
          ? _value.status
          : status // ignore: cast_nullable_to_non_nullable
              as CallStatus,
      assignedDriverId: freezed == assignedDriverId
          ? _value.assignedDriverId
          : assignedDriverId // ignore: cast_nullable_to_non_nullable
              as String?,
      assignedDriverName: freezed == assignedDriverName
          ? _value.assignedDriverName
          : assignedDriverName // ignore: cast_nullable_to_non_nullable
              as String?,
      callTime: freezed == callTime
          ? _value.callTime
          : callTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      assignedTime: freezed == assignedTime
          ? _value.assignedTime
          : assignedTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      acceptedTime: freezed == acceptedTime
          ? _value.acceptedTime
          : acceptedTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      pickedUpTime: freezed == pickedUpTime
          ? _value.pickedUpTime
          : pickedUpTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      completedTime: freezed == completedTime
          ? _value.completedTime
          : completedTime // ignore: cast_nullable_to_non_nullable
              as DateTime?,
      fare: freezed == fare
          ? _value.fare
          : fare // ignore: cast_nullable_to_non_nullable
              as int?,
      pointsUsed: freezed == pointsUsed
          ? _value.pointsUsed
          : pointsUsed // ignore: cast_nullable_to_non_nullable
              as int?,
      pointsEarned: freezed == pointsEarned
          ? _value.pointsEarned
          : pointsEarned // ignore: cast_nullable_to_non_nullable
              as int?,
      notes: freezed == notes
          ? _value.notes
          : notes // ignore: cast_nullable_to_non_nullable
              as String?,
    ));
  }
}

/// @nodoc

class _$CallImpl extends _Call {
  const _$CallImpl(
      {required this.id,
      required this.regionId,
      required this.officeId,
      required this.phoneNumber,
      this.customerName,
      this.pickupLocation,
      this.destination,
      required this.status,
      this.assignedDriverId,
      this.assignedDriverName,
      this.callTime,
      this.assignedTime,
      this.acceptedTime,
      this.pickedUpTime,
      this.completedTime,
      this.fare,
      this.pointsUsed,
      this.pointsEarned,
      this.notes})
      : super._();

  @override
  final String id;
  @override
  final String regionId;
  @override
  final String officeId;
  @override
  final String phoneNumber;
  @override
  final String? customerName;
  @override
  final String? pickupLocation;
  @override
  final String? destination;
  @override
  final CallStatus status;
  @override
  final String? assignedDriverId;
  @override
  final String? assignedDriverName;
  @override
  final DateTime? callTime;
  @override
  final DateTime? assignedTime;
  @override
  final DateTime? acceptedTime;
  @override
  final DateTime? pickedUpTime;
  @override
  final DateTime? completedTime;
  @override
  final int? fare;
  @override
  final int? pointsUsed;
  @override
  final int? pointsEarned;
  @override
  final String? notes;

  @override
  String toString() {
    return 'Call(id: $id, regionId: $regionId, officeId: $officeId, phoneNumber: $phoneNumber, customerName: $customerName, pickupLocation: $pickupLocation, destination: $destination, status: $status, assignedDriverId: $assignedDriverId, assignedDriverName: $assignedDriverName, callTime: $callTime, assignedTime: $assignedTime, acceptedTime: $acceptedTime, pickedUpTime: $pickedUpTime, completedTime: $completedTime, fare: $fare, pointsUsed: $pointsUsed, pointsEarned: $pointsEarned, notes: $notes)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$CallImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.regionId, regionId) ||
                other.regionId == regionId) &&
            (identical(other.officeId, officeId) ||
                other.officeId == officeId) &&
            (identical(other.phoneNumber, phoneNumber) ||
                other.phoneNumber == phoneNumber) &&
            (identical(other.customerName, customerName) ||
                other.customerName == customerName) &&
            (identical(other.pickupLocation, pickupLocation) ||
                other.pickupLocation == pickupLocation) &&
            (identical(other.destination, destination) ||
                other.destination == destination) &&
            (identical(other.status, status) || other.status == status) &&
            (identical(other.assignedDriverId, assignedDriverId) ||
                other.assignedDriverId == assignedDriverId) &&
            (identical(other.assignedDriverName, assignedDriverName) ||
                other.assignedDriverName == assignedDriverName) &&
            (identical(other.callTime, callTime) ||
                other.callTime == callTime) &&
            (identical(other.assignedTime, assignedTime) ||
                other.assignedTime == assignedTime) &&
            (identical(other.acceptedTime, acceptedTime) ||
                other.acceptedTime == acceptedTime) &&
            (identical(other.pickedUpTime, pickedUpTime) ||
                other.pickedUpTime == pickedUpTime) &&
            (identical(other.completedTime, completedTime) ||
                other.completedTime == completedTime) &&
            (identical(other.fare, fare) || other.fare == fare) &&
            (identical(other.pointsUsed, pointsUsed) ||
                other.pointsUsed == pointsUsed) &&
            (identical(other.pointsEarned, pointsEarned) ||
                other.pointsEarned == pointsEarned) &&
            (identical(other.notes, notes) || other.notes == notes));
  }

  @override
  int get hashCode => Object.hashAll([
        runtimeType,
        id,
        regionId,
        officeId,
        phoneNumber,
        customerName,
        pickupLocation,
        destination,
        status,
        assignedDriverId,
        assignedDriverName,
        callTime,
        assignedTime,
        acceptedTime,
        pickedUpTime,
        completedTime,
        fare,
        pointsUsed,
        pointsEarned,
        notes
      ]);

  /// Create a copy of Call
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$CallImplCopyWith<_$CallImpl> get copyWith =>
      __$$CallImplCopyWithImpl<_$CallImpl>(this, _$identity);
}

abstract class _Call extends Call {
  const factory _Call(
      {required final String id,
      required final String regionId,
      required final String officeId,
      required final String phoneNumber,
      final String? customerName,
      final String? pickupLocation,
      final String? destination,
      required final CallStatus status,
      final String? assignedDriverId,
      final String? assignedDriverName,
      final DateTime? callTime,
      final DateTime? assignedTime,
      final DateTime? acceptedTime,
      final DateTime? pickedUpTime,
      final DateTime? completedTime,
      final int? fare,
      final int? pointsUsed,
      final int? pointsEarned,
      final String? notes}) = _$CallImpl;
  const _Call._() : super._();

  @override
  String get id;
  @override
  String get regionId;
  @override
  String get officeId;
  @override
  String get phoneNumber;
  @override
  String? get customerName;
  @override
  String? get pickupLocation;
  @override
  String? get destination;
  @override
  CallStatus get status;
  @override
  String? get assignedDriverId;
  @override
  String? get assignedDriverName;
  @override
  DateTime? get callTime;
  @override
  DateTime? get assignedTime;
  @override
  DateTime? get acceptedTime;
  @override
  DateTime? get pickedUpTime;
  @override
  DateTime? get completedTime;
  @override
  int? get fare;
  @override
  int? get pointsUsed;
  @override
  int? get pointsEarned;
  @override
  String? get notes;

  /// Create a copy of Call
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$CallImplCopyWith<_$CallImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
