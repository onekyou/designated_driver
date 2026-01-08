import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:driver_app_flutter/models/user_session.dart';
import 'package:driver_app_flutter/models/driver_info.dart';
import 'package:driver_app_flutter/services/session_service.dart';
import 'package:driver_app_flutter/core/constants.dart';

class LoginResult {
  final bool success;
  final String? error;
  final UserSession? session;
  final bool needsTokenUpdate;

  LoginResult.success(this.session, {this.needsTokenUpdate = false})
      : success = true,
        error = null;

  LoginResult.error(this.error)
      : success = false,
        session = null,
        needsTokenUpdate = false;
}

class AuthService {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  final SessionService _sessionService = SessionService();

  // Secure Storage for sensitive data (passwords)
  final _secureStorage = const FlutterSecureStorage();

  // Keys for secure storage
  static const String _keyAutoLoginEnabled = 'auto_login_enabled';
  static const String _keyEmail = 'saved_email';
  static const String _keyPassword = 'saved_password';

  /// 자동 로그인 설정 확인
  Future<bool> isAutoLoginEnabled() async {
    final enabled = await _secureStorage.read(key: _keyAutoLoginEnabled);
    return enabled == 'true';
  }

  /// 저장된 로그인 정보 가져오기
  Future<Map<String, String>?> getSavedCredentials() async {
    final email = await _secureStorage.read(key: _keyEmail);
    final password = await _secureStorage.read(key: _keyPassword);

    if (email != null && password != null) {
      return {'email': email, 'password': password};
    }
    return null;
  }

  /// 자동 로그인 정보 저장
  Future<void> saveAutoLoginCredentials(String email, String password) async {
    await _secureStorage.write(key: _keyAutoLoginEnabled, value: 'true');
    await _secureStorage.write(key: _keyEmail, value: email);
    await _secureStorage.write(key: _keyPassword, value: password);
  }

  /// 자동 로그인 정보 삭제
  Future<void> clearAutoLoginCredentials() async {
    await _secureStorage.delete(key: _keyAutoLoginEnabled);
    await _secureStorage.delete(key: _keyEmail);
    await _secureStorage.delete(key: _keyPassword);
  }

  /// 로그인
  Future<LoginResult> login(String email, String password) async {
    try {
      // 1. Firebase 온라인 로그인 시도
      final userCredential = await _auth.signInWithEmailAndPassword(
        email: email,
        password: password,
      );

      final userId = userCredential.user?.uid;
      if (userId == null) {
        return LoginResult.error('로그인 처리 중 오류가 발생했습니다. (UID 누락)');
      }

      // 2. pending_drivers 체크
      final pendingDoc = await _firestore
          .collection(AppConstants.collectionPendingDrivers)
          .doc(userId)
          .get();

      if (pendingDoc.exists) {
        await _auth.signOut();
        return LoginResult.error('관리자 승인 대기 중인 계정입니다.');
      }

      // 3. designated_drivers에서 기사 정보 찾기
      final driversQuery = await _firestore
          .collectionGroup(AppConstants.collectionDrivers)
          .where(AppConstants.fieldAuthUid, isEqualTo: userId)
          .limit(1)
          .get();

      if (driversQuery.docs.isEmpty) {
        await _auth.signOut();
        return LoginResult.error('등록되지 않은 기사 계정입니다.');
      }

      final driverDoc = driversQuery.docs.first;
      final driverData = driverDoc.data();

      final regionId = driverData[AppConstants.fieldRegionId] as String?;
      final officeId = driverData[AppConstants.fieldOfficeId] as String?;
      final approvalStatus = driverData[AppConstants.fieldApprovalStatus] as String?;
      final driverName = driverData[AppConstants.fieldName] as String? ?? '기사님';
      final serverFcmToken = driverData[AppConstants.fieldFcmToken] as String?;

      // 4. 승인 상태 확인
      final approval = DriverApprovalStatus.fromString(approvalStatus);
      if (approval != DriverApprovalStatus.approved) {
        await _auth.signOut();
        return LoginResult.error(approval == DriverApprovalStatus.pending
            ? '관리자 승인 대기 중인 계정입니다.'
            : '가입이 거절된 계정입니다. 관리자에게 문의하세요.');
      }

      if (regionId == null || officeId == null) {
        await _auth.signOut();
        return LoginResult.error('기사 정보(지역/사무실 ID)가 누락되었습니다.');
      }

      // 5. 일반 정보 저장 (SharedPreferences)
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(AppConstants.keyRegionId, regionId);
      await prefs.setString(AppConstants.keyOfficeId, officeId);
      await prefs.setString(AppConstants.keyDriverId, userId);

      // 6. FCM 토큰 확인
      String? localFcmToken;
      try {
        localFcmToken = await _messaging.getToken();
      } catch (e) {
        print('FCM 토큰 가져오기 실패: $e');
      }

      final needsTokenUpdate = serverFcmToken == null ||
                                serverFcmToken.isEmpty ||
                                serverFcmToken != localFcmToken;

      // 7. 세션 저장 (오프라인 로그인 지원)
      final session = UserSession(
        userId: userId,
        email: email,
        regionId: regionId,
        officeId: officeId,
        driverId: userId,
        driverName: driverName,
        fcmToken: localFcmToken,
        lastLoginTime: DateTime.now(),
        isOnline: true,
      );
      await _sessionService.saveSession(session);

      // 8. 기사 상태를 ONLINE으로 업데이트
      final currentStatus = driverData[AppConstants.fieldStatus] as String?;
      if (currentStatus != AppConstants.statusOnline) {
        try {
          await driverDoc.reference.update({
            AppConstants.fieldStatus: AppConstants.statusOnline,
          });
        } catch (e) {
          print('상태 업데이트 실패: $e');
        }
      }

      return LoginResult.success(session, needsTokenUpdate: needsTokenUpdate);

    } on FirebaseAuthException catch (e) {
      // 네트워크 오류인 경우 오프라인 로그인 시도
      if (_isNetworkError(e) && await _sessionService.canLoginOffline()) {
        return await _attemptOfflineLogin(email);
      }
      return LoginResult.error(e.message ?? '로그인에 실패했습니다.');
    } catch (e) {
      // 일반 예외 발생 시 오프라인 로그인 시도
      if (await _sessionService.canLoginOffline()) {
        return await _attemptOfflineLogin(email);
      }
      return LoginResult.error(e.toString());
    }
  }

  /// 네트워크 오류 여부 확인
  bool _isNetworkError(FirebaseAuthException exception) {
    final message = exception.message?.toLowerCase() ?? '';
    return message.contains('network') ||
           message.contains('timeout') ||
           message.contains('unable to resolve host');
  }

  /// 오프라인 로그인 시도
  Future<LoginResult> _attemptOfflineLogin(String email) async {
    final cachedSession = await _sessionService.restoreSession();

    if (cachedSession != null && cachedSession.email == email) {
      await _sessionService.setOnlineStatus(false);
      return LoginResult.success(cachedSession);
    }

    return LoginResult.error('오프라인 상태에서는 이전에 로그인한 계정만 사용할 수 있습니다.');
  }

  /// 로그아웃
  Future<void> logout() async {
    await _auth.signOut();
    await _sessionService.clearSession();
    await clearAutoLoginCredentials();
  }

  /// 현재 로그인한 사용자
  User? get currentUser => _auth.currentUser;

  /// 로그인 상태 스트림
  Stream<User?> get authStateChanges => _auth.authStateChanges();
}
