import 'package:cloud_firestore/cloud_firestore.dart';

/// 사용자 모델
class UserModel {
  final String uid;
  final bool isAnonymous;
  final String? phoneNumber;
  final String? email;
  final DateTime createdAt;
  final DateTime updatedAt;

  UserModel({
    required this.uid,
    required this.isAnonymous,
    this.phoneNumber,
    this.email,
    required this.createdAt,
    required this.updatedAt,
  });

  /// Firestore에서 변환
  factory UserModel.fromFirestore(Map<String, dynamic> data, String uid) {
    return UserModel(
      uid: uid,
      isAnonymous: data['isAnonymous'] ?? true,
      phoneNumber: data['phoneNumber'],
      email: data['email'],
      createdAt: (data['createdAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
      updatedAt: (data['updatedAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
    );
  }

  /// Firestore로 변환
  Map<String, dynamic> toFirestore() {
    return {
      'isAnonymous': isAnonymous,
      'phoneNumber': phoneNumber,
      'email': email,
      'createdAt': Timestamp.fromDate(createdAt),
      'updatedAt': Timestamp.fromDate(updatedAt),
    };
  }

  /// copyWith
  UserModel copyWith({
    String? uid,
    bool? isAnonymous,
    String? phoneNumber,
    String? email,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return UserModel(
      uid: uid ?? this.uid,
      isAnonymous: isAnonymous ?? this.isAnonymous,
      phoneNumber: phoneNumber ?? this.phoneNumber,
      email: email ?? this.email,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}

/// 인증 상태
enum AuthState {
  initial,       // 초기 상태
  loading,       // 로딩 중
  authenticated, // 인증됨
  unauthenticated, // 미인증
  error,         // 에러
}
