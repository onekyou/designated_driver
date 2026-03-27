import 'package:cloud_firestore/cloud_firestore.dart';
import '../../domain/entities/user_session.dart';

/// UserSession Model (Entity ↔ JSON 변환)
class UserSessionModel {
  final String userId;
  final String email;
  final String provinceId;
  final String cityId;
  final String officeId;
  final String driverId;
  final String driverName;
  final String? fcmToken;
  final DateTime lastLoginTime;
  final bool isOnline;

  const UserSessionModel({
    required this.userId,
    required this.email,
    required this.provinceId,
    required this.cityId,
    required this.officeId,
    required this.driverId,
    required this.driverName,
    this.fcmToken,
    required this.lastLoginTime,
    this.isOnline = true,
  });

  /// Entity에서 Model로 변환
  factory UserSessionModel.fromEntity(UserSession session) {
    return UserSessionModel(
      userId: session.userId,
      email: session.email,
      provinceId: session.provinceId,
      cityId: session.cityId,
      officeId: session.officeId,
      driverId: session.driverId,
      driverName: session.driverName,
      fcmToken: session.fcmToken,
      lastLoginTime: session.lastLoginTime,
      isOnline: session.isOnline,
    );
  }

  /// JSON에서 Model로 변환
  factory UserSessionModel.fromJson(Map<String, dynamic> json) {
    return UserSessionModel(
      userId: json['userId'] as String,
      email: json['email'] as String,
      provinceId: json['provinceId'] as String,
      cityId: json['cityId'] as String,
      officeId: json['officeId'] as String,
      driverId: json['driverId'] as String,
      driverName: json['driverName'] as String,
      fcmToken: json['fcmToken'] as String?,
      lastLoginTime: json['lastLoginTime'] is Timestamp
          ? (json['lastLoginTime'] as Timestamp).toDate()
          : DateTime.fromMillisecondsSinceEpoch(json['lastLoginTime'] as int),
      isOnline: json['isOnline'] as bool? ?? true,
    );
  }

  /// Model을 JSON으로 변환
  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'email': email,
      'provinceId': provinceId,
      'cityId': cityId,
      'officeId': officeId,
      'driverId': driverId,
      'driverName': driverName,
      'fcmToken': fcmToken,
      'lastLoginTime': lastLoginTime.millisecondsSinceEpoch,
      'isOnline': isOnline,
    };
  }

  /// Model을 Entity로 변환
  UserSession toEntity() {
    return UserSession(
      userId: userId,
      email: email,
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
      driverId: driverId,
      driverName: driverName,
      fcmToken: fcmToken,
      lastLoginTime: lastLoginTime,
      isOnline: isOnline,
    );
  }

  /// copyWith
  UserSessionModel copyWith({
    String? userId,
    String? email,
    String? provinceId,
    String? cityId,
    String? officeId,
    String? driverId,
    String? driverName,
    String? fcmToken,
    DateTime? lastLoginTime,
    bool? isOnline,
  }) {
    return UserSessionModel(
      userId: userId ?? this.userId,
      email: email ?? this.email,
      provinceId: provinceId ?? this.provinceId,
      cityId: cityId ?? this.cityId,
      officeId: officeId ?? this.officeId,
      driverId: driverId ?? this.driverId,
      driverName: driverName ?? this.driverName,
      fcmToken: fcmToken ?? this.fcmToken,
      lastLoginTime: lastLoginTime ?? this.lastLoginTime,
      isOnline: isOnline ?? this.isOnline,
    );
  }

  /// 세션 만료 여부 확인 (7일)
  bool isExpired() {
    const sessionValidityMs = 7 * 24 * 60 * 60 * 1000;
    return DateTime.now().difference(lastLoginTime).inMilliseconds > sessionValidityMs;
  }
}
