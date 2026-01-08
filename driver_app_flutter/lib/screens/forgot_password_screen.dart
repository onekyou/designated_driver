import 'package:flutter/material.dart';
import 'package:driver_app_flutter/core/theme.dart';

class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('비밀번호 찾기'),
      ),
      body: const Center(
        child: Text(
          'Forgot Password Screen\n\n(구현 예정)',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 18,
            color: AppTheme.textPrimary,
          ),
        ),
      ),
    );
  }
}
