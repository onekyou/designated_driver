import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter/foundation.dart';

/// FCM 푸시 알림 서비스 (기사 앱용)
class FcmService {
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();

  // 알림 타입 상수
  static const String notificationTypeCallAssigned = 'CALL_ASSIGNED';
  static const String notificationTypeCallCancelled = 'CALL_CANCELLED';
  static const String notificationTypeCallUpdated = 'CALL_UPDATED';

  /// FCM 초기화
  Future<void> initialize({
    required String regionId,
    required String officeId,
    required String driverId,
  }) async {
    // 알림 권한 요청
    NotificationSettings settings = await _messaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
      provisional: false,
    );

    if (settings.authorizationStatus == AuthorizationStatus.authorized) {
      debugPrint('[FCM] 알림 권한 승인됨');
    } else if (settings.authorizationStatus ==
        AuthorizationStatus.provisional) {
      debugPrint('[FCM] 임시 알림 권한 승인됨');
    } else {
      debugPrint('[FCM] 알림 권한 거부됨');
      return;
    }

    // FCM 토큰 가져오기
    String? token = await _messaging.getToken();
    if (token != null) {
      debugPrint('[FCM] 토큰: $token');
      // Firestore에 토큰 저장
      await _saveTokenToFirestore(
        regionId: regionId,
        officeId: officeId,
        driverId: driverId,
        token: token,
      );
    }

    // 토큰 갱신 리스너
    _messaging.onTokenRefresh.listen((newToken) {
      debugPrint('[FCM] 토큰 갱신: $newToken');
      _saveTokenToFirestore(
        regionId: regionId,
        officeId: officeId,
        driverId: driverId,
        token: newToken,
      );
    });

    // 로컬 알림 초기화
    await _initializeLocalNotifications();
  }

  /// 로컬 알림 초기화
  Future<void> _initializeLocalNotifications() async {
    const AndroidInitializationSettings androidSettings =
        AndroidInitializationSettings('@mipmap/ic_launcher');

    const DarwinInitializationSettings iosSettings =
        DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );

    const InitializationSettings settings = InitializationSettings(
      android: androidSettings,
      iOS: iosSettings,
    );

    await _localNotifications.initialize(
      settings,
      onDidReceiveNotificationResponse: (NotificationResponse response) {
        debugPrint('[FCM] 로컬 알림 클릭: ${response.payload}');
      },
    );
  }

  /// Firestore에 FCM 토큰 저장
  Future<void> _saveTokenToFirestore({
    required String regionId,
    required String officeId,
    required String driverId,
    required String token,
  }) async {
    try {
      await FirebaseFirestore.instance
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('designated_drivers')
          .doc(driverId)
          .update({
        'fcmToken': token,
        'fcmTokenUpdatedAt': FieldValue.serverTimestamp(),
      });
      debugPrint('[FCM] 토큰 저장 성공');
    } catch (e) {
      debugPrint('[FCM] 토큰 저장 실패: $e');
    }
  }

  /// Foreground 메시지 리스너 설정
  void listenToForegroundMessages(
      void Function(RemoteMessage) onMessage) {
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      debugPrint('[FCM Foreground] 메시지 수신: ${message.messageId}');
      debugPrint('[FCM Foreground] 제목: ${message.notification?.title}');
      debugPrint('[FCM Foreground] 내용: ${message.notification?.body}');
      debugPrint('[FCM Foreground] 데이터: ${message.data}');

      // 로컬 알림 표시
      _showLocalNotification(message);

      // 콜백 호출
      onMessage(message);
    });
  }

  /// Background/Terminated에서 앱 열림 리스너
  void listenToMessageOpenedApp(
      void Function(RemoteMessage) onMessageOpened) {
    // Background에서 알림 클릭
    FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
      debugPrint('[FCM Background] 앱 열림: ${message.messageId}');
      onMessageOpened(message);
    });

    // Terminated에서 알림 클릭
    _messaging.getInitialMessage().then((RemoteMessage? message) {
      if (message != null) {
        debugPrint('[FCM Terminated] 앱 열림: ${message.messageId}');
        onMessageOpened(message);
      }
    });
  }

  /// 로컬 알림 표시 (Foreground용)
  Future<void> _showLocalNotification(RemoteMessage message) async {
    const AndroidNotificationDetails androidDetails =
        AndroidNotificationDetails(
      'driver_call_channel', // 채널 ID
      '콜 배정 알림', // 채널 이름
      channelDescription: '새로운 콜 배정 알림을 받습니다',
      importance: Importance.high,
      priority: Priority.high,
      showWhen: true,
      enableVibration: true,
      playSound: true,
    );

    const DarwinNotificationDetails iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );

    const NotificationDetails details = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    await _localNotifications.show(
      message.hashCode,
      message.notification?.title ?? '새로운 알림',
      message.notification?.body ?? '',
      details,
      payload: message.data.toString(),
    );
  }

  /// FCM 토큰 가져오기
  Future<String?> getToken() async {
    return await _messaging.getToken();
  }

  /// 알림 타입별 처리
  static String getNotificationMessage(String type, Map<String, dynamic> data) {
    switch (type) {
      case notificationTypeCallAssigned:
        return '새로운 콜이 배정되었습니다\n${data['customerName'] ?? ''} - ${data['destination'] ?? ''}';
      case notificationTypeCallCancelled:
        return '콜이 취소되었습니다';
      case notificationTypeCallUpdated:
        return '콜 정보가 업데이트되었습니다';
      default:
        return '새로운 알림이 도착했습니다';
    }
  }
}
