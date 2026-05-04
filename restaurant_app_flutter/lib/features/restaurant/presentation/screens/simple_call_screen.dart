import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../notifiers/call_notifier.dart';
import '../notifiers/restaurant_streams.dart';
import '../state/call_ui_state.dart';
import 'widgets/payment_method_picker.dart';

class SimpleCallScreen extends ConsumerStatefulWidget {
  const SimpleCallScreen({super.key});

  @override
  ConsumerState<SimpleCallScreen> createState() => _SimpleCallScreenState();
}

class _SimpleCallScreenState extends ConsumerState<SimpleCallScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(callNotifierProvider.notifier).setMode(CallSubmitMode.simple);
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(callNotifierProvider);
    final notifier = ref.read(callNotifierProvider.notifier);
    final restAsync = ref.watch(restaurantStreamProvider);

    ref.listen(callNotifierProvider, (prev, next) {
      if (next.isSubmitted && (prev?.isSubmitted ?? false) == false) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('호출 접수 완료. 사무실 응답을 기다려주세요.')),
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

    final address = restAsync.value?.address ?? '';

    return Scaffold(
      appBar: AppBar(title: const Text('단순호출')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('출발지',
                          style: TextStyle(color: Colors.grey)),
                      const SizedBox(height: 4),
                      Text(
                        address.isEmpty ? '식당 주소 (등록되어 있지 않음)' : address,
                        style: const TextStyle(fontSize: 16),
                      ),
                    ],
                  ),
                ),
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
