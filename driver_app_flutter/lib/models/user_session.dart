class UserSession {
  final String userId;
  final String email;
  final String regionId;
  final String officeId;
  final String driverId;
  final String driverName;
  final String? fcmToken;
  final DateTime lastLoginTime;
  final bool isOnline;

  UserSession({
    required this.userId,
    required this.email,
    required this.regionId,
    required this.officeId,
    required this.driverId,
    required this.driverName,
    this.fcmToken,
    DateTime? lastLoginTime,
    this.isOnline = true,
  }) : lastLoginTime = lastLoginTime ?? DateTime.now();

  /// 세션이 만료되었는지 확인 (7일 기준)
  bool isExpired() {
    const sessionValidityMs = 7 * 24 * 60 * 60 * 1000;
    return DateTime.now().millisecondsSinceEpoch - lastLoginTime.millisecondsSinceEpoch > sessionValidityMs;
  }

  /// Map으로 변환 (저장용)
  Map<String, dynamic> toMap() {
    return {
      'userId': userId,
      'email': email,
      'regionId': regionId,
      'officeId': officeId,
      'driverId': driverId,
      'driverName': driverName,
      'fcmToken': fcmToken,
      'lastLoginTime': lastLoginTime.millisecondsSinceEpoch,
      'isOnline': isOnline,
    };
  }

  /// Map에서 복원
  factory UserSession.fromMap(Map<String, dynamic> map) {
    return UserSession(
      userId: map['userId'] as String,
      email: map['email'] as String,
      regionId: map['regionId'] as String,
      officeId: map['officeId'] as String,
      driverId: map['driverId'] as String,
      driverName: map['driverName'] as String? ?? '',
      fcmToken: map['fcmToken'] as String?,
      lastLoginTime: DateTime.fromMillisecondsSinceEpoch(map['lastLoginTime'] as int),
      isOnline: map['isOnline'] as bool? ?? true,
    );
  }

  /// Copy with
  UserSession copyWith({
    String? userId,
    String? email,
    String? regionId,
    String? officeId,
    String? driverId,
    String? driverName,
    String? fcmToken,
    DateTime? lastLoginTime,
    bool? isOnline,
  }) {
    return UserSession(
      userId: userId ?? this.userId,
      email: email ?? this.email,
      regionId: regionId ?? this.regionId,
      officeId: officeId ?? this.officeId,
      driverId: driverId ?? this.driverId,
      driverName: driverName ?? this.driverName,
      fcmToken: fcmToken ?? this.fcmToken,
      lastLoginTime: lastLoginTime ?? this.lastLoginTime,
      isOnline: isOnline ?? this.isOnline,
    );
  }
}
