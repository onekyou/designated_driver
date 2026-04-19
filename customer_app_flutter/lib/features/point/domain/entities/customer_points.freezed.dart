// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'customer_points.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

CustomerPoints _$CustomerPointsFromJson(Map<String, dynamic> json) {
  return _CustomerPoints.fromJson(json);
}

/// @nodoc
mixin _$CustomerPoints {
  String get customerId => throw _privateConstructorUsedError;
  String get phoneNumber => throw _privateConstructorUsedError;
  int get currentPoints => throw _privateConstructorUsedError;
  int get totalEarned => throw _privateConstructorUsedError;
  int get totalUsed => throw _privateConstructorUsedError;
  @CustomerGradeConverter()
  CustomerGrade get grade => throw _privateConstructorUsedError;
  int get totalCalls => throw _privateConstructorUsedError;
  @TimestampConverter()
  DateTime? get lastUpdated => throw _privateConstructorUsedError;
  @TimestampConverter()
  DateTime? get createdAt => throw _privateConstructorUsedError;

  /// Serializes this CustomerPoints to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of CustomerPoints
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $CustomerPointsCopyWith<CustomerPoints> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $CustomerPointsCopyWith<$Res> {
  factory $CustomerPointsCopyWith(
    CustomerPoints value,
    $Res Function(CustomerPoints) then,
  ) = _$CustomerPointsCopyWithImpl<$Res, CustomerPoints>;
  @useResult
  $Res call({
    String customerId,
    String phoneNumber,
    int currentPoints,
    int totalEarned,
    int totalUsed,
    @CustomerGradeConverter() CustomerGrade grade,
    int totalCalls,
    @TimestampConverter() DateTime? lastUpdated,
    @TimestampConverter() DateTime? createdAt,
  });
}

/// @nodoc
class _$CustomerPointsCopyWithImpl<$Res, $Val extends CustomerPoints>
    implements $CustomerPointsCopyWith<$Res> {
  _$CustomerPointsCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of CustomerPoints
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? customerId = null,
    Object? phoneNumber = null,
    Object? currentPoints = null,
    Object? totalEarned = null,
    Object? totalUsed = null,
    Object? grade = null,
    Object? totalCalls = null,
    Object? lastUpdated = freezed,
    Object? createdAt = freezed,
  }) {
    return _then(
      _value.copyWith(
            customerId: null == customerId
                ? _value.customerId
                : customerId // ignore: cast_nullable_to_non_nullable
                      as String,
            phoneNumber: null == phoneNumber
                ? _value.phoneNumber
                : phoneNumber // ignore: cast_nullable_to_non_nullable
                      as String,
            currentPoints: null == currentPoints
                ? _value.currentPoints
                : currentPoints // ignore: cast_nullable_to_non_nullable
                      as int,
            totalEarned: null == totalEarned
                ? _value.totalEarned
                : totalEarned // ignore: cast_nullable_to_non_nullable
                      as int,
            totalUsed: null == totalUsed
                ? _value.totalUsed
                : totalUsed // ignore: cast_nullable_to_non_nullable
                      as int,
            grade: null == grade
                ? _value.grade
                : grade // ignore: cast_nullable_to_non_nullable
                      as CustomerGrade,
            totalCalls: null == totalCalls
                ? _value.totalCalls
                : totalCalls // ignore: cast_nullable_to_non_nullable
                      as int,
            lastUpdated: freezed == lastUpdated
                ? _value.lastUpdated
                : lastUpdated // ignore: cast_nullable_to_non_nullable
                      as DateTime?,
            createdAt: freezed == createdAt
                ? _value.createdAt
                : createdAt // ignore: cast_nullable_to_non_nullable
                      as DateTime?,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$CustomerPointsImplCopyWith<$Res>
    implements $CustomerPointsCopyWith<$Res> {
  factory _$$CustomerPointsImplCopyWith(
    _$CustomerPointsImpl value,
    $Res Function(_$CustomerPointsImpl) then,
  ) = __$$CustomerPointsImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    String customerId,
    String phoneNumber,
    int currentPoints,
    int totalEarned,
    int totalUsed,
    @CustomerGradeConverter() CustomerGrade grade,
    int totalCalls,
    @TimestampConverter() DateTime? lastUpdated,
    @TimestampConverter() DateTime? createdAt,
  });
}

/// @nodoc
class __$$CustomerPointsImplCopyWithImpl<$Res>
    extends _$CustomerPointsCopyWithImpl<$Res, _$CustomerPointsImpl>
    implements _$$CustomerPointsImplCopyWith<$Res> {
  __$$CustomerPointsImplCopyWithImpl(
    _$CustomerPointsImpl _value,
    $Res Function(_$CustomerPointsImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of CustomerPoints
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? customerId = null,
    Object? phoneNumber = null,
    Object? currentPoints = null,
    Object? totalEarned = null,
    Object? totalUsed = null,
    Object? grade = null,
    Object? totalCalls = null,
    Object? lastUpdated = freezed,
    Object? createdAt = freezed,
  }) {
    return _then(
      _$CustomerPointsImpl(
        customerId: null == customerId
            ? _value.customerId
            : customerId // ignore: cast_nullable_to_non_nullable
                  as String,
        phoneNumber: null == phoneNumber
            ? _value.phoneNumber
            : phoneNumber // ignore: cast_nullable_to_non_nullable
                  as String,
        currentPoints: null == currentPoints
            ? _value.currentPoints
            : currentPoints // ignore: cast_nullable_to_non_nullable
                  as int,
        totalEarned: null == totalEarned
            ? _value.totalEarned
            : totalEarned // ignore: cast_nullable_to_non_nullable
                  as int,
        totalUsed: null == totalUsed
            ? _value.totalUsed
            : totalUsed // ignore: cast_nullable_to_non_nullable
                  as int,
        grade: null == grade
            ? _value.grade
            : grade // ignore: cast_nullable_to_non_nullable
                  as CustomerGrade,
        totalCalls: null == totalCalls
            ? _value.totalCalls
            : totalCalls // ignore: cast_nullable_to_non_nullable
                  as int,
        lastUpdated: freezed == lastUpdated
            ? _value.lastUpdated
            : lastUpdated // ignore: cast_nullable_to_non_nullable
                  as DateTime?,
        createdAt: freezed == createdAt
            ? _value.createdAt
            : createdAt // ignore: cast_nullable_to_non_nullable
                  as DateTime?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$CustomerPointsImpl extends _CustomerPoints {
  const _$CustomerPointsImpl({
    this.customerId = '',
    this.phoneNumber = '',
    this.currentPoints = 0,
    this.totalEarned = 0,
    this.totalUsed = 0,
    @CustomerGradeConverter() this.grade = CustomerGrade.bronze,
    this.totalCalls = 0,
    @TimestampConverter() this.lastUpdated,
    @TimestampConverter() this.createdAt,
  }) : super._();

  factory _$CustomerPointsImpl.fromJson(Map<String, dynamic> json) =>
      _$$CustomerPointsImplFromJson(json);

  @override
  @JsonKey()
  final String customerId;
  @override
  @JsonKey()
  final String phoneNumber;
  @override
  @JsonKey()
  final int currentPoints;
  @override
  @JsonKey()
  final int totalEarned;
  @override
  @JsonKey()
  final int totalUsed;
  @override
  @JsonKey()
  @CustomerGradeConverter()
  final CustomerGrade grade;
  @override
  @JsonKey()
  final int totalCalls;
  @override
  @TimestampConverter()
  final DateTime? lastUpdated;
  @override
  @TimestampConverter()
  final DateTime? createdAt;

  @override
  String toString() {
    return 'CustomerPoints(customerId: $customerId, phoneNumber: $phoneNumber, currentPoints: $currentPoints, totalEarned: $totalEarned, totalUsed: $totalUsed, grade: $grade, totalCalls: $totalCalls, lastUpdated: $lastUpdated, createdAt: $createdAt)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$CustomerPointsImpl &&
            (identical(other.customerId, customerId) ||
                other.customerId == customerId) &&
            (identical(other.phoneNumber, phoneNumber) ||
                other.phoneNumber == phoneNumber) &&
            (identical(other.currentPoints, currentPoints) ||
                other.currentPoints == currentPoints) &&
            (identical(other.totalEarned, totalEarned) ||
                other.totalEarned == totalEarned) &&
            (identical(other.totalUsed, totalUsed) ||
                other.totalUsed == totalUsed) &&
            (identical(other.grade, grade) || other.grade == grade) &&
            (identical(other.totalCalls, totalCalls) ||
                other.totalCalls == totalCalls) &&
            (identical(other.lastUpdated, lastUpdated) ||
                other.lastUpdated == lastUpdated) &&
            (identical(other.createdAt, createdAt) ||
                other.createdAt == createdAt));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    customerId,
    phoneNumber,
    currentPoints,
    totalEarned,
    totalUsed,
    grade,
    totalCalls,
    lastUpdated,
    createdAt,
  );

  /// Create a copy of CustomerPoints
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$CustomerPointsImplCopyWith<_$CustomerPointsImpl> get copyWith =>
      __$$CustomerPointsImplCopyWithImpl<_$CustomerPointsImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$CustomerPointsImplToJson(this);
  }
}

abstract class _CustomerPoints extends CustomerPoints {
  const factory _CustomerPoints({
    final String customerId,
    final String phoneNumber,
    final int currentPoints,
    final int totalEarned,
    final int totalUsed,
    @CustomerGradeConverter() final CustomerGrade grade,
    final int totalCalls,
    @TimestampConverter() final DateTime? lastUpdated,
    @TimestampConverter() final DateTime? createdAt,
  }) = _$CustomerPointsImpl;
  const _CustomerPoints._() : super._();

  factory _CustomerPoints.fromJson(Map<String, dynamic> json) =
      _$CustomerPointsImpl.fromJson;

  @override
  String get customerId;
  @override
  String get phoneNumber;
  @override
  int get currentPoints;
  @override
  int get totalEarned;
  @override
  int get totalUsed;
  @override
  @CustomerGradeConverter()
  CustomerGrade get grade;
  @override
  int get totalCalls;
  @override
  @TimestampConverter()
  DateTime? get lastUpdated;
  @override
  @TimestampConverter()
  DateTime? get createdAt;

  /// Create a copy of CustomerPoints
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$CustomerPointsImplCopyWith<_$CustomerPointsImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
