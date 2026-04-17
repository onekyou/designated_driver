import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter/foundation.dart';
import '../core/constants/app_constants.dart';
import '../core/utils/fcm_token_payload.dart';
import 'lock_screen_service.dart';

/// FCM 푸시 알림 서비스 (Phase 4 — Kotlin 6종 완전 대응)
class FcmService {
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();

  // 알림 타입 (Kotlin과 일치)
  static const String typeCallAssigned = 'call_assigned';
  static const String typeCallCancelled = 'call_cancelled';
  static const String typeSettlementFinalized = 'SETTLEMENT_FINALIZED';
  static const String typeSettlementConfirmed = 'SETTLEMENT_CONFIRMED';
  static const String typeSettlementRejected = 'SETTLEMENT_REJECTED';
  static const String typeCarryoverTransferred = 'CARRYOVER_TRANSFERRED';

  // 콜백 (HomeScreen에서 설정)
  void Function(String type, Map<String, dynamic> data)? onMessageReceived;

  /// 초기화
  Future<void> initialize({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String driverId,
  }) async {
    final settings = await _messaging.requestPermission(
      alert: true, badge: true, sound: true, provisional: false,
    );
    if (settings.authorizationStatus != AuthorizationStatus.authorized &&
        settings.authorizationStatus != AuthorizationStatus.provisional) {
      debugPrint('[FCM] 알림 권한 거부됨');
      return;
    }

    // FCM 토큰 등록
    String? token = await _messaging.getToken();
    if (token != null) {
      await _saveTokenToFirestore(
        provinceId: provinceId, cityId: cityId,
        officeId: officeId, driverId: driverId, token: token,
      );
    }

    // 토큰 갱신 리스너
    _messaging.onTokenRefresh.listen((newToken) {
      _saveTokenToFirestore(
        provinceId: provinceId, cityId: cityId,
        officeId: officeId, driverId: driverId, token: newToken,
      );
    });

    await _initializeLocalNotifications();

    debugPrint('[FCM] 초기화 완료');
  }

  /// 로컬 알림 초기화
  Future<void> _initializeLocalNotifications() async {
    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosSettings = DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );

    await _localNotifications.initialize(
      settings: const InitializationSettings(android: androidSettings, iOS: iosSettings),
      onDidReceiveNotificationResponse: (response) {
        debugPrint('[FCM] 알림 클릭: ${response.payload}');
        // Deep link 처리: payload에서 callId 추출 → onMessageReceived 호출
        if (response.payload != null) {
          onMessageReceived?.call('notification_tap', {'payload': response.payload});
        }
      },
    );
  }

  /// Firestore에 FCM 토큰 저장
  Future<void> _saveTokenToFirestore({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String driverId,
    required String token,
  }) async {
    try {
      await FirebaseFirestore.instance
          .collection(AppConstants.collectionProvinces).doc(provinceId)
          .collection(AppConstants.collectionCities).doc(cityId)
          .collection(AppConstants.collectionOffices).doc(officeId)
          .collection(AppConstants.collectionDrivers).doc(driverId)
          .update(buildTokenUpdatePayload(token: token));
      debugPrint('[FCM] 토큰 저장 성공');
    } catch (e) {
      debugPrint('[FCM] 토큰 저장 실패: $e');
    }
  }

  /// Foreground 메시지 리스너
  void listenToForegroundMessages(void Function(RemoteMessage) onMessage) {
    FirebaseMessaging.onMessage.listen((message) {
      debugPrint('[FCM FG] ${message.data}');
      final type = message.data['type'] as String?;

      // call_assigned: 포그라운드에서는 팝업으로 처리 (시스템 알림 안 띄움)
      if (type == typeCallAssigned) {
        onMessage(message);
        return;
      }

      // 나머지: 시스템 알림 + 콜백
      _showLocalNotification(message);
      onMessage(message);
    });
  }

  /// Background/Terminated → 앱 열림 리스너
  void listenToMessageOpenedApp(void Function(RemoteMessage) onMessageOpened) {
    FirebaseMessaging.onMessageOpenedApp.listen((message) {
      debugPrint('[FCM BG] 앱 열림: ${message.data}');
      onMessageOpened(message);
    });

    _messaging.getInitialMessage().then((message) {
      if (message != null) {
        debugPrint('[FCM Terminated] 앱 열림: ${message.data}');
        onMessageOpened(message);
      }
    });
  }

  /// 로컬 알림 표시
  Future<void> _showLocalNotification(RemoteMessage message) async {
    final type = message.data['type'] as String?;
    final channelId = _getChannelId(type);
    final channelName = _getChannelName(type);

    final androidDetails = AndroidNotificationDetails(
      channelId, channelName,
      channelDescription: channelName,
      importance: Importance.high,
      priority: Priority.high,
      showWhen: true,
      enableVibration: true,
      playSound: true,
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true, presentBadge: true, presentSound: true,
    );

    await _localNotifications.show(
      id: message.hashCode,
      title: message.notification?.title ?? _getDefaultTitle(type),
      body: message.notification?.body ?? '',
      notificationDetails: NotificationDetails(android: androidDetails, iOS: iosDetails),
      payload: message.data['callId'] ?? message.data.toString(),
    );
  }

  /// Delivery ACK (Cloud Function 호출)
  Future<void> sendDeliveryAck(String messageId) async {
    try {
      final callable = FirebaseFunctions.instance.httpsCallable('acknowledgeNotification');
      await callable.call({'messageId': messageId});
      debugPrint('[FCM] Delivery ACK: $messageId');
    } catch (e) {
      debugPrint('[FCM] ACK 실패: $e');
    }
  }

  /// 백그라운드 콜 알림 → 잠금화면 Activity
  static Future<void> showLockScreenForCall(Map<String, dynamic> data) async {
    await LockScreenService.showLockScreen(
      callId: data['callId'] ?? '',
      customerName: data['customerName'],
      destination: data['destination'],
      phoneNumber: data['phoneNumber'],
    );
  }

  // 채널 헬퍼
  String _getChannelId(String? type) {
    switch (type) {
      case typeCallAssigned:
      case typeCallCancelled:
        return 'driver_call_channel';
      case typeSettlementFinalized:
      case typeSettlementConfirmed:
      case typeSettlementRejected:
        return 'driver_settlement_channel';
      case typeCarryoverTransferred:
        return 'driver_carryover_channel';
      default:
        return 'driver_default_channel';
    }
  }

  String _getChannelName(String? type) {
    switch (type) {
      case typeCallAssigned:
      case typeCallCancelled:
        return '콜 알림';
      case typeSettlementFinalized:
      case typeSettlementConfirmed:
      case typeSettlementRejected:
        return '정산 알림';
      case typeCarryoverTransferred:
        return '이월금 알림';
      default:
        return '기타 알림';
    }
  }

  String _getDefaultTitle(String? type) {
    switch (type) {
      case typeCallAssigned: return '새로운 콜 배정';
      case typeCallCancelled: return '콜 취소';
      case typeSettlementFinalized: return '정산 확정';
      case typeSettlementConfirmed: return '정산 승인';
      case typeSettlementRejected: return '정산 거절';
      case typeCarryoverTransferred: return '이월금 이체';
      default: return '새로운 알림';
    }
  }

  Future<String?> getToken() => _messaging.getToken();
}
