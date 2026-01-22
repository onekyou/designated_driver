import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// FCM 푸시 알림 서비스
///
/// 주요 기능:
/// - FCM 토큰 관리 및 Firestore 저장
/// - Foreground/Background 알림 처리
/// - 알림 타입별 분기 처리 (DRIVER_ASSIGNED, RIDE_COMPLETED, CALL_CANCELLED)
class FcmService {
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  /// 알림 타입
  static const String notificationTypeDriverAssigned = 'DRIVER_ASSIGNED';
  static const String notificationTypeRideCompleted = 'RIDE_COMPLETED';
  static const String notificationTypeCallCancelled = 'CALL_CANCELLED';

  /// 알림 메시지 스트림 (Foreground용)
  Stream<RemoteMessage> get onMessageStream => FirebaseMessaging.onMessage;

  /// 알림 클릭 스트림 (Background → Foreground 전환 시)
  Stream<RemoteMessage> get onMessageOpenedAppStream =>
      FirebaseMessaging.onMessageOpenedApp;

  /// FCM 서비스 초기화
  ///
  /// 호출 시점: main.dart에서 Firebase.initializeApp() 후
  Future<void> initialize({
    required String regionId,
    required String officeId,
    required String phoneNumber,
  }) async {
    try {
      debugPrint('[FCM] 초기화 시작');

      // 1. 알림 권한 요청
      final NotificationSettings settings = await _requestPermission();
      debugPrint('[FCM] 권한 상태: ${settings.authorizationStatus}');

      if (settings.authorizationStatus == AuthorizationStatus.denied) {
        debugPrint('[FCM] 알림 권한이 거부되었습니다.');
        return;
      }

      // 2. FCM 토큰 가져오기
      final String? token = await _messaging.getToken();
      if (token == null) {
        debugPrint('[FCM] 토큰을 가져올 수 없습니다.');
        return;
      }
      debugPrint('[FCM] 토큰 발급: ${token.substring(0, 20)}...');

      // 3. Firestore에 FCM 토큰 저장
      await _saveFcmToken(
        regionId: regionId,
        officeId: officeId,
        phoneNumber: phoneNumber,
        token: token,
      );

      // 4. 토큰 갱신 리스너 등록
      _messaging.onTokenRefresh.listen((newToken) {
        debugPrint('[FCM] 토큰 갱신: ${newToken.substring(0, 20)}...');
        _saveFcmToken(
          regionId: regionId,
          officeId: officeId,
          phoneNumber: phoneNumber,
          token: newToken,
        );
      });

      // 5. Background 메시지 핸들러 등록 (static 함수)
      FirebaseMessaging.onBackgroundMessage(_firebaseMessagingBackgroundHandler);

      debugPrint('[FCM] 초기화 완료');
    } catch (e) {
      debugPrint('[FCM] 초기화 실패: $e');
    }
  }

  /// 알림 권한 요청
  Future<NotificationSettings> _requestPermission() async {
    return await _messaging.requestPermission(
      alert: true,
      announcement: false,
      badge: true,
      carPlay: false,
      criticalAlert: false,
      provisional: false,
      sound: true,
    );
  }

  /// FCM 토큰을 Firestore에 저장
  ///
  /// 저장 위치: regions/{regionId}/offices/{officeId}/customerInfo/{phoneNumber}
  Future<void> _saveFcmToken({
    required String regionId,
    required String officeId,
    required String phoneNumber,
    required String token,
  }) async {
    try {
      final docRef = _firestore
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('customerInfo')
          .doc(phoneNumber);

      await docRef.set({
        'fcmToken': token,
        'platform': defaultTargetPlatform.name, // android, iOS
        'updatedAt': FieldValue.serverTimestamp(),
      }, SetOptions(merge: true));

      debugPrint('[FCM] 토큰 저장 완료: $phoneNumber');
    } catch (e) {
      debugPrint('[FCM] 토큰 저장 실패: $e');
    }
  }

  /// 현재 저장된 FCM 토큰 가져오기
  Future<String?> getToken() async {
    return await _messaging.getToken();
  }

  /// Foreground 메시지 리스너 등록
  ///
  /// 사용 예:
  /// ```dart
  /// fcmService.listenToForegroundMessages((message) {
  ///   if (message.data['type'] == FcmService.notificationTypeDriverAssigned) {
  ///     // 기사 배정 다이얼로그 표시
  ///   }
  /// });
  /// ```
  void listenToForegroundMessages(
    void Function(RemoteMessage message) onMessage,
  ) {
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      debugPrint('[FCM] Foreground 메시지 수신');
      debugPrint('  - 타입: ${message.data['type']}');
      debugPrint('  - 데이터: ${message.data}');
      debugPrint('  - 알림: ${message.notification?.title}');

      onMessage(message);
    });
  }

  /// Background → Foreground 전환 시 메시지 리스너 등록
  ///
  /// 사용 예:
  /// ```dart
  /// fcmService.listenToMessageOpenedApp((message) {
  ///   // 알림 클릭으로 앱 열림 → 해당 화면으로 이동
  /// });
  /// ```
  void listenToMessageOpenedApp(
    void Function(RemoteMessage message) onMessageOpened,
  ) {
    FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
      debugPrint('[FCM] Background → Foreground 메시지 처리');
      debugPrint('  - 타입: ${message.data['type']}');
      debugPrint('  - 데이터: ${message.data}');

      onMessageOpened(message);
    });
  }

  /// 앱이 종료된 상태에서 알림 클릭으로 실행된 경우 메시지 가져오기
  ///
  /// 호출 시점: main.dart의 runApp() 직후
  Future<RemoteMessage?> getInitialMessage() async {
    return await _messaging.getInitialMessage();
  }

  /// 알림 메시지 데이터 파싱 헬퍼
  static Map<String, dynamic> parseNotificationData(RemoteMessage message) {
    final type = message.data['type'] ?? '';
    final data = Map<String, dynamic>.from(message.data);

    return {
      'type': type,
      'callId': data['callId'],
      'driverName': data['driverName'],
      'driverPhone': data['driverPhone'],
      'vehicleNumber': data['vehicleNumber'],
      'fare': data['fare'],
      'earnedPoints': data['earnedPoints'],
      'message': message.notification?.body ?? '',
    };
  }
}

/// Background 메시지 핸들러 (Top-level 함수 필수)
///
/// 주의: 이 함수는 격리된 환경에서 실행되므로 전역 변수에 접근 불가
@pragma('vm:entry-point')
Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  debugPrint('[FCM] Background 메시지 수신: ${message.messageId}');
  debugPrint('  - 타입: ${message.data['type']}');
  debugPrint('  - 데이터: ${message.data}');

  // Background에서는 로컬 알림만 표시
  // 실제 데이터 처리는 앱이 Foreground로 전환될 때 수행
}
