import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import '../../../core/exceptions/app_exceptions.dart';
import '../../../core/services/storage_service.dart';
import '../../../core/utils/device_fingerprint.dart';
import '../models/user_model.dart';

/// 인증 Repository
class AuthRepository {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final StorageService _storage = StorageService();

  /// 현재 사용자 스트림
  Stream<User?> get authStateChanges => _auth.authStateChanges();

  /// 현재 사용자
  User? get currentUser => _auth.currentUser;

  /// 익명 로그인 (전화번호와 함께)
  Future<UserModel> signInAnonymously({String? phoneNumber}) async {
    try {
      // Firebase 익명 인증
      final userCredential = await _auth.signInAnonymously();
      final user = userCredential.user;

      if (user == null) {
        throw AuthException('익명 로그인에 실패했습니다', code: 'user_null');
      }

      // Device Fingerprint 생성
      final fingerprint = await DeviceFingerprint.generate();
      final deviceId = await DeviceFingerprint.getDeviceId();

      // Firestore에 사용자 정보 저장
      final now = DateTime.now();
      final userData = UserModel(
        uid: user.uid,
        isAnonymous: true,
        phoneNumber: phoneNumber, // 전화번호 포함
        createdAt: now,
        updatedAt: now,
      );

      // customers 컬렉션에 저장
      await _firestore.collection('customers').doc(user.uid).set({
        ...userData.toFirestore(),
        'fingerprint': fingerprint,
        'deviceId': deviceId,
        'lastLoginAt': Timestamp.fromDate(now),
      }, SetOptions(merge: true));

      // 로컬 저장소에 저장
      await _storage.writeSecure('uid', user.uid);
      await _storage.writeSecure('fingerprint', fingerprint);
      await _storage.setString('deviceId', deviceId);

      // 전화번호도 암호화하여 저장
      if (phoneNumber != null) {
        await _storage.writeSecure('phoneNumber', phoneNumber);
      }

      return userData;
    } on FirebaseAuthException catch (e) {
      throw AuthException(
        _getAuthErrorMessage(e.code),
        code: e.code,
      );
    } catch (e) {
      throw AuthException('인증 중 오류가 발생했습니다: $e');
    }
  }

  /// 로그아웃
  Future<void> signOut() async {
    try {
      await _auth.signOut();
      // 로컬 저장소에서 인증 정보 삭제 (사무실 정보는 유지)
      await _storage.deleteSecure('uid');
    } catch (e) {
      throw AuthException('로그아웃 중 오류가 발생했습니다: $e');
    }
  }

  /// 사용자 정보 가져오기
  Future<UserModel?> getUserData(String uid) async {
    try {
      final doc = await _firestore.collection('customers').doc(uid).get();

      if (!doc.exists) {
        return null;
      }

      return UserModel.fromFirestore(doc.data()!, uid);
    } catch (e) {
      throw AuthException('사용자 정보를 가져오는 중 오류가 발생했습니다: $e');
    }
  }

  /// 사용자 정보 업데이트
  Future<void> updateUserData(String uid, Map<String, dynamic> data) async {
    try {
      await _firestore.collection('customers').doc(uid).update({
        ...data,
        'updatedAt': FieldValue.serverTimestamp(),
      });
    } catch (e) {
      throw AuthException('사용자 정보 업데이트 중 오류가 발생했습니다: $e');
    }
  }

  /// Firebase Auth 에러 메시지 변환
  String _getAuthErrorMessage(String code) {
    switch (code) {
      case 'network-request-failed':
        return '네트워크 연결을 확인해주세요';
      case 'too-many-requests':
        return '너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요';
      case 'operation-not-allowed':
        return '익명 로그인이 비활성화되어 있습니다';
      default:
        return '인증 중 오류가 발생했습니다 (code: $code)';
    }
  }

  /// 저장된 UID 확인
  Future<String?> getSavedUid() async {
    try {
      return await _storage.readSecure('uid');
    } catch (e) {
      return null;
    }
  }

  /// 저장된 전화번호 확인
  Future<String?> getSavedPhoneNumber() async {
    try {
      return await _storage.readSecure('phoneNumber');
    } catch (e) {
      return null;
    }
  }

  /// 현재 사용자가 인증되었는지 확인
  bool isAuthenticated() {
    return _auth.currentUser != null;
  }
}
