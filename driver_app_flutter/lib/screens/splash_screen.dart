import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:driver_app_flutter/core/routes.dart';
import 'package:driver_app_flutter/core/providers.dart';
import 'package:driver_app_flutter/features/auth/presentation/providers/auth_state.dart';

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> {
  @override
  void initState() {
    super.initState();
    _initializeApp();
  }

  Future<void> _initializeApp() async {
    // 스플래시 화면 최소 표시 시간
    await Future.delayed(const Duration(seconds: 2));

    if (!mounted) return;

    // 자동 로그인 시도
    await ref.read(authNotifierProvider.notifier).autoLogin();
  }

  @override
  Widget build(BuildContext context) {
    // AuthState 감시 및 화면 전환
    ref.listen<AuthState>(authNotifierProvider, (previous, next) {
      next.when(
        initial: () {},
        loading: () {},
        authenticated: (session) {
          // 자동 로그인 성공 - 홈으로 이동
          Navigator.of(context).pushReplacementNamed(AppRoutes.home);
        },
        unauthenticated: () {
          // 자동 로그인 실패 - 로그인 화면으로 이동
          Navigator.of(context).pushReplacementNamed(AppRoutes.login);
        },
        error: (message) {
          // 에러 발생 - 로그인 화면으로 이동
          Navigator.of(context).pushReplacementNamed(AppRoutes.login);
        },
      );
    });

    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.background,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // 앱 로고
            Icon(
              Icons.local_taxi,
              size: 100,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(height: 24),
            Text(
              '기사앱',
              style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: 48),
            CircularProgressIndicator(
              color: Theme.of(context).colorScheme.primary,
            ),
          ],
        ),
      ),
    );
  }
}
