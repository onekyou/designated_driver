import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:driver_app_flutter/models/user_session.dart';
import 'package:driver_app_flutter/core/constants.dart';

/// 세션 관리 서비스 - 오프라인 지원
/// - 로그인 상태를 로컬에 캐싱
/// - 네트워크 오류 시 캐시된 세션으로 앱 사용 가능
class SessionService {
  static const String _keySession = 'user_session';

  /// 세션 저장
  Future<void> saveSession(UserSession session) async {
    final prefs = await SharedPreferences.getInstance();
    final json = jsonEncode(session.toMap());
    await prefs.setString(_keySession, json);
  }

  /// 세션 복원
  Future<UserSession?> restoreSession() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final json = prefs.getString(_keySession);

      if (json == null) return null;

      final map = jsonDecode(json) as Map<String, dynamic>;
      final session = UserSession.fromMap(map);

      // 만료된 세션은 삭제
      if (session.isExpired()) {
        await clearSession();
        return null;
      }

      return session;
    } catch (e) {
      print('세션 복원 실패: $e');
      await clearSession();
      return null;
    }
  }

  /// 세션 삭제
  Future<void> clearSession() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keySession);
  }

  /// 세션 업데이트
  Future<void> updateSession(UserSession Function(UserSession) updater) async {
    final session = await restoreSession();
    if (session != null) {
      final updated = updater(session);
      await saveSession(updated);
    }
  }

  /// 온라인 상태 업데이트
  Future<void> setOnlineStatus(bool isOnline) async {
    await updateSession((session) => session.copyWith(isOnline: isOnline));
  }

  /// 세션이 유효한지 확인
  Future<bool> isSessionValid() async {
    final session = await restoreSession();
    return session != null && !session.isExpired();
  }

  /// 오프라인 모드에서도 로그인 가능한지 확인
  Future<bool> canLoginOffline() async {
    return await isSessionValid();
  }
}
