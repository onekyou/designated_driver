import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../notifiers/call_notifier.dart';
import '../state/call_ui_state.dart';
import 'widgets/payment_method_picker.dart';

class AppCallScreen extends ConsumerStatefulWidget {
  const AppCallScreen({super.key});

  @override
  ConsumerState<AppCallScreen> createState() => _AppCallScreenState();
}

class _AppCallScreenState extends ConsumerState<AppCallScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(callNotifierProvider.notifier).setMode(CallSubmitMode.app);
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(callNotifierProvider);
    final notifier = ref.read(callNotifierProvider.notifier);

    ref.listen(callNotifierProvider, (prev, next) {
      if (next.isSubmitted && (prev?.isSubmitted ?? false) == false) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('호출 접수 완료')),
        );
        notifier.reset();
        if (context.mounted) context.go('/');
      }
      if (next.error != null && next.error != prev?.error) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(next.error!)),
        );
      }
    });

    return Scaffold(
      appBar: AppBar(title: const Text('앱호출')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextField(
                decoration: const InputDecoration(
                  labelText: '출발지',
                  border: OutlineInputBorder(),
                ),
                onChanged: notifier.updateDeparture,
              ),
              const SizedBox(height: 12),
              TextField(
                decoration: const InputDecoration(
                  labelText: '목적지',
                  border: OutlineInputBorder(),
                ),
                onChanged: notifier.updateDestination,
              ),
              const SizedBox(height: 16),
              PaymentMethodPicker(
                value: state.paymentMethod,
                onChanged: notifier.setPaymentMethod,
              ),
              const Spacer(),
              FilledButton(
                onPressed: state.isLoading ? null : () => notifier.submit(),
                child: state.isLoading
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('호출하기'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
