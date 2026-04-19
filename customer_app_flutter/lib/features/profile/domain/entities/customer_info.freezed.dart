// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'customer_info.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

CustomerInfo _$CustomerInfoFromJson(Map<String, dynamic> json) {
  return _CustomerInfo.fromJson(json);
}

/// @nodoc
mixin _$CustomerInfo {
  String get id => throw _privateConstructorUsedError;
  String get phoneNumber => throw _privateConstructorUsedError;
  String get name => throw _privateConstructorUsedError;
  String get grade => throw _privateConstructorUsedError;
  int get points => throw _privateConstructorUsedError;
  int get totalRides => throw _privateConstructorUsedError;
  int get totalSpent => throw _privateConstructorUsedError;
  String get linkedOfficeId => throw _privateConstructorUsedError;
  String get primaryOfficeId => throw _privateConstructorUsedError;
  int? get attributionScore => throw _privateConstructorUsedError;
  String? get attributionSource => throw _privateConstructorUsedError;
  @TimestampConverter()
  DateTime? get registeredAt => throw _privateConstructorUsedError;
  @TimestampConverter()
  DateTime? get lastRideAt => throw _privateConstructorUsedError;
  String get officePhone => throw _privateConstructorUsedError;
  String get bankName => throw _privateConstructorUsedError;
  String get accountNumber => throw _privateConstructorUsedError;
  String get accountHolder => throw _privateConstructorUsedError;
  String get homeAddress => throw _privateConstructorUsedError;

  /// Serializes this CustomerInfo to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of CustomerInfo
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $CustomerInfoCopyWith<CustomerInfo> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $CustomerInfoCopyWith<$Res> {
  factory $CustomerInfoCopyWith(
    CustomerInfo value,
    $Res Function(CustomerInfo) then,
  ) = _$CustomerInfoCopyWithImpl<$Res, CustomerInfo>;
  @useResult
  $Res call({
    String id,
    String phoneNumber,
    String name,
    String grade,
    int points,
    int totalRides,
    int totalSpent,
    String linkedOfficeId,
    String primaryOfficeId,
    int? attributionScore,
    String? attributionSource,
    @TimestampConverter() DateTime? registeredAt,
    @TimestampConverter() DateTime? lastRideAt,
    String officePhone,
    String bankName,
    String accountNumber,
    String accountHolder,
    String homeAddress,
  });
}

/// @nodoc
class _$CustomerInfoCopyWithImpl<$Res, $Val extends CustomerInfo>
    implements $CustomerInfoCopyWith<$Res> {
  _$CustomerInfoCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of CustomerInfo
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? phoneNumber = null,
    Object? name = null,
    Object? grade = null,
    Object? points = null,
    Object? totalRides = null,
    Object? totalSpent = null,
    Object? linkedOfficeId = null,
    Object? primaryOfficeId = null,
    Object? attributionScore = freezed,
    Object? attributionSource = freezed,
    Object? registeredAt = freezed,
    Object? lastRideAt = freezed,
    Object? officePhone = null,
    Object? bankName = null,
    Object? accountNumber = null,
    Object? accountHolder = null,
    Object? homeAddress = null,
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
            name: null == name
                ? _value.name
                : name // ignore: cast_nullable_to_non_nullable
                      as String,
            grade: null == grade
                ? _value.grade
                : grade // ignore: cast_nullable_to_non_nullable
                      as String,
            points: null == points
                ? _value.points
                : points // ignore: cast_nullable_to_non_nullable
                      as int,
            totalRides: null == totalRides
                ? _value.totalRides
                : totalRides // ignore: cast_nullable_to_non_nullable
                      as int,
            totalSpent: null == totalSpent
                ? _value.totalSpent
                : totalSpent // ignore: cast_nullable_to_non_nullable
                      as int,
            linkedOfficeId: null == linkedOfficeId
                ? _value.linkedOfficeId
                : linkedOfficeId // ignore: cast_nullable_to_non_nullable
                      as String,
            primaryOfficeId: null == primaryOfficeId
                ? _value.primaryOfficeId
                : primaryOfficeId // ignore: cast_nullable_to_non_nullable
                      as String,
            attributionScore: freezed == attributionScore
                ? _value.attributionScore
                : attributionScore // ignore: cast_nullable_to_non_nullable
                      as int?,
            attributionSource: freezed == attributionSource
                ? _value.attributionSource
                : attributionSource // ignore: cast_nullable_to_non_nullable
                      as String?,
            registeredAt: freezed == registeredAt
                ? _value.registeredAt
                : registeredAt // ignore: cast_nullable_to_non_nullable
                      as DateTime?,
            lastRideAt: freezed == lastRideAt
                ? _value.lastRideAt
                : lastRideAt // ignore: cast_nullable_to_non_nullable
                      as DateTime?,
            officePhone: null == officePhone
                ? _value.officePhone
                : officePhone // ignore: cast_nullable_to_non_nullable
                      as String,
            bankName: null == bankName
                ? _value.bankName
                : bankName // ignore: cast_nullable_to_non_nullable
                      as String,
            accountNumber: null == accountNumber
                ? _value.accountNumber
                : accountNumber // ignore: cast_nullable_to_non_nullable
                      as String,
            accountHolder: null == accountHolder
                ? _value.accountHolder
                : accountHolder // ignore: cast_nullable_to_non_nullable
                      as String,
            homeAddress: null == homeAddress
                ? _value.homeAddress
                : homeAddress // ignore: cast_nullable_to_non_nullable
                      as String,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$CustomerInfoImplCopyWith<$Res>
    implements $CustomerInfoCopyWith<$Res> {
  factory _$$CustomerInfoImplCopyWith(
    _$CustomerInfoImpl value,
    $Res Function(_$CustomerInfoImpl) then,
  ) = __$$CustomerInfoImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    String id,
    String phoneNumber,
    String name,
    String grade,
    int points,
    int totalRides,
    int totalSpent,
    String linkedOfficeId,
    String primaryOfficeId,
    int? attributionScore,
    String? attributionSource,
    @TimestampConverter() DateTime? registeredAt,
    @TimestampConverter() DateTime? lastRideAt,
    String officePhone,
    String bankName,
    String accountNumber,
    String accountHolder,
    String homeAddress,
  });
}

/// @nodoc
class __$$CustomerInfoImplCopyWithImpl<$Res>
    extends _$CustomerInfoCopyWithImpl<$Res, _$CustomerInfoImpl>
    implements _$$CustomerInfoImplCopyWith<$Res> {
  __$$CustomerInfoImplCopyWithImpl(
    _$CustomerInfoImpl _value,
    $Res Function(_$CustomerInfoImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of CustomerInfo
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? phoneNumber = null,
    Object? name = null,
    Object? grade = null,
    Object? points = null,
    Object? totalRides = null,
    Object? totalSpent = null,
    Object? linkedOfficeId = null,
    Object? primaryOfficeId = null,
    Object? attributionScore = freezed,
    Object? attributionSource = freezed,
    Object? registeredAt = freezed,
    Object? lastRideAt = freezed,
    Object? officePhone = null,
    Object? bankName = null,
    Object? accountNumber = null,
    Object? accountHolder = null,
    Object? homeAddress = null,
  }) {
    return _then(
      _$CustomerInfoImpl(
        id: null == id
            ? _value.id
            : id // ignore: cast_nullable_to_non_nullable
                  as String,
        phoneNumber: null == phoneNumber
            ? _value.phoneNumber
            : phoneNumber // ignore: cast_nullable_to_non_nullable
                  as String,
        name: null == name
            ? _value.name
            : name // ignore: cast_nullable_to_non_nullable
                  as String,
        grade: null == grade
            ? _value.grade
            : grade // ignore: cast_nullable_to_non_nullable
                  as String,
        points: null == points
            ? _value.points
            : points // ignore: cast_nullable_to_non_nullable
                  as int,
        totalRides: null == totalRides
            ? _value.totalRides
            : totalRides // ignore: cast_nullable_to_non_nullable
                  as int,
        totalSpent: null == totalSpent
            ? _value.totalSpent
            : totalSpent // ignore: cast_nullable_to_non_nullable
                  as int,
        linkedOfficeId: null == linkedOfficeId
            ? _value.linkedOfficeId
            : linkedOfficeId // ignore: cast_nullable_to_non_nullable
                  as String,
        primaryOfficeId: null == primaryOfficeId
            ? _value.primaryOfficeId
            : primaryOfficeId // ignore: cast_nullable_to_non_nullable
                  as String,
        attributionScore: freezed == attributionScore
            ? _value.attributionScore
            : attributionScore // ignore: cast_nullable_to_non_nullable
                  as int?,
        attributionSource: freezed == attributionSource
            ? _value.attributionSource
            : attributionSource // ignore: cast_nullable_to_non_nullable
                  as String?,
        registeredAt: freezed == registeredAt
            ? _value.registeredAt
            : registeredAt // ignore: cast_nullable_to_non_nullable
                  as DateTime?,
        lastRideAt: freezed == lastRideAt
            ? _value.lastRideAt
            : lastRideAt // ignore: cast_nullable_to_non_nullable
                  as DateTime?,
        officePhone: null == officePhone
            ? _value.officePhone
            : officePhone // ignore: cast_nullable_to_non_nullable
                  as String,
        bankName: null == bankName
            ? _value.bankName
            : bankName // ignore: cast_nullable_to_non_nullable
                  as String,
        accountNumber: null == accountNumber
            ? _value.accountNumber
            : accountNumber // ignore: cast_nullable_to_non_nullable
                  as String,
        accountHolder: null == accountHolder
            ? _value.accountHolder
            : accountHolder // ignore: cast_nullable_to_non_nullable
                  as String,
        homeAddress: null == homeAddress
            ? _value.homeAddress
            : homeAddress // ignore: cast_nullable_to_non_nullable
                  as String,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$CustomerInfoImpl extends _CustomerInfo {
  const _$CustomerInfoImpl({
    this.id = '',
    this.phoneNumber = '',
    this.name = '',
    this.grade = 'bronze',
    this.points = 0,
    this.totalRides = 0,
    this.totalSpent = 0,
    this.linkedOfficeId = '',
    this.primaryOfficeId = '',
    this.attributionScore,
    this.attributionSource,
    @TimestampConverter() this.registeredAt,
    @TimestampConverter() this.lastRideAt,
    this.officePhone = '',
    this.bankName = '',
    this.accountNumber = '',
    this.accountHolder = '',
    this.homeAddress = '',
  }) : super._();

  factory _$CustomerInfoImpl.fromJson(Map<String, dynamic> json) =>
      _$$CustomerInfoImplFromJson(json);

  @override
  @JsonKey()
  final String id;
  @override
  @JsonKey()
  final String phoneNumber;
  @override
  @JsonKey()
  final String name;
  @override
  @JsonKey()
  final String grade;
  @override
  @JsonKey()
  final int points;
  @override
  @JsonKey()
  final int totalRides;
  @override
  @JsonKey()
  final int totalSpent;
  @override
  @JsonKey()
  final String linkedOfficeId;
  @override
  @JsonKey()
  final String primaryOfficeId;
  @override
  final int? attributionScore;
  @override
  final String? attributionSource;
  @override
  @TimestampConverter()
  final DateTime? registeredAt;
  @override
  @TimestampConverter()
  final DateTime? lastRideAt;
  @override
  @JsonKey()
  final String officePhone;
  @override
  @JsonKey()
  final String bankName;
  @override
  @JsonKey()
  final String accountNumber;
  @override
  @JsonKey()
  final String accountHolder;
  @override
  @JsonKey()
  final String homeAddress;

  @override
  String toString() {
    return 'CustomerInfo(id: $id, phoneNumber: $phoneNumber, name: $name, grade: $grade, points: $points, totalRides: $totalRides, totalSpent: $totalSpent, linkedOfficeId: $linkedOfficeId, primaryOfficeId: $primaryOfficeId, attributionScore: $attributionScore, attributionSource: $attributionSource, registeredAt: $registeredAt, lastRideAt: $lastRideAt, officePhone: $officePhone, bankName: $bankName, accountNumber: $accountNumber, accountHolder: $accountHolder, homeAddress: $homeAddress)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$CustomerInfoImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.phoneNumber, phoneNumber) ||
                other.phoneNumber == phoneNumber) &&
            (identical(other.name, name) || other.name == name) &&
            (identical(other.grade, grade) || other.grade == grade) &&
            (identical(other.points, points) || other.points == points) &&
            (identical(other.totalRides, totalRides) ||
                other.totalRides == totalRides) &&
            (identical(other.totalSpent, totalSpent) ||
                other.totalSpent == totalSpent) &&
            (identical(other.linkedOfficeId, linkedOfficeId) ||
                other.linkedOfficeId == linkedOfficeId) &&
            (identical(other.primaryOfficeId, primaryOfficeId) ||
                other.primaryOfficeId == primaryOfficeId) &&
            (identical(other.attributionScore, attributionScore) ||
                other.attributionScore == attributionScore) &&
            (identical(other.attributionSource, attributionSource) ||
                other.attributionSource == attributionSource) &&
            (identical(other.registeredAt, registeredAt) ||
                other.registeredAt == registeredAt) &&
            (identical(other.lastRideAt, lastRideAt) ||
                other.lastRideAt == lastRideAt) &&
            (identical(other.officePhone, officePhone) ||
                other.officePhone == officePhone) &&
            (identical(other.bankName, bankName) ||
                other.bankName == bankName) &&
            (identical(other.accountNumber, accountNumber) ||
                other.accountNumber == accountNumber) &&
            (identical(other.accountHolder, accountHolder) ||
                other.accountHolder == accountHolder) &&
            (identical(other.homeAddress, homeAddress) ||
                other.homeAddress == homeAddress));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    id,
    phoneNumber,
    name,
    grade,
    points,
    totalRides,
    totalSpent,
    linkedOfficeId,
    primaryOfficeId,
    attributionScore,
    attributionSource,
    registeredAt,
    lastRideAt,
    officePhone,
    bankName,
    accountNumber,
    accountHolder,
    homeAddress,
  );

  /// Create a copy of CustomerInfo
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$CustomerInfoImplCopyWith<_$CustomerInfoImpl> get copyWith =>
      __$$CustomerInfoImplCopyWithImpl<_$CustomerInfoImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$CustomerInfoImplToJson(this);
  }
}

abstract class _CustomerInfo extends CustomerInfo {
  const factory _CustomerInfo({
    final String id,
    final String phoneNumber,
    final String name,
    final String grade,
    final int points,
    final int totalRides,
    final int totalSpent,
    final String linkedOfficeId,
    final String primaryOfficeId,
    final int? attributionScore,
    final String? attributionSource,
    @TimestampConverter() final DateTime? registeredAt,
    @TimestampConverter() final DateTime? lastRideAt,
    final String officePhone,
    final String bankName,
    final String accountNumber,
    final String accountHolder,
    final String homeAddress,
  }) = _$CustomerInfoImpl;
  const _CustomerInfo._() : super._();

  factory _CustomerInfo.fromJson(Map<String, dynamic> json) =
      _$CustomerInfoImpl.fromJson;

  @override
  String get id;
  @override
  String get phoneNumber;
  @override
  String get name;
  @override
  String get grade;
  @override
  int get points;
  @override
  int get totalRides;
  @override
  int get totalSpent;
  @override
  String get linkedOfficeId;
  @override
  String get primaryOfficeId;
  @override
  int? get attributionScore;
  @override
  String? get attributionSource;
  @override
  @TimestampConverter()
  DateTime? get registeredAt;
  @override
  @TimestampConverter()
  DateTime? get lastRideAt;
  @override
  String get officePhone;
  @override
  String get bankName;
  @override
  String get accountNumber;
  @override
  String get accountHolder;
  @override
  String get homeAddress;

  /// Create a copy of CustomerInfo
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$CustomerInfoImplCopyWith<_$CustomerInfoImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
