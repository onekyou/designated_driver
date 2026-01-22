import 'package:cloud_firestore/cloud_firestore.dart';

/// 포인트 거래 타입
enum TransactionType {
  earn('EARN', '적립'),
  use('USE', '사용'),
  expire('EXPIRE', '만료'),
  cancel('CANCEL', '취소'),
  admin('ADMIN', '관리자 조정');

  const TransactionType(this.code, this.displayName);

  final String code;
  final String displayName;

  static TransactionType fromString(String? type) {
    switch (type?.toUpperCase()) {
      case 'EARN':
        return TransactionType.earn;
      case 'USE':
        return TransactionType.use;
      case 'EXPIRE':
        return TransactionType.expire;
      case 'CANCEL':
        return TransactionType.cancel;
      case 'ADMIN':
        return TransactionType.admin;
      default:
        return TransactionType.earn;
    }
  }
}

/// 포인트 거래 내역 모델
class PointTransaction {
  final String id;
  final String customerId; // 전화번호
  final TransactionType type;
  final int amount; // 적립: 양수, 사용: 음수
  final int balance; // 거래 후 잔액
  final String description;
  final String? callId; // 관련 콜 ID (있는 경우)
  final int? fare; // 콜 요금 (적립 시)
  final String grade; // 거래 당시 등급
  final DateTime timestamp;

  const PointTransaction({
    required this.id,
    required this.customerId,
    required this.type,
    required this.amount,
    required this.balance,
    required this.description,
    this.callId,
    this.fare,
    required this.grade,
    required this.timestamp,
  });

  /// Firestore 문서에서 객체 생성
  factory PointTransaction.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;

    return PointTransaction(
      id: doc.id,
      customerId: data['customerId'] ?? '',
      type: TransactionType.fromString(data['type']),
      amount: data['amount'] ?? 0,
      balance: data['balance'] ?? 0,
      description: data['description'] ?? '',
      callId: data['callId'],
      fare: data['fare'],
      grade: data['grade'] ?? 'BRONZE',
      timestamp: (data['timestamp'] as Timestamp?)?.toDate() ?? DateTime.now(),
    );
  }

  /// Firestore에 저장할 Map으로 변환
  Map<String, dynamic> toFirestore() {
    return {
      'customerId': customerId,
      'type': type.code,
      'amount': amount,
      'balance': balance,
      'description': description,
      if (callId != null) 'callId': callId,
      if (fare != null) 'fare': fare,
      'grade': grade,
      'timestamp': FieldValue.serverTimestamp(),
    };
  }

  /// 거래 생성 팩토리 메서드들

  /// 포인트 적립 거래 생성
  factory PointTransaction.earn({
    required String id,
    required String customerId,
    required int amount,
    required int balance,
    required String callId,
    required int fare,
    required String grade,
  }) {
    return PointTransaction(
      id: id,
      customerId: customerId,
      type: TransactionType.earn,
      amount: amount,
      balance: balance,
      description: '대리운전 이용 적립 (${grade})',
      callId: callId,
      fare: fare,
      grade: grade,
      timestamp: DateTime.now(),
    );
  }

  /// 포인트 사용 거래 생성
  factory PointTransaction.use({
    required String id,
    required String customerId,
    required int amount,
    required int balance,
    String? callId,
    String description = '포인트 사용',
    required String grade,
  }) {
    return PointTransaction(
      id: id,
      customerId: customerId,
      type: TransactionType.use,
      amount: -amount, // 사용은 음수
      balance: balance,
      description: description,
      callId: callId,
      grade: grade,
      timestamp: DateTime.now(),
    );
  }

  /// 포인트 만료 거래 생성
  factory PointTransaction.expire({
    required String id,
    required String customerId,
    required int amount,
    required int balance,
    required String grade,
  }) {
    return PointTransaction(
      id: id,
      customerId: customerId,
      type: TransactionType.expire,
      amount: -amount, // 만료는 음수
      balance: balance,
      description: '포인트 만료',
      grade: grade,
      timestamp: DateTime.now(),
    );
  }

  @override
  String toString() {
    return 'PointTransaction(type: ${type.displayName}, amount: $amount, balance: $balance)';
  }
}
