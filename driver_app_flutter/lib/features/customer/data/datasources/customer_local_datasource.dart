import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../../../../core/error/exceptions.dart';
import '../models/customer_points_model.dart';

/// 고객 포인트 관련 로컬 데이터 소스 (캐싱)
abstract class CustomerLocalDataSource {
  /// 캐시된 고객 포인트 가져오기
  Future<CustomerPointsModel?> getCachedCustomerPoints(String phoneNumber);

  /// 고객 포인트 캐시
  Future<void> cacheCustomerPoints(CustomerPointsModel customerPoints);

  /// 특정 고객 캐시 삭제
  Future<void> deleteCachedCustomerPoints(String phoneNumber);

  /// 전체 캐시 삭제
  Future<void> clearCache();
}

class CustomerLocalDataSourceImpl implements CustomerLocalDataSource {
  final SharedPreferences sharedPreferences;

  static const String _keyPrefix = 'customer_points_';
  static const int _cacheTTL = 300; // 5분 (초)

  CustomerLocalDataSourceImpl({required this.sharedPreferences});

  /// 캐시 키 생성
  String _getCacheKey(String phoneNumber) => '$_keyPrefix$phoneNumber';

  /// 캐시 타임스탬프 키 생성
  String _getTimestampKey(String phoneNumber) =>
      '${_keyPrefix}timestamp_$phoneNumber';

  @override
  Future<CustomerPointsModel?> getCachedCustomerPoints(
      String phoneNumber) async {
    try {
      final cacheKey = _getCacheKey(phoneNumber);
      final timestampKey = _getTimestampKey(phoneNumber);

      // 캐시 타임스탬프 확인
      final timestamp = sharedPreferences.getInt(timestampKey);
      if (timestamp == null) {
        return null;
      }

      // TTL 체크
      final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
      if (now - timestamp > _cacheTTL) {
        // 캐시 만료
        await deleteCachedCustomerPoints(phoneNumber);
        return null;
      }

      // 캐시된 데이터 가져오기
      final jsonString = sharedPreferences.getString(cacheKey);
      if (jsonString == null) {
        return null;
      }

      final jsonMap = json.decode(jsonString) as Map<String, dynamic>;
      return CustomerPointsModel.fromJson(jsonMap);
    } catch (e) {
      throw CacheException('고객 포인트 캐시 로드 실패: $e');
    }
  }

  @override
  Future<void> cacheCustomerPoints(CustomerPointsModel customerPoints) async {
    try {
      final cacheKey = _getCacheKey(customerPoints.phoneNumber);
      final timestampKey = _getTimestampKey(customerPoints.phoneNumber);

      final jsonString = json.encode(customerPoints.toJson());
      final timestamp = DateTime.now().millisecondsSinceEpoch ~/ 1000;

      await sharedPreferences.setString(cacheKey, jsonString);
      await sharedPreferences.setInt(timestampKey, timestamp);
    } catch (e) {
      throw CacheException('고객 포인트 캐시 저장 실패: $e');
    }
  }

  @override
  Future<void> deleteCachedCustomerPoints(String phoneNumber) async {
    try {
      final cacheKey = _getCacheKey(phoneNumber);
      final timestampKey = _getTimestampKey(phoneNumber);

      await sharedPreferences.remove(cacheKey);
      await sharedPreferences.remove(timestampKey);
    } catch (e) {
      throw CacheException('고객 포인트 캐시 삭제 실패: $e');
    }
  }

  @override
  Future<void> clearCache() async {
    try {
      final keys = sharedPreferences
          .getKeys()
          .where((key) => key.startsWith(_keyPrefix))
          .toList();

      for (final key in keys) {
        await sharedPreferences.remove(key);
      }
    } catch (e) {
      throw CacheException('고객 포인트 캐시 전체 삭제 실패: $e');
    }
  }
}
