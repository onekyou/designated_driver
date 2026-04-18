/// 손님 등급 4종 (Kotlin `CustomerGrade.kt:7-94` 포팅).
///
/// Firestore 저장은 enum `.name` 대문자 문자열 (`json` 필드 사용).
/// Dart enum 값 이름(`bronze`)과 Firestore 저장값(`BRONZE`) 차이 매핑용.
enum CustomerGrade {
  bronze(
    json: 'BRONZE',
    displayName: '브론즈',
    icon: '🥉',
    pointRate: 0.03,
    minCalls: 0,
    colorArgb: 0xFFCD7F32,
  ),
  silver(
    json: 'SILVER',
    displayName: '실버',
    icon: '🥈',
    pointRate: 0.05,
    minCalls: 10,
    colorArgb: 0xFFC0C0C0,
  ),
  gold(
    json: 'GOLD',
    displayName: '골드',
    icon: '🥇',
    pointRate: 0.07,
    minCalls: 30,
    colorArgb: 0xFFFFD700,
  ),
  vip(
    json: 'VIP',
    displayName: 'VIP',
    icon: '⭐',
    pointRate: 0.09,
    minCalls: 50,
    colorArgb: 0xFFFF6B6B,
  );

  const CustomerGrade({
    required this.json,
    required this.displayName,
    required this.icon,
    required this.pointRate,
    required this.minCalls,
    required this.colorArgb,
  });

  /// Firestore 저장 문자열 (Kotlin enum `.name`과 동일, 대문자).
  final String json;

  /// UI 표시 한글.
  final String displayName;

  /// UI 표시 이모지.
  final String icon;

  /// 포인트 적립률 (fare × pointRate).
  final double pointRate;

  /// 승격 기준 최소 콜 수.
  final int minCalls;

  /// 등급별 테마 색상 (ARGB int, Kotlin color Long과 동일 값).
  final int colorArgb;

  /// Firestore 저장 문자열 → enum (Kotlin `fromString` 포팅).
  /// null/미매칭은 BRONZE fallback.
  static CustomerGrade fromString(String? value) =>
      CustomerGrade.values.firstWhere(
        (e) => e.json == value?.toUpperCase(),
        orElse: () => CustomerGrade.bronze,
      );

  String toJson() => json;

  /// 콜 수 기반 등급 계산 (Kotlin `fromCallCount` 포팅).
  static CustomerGrade fromCallCount(int totalCalls) {
    if (totalCalls >= 50) return CustomerGrade.vip;
    if (totalCalls >= 30) return CustomerGrade.gold;
    if (totalCalls >= 10) return CustomerGrade.silver;
    return CustomerGrade.bronze;
  }

  /// 포인트 적립 계산 (Kotlin `calculatePoints` 포팅).
  int calculatePoints(int fare) => (fare * pointRate).toInt();

  /// 다음 등급 (Kotlin `nextGrade` 포팅, VIP는 null).
  CustomerGrade? get nextGrade => switch (this) {
        CustomerGrade.bronze => CustomerGrade.silver,
        CustomerGrade.silver => CustomerGrade.gold,
        CustomerGrade.gold => CustomerGrade.vip,
        CustomerGrade.vip => null,
      };

  /// 다음 등급까지 남은 콜 수 (Kotlin `getCallsToNextGrade` 포팅, VIP는 null).
  int? callsToNext(int currentCalls) {
    final next = nextGrade;
    if (next == null) return null;
    return next.minCalls - currentCalls;
  }
}
