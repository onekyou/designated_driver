import 'package:flutter/material.dart';
import 'package:driver_app_flutter/core/theme.dart';

class HistorySettlementScreen extends StatefulWidget {
  const HistorySettlementScreen({super.key});

  @override
  State<HistorySettlementScreen> createState() => _HistorySettlementScreenState();
}

class _HistorySettlementScreenState extends State<HistorySettlementScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('운행내역 / 정산'),
      ),
      body: const Center(
        child: Text(
          'History & Settlement Screen\n\n(구현 예정)',
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
