import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:driver_app_flutter/core/routes.dart';
import 'package:driver_app_flutter/core/theme.dart';
import 'package:driver_app_flutter/core/providers.dart';
import 'package:driver_app_flutter/features/auth/presentation/providers/auth_state.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();

  bool _autoLogin = false;
  bool _obscurePassword = true;
  String? _errorMessage;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _handleLogin() {
    // Validation
    if (_emailController.text.trim().isEmpty || _passwordController.text.isEmpty) {
      setState(() {
        _errorMessage = '이메일(또는 전화번호)과 비밀번호를 모두 입력해주세요.';
      });
      return;
    }

    setState(() {
      _errorMessage = null;
    });

    // Call AuthNotifier
    ref.read(authNotifierProvider.notifier).login(
          email: _emailController.text.trim(),
          password: _passwordController.text,
          saveCredentials: _autoLogin,
        );
  }

  @override
  Widget build(BuildContext context) {
    // AuthState 감시
    final authState = ref.watch(authNotifierProvider);

    // AuthState 변화에 따른 처리
    ref.listen<AuthState>(authNotifierProvider, (previous, next) {
      next.when(
        initial: () {},
        loading: () {},
        authenticated: (session) {
          // 로그인 성공 - 홈으로 이동
          Navigator.of(context).pushReplacementNamed(AppRoutes.home);

          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('로그인 되었습니다.'),
              duration: Duration(seconds: 2),
            ),
          );
        },
        unauthenticated: () {},
        error: (message) {
          // 에러 메시지 표시
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(message),
              backgroundColor: Colors.red,
              duration: const Duration(seconds: 3),
            ),
          );
        },
      );
    });

    final isLoading = authState.maybeWhen(
      loading: () => true,
      orElse: () => false,
    );

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Center(
            child: SingleChildScrollView(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  // 타이틀
                  Text(
                    '기사님 로그인',
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 20),

                  // 이메일 입력
                  TextField(
                    controller: _emailController,
                    decoration: InputDecoration(
                      labelText: '이메일 또는 전화번호',
                      border: const OutlineInputBorder(),
                      enabled: !isLoading,
                      errorText: _errorMessage != null && _emailController.text.trim().isEmpty
                          ? _errorMessage
                          : null,
                    ),
                    keyboardType: TextInputType.emailAddress,
                    textInputAction: TextInputAction.next,
                  ),
                  const SizedBox(height: 12),

                  // 비밀번호 입력
                  TextField(
                    controller: _passwordController,
                    decoration: InputDecoration(
                      labelText: '비밀번호',
                      border: const OutlineInputBorder(),
                      enabled: !isLoading,
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscurePassword ? Icons.visibility_off : Icons.visibility,
                        ),
                        onPressed: () {
                          setState(() {
                            _obscurePassword = !_obscurePassword;
                          });
                        },
                      ),
                      errorText: _errorMessage != null && _passwordController.text.isEmpty
                          ? _errorMessage
                          : null,
                    ),
                    obscureText: _obscurePassword,
                    textInputAction: TextInputAction.done,
                    onSubmitted: (_) => _handleLogin(),
                  ),

                  // 에러 메시지 표시
                  if (_errorMessage != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 8.0, left: 4.0),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          _errorMessage!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                            fontSize: 12,
                          ),
                        ),
                      ),
                    ),

                  // 자동 로그인 체크박스
                  Row(
                    children: [
                      Checkbox(
                        value: _autoLogin,
                        onChanged: !isLoading
                            ? (value) {
                                setState(() {
                                  _autoLogin = value ?? false;
                                });
                              }
                            : null,
                      ),
                      const Text('자동 로그인 (아이디/비번 기억)'),
                    ],
                  ),

                  // 로그인 버튼
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: !isLoading ? _handleLogin : null,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Theme.of(context).colorScheme.onBackground,
                        foregroundColor: Theme.of(context).colorScheme.background,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                      ),
                      child: isLoading
                          ? SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Theme.of(context).colorScheme.background,
                              ),
                            )
                          : const Text('로그인'),
                    ),
                  ),

                  // 비밀번호 찾기 버튼
                  TextButton(
                    onPressed: !isLoading
                        ? () {
                            Navigator.of(context).pushNamed(AppRoutes.forgotPassword);
                          }
                        : null,
                    style: TextButton.styleFrom(
                      foregroundColor: AppTheme.primaryColor,
                    ),
                    child: const Text('비밀번호를 잊으셨나요?'),
                  ),

                  // 회원가입 버튼
                  Padding(
                    padding: const EdgeInsets.only(top: 8.0),
                    child: TextButton(
                      onPressed: !isLoading
                          ? () {
                              Navigator.of(context).pushNamed(AppRoutes.signUp);
                            }
                          : null,
                      child: const Text('아직 계정이 없으신가요? 회원가입 신청'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
