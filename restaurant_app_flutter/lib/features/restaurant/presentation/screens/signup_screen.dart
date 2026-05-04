import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../notifiers/signup_notifier.dart';

class SignupScreen extends ConsumerStatefulWidget {
  const SignupScreen({super.key});

  @override
  ConsumerState<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends ConsumerState<SignupScreen> {
  final _formKey = GlobalKey<FormState>();

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(signupNotifierProvider);
    final notifier = ref.read(signupNotifierProvider.notifier);

    ref.listen(signupNotifierProvider, (prev, next) {
      if (next.isCompleted && (prev?.isCompleted ?? false) == false) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('가입 완료')),
        );
        context.go('/');
      }
      if (next.error != null && next.error != prev?.error) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(next.error!)),
        );
      }
    });

    return Scaffold(
      appBar: AppBar(title: const Text('식당 가입')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const Text(
                '관리자에게 받은 가입 코드와 사무실 정보를 입력하세요.',
                style: TextStyle(fontSize: 14, color: Colors.grey),
              ),
              const SizedBox(height: 16),
              _input(
                label: '가입 코드 *',
                onChanged: notifier.updateCode,
                textCapitalization: TextCapitalization.characters,
              ),
              _input(label: 'province *', onChanged: notifier.updateProvinceId),
              _input(label: 'city *', onChanged: notifier.updateCityId),
              _input(label: 'office *', onChanged: notifier.updateOfficeId),
              const Divider(height: 32),
              _input(label: '식당명 *', onChanged: notifier.updateName),
              _input(
                label: '식당 전화번호 *',
                keyboardType: TextInputType.phone,
                onChanged: notifier.updatePhone,
              ),
              _input(label: '식당 주소', onChanged: notifier.updateAddress),
              _input(
                label: '단골 콜택시 번호',
                keyboardType: TextInputType.phone,
                onChanged: notifier.updateTaxiPhoneNumber,
              ),
              const SizedBox(height: 24),
              FilledButton(
                onPressed: state.isLoading ? null : () => notifier.submit(),
                child: state.isLoading
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('가입하기'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _input({
    required String label,
    required void Function(String) onChanged,
    TextInputType? keyboardType,
    TextCapitalization textCapitalization = TextCapitalization.none,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextFormField(
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
        ),
        keyboardType: keyboardType,
        textCapitalization: textCapitalization,
        onChanged: onChanged,
      ),
    );
  }
}
