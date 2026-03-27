import 'package:freezed_annotation/freezed_annotation.dart';

part 'user_session.freezed.dart';

/// 사용자 세션 Entity (순수 Dart, Firebase 독립적)
@freezed
class UserSession with _$UserSession {
  const factory UserSession({
    required String userId,
    required String email,
    required String provinceId,
    required String cityId,
    required String officeId,
    required String driverId,
    required String driverName,
    String? fcmToken,
    required DateTime lastLoginTime,
    @Default(true) bool isOnline,
  }) = _UserSession;

  const UserSession._();

  /// 세션이 만료되었는지 확인 (7일 기준)
  bool isExpired() {
    final sessionValidityMs = 7 * 24 * 60 * 60 * 1000; // 7일
    return DateTime.now().difference(lastLoginTime).inMilliseconds > sessionValidityMs;
  }

  /// 세션이 유효한지 확인
  bool isValid() {
    return !isExpired();
  }
}
