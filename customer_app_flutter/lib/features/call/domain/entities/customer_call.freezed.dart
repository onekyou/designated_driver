// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'customer_call.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

CustomerCall _$CustomerCallFromJson(Map<String, dynamic> json) {
  return _CustomerCall.fromJson(json);
}

/// @nodoc
mixin _$CustomerCall {
  String get id => throw _privateConstructorUsedError;
  String get phoneNumber => throw _privateConstructorUsedError;
  String get officeId => throw _privateConstructorUsedError;
  String get provinceId => throw _privateConstructorUsedError;
  String get cityId => throw _privateConstructorUsedError;
  String get currentLocation => throw _privateConstructorUsedError;
  String get destinationLocation => throw _privateConstructorUsedError;
  int get timestamp => throw _privateConstructorUsedError;
  String get status => throw _privateConstructorUsedError;
  @JsonKey(name: 'assignedDriverId')
  String? get driverId => throw _privateConstructorUsedError;
  int? get estimatedArrivalTime => throw _privateConstructorUsedError;
  int? get fare => throw _privateConstructorUsedError;
  String? get notes => throw _privateConstructorUsedError;
  String get createdFrom => throw _privateConstructorUsedError;
  String? get customerId => throw _privateConstructorUsedError;
  String? get customerName => throw _privateConstructorUsedError;
  String? get customerGrade => throw _privateConstructorUsedError;
  bool get isAppCustomer => throw _privateConstructorUsedError;
  int get pointsUsed => throw _privateConstructorUsedError;
  int? get finalFare => throw _privateConstructorUsedError;
  int get discountAmount => throw _privateConstructorUsedError;

  /// Serializes this CustomerCall to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of CustomerCall
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $CustomerCallCopyWith<CustomerCall> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $CustomerCallCopyWith<$Res> {
  factory $CustomerCallCopyWith(
    CustomerCall value,
    $Res Function(CustomerCall) then,
  ) = _$CustomerCallCopyWithImpl<$Res, CustomerCall>;
  @useResult
  $Res call({
    String id,
    String phoneNumber,
    String officeId,
    String provinceId,
    String cityId,
    String currentLocation,
    String destinationLocation,
    int timestamp,
    String status,
    @JsonKey(name: 'assignedDriverId') String? driverId,
    int? estimatedArrivalTime,
    int? fare,
    String? notes,
    String createdFrom,
    String? customerId,
    String? customerName,
    String? customerGrade,
    bool isAppCustomer,
    int pointsUsed,
    int? finalFare,
    int discountAmount,
  });
}

/// @nodoc
class _$CustomerCallCopyWithImpl<$Res, $Val extends CustomerCall>
    implements $CustomerCallCopyWith<$Res> {
  _$CustomerCallCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of CustomerCall
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? phoneNumber = null,
    Object? officeId = null,
    Object? provinceId = null,
    Object? cityId = null,
    Object? currentLocation = null,
    Object? destinationLocation = null,
    Object? timestamp = null,
    Object? status = null,
    Object? driverId = freezed,
    Object? estimatedArrivalTime = freezed,
    Object? fare = freezed,
    Object? notes = freezed,
    Object? createdFrom = null,
    Object? customerId = freezed,
    Object? customerName = freezed,
    Object? customerGrade = freezed,
    Object? isAppCustomer = null,
    Object? pointsUsed = null,
    Object? finalFare = freezed,
    Object? discountAmount = null,
  }) {
    return _then(
      _value.copyWith(
            id: null == id
                ? _value.id
                : id // ignore: cast_nullable_to_non_nullable
                      as String,
            phoneNumber: null == phoneNumber
                ? _value.phoneNumber
                : phoneNumber // ignore: cast_nullable_to_non_nullable
                      as String,
            officeId: null == officeId
                ? _value.officeId
                : officeId // ignore: cast_nullable_to_non_nullable
                      as String,
            provinceId: null == provinceId
                ? _value.provinceId
                : provinceId // ignore: cast_nullable_to_non_nullable
                      as String,
            cityId: null == cityId
                ? _value.cityId
                : cityId // ignore: cast_nullable_to_non_nullable
                      as String,
            currentLocation: null == currentLocation
                ? _value.currentLocation
                : currentLocation // ignore: cast_nullable_to_non_nullable
                      as String,
            destinationLocation: null == destinationLocation
                ? _value.destinationLocation
                : destinationLocation // ignore: cast_nullable_to_non_nullable
                      as String,
            timestamp: null == timestamp
                ? _value.timestamp
                : timestamp // ignore: cast_nullable_to_non_nullable
                      as int,
            status: null == status
                ? _value.status
                : status // ignore: cast_nullable_to_non_nullable
                      as String,
            driverId: freezed == driverId
                ? _value.driverId
                : driverId // ignore: cast_nullable_to_non_nullable
                      as String?,
            estimatedArrivalTime: freezed == estimatedArrivalTime
                ? _value.estimatedArrivalTime
                : estimatedArrivalTime // ignore: cast_nullable_to_non_nullable
                      as int?,
            fare: freezed == fare
                ? _value.fare
                : fare // ignore: cast_nullable_to_non_nullable
                      as int?,
            notes: freezed == notes
                ? _value.notes
                : notes // ignore: cast_nullable_to_non_nullable
                      as String?,
            createdFrom: null == createdFrom
                ? _value.createdFrom
                : createdFrom // ignore: cast_nullable_to_non_nullable
                      as String,
            customerId: freezed == customerId
                ? _value.customerId
                : customerId // ignore: cast_nullable_to_non_nullable
                      as String?,
            customerName: freezed == customerName
                ? _value.customerName
                : customerName // ignore: cast_nullable_to_non_nullable
                      as String?,
            customerGrade: freezed == customerGrade
                ? _value.customerGrade
                : customerGrade // ignore: cast_nullable_to_non_nullable
                      as String?,
            isAppCustomer: null == isAppCustomer
                ? _value.isAppCustomer
                : isAppCustomer // ignore: cast_nullable_to_non_nullable
                      as bool,
            pointsUsed: null == pointsUsed
                ? _value.pointsUsed
                : pointsUsed // ignore: cast_nullable_to_non_nullable
                      as int,
            finalFare: freezed == finalFare
                ? _value.finalFare
                : finalFare // ignore: cast_nullable_to_non_nullable
                      as int?,
            discountAmount: null == discountAmount
                ? _value.discountAmount
                : discountAmount // ignore: cast_nullable_to_non_nullable
                      as int,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$CustomerCallImplCopyWith<$Res>
    implements $CustomerCallCopyWith<$Res> {
  factory _$$CustomerCallImplCopyWith(
    _$CustomerCallImpl value,
    $Res Function(_$CustomerCallImpl) then,
  ) = __$$CustomerCallImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    String id,
    String phoneNumber,
    String officeId,
    String provinceId,
    String cityId,
    String currentLocation,
    String destinationLocation,
    int timestamp,
    String status,
    @JsonKey(name: 'assignedDriverId') String? driverId,
    int? estimatedArrivalTime,
    int? fare,
    String? notes,
    String createdFrom,
    String? customerId,
    String? customerName,
    String? customerGrade,
    bool isAppCustomer,
    int pointsUsed,
    int? finalFare,
    int discountAmount,
  });
}

/// @nodoc
class __$$CustomerCallImplCopyWithImpl<$Res>
    extends _$CustomerCallCopyWithImpl<$Res, _$CustomerCallImpl>
    implements _$$CustomerCallImplCopyWith<$Res> {
  __$$CustomerCallImplCopyWithImpl(
    _$CustomerCallImpl _value,
    $Res Function(_$CustomerCallImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of CustomerCall
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? phoneNumber = null,
    Object? officeId = null,
    Object? provinceId = null,
    Object? cityId = null,
    Object? currentLocation = null,
    Object? destinationLocation = null,
    Object? timestamp = null,
    Object? status = null,
    Object? driverId = freezed,
    Object? estimatedArrivalTime = freezed,
    Object? fare = freezed,
    Object? notes = freezed,
    Object? createdFrom = null,
    Object? customerId = freezed,
    Object? customerName = freezed,
    Object? customerGrade = freezed,
    Object? isAppCustomer = null,
    Object? pointsUsed = null,
    Object? finalFare = freezed,
    Object? discountAmount = null,
  }) {
    return _then(
      _$CustomerCallImpl(
        id: null == id
            ? _value.id
            : id // ignore: cast_nullable_to_non_nullable
                  as String,
        phoneNumber: null == phoneNumber
            ? _value.phoneNumber
            : phoneNumber // ignore: cast_nullable_to_non_nullable
                  as String,
        officeId: null == officeId
            ? _value.officeId
            : officeId // ignore: cast_nullable_to_non_nullable
                  as String,
        provinceId: null == provinceId
            ? _value.provinceId
            : provinceId // ignore: cast_nullable_to_non_nullable
                  as String,
        cityId: null == cityId
            ? _value.cityId
            : cityId // ignore: cast_nullable_to_non_nullable
                  as String,
        currentLocation: null == currentLocation
            ? _value.currentLocation
            : currentLocation // ignore: cast_nullable_to_non_nullable
                  as String,
        destinationLocation: null == destinationLocation
            ? _value.destinationLocation
            : destinationLocation // ignore: cast_nullable_to_non_nullable
                  as String,
        timestamp: null == timestamp
            ? _value.timestamp
            : timestamp // ignore: cast_nullable_to_non_nullable
                  as int,
        status: null == status
            ? _value.status
            : status // ignore: cast_nullable_to_non_nullable
                  as String,
        driverId: freezed == driverId
            ? _value.driverId
            : driverId // ignore: cast_nullable_to_non_nullable
                  as String?,
        estimatedArrivalTime: freezed == estimatedArrivalTime
            ? _value.estimatedArrivalTime
            : estimatedArrivalTime // ignore: cast_nullable_to_non_nullable
                  as int?,
        fare: freezed == fare
            ? _value.fare
            : fare // ignore: cast_nullable_to_non_nullable
                  as int?,
        notes: freezed == notes
            ? _value.notes
            : notes // ignore: cast_nullable_to_non_nullable
                  as String?,
        createdFrom: null == createdFrom
            ? _value.createdFrom
            : createdFrom // ignore: cast_nullable_to_non_nullable
                  as String,
        customerId: freezed == customerId
            ? _value.customerId
            : customerId // ignore: cast_nullable_to_non_nullable
                  as String?,
        customerName: freezed == customerName
            ? _value.customerName
            : customerName // ignore: cast_nullable_to_non_nullable
                  as String?,
        customerGrade: freezed == customerGrade
            ? _value.customerGrade
            : customerGrade // ignore: cast_nullable_to_non_nullable
                  as String?,
        isAppCustomer: null == isAppCustomer
            ? _value.isAppCustomer
            : isAppCustomer // ignore: cast_nullable_to_non_nullable
                  as bool,
        pointsUsed: null == pointsUsed
            ? _value.pointsUsed
            : pointsUsed // ignore: cast_nullable_to_non_nullable
                  as int,
        finalFare: freezed == finalFare
            ? _value.finalFare
            : finalFare // ignore: cast_nullable_to_non_nullable
                  as int?,
        discountAmount: null == discountAmount
            ? _value.discountAmount
            : discountAmount // ignore: cast_nullable_to_non_nullable
                  as int,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$CustomerCallImpl extends _CustomerCall {
  const _$CustomerCallImpl({
    this.id = '',
    required this.phoneNumber,
    required this.officeId,
    required this.provinceId,
    required this.cityId,
    this.currentLocation = '',
    this.destinationLocation = '',
    this.timestamp = 0,
    this.status = 'REQUESTED',
    @JsonKey(name: 'assignedDriverId') this.driverId,
    this.estimatedArrivalTime,
    this.fare,
    this.notes,
    this.createdFrom = 'customer_app',
    this.customerId,
    this.customerName,
    this.customerGrade = 'bronze',
    this.isAppCustomer = true,
    this.pointsUsed = 0,
    this.finalFare,
    this.discountAmount = 0,
  }) : super._();

  factory _$CustomerCallImpl.fromJson(Map<String, dynamic> json) =>
      _$$CustomerCallImplFromJson(json);

  @override
  @JsonKey()
  final String id;
  @override
  final String phoneNumber;
  @override
  final String officeId;
  @override
  final String provinceId;
  @override
  final String cityId;
  @override
  @JsonKey()
  final String currentLocation;
  @override
  @JsonKey()
  final String destinationLocation;
  @override
  @JsonKey()
  final int timestamp;
  @override
  @JsonKey()
  final String status;
  @override
  @JsonKey(name: 'assignedDriverId')
  final String? driverId;
  @override
  final int? estimatedArrivalTime;
  @override
  final int? fare;
  @override
  final String? notes;
  @override
  @JsonKey()
  final String createdFrom;
  @override
  final String? customerId;
  @override
  final String? customerName;
  @override
  @JsonKey()
  final String? customerGrade;
  @override
  @JsonKey()
  final bool isAppCustomer;
  @override
  @JsonKey()
  final int pointsUsed;
  @override
  final int? finalFare;
  @override
  @JsonKey()
  final int discountAmount;

  @override
  String toString() {
    return 'CustomerCall(id: $id, phoneNumber: $phoneNumber, officeId: $officeId, provinceId: $provinceId, cityId: $cityId, currentLocation: $currentLocation, destinationLocation: $destinationLocation, timestamp: $timestamp, status: $status, driverId: $driverId, estimatedArrivalTime: $estimatedArrivalTime, fare: $fare, notes: $notes, createdFrom: $createdFrom, customerId: $customerId, customerName: $customerName, customerGrade: $customerGrade, isAppCustomer: $isAppCustomer, pointsUsed: $pointsUsed, finalFare: $finalFare, discountAmount: $discountAmount)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$CustomerCallImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.phoneNumber, phoneNumber) ||
                other.phoneNumber == phoneNumber) &&
            (identical(other.officeId, officeId) ||
                other.officeId == officeId) &&
            (identical(other.provinceId, provinceId) ||
                other.provinceId == provinceId) &&
            (identical(other.cityId, cityId) || other.cityId == cityId) &&
            (identical(other.currentLocation, currentLocation) ||
                other.currentLocation == currentLocation) &&
            (identical(other.destinationLocation, destinationLocation) ||
                other.destinationLocation == destinationLocation) &&
            (identical(other.timestamp, timestamp) ||
                other.timestamp == timestamp) &&
            (identical(other.status, status) || other.status == status) &&
            (identical(other.driverId, driverId) ||
                other.driverId == driverId) &&
            (identical(other.estimatedArrivalTime, estimatedArrivalTime) ||
                other.estimatedArrivalTime == estimatedArrivalTime) &&
            (identical(other.fare, fare) || other.fare == fare) &&
            (identical(other.notes, notes) || other.notes == notes) &&
            (identical(other.createdFrom, createdFrom) ||
                other.createdFrom == createdFrom) &&
            (identical(other.customerId, customerId) ||
                other.customerId == customerId) &&
            (identical(other.customerName, customerName) ||
                other.customerName == customerName) &&
            (identical(other.customerGrade, customerGrade) ||
                other.customerGrade == customerGrade) &&
            (identical(other.isAppCustomer, isAppCustomer) ||
                other.isAppCustomer == isAppCustomer) &&
            (identical(other.pointsUsed, pointsUsed) ||
                other.pointsUsed == pointsUsed) &&
            (identical(other.finalFare, finalFare) ||
                other.finalFare == finalFare) &&
            (identical(other.discountAmount, discountAmount) ||
                other.discountAmount == discountAmount));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hashAll([
    runtimeType,
    id,
    phoneNumber,
    officeId,
    provinceId,
    cityId,
    currentLocation,
    destinationLocation,
    timestamp,
    status,
    driverId,
    estimatedArrivalTime,
    fare,
    notes,
    createdFrom,
    customerId,
    customerName,
    customerGrade,
    isAppCustomer,
    pointsUsed,
    finalFare,
    discountAmount,
  ]);

  /// Create a copy of CustomerCall
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$CustomerCallImplCopyWith<_$CustomerCallImpl> get copyWith =>
      __$$CustomerCallImplCopyWithImpl<_$CustomerCallImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$CustomerCallImplToJson(this);
  }
}

abstract class _CustomerCall extends CustomerCall {
  const factory _CustomerCall({
    final String id,
    required final String phoneNumber,
    required final String officeId,
    required final String provinceId,
    required final String cityId,
    final String currentLocation,
    final String destinationLocation,
    final int timestamp,
    final String status,
    @JsonKey(name: 'assignedDriverId') final String? driverId,
    final int? estimatedArrivalTime,
    final int? fare,
    final String? notes,
    final String createdFrom,
    final String? customerId,
    final String? customerName,
    final String? customerGrade,
    final bool isAppCustomer,
    final int pointsUsed,
    final int? finalFare,
    final int discountAmount,
  }) = _$CustomerCallImpl;
  const _CustomerCall._() : super._();

  factory _CustomerCall.fromJson(Map<String, dynamic> json) =
      _$CustomerCallImpl.fromJson;

  @override
  String get id;
  @override
  String get phoneNumber;
  @override
  String get officeId;
  @override
  String get provinceId;
  @override
  String get cityId;
  @override
  String get currentLocation;
  @override
  String get destinationLocation;
  @override
  int get timestamp;
  @override
  String get status;
  @override
  @JsonKey(name: 'assignedDriverId')
  String? get driverId;
  @override
  int? get estimatedArrivalTime;
  @override
  int? get fare;
  @override
  String? get notes;
  @override
  String get createdFrom;
  @override
  String? get customerId;
  @override
  String? get customerName;
  @override
  String? get customerGrade;
  @override
  bool get isAppCustomer;
  @override
  int get pointsUsed;
  @override
  int? get finalFare;
  @override
  int get discountAmount;

  /// Create a copy of CustomerCall
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$CustomerCallImplCopyWith<_$CustomerCallImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
