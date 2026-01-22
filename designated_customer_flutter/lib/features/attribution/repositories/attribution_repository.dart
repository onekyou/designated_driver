import 'package:cloud_functions/cloud_functions.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../core/exceptions/app_exceptions.dart';
import '../../../core/services/storage_service.dart';
import '../../../core/utils/device_fingerprint.dart';
import '../../../core/constants/app_constants.dart';
import '../models/attribution_result.dart';

/// Attribution Repository
class AttributionRepository {
  final FirebaseFunctions _functions = FirebaseFunctions.instanceFor(
    region: 'asia-northeast3',
  );
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final StorageService _storage = StorageService();

  static const platform = MethodChannel('com.designated.customer/screen_resolution');

  /// Attribution 매칭
  /// 1. 파라미터 토큰으로 매칭 시도
  /// 2. 캐시된 토큰으로 매칭 시도
  /// 3. Firestore에서 화면 해상도로 attribution 검색 → token 추출
  /// 4. Device Fingerprint 기반 매칭
  Future<AttributionResult> matchAttribution({String? token}) async {
    try {
      // 1. 파라미터 토큰 기반 매칭 시도
      if (token != null && token.isNotEmpty) {
        final tokenResult = await _matchByToken(token);
        if (tokenResult != null) {
          await _saveAttributionData(tokenResult);
          return tokenResult;
        }
      }

      // 2. 캐시된 토큰으로 매칭 시도
      final cachedToken = await _getCachedToken();
      if (cachedToken != null) {
        final cachedResult = await _matchByToken(cachedToken);
        if (cachedResult != null) {
          await _saveAttributionData(cachedResult);
          return cachedResult;
        }
        // 토큰이 만료되었거나 유효하지 않으면 삭제
        await _storage.deleteSecure(AppConstants.keyLastAttributionToken);
      }

      // 3. Firestore에서 화면 해상도로 attribution 검색 → token 추출
      final firestoreToken = await _getAttributionTokenFromFirestore();
      if (firestoreToken != null) {
        final firestoreResult = await _matchByToken(firestoreToken);
        if (firestoreResult != null) {
          // 토큰 캐시에 저장
          await _storage.writeSecure(
            AppConstants.keyLastAttributionToken,
            firestoreToken,
          );
          await _storage.setInt(
            '${AppConstants.keyLastAttributionToken}_timestamp',
            DateTime.now().millisecondsSinceEpoch,
          );
          await _saveAttributionData(firestoreResult);
          return firestoreResult;
        }
      }

      // 4. Device Fingerprint 기반 매칭
      final fingerprintResult = await _matchByFingerprint();
      await _saveAttributionData(fingerprintResult);
      return fingerprintResult;
    } catch (e) {
      if (e is AttributionException) {
        rethrow;
      }
      throw AttributionException('Attribution 매칭 중 오류가 발생했습니다: $e');
    }
  }

  /// 토큰 기반 매칭
  Future<AttributionResult?> _matchByToken(String token) async {
    try {
      final result = await _functions.httpsCallable('matchByToken').call({
        'token': token,
      });

      final data = result.data as Map<String, dynamic>;

      if (data['success'] == true) {
        // matchByToken은 attribution 객체가 아니라 직접 필드를 반환
        return AttributionResult(
          regionId: data['regionId'] as String,
          officeId: data['officeId'] as String,
          officeName: data['officeName'] as String? ?? 'Unknown',
          officePhone: data['officePhone'] as String? ?? '',
          bankName: data['bankName'] as String?,
          accountNumber: data['accountNumber'] as String?,
          accountHolder: data['accountHolder'] as String?,
          referralDriverId: data['referralDriverId'] as String?,
          referralDriverName: data['referralDriverName'] as String?,
          token: token,
        );
      }

      return null;
    } on FirebaseFunctionsException catch (e) {
      // 토큰이 유효하지 않거나 만료된 경우 null 반환
      if (e.code == 'not-found' || e.code == 'invalid-argument') {
        return null;
      }
      throw AttributionException(
        'Token 매칭 실패: ${e.message}',
        code: e.code,
      );
    } catch (e) {
      return null;
    }
  }

  /// Device Fingerprint 기반 매칭
  Future<AttributionResult> _matchByFingerprint() async {
    try {
      // Device Fingerprint 수집
      final fingerprint = await DeviceFingerprint.generate();
      final deviceId = await DeviceFingerprint.getDeviceId();

      // Cloud Function 호출
      final result = await _functions.httpsCallable('matchAttribution').call({
        'fingerprint': fingerprint,
        'deviceId': deviceId,
        'timestamp': DateTime.now().millisecondsSinceEpoch,
      });

      final data = result.data as Map<String, dynamic>;

      if (data['success'] != true) {
        throw AttributionException(
          data['message'] ?? 'Attribution 매칭에 실패했습니다',
        );
      }

      if (data['attribution'] == null) {
        throw AttributionException('매칭된 사무실 정보가 없습니다');
      }

      return AttributionResult.fromJson(data['attribution']);
    } on FirebaseFunctionsException catch (e) {
      throw AttributionException(
        _getFunctionsErrorMessage(e.code, e.message),
        code: e.code,
      );
    } catch (e) {
      if (e is AttributionException) {
        rethrow;
      }
      throw AttributionException('Fingerprint 매칭 중 오류가 발생했습니다: $e');
    }
  }

  /// Attribution 데이터 저장
  Future<void> _saveAttributionData(AttributionResult result) async {
    try {
      // 보안 저장소에 저장
      await _storage.writeSecure('regionId', result.regionId);
      await _storage.writeSecure('officeId', result.officeId);
      await _storage.writeSecure(AppConstants.keyOfficeName, result.officeName);
      await _storage.writeSecure(AppConstants.keyOfficePhone, result.officePhone);

      // 토큰이 있으면 저장 (24시간 만료)
      if (result.token != null) {
        await _storage.writeSecure(
          AppConstants.keyLastAttributionToken,
          result.token!,
        );
        await _storage.setInt(
          '${AppConstants.keyLastAttributionToken}_timestamp',
          DateTime.now().millisecondsSinceEpoch,
        );
      }

      // 추천인 정보 저장
      if (result.referralDriverId != null) {
        await _storage.setString('referralDriverId', result.referralDriverId!);
      }
      if (result.referralDriverName != null) {
        await _storage.setString('referralDriverName', result.referralDriverName!);
      }

      // 계좌 정보 저장
      if (result.bankName != null) {
        await _storage.writeSecure('bankName', result.bankName!);
      }
      if (result.accountNumber != null) {
        await _storage.writeSecure('accountNumber', result.accountNumber!);
      }
      if (result.accountHolder != null) {
        await _storage.writeSecure('accountHolder', result.accountHolder!);
      }
    } catch (e) {
      throw AttributionException('Attribution 데이터 저장 중 오류가 발생했습니다: $e');
    }
  }

  /// Firestore에서 화면 해상도로 attribution 검색 → token 추출
  /// Android 앱의 getAttributionToken() 로직과 동일
  Future<String?> _getAttributionTokenFromFirestore() async {
    try {
      // 화면 해상도 가져오기 (Android DisplayMetrics 사용 - Native 앱 및 랜딩 페이지와 동일)
      String screenResolution;
      try {
        final String result = await platform.invokeMethod('getScreenResolution');
        screenResolution = result;
      } catch (e) {
        // 폴백: Flutter 기본 방식 (iOS 또는 Platform Channel 실패 시)
        final view = WidgetsBinding.instance.platformDispatcher.views.first;
        final width = view.physicalSize.width.round();
        final height = view.physicalSize.height.round();
        screenResolution = '${width}x$height';
        debugPrint('[Attribution] Platform Channel 실패, 폴백 사용: $e');
      }

      debugPrint('[Attribution] 화면 해상도로 토큰 검색 시작: $screenResolution');

      // 24시간 이내 attribution만 검색
      final oneDayAgo = DateTime.now().subtract(const Duration(hours: 24));

      // 모든 지역/사무실을 순회하며 매칭되는 attribution 찾기
      final regionsSnapshot = await _firestore.collection('regions').get();

      for (final regionDoc in regionsSnapshot.docs) {
        final regionId = regionDoc.id;
        final officesSnapshot = await _firestore
            .collection('regions')
            .doc(regionId)
            .collection('offices')
            .get();

        for (final officeDoc in officesSnapshot.docs) {
          final officeId = officeDoc.id;

          // screenResolution으로 attribution 검색
          final attributionQuery = await _firestore
              .collection('regions')
              .doc(regionId)
              .collection('offices')
              .doc(officeId)
              .collection('attributions')
              .where('screenResolution', isEqualTo: screenResolution)
              .where('createdAt', isGreaterThan: Timestamp.fromDate(oneDayAgo))
              .orderBy('createdAt', descending: true)
              .limit(1)
              .get();

          if (attributionQuery.docs.isNotEmpty) {
            final attribution = attributionQuery.docs.first;
            final token = attribution.data()['token'] as String?;

            if (token != null && token.isNotEmpty) {
              debugPrint('[Attribution] 토큰 발견: $token (해상도: $screenResolution)');
              return token;
            }
          }
        }
      }

      debugPrint('[Attribution] 토큰을 찾지 못함 (해상도: $screenResolution)');
      return null;
    } catch (e) {
      debugPrint('[Attribution] Firestore 토큰 조회 실패: $e');
      return null;
    }
  }

  /// 캐시된 토큰 가져오기 (24시간 이내)
  Future<String?> _getCachedToken() async {
    try {
      final token = await _storage.readSecure(AppConstants.keyLastAttributionToken);
      if (token == null) return null;

      final timestamp = _storage.getInt('${AppConstants.keyLastAttributionToken}_timestamp');
      if (timestamp == null) return null;

      final now = DateTime.now().millisecondsSinceEpoch;
      final expiryDuration = AppConstants.attributionExpiryHours * 60 * 60 * 1000;

      // 24시간 이내인지 확인
      if (now - timestamp < expiryDuration) {
        return token;
      }

      // 만료된 토큰 삭제
      await _storage.deleteSecure(AppConstants.keyLastAttributionToken);
      await _storage.remove('${AppConstants.keyLastAttributionToken}_timestamp');

      return null;
    } catch (e) {
      return null;
    }
  }

  /// 저장된 Attribution 정보 가져오기
  Future<AttributionResult?> getSavedAttribution() async {
    try {
      final regionId = await _storage.readSecure('regionId');
      final officeId = await _storage.readSecure('officeId');
      final officeName = await _storage.readSecure(AppConstants.keyOfficeName);
      final officePhone = await _storage.readSecure(AppConstants.keyOfficePhone);

      if (regionId == null || officeId == null) {
        return null;
      }

      return AttributionResult(
        regionId: regionId,
        officeId: officeId,
        officeName: officeName ?? AppConstants.defaultOfficeName,
        officePhone: officePhone ?? AppConstants.defaultOfficePhone,
        bankName: await _storage.readSecure('bankName'),
        accountNumber: await _storage.readSecure('accountNumber'),
        accountHolder: await _storage.readSecure('accountHolder'),
        referralDriverId: _storage.getString('referralDriverId'),
        referralDriverName: _storage.getString('referralDriverName'),
      );
    } catch (e) {
      return null;
    }
  }

  /// Firebase Functions 에러 메시지 변환
  String _getFunctionsErrorMessage(String code, String? message) {
    switch (code) {
      case 'unauthenticated':
        return '인증이 필요합니다';
      case 'permission-denied':
        return '권한이 없습니다';
      case 'not-found':
        return '매칭된 사무실을 찾을 수 없습니다';
      case 'unavailable':
        return '서버에 연결할 수 없습니다. 네트워크를 확인해주세요';
      case 'deadline-exceeded':
        return '요청 시간이 초과되었습니다. 다시 시도해주세요';
      default:
        return message ?? 'Cloud Function 호출 중 오류가 발생했습니다 (code: $code)';
    }
  }

  /// Attribution 정보 삭제
  Future<void> clearAttribution() async {
    try {
      await _storage.deleteSecure('regionId');
      await _storage.deleteSecure('officeId');
      await _storage.deleteSecure(AppConstants.keyOfficeName);
      await _storage.deleteSecure(AppConstants.keyOfficePhone);
      await _storage.deleteSecure(AppConstants.keyLastAttributionToken);
      await _storage.remove('${AppConstants.keyLastAttributionToken}_timestamp');
      await _storage.remove('referralDriverId');
      await _storage.remove('referralDriverName');
      await _storage.deleteSecure('bankName');
      await _storage.deleteSecure('accountNumber');
      await _storage.deleteSecure('accountHolder');
    } catch (e) {
      throw AttributionException('Attribution 정보 삭제 중 오류가 발생했습니다: $e');
    }
  }
}
