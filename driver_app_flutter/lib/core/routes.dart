import 'package:flutter/material.dart';
import 'package:driver_app_flutter/screens/splash_screen.dart';
import 'package:driver_app_flutter/screens/login_screen.dart';
import 'package:driver_app_flutter/screens/signup_screen.dart';
import 'package:driver_app_flutter/screens/forgot_password_screen.dart';
import 'package:driver_app_flutter/screens/home_screen.dart';
import 'package:driver_app_flutter/screens/history_settlement_screen.dart';
import 'package:driver_app_flutter/screens/call_details_screen.dart';
import 'package:driver_app_flutter/screens/referral_qr_screen.dart';

class AppRoutes {
  // Route names (기존 Android 앱과 동일)
  static const String splash = '/';
  static const String login = '/login';
  static const String signUp = '/signup';
  static const String forgotPassword = '/forgot_password';
  static const String home = '/home';
  static const String historySettlement = '/history_settlement';
  static const String callDetails = '/call_details';
  static const String referralQR = '/referral_qr';

  static Map<String, WidgetBuilder> getRoutes() {
    return {
      splash: (context) => const SplashScreen(),
      login: (context) => const LoginScreen(),
      signUp: (context) => const SignUpScreen(),
      forgotPassword: (context) => const ForgotPasswordScreen(),
      home: (context) => const HomeScreen(),
      historySettlement: (context) => const HistorySettlementScreen(),
      referralQR: (context) => const ReferralQRScreen(),
      // callDetails는 onGenerateRoute에서 처리 (arguments 필요)
    };
  }

  static Route<dynamic>? onGenerateRoute(RouteSettings settings) {
    // Handle dynamic routes with arguments
    switch (settings.name) {
      case callDetails:
        // CallDetails는 arguments를 받아야 함 (callId 등)
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => CallDetailsScreen(callId: args?['callId']),
        );
    }

    // Handle unknown routes
    return MaterialPageRoute(
      builder: (context) => const SplashScreen(),
    );
  }
}
