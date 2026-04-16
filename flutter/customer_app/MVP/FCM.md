# 손님앱 FCM 핸들링 — Time Sensitive + 5종 메시지 + 포인트 교차 의존

> **기사앱 `flutter/driver_app/MVP/FCM.md`와 자매 문서**. 손님앱은 FCM 5종 + `CallNotifier ↔ PointNotifier` 교차 의존 + 전화번호(phone) 키 기반 문서 구조가 핵심 차이.
> **의제 4 (apns 블록 + data 보존)** + **의제 5 (fcmToken + fcmTokenPlatform 메타)** + **의제 7 (Time Sensitive MVP)** + **의제 11 (측정 인프라)** 결정 반영.
> **원칙**: Phone Auth는 프로필/본인확인 전용, FCM 토큰 저장은 **Anonymous Auth UID 아닌 phone number 키 기반** (`customerInfo/{phone}`). Kotlin 호환 유지.
> **작성일**: 2026-04-16

---

## 0. 의존성 사양 (pubspec.yaml)

손님앱 `customer_app_flutter/pubspec.yaml`은 Phase 1 신규 작성 — 기사앱(`driver_app_flutter/`)과 동일 버전 정책 적용.

```yaml
name: customer_app_flutter
description: "대리운전 손님앱"
publish_to: 'none'

version: 1.0.0+1

environment:
  sdk: ^3.5.4

dependencies:
  flutter:
    sdk: flutter

  # Firebase (기사앱과 동일 버전 — 의제 1 iOS 15.0 정합)
  firebase_core: ^3.6.0
  firebase_auth: ^5.3.1      # Phone Auth
  cloud_firestore: ^5.4.4
  firebase_messaging: ^15.1.3
  cloud_functions: ^5.3.3

  # 의제 11 측정 인프라
  firebase_analytics: ^11.3.0
  firebase_crashlytics: ^4.1.0

  # 의제 10 (앱 업데이트 + 버전 강제)
  in_app_update: ^4.2.3
  firebase_remote_config: ^5.1.0

  # 상태관리
  flutter_riverpod: ^2.5.1
  riverpod_annotation: ^2.3.5

  # 데이터 모델
  freezed_annotation: ^2.4.4

  # 라우팅 (기사앱과 통일)
  go_router: ^14.2.7

  # 저장소
  shared_preferences: ^2.3.2
  flutter_secure_storage: ^9.2.2

  # 위치
  geolocator: ^10.1.0
  geocoding: ^2.1.1

  # 알림 (iOS Time Sensitive)
  flutter_local_notifications: ^21.0.0

  # QR 스캔 (ATTRIBUTION.md §2.4)
  mobile_scanner: ^5.2.0

  # 기타
  connectivity_plus: ^6.0.5
  permission_handler: ^11.3.1
  intl: ^0.19.0
  http: ^1.6.0

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^4.0.0
  build_runner: ^2.4.12
  freezed: ^2.5.7
  riverpod_generator: ^2.4.3
```

**손님앱 미포함** (기사앱과 차이):
- `firebase_database` — Presence 시스템 없음 (손님은 오프라인 추적 불필요)
- `flutter_foreground_task` — Foreground Service 불필요
- `qr_flutter` — QR 생성 없음 (스캔만, mobile_scanner로 대체)

---

## 1. APNs 등록 + FCM 토큰 저장

### 1.1 토큰 저장 경로 (Kotlin 호환)

**Kotlin 원본 저장 경로**: `customerInfo/{phone}` (phone number를 문서 ID로 사용)

Kotlin 손님앱 `MainActivity.kt:315, 592` / `ProfileSetupViewModel.kt:154, 181` / `MyFirebaseMessagingService.kt:404` 모두 `customerInfo/{phone}` 경로에 `fcmToken` 필드로 저장 (ATTRIBUTION.md §1.3에서 확인된 키 계약).

**왜 Anonymous Auth UID 아닌 phone 키인가**:
- 손님앱은 가입 플로우가 **Phone Auth 기반** (Kotlin 기존)
- 전화번호가 불변 식별자 (기기 변경, 앱 재설치, UID 변경에도 같은 phone)
- 기사앱(`designated_drivers/{uid}`)과 구조적으로 다름

**Flutter 저장 경로 (의제 5 메타 필드 추가)**:
```
customerInfo/{phone}
  ├── fcmToken: String
  ├── fcmTokenPlatform: 'ios' | 'android'   ← 의제 5 신규
  ├── fcmTokenUpdatedAt: Timestamp
  ├── phoneNumber: String
  ├── name: String
  └── ... (ATTRIBUTION.md §1.3 19개 키 중 customerInfo/* 서브셋)
```

### 1.2 초기화 타이밍

**기사앱과 손님앱 초기화 시점 차이**:

| 앱 | FCM 초기화 시점 | 토큰 저장 키 |
|----|-----------------|--------------|
| 기사앱 | 이메일 로그인 성공 + 기사 승인 확인 후 | `designated_drivers/{uid}` |
| 손님앱 | **Phone Auth 완료 + 프로필 생성 후** | `customerInfo/{phone}` |

손님이 Phone Auth 완료 전에 토큰 저장 시도 → phone이 null이라 Firestore 경로 구성 불가. 순서 엄수 필요.

### 1.3 main.dart 초기화

**파일**: `customer_app_flutter/lib/main.dart`

```dart
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:firebase_crashlytics/firebase_crashlytics.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'firebase_options.dart';
import 'services/fcm_service.dart';
import 'services/app_clip_migration_service.dart'; // ATTRIBUTION.md §3.6

/// 백그라운드 메시지 핸들러 — top-level 필수, tree-shaking 방지
@pragma('vm:entry-point')
Future<void> customerFirebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  debugPrint('[Customer FCM BG] type=${message.data['type']} callId=${message.data['callId']}');

  // 손님앱 백그라운드 isolate 작업 최소 (§5 참조)
  // CALL_CANCELLED: 팝업 제거 로직은 foreground에서, BG는 배너만
  // 장시간 작업 금지 (iOS 30초 제한)
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);

  // 의제 11 — Crashlytics 초기화
  FlutterError.onError = FirebaseCrashlytics.instance.recordFlutterFatalError;
  PlatformDispatcher.instance.onError = (error, stack) {
    FirebaseCrashlytics.instance.recordError(error, stack, fatal: true);
    return true;
  };

  // BG handler 등록
  FirebaseMessaging.onBackgroundMessage(customerFirebaseMessagingBackgroundHandler);

  runApp(const ProviderScope(child: CustomerApp()));
}
```

### 1.4 FcmService 구현

**파일**: `customer_app_flutter/lib/services/fcm_service.dart` (신규)

```dart
import 'dart:io' show Platform;
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_analytics/firebase_analytics.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter/foundation.dart';
import '../core/constants/app_constants.dart';

class CustomerFcmService {
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();
  final FirebaseAnalytics _analytics = FirebaseAnalytics.instance;

  // 손님앱 FCM 5종 — Kotlin MyFirebaseMessagingService.kt:41-119 1:1 일치
  static const String typeCallReceived = 'CALL_RECEIVED';
  static const String typeDriverAssigned = 'DRIVER_ASSIGNED';
  static const String typeRideCompleted = 'RIDE_COMPLETED';
  static const String typeCallCancelled = 'CALL_CANCELLED';
  static const String typeCallStatusUpdate = 'call_status_update';

  // 기사앱용 알림 타입 (손님앱에서 무시, Kotlin line 37 참조)
  static const String typeCallAssigned = 'call_assigned'; // ignore

  // Riverpod Notifier에 전달할 콜백
  void Function(String type, Map<String, dynamic> data)? onMessageReceived;

  /// Phone Auth 완료 + 프로필 설정 후 호출
  Future<void> initialize({required String phoneNumber}) async {
    // iOS 권한 요청
    final settings = await _messaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
      provisional: false,
      criticalAlert: false,
    );

    if (settings.authorizationStatus != AuthorizationStatus.authorized &&
        settings.authorizationStatus != AuthorizationStatus.provisional) {
      debugPrint('[Customer FCM] 권한 거부됨');
      await _analytics.logEvent(
        name: 'customer_fcm_permission_denied',
        parameters: {'platform': Platform.isIOS ? 'ios' : 'android'},
      );
      return;
    }

    // iOS APNs 토큰 확인
    if (Platform.isIOS) {
      final apnsToken = await _messaging.getAPNSToken();
      if (apnsToken == null) {
        debugPrint('[Customer FCM] iOS APNs 토큰 null');
        return;
      }
    }

    // FCM 토큰 발급 + Firestore 저장
    final token = await _messaging.getToken();
    if (token != null && phoneNumber.isNotEmpty) {
      await _saveTokenToFirestore(phoneNumber: phoneNumber, token: token);
    }

    // 토큰 갱신 리스너
    _messaging.onTokenRefresh.listen((newToken) {
      if (phoneNumber.isNotEmpty) {
        _saveTokenToFirestore(phoneNumber: phoneNumber, token: newToken);
      }
    });

    await _initializeLocalNotifications();
    debugPrint('[Customer FCM] 초기화 완료 (phone=$phoneNumber)');
  }

  /// customerInfo/{phone} 경로에 토큰 저장 (의제 5 메타 포함)
  Future<void> _saveTokenToFirestore({
    required String phoneNumber,
    required String token,
  }) async {
    try {
      final platform = Platform.isIOS ? 'ios' : 'android'; // 의제 5

      await FirebaseFirestore.instance
          .collection(AppConstants.collectionCustomerInfo)
          .doc(phoneNumber)
          .set({
        AppConstants.fieldFcmToken: token,
        'fcmTokenPlatform': platform,
        'fcmTokenUpdatedAt': FieldValue.serverTimestamp(),
      }, SetOptions(merge: true)); // 다른 필드 덮어쓰기 방지

      debugPrint('[Customer FCM] 토큰 저장: phone=$phoneNumber platform=$platform');
    } on FirebaseException catch (e) {
      debugPrint('[Customer FCM] 토큰 저장 실패: ${e.code} ${e.message}');
    }
  }
}
```

> **주의**: 기사앱은 `.update()` 사용 (문서 이미 존재 전제), 손님앱은 `.set(..., merge: true)` 사용 (프로필 설정 중 생성되므로 문서 없을 수도 있음).

### 1.5 onTokenRefresh 특이사항

손님앱은 **phone 키 기반**이므로:
- 사용자가 폰 교체 + 같은 전화번호 → 새 기기의 FCM 토큰이 `customerInfo/{phone}`을 덮어씀 → 새 기기로 FCM 도달 (정상)
- 사용자가 전화번호 변경 → 기존 `customerInfo/{old_phone}` 문서는 고아. 재로그인 시 `customerInfo/{new_phone}` 신규 생성
- Phone Auth 재인증 시 재사용 — FCM 토큰도 새 phone 문서에 저장

**의제 9 결정 재확인**: Kotlin → Flutter Android 업데이트 시 Firebase Auth Phone Auth credential 유지 가능성 높음 (같은 applicationId). iOS는 신규이므로 Phone Auth 최초 진입.

### 1.6 손님앱 AppConstants 신규 필드

**파일**: `customer_app_flutter/lib/core/constants/app_constants.dart` (신규, 기사앱과 대칭)

```dart
class AppConstants {
  // Firestore Collections
  static const String collectionProvinces = 'provinces';
  static const String collectionCities = 'cities';
  static const String collectionOffices = 'offices';
  static const String collectionCalls = 'calls';
  static const String collectionCustomers = 'customers';
  static const String collectionCustomerInfo = 'customerInfo'; // phone 키 기반
  static const String collectionCustomerPoints = 'customerPoints';

  // Fields
  static const String fieldFcmToken = 'fcmToken';
  static const String fieldPhoneNumber = 'phoneNumber';
  static const String fieldFare = 'fare';
  static const String fieldPointsUsed = 'pointsUsed';
  static const String fieldPointsEarned = 'pointsEarned';
  // ...

  // Call Status (Kotlin과 일치)
  static const String callStatusWaiting = 'WAITING';
  static const String callStatusAssigned = 'ASSIGNED';
  static const String callStatusAccepted = 'ACCEPTED';
  static const String callStatusInProgress = 'IN_PROGRESS';
  static const String callStatusCompleted = 'COMPLETED';
  static const String callStatusCanceled = 'CANCELED';
  static const String callStatusCancelledByDriver = 'CANCELLED_BY_DRIVER';
  static const String callStatusCancelledByCustomer = 'CANCELLED_BY_CUSTOMER';
}
```

---

## 2. FCM 5종 핸들러 매트릭스

### 2.1 Kotlin 원본 참조 (MyFirebaseMessagingService.kt:32-122)

| 라인 | 타입 | 처리 |
|------|------|------|
| 32 | `onMessageReceived` | 진입점 |
| 36 | `call_assigned` | **무시** (기사앱용) |
| 41 | `CALL_RECEIVED` | Broadcast + 알림 표시 |
| 55 | `DRIVER_ASSIGNED` | Broadcast + 알림 (이름/전화/차량) |
| 72 | `RIDE_COMPLETED` | Broadcast + 포인트 적립 팝업 |
| 87 | `CALL_CANCELLED` | Broadcast + 취소 알림 |
| 101 | `call_status_update` | Broadcast + ACCEPTED/IN_PROGRESS 메시지 |

### 2.2 Flutter 처리 매트릭스

| 타입 | 포그라운드 | 백그라운드 | Time Sensitive |
|------|-----------|-----------|----------------|
| `CALL_RECEIVED` | `CallNotifier.onCallReceived()` → 상태 CONFIRMED + `restoreActiveCall()` | 배너 (채널 call_received) | ❌ active |
| `DRIVER_ASSIGNED` | 팝업 다이얼로그 (기사 이름/전화/차량) + 진동/벨소리 + `CallNotifier.onDriverAssigned()` | Time Sensitive 배너 | ✅ **timeSensitive** |
| `RIDE_COMPLETED` | 포인트 적립 팝업 + `PointNotifier.refresh()` | 배너 | ❌ active |
| `CALL_CANCELLED` | 팝업 닫기 + SnackBar "콜이 취소되었습니다" + `PointNotifier.refresh()` (환불 확인) | Time Sensitive 배너 | ✅ **timeSensitive** |
| `call_status_update` | 상태 전이 UI 갱신 (ACCEPTED: "기사 도착 중", IN_PROGRESS: "운행 시작") | 배너 | ❌ active |

> **왜 DRIVER_ASSIGNED / CALL_CANCELLED만 Time Sensitive?**
> - 손님 입장에서 "기사가 도착 중" / "콜이 취소됨"은 즉시 확인 필요
> - 나머지 3종은 일반 알림 수준으로 충분 (CALL_RECEIVED는 직접 요청 후 즉시 발송, RIDE_COMPLETED/status_update는 운행 중/후 알림)

### 2.3 포그라운드 리스너

**파일**: `customer_app_flutter/lib/services/fcm_service.dart` (계속)

```dart
  /// 포그라운드 메시지 리스너
  void listenToForegroundMessages(
    void Function(RemoteMessage) onMessage,
  ) {
    FirebaseMessaging.onMessage.listen((message) {
      final type = message.data['type'] as String?;
      final callId = message.data['callId'] as String?;
      debugPrint('[Customer FG] type=$type callId=$callId');

      // 기사앱용 알림 무시 (Kotlin line 37 대응)
      if (type == typeCallAssigned) {
        debugPrint('[Customer FG] 기사앱용 알림 — 무시');
        return;
      }

      // 의제 11 측정 (§4)
      _logCustomerEvent(type, callId);

      switch (type) {
        case typeCallReceived:
          // Kotlin line 41-53: Broadcast + showCallReceivedNotification
          _showLocalNotification(message);
          onMessage(message); // CallNotifier에 전달
          break;

        case typeDriverAssigned:
          // Kotlin line 55-70: Broadcast + showDriverAssignedNotification
          // 포그라운드에서는 앱 내 팝업만 (시스템 알림 중복 방지)
          onMessage(message); // CallNotifier가 팝업 띄움
          break;

        case typeRideCompleted:
          // Kotlin line 72-85: 포인트 적립 팝업
          onMessage(message); // CallNotifier가 포인트 팝업 트리거
          break;

        case typeCallCancelled:
          // Kotlin line 87-99: 팝업 제거 + 일반 알림
          _showLocalNotification(message);
          onMessage(message); // CallNotifier가 UI 리셋 + PointNotifier.refresh
          break;

        case typeCallStatusUpdate:
          // Kotlin line 101-119: 상태 전이 알림
          _showLocalNotification(message);
          onMessage(message); // CallNotifier가 상태 갱신
          break;

        default:
          debugPrint('[Customer FG] 알 수 없는 타입: $type');
      }
    });
  }

  /// 알림 탭 → 앱 열림
  void listenToMessageOpenedApp(
    void Function(RemoteMessage) onMessageOpened,
  ) {
    FirebaseMessaging.onMessageOpenedApp.listen((message) {
      debugPrint('[Customer tap] type=${message.data['type']}');
      onMessageOpened(message);
    });

    _messaging.getInitialMessage().then((message) {
      if (message != null) {
        debugPrint('[Customer initial] type=${message.data['type']}');
        onMessageOpened(message);
      }
    });
  }
```

### 2.4 로컬 알림 설정 (Android 채널 3종 + iOS Time Sensitive)

**Kotlin Android 채널 3종 (line 22-24)**:
- `call_received_channel` — 콜 접수
- `driver_assigned_channel` — 기사 배정
- `ride_completed_channel` — 운행 완료

Flutter에서도 동일 채널 ID 재사용 (Android 이관 시 알림 설정 보존):

```dart
  Future<void> _initializeLocalNotifications() async {
    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosSettings = DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );

    await _localNotifications.initialize(
      const InitializationSettings(android: androidSettings, iOS: iosSettings),
      onDidReceiveNotificationResponse: (response) {
        debugPrint('[Customer tap local] payload=${response.payload}');
        if (response.payload != null) {
          onMessageReceived?.call('notification_tap', {'payload': response.payload});
        }
      },
    );

    await _createAndroidChannels();
  }

  Future<void> _createAndroidChannels() async {
    final androidImpl = _localNotifications
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>();

    // Kotlin 호환 채널 3종 + 손님앱 신규 2종
    await androidImpl?.createNotificationChannel(const AndroidNotificationChannel(
      'call_received_channel',
      '콜 접수 알림',
      description: '콜이 접수되었을 때 알림',
      importance: Importance.high,
      enableVibration: true,
    ));

    await androidImpl?.createNotificationChannel(const AndroidNotificationChannel(
      'driver_assigned_channel',
      '기사 배정 알림',
      description: '기사가 배정되었을 때 알림',
      importance: Importance.max, // 중요도 최고
      enableVibration: true,
      playSound: true,
    ));

    await androidImpl?.createNotificationChannel(const AndroidNotificationChannel(
      'ride_completed_channel',
      '운행 완료 알림',
      description: '운행이 완료되었을 때 알림',
      importance: Importance.high,
    ));

    // 손님앱 Flutter 신규 채널 (call_cancelled_channel, call_status_update_channel)
    await androidImpl?.createNotificationChannel(const AndroidNotificationChannel(
      'call_cancelled_channel',
      '콜 취소 알림',
      description: '콜이 취소되었을 때 알림',
      importance: Importance.max,
      enableVibration: true,
    ));

    await androidImpl?.createNotificationChannel(const AndroidNotificationChannel(
      'call_status_update_channel',
      '콜 상태 변경',
      description: '기사 수락/운행 시작 알림',
      importance: Importance.high,
    ));
  }

  /// 로컬 알림 표시 (iOS Time Sensitive 차등 적용)
  Future<void> _showLocalNotification(RemoteMessage message) async {
    final type = message.data['type'] as String?;
    final channelId = _getChannelId(type);

    final androidDetails = AndroidNotificationDetails(
      channelId,
      _getChannelName(type),
      channelDescription: _getChannelName(type),
      importance: type == typeDriverAssigned || type == typeCallCancelled
          ? Importance.max
          : Importance.high,
      priority: Priority.high,
      showWhen: true,
      enableVibration: true,
      playSound: true,
    );

    // 의제 7 — iOS Time Sensitive (DRIVER_ASSIGNED, CALL_CANCELLED만)
    final iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
      interruptionLevel: (type == typeDriverAssigned || type == typeCallCancelled)
          ? InterruptionLevel.timeSensitive
          : InterruptionLevel.active,
      categoryIdentifier: type,
    );

    await _localNotifications.show(
      message.hashCode,
      message.notification?.title ?? _getDefaultTitle(type),
      message.notification?.body ?? _getDefaultBody(message.data),
      NotificationDetails(android: androidDetails, iOS: iosDetails),
      payload: message.data['callId'] ?? message.data.toString(),
    );
  }

  String _getChannelId(String? type) => switch (type) {
        typeCallReceived => 'call_received_channel',
        typeDriverAssigned => 'driver_assigned_channel',
        typeRideCompleted => 'ride_completed_channel',
        typeCallCancelled => 'call_cancelled_channel',
        typeCallStatusUpdate => 'call_status_update_channel',
        _ => 'call_received_channel',
      };

  String _getChannelName(String? type) => switch (type) {
        typeCallReceived => '콜 접수 알림',
        typeDriverAssigned => '기사 배정 알림',
        typeRideCompleted => '운행 완료 알림',
        typeCallCancelled => '콜 취소 알림',
        typeCallStatusUpdate => '콜 상태 변경',
        _ => '알림',
      };

  String _getDefaultTitle(String? type) => switch (type) {
        typeCallReceived => '콜 접수 완료',
        typeDriverAssigned => '기사가 배정되었습니다',
        typeRideCompleted => '운행이 완료되었습니다',
        typeCallCancelled => '콜이 취소되었습니다',
        typeCallStatusUpdate => '콜 상태 변경',
        _ => '새로운 알림',
      };

  String _getDefaultBody(Map<String, dynamic> data) {
    final type = data['type'] as String?;
    return switch (type) {
      typeCallReceived => data['message'] as String? ?? '콜이 접수되었습니다.',
      typeDriverAssigned =>
        '${data['driverName'] ?? "기사"}님이 배정되었습니다.',
      typeRideCompleted => '요금 ${data['fare'] ?? 0}원 · 포인트 적립',
      typeCallCancelled => data['cancelReason'] as String? ?? '콜이 취소되었습니다.',
      typeCallStatusUpdate => switch (data['status']) {
          'ACCEPTED' => '기사가 콜을 수락했습니다. 곧 도착합니다.',
          'IN_PROGRESS' => '운행이 시작되었습니다.',
          _ => '상태가 변경되었습니다.',
        },
      _ => '',
    };
  }
```

### 2.5 각 타입별 상세 핸들러 (CallNotifier 연계)

**파일**: `customer_app_flutter/lib/features/call/notifiers/call_notifier.dart` (신규, 기존 `MainViewModel.kt` 분해)

```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import '../../../services/fcm_service.dart';
import '../../points/notifiers/point_notifier.dart';

class CallState {
  final String? activeCallId;
  final String? callStatus; // WAITING/ASSIGNED/ACCEPTED/IN_PROGRESS/COMPLETED
  final String? driverName;
  final String? driverPhone;
  final String? vehicleNumber;
  final String? driverId;
  final int? completedFare;
  final int? pointsUsed;
  final String? cancelReason;
  final bool showDriverAssignedDialog;
  final bool showRideCompletedDialog;
  final bool showCancelledSnackbar;

  const CallState({
    this.activeCallId,
    this.callStatus,
    this.driverName,
    this.driverPhone,
    this.vehicleNumber,
    this.driverId,
    this.completedFare,
    this.pointsUsed,
    this.cancelReason,
    this.showDriverAssignedDialog = false,
    this.showRideCompletedDialog = false,
    this.showCancelledSnackbar = false,
  });

  CallState copyWith({ /* ... */ }) => /* ... */;
}

class CallNotifier extends StateNotifier<CallState> {
  final Ref _ref;
  final FirebaseFirestore _firestore;

  CallNotifier(this._ref, this._firestore) : super(const CallState());

  /// FCM onMessage 콜백 — main CustomerApp에서 연결
  Future<void> handleFcmMessage(RemoteMessage message) async {
    final type = message.data['type'] as String?;
    final callId = message.data['callId'] as String?;

    switch (type) {
      case CustomerFcmService.typeCallReceived:
        await _handleCallReceived(callId, message.data);
        break;
      case CustomerFcmService.typeDriverAssigned:
        await _handleDriverAssigned(message.data);
        break;
      case CustomerFcmService.typeRideCompleted:
        await _handleRideCompleted(message.data);
        break;
      case CustomerFcmService.typeCallCancelled:
        await _handleCallCancelled(message.data);
        break;
      case CustomerFcmService.typeCallStatusUpdate:
        await _handleCallStatusUpdate(message.data);
        break;
    }
  }

  /// CALL_RECEIVED — Kotlin MainViewModel.kt:191 restoreActiveCall 대응
  Future<void> _handleCallReceived(String? callId, Map<String, dynamic> data) async {
    if (callId == null) return;
    state = state.copyWith(
      activeCallId: callId,
      callStatus: 'WAITING',
    );
    // UI에서 "콜 접수 완료" SnackBar 또는 홈 화면 갱신
  }

  /// DRIVER_ASSIGNED — Kotlin line 55-70
  Future<void> _handleDriverAssigned(Map<String, dynamic> data) async {
    final driverName = data['driverName'] as String? ?? '기사';
    final driverPhone = data['driverPhone'] as String? ?? '';
    final vehicleNumber = data['vehicleNumber'] as String? ?? '';
    final driverId = data['driverId'] as String? ?? '';

    state = state.copyWith(
      callStatus: 'ASSIGNED',
      driverName: driverName,
      driverPhone: driverPhone,
      vehicleNumber: vehicleNumber,
      driverId: driverId,
      showDriverAssignedDialog: true,
    );
    // UI가 state.showDriverAssignedDialog 관측 → Dialog 표시 → 닫기 시 리셋
  }

  /// RIDE_COMPLETED — Kotlin line 72-85
  /// CF가 이미 포인트 적립 처리 완료 → 손님앱은 표시만
  Future<void> _handleRideCompleted(Map<String, dynamic> data) async {
    final fare = int.tryParse(data['fare']?.toString() ?? '0') ?? 0;
    final pointsUsed = int.tryParse(data['pointsUsed']?.toString() ?? '0') ?? 0;

    state = state.copyWith(
      callStatus: 'COMPLETED',
      completedFare: fare,
      pointsUsed: pointsUsed,
      showRideCompletedDialog: true,
    );

    // 포인트 실시간 갱신 (CallNotifier → PointNotifier, §6 교차 의존)
    await _ref.read(pointNotifierProvider.notifier).refresh();
  }

  /// CALL_CANCELLED — Kotlin line 87-99
  /// CF가 포인트 환불 처리 → 손님앱은 refresh 호출로 UI 갱신
  Future<void> _handleCallCancelled(Map<String, dynamic> data) async {
    final cancelReason = data['cancelReason'] as String? ?? '운행취소';

    state = state.copyWith(
      activeCallId: null,
      callStatus: null,
      driverName: null,
      driverPhone: null,
      vehicleNumber: null,
      driverId: null,
      showDriverAssignedDialog: false, // 팝업 닫기
      showRideCompletedDialog: false,
      cancelReason: cancelReason,
      showCancelledSnackbar: true,
    );

    // 포인트 환불 반영 (§6 교차 의존)
    await _ref.read(pointNotifierProvider.notifier).refresh();
  }

  /// call_status_update — Kotlin line 101-119
  Future<void> _handleCallStatusUpdate(Map<String, dynamic> data) async {
    final status = data['status'] as String? ?? '';
    final driverName = data['driverName'] as String?;
    final driverPhone = data['driverPhone'] as String?;

    state = state.copyWith(
      callStatus: status,
      driverName: driverName ?? state.driverName,
      driverPhone: driverPhone ?? state.driverPhone,
    );
    // UI가 status에 따라 "기사 도착 중" / "운행 중" 배너 표시
  }
}
```

---

## 3. Time Sensitive Notification (의제 7 차등 적용)

### 3.1 CF payload (의제 4 확정 형태)

손님앱 FCM은 CF 여러 곳에서 발송. 각 CF가 동일한 apns 블록 구조를 가져야 함 (의제 4 결정).

**DRIVER_ASSIGNED 예시** (`functions/src/index.ts` 손님앱 분기):

```typescript
// CF onCallAssigned — 기사 배정 시 손님에게 전송
const customerPayload = {
  data: {
    // 의제 4 — data 블록 보존
    callId: callId,
    type: "DRIVER_ASSIGNED",
    driverName: driverName,
    driverPhone: driverPhone,
    vehicleNumber: vehicleNumber,
    driverId: driverId,
  },
  notification: {
    title: "기사가 배정되었습니다",
    body: `${driverName}님이 곧 도착합니다`,
  },
  android: {
    priority: "high" as const,
    ttl: 60000,
    notification: {
      channelId: "driver_assigned_channel", // Kotlin 호환
      sound: "default",
    },
  },
  apns: {
    headers: {
      "apns-push-type": "alert",
      "apns-priority": "10",
      "apns-expiration": String(Math.floor(Date.now() / 1000) + 60),
    },
    payload: {
      aps: {
        alert: {
          title: "기사가 배정되었습니다",
          body: `${driverName}님이 곧 도착합니다`,
        },
        sound: "default",
        "content-available": 1,
        "mutable-content": 1,
        "interruption-level": "time-sensitive", // DRIVER_ASSIGNED만
      },
    },
  },
  token: customerFcmToken,
};
```

**CALL_RECEIVED / RIDE_COMPLETED / call_status_update 차이**:
- 동일 apns 구조이되 `"interruption-level": "active"` (기본값)
- TTL 더 길게 (RIDE_COMPLETED는 5분 허용)

**CALL_CANCELLED**:
- DRIVER_ASSIGNED과 동일 `"time-sensitive"` (즉시 확인 필요)

### 3.2 CF 송신 위치 (서버 별도 과제)

| FCM 타입 | 발송 CF | 파일 | apns 수정 필요 |
|----------|---------|------|----------------|
| `CALL_RECEIVED` | `onCallCreated` | `functions/src/index.ts` | ✅ |
| `DRIVER_ASSIGNED` | `onCallAssigned` (손님 분기) | `index.ts` line ~595-620 | ✅ **Time Sensitive** |
| `RIDE_COMPLETED` | `onCallCompleted` | `index.ts` | ✅ |
| `CALL_CANCELLED` | `onCallCancelled`, `onCallCancelledByDriver` | `index.ts` | ✅ **Time Sensitive** |
| `call_status_update` | `onCallStatusChanged` | `index.ts` | ✅ |

전체 CF audit은 **기사앱 FCM.md §3.1 + 의제 4 28곳+** 과제의 일부.

### 3.3 Info.plist / Entitlements

손님앱 `customer_app_flutter/ios/Runner/Info.plist` 신규 작성 시 기사앱과 동일 패턴:

```xml
<key>UIBackgroundModes</key>
<array>
  <string>fetch</string>
  <string>remote-notification</string>
  <string>location</string>
</array>

<key>NSCameraUsageDescription</key>
<string>사무실 QR 코드를 스캔하여 연결하기 위해 카메라를 사용합니다.</string>
<key>NSLocationWhenInUseUsageDescription</key>
<string>출발지와 도착지를 자동으로 입력하기 위해 현재 위치를 사용합니다.</string>
<key>NSMicrophoneUsageDescription</key>
<string>도착지를 음성으로 입력할 때 사용합니다 (R2 기능).</string>
```

`Runner.entitlements` (신규):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0">
<dict>
  <key>com.apple.developer.usernotifications.time-sensitive</key>
  <true/>
  <key>aps-environment</key>
  <string>production</string>
</dict>
</plist>
```

App Group `group.com.designated.customer`는 Phase 2 App Clip 도입 시 추가 (ATTRIBUTION.md §3.3 참조).

---

## 4. 측정 이벤트 로깅 (의제 11)

### 4.1 Analytics 이벤트 목록 (손님앱 전용)

기사앱은 `call_assigned_received` 등이지만 손님앱은 **별도 네임스페이스** (`customer_*`) 사용 — 이벤트명 충돌 방지 + 대시보드 분리.

| 이벤트명 | 발생 시점 | 파라미터 | 용도 |
|---------|-----------|----------|------|
| `customer_fcm_permission_denied` | 권한 거부 | `platform` | 거부율 |
| `customer_call_received_received` | CALL_RECEIVED 수신 | `call_id`, `platform` | 접수 피드백 지연 |
| `customer_driver_assigned_received` | DRIVER_ASSIGNED 수신 | `call_id`, `platform`, `time_to_assign_ms` | 배차 완료까지 시간 |
| `customer_ride_completed_received` | RIDE_COMPLETED 수신 | `call_id`, `fare`, `points_used` | 완료 흐름 |
| `customer_call_cancelled_received` | CALL_CANCELLED 수신 | `call_id`, `reason` | 취소 원인 분석 |
| `customer_call_status_changed` | call_status_update 수신 | `call_id`, `status` (ACCEPTED/IN_PROGRESS) | 상태 전환 |
| `customer_points_refund_observed` | 환불 확인 (CALL_CANCELLED 후 refresh) | `amount` | 환불 정합성 |
| `customer_driver_assigned_dialog_dismissed` | 기사 배정 팝업 닫기 | `call_id`, `duration_ms` | 사용자 반응 시간 |

### 4.2 구현 (FcmService)

```dart
  /// 의제 11 — 손님앱 이벤트 로깅
  void _logCustomerEvent(String? type, String? callId) {
    if (type == null) return;
    final eventName = switch (type) {
      typeCallReceived => 'customer_call_received_received',
      typeDriverAssigned => 'customer_driver_assigned_received',
      typeRideCompleted => 'customer_ride_completed_received',
      typeCallCancelled => 'customer_call_cancelled_received',
      typeCallStatusUpdate => 'customer_call_status_changed',
      _ => null,
    };
    if (eventName == null) return;

    _analytics.logEvent(
      name: eventName,
      parameters: {
        'call_id': callId ?? '',
        'platform': Platform.isIOS ? 'ios' : 'android',
        'received_at_ms': DateTime.now().millisecondsSinceEpoch,
      },
    );
  }
```

### 4.3 Time-To-Assign 계산

**정의**: CALL_RECEIVED 수신부터 DRIVER_ASSIGNED 수신까지의 시간. 손님 입장 "배차 대기 시간".

**Firestore 기반** (정확):
- `calls/{callId}.createdAt` (WAITING 생성 시각)
- `calls/{callId}.assignedAt` (ASSIGNED 전이 시각)
- TTA = `assignedAt - createdAt`

**클라이언트 보조**:
```dart
// DRIVER_ASSIGNED 수신 시
final callReceivedMs = _prefs.getInt('customer_call_received_at_$callId');
final ttaMs = callReceivedMs != null
    ? DateTime.now().millisecondsSinceEpoch - callReceivedMs
    : null;

await _analytics.logEvent(
  name: 'customer_driver_assigned_received',
  parameters: {
    'call_id': callId,
    'platform': Platform.isIOS ? 'ios' : 'android',
    if (ttaMs != null) 'time_to_assign_ms': ttaMs,
  },
);
```

### 4.4 손님앱 특유 지표 — 포인트 환불 정합성

의제 11 추가 측정 항목 (손님앱 전용):

- **포인트 환불 성공률**: CALL_CANCELLED 수신 수 대비 `customer_points_refund_observed` 수 비율
- **목표**: 99%+ (CF가 포인트 환불 처리 → FCM으로 알림 → 손님앱 refresh 후 금액 확인)
- **임계치**: 95% 미만 시 CF `onCallCancelled` 포인트 환불 트랜잭션 검증

### 4.5 Crashlytics 배경 isolate 통합 (§5 참조)

손님앱 BG handler는 **최소 작업**만 수행 (배너 표시는 OS 자동). Crashlytics 기록은 기사앱과 동일 패턴:

```dart
@pragma('vm:entry-point')
Future<void> customerFirebaseMessagingBackgroundHandler(RemoteMessage message) async {
  try {
    await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
    // 손님앱 BG는 단순 Analytics 로깅만 (CallNotifier UI 갱신 불가)
    await FirebaseAnalytics.instance.logEvent(
      name: 'customer_bg_fcm_received',
      parameters: {
        'type': message.data['type'] ?? '',
        'call_id': message.data['callId'] ?? '',
        'platform': Platform.isIOS ? 'ios' : 'android',
      },
    );
  } catch (e, stack) {
    await FirebaseCrashlytics.instance.recordError(
      e,
      stack,
      reason: 'Customer BG isolate (type=${message.data['type']})',
      fatal: false,
    );
  }
}
```

---

## 5. 백그라운드 Isolate — 손님앱 단순화

### 5.1 기사앱과의 차이

| 측면 | 기사앱 | 손님앱 |
|------|--------|--------|
| 주 사용 상태 | 포그라운드 대기 (배차 수락 즉시 반응) | 포그라운드 이용 (콜 요청 후 포그라운드 대기) |
| BG 작업 | Delivery ACK (acknowledgeNotification CF) 필수 | **없음** — 손님 측 ACK 불필요 (기사 수락 시점이 도달 보장) |
| 30초 제한 위험 | 중간 (ACK CF 호출) | **낮음** (단순 Analytics만) |
| Crashlytics | 복잡 (비동기 체인) | 단순 (logEvent 1회) |

### 5.2 Kotlin 원본 분석 (MyFirebaseMessagingService.kt)

Kotlin은 **모든 타입을 Broadcast 전송** → MainActivity에서 수신 후 처리. 손님앱 BG에서는 **Broadcast 전달만** 하고 실제 UI 처리는 MainActivity 재개 시 이루어짐.

Flutter도 동일 패턴: BG isolate는 **간단한 로깅**만, UI 전환은 사용자가 앱을 열 때 `onMessageOpenedApp` 또는 `getInitialMessage`로 처리.

### 5.3 허용 작업 매트릭스

| 작업 | 손님앱 BG isolate |
|------|-------------------|
| Firebase init | ✅ 필수 |
| Analytics logEvent | ✅ 1~2회 |
| Crashlytics recordError | ✅ try/catch |
| SharedPreferences 갱신 | ✅ 단일 값 (예: 마지막 FCM 수신 시각) |
| Firestore read | ⚠️ 피하기 (네트워크 지연) |
| Firestore write | ⚠️ 피하기 (사용자 데이터 변경은 FG에서) |
| PointNotifier refresh | ❌ 불가 (ProviderScope 없음) |
| Dialog/SnackBar | ❌ 불가 (UI context 없음) |

### 5.4 iOS 30초 제한 주의

iOS는 BG isolate 실행이 **30초 이내** 완료되지 않으면 강제 종료 (OS). Firebase init(~1~2초) + Analytics logEvent(~500ms) = **최대 3초** 여유 있음. 현재 설계는 안전.

---

## 6. CallNotifier ↔ PointNotifier 교차 의존

### 6.1 PLAN.md §MainViewModel 995 LOC 분해 재확인

**4 Notifier 분해** (PLAN.md 결정):
- `CallNotifier`: 콜 생명주기 + FCM 콜 4종 (CALL_RECEIVED/DRIVER_ASSIGNED/RIDE_COMPLETED/CALL_CANCELLED/call_status_update)
- `PointNotifier`: 포인트 조회 + 사용 + 환불 반영
- `ProfileNotifier`: 프로필 CRUD + 사무실 정보
- `AuthNotifier`: Phone Auth + FCM 토큰 등록

**교차 의존 3지점** (PLAN.md 인용):
1. 콜 취소 → 포인트 재조회 (본 문서 §2.5 CALL_CANCELLED 핸들러)
2. 운행 완료 → 포인트 적립 팝업 + refresh (§2.5 RIDE_COMPLETED 핸들러)
3. 콜 요청 → 포인트 사용 → 실패 시 환불 (CallNotifier.requestCall 내부 — 본 FCM.md 범위 외)

### 6.2 구현 원칙 (Riverpod 패턴)

```dart
// CallNotifier가 PointNotifier 직접 호출
await _ref.read(pointNotifierProvider.notifier).refresh();
```

**금지 사항** (PLAN.md "공통 이벤트 버스 도입 금지"):
- ❌ `EventBus`, `RxDart Subject`, `StreamController` 전역 이벤트 버스
- ❌ PointNotifier가 CallNotifier 상태 watch → 반대 방향 의존 (순환 위험)

**권장 패턴**:
- ✅ CallNotifier의 FCM 핸들러에서 `_ref.read(pointNotifierProvider.notifier).refresh()` 직접 호출
- ✅ PointNotifier는 Firestore `customerPoints/{phone}` 리스너 유지 (실시간 갱신)
- ✅ CallNotifier refresh 호출 → PointNotifier가 Firestore 재조회 → 상태 갱신 → UI 리빌드

### 6.3 RIDE_COMPLETED 포인트 적립 Race Condition

**시나리오**:
1. 기사가 `completeRide()` 버튼 탭 → Firestore `calls/{callId}.status = COMPLETED` 쓰기
2. CF `onCallCompleted` 트리거 → 포인트 적립 Firestore 쓰기 + FCM RIDE_COMPLETED 전송
3. 손님앱이 FCM 수신 → `PointNotifier.refresh()` 호출
4. **Race**: CF 포인트 적립 Firestore write **완료 전** 손님앱 refresh 호출 가능성

**완화**:
- CF 측에서 포인트 적립 Firestore write **완료 후** FCM 전송 순서 보장 (`functions/src/index.ts` `onCallCompleted` 코드 검증 필요)
- 손님앱 측: `refresh()` 호출 시 **500ms 지연** 추가 (Race 안전 마진)

```dart
Future<void> _handleRideCompleted(Map<String, dynamic> data) async {
  // ... state 갱신 ...

  // Race condition 완화: CF 포인트 적립 write 완료 대기
  await Future.delayed(const Duration(milliseconds: 500));
  await _ref.read(pointNotifierProvider.notifier).refresh();
}
```

**대안** (더 안전): `PointNotifier`가 **실시간 리스너**로 `customerPoints/{phone}` 구독 → CF write 즉시 자동 갱신 → 손님앱 refresh 호출 불필요.

### 6.4 의제 11 `acceptanceEvents` 기사앱 전용

기사앱 FCM.md §5에서 언급한 `acceptanceEvents` CF 컬렉션은 **기사 수락률 측정 전용**. 손님앱은 **별도 측정** (§4.1 `customer_*` 네임스페이스).

Firestore 보안 규칙에서 `acceptanceEvents/`는 admin 전용 읽기, `customer_*` Analytics 이벤트는 Firebase 자체 집계 (Firestore 컬렉션 없음).

---

## 7. 테스트 시나리오

### 7.1 Firebase Emulator (`functions/scripts/` 활용)

```bash
# Functions Emulator
cd functions && npm run serve

# 손님앱 테스트 콜 시뮬레이션
node functions/scripts/create-test-call.js <customerPhone> <officeId>

# 실기기에서 FCM 수신 확인 (에뮬레이터는 FCM 송신 불가)
```

### 7.2 5종 수신 체크리스트

| 타입 | 포그라운드 iOS | 백그라운드 iOS | Terminated iOS | 포그라운드 Android | 백그라운드 Android |
|------|----------------|----------------|----------------|---------------------|---------------------|
| `CALL_RECEIVED` | SnackBar + 상태 갱신 | 배너 | 배너 | SnackBar | 배너 |
| `DRIVER_ASSIGNED` | **Dialog 팝업** + 진동 + 벨소리 | **Time Sensitive 배너** | **Time Sensitive 배너** | Dialog + 진동 | 배너 + Activity 기동 |
| `RIDE_COMPLETED` | **포인트 적립 Dialog** | 배너 | 배너 | Dialog | 배너 |
| `CALL_CANCELLED` | Dialog 닫기 + SnackBar | **Time Sensitive 배너** | **Time Sensitive 배너** | Dialog 닫기 + SnackBar | 배너 |
| `call_status_update` | 상태 배너 | 배너 | 배너 | 상태 배너 | 배너 |

### 7.3 시나리오 상세

**시나리오 1: 콜 요청 후 배차 (성공 흐름)**
1. 손님이 "콜 요청" 탭 → `CallNotifier.requestCall()` → Firestore `calls/{}` WAITING 쓰기
2. CF `onCallCreated` → FCM `CALL_RECEIVED` 전송
3. 손님앱 FG 수신 → SnackBar "콜이 접수되었습니다"
4. 기사 배차됨 → CF `onCallAssigned` → FCM `DRIVER_ASSIGNED` 전송
5. 손님앱 FG → Dialog "김기사님 (010-xxxx-xxxx, 12가3456)" 표시 + 진동/벨소리
6. 기사 수락 → CF `onCallStatusChanged` → FCM `call_status_update` status=ACCEPTED
7. 손님앱 → 상태 배너 "기사 도착 중" 표시
8. 기사 운행 시작 → FCM status=IN_PROGRESS → "운행 중"
9. 기사 운행 완료 → CF `onCallCompleted` → FCM `RIDE_COMPLETED`
10. 손님앱 FG → Dialog "요금 30000원 · 포인트 1500 적립" + PointNotifier refresh

**시나리오 2: 기사 배정 후 손님 취소**
1. DRIVER_ASSIGNED 수신 후 손님이 "취소" 탭
2. Firestore `calls/{}.status = CANCELLED_BY_CUSTOMER` 쓰기
3. CF `onCallCancelled` → 포인트 환불 + FCM `CALL_CANCELLED` 전송
4. 손님앱 FG → Dialog 닫기 + SnackBar "콜이 취소되었습니다"
5. PointNotifier.refresh() → Firestore `customerPoints/{phone}` 최신 잔액 조회 → 환불 확인

**시나리오 3: 기사 대면 취소** (의제 CLAUDE.md 3/24 HOLD → CANCELLED_BY_DRIVER)
1. ACCEPTED/IN_PROGRESS 상태에서 기사가 `cancelTrip()` 호출
2. CF `onCallCancelledByDriver` → FCM `CALL_CANCELLED` (cancelReason="운행취소") 전송
3. 손님앱 Dialog 닫기 + SnackBar
4. 포인트 환불 확인

**시나리오 4: iOS Time Sensitive (Focus 모드 우회)**
1. 손님 iPhone 15, iOS 17, Focus "수면" 모드 활성
2. DRIVER_ASSIGNED 수신 → `interruption-level: time-sensitive` 덕분에 Focus 뚫고 배너 표시
3. CALL_CANCELLED도 동일
4. CALL_RECEIVED/RIDE_COMPLETED는 Focus에 막혀 배너 표시 안 됨 (의도된 동작, active level)

### 7.4 포인트 환불 정합성 검증

1. 콜 요청 시 포인트 1000 사용 (잔액 5000 → 4000)
2. ASSIGNED 상태 → 손님 취소
3. CF 포인트 환불 (4000 → 5000)
4. FCM CALL_CANCELLED 수신
5. 손님앱 refresh → 잔액 5000 확인 → `customer_points_refund_observed` 이벤트 로그
6. Analytics 대시보드 → 환불 성공률 99%+ 검증

---

## 8. 위험 신호 + 완화

| 리스크 | 완화 |
|-------|------|
| CF apns 블록 누락 → iOS Terminated FCM 무응답 (의제 4 공통) | 의제 4 CF 28곳+ audit. 손님앱 전송 CF 5개 모두 포함 |
| CALL_RECEIVED / DRIVER_ASSIGNED / 기타에 Time Sensitive 과도 부여 | §2.2 매트릭스대로 2종만 timeSensitive, 3종은 active. 과도 사용 시 Apple 심사 지적 가능 |
| 포인트 적립 타이밍 race (RIDE_COMPLETED 수신 vs CF 포인트 write) | §6.3 완화: 500ms 지연 또는 PointNotifier 실시간 리스너 |
| 포인트 환불 지연 (CALL_CANCELLED 수신했으나 잔액 변경 안 됨) | `customer_points_refund_observed` 이벤트로 95% 미만 시 CF 트랜잭션 검증 |
| 손님앱 사용자가 FCM 권한 거부 | 권한 거부 시 `fcm_permission_denied` 로깅 + 설정 유도 다이얼로그 + 포그라운드에서는 Firestore 리스너 fallback |
| DRIVER_ASSIGNED 팝업 중복 표시 (FG + BG 전환 시) | `showDriverAssignedDialog: true` state 플래그로 단일 표시 보장 + 팝업 닫기 시 false로 리셋 |
| 손님앱 백그라운드 isolate UI 접근 시도 (잘못된 구현) | §5 허용 작업 매트릭스 엄수. Dialog/SnackBar 불가 — `getInitialMessage` / `onMessageOpenedApp`에서 처리 |
| Phone Auth 완료 전 FCM 초기화 호출 → phone이 null → 저장 실패 | `initialize(phoneNumber:)` 호출 순서 엄수: Phone Auth → 프로필 설정 → FcmService.initialize |
| `customerInfo/{phone}` 문서 미존재 + `.set(..., merge: true)` 사용 | `.update()` 대신 `merge: true` 사용 (§1.4) → 자동 생성 |
| FCM 토큰 경합 (같은 phone 다른 기기 동시 로그인) | 나중 로그인 기기 토큰이 덮어씀 → 이전 기기 FCM 수신 불가. 1 phone 1 기기 정책 명시 |
| Kotlin 이관 후 기존 `customerInfo/{phone}.fcmToken` 자동 갱신 타이밍 | Flutter 첫 실행 시 `onTokenRefresh`가 새 토큰으로 자동 덮어씀. 큰 문제 없음 |
| call_status_update 상태 파싱 오류 (예: 예상 못한 status 값) | `_handleCallStatusUpdate`에서 known status만 처리, unknown은 로그 + Crashlytics recordError |

---

## 9. Phase 1 신규 파일 체크리스트

| 파일 | 작업 | 비고 |
|------|------|------|
| `customer_app_flutter/pubspec.yaml` | 신규 | §0 의존성 사양 |
| `customer_app_flutter/lib/main.dart` | 신규 | Crashlytics + BG handler 등록 |
| `customer_app_flutter/lib/core/constants/app_constants.dart` | 신규 | customerInfo 컬렉션 + 5 FCM 타입 |
| `customer_app_flutter/lib/services/fcm_service.dart` | 신규 | 5종 핸들러 + Time Sensitive 차등 |
| `customer_app_flutter/lib/features/call/notifiers/call_notifier.dart` | 신규 | MainViewModel 995 LOC 분해 일부 |
| `customer_app_flutter/lib/features/points/notifiers/point_notifier.dart` | 신규 | 포인트 refresh + 실시간 리스너 |
| `customer_app_flutter/ios/Runner/Info.plist` | 신규 | UIBackgroundModes + Permission |
| `customer_app_flutter/ios/Runner/Runner.entitlements` | 신규 | Time Sensitive + aps-environment |
| `customer_app_flutter/ios/Runner/PrivacyInfo.xcprivacy` | 신규 | 의제 6 Privacy Manifest |
| `customer_app_flutter/ios/Podfile` | 신규 | iOS 15.0 + permission_handler 매크로 |
| `customer_app_flutter/android/app/src/main/AndroidManifest.xml` | 신규 | POST_NOTIFICATIONS + VIBRATE |
| `functions/src/index.ts` (손님앱 분기) | 수정 | 5 CF apns 블록 추가 (의제 4) |

---

## 10. 참조

- `flutter/driver_app/MVP/FCM.md` — 기사앱 자매 문서 (공통 Time Sensitive / apns / ACK 패턴)
- `flutter/customer_app/PLAN.md` — 손님앱 Phase 1 범위 + 4 Notifier 분해 결정
- `flutter/customer_app/MVP/ATTRIBUTION.md` — Phone Auth / fcmToken 재발급 §4 정합
- `flutter/WORKING_DOC.md` §5 의제 4, 5, 7, 11 결정 전문
- `customer_app/app/src/main/java/com/designated/customer/service/MyFirebaseMessagingService.kt:22-122` — Kotlin 원본 (5 FCM 타입 + 3 채널)
- `customer_app/app/src/main/java/com/designated/customer/ui/main/MainViewModel.kt:191,278-353,416-501` — Kotlin 포인트 교차 의존 로직
- `functions/src/index.ts` — 손님앱 FCM 송신 CF (CALL_RECEIVED/DRIVER_ASSIGNED/RIDE_COMPLETED/CALL_CANCELLED/call_status_update)
- Apple Time Sensitive Notifications: https://developer.apple.com/documentation/usernotifications/unnotificationinterruptionlevel
- Firebase Admin SDK apns: https://firebase.google.com/docs/reference/admin/node/firebase-admin.messaging.apnsconfig
- flutter_local_notifications InterruptionLevel: https://pub.dev/documentation/flutter_local_notifications/latest/flutter_local_notifications/InterruptionLevel.html
