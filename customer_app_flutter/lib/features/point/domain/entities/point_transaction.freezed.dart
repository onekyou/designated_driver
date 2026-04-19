// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'point_transaction.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

PointTransaction _$PointTransactionFromJson(Map<String, dynamic> json) {
  return _PointTransaction.fromJson(json);
}

/// @nodoc
mixin _$PointTransaction {
  String get id => throw _privateConstructorUsedError;
  String get customerId => throw _privateConstructorUsedError;
  @TransactionTypeConverter()
  TransactionType get type => throw _privateConstructorUsedError;
  int get amount => throw _privateConstructorUsedError;
  int get balance => throw _privateConstructorUsedError;
  String get description => throw _privateConstructorUsedError;
  String? get callId => throw _privateConstructorUsedError;
  @TimestampConverter()
  DateTime? get timestamp => throw _privateConstructorUsedError;
  int? get fare => throw _privateConstructorUsedError;
  String get grade => throw _privateConstructorUsedError;

  /// Serializes this PointTransaction to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of PointTransaction
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $PointTransactionCopyWith<PointTransaction> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $PointTransactionCopyWith<$Res> {
  factory $PointTransactionCopyWith(
    PointTransaction value,
    $Res Function(PointTransaction) then,
  ) = _$PointTransactionCopyWithImpl<$Res, PointTransaction>;
  @useResult
  $Res call({
    String id,
    String customerId,
    @TransactionTypeConverter() TransactionType type,
    int amount,
    int balance,
    String description,
    String? callId,
    @TimestampConverter() DateTime? timestamp,
    int? fare,
    String grade,
  });
}

/// @nodoc
class _$PointTransactionCopyWithImpl<$Res, $Val extends PointTransaction>
    implements $PointTransactionCopyWith<$Res> {
  _$PointTransactionCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of PointTransaction
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? customerId = null,
    Object? type = null,
    Object? amount = null,
    Object? balance = null,
    Object? description = null,
    Object? callId = freezed,
    Object? timestamp = freezed,
    Object? fare = freezed,
    Object? grade = null,
  }) {
    return _then(
      _value.copyWith(
            id: null == id
                ? _value.id
                : id // ignore: cast_nullable_to_non_nullable
                      as String,
            customerId: null == customerId
                ? _value.customerId
                : customerId // ignore: cast_nullable_to_non_nullable
                      as String,
            type: null == type
                ? _value.type
                : type // ignore: cast_nullable_to_non_nullable
                      as TransactionType,
            amount: null == amount
                ? _value.amount
                : amount // ignore: cast_nullable_to_non_nullable
                      as int,
            balance: null == balance
                ? _value.balance
                : balance // ignore: cast_nullable_to_non_nullable
                      as int,
            description: null == description
                ? _value.description
                : description // ignore: cast_nullable_to_non_nullable
                      as String,
            callId: freezed == callId
                ? _value.callId
                : callId // ignore: cast_nullable_to_non_nullable
                      as String?,
            timestamp: freezed == timestamp
                ? _value.timestamp
                : timestamp // ignore: cast_nullable_to_non_nullable
                      as DateTime?,
            fare: freezed == fare
                ? _value.fare
                : fare // ignore: cast_nullable_to_non_nullable
                      as int?,
            grade: null == grade
                ? _value.grade
                : grade // ignore: cast_nullable_to_non_nullable
                      as String,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$PointTransactionImplCopyWith<$Res>
    implements $PointTransactionCopyWith<$Res> {
  factory _$$PointTransactionImplCopyWith(
    _$PointTransactionImpl value,
    $Res Function(_$PointTransactionImpl) then,
  ) = __$$PointTransactionImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    String id,
    String customerId,
    @TransactionTypeConverter() TransactionType type,
    int amount,
    int balance,
    String description,
    String? callId,
    @TimestampConverter() DateTime? timestamp,
    int? fare,
    String grade,
  });
}

/// @nodoc
class __$$PointTransactionImplCopyWithImpl<$Res>
    extends _$PointTransactionCopyWithImpl<$Res, _$PointTransactionImpl>
    implements _$$PointTransactionImplCopyWith<$Res> {
  __$$PointTransactionImplCopyWithImpl(
    _$PointTransactionImpl _value,
    $Res Function(_$PointTransactionImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of PointTransaction
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? customerId = null,
    Object? type = null,
    Object? amount = null,
    Object? balance = null,
    Object? description = null,
    Object? callId = freezed,
    Object? timestamp = freezed,
    Object? fare = freezed,
    Object? grade = null,
  }) {
    return _then(
      _$PointTransactionImpl(
        id: null == id
            ? _value.id
            : id // ignore: cast_nullable_to_non_nullable
                  as String,
        customerId: null == customerId
            ? _value.customerId
            : customerId // ignore: cast_nullable_to_non_nullable
                  as String,
        type: null == type
            ? _value.type
            : type // ignore: cast_nullable_to_non_nullable
                  as TransactionType,
        amount: null == amount
            ? _value.amount
            : amount // ignore: cast_nullable_to_non_nullable
                  as int,
        balance: null == balance
            ? _value.balance
            : balance // ignore: cast_nullable_to_non_nullable
                  as int,
        description: null == description
            ? _value.description
            : description // ignore: cast_nullable_to_non_nullable
                  as String,
        callId: freezed == callId
            ? _value.callId
            : callId // ignore: cast_nullable_to_non_nullable
                  as String?,
        timestamp: freezed == timestamp
            ? _value.timestamp
            : timestamp // ignore: cast_nullable_to_non_nullable
                  as DateTime?,
        fare: freezed == fare
            ? _value.fare
            : fare // ignore: cast_nullable_to_non_nullable
                  as int?,
        grade: null == grade
            ? _value.grade
            : grade // ignore: cast_nullable_to_non_nullable
                  as String,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$PointTransactionImpl extends _PointTransaction {
  const _$PointTransactionImpl({
    this.id = '',
    this.customerId = '',
    @TransactionTypeConverter() this.type = TransactionType.earn,
    this.amount = 0,
    this.balance = 0,
    this.description = '',
    this.callId,
    @TimestampConverter() this.timestamp,
    this.fare,
    this.grade = 'BRONZE',
  }) : super._();

  factory _$PointTransactionImpl.fromJson(Map<String, dynamic> json) =>
      _$$PointTransactionImplFromJson(json);

  @override
  @JsonKey()
  final String id;
  @override
  @JsonKey()
  final String customerId;
  @override
  @JsonKey()
  @TransactionTypeConverter()
  final TransactionType type;
  @override
  @JsonKey()
  final int amount;
  @override
  @JsonKey()
  final int balance;
  @override
  @JsonKey()
  final String description;
  @override
  final String? callId;
  @override
  @TimestampConverter()
  final DateTime? timestamp;
  @override
  final int? fare;
  @override
  @JsonKey()
  final String grade;

  @override
  String toString() {
    return 'PointTransaction(id: $id, customerId: $customerId, type: $type, amount: $amount, balance: $balance, description: $description, callId: $callId, timestamp: $timestamp, fare: $fare, grade: $grade)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$PointTransactionImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.customerId, customerId) ||
                other.customerId == customerId) &&
            (identical(other.type, type) || other.type == type) &&
            (identical(other.amount, amount) || other.amount == amount) &&
            (identical(other.balance, balance) || other.balance == balance) &&
            (identical(other.description, description) ||
                other.description == description) &&
            (identical(other.callId, callId) || other.callId == callId) &&
            (identical(other.timestamp, timestamp) ||
                other.timestamp == timestamp) &&
            (identical(other.fare, fare) || other.fare == fare) &&
            (identical(other.grade, grade) || other.grade == grade));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    id,
    customerId,
    type,
    amount,
    balance,
    description,
    callId,
    timestamp,
    fare,
    grade,
  );

  /// Create a copy of PointTransaction
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$PointTransactionImplCopyWith<_$PointTransactionImpl> get copyWith =>
      __$$PointTransactionImplCopyWithImpl<_$PointTransactionImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$PointTransactionImplToJson(this);
  }
}

abstract class _PointTransaction extends PointTransaction {
  const factory _PointTransaction({
    final String id,
    final String customerId,
    @TransactionTypeConverter() final TransactionType type,
    final int amount,
    final int balance,
    final String description,
    final String? callId,
    @TimestampConverter() final DateTime? timestamp,
    final int? fare,
    final String grade,
  }) = _$PointTransactionImpl;
  const _PointTransaction._() : super._();

  factory _PointTransaction.fromJson(Map<String, dynamic> json) =
      _$PointTransactionImpl.fromJson;

  @override
  String get id;
  @override
  String get customerId;
  @override
  @TransactionTypeConverter()
  TransactionType get type;
  @override
  int get amount;
  @override
  int get balance;
  @override
  String get description;
  @override
  String? get callId;
  @override
  @TimestampConverter()
  DateTime? get timestamp;
  @override
  int? get fare;
  @override
  String get grade;

  /// Create a copy of PointTransaction
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$PointTransactionImplCopyWith<_$PointTransactionImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
