import 'package:flutter/material.dart';
import 'package:driver_app_flutter/screens/splash_screen.dart';
import 'package:driver_app_flutter/screens/login_screen.dart';
import 'package:driver_app_flutter/screens/signup_screen.dart';
import 'package:driver_app_flutter/screens/forgot_password_screen.dart';
import 'package:driver_app_flutter/screens/home_screen_new.dart';
import 'package:driver_app_flutter/screens/history_settlement_screen.dart';
import 'package:driver_app_flutter/screens/call_details_screen.dart';
import 'package:driver_app_flutter/screens/referral_qr_screen.dart';

class AppRoutes {
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
    };
  }

  static Route<dynamic>? onGenerateRoute(RouteSettings settings) {
    switch (settings.name) {
      case callDetails:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => CallDetailsScreen(callId: args?['callId']),
        );
    }
    return MaterialPageRoute(builder: (_) => const SplashScreen());
  }
}
