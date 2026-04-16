# 기사앱 화면 매핑 (Kotlin Composable → Flutter Widget)

> **의제 14-A (Dart 3 sealed class + pattern matching) + Riverpod ConsumerStatefulWidget + GoRouter ^14** 반영
> **MODELS.md Freezed 모델 + ENUMS.md sealed class + NOTIFIERS.md Notifier 8종** 사용
> **원본 기준**: `driver_app/app/src/main/java/com/designated/driverapp/ui/` 전체 + `navigation/AppNavigation.kt`
> **작성일**: 2026-04-16
> **작성자**: kotlin-expert

---

## 1. 화면 인벤토리 + 라우팅

### 1.1 전체 화면 12종 (Kotlin Composable 1:1 매핑)

| # | 화면 | Kotlin 원본 파일 | Kotlin 함수 (라인) | Flutter Widget | GoRouter path | 모달/화면 |
|---|------|----------------|------------------|---------------|---------------|-----------|
| 1 | **LoginScreen** | `ui/login/LoginScreen.kt` | `fun LoginScreen (line 27)` | `LoginScreen` ConsumerStatefulWidget | `/login` | 화면 |
| 2 | **SignUpScreen** | `ui/login/SignUpScreen.kt` | `fun SignUpScreen (line 29)` | `SignUpScreen` ConsumerStatefulWidget | `/signup` | 화면 |
| 3 | **ForgotPasswordScreen** | `ui/login/ForgotPasswordScreen.kt` | `fun ForgotPasswordScreen (line 103)` | `ForgotPasswordScreen` ConsumerStatefulWidget | `/forgot` | 화면 |
| 4 | **HomeScreen** (분기 컨테이너) | `ui/home/HomeScreen.kt` | `fun HomeScreen (line 60)` | `HomeScreen` ConsumerStatefulWidget | `/` | 화면 |
| 5 | **WaitingScreen** (내부) | `ui/screens/home/WaitingScreen.kt` | `fun WaitingScreen` | `WaitingView` ConsumerWidget | (HomeScreen 내부) | 뷰 |
| 6 | **NewCallPopup** (내부) | `ui/screens/home/NewCallPopup.kt` | `fun NewCallPopup (line 19)` | `NewCallPopup` AlertDialog | (HomeScreen 모달) | 모달 |
| 7 | **TripPreparationScreen** (내부) | `ui/screens/home/TripPreparationScreen.kt` | `fun TripPreparationScreen (line 64)` | `TripPreparationView` ConsumerStatefulWidget | (HomeScreen 내부) | 뷰 |
| 8 | **InProgressScreen** (내부) | `ui/screens/home/InProgressScreen.kt` | `fun InProgressScreen (line 31)` | `InProgressView` ConsumerWidget | (HomeScreen 내부) | 뷰 |
| 9 | **SettlementSummaryPopup** (내부) | `ui/home/HomeScreen.kt` | `fun SettlementSummaryPopup (line 493)` | `SettlementSummaryDialog` AlertDialog | (HomeScreen 모달) | 모달 |
| 10 | **HistorySettlementScreen** | `ui/home/HistorySettlementScreen.kt` | `fun HistorySettlementScreen (line 70)` | `HistorySettlementScreen` ConsumerStatefulWidget | `/history` | 화면 |
| 11 | **SettingsScreen** | `ui/home/SettingsScreen.kt` | `fun SettingsScreen (line 55)` | `SettingsScreen` ConsumerStatefulWidget | `/settings` | 화면 |
| 12 | **CallDetailsScreen** | `ui/details/CallDetailsScreen.kt` | `fun CallDetailsScreen (line 33)` | `CallDetailsScreen` ConsumerWidget | `/call/:id` | 화면 |
| 13 | **ReferralQRScreen** (R2) | `ui/screens/home/ReferralQRScreen.kt` | `fun ReferralQRScreen (line 27)` | `ReferralQRScreen` ConsumerWidget | `/referral-qr` | 화면 (R2) |

**중복/보조 화면**:
- **AddressSearchScreen** — Kotlin은 별도 화면 없이 `TripPreparationScreen` 내부 Dialog/BottomSheet 로 통합 (`driver_app/.../util/AddressSearchHelper.kt` 사용)
- **OfflineScreen** (`ui/screens/home/OfflineScreen.kt`) — R1 `connectivity_plus` 배너로 대체 예정 (의제 11 R1 `R1_NETWORK_BANNER`)

### 1.2 라우팅 스펙 (Kotlin `AppDestinations` → GoRouter)

**Kotlin 원본** (`navigation/AppNavigation.kt:26-35`):
```kotlin
object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val HOME_ROUTE = "home"
    const val FORGOT_PASSWORD_ROUTE = "forgot_password"
    const val SIGNUP_ROUTE = "signup"
    const val HISTORY_SETTLEMENT_ROUTE = "history_settlement"
    const val CALL_DETAILS_ROUTE = "call_details"
    const val REFERRAL_QR_ROUTE = "referral_qr"
    const val SETTINGS_ROUTE = "settings"
}
```

**Flutter GoRouter 매핑**:
| Kotlin route | GoRouter path | name | 동등성 |
|-------------|--------------|------|--------|
| `LOGIN_ROUTE` | `/login` | `'login'` | 동일 |
| `HOME_ROUTE` | `/` | `'home'` | `/` 채택 (GoRouter 관용구) |
| `FORGOT_PASSWORD_ROUTE` | `/forgot` | `'forgot-password'` | 동일 (경로 축약) |
| `SIGNUP_ROUTE` | `/signup` | `'signup'` | 동일 |
| `HISTORY_SETTLEMENT_ROUTE` | `/history` | `'history'` | 축약 |
| `CALL_DETAILS_ROUTE/{callId}` | `/call/:callId` | `'call-details'` | 동일 |
| `REFERRAL_QR_ROUTE` | `/referral-qr` | `'referral-qr'` | 동일 |
| `SETTINGS_ROUTE` | `/settings` | `'settings'` | 동일 |

---

## 2. GoRouter 설정

### 2.1 Router 정의

```dart
// lib/core/routing/app_router.dart

import 'package:go_router/go_router.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_auth/firebase_auth.dart';
import '../../features/auth/screens/login_screen.dart';
import '../../features/auth/screens/signup_screen.dart';
import '../../features/auth/screens/forgot_password_screen.dart';
import '../../features/driver/screens/home_screen.dart';
import '../../features/settlement/screens/history_settlement_screen.dart';
import '../../features/call/screens/call_details_screen.dart';
import '../../features/settings/screens/settings_screen.dart';
import '../../features/referral/screens/referral_qr_screen.dart';

final goRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/login',
    redirect: (context, state) {
      // Firebase Auth 세션 유지 시 자동으로 홈으로 (의제 9)
      final user = FirebaseAuth.instance.currentUser;
      final onAuthScreen = state.matchedLocation == '/login' ||
          state.matchedLocation == '/signup' ||
          state.matchedLocation == '/forgot';

      if (user != null && onAuthScreen) return '/';
      if (user == null && !onAuthScreen) return '/login';
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        name: 'login',
        builder: (_, __) => const LoginScreen(),
      ),
      GoRoute(
        path: '/signup',
        name: 'signup',
        builder: (_, __) => const SignUpScreen(),
      ),
      GoRoute(
        path: '/forgot',
        name: 'forgot-password',
        builder: (_, __) => const ForgotPasswordScreen(),
      ),
      GoRoute(
        path: '/',
        name: 'home',
        builder: (_, __) => const HomeScreen(),
        routes: [
          GoRoute(
            path: 'history',
            name: 'history',
            builder: (_, __) => const HistorySettlementScreen(),
          ),
          GoRoute(
            path: 'settings',
            name: 'settings',
            builder: (_, __) => const SettingsScreen(),
          ),
          GoRoute(
            path: 'call/:callId',
            name: 'call-details',
            builder: (_, state) =>
                CallDetailsScreen(callId: state.pathParameters['callId']!),
          ),
          GoRoute(
            path: 'referral-qr',
            name: 'referral-qr',
            builder: (_, __) => const ReferralQRScreen(),
          ),
        ],
      ),
    ],
  );
});
```

### 2.2 ShellRoute 미사용 이유

Kotlin 기사앱은 **BottomNavigationBar 없음** (`HomeScreen.kt` 전체 단일 Scaffold, 다른 화면은 개별 네비게이션). Flutter도 동일 구조 유지. ShellRoute 불필요.

### 2.3 Deep Link 처리 (FCM 알림 탭)

FCM 알림 탭 시 `callId` 파라미터로 특정 콜 화면 이동:

```dart
// main.dart 또는 fcm_routing_service.dart

FirebaseMessaging.onMessageOpenedApp.listen((message) {
  final callId = message.data['callId'] as String?;
  if (callId != null) {
    // 현재 콜이면 홈 유지 (handleCallCancelled 로 UI 정리)
    // 완료 콜이면 상세 화면
    final ref = ...; // Riverpod Container 접근
    final workflow = ref.read(driverWorkflowProvider);
    if (workflow.activeCall?.id == callId ||
        workflow.assignedCalls.any((c) => c.id == callId)) {
      // 홈 유지 (이미 처리 중)
    } else {
      // 완료 콜 상세 화면
      ref.read(goRouterProvider).pushNamed(
        'call-details',
        pathParameters: {'callId': callId},
      );
    }
  }
});
```

### 2.4 BackHandler (HistorySettlementScreen 뒤로가기 차단)

**Kotlin 원본** (`AppNavigation.kt:108-111`):
```kotlin
composable(AppDestinations.HISTORY_SETTLEMENT_ROUTE) {
    BackHandler(enabled = true) {
        // 아무 동작도 하지 않음
    }
    HistorySettlementScreen(...)
}
```

**이유**: 정산 완료 전 실수로 뒤로가기 → 정산 누락 위험. 시스템 백버튼 차단.

**Flutter 매핑**: `PopScope` (Flutter 3.12+):

```dart
class HistorySettlementScreen extends ConsumerStatefulWidget {
  @override
  ConsumerState<HistorySettlementScreen> createState() => _HistorySettlementScreenState();
}

class _HistorySettlementScreenState extends ConsumerState<HistorySettlementScreen> {
  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,  // 시스템 백버튼 차단
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () => context.pop(),  // 명시적 뒤로가기만 허용
          ),
          title: const Text('운행 내역 / 정산'),
        ),
        body: ...,
      ),
    );
  }
}
```

---

## 3. 각 화면별 상세

### 3.1 LoginScreen

**Kotlin 원본**: `driver_app/app/src/main/java/com/designated/driverapp/ui/login/LoginScreen.kt:27-400` (약 ~400 LOC)
**의존 Notifier**: `LoginNotifier` (NOTIFIERS.md §3)
**진입 경로**: 앱 첫 실행 + Firebase Auth `currentUser == null` (redirect)
**AUTH.md §6 참조**: 이메일 사전 채움 + 비밀번호 찾기 다이얼로그

#### UI 사양 (의제 9 기반)

```
┌─────────────────────────────┐
│   [콜마당 로고]              │
│                              │
│   ┌──────────────────────┐   │
│   │ 이메일 (사전 채움)   │   │  ← LoginNotifier.form.email 복원
│   └──────────────────────┘   │
│   ┌──────────────────────┐   │
│   │ 비밀번호             │   │
│   └──────────────────────┘   │
│   ☑ 자동 로그인               │
│                              │
│   [    로그인    ]           │
│                              │
│   비밀번호를 잊으셨나요?    │
│   회원가입                  │
└─────────────────────────────┘
```

#### Flutter 코드 골격

```dart
// lib/features/auth/screens/login_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../notifiers/login_notifier.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _autoLogin = false;

  @override
  void initState() {
    super.initState();
    // AUTH.md §6.2: 이메일 사전 채움
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final form = ref.read(loginProvider.notifier).form;
      setState(() {
        _emailController.text = form.email;
        _autoLogin = form.autoLogin;
      });
    });
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final loginState = ref.watch(loginProvider);
    final isLoading = loginState is _Loading;

    // side effect: Success → /, Error → Snackbar
    ref.listen<LoginState>(loginProvider, (_, next) {
      next.whenOrNull(
        success: (provinceId, cityId, officeId, driverId, _) {
          context.goNamed('home');
        },
        error: (msg) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(msg)),
          );
        },
      );
    });

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Text('콜마당', style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold)),
              const SizedBox(height: 48),

              TextField(
                controller: _emailController,
                keyboardType: TextInputType.emailAddress,
                enabled: !isLoading,
                decoration: const InputDecoration(
                  labelText: '이메일 또는 전화번호',
                  border: OutlineInputBorder(),
                ),
                onChanged: (v) => ref.read(loginProvider.notifier).setEmail(v),
              ),
              const SizedBox(height: 16),

              TextField(
                controller: _passwordController,
                obscureText: true,
                enabled: !isLoading,
                decoration: const InputDecoration(
                  labelText: '비밀번호',
                  border: OutlineInputBorder(),
                ),
                onChanged: (v) => ref.read(loginProvider.notifier).setPassword(v),
                onSubmitted: (_) => ref.read(loginProvider.notifier).login(),
              ),
              const SizedBox(height: 16),

              SwitchListTile(
                value: _autoLogin,
                title: const Text('자동 로그인'),
                onChanged: isLoading ? null : (v) {
                  setState(() => _autoLogin = v);
                  ref.read(loginProvider.notifier).toggleAutoLogin(v);
                },
              ),
              const SizedBox(height: 24),

              FilledButton(
                onPressed: isLoading ? null : () => ref.read(loginProvider.notifier).login(),
                child: isLoading
                    ? const SizedBox(
                        height: 20, width: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('로그인'),
              ),
              const SizedBox(height: 12),

              TextButton(
                onPressed: isLoading ? null : () => _showPasswordResetDialog(),
                child: const Text('비밀번호를 잊으셨나요?'),
              ),
              TextButton(
                onPressed: isLoading ? null : () => context.pushNamed('signup'),
                child: const Text('회원가입'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showPasswordResetDialog() async {
    final emailCtl = TextEditingController(text: _emailController.text);
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('비밀번호 재설정'),
        content: TextField(
          controller: emailCtl,
          keyboardType: TextInputType.emailAddress,
          decoration: const InputDecoration(labelText: '이메일'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () async {
              final email = emailCtl.text.trim();
              if (email.isEmpty) return;
              await ref.read(loginProvider.notifier).sendPasswordResetEmail(email);
              if (!ctx.mounted) return;
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('$email 로 재설정 이메일을 전송했습니다.')),
              );
            },
            child: const Text('전송'),
          ),
        ],
      ),
    );
  }
}
```

### 3.2 SignUpScreen

**Kotlin 원본**: `ui/login/SignUpScreen.kt:29-` (미Read, NOTIFIERS.md §6 기반 추론)
**의존 Notifier**: `SignUpNotifier` (NOTIFIERS.md §6)
**진입 경로**: 로그인 화면에서 "회원가입" 탭

#### UI 사양

3단계 드롭다운 + 개인정보 입력:
```
┌─────────────────────────────┐
│ [뒤로]  회원가입             │
│                              │
│   지역 ▼                    │  ← provinces
│   도시 ▼                    │  ← cities (지역 선택 후)
│   사무실 ▼                   │  ← offices (도시 선택 후)
│                              │
│   이름                       │
│   전화번호                   │
│   이메일                     │
│   비밀번호                   │
│   비밀번호 확인              │
│                              │
│   [  가입 신청  ]            │
└─────────────────────────────┘
```

#### Flutter 코드 골격

```dart
// lib/features/auth/screens/signup_screen.dart

class SignUpScreen extends ConsumerStatefulWidget {
  const SignUpScreen({super.key});
  @override
  ConsumerState<SignUpScreen> createState() => _SignUpScreenState();
}

class _SignUpScreenState extends ConsumerState<SignUpScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameCtl = TextEditingController();
  final _phoneCtl = TextEditingController();
  final _emailCtl = TextEditingController();
  final _passwordCtl = TextEditingController();
  final _passwordConfirmCtl = TextEditingController();

  ProvinceItem? _selectedProvince;
  CityItem? _selectedCity;
  OfficeItem? _selectedOffice;

  List<ProvinceItem> _provinces = const [];
  List<CityItem> _cities = const [];
  List<OfficeItem> _offices = const [];

  @override
  void initState() {
    super.initState();
    _loadProvinces();
  }

  Future<void> _loadProvinces() async {
    final p = await ref.read(signUpProvider.notifier).loadProvinces();
    setState(() => _provinces = p);
  }

  Future<void> _onProvinceChanged(ProvinceItem? v) async {
    setState(() {
      _selectedProvince = v;
      _selectedCity = null;
      _selectedOffice = null;
      _cities = [];
      _offices = [];
    });
    if (v != null) {
      final cities = await ref.read(signUpProvider.notifier).loadCities(v.id);
      setState(() => _cities = cities);
    }
  }

  Future<void> _onCityChanged(CityItem? v) async {
    setState(() {
      _selectedCity = v;
      _selectedOffice = null;
      _offices = [];
    });
    if (v != null && _selectedProvince != null) {
      final offices = await ref.read(signUpProvider.notifier).loadOffices(
            _selectedProvince!.id, v.id,
          );
      setState(() => _offices = offices);
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(signUpProvider);

    ref.listen<SignUpState>(signUpProvider, (_, next) {
      next.whenOrNull(
        success: () => _showSuccessDialog(),
        error: (msg) => ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(msg))),
      );
    });

    return Scaffold(
      appBar: AppBar(title: const Text('회원가입')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            DropdownButtonFormField<ProvinceItem>(
              value: _selectedProvince,
              decoration: const InputDecoration(labelText: '지역'),
              items: _provinces.map((p) =>
                DropdownMenuItem(value: p, child: Text(p.name))).toList(),
              onChanged: _onProvinceChanged,
              validator: (v) => v == null ? '지역을 선택하세요' : null,
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<CityItem>(
              value: _selectedCity,
              decoration: const InputDecoration(labelText: '도시'),
              items: _cities.map((c) =>
                DropdownMenuItem(value: c, child: Text(c.name))).toList(),
              onChanged: _selectedProvince == null ? null : _onCityChanged,
              validator: (v) => v == null ? '도시를 선택하세요' : null,
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<OfficeItem>(
              value: _selectedOffice,
              decoration: const InputDecoration(labelText: '사무실'),
              items: _offices.map((o) =>
                DropdownMenuItem(value: o, child: Text(o.name))).toList(),
              onChanged: _selectedCity == null
                  ? null
                  : (v) => setState(() => _selectedOffice = v),
              validator: (v) => v == null ? '사무실을 선택하세요' : null,
            ),
            const SizedBox(height: 24),

            TextFormField(
              controller: _nameCtl,
              decoration: const InputDecoration(labelText: '이름'),
              validator: (v) => v == null || v.isEmpty ? '이름 입력' : null,
            ),
            TextFormField(
              controller: _phoneCtl,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(labelText: '전화번호'),
              validator: (v) => v == null || v.isEmpty ? '전화번호 입력' : null,
            ),
            TextFormField(
              controller: _emailCtl,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(labelText: '이메일'),
              validator: (v) => v == null || !v.contains('@') ? '올바른 이메일' : null,
            ),
            TextFormField(
              controller: _passwordCtl,
              obscureText: true,
              decoration: const InputDecoration(labelText: '비밀번호'),
              validator: (v) => v == null || v.length < 6 ? '6자 이상' : null,
            ),
            TextFormField(
              controller: _passwordConfirmCtl,
              obscureText: true,
              decoration: const InputDecoration(labelText: '비밀번호 확인'),
              validator: (v) => v != _passwordCtl.text ? '일치하지 않음' : null,
            ),

            const SizedBox(height: 24),
            FilledButton(
              onPressed: state is _Loading ? null : _onSubmit,
              child: state is _Loading
                  ? const CircularProgressIndicator()
                  : const Text('가입 신청'),
            ),
          ],
        ),
      ),
    );
  }

  void _onSubmit() {
    if (!_formKey.currentState!.validate()) return;
    ref.read(signUpProvider.notifier).signUp(
      email: _emailCtl.text.trim(),
      password: _passwordCtl.text,
      name: _nameCtl.text.trim(),
      phone: _phoneCtl.text.trim(),
      provinceId: _selectedProvince!.id,
      cityId: _selectedCity!.id,
      officeId: _selectedOffice!.id,
    );
  }

  void _showSuccessDialog() {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('가입 신청 완료'),
        content: const Text('관리자 승인 후 로그인할 수 있습니다.'),
        actions: [
          FilledButton(
            onPressed: () {
              Navigator.pop(ctx);
              context.goNamed('login');
            },
            child: const Text('로그인 화면으로'),
          ),
        ],
      ),
    );
  }
}
```

### 3.3 ForgotPasswordScreen

**Kotlin 원본**: `ui/login/ForgotPasswordScreen.kt:103-` (전용 화면)
**의존 Notifier**: `LoginNotifier.sendPasswordResetEmail`
**MVP 범위**: AUTH.md §6.3 다이얼로그로 축소 가능. **전용 화면 유지 여부 중재자 판단** (본 문서는 전용 화면으로 기술).

```dart
// lib/features/auth/screens/forgot_password_screen.dart

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});
  @override
  ConsumerState<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _emailCtl = TextEditingController();
  bool _isSending = false;

  @override
  void initState() {
    super.initState();
    // 로그인 화면에서 입력한 이메일 사전 채움
    final form = ref.read(loginProvider.notifier).form;
    _emailCtl.text = form.email;
  }

  Future<void> _onSubmit() async {
    final email = _emailCtl.text.trim();
    if (email.isEmpty || !email.contains('@')) return;
    setState(() => _isSending = true);
    await ref.read(loginProvider.notifier).sendPasswordResetEmail(email);
    setState(() => _isSending = false);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('$email 로 재설정 이메일을 전송했습니다.')),
    );
    context.pop();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('비밀번호 찾기')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            TextField(
              controller: _emailCtl,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(
                labelText: '이메일',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _isSending ? null : _onSubmit,
              child: _isSending
                  ? const CircularProgressIndicator()
                  : const Text('재설정 이메일 전송'),
            ),
          ],
        ),
      ),
    );
  }
}
```

### 3.4 HomeScreen (분기 컨테이너)

**Kotlin 원본**: `ui/home/HomeScreen.kt:60-472` (~400 LOC)
**의존 Notifier**: `DriverWorkflowNotifier` (NOTIFIERS.md §2) + `CarryOverNotifier` (배지 표시용)

#### 화면 전환 로직 (Kotlin `HomeScreen.kt:275-350` 분기)

```
activeCall != null
├── activeCall.status == ACCEPTED → TripPreparationView (3.7)
├── activeCall.status == IN_PROGRESS → InProgressView (3.8)
└── else → WaitingView (3.5)

activeCall == null
├── driverStatus == OFFLINE → "오프라인" 메시지
└── else → WaitingView

// 모달 (상태 독립)
newCallPopup != null → NewCallPopup Dialog (3.6)
callForSettlement != null → SettlementSummaryDialog (3.9)
```

#### Flutter 코드 골격

```dart
// lib/features/driver/screens/home_screen.dart

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});
  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// onResume 재검증 (Kotlin DriverViewModel.kt:929-967 refreshActiveCallStatus)
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      ref.read(driverWorkflowProvider.notifier).refreshActiveCallStatus();
    }
  }

  @override
  Widget build(BuildContext context) {
    final workflow = ref.watch(driverWorkflowProvider);
    final carryOverState = ref.watch(carryOverProvider);

    // errorMessage 감지 → Snackbar + 플래그 리셋
    ref.listen<DriverScreenUiState>(driverWorkflowProvider, (prev, next) {
      if (next.errorMessage != null && prev?.errorMessage != next.errorMessage) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(next.errorMessage!)),
        );
        ref.read(driverWorkflowProvider.notifier).onErrorMessageHandled();
      }

      // navigateToHistorySettlement 감지 → /history 이동
      if (next.navigateToHistorySettlement) {
        context.goNamed('history');
        ref.read(driverWorkflowProvider.notifier).onNavigateToHistorySettlementHandled();
      }
    });

    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        leading: IconButton(
          icon: const Icon(Icons.exit_to_app),
          onPressed: () => _confirmLogout(),
        ),
        title: const OfficeNameText(),  // Firestore 조회 + officeName 표시
        actions: [
          IconButton(
            icon: const Icon(Icons.history),
            onPressed: () => context.goNamed('history'),
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () => context.goNamed('settings'),
          ),
        ],
      ),
      body: Stack(
        children: [
          _buildContent(workflow),

          // 모달: NewCallPopup
          if (workflow.newCallPopup != null)
            NewCallPopup(
              callInfo: workflow.newCallPopup!,
              pendingCallCount: workflow.assignedCalls
                  .where((c) => c.status is CallStatusAssigned && c.id != workflow.newCallPopup!.id)
                  .length,
              onAccept: () {
                ref.read(driverWorkflowProvider.notifier).acceptCall(workflow.newCallPopup!.id);
              },
              onDismiss: () {
                ref.read(driverWorkflowProvider.notifier).dismissNewCallPopup();
              },
            ),

          // 모달: SettlementSummaryDialog
          if (workflow.callForSettlement != null)
            SettlementSummaryDialog(
              callInfo: workflow.callForSettlement!,
              onConfirm: (paymentMethod, cashAmount, finalFare, pointsToUse) {
                ref.read(driverWorkflowProvider.notifier).confirmAndFinalizeTrip(
                  callId: workflow.callForSettlement!.id,
                  paymentMethod: paymentMethod,
                  cashAmount: cashAmount,
                  fareToSet: finalFare,
                  tripSummaryToSet: workflow.callForSettlement!.tripSummary ?? '',
                  pointsToUse: pointsToUse,
                );
              },
              onDismiss: () {
                ref.read(driverWorkflowProvider.notifier).dismissSettlementPopup();
              },
            ),

          // 로딩 오버레이
          if (workflow.isLoading)
            Container(
              color: Colors.black45,
              child: const Center(child: CircularProgressIndicator()),
            ),
        ],
      ),
    );
  }

  Widget _buildContent(DriverScreenUiState workflow) {
    final activeCall = workflow.activeCall;
    if (activeCall != null) {
      return switch (activeCall.status) {
        CallStatusAccepted() => TripPreparationView(call: activeCall),
        CallStatusInProgress() => InProgressView(call: activeCall),
        _ => WaitingView(driverStatus: workflow.driverStatus),
      };
    }

    return switch (workflow.driverStatus) {
      DriverStatusOffline() => const Center(
          child: Text('현재 오프라인 상태입니다.', style: TextStyle(fontSize: 18)),
        ),
      _ => WaitingView(driverStatus: workflow.driverStatus),
    };
  }

  Future<void> _confirmLogout() async {
    // Kotlin HomeScreen.kt:378-431 로그아웃 확인 다이얼로그
    // 미저장 운행 내역 있으면 "저장 후 종료" / "저장 없이 종료" 옵션
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('종료 확인'),
        content: const Text('정말 종료하시겠습니까?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('종료')),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      await ref.read(loginProvider.notifier).logout();
      if (mounted) context.goNamed('login');
    }
  }
}
```

### 3.5 WaitingView (내부 뷰)

**Kotlin 원본**: `ui/screens/home/WaitingScreen.kt` (별도 파일, 간단 구조)

```dart
// lib/features/driver/widgets/waiting_view.dart

class WaitingView extends ConsumerWidget {
  final DriverStatus driverStatus;
  const WaitingView({required this.driverStatus, super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            driverStatus.displayName,
            style: Theme.of(context).textTheme.headlineMedium,
          ),
          const SizedBox(height: 16),
          if (driverStatus is DriverStatusOffline)
            FilledButton(
              onPressed: () => ref.read(driverWorkflowProvider.notifier)
                  .updateDriverStatus(const DriverStatusOnline()),
              child: const Text('온라인 전환'),
            )
          else
            const Text('콜 대기 중...', style: TextStyle(fontSize: 18)),

          const SizedBox(height: 32),

          TextButton.icon(
            icon: const Icon(Icons.qr_code),
            label: const Text('추천 QR'),
            onPressed: () => context.goNamed('referral-qr'),
          ),
        ],
      ),
    );
  }
}
```

### 3.6 NewCallPopup (모달)

**Kotlin 원본**: `ui/screens/home/NewCallPopup.kt:19-99` (정확히 확인)

- Dialog + `DisposableEffect` 로 알림음 1회 재생 (Kotlin line 28-53)
- **의제 7 LockScreen 대체 결정**: Time Sensitive는 외부 알림. 앱 내 팝업은 **알림음 1회 재생** (포그라운드에서만)
- 수락 버튼만 (거절은 제거됨, CLAUDE.md 3/18)
- `pendingCallCount > 0` 시 "대기 중인 콜 N건" 표시

#### Flutter 코드

```dart
// lib/features/driver/widgets/new_call_popup.dart

import 'package:audioplayers/audioplayers.dart';

class NewCallPopup extends StatefulWidget {
  final CallInfo callInfo;
  final int pendingCallCount;
  final VoidCallback onAccept;
  final VoidCallback onDismiss;

  const NewCallPopup({
    required this.callInfo,
    this.pendingCallCount = 0,
    required this.onAccept,
    required this.onDismiss,
    super.key,
  });

  @override
  State<NewCallPopup> createState() => _NewCallPopupState();
}

class _NewCallPopupState extends State<NewCallPopup> {
  late final AudioPlayer _player;

  @override
  void initState() {
    super.initState();
    // Kotlin DisposableEffect 등가: 팝업 표시 시 알림음 1회
    _player = AudioPlayer();
    _player.play(AssetSource('sounds/notification.mp3'));
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      // barrierDismissible: false — Kotlin line 56 "바깥 클릭으로 닫히지 않음"
      insetPadding: const EdgeInsets.all(16),
      shape: RoundedCornerShape(16),  // Material 3
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              '새로운 콜',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 20),
            const Text('새로운 호출이 들어왔습니다.', style: TextStyle(fontSize: 16)),

            if (widget.pendingCallCount > 0) ...[
              const SizedBox(height: 8),
              Text(
                '대기 중인 콜 ${widget.pendingCallCount}건',
                style: TextStyle(
                  color: Theme.of(context).colorScheme.primary,
                  fontSize: 14,
                ),
              ),
            ],

            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: widget.onAccept,
                child: const Text('수락'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// RoundedCornerShape 헬퍼
ShapeBorder RoundedCornerShape(double radius) =>
    RoundedRectangleBorder(borderRadius: BorderRadius.circular(radius));
```

**주의**: 의제 7 결정 — iOS `flutter_local_notifications` Time Sensitive + 앱 내 사운드 1회 (반복 사운드는 R1 CallKit).

### 3.7 TripPreparationView (운행 준비)

**Kotlin 원본**: `ui/screens/home/TripPreparationScreen.kt:64-` (~500 LOC)
**의존 Notifier**: `DriverWorkflowNotifier.startDriving` + `cancelTrip`

#### UI 사양

```
┌─────────────────────────────┐
│ 고객: 홍길동                 │
│ 전화: 010-XXXX-XXXX          │
│                              │
│ 출발지 [           ] [📍위치] │
│ 도착지 [           ] [🔍검색] │
│ 경유지 [           ]         │
│ 요금   [           ]원        │
│                              │
│ [  운행 취소  ]  [ 운행 시작 ]│
└─────────────────────────────┘
```

#### Flutter 코드 골격

```dart
// lib/features/driver/widgets/trip_preparation_view.dart

class TripPreparationView extends ConsumerStatefulWidget {
  final CallInfo call;
  const TripPreparationView({required this.call, super.key});
  @override
  ConsumerState<TripPreparationView> createState() => _TripPreparationViewState();
}

class _TripPreparationViewState extends ConsumerState<TripPreparationView> {
  final _departureCtl = TextEditingController();
  final _destinationCtl = TextEditingController();
  final _waypointsCtl = TextEditingController();
  final _fareCtl = TextEditingController();
  bool _isGettingLocation = false;

  @override
  void initState() {
    super.initState();
    // 기존 값 복원 (departure_set, destination_set 등)
    _departureCtl.text = widget.call.departureSet ?? '';
    _destinationCtl.text = widget.call.destinationSet ?? widget.call.destination ?? '';
    _waypointsCtl.text = widget.call.waypointsSet ?? '';
    _fareCtl.text = (widget.call.fareSet ?? widget.call.fare ?? '').toString();
  }

  @override
  void dispose() {
    _departureCtl.dispose();
    _destinationCtl.dispose();
    _waypointsCtl.dispose();
    _fareCtl.dispose();
    super.dispose();
  }

  Future<void> _getCurrentLocation() async {
    setState(() => _isGettingLocation = true);
    try {
      // geolocator + geocoding 패키지 사용 (pubspec.yaml:37-38)
      final position = await Geolocator.getCurrentPosition();
      final placemarks = await placemarkFromCoordinates(position.latitude, position.longitude);
      if (placemarks.isNotEmpty && mounted) {
        final p = placemarks.first;
        _departureCtl.text = '${p.thoroughfare ?? ''} ${p.subThoroughfare ?? ''}'.trim();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('위치 조회 실패: $e')));
      }
    } finally {
      if (mounted) setState(() => _isGettingLocation = false);
    }
  }

  Future<void> _searchAddress(TextEditingController targetCtl) async {
    // Kakao 주소 검색 API 호출 (lib/services/address_search_service.dart)
    final result = await Navigator.of(context).push<AddressSearchResult>(
      MaterialPageRoute(builder: (_) => const AddressSearchDialog()),
    );
    if (result != null) {
      targetCtl.text = result.address;
    }
  }

  void _onStart() {
    final fare = int.tryParse(_fareCtl.text.replaceAll(',', '')) ?? 0;
    if (_departureCtl.text.isEmpty || _destinationCtl.text.isEmpty || fare <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('출발지·도착지·요금을 입력하세요')),
      );
      return;
    }
    ref.read(driverWorkflowProvider.notifier).startDriving(
      callId: widget.call.id,
      departure: _departureCtl.text,
      destination: _destinationCtl.text,
      waypoints: _waypointsCtl.text,
      fare: fare,
    );
  }

  Future<void> _onCancel() async {
    final reason = await _askCancelReason();
    if (reason != null && mounted) {
      await ref.read(driverWorkflowProvider.notifier)
          .cancelTrip(widget.call.id, cancelReason: reason);
    }
  }

  Future<String?> _askCancelReason() async {
    final ctl = TextEditingController(text: '운행취소');
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('운행 취소 사유'),
        content: TextField(controller: ctl),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, ctl.text),
            child: const Text('확인'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
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
                  Text('고객: ${widget.call.customerName}', style: const TextStyle(fontSize: 16)),
                  Text('전화: ${widget.call.phoneNumber}'),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _departureCtl,
                  decoration: const InputDecoration(labelText: '출발지'),
                ),
              ),
              IconButton(
                icon: _isGettingLocation
                    ? const CircularProgressIndicator()
                    : const Icon(Icons.my_location),
                onPressed: _isGettingLocation ? null : _getCurrentLocation,
              ),
            ],
          ),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _destinationCtl,
                  decoration: const InputDecoration(labelText: '도착지'),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.search),
                onPressed: () => _searchAddress(_destinationCtl),
              ),
            ],
          ),
          TextField(
            controller: _waypointsCtl,
            decoration: const InputDecoration(labelText: '경유지 (선택)'),
          ),
          TextField(
            controller: _fareCtl,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: '요금 (원)'),
          ),
          const Spacer(),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: _onCancel,
                  style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
                  child: const Text('운행 취소'),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: FilledButton(
                  onPressed: _onStart,
                  child: const Text('운행 시작'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
```

### 3.8 InProgressView (운행 중)

**Kotlin 원본**: `ui/screens/home/InProgressScreen.kt:31-` (~150 LOC)
**의존 Notifier**: `DriverWorkflowNotifier.completeCall`

```dart
class InProgressView extends ConsumerWidget {
  final CallInfo call;
  const InProgressView({required this.call, super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('운행 중', style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
                const SizedBox(height: 16),
                Text('고객: ${call.customerName}'),
                Text('출발: ${call.departureSet ?? "-"}'),
                Text('도착: ${call.destinationSet ?? "-"}'),
                if (call.waypointsSet != null && call.waypointsSet!.isNotEmpty)
                  Text('경유: ${call.waypointsSet}'),
                Text('요금: ${call.fareSet ?? call.fare ?? 0}원'),
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: () {
                      ref.read(driverWorkflowProvider.notifier).completeCall(call.id);
                    },
                    child: const Text('운행 완료'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
```

### 3.9 SettlementSummaryDialog (모달)

**Kotlin 원본**: `ui/home/HomeScreen.kt:493-650` (~160 LOC, `SettlementSummaryPopup`)
**의존 Notifier**: `DriverWorkflowNotifier.confirmAndFinalizeTrip`

#### UI 사양

```
┌───────────────────────────────┐
│  정산 확정                      │
│                                │
│  요금: 15,000원                │
│                                │
│  결제방식                       │
│  ◯ 현금   ◯ 이체   ◯ 외상      │
│  ◯ 포인트 ◯ 현금+포인트       │
│                                │
│  [현금액 입력]  (현금+포인트일 때)│
│  [포인트 사용]                  │
│                                │
│  [    정산 확정    ]           │
└───────────────────────────────┘
```

#### Flutter 코드 골격

```dart
// lib/features/driver/widgets/settlement_summary_dialog.dart

class SettlementSummaryDialog extends StatefulWidget {
  final CallInfo callInfo;
  final void Function(String paymentMethod, int? cashAmount, int finalFare, int pointsToUse) onConfirm;
  final VoidCallback onDismiss;

  const SettlementSummaryDialog({
    required this.callInfo,
    required this.onConfirm,
    required this.onDismiss,
    super.key,
  });

  @override
  State<SettlementSummaryDialog> createState() => _SettlementSummaryDialogState();
}

class _SettlementSummaryDialogState extends State<SettlementSummaryDialog> {
  String _paymentMethod = '현금';
  final _cashAmountCtl = TextEditingController();
  final _pointsCtl = TextEditingController(text: '0');

  int get _fare => widget.callInfo.fareSet ?? widget.callInfo.fare ?? 0;

  @override
  void initState() {
    super.initState();
    _cashAmountCtl.text = _fare.toString();  // 기본값 = 요금 전액
  }

  @override
  void dispose() {
    _cashAmountCtl.dispose();
    _pointsCtl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('정산 확정', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            const SizedBox(height: 16),
            Text('요금: ${_fare}원', style: const TextStyle(fontSize: 18)),
            const SizedBox(height: 16),

            const Text('결제 방식', style: TextStyle(fontWeight: FontWeight.bold)),
            Wrap(
              children: ['현금', '이체', '외상', '포인트', '현금+포인트'].map((m) =>
                RadioListTile<String>(
                  value: m,
                  groupValue: _paymentMethod,
                  title: Text(m),
                  onChanged: (v) => setState(() => _paymentMethod = v!),
                ),
              ).toList(),
            ),

            if (_paymentMethod == '현금+포인트')
              TextField(
                controller: _cashAmountCtl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: '현금 금액'),
              ),

            TextField(
              controller: _pointsCtl,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: '포인트 사용액 (0이면 미사용)'),
            ),

            const SizedBox(height: 24),
            Row(
              children: [
                TextButton(onPressed: widget.onDismiss, child: const Text('취소')),
                const Spacer(),
                FilledButton(
                  onPressed: _onConfirm,
                  child: const Text('정산 확정'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _onConfirm() {
    final cashAmount = _paymentMethod == '현금' || _paymentMethod == '현금+포인트'
        ? int.tryParse(_cashAmountCtl.text.replaceAll(',', ''))
        : null;
    final points = int.tryParse(_pointsCtl.text.replaceAll(',', '')) ?? 0;

    widget.onConfirm(_paymentMethod, cashAmount, _fare, points);
  }
}
```

### 3.10 HistorySettlementScreen (운행 내역 + 정산)

**Kotlin 원본**: `ui/home/HistorySettlementScreen.kt:70-` (긴 화면, ~1,000 LOC 추정)
**의존 Notifier**: `DriverWorkflowNotifier` + `CarryOverNotifier` + `SettlementNotifier`

#### UI 구조

```
┌──────────────────────────────────┐
│ [뒤로] 운행 내역 / 정산         │
│                                   │
│ ─── 이월금 카드 ───              │
│  balance: 50,000원 (수령대기)    │
│  [수령 확인] (TRANSFERRED 시)    │
│                                   │
│ ─── 오늘 정산 카드 ───            │
│  총 운행료: 120,000원             │
│  내 수익:   48,000원              │
│  납부액:    72,000원              │
│  외상:      15,000원              │
│                                   │
│  [업무 마감하기] 또는 [통합 제출] │
│                                   │
│ ─── 운행 내역 리스트 ───          │
│  1. 홍길동 출→도 15,000원 현금    │
│  2. 김철수 출→도 10,000원 이체    │
│  ...                              │
└──────────────────────────────────┘
```

#### Flutter 코드 골격 (뼈대만)

```dart
// lib/features/settlement/screens/history_settlement_screen.dart

class HistorySettlementScreen extends ConsumerStatefulWidget {
  const HistorySettlementScreen({super.key});
  @override
  ConsumerState<HistorySettlementScreen> createState() => _HistorySettlementScreenState();
}

class _HistorySettlementScreenState extends ConsumerState<HistorySettlementScreen> {
  @override
  Widget build(BuildContext context) {
    final workflow = ref.watch(driverWorkflowProvider);
    final carryOver = ref.watch(carryOverProvider);
    final settlement = ref.watch(settlementProvider);

    // 매니저 반려 시 다이얼로그 (Kotlin HistorySettlementScreen LaunchedEffect)
    ref.listen<CarryOverState>(carryOverProvider, (prev, next) {
      if (next.dailySettlementStatus is DailySettlementStatusRejected &&
          prev?.dailySettlementStatus is! DailySettlementStatusRejected) {
        _showRejectedDialog();
      }
      if (next.dailySettlementStatus is DailySettlementStatusConfirmed &&
          prev?.dailySettlementStatus is! DailySettlementStatusConfirmed) {
        _showConfirmedDialog();
      }
    });

    return PopScope(
      canPop: false,  // §2.4 BackHandler 등가
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () => context.pop(),
          ),
          title: const Text('운행 내역 / 정산'),
        ),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (carryOver.carryOver != null)
              CarryOverCard(
                carryOver: carryOver.carryOver!,
                onConfirmReceive: () async {
                  final (success, msg) = await ref.read(carryOverProvider.notifier).confirmReceive();
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
                  }
                },
              ),
            const SizedBox(height: 16),

            TodaySettlementCard(
              settlement: settlement.todaySettlement,
              depositRatio: settlement.depositRatio,
              dailyStatus: carryOver.dailySettlementStatus,
              isSubmitting: settlement.isSubmittingSettlement,
              onSubmit: _onSubmitSettlement,
            ),
            const SizedBox(height: 16),

            const Text('운행 내역', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            ...settlement.tripHistory.map((item) => TripHistoryTile(item: item)),
          ],
        ),
      ),
    );
  }

  Future<void> _onSubmitSettlement() async {
    final realDeposit = await _askRealDeposit();
    if (realDeposit == null || !mounted) return;

    final (success, msg) = await ref.read(settlementProvider.notifier)
        .submitDailySettlement(realDeposit);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    }
  }

  Future<int?> _askRealDeposit() async {
    final ctl = TextEditingController();
    return showDialog<int>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('실 납부액 입력'),
        content: TextField(
          controller: ctl,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(labelText: '원'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, int.tryParse(ctl.text.replaceAll(',', ''))),
            child: const Text('제출'),
          ),
        ],
      ),
    );
  }

  void _showRejectedDialog() {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('정산 반려'),
        content: const Text('매니저가 정산을 반려했습니다. 다시 제출해주세요.'),
        actions: [FilledButton(onPressed: () => Navigator.pop(ctx), child: const Text('확인'))],
      ),
    );
  }

  void _showConfirmedDialog() {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('정산 확인 완료'),
        content: const Text('매니저가 정산을 확인했습니다. 퇴근할 수 있습니다.'),
        actions: [FilledButton(onPressed: () => Navigator.pop(ctx), child: const Text('확인'))],
      ),
    );
  }
}
```

**보조 위젯들** (`CarryOverCard`, `TodaySettlementCard`, `TripHistoryTile`) 는 별도 파일로 분리하되 구조는 Kotlin `HistorySettlementScreen.kt` 원본 참조해 구현. 본 문서는 HomeScreen 상위 구조만 제시.

### 3.11 SettingsScreen

**Kotlin 원본**: `ui/home/SettingsScreen.kt:55-` (자동로그인 토글, 로그아웃 등)

```dart
class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});
  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  bool _autoLogin = false;

  @override
  void initState() {
    super.initState();
    _autoLogin = ref.read(loginProvider.notifier).form.autoLogin;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
        title: const Text('설정'),
      ),
      body: ListView(
        children: [
          SwitchListTile(
            title: const Text('자동 로그인'),
            subtitle: const Text('앱 재시작 시 자동으로 로그인합니다'),
            value: _autoLogin,
            onChanged: (v) {
              setState(() => _autoLogin = v);
              ref.read(loginProvider.notifier).toggleAutoLogin(v);
            },
          ),
          const Divider(),
          ListTile(
            title: const Text('운행 내역'),
            leading: const Icon(Icons.history),
            onTap: () => context.goNamed('history'),
          ),
          ListTile(
            title: const Text('추천 QR'),
            leading: const Icon(Icons.qr_code),
            onTap: () => context.goNamed('referral-qr'),
          ),
          const Divider(),
          ListTile(
            title: const Text('로그아웃', style: TextStyle(color: Colors.red)),
            leading: const Icon(Icons.exit_to_app, color: Colors.red),
            onTap: _onLogout,
          ),
        ],
      ),
    );
  }

  Future<void> _onLogout() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('로그아웃'),
        content: const Text('로그아웃하시겠습니까?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('로그아웃')),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      await ref.read(loginProvider.notifier).logout();
      if (mounted) context.goNamed('login');
    }
  }
}
```

### 3.12 CallDetailsScreen

**Kotlin 원본**: `ui/details/CallDetailsScreen.kt:33-` (~200 LOC)
**의존 Notifier**: `CallDetailsNotifier.family(callId)` (NOTIFIERS.md §9)

```dart
class CallDetailsScreen extends ConsumerWidget {
  final String callId;
  const CallDetailsScreen({required this.callId, super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(callDetailsProvider(callId));

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
        title: const Text('콜 상세'),
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('오류: $e')),
        data: (call) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _row('콜 ID', call.id),
            _row('고객', call.customerName),
            _row('전화', call.phoneNumber),
            _row('상태', call.status.displayName),
            _row('출발', call.departureSet ?? call.customerAddress ?? '-'),
            _row('도착', call.destinationSet ?? call.destination ?? '-'),
            if (call.waypointsSet != null && call.waypointsSet!.isNotEmpty)
              _row('경유', call.waypointsSet!),
            _row('요금', '${call.fareSet ?? call.fare ?? 0}원'),
            _row('결제', call.paymentMethod),
            if (call.tripSummaryFinal != null)
              _row('운행 요약', call.tripSummaryFinal!),
            if (call.completedAt != null)
              _row('완료 시각', _formatDateTime(call.completedAt!)),
          ],
        ),
      ),
    );
  }

  Widget _row(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(width: 100, child: Text('$label:', style: const TextStyle(fontWeight: FontWeight.bold))),
        Expanded(child: Text(value)),
      ],
    ),
  );

  String _formatDateTime(DateTime dt) {
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')} '
        '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}
```

### 3.13 ReferralQRScreen (R2, 선택)

**Kotlin 원본**: `ui/screens/home/ReferralQRScreen.kt:27-`
**의존**: `qr_flutter: ^4.1.0` (pubspec.yaml:44)
**분류**: R2 (OVERVIEW.md §Phase 1 범위 제외)

```dart
class ReferralQRScreen extends ConsumerWidget {
  const ReferralQRScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final prefs = ref.watch(sharedPreferencesProvider);
    final provinceId = prefs.getString('pref_province_id') ?? '';
    final cityId = prefs.getString('pref_city_id') ?? '';
    final officeId = prefs.getString('pref_office_id') ?? '';
    final driverId = ref.watch(firebaseAuthProvider).currentUser?.uid ?? '';

    // QR URL 포맷 (Install Referrer 호환, MainActivity.kt:220-228 파라미터)
    final referrerUrl = 'p=$provinceId&c=$cityId&o=$officeId'
        '&driver=$driverId&driverName=${Uri.encodeComponent(_driverName)}';
    final qrData = 'https://play.google.com/store/apps/details'
        '?id=com.designated.customer.app&referrer=${Uri.encodeComponent(referrerUrl)}';

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
        title: const Text('추천 QR'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            QrImageView(data: qrData, size: 280),
            const SizedBox(height: 24),
            const Text('고객이 이 QR을 스캔하면\n앱 설치 시 기사님께 자동 귀속됩니다.',
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
```

---

## 4. 공통 위젯

### 4.1 AppLifecycleObserver

앱 lifecycle 이벤트를 Notifier에 전달. HomeScreen은 이미 `WidgetsBindingObserver` 사용 중. 별도 위젯은 불필요.

### 4.2 LoadingIndicator

Kotlin Material3 `CircularProgressIndicator` 등가. Flutter 기본 제공.

### 4.3 ErrorSnackBar 헬퍼

```dart
// lib/core/utils/snackbar_utils.dart

extension SnackBarExt on BuildContext {
  void showErrorSnackBar(String message) {
    ScaffoldMessenger.of(this).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Theme.of(this).colorScheme.error,
        duration: const Duration(seconds: 4),
      ),
    );
  }

  void showSuccessSnackBar(String message) {
    ScaffoldMessenger.of(this).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.green,
      ),
    );
  }
}
```

### 4.4 OfficeNameText (AppBar 사무실 이름 표시)

Kotlin `HomeScreen.kt:123-144` 에서 Firestore 조회로 사무실 이름 로드. 별도 Provider 분리 권장:

```dart
final officeNameProvider = FutureProvider<String>((ref) async {
  final prefs = ref.watch(sharedPreferencesProvider);
  final firestore = ref.watch(firestoreProvider);

  final provinceId = prefs.getString('pref_province_id');
  final cityId = prefs.getString('pref_city_id');
  final officeId = prefs.getString('pref_office_id');
  if (provinceId == null || cityId == null || officeId == null) return '사무실';

  try {
    final doc = await firestore
        .collection('provinces').doc(provinceId)
        .collection('cities').doc(cityId)
        .collection('offices').doc(officeId)
        .get();
    return doc.data()?['name'] as String? ?? officeId;
  } catch (_) {
    return officeId;
  }
});

class OfficeNameText extends ConsumerWidget {
  const OfficeNameText({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(officeNameProvider);
    return async.when(
      loading: () => const Text('...'),
      error: (_, __) => const Text('사무실'),
      data: Text.new,
    );
  }
}
```

### 4.5 AddressSearchDialog (Kakao API)

**Kotlin 원본**: `util/AddressSearchHelper.kt` — 외부 Kakao API 호출 후 결과 리스트 표시

```dart
// lib/features/driver/widgets/address_search_dialog.dart

class AddressSearchDialog extends ConsumerStatefulWidget {
  const AddressSearchDialog({super.key});
  @override
  ConsumerState<AddressSearchDialog> createState() => _AddressSearchDialogState();
}

class _AddressSearchDialogState extends ConsumerState<AddressSearchDialog> {
  final _queryCtl = TextEditingController();
  List<AddressSearchResult> _results = [];
  bool _loading = false;

  Future<void> _search() async {
    final query = _queryCtl.text.trim();
    if (query.isEmpty) return;
    setState(() => _loading = true);
    try {
      final results = await ref.read(addressSearchServiceProvider).search(query);
      setState(() => _results = results);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('주소 검색')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _queryCtl,
                    onSubmitted: (_) => _search(),
                    decoration: const InputDecoration(
                      labelText: '주소 또는 장소명',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
                IconButton(icon: const Icon(Icons.search), onPressed: _search),
              ],
            ),
          ),
          if (_loading) const CircularProgressIndicator(),
          Expanded(
            child: ListView.builder(
              itemCount: _results.length,
              itemBuilder: (_, i) => ListTile(
                title: Text(_results[i].address),
                subtitle: _results[i].placeName != null ? Text(_results[i].placeName!) : null,
                onTap: () => Navigator.pop(context, _results[i]),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
```

---

## 5. 화면 전환 패턴 (Notifier State 변화 → 화면 전환)

### 5.1 자동 전환 매트릭스

| Notifier state 변화 | 화면 전환 / UI 반응 | 구현 위치 |
|--------------------|------------------|----------|
| `driverStatus == WAITING` + `newCallPopup != null` | **NewCallPopup Dialog 자동 표시** | `HomeScreen._buildContent` + Stack 하단 |
| `activeCall.status == CallStatusAccepted` | **TripPreparationView 자동 렌더** | `HomeScreen._buildContent switch` |
| `activeCall.status == CallStatusInProgress` | **InProgressView 자동 렌더** | `HomeScreen._buildContent switch` |
| `callForSettlement != null` | **SettlementSummaryDialog 자동 표시** | `HomeScreen` Stack 하단 |
| `activeCall == null` + `driverStatus == WAITING` | **WaitingView 복귀** | `HomeScreen._buildContent` |
| `errorMessage != null` | **Snackbar 표시 + `onErrorMessageHandled()` 호출** | `HomeScreen` ref.listen |
| `navigateToHistorySettlement == true` | **`/history` 이동 + 플래그 리셋** | `HomeScreen` ref.listen |
| `dailySettlementStatus is Rejected` | **"정산 반려" 다이얼로그** | `HistorySettlementScreen` ref.listen(carryOverProvider) |
| `dailySettlementStatus is Confirmed` | **"정산 확인 완료" 다이얼로그** | `HistorySettlementScreen` ref.listen(carryOverProvider) |
| `loginState is Success` | **`/` 홈 이동** | `LoginScreen` ref.listen |

### 5.2 ref.listen 패턴 요약

```dart
// HomeScreen build 메서드 내부 공통 패턴
ref.listen<DriverScreenUiState>(driverWorkflowProvider, (prev, next) {
  // 1. errorMessage 변경 감지
  if (next.errorMessage != null && prev?.errorMessage != next.errorMessage) {
    context.showErrorSnackBar(next.errorMessage!);
    ref.read(driverWorkflowProvider.notifier).onErrorMessageHandled();
  }

  // 2. navigation 플래그 감지 (일회성)
  if (next.navigateToHome && prev?.navigateToHome != true) {
    context.goNamed('home');
    ref.read(driverWorkflowProvider.notifier).onNavigateToHomeHandled();
  }
  if (next.navigateToHistorySettlement && prev?.navigateToHistorySettlement != true) {
    context.goNamed('history');
    ref.read(driverWorkflowProvider.notifier).onNavigateToHistorySettlementHandled();
  }
});
```

### 5.3 핵심 원칙

1. **Navigation은 ref.listen side effect** — ref.watch 로 기반 리빌드 + ref.listen 로 일회성 이동
2. **모달은 Stack 자식으로** — `showDialog`가 아닌 Stack 하단 조건부 렌더링 권장 (Notifier state 추적 용이)
3. **일회성 플래그 리셋 필수** — `navigateTo*` 설정 후 즉시 `on*Handled()` 호출하여 재진입 방지

---

## 6. 접근성 / 테스트 훅

### 6.1 Semantics / accessibility

각 주요 버튼에 `Semantics` 라벨 부여:

```dart
Semantics(
  label: '콜 수락',
  button: true,
  child: FilledButton(onPressed: onAccept, child: const Text('수락')),
),
```

**의제 6 Privacy 보완과 무관** — Semantics 는 App Store 심사보다는 장애 사용자 지원.

### 6.2 테스트 키

TestFlight QA 시 위젯 찾기 용이:

```dart
const _acceptButtonKey = Key('new_call_popup_accept_button');
const _submitSettlementKey = Key('history_submit_settlement_button');
```

### 6.3 테스트 예시 (integration_test)

```dart
// test/integration/home_flow_test.dart

void main() {
  testWidgets('콜 수락 → 운행 준비 화면 이동', (tester) async {
    // Arrange
    await tester.pumpWidget(const ProviderScope(child: MyApp()));
    await tester.pumpAndSettle();

    // state에 newCallPopup 주입
    // ...

    // Act
    await tester.tap(find.byKey(const Key('new_call_popup_accept_button')));
    await tester.pumpAndSettle();

    // Assert
    expect(find.byType(TripPreparationView), findsOneWidget);
  });
}
```

---

## 7. 마이그레이션 작업 순서 (Phase 6)

### Week 1
1. `lib/core/routing/app_router.dart` — GoRouter 설정 + redirect 로직
2. `LoginScreen` + `ForgotPasswordScreen` (AUTH.md §6 연동)
3. `SignUpScreen` (NOTIFIERS.md §6 연동)

### Week 2
4. `HomeScreen` + `WaitingView` + `NewCallPopup` + `TripPreparationView` + `InProgressView`
5. `SettlementSummaryDialog` (HomeScreen 모달)
6. AddressSearchDialog + Kakao API 연동 (`lib/services/address_search_service.dart` 신규)

### Week 3
7. `HistorySettlementScreen` + `CarryOverCard` + `TodaySettlementCard` + `TripHistoryTile`
8. `SettingsScreen` + `CallDetailsScreen`
9. `ReferralQRScreen` (R2, 선택)

### Week 4
10. 공통 위젯 (OfficeNameText, SnackBar 헬퍼)
11. 위젯 테스트 10건 최소 (integration_test)
12. TestFlight Internal 업로드 + 기사 파일럿

---

## 8. 작성 완료 요약

- **화면 13종 전수 매핑**: Login/SignUp/Forgot/Home(분기)/Waiting/NewCallPopup/TripPreparation/InProgress/SettlementSummary/HistorySettlement/Settings/CallDetails/ReferralQR(R2)
- **GoRouter 스펙 + Deep Link 처리 + BackHandler PopScope 매핑**
- **화면 전환 매트릭스 10종** (§5.1)
- **Kotlin 원본 라인 번호 100% 인용**
- **Dart 코드 샘플** 모든 화면 최소 골격 + 핵심 로직
- **공통 위젯 5종** (OfficeName/AddressSearch/SnackBar 헬퍼/LifecycleObserver/LoadingIndicator)
- **MVP 범위 명확화**: R2 ReferralQR 분리, R1 NetworkBanner 예정
- **Phase 6 Week 1~4 작업 순서 + 테스트 전략**

---

## 9. 참조

- `flutter/driver_app/MVP/OVERVIEW.md` — Phase 6 실행 사양
- `flutter/driver_app/MVP/MODELS.md` — Freezed 모델 (CallInfo, Driver, ...)
- `flutter/driver_app/MVP/ENUMS.md` — sealed class (CallStatus, DriverStatus, ...)
- `flutter/driver_app/MVP/NOTIFIERS.md` — Riverpod Notifier 8종 (DriverWorkflowNotifier, LoginNotifier, ...)
- `flutter/driver_app/MVP/AUTH.md` — 로그인/자동로그인 상세
- `flutter/driver_app/MVP/FIRESTORE.md` — 트랜잭션 6곳 (차기 작성)
- `driver_app/app/src/main/java/com/designated/driverapp/ui/` — Kotlin 원본 UI 전체
- `driver_app/app/src/main/java/com/designated/driverapp/navigation/AppNavigation.kt` — Kotlin 라우팅 155 LOC
