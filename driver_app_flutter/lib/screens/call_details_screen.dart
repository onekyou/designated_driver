import 'package:flutter/material.dart';
import 'package:driver_app_flutter/core/theme.dart';

class CallDetailsScreen extends StatefulWidget {
  final String? callId;

  const CallDetailsScreen({
    super.key,
    this.callId,
  });

  @override
  State<CallDetailsScreen> createState() => _CallDetailsScreenState();
}

class _CallDetailsScreenState extends State<CallDetailsScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('콜 상세정보'),
      ),
      body: Center(
        child: Text(
          'Call Details Screen\n\nCall ID: ${widget.callId ?? "N/A"}\n\n(구현 예정)',
          textAlign: TextAlign.center,
          style: const TextStyle(
            fontSize: 18,
            color: AppTheme.textPrimary,
          ),
        ),
      ),
    );
  }
}
