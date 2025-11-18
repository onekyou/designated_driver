import 'package:flutter/material.dart';

/// 포인트 시스템 관련 상수
class PointConstants {
  // 포인트 적립 기준
  static const int pointsPerCall = 100; // 콜당 기본 포인트
  static const int pointsPerSteps1000 = 10; // 1000보당 포인트
  static const int pointsPerDailyLogin = 5; // 일일 로그인 포인트

  // 포인트 유형
  static const String typeCall = 'call'; // 콜 사용
  static const String typeSteps = 'steps'; // 만보기
  static const String typeLogin = 'login'; // 로그인
  static const String typeBonus = 'bonus'; // 보너스
  static const String typeDeduction = 'deduction'; // 차감

  // 포인트 설명
  static const String descCall = '대리운전 이용';
  static const String descSteps = '만보기 포인트';
  static const String descLogin = '일일 로그인';
  static const String descBonus = '보너스 포인트';
  static const String descDeduction = '포인트 사용';

  // 만보기 설정
  static const int minStepsForPoints = 1000; // 포인트 적립 최소 걸음수
  static const int maxStepsPerDay = 20000; // 하루 최대 인정 걸음수
  static const int stepsGoal = 10000; // 일일 목표 걸음수
}

/// 고객 등급 정의 (콜매니저와 동일한 등급 체계)
enum CustomerGrade {
  bronze(
    displayName: '브론즈',
    icon: '🥉',
    pointRate: 0.03, // 3% 적립
    minCalls: 0,
    color: Color(0xFFCD7F32),
  ),
  silver(
    displayName: '실버',
    icon: '🥈',
    pointRate: 0.05, // 5% 적립
    minCalls: 10, // 10회 이상 이용
    color: Color(0xFFC0C0C0),
  ),
  gold(
    displayName: '골드',
    icon: '🥇',
    pointRate: 0.07, // 7% 적립
    minCalls: 30, // 30회 이상 이용
    color: Color(0xFFFFD700),
  ),
  vip(
    displayName: 'VIP',
    icon: '⭐',
    pointRate: 0.09, // 9% 적립
    minCalls: 50, // 50회 이상 이용
    color: Color(0xFFFF6B6B),
  );

  const CustomerGrade({
    required this.displayName,
    required this.icon,
    required this.pointRate,
    required this.minCalls,
    required this.color,
  });

  final String displayName;
  final String icon;
  final double pointRate;
  final int minCalls;
  final Color color;

  /// 이용 횟수에 따른 등급 계산
  static CustomerGrade fromCallCount(int callCount) {
    if (callCount >= 50) return CustomerGrade.vip;
    if (callCount >= 30) return CustomerGrade.gold;
    if (callCount >= 10) return CustomerGrade.silver;
    return CustomerGrade.bronze;
  }

  /// 문자열로부터 등급 변환
  static CustomerGrade fromString(String? grade) {
    switch (grade?.toUpperCase()) {
      case 'VIP':
        return CustomerGrade.vip;
      case 'GOLD':
        return CustomerGrade.gold;
      case 'SILVER':
        return CustomerGrade.silver;
      default:
        return CustomerGrade.bronze;
    }
  }

  /// 다음 등급까지 필요한 콜 수
  int? getCallsToNextGrade(int currentCalls) {
    final nextGrade = switch (this) {
      CustomerGrade.bronze => CustomerGrade.silver,
      CustomerGrade.silver => CustomerGrade.gold,
      CustomerGrade.gold => CustomerGrade.vip,
      CustomerGrade.vip => null, // 최고 등급
    };

    if (nextGrade == null) return null;
    return nextGrade.minCalls - currentCalls;
  }

  /// 다음 등급
  CustomerGrade? get nextGrade {
    return switch (this) {
      CustomerGrade.bronze => CustomerGrade.silver,
      CustomerGrade.silver => CustomerGrade.gold,
      CustomerGrade.gold => CustomerGrade.vip,
      CustomerGrade.vip => null,
    };
  }

  /// 포인트 적립 계산
  int calculatePoints(int amount) {
    return (amount * pointRate).toInt();
  }

  /// Firestore에 저장할 문자열
  String toFirestore() {
    return name.toUpperCase();
  }
}
