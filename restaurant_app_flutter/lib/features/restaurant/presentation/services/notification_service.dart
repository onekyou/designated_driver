import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

/// FCM 메시지 처리.
///
/// 백엔드 (PR 1) 가 식당앱으로 보내는 type:
/// - `RESTAURANT_CALL_CLAIMED` (onSharedCallClaimed) — data: officeName, officePhone, sharedCallId
/// - `RESTAURANT_NO_RESPONSE` (notifyRestaurantOnNoResponse) — 5분 미응답 알림
///
/// foreground listener 는 main.dart 초기화 시 `attach(navigatorKey)` 1회 호출.
/// background/종료 상태 알림은 시스템 트레이가 자동 표시.
class RestaurantNotificationService {
  RestaurantNotificationService._();
  static final instance = RestaurantNotificationService._();

  GlobalKey<NavigatorState>? _navigatorKey;

  Future<void> attach(GlobalKey<NavigatorState> navigatorKey) async {
    _navigatorKey = navigatorKey;
    await FirebaseMessaging.instance.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );
    FirebaseMessaging.onMessage.listen(_onForegroundMessage);
    FirebaseMessaging.onMessageOpenedApp.listen(_onMessageOpened);
    debugPrint('[NotificationService] attached');
  }

  void _onForegroundMessage(RemoteMessage message) {
    final type = message.data['type'];
    debugPrint('[NotificationService] foreground - type: $type, data: ${message.data}');
    if (type == 'RESTAURANT_CALL_CLAIMED') {
      _showCallClaimedDialog(message);
    } else if (type == 'RESTAURANT_NO_RESPONSE') {
      _showSnackBar('5분간 콜 응답이 없습니다. 다른 호출을 시도하세요.');
    }
  }

  void _onMessageOpened(RemoteMessage message) {
    final type = message.data['type'];
    debugPrint('[NotificationService] opened - type: $type');
    if (type == 'RESTAURANT_CALL_CLAIMED') {
      _showCallClaimedDialog(message);
    }
  }

  void _showCallClaimedDialog(RemoteMessage message) {
    final ctx = _navigatorKey?.currentState?.overlay?.context;
    if (ctx == null) return;
    final officeName = message.data['officeName'] ?? '사무실';
    final officePhone = message.data['officePhone'] ?? '';
    showDialog<void>(
      context: ctx,
      builder: (_) => AlertDialog(
        title: const Text('콜이 잡혔습니다'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('$officeName이(가) 운행을 시작합니다.'),
            if (officePhone.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('연락처: $officePhone'),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('확인'),
          ),
          if (officePhone.isNotEmpty)
            FilledButton.icon(
              icon: const Icon(Icons.phone),
              label: const Text('통화'),
              onPressed: () async {
                Navigator.pop(ctx);
                final uri = Uri(
                    scheme: 'tel', path: officePhone.replaceAll('-', ''));
                if (await canLaunchUrl(uri)) await launchUrl(uri);
              },
            ),
        ],
      ),
    );
  }

  void _showSnackBar(String message) {
    final ctx = _navigatorKey?.currentState?.overlay?.context;
    if (ctx == null) return;
    ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: Text(message)));
  }
}
