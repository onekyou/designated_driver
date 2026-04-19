import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

import '../../../../core/domain/converters/timestamp_converter.dart';
import '../../../../core/domain/converters/transaction_type_converter.dart';
import '../../../../core/domain/enums/transaction_type.dart';

part 'point_transaction.freezed.dart';
part 'point_transaction.g.dart';

/// 포인트 거래 내역 (Kotlin `PointTransaction.kt:8-51` 포팅).
///
/// Firestore 경로: `provinces/{p}/cities/{c}/offices/{o}/pointTransactions/{autoId}`
///
/// `amount`는 부호를 포함해 저장: EARN/CANCEL은 양수, USE/EXPIRE는 음수, ADMIN은 양/음 모두 가능.
/// UI 표시 시 `TransactionType.formatAmount`로 부호 포맷.
@freezed
class PointTransaction with _$PointTransaction {
  const PointTransaction._();

  const factory PointTransaction({
    @Default('') String id,
    @Default('') String customerId,
    @TransactionTypeConverter()
    @Default(TransactionType.earn)
    TransactionType type,
    @Default(0) int amount,
    @Default(0) int balance,
    @Default('') String description,
    String? callId,
    @TimestampConverter() DateTime? timestamp,
    int? fare,
    @Default('BRONZE') String grade,
  }) = _PointTransaction;

  factory PointTransaction.fromJson(Map<String, Object?> json) =>
      _$PointTransactionFromJson(json);

  /// Firestore 문서 → PointTransaction. doc.id 주입 (autoId 생성 시 기본값 '' 덮어씀).
  factory PointTransaction.fromFirestore(
      DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};
    return PointTransaction.fromJson({...data, 'id': doc.id});
  }
}
