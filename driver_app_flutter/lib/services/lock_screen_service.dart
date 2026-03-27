import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// 잠금화면 콜 알림 서비스 (Android MethodChannel)
/// LockScreenActivity를 네이티브로 실행
class LockScreenService {
  static const _channel = MethodChannel('com.designated.driverapp/lockscreen');

  /// 잠금화면 콜 알림 표시
  static Future<void> showLockScreen({
    required String callId,
    String? customerName,
    String? destination,
    String? phoneNumber,
  }) async {
    try {
      await _channel.invokeMethod('showLockScreen', {
        'callId': callId,
        'customerName': customerName ?? '고객',
        'destination': destination ?? '',
        'phoneNumber': phoneNumber ?? '',
      });
    } on PlatformException catch (e) {
      debugPrint('[LockScreen] 실행 실패: $e');
    }
  }
}
