# FCM 핸들링 — iOS Time Sensitive + 6종 메시지 + Delivery ACK

> **의제 4 (apns 블록 + data 보존)** + **의제 7 (Time Sensitive MVP + Feature Flag)** + **의제 11 (측정 인프라)** + 의제 5 (fcmToken + fcmTokenPlatform 메타) 통합 실행 사양.
> **자매 문서**: `PLATFORM_CHANNELS.md` (LockScreen/ForegroundService 측), `BUILD.md` (Entitlements/Info.plist/Podfile).
> **원칙**: 서버 CF payload는 `data` 블록 보존 (Android Kotlin 이중 알림 회귀 방지). iOS 배너는 `apns.payload.aps.alert` + 클라이언트 측 `DarwinNotificationDetails.interruptionLevel` 동시 설정.
> **작성일**: 2026-04-16

---

## 0. 의존성 사양 (pubspec.yaml)

`driver_app_flutter/pubspec.yaml` 현재 + Phase 6 추가:

```yaml
environment:
  sdk: ^3.5.4

dependencies:
  flutter:
    sdk: flutter

  # Firebase (현재 유지)
  firebase_core: ^3.6.0
  firebase_auth: ^5.3.1
  cloud_firestore: ^5.4.4
  firebase_messaging: ^15.1.3
  cloud_functions: ^5.3.3
  firebase_database: ^11.3.3

  # Local Notifications (iOS Time Sensitive 지원 — 의제 7)
  flutter_local_notifications: ^21.0.0

  # 의제 11 신규 추가 (Phase 6 Week 0~1)
  firebase_analytics: ^11.3.0
  firebase_crashlytics: ^4.1.0

  # 의제 10 신규 추가
  in_app_update: ^4.2.3
  firebase_remote_config: ^5.1.0
```

> `^` caret 정책: 메이저 고정, 마이너/패치 자동 상승. `flutter pub outdated` 주기 실행하여 iOS 15 호환성 유지.

---

## 1. APNs 등록 + FCM 토큰 저장

### 1.1 APNs Authentication Key (.p8) 발급 절차

Apple Developer Console (Membership 승인 후):

1. **Certificates, Identifiers & Profiles → Keys → `+`**
2. Key Name: `DriverApp APNs Key`
3. Capability 체크: **Apple Push Notifications service (APNs)**
4. `Continue → Register` → **`.p8` 파일 다운로드 (한 번만 가능)**
5. **Key ID** (10자 영숫자) 기록
6. **Team ID** (Membership → Account → Membership Details) 기록

**Firebase Console 등록**:

1. Firebase Console → Project Settings → Cloud Messaging 탭
2. iOS app → **APNs Authentication Key** 섹션
3. `.p8` 업로드 + Key ID + Team ID 입력
4. 저장 → Firebase가 APNs 경유 FCM 전송 가능

> **.p8 vs .p12 선택**: `.p8`은 갱신 불필요 (영구), `.p12`는 1년마다 재발급. **`.p8` 권장**.

### 1.2 초기화 흐름

**파일**: `driver_app_flutter/lib/main.dart` (수정)

```dart
import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:firebase_crashlytics/firebase_crashlytics.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'firebase_options.dart';
import 'services/fcm_service.dart';

/// 백그라운드 메시지 핸들러 — 별도 isolate에서 실행되므로 top-level 필수
/// @pragma('vm:entry-point') — AOT 빌드에서 tree-shaking 방지 (의제 7)
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  // 백그라운드 isolate에서도 Firebase 초기화 필요
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);

  debugPrint('[FCM BG Isolate] type=${message.data['type']} callId=${message.data['callId']}');

  // 백그라운드에서 Firestore/Functions 접근 가능 (단 30초 제한)
  // Delivery ACK (§4) 호출은 가능하나 과도한 로직 금지
  final type = message.data['type'] as String?;
  final notificationId = message.data['notificationId'] as String?;

  if (notificationId != null && type == FcmService.typeCallAssigned) {
    await FcmService.sendDeliveryAckStatic(notificationId);
  }
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

  // 백그라운드 메시지 핸들러 등록 (main 함수 최상단, Runner 시작 전 필수)
  FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);

  runApp(const ProviderScope(child: MyApp()));
}
```

### 1.3 권한 요청 시점 (의제 6)

**정책**: **로그인 성공 직후** 1회 요청. 앱 첫 실행 시 요청하면 거부율 높음 (사용자가 앱 기능 모름).

**파일**: `driver_app_flutter/lib/services/fcm_service.dart` (수정)

```dart
import 'dart:io' show Platform;
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_analytics/firebase_analytics.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter/foundation.dart';
import '../core/constants/app_constants.dart';
import 'incoming_call_service.dart';

class FcmService {
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();
  final FirebaseAnalytics _analytics = FirebaseAnalytics.instance;

  // 의제 4 — 알림 타입 6종 (Kotlin MyFirebaseMessagingService와 1:1 일치)
  static const String typeCallAssigned = 'call_assigned';
  static const String typeCallCancelled = 'call_cancelled';
  static const String typeSettlementFinalized = 'SETTLEMENT_FINALIZED';
  static const String typeSettlementConfirmed = 'SETTLEMENT_CONFIRMED';
  static const String typeSettlementRejected = 'SETTLEMENT_REJECTED';
  static const String typeCarryoverTransferred = 'CARRYOVER_TRANSFERRED';

  void Function(String type, Map<String, dynamic> data)? onMessageReceived;

  Future<void> initialize({
    required String provinceId,
    required String cityId,
    required String officeId,
    required String driverId,
  }) async {
    // 권한 요청 (iOS는 명시 필수, Android 13+는 POST_NOTIFICATIONS 런타임)
    final settings = await _messaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
      provisional: false,
      criticalAlert: false, // 의제 7: Critical Alert은 Apple 별도 승인 필요, 미사용
    );

    if (settings.authorizationStatus != AuthorizationStatus.authorized &&
        settings.authorizationStatus != AuthorizationStatus.provisional) {
      debugPrint('[FCM] 권한 거부됨 (status=${settings.authorizationStatus})');
      await _analytics.logEvent(
        name: 'fcm_permission_denied',
        parameters: {'platform': Platform.isIOS ? 'ios' : 'android'},
      );
      return;
    }

    // iOS APNs 토큰 확인 (FCM 토큰 발급 전제)
    if (Platform.isIOS) {
      final apnsToken = await _messaging.getAPNSToken();
      if (apnsToken == null) {
        debugPrint('[FCM] iOS APNs 토큰 null — Firebase APNs Key 확인 필요');
        return;
      }
      debugPrint('[FCM] iOS APNs 토큰 획득');
    }

    // FCM 토큰 획득 + Firestore 저장
    final token = await _messaging.getToken();
    if (token != null) {
      await _saveTokenToFirestore(
        provinceId: provinceId,
        cityId: cityId,
        officeId: officeId,
        driverId: driverId,
        token: token,
      );
    }

    // 토큰 갱신 리스너
    _messaging.onTokenRefresh.listen((newToken) {
      _saveTokenToFirestore(
        provinceId: provinceId,
        cityId: cityId,
        officeId: officeId,
        driverId: driverId,
        token: newToken,
      );
    });

    await _initializeLocalNotifications();
    debugPrint('[FCM] 초기화 완료 (platform=${Platform.isIOS ? "ios" : "android"})');
  }

  // ... (continued §1.4)
}
```

### 1.4 FCM 토큰 저장 (의제 5 — fcmToken + fcmTokenPlatform 메타)

**현재** (`fcm_service.dart:85-107`): `fcmToken` 단일 필드만 저장.
**의제 5 결정**: `fcmToken` 필드명 유지 + `fcmTokenPlatform` 메타 필드 1줄 추가. 단일 필드 전략 (Admin SDK 자동 분기).

```dart
/// Firestore FCM 토큰 저장 — 의제 5 메타 필드 포함
Future<void> _saveTokenToFirestore({
  required String provinceId,
  required String cityId,
  required String officeId,
  required String driverId,
  required String token,
}) async {
  try {
    final platform = Platform.isIOS ? 'ios' : 'android'; // ← 의제 5 신규

    await FirebaseFirestore.instance
        .collection(AppConstants.collectionProvinces)
        .doc(provinceId)
        .collection(AppConstants.collectionCities)
        .doc(cityId)
        .collection(AppConstants.collectionOffices)
        .doc(officeId)
        .collection(AppConstants.collectionDrivers)
        .doc(driverId)
        .update({
      AppConstants.fieldFcmToken: token,
      'fcmTokenPlatform': platform, // ← 의제 5: 관리자 UI 통계/CF 디버깅용 메타
      'fcmTokenUpdatedAt': FieldValue.serverTimestamp(),
    });

    debugPrint('[FCM] 토큰 저장 성공 (platform=$platform)');
  } on FirebaseException catch (e) {
    debugPrint('[FCM] 토큰 저장 실패: ${e.code} ${e.message}');
    // 문서 미존재 시 (.update 실패) — 로그인 직후 driver 문서 생성 선행 필요
  }
}
```

> **위험 신호**: `.update()`는 문서 미존재 시 실패. 로그인 직후 기사 승인 완료 → `designated_drivers/{uid}` 문서 생성 선행 필수. `AUTH.md` §로그인 시퀀스 참조.

### 1.5 onTokenRefresh 핸들러

**언제 호출되는가**:
- 앱 첫 실행 후 일정 시간
- FCM SDK 갱신 주기 (Google 서버 측 판정)
- 앱 데이터 초기화 후
- 디바이스 복원 후

**의제 9 연관**: Kotlin → Flutter 앱 업데이트 후 **같은 `applicationId`** 면 이전 FCM 토큰이 그대로 유지될 수도 있고 재발급될 수도 있음. `onTokenRefresh`가 알아서 처리하므로 앱 측 대응 불필요.

---

## 2. FCM 6종 메시지 핸들러 매트릭스

### 2.1 Kotlin 원본 참조

**파일**: `driver_app/app/src/main/java/com/designated/driverapp/MyFirebaseMessagingService.kt`

| 라인 | 타입 | 처리 |
|------|------|------|
| 122 | `onMessageReceived` | 진입점 |
| 148 | `call_assigned` | CallAssignedActivity/LockScreen + 시스템 알림 |
| 177 | `call_cancelled` | LocalBroadcast + 시스템 알림 |
| 189 | `SETTLEMENT_FINALIZED` | LocalBroadcast |
| 207 | `SETTLEMENT_CONFIRMED` | LocalBroadcast |
| 218 | `SETTLEMENT_REJECTED` | LocalBroadcast |
| 229 | `CARRYOVER_TRANSFERRED` | LocalBroadcast |
| 388 | `acknowledgeNotification` CF 호출 | Delivery ACK |

### 2.2 Flutter 처리 매트릭스

| 타입 | 포그라운드 (`onMessage`) | 백그라운드 (`onBackgroundMessage`) | 잠금화면 (iOS 15+) |
|------|--------------------------|-----------------------------------|---------------------|
| `call_assigned` | `NewCallPopup` 다이얼로그 + `IncomingCallService` 라우팅 + Delivery ACK + `call_assigned_received` 이벤트 | APNs 배너 자동 표시 + BG isolate에서 ACK 호출 | **Time Sensitive 배너** (Focus 우회) + Android LockScreenActivity |
| `call_cancelled` | Snackbar "고객이 콜을 취소했습니다" + 홈 화면 리셋 | 시스템 알림 배너 (alert) | 일반 배너 |
| `SETTLEMENT_FINALIZED` | Snackbar + 정산 화면 새로고침 | 알림 배너 | 일반 배너 |
| `SETTLEMENT_CONFIRMED` | Snackbar + 상태 갱신 | 알림 배너 | 일반 배너 |
| `SETTLEMENT_REJECTED` | 다이얼로그 "매니저 반려 — 사유: XXX" + 재제출 유도 | 알림 배너 | 일반 배너 |
| `CARRYOVER_TRANSFERRED` | Snackbar + 이월금 리스너 갱신 | 알림 배너 | 일반 배너 |

> 백그라운드 isolate는 UI 접근 불가. `onMessageOpenedApp`에서 사용자가 알림 탭 시 해당 화면으로 이동.

### 2.3 포그라운드 리스너 (`onMessage`)

**파일**: `driver_app_flutter/lib/services/fcm_service.dart` (계속)

```dart
  /// 포그라운드 메시지 리스너
  void listenToForegroundMessages(
    void Function(RemoteMessage) onMessage,
  ) {
    FirebaseMessaging.onMessage.listen((message) {
      debugPrint('[FCM FG] type=${message.data['type']}');

      final type = message.data['type'] as String?;
      final callId = message.data['callId'] as String?;
      final notificationId = message.data['notificationId'] as String?;

      // 의제 11 — 수신 이벤트 로깅 (call_assigned만)
      if (type == typeCallAssigned && callId != null) {
        _logCallAssignedReceived(callId);
      }

      // 의제 4 — Delivery ACK (§4)
      if (type == typeCallAssigned && notificationId != null) {
        sendDeliveryAck(notificationId);
      }

      switch (type) {
        case typeCallAssigned:
          // 포그라운드에서는 시스템 알림 대신 앱 내 NewCallPopup
          onMessage(message); // Notifier가 _showNewCallPopup.value = true
          break;

        case typeCallCancelled:
          // 시스템 알림 + 앱 내 Snackbar (둘 다)
          _showLocalNotification(message);
          onMessage(message);
          break;

        case typeSettlementFinalized:
        case typeSettlementConfirmed:
        case typeSettlementRejected:
        case typeCarryoverTransferred:
          _showLocalNotification(message);
          onMessage(message);
          break;

        default:
          debugPrint('[FCM FG] 알 수 없는 타입: $type');
      }
    });
  }

  /// 알림 탭 시 앱 열림 (백그라운드/Terminated → 앱 포그라운드)
  void listenToMessageOpenedApp(
    void Function(RemoteMessage) onMessageOpened,
  ) {
    // 백그라운드에서 알림 탭
    FirebaseMessaging.onMessageOpenedApp.listen((message) {
      debugPrint('[FCM tap] type=${message.data['type']}');
      onMessageOpened(message);
    });

    // Terminated 상태에서 알림 탭으로 앱 시작 (최초 1회)
    _messaging.getInitialMessage().then((message) {
      if (message != null) {
        debugPrint('[FCM initial] type=${message.data['type']}');
        onMessageOpened(message);
      }
    });
  }
```

### 2.4 로컬 알림 표시 (의제 7 Time Sensitive 통합)

```dart
  Future<void> _initializeLocalNotifications() async {
    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');

    const iosSettings = DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
      // 의제 7 Time Sensitive — 아래 DarwinNotificationDetails에서 interruptionLevel 설정
    );

    await _localNotifications.initialize(
      const InitializationSettings(android: androidSettings, iOS: iosSettings),
      onDidReceiveNotificationResponse: (response) {
        debugPrint('[FCM tap local] payload=${response.payload}');
        // Deep link: payload callId → 콜 상세 화면 라우팅
        if (response.payload != null) {
          onMessageReceived?.call('notification_tap', {'payload': response.payload});
        }
      },
    );

    // Android 채널 명시적 생성 (Android 8+ 필수)
    await _createAndroidChannels();
  }

  Future<void> _createAndroidChannels() async {
    final androidImpl = _localNotifications
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>();

    await androidImpl?.createNotificationChannel(const AndroidNotificationChannel(
      'driver_call_channel',
      '콜 알림',
      description: '배차 및 취소 알림',
      importance: Importance.max, // 잠금화면 표시 + 진동
      playSound: true,
    ));

    await androidImpl?.createNotificationChannel(const AndroidNotificationChannel(
      'driver_settlement_channel',
      '정산 알림',
      description: '정산 확정/승인/반려 알림',
      importance: Importance.high,
    ));

    await androidImpl?.createNotificationChannel(const AndroidNotificationChannel(
      'driver_carryover_channel',
      '이월금 알림',
      description: '이월금 이체 알림',
      importance: Importance.high,
    ));

    await androidImpl?.createNotificationChannel(const AndroidNotificationChannel(
      'driver_default_channel',
      '기타 알림',
      description: '기타 알림',
      importance: Importance.defaultImportance,
    ));
  }

  /// 로컬 알림 표시 (Android 포그라운드, iOS 백그라운드 수동 표시 등)
  Future<void> _showLocalNotification(RemoteMessage message) async {
    final type = message.data['type'] as String?;
    final channelId = _getChannelId(type);
    final channelName = _getChannelName(type);

    final androidDetails = AndroidNotificationDetails(
      channelId,
      channelName,
      channelDescription: channelName,
      importance: Importance.high,
      priority: Priority.high,
      showWhen: true,
      enableVibration: true,
      playSound: true,
    );

    // 의제 7 — iOS Time Sensitive (call_assigned는 timeSensitive, 나머지는 active)
    final iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
      interruptionLevel: type == typeCallAssigned
          ? InterruptionLevel.timeSensitive
          : InterruptionLevel.active,
      categoryIdentifier: type, // deep link 라우팅용
    );

    await _localNotifications.show(
      message.hashCode,
      message.notification?.title ?? _getDefaultTitle(type),
      message.notification?.body ?? '',
      NotificationDetails(android: androidDetails, iOS: iosDetails),
      payload: message.data['callId'] ?? message.data.toString(),
    );
  }

  // 채널 헬퍼 (기존 그대로)
  String _getChannelId(String? type) => switch (type) {
        typeCallAssigned || typeCallCancelled => 'driver_call_channel',
        typeSettlementFinalized ||
        typeSettlementConfirmed ||
        typeSettlementRejected =>
          'driver_settlement_channel',
        typeCarryoverTransferred => 'driver_carryover_channel',
        _ => 'driver_default_channel',
      };

  String _getChannelName(String? type) => switch (type) {
        typeCallAssigned || typeCallCancelled => '콜 알림',
        typeSettlementFinalized ||
        typeSettlementConfirmed ||
        typeSettlementRejected =>
          '정산 알림',
        typeCarryoverTransferred => '이월금 알림',
        _ => '기타 알림',
      };

  String _getDefaultTitle(String? type) => switch (type) {
        typeCallAssigned => '새로운 콜 배정',
        typeCallCancelled => '콜 취소',
        typeSettlementFinalized => '정산 확정',
        typeSettlementConfirmed => '정산 승인',
        typeSettlementRejected => '정산 반려',
        typeCarryoverTransferred => '이월금 이체',
        _ => '새로운 알림',
      };
```

### 2.5 call_assigned 상세 핸들러 (Time Sensitive + LockScreen 라우팅)

**파일**: `lib/features/driver/presentation/notifiers/driver_workflow_notifier.dart` (기존 핸들러 보강)

```dart
/// FCM onMessage 콜백 — DriverWorkflowNotifier에 연결
void handleFcmMessage(RemoteMessage message) async {
  final type = message.data['type'] as String?;
  final callId = message.data['callId'] as String?;

  switch (type) {
    case FcmService.typeCallAssigned:
      if (callId == null) return;

      // Firestore에서 콜 문서 조회
      final callDoc = await _firestore
          .collection('provinces').doc(provinceId)
          .collection('cities').doc(cityId)
          .collection('offices').doc(officeId)
          .collection('calls').doc(callId)
          .get();

      if (!callDoc.exists) return;
      final call = Call.fromFirestore(callDoc);

      // 의제 7 — IncomingCallService 라우팅 (Android LockScreen / iOS Time Sensitive)
      await IncomingCallService.handleCallAssigned(
        callId: callId,
        customerName: call.customerName ?? '고객',
        destination: call.destination ?? '',
        phoneNumber: call.customerPhone ?? '',
      );

      // StateNotifier에 popup 플래그
      state = state.copyWith(newCallPopup: call);
      break;

    case FcmService.typeCallCancelled:
      // Kotlin MyFirebaseMessagingService.kt:177 대응
      state = state.copyWith(
        currentCall: null,
        newCallPopup: null,
        errorMessage: '고객이 콜을 취소했습니다',
      );
      break;

    case FcmService.typeSettlementFinalized:
    case FcmService.typeSettlementConfirmed:
    case FcmService.typeSettlementRejected:
      // 정산 화면에서 처리 (SettlementNotifier 측)
      _ref.read(settlementNotifierProvider.notifier).refresh();
      break;

    case FcmService.typeCarryoverTransferred:
      // carryOverListener가 이미 실시간 갱신 (Phase 3 기구현)
      state = state.copyWith(lastCarryOverEvent: DateTime.now());
      break;
  }
}
```

---

## 3. Time Sensitive Notification (의제 7)

### 3.1 CF Payload 확정 형태 (의제 4 재수록)

**파일**: `functions/src/index.ts:560-573` (Phase 6 Week 2~3 서버 별도 과제)

현재 상태 (apns 블록 부재):

```typescript
const driverPayload = {
  data: {
    callId: callId,
    notificationId: notificationId,
    type: "call_assigned",
    title: "새로운 콜 배정",
    body: "새로운 콜이 배정되었습니다. 즉시 확인해주세요!"
  },
  android: {
    priority: "high" as const,
    ttl: 30000,
  },
  token: driverFcmToken,
};
```

**의제 4 결정 — 수정 후**:

```typescript
const driverPayload = {
  data: {
    // 의제 4 — data 블록 보존 원칙 (최상위 notification 블록 금지)
    callId: callId,
    notificationId: notificationId,
    type: "call_assigned",
    title: "새로운 콜 배정",
    body: "새로운 콜이 배정되었습니다. 즉시 확인해주세요!"
  },
  android: {
    priority: "high" as const,
    ttl: 30000,
    // Android는 기존 onMessageReceived가 data 파싱하여 LockScreenActivity 기동
  },
  // 의제 4 — apns 블록 신규 추가 (iOS Terminated 수신 보장)
  apns: {
    headers: {
      "apns-push-type": "alert",        // iOS 13+ 필수
      "apns-priority": "10",            // 최고 우선순위 (즉시 전달)
      "apns-expiration": String(Math.floor(Date.now() / 1000) + 30), // 30초 TTL
    },
    payload: {
      aps: {
        alert: {
          title: "새로운 콜 배정",
          body: "새로운 콜이 배정되었습니다. 즉시 확인해주세요!",
        },
        sound: "default",
        "content-available": 1,          // Terminated에서도 앱 깨움
        "mutable-content": 1,            // Notification Service Extension 허용 (미래)
        "interruption-level": "time-sensitive", // 의제 7 — Focus 우회 (iOS 15+)
      },
    },
  },
  token: driverFcmToken,
};
```

**핵심 포인트**:
- `data` 블록의 `type`/`callId`/`notificationId`는 **양 플랫폼 공통**이 읽음
- iOS는 `apns.payload.aps.alert`로 시스템 배너 생성 (클라이언트 `onMessage` 통하지 않아도 표시)
- Android는 `data`만 읽고 자체 로직(LockScreenActivity 등)으로 알림 생성
- **이중 알림 방지**: Android는 apns 블록 무시, iOS는 android 블록 무시 (FCM 서버 자동 분기)

### 3.2 클라이언트 측 Time Sensitive 설정

**파일**: `fcm_service.dart:157-160` (§2.4에 이미 포함)

핵심 1줄:

```dart
final iosDetails = DarwinNotificationDetails(
  // ...
  interruptionLevel: type == typeCallAssigned
      ? InterruptionLevel.timeSensitive   // ← 의제 7 핵심
      : InterruptionLevel.active,
  // ...
);
```

**InterruptionLevel 4종** (iOS 15+):
- `passive`: 조용히 알림 (노티 센터만)
- `active`: 일반 배너 (기본값)
- `timeSensitive`: Focus 우회 ← **call_assigned 전용**
- `critical`: Silent 모드도 뚫음 (Apple 별도 승인 필요, 미사용)

### 3.3 iOS Entitlements 요구사항

`BUILD.md §3.2` 참조 완료. 요약:

**파일**: `driver_app_flutter/ios/Runner/Runner.entitlements` (신규)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>com.apple.developer.usernotifications.time-sensitive</key>
  <true/>
  <key>aps-environment</key>
  <string>production</string>
</dict>
</plist>
```

**Apple Developer Console 작업**:
1. App ID → Capabilities → **Time Sensitive Notifications** 체크
2. App ID → Capabilities → **Push Notifications** 체크
3. 새 Provisioning Profile 발급 → Codemagic에 업로드

**Xcode 프로젝트 설정** (Codemagic workflow에서 자동화 또는 Mac 도착 후):
- Signing & Capabilities → `+ Capability` → `Time Sensitive Notifications` 추가
- `Push Notifications` 추가

### 3.4 Info.plist UIBackgroundModes

`BUILD.md §3.1` 참조. 핵심:

```xml
<key>UIBackgroundModes</key>
<array>
  <string>fetch</string>
  <string>remote-notification</string>   <!-- 의제 4 필수 — content-available 수신 -->
  <string>location</string>
</array>
```

**`remote-notification` 없으면**: `content-available: 1` + Terminated 상태에서 앱 깨우기 불가. 현재 `Info.plist:54-59` 이미 존재 확인.

---

## 4. Delivery ACK (`acknowledgeNotification` CF)

### 4.1 목적

Kotlin `MyFirebaseMessagingService.kt:388` 라인에서 `acknowledgeNotification` Callable CF 호출:
- FCM 메시지가 **실제로 클라이언트에 도달했는지** 서버 측에서 확인
- 의제 11 **FCM 도달률** 지표 산출 (CF 발송 수 vs ACK 수신 수)
- Firestore `notifications/{notificationId}` 문서의 `deliveredAt` 필드 업데이트

### 4.2 클라이언트 호출 패턴

**파일**: `fcm_service.dart` (계속)

```dart
  /// Delivery ACK — call_assigned 수신 직후 호출
  Future<void> sendDeliveryAck(String notificationId) async {
    try {
      final callable = FirebaseFunctions.instance.httpsCallable(
        'acknowledgeNotification',
        options: HttpsCallableOptions(
          timeout: const Duration(seconds: 10),
        ),
      );
      await callable.call({'notificationId': notificationId});
      debugPrint('[FCM ACK] $notificationId');
    } on FirebaseFunctionsException catch (e) {
      // 네트워크 오류 시 무시 (재시도 안 함 — 중복 ACK 방지)
      debugPrint('[FCM ACK] 실패: ${e.code} ${e.message}');
    } catch (e) {
      debugPrint('[FCM ACK] 실패: $e');
    }
  }

  /// 백그라운드 isolate용 static 버전
  static Future<void> sendDeliveryAckStatic(String notificationId) async {
    try {
      final callable = FirebaseFunctions.instance.httpsCallable(
        'acknowledgeNotification',
        options: HttpsCallableOptions(timeout: const Duration(seconds: 10)),
      );
      await callable.call({'notificationId': notificationId});
      debugPrint('[FCM BG ACK] $notificationId');
    } catch (e) {
      debugPrint('[FCM BG ACK] 실패: $e');
    }
  }
```

### 4.3 실패 재시도 정책

- **재시도 없음**: 중복 ACK 방지가 더 중요 (CF 비용, 서버 집계 왜곡)
- 네트워크 오류 시 silent fail
- 의제 11 측정에서 "누락된 ACK"는 FCM 도달률 지표 하락 요인으로 간주

---

## 5. 측정 이벤트 로깅 (의제 11)

### 5.1 Firebase Analytics 이벤트 목록

| 이벤트명 | 발생 시점 | 파라미터 | 용도 |
|---------|-----------|----------|------|
| `fcm_permission_denied` | 권한 거부 시 | `platform` | 거부율 추적 |
| `call_assigned_received` | FCM 수신 (FG/BG 공통) | `call_id`, `platform`, `app_state` | 수신률 |
| `call_accepted` | 기사 수락 버튼 탭 | `call_id`, `platform`, `time_to_accept_ms` | 수락률, TTA |
| `call_rejected` | 거절/타임아웃 | `call_id`, `reason` | 거절 사유 분석 |
| `fcm_ack_sent` | Delivery ACK 성공 | `notification_id` | FCM 도달률 |
| `presence_offline` | Presence offline 전환 | `reason`, `background_duration_ms` | 의제 8 OFFLINE 오탐 |

### 5.2 call_assigned_received 로깅 상세

**파일**: `fcm_service.dart` (계속)

```dart
  /// 의제 11 — call_assigned 수신 이벤트 로깅
  Future<void> _logCallAssignedReceived(String callId) async {
    await _analytics.logEvent(
      name: 'call_assigned_received',
      parameters: {
        'call_id': callId,
        'platform': Platform.isIOS ? 'ios' : 'android',
        'app_state': 'foreground', // onMessage 시점
        'received_at_ms': DateTime.now().millisecondsSinceEpoch,
      },
    );
  }
```

**백그라운드 isolate에서는**:

```dart
// main.dart firebaseMessagingBackgroundHandler 내부
await FirebaseAnalytics.instance.logEvent(
  name: 'call_assigned_received',
  parameters: {
    'call_id': message.data['callId'] ?? '',
    'platform': Platform.isIOS ? 'ios' : 'android',
    'app_state': 'background',
    'received_at_ms': DateTime.now().millisecondsSinceEpoch,
  },
);
```

### 5.3 Time-To-Accept 계산

**정의**: `call_accepted.received_at_ms - call_assigned_received.received_at_ms`

**Firestore 서버 타임스탬프 기반 방식이 더 정확**:
- CF `oncallassigned` 실행 시점 = `assigned_at` (Firestore 쓰기)
- 기사 수락 시점 = `accepted_at` (Firestore 트랜잭션)
- TTA = `accepted_at - assigned_at` (서버 시계 기준)

**Flutter 측 이벤트는 보조 지표**: 기사 기기 시계 오차 감안.

**수락 버튼 핸들러** (DriverWorkflowNotifier):

```dart
Future<void> acceptCall(Call call) async {
  final acceptedAt = DateTime.now();
  final ttaMs = call.assignedAt != null
      ? acceptedAt.difference(call.assignedAt!).inMilliseconds
      : 0;

  // Firestore 트랜잭션 (상태 전이)
  await _firestore.runTransaction((tx) async {
    // ... ACCEPTED 전이 ...
  });

  // 의제 11 — 수락 이벤트
  await FirebaseAnalytics.instance.logEvent(
    name: 'call_accepted',
    parameters: {
      'call_id': call.id,
      'platform': Platform.isIOS ? 'ios' : 'android',
      'time_to_accept_ms': ttaMs,
    },
  );
}
```

### 5.4 Crashlytics 배경 isolate 통합

**배경 isolate에서 발생한 예외**는 메인 isolate의 `FlutterError.onError`로 전달되지 않음. 수동 `recordError` 필요:

```dart
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  try {
    await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
    // ... 처리 ...
  } catch (e, stack) {
    // 의제 11 — 백그라운드 isolate 예외를 Crashlytics에 기록
    await FirebaseCrashlytics.instance.recordError(
      e,
      stack,
      reason: 'BG isolate error (type=${message.data['type']})',
      fatal: false, // 백그라운드 예외는 non-fatal
    );
  }
}
```

**iOS 설정** (BUILD.md §2 Podfile 참조):
- Crashlytics는 dSYM 업로드 필요. Codemagic `codemagic.yaml` artifacts에 dSYM 포함.

---

## 6. Feature Flag 라우팅

상세 구현은 **`PLATFORM_CHANNELS.md` §1.5** 참조. 본 문서는 Time Sensitive 측 MVP 경로만 담당.

요약:

```dart
// lib/core/feature_flags.dart (신규)
class FeatureFlags {
  static const bool useCallKitOnIos = false; // Phase 1 MVP
  static const bool enableAnalytics = true;
  static const bool enableCrashlytics = true;
}

// lib/services/incoming_call_service.dart (신규)
class IncomingCallService {
  static Future<void> handleCallAssigned({...}) async {
    if (Platform.isAndroid) {
      return LockScreenService.showLockScreen(...);
    }
    if (Platform.isIOS && FeatureFlags.useCallKitOnIos) {
      // R1: return CallKitService.showIncomingCall(...);
    }
    // iOS 기본 MVP — Time Sensitive는 CF apns 블록 + 클라이언트 DarwinNotificationDetails로 이미 표시됨
    // 이 경로는 클라이언트가 추가 로컬 알림 생성할 필요 없음 (apns.aps.alert가 시스템 배너 생성)
    debugPrint('[IncomingCall] iOS Time Sensitive 경로 — apns 블록에 위임');
  }
}
```

**핵심**: iOS MVP는 CF `apns.payload.aps.alert`가 시스템 배너를 **자동 표시**하므로 클라이언트가 `flutter_local_notifications.show()` 호출 불필요. `onMessage`에서 호출하면 **이중 배너** 발생.

**예외**: 앱 포그라운드 상태에서는 iOS가 시스템 배너를 숨기는 경향 → 이 경우 `NewCallPopup` 다이얼로그(앱 내 UI)로 대체.

---

## 7. 백그라운드 Isolate 핸들러

### 7.1 `@pragma('vm:entry-point')` 필수 이유

- Flutter AOT 빌드 (Release)는 tree-shaking으로 호출되지 않는 top-level 함수 제거
- `FirebaseMessaging.onBackgroundMessage`는 native 측(Android `FirebaseMessagingService` / iOS `didReceiveRemoteNotification`)에서 **새 Dart isolate 시작** → main isolate의 함수를 tree-shaking이 이미 제거
- `@pragma('vm:entry-point')`로 "진입점 함수"임을 컴파일러에 명시 → 빌드에 포함 유지

```dart
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  // ...
}
```

### 7.2 주의사항

| 제약 | 설명 |
|------|------|
| 실행 시간 | iOS 30초, Android 10초 (OS별 상이, 초과 시 강제 종료) |
| Riverpod 접근 불가 | main isolate의 `ProviderScope` 미존재. StateNotifier 갱신 불가 |
| UI 불가 | MaterialApp 컨텍스트 없음. Snackbar/다이얼로그 불가 |
| Firebase 재초기화 | `await Firebase.initializeApp()` 필수 (isolate별 독립 상태) |
| SharedPreferences 가능 | `await SharedPreferences.getInstance()` 동작 |
| Firestore/Functions 가능 | 단 단기 작업만 |

### 7.3 허용되는 작업

1. **Delivery ACK** (`acknowledgeNotification` CF 호출) — §4
2. **Crashlytics 예외 기록** — §5.4
3. **Analytics 이벤트 로깅** — §5.2
4. **SharedPreferences 갱신** (예: 마지막 배차 타임스탬프 캐시)
5. **Firestore 간단 read/write** (단 트랜잭션 금지)

### 7.4 금지되는 작업

1. **StateNotifier 갱신** — ProviderScope 없음
2. **Navigator 호출** — context 없음
3. **Snackbar/Dialog** — UI 없음
4. **장시간 작업** — 30초 초과 시 iOS 강제 종료
5. **로그인 상태 확인 후 분기** — `FirebaseAuth.instance.currentUser`는 가능하나 redirect 불가

---

## 8. 테스트 시나리오

### 8.1 Firebase Emulator (Cloud Functions 로컬)

`.agent-teams/emulator/PLAYBOOK.md` 참조. 핵심:

```bash
# 1. Functions Emulator 시작
cd functions
npm run serve

# 2. Flutter 앱이 Emulator 사용하도록 설정
# main.dart에 dev 환경 감지 시:
if (kDebugMode) {
  await FirebaseFunctions.instance.useFunctionsEmulator('10.0.2.2', 5001); // Android 에뮬
  // iOS 시뮬레이터는 'localhost' 사용
}

# 3. 테스트 콜 배차 시뮬레이션
node functions/scripts/assign-call-simulate.js <driverId> <callId>

# 4. 실기기 FCM 수신 확인 (에뮬레이터는 FCM 송신 불가, 실제 Firebase 사용)
```

**참고**: FCM 송신은 Emulator 미지원 → 실제 Firebase 프로젝트 + 실기기 조합만 가능.

### 8.2 플랫폼별 시나리오

**Android 실기기 (Galaxy S22 R5CT41TJZFP)**:
1. 앱 종료 → Manager 앱에서 배차 → **LockScreenActivity 자동 기동** + 3초 반복 알림음
2. 앱 백그라운드 + 잠금 → 동일 LockScreenActivity
3. 앱 포그라운드 → `NewCallPopup` 다이얼로그
4. call_cancelled 수신 → Snackbar "고객이 콜을 취소했습니다" + 홈 리셋
5. 정산 3종 + 이월금 수신 → 알림 채널별 배너

**iOS 실기기 (Mac 도착 후 TestFlight)**:
1. 앱 종료 → 배차 → **Time Sensitive 배너** (Focus 우회) + 사운드 1회
2. 앱 백그라운드 + 잠금 → 동일 배너
3. 앱 포그라운드 → `NewCallPopup` 다이얼로그 (iOS는 시스템 배너 숨김 경향)
4. **LockScreen 풀스크린 UI 부재 확인** (Apple 정책 D)
5. **3초 반복 알림음 부재 확인** (Time Sensitive 한계, R1에서 VoIP push로 해결)
6. Focus 모드 설정 → 배차 → 여전히 배너 뜸 (interruption-level 효과)

### 8.3 FCM 6종 상태별 체크리스트

| 타입 | 포그라운드 iOS | 포그라운드 Android | 백그라운드 iOS | 백그라운드 Android | Terminated iOS | Terminated Android |
|------|-----|-----|-----|-----|-----|-----|
| `call_assigned` | Popup | Popup | Time Sensitive 배너 | LockScreenActivity | Time Sensitive | LockScreenActivity |
| `call_cancelled` | Snackbar + 배너 | Snackbar + 배너 | 배너 | 배너 | 배너 | 배너 |
| `SETTLEMENT_FINALIZED` | Snackbar + 배너 | 동일 | 배너 | 배너 | 배너 | 배너 |
| `SETTLEMENT_CONFIRMED` | 동일 | 동일 | 배너 | 배너 | 배너 | 배너 |
| `SETTLEMENT_REJECTED` | Dialog + 배너 | 동일 | 배너 | 배너 | 배너 | 배너 |
| `CARRYOVER_TRANSFERRED` | Snackbar + 배너 | 동일 | 배너 | 배너 | 배너 | 배너 |

### 8.4 Delivery ACK 검증

1. CF 측 `notifications/{notificationId}` 문서 생성 확인 (assigned 시점)
2. 기사 수신 후 `deliveredAt` 필드 채워짐 확인 (ACK 수신)
3. Firebase Console → Functions 로그에서 `acknowledgeNotification` 호출 확인
4. Analytics 대시보드 → `fcm_ack_sent` 이벤트 집계 확인 (24~48시간 지연)

---

## 9. 위험 신호 + 완화

| 리스크 | 완화 |
|-------|------|
| CF `apns` 블록 누락 → iOS Terminated FCM 무응답 | 의제 4 실행. 전체 CF 28곳+ audit 체크리스트 (Phase 6 Week 2~3) |
| `apns-push-type` 헤더 누락 → iOS 13+ 조용히 drop | §3.1 `"apns-push-type": "alert"` 명시 |
| `interruption-level: time-sensitive` Entitlement 누락 → 일반 배너로 격하 | §3.3 Runner.entitlements + Apple Dev Console Capability |
| 백그라운드 isolate 30초 초과 → iOS 강제 종료 + 데이터 손실 | §7.3 허용 작업만 수행. `acknowledgeNotification` CF timeout 10초 설정 |
| `@pragma('vm:entry-point')` 누락 → Release 빌드에서 BG handler 호출 안됨 | §7.1 함수 선언 직전 주석 필수 |
| Time Sensitive interruption-level Focus 허용 목록 미등록 사용자 → 일반 배너와 동일 | 앱 첫 실행 온보딩: "Focus 설정에서 이 앱을 Time Sensitive 허용 목록에 추가" 안내 |
| Android `notification` 블록 추가 시 onMessageReceived 미호출 (시스템 트레이만) | 의제 4 결정: **최상위 `notification` 블록 금지**. apns 블록은 iOS 전용이므로 Android는 영향 없음 |
| 이중 알림 (iOS apns.aps.alert + 클라이언트 local notification) | `onMessage`에서 iOS는 `_showLocalNotification` 호출 금지 (§6 참조). 배너는 apns가 이미 생성 |
| FCM Admin SDK가 iOS 토큰에 `android` 블록 적용 시도? | 오동작 없음 — FCM 서버가 토큰 플랫폼 자동 분기 (의제 4 근거). android 블록은 iOS 토큰에서 무시 |
| Delivery ACK 중복 호출 → 서버 집계 왜곡 | §4.3 재시도 없음 정책. FG/BG 중복 가능성 낮음 (onMessage는 FG 전용, onBackgroundMessage는 BG 전용) |
| 기사가 권한 거부 → FCM 수신 불가 | §5.1 `fcm_permission_denied` 이벤트 추적 + 설정 유도 다이얼로그 (앱 내 권한 체크) |

---

## 10. Phase 6 신규/수정 파일 체크리스트

| 파일 | 작업 | 의제 |
|------|------|------|
| `lib/main.dart` | Crashlytics 초기화 + `firebaseMessagingBackgroundHandler` 등록 | 11, 4 |
| `lib/services/fcm_service.dart` | `interruptionLevel` 추가, `fcmTokenPlatform` 메타, Analytics 로깅, ACK static | 7, 5, 11 |
| `lib/services/incoming_call_service.dart` | 신규 — 플랫폼 분기 라우터 | 7 |
| `lib/core/feature_flags.dart` | 신규 — CallKit Feature Flag | 7 |
| `lib/features/driver/presentation/notifiers/driver_workflow_notifier.dart` | `handleFcmMessage` 6종 분기 + TTA 계산 | 4, 11 |
| `pubspec.yaml` | `firebase_analytics`, `firebase_crashlytics`, `firebase_remote_config` 추가 | 11, 10 |
| `ios/Runner/Info.plist` | Permission 4종 + `UIBackgroundModes.remote-notification` (현재 이미 있음) | 6, 4 |
| `ios/Runner/Runner.entitlements` | 신규 — Time Sensitive + aps-environment | 7, 4 |
| `ios/Podfile` | iOS 15.0 + permission_handler 매크로 + Crashlytics Run Script | 1, 6, 11 |
| `functions/src/index.ts:560-573` | CF `apns` 블록 추가 (서버 별도 과제) | 4 |
| `functions/src/index.ts` 전체 | 28곳+ audit + apns 블록 일괄 적용 (서버 별도 과제) | 4 |

---

## 참조

- `flutter/driver_app/MVP/OVERVIEW.md` — Phase 6 전체 실행 순서
- `flutter/driver_app/MVP/PLATFORM_CHANNELS.md` — LockScreen/ForegroundService + Entitlements/Privacy Manifest/Podfile 상세
- `flutter/driver_app/MVP/BUILD.md` — Codemagic workflow + Privacy Manifest + dSYM
- `flutter/driver_app/MVP/ENUMS.md` — CallStatus sealed class (Firestore 직렬화 계약)
- `flutter/WORKING_DOC.md` §5 의제 4, 5, 7, 11 결정 전문
- `driver_app_flutter/lib/services/fcm_service.dart` — 현재 구현
- `driver_app_flutter/lib/main.dart` — 현재 진입점
- `driver_app/app/src/main/java/com/designated/driverapp/MyFirebaseMessagingService.kt:122-397` — Kotlin 원본 (계약 출처)
- `functions/src/index.ts:560-573` — CF `oncallassigned` 현 상태 (의제 4 수정 대상)
- Apple APNs 공식: https://developer.apple.com/documentation/usernotifications/sending_notification_requests_to_apns
- Firebase Admin SDK apns: https://firebase.google.com/docs/reference/admin/node/firebase-admin.messaging.apnsconfig
- flutter_local_notifications InterruptionLevel: https://pub.dev/documentation/flutter_local_notifications/latest/flutter_local_notifications/InterruptionLevel.html
- firebase_messaging background handler: https://firebase.flutter.dev/docs/messaging/usage/#background-messages
