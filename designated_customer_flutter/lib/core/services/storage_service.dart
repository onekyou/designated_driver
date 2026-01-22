import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// 로컬 저장소 서비스
/// - 보안이 필요한 데이터: FlutterSecureStorage
/// - 일반 데이터: SharedPreferences
class StorageService {
  static final StorageService _instance = StorageService._internal();
  factory StorageService() => _instance;
  StorageService._internal();

  // Secure Storage (암호화된 저장소)
  final _secureStorage = const FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
  );

  // Shared Preferences (일반 저장소)
  SharedPreferences? _prefs;

  /// SharedPreferences 초기화
  Future<void> init() async {
    _prefs ??= await SharedPreferences.getInstance();
  }

  // ========== Secure Storage Methods ==========

  /// 보안 데이터 저장
  Future<void> writeSecure(String key, String value) async {
    try {
      await _secureStorage.write(key: key, value: value);
    } catch (e) {
      throw Exception('Secure 저장 실패: $e');
    }
  }

  /// 보안 데이터 읽기
  Future<String?> readSecure(String key) async {
    try {
      return await _secureStorage.read(key: key);
    } catch (e) {
      throw Exception('Secure 읽기 실패: $e');
    }
  }

  /// 보안 데이터 삭제
  Future<void> deleteSecure(String key) async {
    try {
      await _secureStorage.delete(key: key);
    } catch (e) {
      throw Exception('Secure 삭제 실패: $e');
    }
  }

  /// 모든 보안 데이터 삭제
  Future<void> deleteAllSecure() async {
    try {
      await _secureStorage.deleteAll();
    } catch (e) {
      throw Exception('Secure 전체 삭제 실패: $e');
    }
  }

  // ========== SharedPreferences Methods ==========

  /// 문자열 저장
  Future<bool> setString(String key, String value) async {
    await init();
    return _prefs!.setString(key, value);
  }

  /// 문자열 읽기
  String? getString(String key) {
    return _prefs?.getString(key);
  }

  /// 정수 저장
  Future<bool> setInt(String key, int value) async {
    await init();
    return _prefs!.setInt(key, value);
  }

  /// 정수 읽기
  int? getInt(String key) {
    return _prefs?.getInt(key);
  }

  /// 불린 저장
  Future<bool> setBool(String key, bool value) async {
    await init();
    return _prefs!.setBool(key, value);
  }

  /// 불린 읽기
  bool? getBool(String key) {
    return _prefs?.getBool(key);
  }

  /// 더블 저장
  Future<bool> setDouble(String key, double value) async {
    await init();
    return _prefs!.setDouble(key, value);
  }

  /// 더블 읽기
  double? getDouble(String key) {
    return _prefs?.getDouble(key);
  }

  /// 문자열 리스트 저장
  Future<bool> setStringList(String key, List<String> value) async {
    await init();
    return _prefs!.setStringList(key, value);
  }

  /// 문자열 리스트 읽기
  List<String>? getStringList(String key) {
    return _prefs?.getStringList(key);
  }

  /// 특정 키 삭제
  Future<bool> remove(String key) async {
    await init();
    return _prefs!.remove(key);
  }

  /// 모든 데이터 삭제
  Future<bool> clear() async {
    await init();
    return _prefs!.clear();
  }

  /// 키 존재 여부 확인
  bool containsKey(String key) {
    return _prefs?.containsKey(key) ?? false;
  }

  /// 모든 키 가져오기
  Set<String> getKeys() {
    return _prefs?.getKeys() ?? {};
  }
}
