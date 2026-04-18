import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:json_annotation/json_annotation.dart';

/// Firestore `Timestamp` ↔ Dart `DateTime?` 변환.
///
/// Firestore 저장 시에는 `Timestamp`로, JSON 직렬화/복원 시에는
/// `Timestamp` 객체 또는 legacy `int` (밀리초) 또는 ISO `String` 모두 대응.
/// Kotlin 원본 `fromMap` 로직의 관용 패턴 포팅.
class TimestampConverter implements JsonConverter<DateTime?, dynamic> {
  const TimestampConverter();

  @override
  DateTime? fromJson(dynamic value) {
    if (value == null) return null;
    if (value is Timestamp) return value.toDate();
    if (value is int) return DateTime.fromMillisecondsSinceEpoch(value);
    if (value is String) return DateTime.tryParse(value);
    return null;
  }

  @override
  dynamic toJson(DateTime? value) =>
      value == null ? null : Timestamp.fromDate(value);
}
