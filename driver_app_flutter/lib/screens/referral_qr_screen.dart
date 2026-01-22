import 'package:flutter/material.dart';
import 'package:driver_app_flutter/core/theme.dart';

class ReferralQRScreen extends StatefulWidget {
  const ReferralQRScreen({super.key});

  @override
  State<ReferralQRScreen> createState() => _ReferralQRScreenState();
}

class _ReferralQRScreenState extends State<ReferralQRScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('추천 QR 코드'),
      ),
      body: const Center(
        child: Text(
          'Referral QR Screen\n\n(구현 예정)',
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
