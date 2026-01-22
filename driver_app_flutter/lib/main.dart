import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:driver_app_flutter/core/theme.dart';
import 'package:driver_app_flutter/core/routes.dart';
import 'package:driver_app_flutter/core/di/injection.dart' as di;
import 'firebase_options.dart';

/// FCM Background Message Handler
/// 앱이 백그라운드/종료 상태일 때 메시지 수신
@pragma('vm:entry-point')
Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  debugPrint('[FCM Background] 메시지 수신: ${message.messageId}');
  debugPrint('[FCM Background] 제목: ${message.notification?.title}');
  debugPrint('[FCM Background] 내용: ${message.notification?.body}');
  debugPrint('[FCM Background] 데이터: ${message.data}');
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Firebase 초기화
  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );

  // FCM Background Handler 등록
  FirebaseMessaging.onBackgroundMessage(_firebaseMessagingBackgroundHandler);

  // Dependency Injection 초기화
  await di.initializeDependencies();

  runApp(
    const ProviderScope(
      child: DriverApp(),
    ),
  );
}

class DriverApp extends StatelessWidget {
  const DriverApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '기사앱',
      theme: AppTheme.lightTheme,
      routes: AppRoutes.getRoutes(),
      initialRoute: AppRoutes.splash,
      onGenerateRoute: AppRoutes.onGenerateRoute,
      debugShowCheckedModeBanner: false,
    );
  }
}
