import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter/foundation.dart';

/// Foreground Service — 앱이 백그라운드에서도 FCM 수신 + Presence 유지
/// Kotlin DriverForegroundService 대응
class DriverForegroundService {
  static bool _initialized = false;

  /// 포그라운드 서비스 초기화
  static Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;

    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: 'driver_foreground_channel',
        channelName: '기사앱 서비스',
        channelDescription: '기사앱이 실행 중입니다',
        channelImportance: NotificationChannelImportance.LOW,
        priority: NotificationPriority.LOW,
        visibility: NotificationVisibility.VISIBILITY_SECRET,
      ),
      iosNotificationOptions: const IOSNotificationOptions(
        showNotification: false,
      ),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.nothing(),
        autoRunOnBoot: true,
        autoRunOnMyPackageReplaced: true,
        allowWakeLock: true,
        allowWifiLock: true,
      ),
    );

    debugPrint('[ForegroundService] 초기화 완료');
  }

  /// 서비스 시작
  static Future<void> start() async {
    if (!_initialized) await initialize();

    final isRunning = await FlutterForegroundTask.isRunningService;
    if (isRunning) return;

    await FlutterForegroundTask.startService(
      serviceId: 100,
      notificationTitle: '기사앱',
      notificationText: '콜 대기 중...',
      callback: _startCallback,
    );

    debugPrint('[ForegroundService] 서비스 시작');
  }

  /// 알림 텍스트 업데이트
  static Future<void> updateNotification(String text) async {
    await FlutterForegroundTask.updateService(
      notificationTitle: '기사앱',
      notificationText: text,
    );
  }

  /// 서비스 중지
  static Future<void> stop() async {
    await FlutterForegroundTask.stopService();
    debugPrint('[ForegroundService] 서비스 중지');
  }
}

/// 백그라운드 서비스 콜백 (최소 구현)
@pragma('vm:entry-point')
void _startCallback() {
  FlutterForegroundTask.setTaskHandler(_DriverTaskHandler());
}

class _DriverTaskHandler extends TaskHandler {
  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {
    debugPrint('[TaskHandler] onStart');
  }

  @override
  void onRepeatEvent(DateTime timestamp) {
    // 주기적 이벤트 — 현재 미사용
  }

  @override
  Future<void> onDestroy(DateTime timestamp) async {
    debugPrint('[TaskHandler] onDestroy');
  }
}
