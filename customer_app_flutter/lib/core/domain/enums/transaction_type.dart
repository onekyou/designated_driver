/// 포인트 거래 유형 5종 (Kotlin `PointTransaction.kt:56-62` 포팅).
///
/// Kotlin 원본은 필드 없는 단순 enum. Dart에서는 UI 부호 포맷팅을 위해
/// `displayName`/`sign` 필드 확장 (Kotlin과 직렬화 호환 — `.name` 사용).
enum TransactionType {
  earn(json: 'EARN', displayName: '적립', sign: 1),
  use(json: 'USE', displayName: '사용', sign: -1),
  expire(json: 'EXPIRE', displayName: '만료', sign: -1),
  cancel(json: 'CANCEL', displayName: '취소 환불', sign: 1),
  admin(json: 'ADMIN', displayName: '관리자 조정', sign: 0);

  const TransactionType({
    required this.json,
    required this.displayName,
    required this.sign,
  });

  /// Firestore 저장 문자열 (Kotlin enum `.name`과 동일, 대문자).
  final String json;

  /// UI 표시 한글.
  final String displayName;

  /// amount 예상 부호 (+1: 증가, -1: 감소, 0: 양쪽 가능).
  final int sign;

  /// Firestore 저장 문자열 → enum. 미매칭은 `earn` fallback.
  static TransactionType fromString(String? value) =>
      TransactionType.values.firstWhere(
        (e) => e.json == value?.toUpperCase(),
        orElse: () => TransactionType.earn,
      );

  String toJson() => json;

  /// amount 입력 검증 (클라이언트 측 사전 검증, CF에서도 재검증).
  bool isValidAmount(int amount) => switch (sign) {
        1 => amount > 0,
        -1 => amount < 0,
        0 => true,
        _ => false,
      };

  /// UI 표시용 부호 포함 금액 문자열. amount는 Firestore 저장값(부호 포함).
  String formatAmount(int amount) {
    final absAmount = amount.abs();
    return switch (this) {
      TransactionType.earn || TransactionType.cancel => '+$absAmount',
      TransactionType.use || TransactionType.expire => '-$absAmount',
      TransactionType.admin => amount >= 0 ? '+$absAmount' : '-$absAmount',
    };
  }
}
