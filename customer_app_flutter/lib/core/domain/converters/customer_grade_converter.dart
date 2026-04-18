import 'package:json_annotation/json_annotation.dart';

import '../enums/customer_grade.dart';

/// `CustomerGrade` ↔ `String?` 변환.
///
/// null 또는 미매칭 문자열은 `CustomerGrade.bronze` fallback (Kotlin `fromString` 동작 일치).
class CustomerGradeConverter implements JsonConverter<CustomerGrade, String?> {
  const CustomerGradeConverter();

  @override
  CustomerGrade fromJson(String? json) => CustomerGrade.fromString(json);

  @override
  String toJson(CustomerGrade object) => object.toJson();
}
