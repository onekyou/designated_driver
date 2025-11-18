import 'package:cloud_firestore/cloud_firestore.dart';
import '../../../core/constants/point_constants.dart';

/// 고객 포인트 정보 모델
class CustomerPoints {
  final String customerId; // 전화번호
  final String phoneNumber;
  final int currentPoints; // 보유 포인트
  final int totalEarned; // 총 적립
  final int totalUsed; // 총 사용
  final CustomerGrade grade; // 등급
  final int totalCalls; // 총 이용 횟수
  final DateTime lastUpdated;
  final DateTime createdAt;

  const CustomerPoints({
    required this.customerId,
    required this.phoneNumber,
    required this.currentPoints,
    required this.totalEarned,
    required this.totalUsed,
    required this.grade,
    required this.totalCalls,
    required this.lastUpdated,
    required this.createdAt,
  });

  /// Firestore 문서에서 객체 생성
  factory CustomerPoints.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;

    return CustomerPoints(
      customerId: doc.id,
      phoneNumber: data['phoneNumber'] ?? '',
      currentPoints: data['currentPoints'] ?? 0,
      totalEarned: data['totalEarned'] ?? 0,
      totalUsed: data['totalUsed'] ?? 0,
      grade: CustomerGrade.fromString(data['grade']),
      totalCalls: data['totalCalls'] ?? 0,
      lastUpdated: (data['lastUpdated'] as Timestamp?)?.toDate() ?? DateTime.now(),
      createdAt: (data['createdAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
    );
  }

  /// Firestore에 저장할 Map으로 변환
  Map<String, dynamic> toFirestore() {
    return {
      'phoneNumber': phoneNumber,
      'currentPoints': currentPoints,
      'totalEarned': totalEarned,
      'totalUsed': totalUsed,
      'grade': grade.toFirestore(),
      'totalCalls': totalCalls,
      'lastUpdated': FieldValue.serverTimestamp(),
      'createdAt': Timestamp.fromDate(createdAt),
    };
  }

  /// 포인트 적립 가능 여부
  bool canEarnPoints(int amount) {
    return amount > 0;
  }

  /// 포인트 사용 가능 여부
  bool canUsePoints(int amount) {
    return amount > 0 && currentPoints >= amount;
  }

  /// 등급별 적립 포인트 계산
  int calculateEarnPoints(int fare) {
    return grade.calculatePoints(fare);
  }

  /// 다음 등급까지 필요한 콜 수
  int? getCallsToNextGrade() {
    return grade.getCallsToNextGrade(totalCalls);
  }

  /// copyWith 메서드
  CustomerPoints copyWith({
    String? customerId,
    String? phoneNumber,
    int? currentPoints,
    int? totalEarned,
    int? totalUsed,
    CustomerGrade? grade,
    int? totalCalls,
    DateTime? lastUpdated,
    DateTime? createdAt,
  }) {
    return CustomerPoints(
      customerId: customerId ?? this.customerId,
      phoneNumber: phoneNumber ?? this.phoneNumber,
      currentPoints: currentPoints ?? this.currentPoints,
      totalEarned: totalEarned ?? this.totalEarned,
      totalUsed: totalUsed ?? this.totalUsed,
      grade: grade ?? this.grade,
      totalCalls: totalCalls ?? this.totalCalls,
      lastUpdated: lastUpdated ?? this.lastUpdated,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  /// 새 고객 포인트 생성 (초기값)
  factory CustomerPoints.create(String phoneNumber) {
    final now = DateTime.now();
    return CustomerPoints(
      customerId: phoneNumber,
      phoneNumber: phoneNumber,
      currentPoints: 0,
      totalEarned: 0,
      totalUsed: 0,
      grade: CustomerGrade.bronze,
      totalCalls: 0,
      lastUpdated: now,
      createdAt: now,
    );
  }

  @override
  String toString() {
    return 'CustomerPoints(phoneNumber: $phoneNumber, currentPoints: $currentPoints, grade: ${grade.displayName}, totalCalls: $totalCalls)';
  }
}
