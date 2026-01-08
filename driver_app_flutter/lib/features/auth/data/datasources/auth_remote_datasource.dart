import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import '../../../../core/constants/app_constants.dart';
import '../../../../core/error/exceptions.dart';
import '../models/user_session_model.dart';

/// 인증 관련 원격 데이터 소스 (Firebase)
abstract class AuthRemoteDataSource {
  /// Firebase 로그인
  Future<UserSessionModel> login({
    required String email,
    required String password,
  });

  /// Firebase 로그아웃
  Future<void> logout();

  /// 현재 Firebase 사용자
  User? getCurrentUser();

  /// FCM 토큰 업데이트
  Future<void> updateFcmToken({
    required String driverId,
    required String regionId,
    required String officeId,
    required String token,
  });

  /// 비밀번호 재설정
  Future<void> resetPassword(String email);
}

class AuthRemoteDataSourceImpl implements AuthRemoteDataSource {
  final FirebaseAuth firebaseAuth;
  final FirebaseFirestore firestore;
  final FirebaseMessaging firebaseMessaging;

  AuthRemoteDataSourceImpl({
    required this.firebaseAuth,
    required this.firestore,
    required this.firebaseMessaging,
  });

  @override
  Future<UserSessionModel> login({
    required String email,
    required String password,
  }) async {
    try {
      // 1. Firebase 인증
      final userCredential = await firebaseAuth.signInWithEmailAndPassword(
        email: email,
        password: password,
      );

      final userId = userCredential.user?.uid;
      if (userId == null) {
        throw AuthException('사용자 ID를 가져올 수 없습니다.');
      }

      // 2. pending_drivers 체크
      final pendingDoc = await firestore
          .collection(AppConstants.collectionPendingDrivers)
          .doc(userId)
          .get();

      if (pendingDoc.exists) {
        await firebaseAuth.signOut();
        throw AuthException('관리자 승인 대기 중인 계정입니다.');
      }

      // 3. designated_drivers에서 기사 정보 찾기
      final driversQuery = await firestore
          .collectionGroup(AppConstants.collectionDrivers)
          .where(AppConstants.fieldAuthUid, isEqualTo: userId)
          .limit(1)
          .get();

      if (driversQuery.docs.isEmpty) {
        await firebaseAuth.signOut();
        throw AuthException('등록되지 않은 기사 계정입니다.');
      }

      final driverDoc = driversQuery.docs.first;
      final driverData = driverDoc.data();

      // 4. 승인 상태 확인
      final approvalStatus = driverData[AppConstants.fieldApprovalStatus] as String?;
      if (approvalStatus != AppConstants.approvalApproved) {
        await firebaseAuth.signOut();
        if (approvalStatus == AppConstants.approvalPending) {
          throw AuthException('관리자 승인 대기 중인 계정입니다.');
        } else {
          throw AuthException('가입이 거절된 계정입니다. 관리자에게 문의하세요.');
        }
      }

      // 5. 기사 정보 추출
      final regionId = driverData[AppConstants.fieldRegionId] as String?;
      final officeId = driverData[AppConstants.fieldOfficeId] as String?;
      final driverName = driverData[AppConstants.fieldName] as String? ?? '기사님';

      if (regionId == null || officeId == null) {
        await firebaseAuth.signOut();
        throw AuthException('기사 정보(지역/사무실)가 누락되었습니다.');
      }

      // 6. FCM 토큰 가져오기
      String? fcmToken;
      try {
        fcmToken = await firebaseMessaging.getToken();
      } catch (e) {
        // FCM 토큰 실패해도 로그인은 진행
      }

      // 7. 기사 상태를 ONLINE으로 업데이트
      try {
        await driverDoc.reference.update({
          AppConstants.fieldStatus: AppConstants.statusOnline,
        });
      } catch (e) {
        // 상태 업데이트 실패해도 로그인은 진행
      }

      // 8. UserSessionModel 생성
      return UserSessionModel(
        userId: userId,
        email: email,
        regionId: regionId,
        officeId: officeId,
        driverId: userId,
        driverName: driverName,
        fcmToken: fcmToken,
        lastLoginTime: DateTime.now(),
        isOnline: true,
      );
    } on FirebaseAuthException catch (e) {
      throw AuthException(e.message ?? '로그인에 실패했습니다.');
    } catch (e) {
      if (e is AuthException) rethrow;
      throw AuthException('로그인 중 오류 발생: $e');
    }
  }

  @override
  Future<void> logout() async {
    try {
      await firebaseAuth.signOut();
    } on FirebaseAuthException catch (e) {
      throw AuthException(e.message ?? '로그아웃에 실패했습니다.');
    }
  }

  @override
  User? getCurrentUser() {
    return firebaseAuth.currentUser;
  }

  @override
  Future<void> updateFcmToken({
    required String driverId,
    required String regionId,
    required String officeId,
    required String token,
  }) async {
    try {
      await firestore
          .collection(AppConstants.collectionRegions)
          .doc(regionId)
          .collection(AppConstants.collectionOffices)
          .doc(officeId)
          .collection(AppConstants.collectionDrivers)
          .doc(driverId)
          .update({
        AppConstants.fieldFcmToken: token,
      });
    } on FirebaseException catch (e) {
      throw ServerException(e.message ?? 'FCM 토큰 업데이트 실패');
    }
  }

  @override
  Future<void> resetPassword(String email) async {
    try {
      await firebaseAuth.sendPasswordResetEmail(email: email);
    } on FirebaseAuthException catch (e) {
      throw AuthException(e.message ?? '비밀번호 재설정 이메일 전송 실패');
    }
  }
}
