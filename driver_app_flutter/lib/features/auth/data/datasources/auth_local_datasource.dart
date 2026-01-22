import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../../../core/error/exceptions.dart';
import '../models/user_session_model.dart';

/// 인증 관련 로컬 데이터 소스
abstract class AuthLocalDataSource {
  /// 세션 저장
  Future<void> saveSession(UserSessionModel session);

  /// 세션 가져오기
  Future<UserSessionModel?> getSession();

  /// 세션 삭제
  Future<void> clearSession();

  /// 자동 로그인 정보 저장 (암호화)
  Future<void> saveCredentials({
    required String email,
    required String password,
  });

  /// 자동 로그인 정보 가져오기
  Future<Map<String, String>?> getCredentials();

  /// 자동 로그인 정보 삭제
  Future<void> clearCredentials();

  /// 자동 로그인 활성화 여부
  Future<bool> isAutoLoginEnabled();
}

class AuthLocalDataSourceImpl implements AuthLocalDataSource {
  final SharedPreferences sharedPreferences;
  final FlutterSecureStorage secureStorage;

  static const String _keySession = 'user_session';
  static const String _keyAutoLogin = 'auto_login_enabled';
  static const String _keyEmail = 'saved_email';
  static const String _keyPassword = 'saved_password';

  AuthLocalDataSourceImpl({
    required this.sharedPreferences,
    required this.secureStorage,
  });

  @override
  Future<void> saveSession(UserSessionModel session) async {
    try {
      final jsonString = json.encode(session.toJson());
      await sharedPreferences.setString(_keySession, jsonString);
    } catch (e) {
      throw CacheException('세션 저장 실패: $e');
    }
  }

  @override
  Future<UserSessionModel?> getSession() async {
    try {
      final jsonString = sharedPreferences.getString(_keySession);
      if (jsonString == null) return null;

      final jsonMap = json.decode(jsonString) as Map<String, dynamic>;
      return UserSessionModel.fromJson(jsonMap);
    } catch (e) {
      throw CacheException('세션 로드 실패: $e');
    }
  }

  @override
  Future<void> clearSession() async {
    try {
      await sharedPreferences.remove(_keySession);
    } catch (e) {
      throw CacheException('세션 삭제 실패: $e');
    }
  }

  @override
  Future<void> saveCredentials({
    required String email,
    required String password,
  }) async {
    try {
      // 암호화된 저장소에 저장
      await secureStorage.write(key: _keyAutoLogin, value: 'true');
      await secureStorage.write(key: _keyEmail, value: email);
      await secureStorage.write(key: _keyPassword, value: password);
    } catch (e) {
      throw CacheException('자동 로그인 정보 저장 실패: $e');
    }
  }

  @override
  Future<Map<String, String>?> getCredentials() async {
    try {
      final email = await secureStorage.read(key: _keyEmail);
      final password = await secureStorage.read(key: _keyPassword);

      if (email != null && password != null) {
        return {'email': email, 'password': password};
      }
      return null;
    } catch (e) {
      throw CacheException('자동 로그인 정보 로드 실패: $e');
    }
  }

  @override
  Future<void> clearCredentials() async {
    try {
      await secureStorage.delete(key: _keyAutoLogin);
      await secureStorage.delete(key: _keyEmail);
      await secureStorage.delete(key: _keyPassword);
    } catch (e) {
      throw CacheException('자동 로그인 정보 삭제 실패: $e');
    }
  }

  @override
  Future<bool> isAutoLoginEnabled() async {
    try {
      final enabled = await secureStorage.read(key: _keyAutoLogin);
      return enabled == 'true';
    } catch (e) {
      return false;
    }
  }
}
