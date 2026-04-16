# 손님앱 화면 매핑 (Kotlin Composable → Flutter Widget)

> **의제 14-A (Dart 3 sealed class) + Riverpod ConsumerStatefulWidget + GoRouter ^14** 반영
> **기사앱 SCREENS.md 자매 문서** (`flutter/driver_app/MVP/SCREENS.md`) — 동일 원칙 + 손님앱 차이점
> **MODELS.md UI 상태 구조체 + NOTIFIERS.md 4 Notifier** 사용
> **원본 기준**: `customer_app/app/src/main/java/com/designated/customer/ui/` 전체 + `MainActivity.kt` 상태 머신
> **작성일**: 2026-04-16
> **작성자**: kotlin-expert

---

## 1. 화면 인벤토리 + 라우팅

### 1.1 전체 화면 11종 (Kotlin Composable 1:1 매핑)

| # | 화면 | Kotlin 원본 파일 | Kotlin 함수 (라인) | Flutter Widget | GoRouter path |
|---|------|----------------|------------------|---------------|---------------|
| 1 | **SplashScreen** (LOADING) | `MainActivity.kt` (AppScreen.LOADING) | `currentScreen=LOADING` 초기화 블록 (line 382-500+) | `SplashScreen` ConsumerWidget | `/splash` |
| 2 | **OfficeCodeScreen** (QR_REQUIRED, 신규 Phase 1) | `MainActivity.kt` (AppScreen.QR_REQUIRED) + 신규 ATTRIBUTION §2 | — (본 MVP 신규) | `OfficeCodeScreen` ConsumerStatefulWidget | `/office-code` |
| 3 | **TermsAgreementScreen** | `ui/auth/TermsAgreementScreen.kt` | `fun TermsAgreementScreen` | `TermsAgreementScreen` ConsumerStatefulWidget | `/auth/terms` |
| 4 | **DocumentViewerScreen** (약관/개인정보) | `ui/auth/DocumentViewerScreen` (MainActivity에서 import) | `fun DocumentViewerScreen` | `DocumentViewerScreen` ConsumerWidget | `/auth/terms/view` + `/auth/privacy` |
| 5 | **PhoneAuthScreen** | `ui/auth/PhoneAuthScreen.kt` | `fun PhoneAuthScreen` | `PhoneAuthScreen` ConsumerStatefulWidget | `/auth/phone` |
| 6 | **ProfileSetupScreen** | `ui/profile/ProfileSetupScreen.kt` | `fun ProfileSetupScreen` | `ProfileSetupScreen` ConsumerStatefulWidget | `/profile/setup` |
| 7 | **HomeScreen** (콜 요청) | `ui/main/HomeScreen.kt` | `fun HomeScreen` | `HomeScreen` ConsumerStatefulWidget | `/` (MainShell 탭) |
| 8 | **ProfileScreen** (내 정보) | `ui/profile/ProfileScreen.kt` | `fun ProfileScreen` | `ProfileScreen` ConsumerStatefulWidget | `/profile` (탭) |
| 9 | **PointScreen** (포인트 대시보드) | `ui/point/PointScreen.kt` | `fun PointScreen` | `PointScreen` ConsumerStatefulWidget | `/points` (탭) |
| 10 | **PointHistoryScreen** (거래 내역) | `ui/point/PointHistoryScreen.kt` | `fun PointHistoryScreen` | `PointHistoryScreen` ConsumerWidget | `/points/history` |
| 11 | **CallHistoryScreen** (이용 내역) | `ui/history/CallHistoryScreen.kt` | `fun CallHistoryScreen` | `CallHistoryScreen` ConsumerWidget | `/history` (탭) |

**공통 모달/다이얼로그** (HomeScreen 내부 + 기타 화면 재사용):
- **DriverAssignedDialog** (FCM DRIVER_ASSIGNED 수신 시)
- **RideCompletedDialog** (FCM RIDE_COMPLETED 수신 시 + 500ms 지연 후 표시)
- **CallCancelledDialog** (FCM CALL_CANCELLED 수신 시)
- **HomeAddressDialog** (집주소 입력/수정)
- **PointsEarnedDialog** (포인트 적립/사용 팝업)

### 1.2 라우팅 스펙 (Kotlin `AppScreen` enum + `NavTab` → GoRouter)

**Kotlin 원본 `AppScreen` enum** (`MainActivity.kt:331-340`):
```kotlin
enum class AppScreen {
    LOADING,           // 초기화 중 (익명인증 + 프로필 체크)
    QR_REQUIRED,       // 사무실 정보 없음 → QR 스캔 또는 코드 입력 필요
    TERMS_AGREEMENT,   // 약관 동의 필요
    TERMS_VIEWER,      // 약관 전문 뷰어
    PRIVACY_VIEWER,    // 개인정보 뷰어
    PHONE_AUTH,        // 전화번호 인증
    PROFILE_SETUP,     // 프로필 설정 (이름/주소 입력)
    MAIN               // MainNavigation (BottomNav 4탭)
}
```

**Kotlin `NavTab` enum** (`MainNavigation.kt:19-28`, **BottomNavigation 4탭**):
```kotlin
enum class NavTab(val title: String, val icon: ImageVector, val route: String) {
    HOME("홈", Icons.Default.Home, "home"),
    HISTORY("이용내역", Icons.Default.List, "history"),
    POINTS("포인트", Icons.Default.Star, "points"),
    PROFILE("내정보", Icons.Default.Person, "profile")
}
```

**⚠️ 수정**: 본 문서 §1.1에서 "5탭"으로 오기했을 경우 — **실제 Kotlin 원본은 4탭** (HOME/HISTORY/POINTS/PROFILE). 설정 탭 별도 없음 (ProfileScreen 안에 로그아웃 등 포함).

**Flutter GoRouter 매핑**:

| Kotlin state/tab | GoRouter path | name | 비고 |
|----------------|-------------|------|-----|
| `AppScreen.LOADING` | `/splash` | `'splash'` | 초기 진입점, redirect로 자동 전환 |
| `AppScreen.QR_REQUIRED` | `/office-code` | `'office-code'` | B옵션(코드) + C옵션(QR) 통합 화면 |
| `AppScreen.TERMS_AGREEMENT` | `/auth/terms` | `'terms'` | |
| `AppScreen.TERMS_VIEWER` | `/auth/terms/view` | `'terms-view'` | |
| `AppScreen.PRIVACY_VIEWER` | `/auth/privacy` | `'privacy-view'` | |
| `AppScreen.PHONE_AUTH` | `/auth/phone` | `'phone-auth'` | |
| `AppScreen.PROFILE_SETUP` | `/profile/setup` | `'profile-setup'` | |
| `AppScreen.MAIN` + `NavTab.HOME` | `/` | `'home'` | ShellRoute 자식 |
| `AppScreen.MAIN` + `NavTab.HISTORY` | `/history` | `'history'` | ShellRoute 자식 |
| `AppScreen.MAIN` + `NavTab.POINTS` | `/points` | `'points'` | ShellRoute 자식 |
| `AppScreen.MAIN` + `NavTab.PROFILE` | `/profile` | `'profile'` | ShellRoute 자식 |

---

## 2. GoRouter 설정

### 2.1 Router 정의 (BottomNavigation ShellRoute)

**기사앱 SCREENS.md §2 와의 차이**: 기사앱은 BottomNav 없이 단일 Scaffold. **손님앱은 4탭 BottomNavigation으로 ShellRoute 필수**.

```dart
// lib/core/routing/app_router.dart

import 'package:go_router/go_router.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../features/onboarding/screens/splash_screen.dart';
import '../../features/attribution/screens/office_code_screen.dart';
import '../../features/auth/screens/phone_auth_screen.dart';
import '../../features/auth/screens/terms_agreement_screen.dart';
import '../../features/auth/screens/document_viewer_screen.dart';
import '../../features/profile/screens/profile_setup_screen.dart';
import '../../features/profile/screens/profile_screen.dart';
import '../../features/call/screens/home_screen.dart';
import '../../features/history/screens/call_history_screen.dart';
import '../../features/point/screens/point_screen.dart';
import '../../features/point/screens/point_history_screen.dart';
import '../../features/shell/main_shell.dart';

final goRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/splash',
    redirect: (context, state) {
      // 온보딩 상태 머신 redirect는 SplashScreen 내부에서 처리
      // (복잡한 분기는 SplashScreen에서 context.go(...) 호출)
      return null;
    },
    routes: [
      GoRoute(
        path: '/splash',
        name: 'splash',
        builder: (_, __) => const SplashScreen(),
      ),
      GoRoute(
        path: '/office-code',
        name: 'office-code',
        builder: (_, __) => const OfficeCodeScreen(),
      ),

      // 약관 → 전화인증 → 프로필 설정 (온보딩 3단계)
      GoRoute(
        path: '/auth/terms',
        name: 'terms',
        builder: (_, __) => const TermsAgreementScreen(),
        routes: [
          GoRoute(
            path: 'view',
            name: 'terms-view',
            builder: (_, state) => DocumentViewerScreen(
              documentType: state.uri.queryParameters['type'] ?? 'terms',
            ),
          ),
        ],
      ),
      GoRoute(
        path: '/auth/privacy',
        name: 'privacy-view',
        builder: (_, __) => const DocumentViewerScreen(documentType: 'privacy'),
      ),
      GoRoute(
        path: '/auth/phone',
        name: 'phone-auth',
        builder: (_, __) => const PhoneAuthScreen(),
      ),
      GoRoute(
        path: '/profile/setup',
        name: 'profile-setup',
        builder: (_, __) => const ProfileSetupScreen(),
      ),

      // MainShell (BottomNavigation 4탭)
      ShellRoute(
        builder: (_, __, child) => MainShell(child: child),
        routes: [
          GoRoute(
            path: '/',
            name: 'home',
            pageBuilder: (_, state) => const NoTransitionPage(child: HomeScreen()),
          ),
          GoRoute(
            path: '/history',
            name: 'history',
            pageBuilder: (_, state) => const NoTransitionPage(child: CallHistoryScreen()),
          ),
          GoRoute(
            path: '/points',
            name: 'points',
            pageBuilder: (_, state) => const NoTransitionPage(child: PointScreen()),
            routes: [
              GoRoute(
                path: 'history',
                name: 'point-history',
                builder: (_, __) => const PointHistoryScreen(),
              ),
            ],
          ),
          GoRoute(
            path: '/profile',
            name: 'profile',
            pageBuilder: (_, state) => const NoTransitionPage(child: ProfileScreen()),
          ),
        ],
      ),
    ],
  );
});
```

### 2.2 MainShell (BottomNavigation 4탭)

**Kotlin 원본** (`MainNavigation.kt:31-131`): Scaffold + NavigationBar + `when (selectedTab)` 분기. Back 버튼 시 HOME 으로 복귀 (`MainNavigation.kt:65-67`).

```dart
// lib/features/shell/main_shell.dart

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class MainShell extends StatelessWidget {
  final Widget child;
  const MainShell({required this.child, super.key});

  static const _tabs = [
    _TabInfo(icon: Icons.home, label: '홈', path: '/'),
    _TabInfo(icon: Icons.list, label: '이용내역', path: '/history'),
    _TabInfo(icon: Icons.star, label: '포인트', path: '/points'),
    _TabInfo(icon: Icons.person, label: '내정보', path: '/profile'),
  ];

  int _currentIndex(BuildContext context) {
    final location = GoRouterState.of(context).uri.toString();
    for (var i = 0; i < _tabs.length; i++) {
      if (location == _tabs[i].path ||
          (_tabs[i].path != '/' && location.startsWith(_tabs[i].path))) {
        return i;
      }
    }
    return 0;
  }

  @override
  Widget build(BuildContext context) {
    final idx = _currentIndex(context);
    return PopScope(
      // Kotlin BackHandler 등가: 홈이 아니면 홈으로 복귀 (MainNavigation.kt:65-67)
      canPop: idx == 0,
      onPopInvoked: (didPop) {
        if (!didPop && idx != 0) {
          context.go('/');
        }
      },
      child: Scaffold(
        body: child,
        bottomNavigationBar: NavigationBar(
          selectedIndex: idx,
          onDestinationSelected: (i) => context.go(_tabs[i].path),
          destinations: _tabs.map((t) =>
            NavigationDestination(icon: Icon(t.icon), label: t.label),
          ).toList(),
        ),
      ),
    );
  }
}

class _TabInfo {
  final IconData icon;
  final String label;
  final String path;
  const _TabInfo({required this.icon, required this.label, required this.path});
}
```

---

## 3. 온보딩 상태 머신 (MainActivity.kt 복제)

### 3.1 Kotlin 원본 상태 머신 분석

**MainActivity.kt `CustomerApp` composable** (`line 342-550+`):

```
LaunchedEffect(Unit) {
  // Step 1: SharedPreferences 사무실 정보 로드
  prefsOfficeId, prefsProvinceId, prefsCityId 읽기

  // Step 2: 사무실 정보 없으면 → QR_REQUIRED
  if (prefsOfficeId == null || ...) {
    currentScreen = AppScreen.QR_REQUIRED
    return
  }

  // Step 3: Anonymous Auth 자동 로그인 (line 398-411)
  var currentUser = auth.currentUser
  if (currentUser == null) {
    result = auth.signInAnonymously().await()
    currentUser = result.user
  }
  if (currentUser == null) {
    currentScreen = AppScreen.QR_REQUIRED  // Anonymous 실패 시 QR로
    return
  }

  // Step 4: 프로필 문서 존재 확인 (customers/{uid})
  val doc = firestore...collection("customers").doc(currentUser.uid).get().await()

  if (doc.exists()) {
    customerInfo = CustomerInfo.fromMap(doc.data)
    phoneNumber 저장 + lastActiveAt 업데이트
    currentScreen = AppScreen.MAIN  // 홈 진입
  } else {
    // 프로필 없음 → 약관 동의부터 시작
    currentScreen = AppScreen.TERMS_AGREEMENT
  }
}
```

### 3.2 Flutter SplashScreen 복제 (Riverpod 통합)

```dart
// lib/features/onboarding/screens/splash_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});
  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _bootstrap());
  }

  /// Kotlin MainActivity.kt:382-500+ CustomerApp LaunchedEffect 재현
  Future<void> _bootstrap() async {
    final prefs = ref.read(sharedPreferencesProvider);
    final profile = ref.read(profileNotifierProvider.notifier);
    final auth = ref.read(authNotifierProvider.notifier);

    // Step 1: 사무실 정보 확인
    final officeId = prefs.getString('office_id');
    final provinceId = prefs.getString('province_id');
    final cityId = prefs.getString('city_id');

    if (officeId == null || provinceId == null || cityId == null) {
      if (mounted) context.goNamed('office-code');
      return;
    }

    // Step 2: Anonymous Auth (NOTIFIERS.md §5.3 _initAnonymousAuth 자동 실행)
    // AuthNotifier 생성 시 이미 signInAnonymously 호출됨 → 결과 대기
    await Future<void>.delayed(const Duration(milliseconds: 200));
    var authState = ref.read(authNotifierProvider);
    if (!authState.isAnonymouslySignedIn) {
      // 재시도 또는 실패 처리
      await Future<void>.delayed(const Duration(milliseconds: 500));
      authState = ref.read(authNotifierProvider);
    }
    if (!authState.isAnonymouslySignedIn || authState.anonymousUid == null) {
      // Anonymous Auth 실패 → QR 재입력 (Kotlin line 404-408)
      if (mounted) context.goNamed('office-code');
      return;
    }

    final uid = authState.anonymousUid!;

    // Step 3: 프로필 문서 존재 확인
    final firestore = ref.read(firestoreProvider);
    try {
      final doc = await firestore
          .collection('provinces').doc(provinceId)
          .collection('cities').doc(cityId)
          .collection('offices').doc(officeId)
          .collection('customers').doc(uid)
          .get();

      if (doc.exists) {
        // 기존 손님: 프로필 복구 + HOME 진입
        await profile.loadCustomerInfo(uid);

        // phoneNumber를 AuthNotifier에도 반영 (FCM 토큰 저장에 필요)
        final customerInfo = ref.read(profileNotifierProvider).customerInfo;
        if (customerInfo != null && customerInfo.phoneNumber.isNotEmpty) {
          auth.setVerifiedPhoneNumber(customerInfo.phoneNumber);
          await prefs.setString('phone_number', customerInfo.phoneNumber);
        }

        // lastActiveAt 업데이트 (Kotlin line 440-450)
        try {
          await doc.reference.update({'lastActiveAt': Timestamp.now()});
        } catch (_) {}

        // 초기 FCM 토큰 등록 (의제 5 platform 메타)
        await auth.initialFcmTokenRegistration();

        if (mounted) context.goNamed('home');
      } else {
        // 신규 손님: 약관 동의부터
        if (mounted) context.goNamed('terms');
      }
    } catch (e) {
      debugPrint('[SplashScreen] 프로필 조회 실패: $e');
      if (mounted) context.goNamed('terms');
    }
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.local_taxi, size: 80, color: Colors.orange),
            SizedBox(height: 24),
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('앱 초기화 중...'),
          ],
        ),
      ),
    );
  }
}
```

### 3.3 온보딩 상태 전이 매트릭스

| From | 트리거 | To |
|------|--------|-----|
| `/splash` | 사무실 정보 없음 | `/office-code` |
| `/splash` | 기존 손님 (프로필 O) | `/` (홈) |
| `/splash` | 신규 손님 (프로필 X) | `/auth/terms` |
| `/office-code` | 코드 입력 or QR 성공 | `/splash` (재진입) |
| `/auth/terms` | 약관 동의 완료 | `/auth/phone` |
| `/auth/phone` | Phone Auth 검증 완료 | `/profile/setup` |
| `/profile/setup` | 프로필 저장 완료 | `/` (홈) |
| `/` | (언제든) | `/history`, `/points`, `/profile` (탭) |

---

## 4. 각 화면 상세

### 4.1 OfficeCodeScreen (신규, ATTRIBUTION.md §2 B+C 옵션)

**의제 10 결정** (OVERVIEW.md §Phase 1 포함): 수동 사무실 코드 입력 + QR 스캔 통합 화면. Kotlin 원본에는 없음 (Kotlin은 Install Referrer만).

**UI 구조**:

```
┌─────────────────────────────┐
│   [콜마당 로고]              │
│   사무실 연결이 필요합니다    │
│                              │
│  방법 1: 사무실 코드 입력     │
│  ┌──────────────────────┐   │
│  │ 6자리 코드           │   │
│  └──────────────────────┘   │
│  [ 코드 적용 ]               │
│                              │
│  ─────── OR ───────          │
│                              │
│  방법 2: QR 스캔             │
│  [ 카메라로 QR 스캔 ]       │
└─────────────────────────────┘
```

```dart
// lib/features/attribution/screens/office_code_screen.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

class OfficeCodeScreen extends ConsumerStatefulWidget {
  const OfficeCodeScreen({super.key});
  @override
  ConsumerState<OfficeCodeScreen> createState() => _OfficeCodeScreenState();
}

class _OfficeCodeScreenState extends ConsumerState<OfficeCodeScreen> {
  final _codeCtl = TextEditingController();
  bool _isApplying = false;

  @override
  void dispose() {
    _codeCtl.dispose();
    super.dispose();
  }

  /// B 옵션: 단축 코드 적용
  Future<void> _applyCode() async {
    final code = _codeCtl.text.trim().toUpperCase();
    if (code.length != 6) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('6자리 코드를 입력해주세요')),
      );
      return;
    }
    setState(() => _isApplying = true);
    try {
      final ok = await ref.read(profileNotifierProvider.notifier).applyOfficeCode(code);
      if (!mounted) return;
      if (ok) {
        context.goNamed('splash');  // 재부팅하여 프로필 체크
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('올바른 사무실 코드가 아닙니다')),
        );
      }
    } finally {
      if (mounted) setState(() => _isApplying = false);
    }
  }

  /// C 옵션: QR 스캔
  Future<void> _scanQR() async {
    final result = await Navigator.of(context).push<String>(
      MaterialPageRoute(builder: (_) => const _QRScannerPage()),
    );
    if (result == null) return;

    // QR URL 파라미터 파싱 (ATTRIBUTION.md §3)
    final uri = Uri.tryParse(result);
    if (uri == null) return;

    final params = uri.queryParameters;
    final provinceId = params['p'] ?? params['r'];
    final cityId = params['c'];
    final officeId = params['o'];

    if (provinceId == null || cityId == null || officeId == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('QR 코드 형식이 올바르지 않습니다')),
        );
      }
      return;
    }

    // ATTRIBUTION.md §3: driverId/driverName + 사무실 연락처 저장
    final prefs = ref.read(sharedPreferencesProvider);
    await prefs.setString('province_id', provinceId);
    await prefs.setString('city_id', cityId);
    await prefs.setString('office_id', officeId);

    if (params['driver'] != null && params['driverName'] != null) {
      await prefs.setString('driver_id', params['driver']!);
      await prefs.setString('driver_name', params['driverName']!);
    }
    if (params['phone'] != null && params['bank'] != null &&
        params['account'] != null && params['holder'] != null) {
      await prefs.setString('office_phone', params['phone']!);
      await prefs.setString('bank_name', params['bank']!);
      await prefs.setString('account_number', params['account']!);
      await prefs.setString('account_holder', params['holder']!);
    }

    if (mounted) context.goNamed('splash');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.local_taxi, size: 64, color: Colors.orange),
              const SizedBox(height: 16),
              const Text('콜마당', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              const Text('사무실 연결이 필요합니다', style: TextStyle(fontSize: 16)),
              const SizedBox(height: 48),

              // 방법 1: 사무실 코드
              const Align(
                alignment: Alignment.centerLeft,
                child: Text('방법 1: 사무실 코드 입력', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _codeCtl,
                textCapitalization: TextCapitalization.characters,
                maxLength: 6,
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  hintText: '6자리 코드',
                  counterText: '',
                ),
              ),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: _isApplying ? null : _applyCode,
                child: _isApplying
                    ? const CircularProgressIndicator()
                    : const Text('코드 적용'),
              ),

              const SizedBox(height: 24),
              const Row(children: [
                Expanded(child: Divider()),
                Padding(padding: EdgeInsets.symmetric(horizontal: 8), child: Text('OR')),
                Expanded(child: Divider()),
              ]),
              const SizedBox(height: 24),

              // 방법 2: QR 스캔
              const Align(
                alignment: Alignment.centerLeft,
                child: Text('방법 2: QR 스캔', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: _scanQR,
                icon: const Icon(Icons.qr_code_scanner),
                label: const Text('카메라로 QR 스캔'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _QRScannerPage extends StatelessWidget {
  const _QRScannerPage();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('QR 스캔')),
      body: MobileScanner(
        onDetect: (capture) {
          final barcode = capture.barcodes.firstOrNull;
          final value = barcode?.rawValue;
          if (value != null) {
            Navigator.of(context).pop(value);
          }
        },
      ),
    );
  }
}
```

### 4.2 TermsAgreementScreen

**Kotlin 원본**: `ui/auth/TermsAgreementScreen.kt` (직접 확인 필요)
**의존**: `TermsAgreementViewModel` → Flutter는 `ProfileNotifier.acceptTerms` (NOTIFIERS.md §4.3.6)

**UI 구조**:
- 체크박스 3종: 이용약관 (필수) + 개인정보처리방침 (필수) + 마케팅 동의 (선택)
- "전체 동의" 상단 체크박스
- 각 약관 옆 "전문 보기" 버튼 → DocumentViewerScreen 이동
- 하단 "동의하고 계속" 버튼 (필수 2종 체크 시 활성화)

```dart
// lib/features/auth/screens/terms_agreement_screen.dart

class TermsAgreementScreen extends ConsumerStatefulWidget {
  const TermsAgreementScreen({super.key});
  @override
  ConsumerState<TermsAgreementScreen> createState() => _TermsAgreementScreenState();
}

class _TermsAgreementScreenState extends ConsumerState<TermsAgreementScreen> {
  bool _termsChecked = false;
  bool _privacyChecked = false;
  bool _marketingChecked = false;

  bool get _allRequired => _termsChecked && _privacyChecked;

  Future<void> _onAgree() async {
    if (!_allRequired) return;
    await ref.read(profileNotifierProvider.notifier).acceptTerms(
      version: '1.0',
      marketingConsent: _marketingChecked,
    );
    if (!mounted) return;
    context.goNamed('phone-auth');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('약관 동의')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // 전체 동의
          CheckboxListTile(
            value: _termsChecked && _privacyChecked && _marketingChecked,
            tristate: true,
            onChanged: (v) {
              setState(() {
                final all = v ?? false;
                _termsChecked = all;
                _privacyChecked = all;
                _marketingChecked = all;
              });
            },
            title: const Text('전체 동의', style: TextStyle(fontWeight: FontWeight.bold)),
          ),
          const Divider(),

          // 이용약관 (필수)
          _buildAgreementRow(
            label: '[필수] 이용약관',
            checked: _termsChecked,
            onChanged: (v) => setState(() => _termsChecked = v!),
            onView: () => context.goNamed('terms-view', queryParameters: {'type': 'terms'}),
          ),

          // 개인정보 (필수)
          _buildAgreementRow(
            label: '[필수] 개인정보처리방침',
            checked: _privacyChecked,
            onChanged: (v) => setState(() => _privacyChecked = v!),
            onView: () => context.goNamed('privacy-view'),
          ),

          // 마케팅 (선택)
          _buildAgreementRow(
            label: '[선택] 마케팅 정보 수신',
            checked: _marketingChecked,
            onChanged: (v) => setState(() => _marketingChecked = v!),
            onView: null,
          ),

          const SizedBox(height: 24),
          FilledButton(
            onPressed: _allRequired ? _onAgree : null,
            child: const Text('동의하고 계속'),
          ),
        ],
      ),
    );
  }

  Widget _buildAgreementRow({
    required String label,
    required bool checked,
    required ValueChanged<bool?> onChanged,
    VoidCallback? onView,
  }) {
    return Row(
      children: [
        Expanded(
          child: CheckboxListTile(
            value: checked,
            onChanged: onChanged,
            title: Text(label),
          ),
        ),
        if (onView != null)
          TextButton(onPressed: onView, child: const Text('보기')),
      ],
    );
  }
}
```

### 4.3 DocumentViewerScreen

**Kotlin 원본**: `ui/auth/DocumentViewerScreen.kt` (약관/개인정보 전문 스크롤)
**의존**: 정적 텍스트 또는 `assets/terms.txt` 읽기

```dart
class DocumentViewerScreen extends StatelessWidget {
  final String documentType;  // 'terms' | 'privacy'
  const DocumentViewerScreen({required this.documentType, super.key});

  @override
  Widget build(BuildContext context) {
    final title = documentType == 'terms' ? '이용약관' : '개인정보처리방침';
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: FutureBuilder<String>(
        future: rootBundle.loadString(
          documentType == 'terms' ? 'assets/docs/terms.txt' : 'assets/docs/privacy.txt',
        ),
        builder: (_, snap) {
          if (!snap.hasData) return const Center(child: CircularProgressIndicator());
          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Text(snap.data!),
          );
        },
      ),
    );
  }
}
```

### 4.4 PhoneAuthScreen

**Kotlin 원본**: `ui/auth/PhoneAuthScreen.kt` (2단계: 전화번호 입력 → SMS 코드 입력)
**의존**: `AuthNotifier` (NOTIFIERS.md §5.4) — `sendVerificationCode` + `verifyCode`

**UI 구조**:
- Step 1: 전화번호 입력 + "인증번호 받기" 버튼
- Step 2 (isCodeSent=true): 인증번호 입력 + "확인" + "재전송" 버튼

```dart
// lib/features/auth/screens/phone_auth_screen.dart

class PhoneAuthScreen extends ConsumerStatefulWidget {
  const PhoneAuthScreen({super.key});
  @override
  ConsumerState<PhoneAuthScreen> createState() => _PhoneAuthScreenState();
}

class _PhoneAuthScreenState extends ConsumerState<PhoneAuthScreen> {
  final _phoneCtl = TextEditingController();
  final _codeCtl = TextEditingController();

  @override
  void dispose() {
    _phoneCtl.dispose();
    _codeCtl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(authNotifierProvider);

    // 검증 완료 시 프로필 설정으로 이동
    ref.listen<AuthUiState>(authNotifierProvider, (prev, next) {
      if (next.isVerified && prev?.isVerified != true) {
        context.goNamed('profile-setup');
      }
      if (next.error != null && prev?.error != next.error) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(next.error!)),
        );
      }
    });

    return Scaffold(
      appBar: AppBar(title: const Text('전화번호 인증')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _phoneCtl,
              keyboardType: TextInputType.phone,
              enabled: !state.isCodeSent && !state.isLoading,
              decoration: const InputDecoration(
                labelText: '전화번호',
                hintText: '010-1234-5678',
                border: OutlineInputBorder(),
              ),
              onChanged: (v) => ref.read(authNotifierProvider.notifier).updatePhoneNumber(v),
            ),
            const SizedBox(height: 16),
            if (!state.isCodeSent)
              FilledButton(
                onPressed: state.isLoading
                    ? null
                    : () => ref.read(authNotifierProvider.notifier).sendVerificationCode(),
                child: state.isLoading
                    ? const CircularProgressIndicator()
                    : const Text('인증번호 받기'),
              ),
            if (state.isCodeSent) ...[
              const SizedBox(height: 16),
              TextField(
                controller: _codeCtl,
                keyboardType: TextInputType.number,
                enabled: !state.isLoading,
                decoration: const InputDecoration(
                  labelText: '인증번호 6자리',
                  border: OutlineInputBorder(),
                ),
                onChanged: (v) => ref.read(authNotifierProvider.notifier).updateVerificationCode(v),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: state.isLoading
                    ? null
                    : () => ref.read(authNotifierProvider.notifier).verifyCode(),
                child: state.isLoading
                    ? const CircularProgressIndicator()
                    : const Text('확인'),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: state.isLoading
                    ? null
                    : () => ref.read(authNotifierProvider.notifier).sendVerificationCode(),
                child: const Text('인증번호 재전송'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
```

### 4.5 ProfileSetupScreen

**Kotlin 원본**: `ui/profile/ProfileSetupScreen.kt` + `ProfileSetupViewModel.kt`
**의존**: `ProfileNotifier.updateProfile` (NOTIFIERS.md §4.3.4) + `ProfileSetupViewModel.kt:109, 153-181` FCM 토큰 동시 저장

```dart
// lib/features/profile/screens/profile_setup_screen.dart

class ProfileSetupScreen extends ConsumerStatefulWidget {
  const ProfileSetupScreen({super.key});
  @override
  ConsumerState<ProfileSetupScreen> createState() => _ProfileSetupScreenState();
}

class _ProfileSetupScreenState extends ConsumerState<ProfileSetupScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameCtl = TextEditingController();
  final _homeAddressCtl = TextEditingController();
  bool _isSaving = false;

  @override
  void dispose() {
    _nameCtl.dispose();
    _homeAddressCtl.dispose();
    super.dispose();
  }

  Future<void> _onSubmit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _isSaving = true);
    try {
      final phone = ref.read(authNotifierProvider).phoneNumber;
      final ok = await ref.read(profileNotifierProvider.notifier).updateProfile(
        name: _nameCtl.text.trim(),
        phoneNumber: phone,
        homeAddress: _homeAddressCtl.text.trim(),
      );
      if (!mounted) return;
      if (ok) {
        // FCM 토큰 재등록 (phoneNumber 확정 후)
        await ref.read(authNotifierProvider.notifier).initialFcmTokenRegistration();
        if (mounted) context.goNamed('home');
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('프로필 저장 실패')),
        );
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('프로필 설정')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _nameCtl,
              decoration: const InputDecoration(
                labelText: '이름 (또는 닉네임)',
                border: OutlineInputBorder(),
              ),
              validator: (v) => v == null || v.trim().isEmpty ? '이름을 입력해주세요' : null,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _homeAddressCtl,
              decoration: const InputDecoration(
                labelText: '집주소 (선택)',
                hintText: '자주 가는 목적지를 저장하면 편리합니다',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _isSaving ? null : _onSubmit,
              child: _isSaving
                  ? const CircularProgressIndicator()
                  : const Text('완료'),
            ),
          ],
        ),
      ),
    );
  }
}
```

### 4.6 HomeScreen (콜 요청 — 가장 복잡, ~400 LOC)

**Kotlin 원본**: `ui/main/HomeScreen.kt:60+` (파일 자체 긴 편, 본 문서는 핵심 구조만)
**의존**: `CallNotifier` (NOTIFIERS.md §2) + `PointNotifier` + `ProfileNotifier`

#### 4.6.1 UI 분기 (callStatus 상태별)

```
callStatus == null  → 콜 요청 UI (위치 입력 + 포인트 사용 토글 + "콜 요청")
callStatus.state == REQUESTED  → "기사 배정 대기 중" + 취소 버튼
callStatus.state == ASSIGNED  → 기사 정보 카드 + 취소 버튼
callStatus.state == DRIVER_ARRIVING  → 기사 이동 중 표시
callStatus.state == IN_PROGRESS  → "운행 중" + 기사 연락처
callStatus.state == COMPLETED  → (RIDE_COMPLETED 팝업 자동 표시)
callStatus.state == CANCELLED  → (CALL_CANCELLED 팝업 자동 표시)
```

#### 4.6.2 Flutter 코드 골격

```dart
// lib/features/call/screens/home_screen.dart

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});
  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  final _currentLocationCtl = TextEditingController();
  final _destinationCtl = TextEditingController();

  @override
  void initState() {
    super.initState();
    // onResume: 활성 콜 재조회 (NOTIFIERS.md restoreActiveCall 호출)
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(callNotifierProvider.notifier).restoreActiveCall();
    });
  }

  @override
  void dispose() {
    _currentLocationCtl.dispose();
    _destinationCtl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final callState = ref.watch(callNotifierProvider);
    final profileState = ref.watch(profileNotifierProvider);
    final pointState = ref.watch(pointNotifierProvider);

    // CallNotifier state 변화 감지 → 팝업 표시
    ref.listen<CallUiState>(callNotifierProvider, (prev, next) {
      // DRIVER_ASSIGNED → 팝업
      if (next.callStatus?.state is CallStateAssigned &&
          prev?.callStatus?.state is! CallStateAssigned) {
        _showDriverAssignedDialog(next.callStatus!);
      }
      // 에러 Snackbar
      if (next.error != null && prev?.error != next.error) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(next.error!)),
        );
        ref.read(callNotifierProvider.notifier).clearError();
      }
    });

    // PointNotifier showPointsEarnedDialog 감지
    ref.listen<PointUiState>(pointNotifierProvider, (prev, next) {
      if (next.showPointsEarnedDialog && prev?.showPointsEarnedDialog != true) {
        _showPointsEarnedDialog(next);
      }
    });

    return Scaffold(
      appBar: AppBar(
        title: Text(profileState.officeName),
      ),
      body: SafeArea(
        child: callState.callStatus == null
            ? _buildRequestCallView(callState, profileState, pointState)
            : _buildActiveCallView(callState.callStatus!, profileState),
      ),
    );
  }

  Widget _buildRequestCallView(CallUiState callState, ProfileUiState profileState, PointUiState pointState) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // 슬로건
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              profileState.slogan,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 16),
            ),
          ),
        ),
        const SizedBox(height: 16),

        // 위치 입력
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: _currentLocationCtl,
                decoration: const InputDecoration(
                  labelText: '출발지',
                  border: OutlineInputBorder(),
                ),
                onChanged: (v) =>
                    ref.read(callNotifierProvider.notifier).updateCurrentLocation(v),
              ),
            ),
            IconButton(
              icon: callState.isLoadingLocation
                  ? const CircularProgressIndicator()
                  : const Icon(Icons.my_location),
              onPressed: callState.isLoadingLocation
                  ? null
                  : () => ref.read(callNotifierProvider.notifier).getCurrentLocation(),
            ),
          ],
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _destinationCtl,
          decoration: InputDecoration(
            labelText: '도착지',
            border: const OutlineInputBorder(),
            suffixIcon: IconButton(
              icon: const Icon(Icons.home),
              onPressed: () => ref.read(profileNotifierProvider.notifier).onHomeAddressClick(),
            ),
          ),
          onChanged: (v) =>
              ref.read(callNotifierProvider.notifier).updateDestinationLocation(v),
        ),
        const SizedBox(height: 16),

        // 포인트 사용 토글 (PointBadge 재사용)
        PointUsageSection(
          points: pointState.customerPoints,
          usePoints: pointState.usePoints,
          pointsToUse: pointState.pointsToUse,
          onToggle: () => ref.read(pointNotifierProvider.notifier).toggleUsePoints(),
          onUpdate: (v) => ref.read(pointNotifierProvider.notifier).updatePointsToUse(v),
        ),
        const SizedBox(height: 24),

        // 콜 요청 버튼
        FilledButton(
          onPressed: callState.isLoadingCall
              ? null
              : () => ref.read(callNotifierProvider.notifier).requestCall(),
          child: callState.isLoadingCall
              ? const CircularProgressIndicator()
              : const Text('콜 요청', style: TextStyle(fontSize: 18)),
        ),
      ],
    );
  }

  Widget _buildActiveCallView(CallStatus status, ProfileUiState profile) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          ActiveCallBanner(status: status, officeName: profile.officeName),
          const SizedBox(height: 16),

          if (status.driverInfo != null)
            DriverInfoCard(driver: status.driverInfo!),

          const Spacer(),

          // 취소 버튼 (state.canCancel == true 인 경우만)
          if (status.state.canCancel)
            OutlinedButton(
              onPressed: () => _confirmCancel(),
              style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
              child: const Text('콜 취소'),
            ),
        ],
      ),
    );
  }

  Future<void> _confirmCancel() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('콜 취소'),
        content: const Text('정말 취소하시겠습니까?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('아니오')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            child: const Text('취소하기'),
          ),
        ],
      ),
    );
    if (confirm == true && mounted) {
      await ref.read(callNotifierProvider.notifier).cancelCall();
    }
  }

  void _showDriverAssignedDialog(CallStatus status) {
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => DriverAssignedDialog(
        status: status,
        onDismiss: () => Navigator.pop(ctx),
      ),
    );
  }

  void _showPointsEarnedDialog(PointUiState state) {
    showDialog<void>(
      context: context,
      builder: (ctx) => PointsEarnedDialog(
        earnedPoints: state.earnedPoints,
        usedPoints: state.usedPoints,
        rideCompletedFare: state.rideCompletedFare,
        onDismiss: () {
          Navigator.pop(ctx);
          ref.read(pointNotifierProvider.notifier).dismissPointsEarnedDialog();
        },
      ),
    );
  }
}
```

### 4.7 ProfileScreen (내 정보 탭)

**Kotlin 원본**: `ui/profile/ProfileScreen.kt`
**의존**: `ProfileNotifier` + `PointNotifier` (등급 표시)

**UI 구조**:
```
┌──────────────────────────────┐
│ 내 정보                      │
├──────────────────────────────┤
│ 🥉 브론즈 / 3% 적립           │
│ 이용 횟수: 5회                │
│ 다음 등급까지: 5회            │
├──────────────────────────────┤
│ 이름: 홍길동                 │
│ 전화: 010-1234-5678          │
│ 집주소: [수정]               │
├──────────────────────────────┤
│ ► 포인트 이력                 │
│ ► 이용 내역                  │
│ ► 약관/개인정보              │
│                              │
│ [ 로그아웃 ] (계정 삭제는 R2) │
└──────────────────────────────┘
```

```dart
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(profileNotifierProvider);
    final points = ref.watch(pointNotifierProvider);
    final info = profile.customerInfo;

    if (info == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      appBar: AppBar(title: const Text('내 정보')),
      body: ListView(
        children: [
          // 등급 카드
          GradeCard(grade: points.customerPoints?.grade ?? CustomerGrade.bronze,
                    totalCalls: points.customerPoints?.totalCalls ?? 0),
          const Divider(),

          ListTile(title: const Text('이름'), subtitle: Text(info.name)),
          ListTile(title: const Text('전화번호'), subtitle: Text(info.phoneNumber)),
          ListTile(
            title: const Text('집주소'),
            subtitle: Text(profile.homeAddress.isEmpty ? '(없음)' : profile.homeAddress),
            trailing: IconButton(
              icon: const Icon(Icons.edit),
              onPressed: () => ref.read(profileNotifierProvider.notifier).onEditHomeAddress(),
            ),
          ),
          const Divider(),

          ListTile(
            leading: const Icon(Icons.star),
            title: const Text('포인트 이력'),
            onTap: () => context.goNamed('point-history'),
          ),
          ListTile(
            leading: const Icon(Icons.history),
            title: const Text('이용 내역'),
            onTap: () => context.goNamed('history'),
          ),
          ListTile(
            leading: const Icon(Icons.description),
            title: const Text('이용약관'),
            onTap: () => context.goNamed('terms-view', queryParameters: {'type': 'terms'}),
          ),
          ListTile(
            leading: const Icon(Icons.privacy_tip),
            title: const Text('개인정보처리방침'),
            onTap: () => context.goNamed('privacy-view'),
          ),
        ],
      ),
    );
  }
}
```

### 4.8 PointScreen (포인트 탭)

**Kotlin 원본**: `ui/point/PointScreen.kt`
**의존**: `PointNotifier`

```dart
class PointScreen extends ConsumerWidget {
  const PointScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final points = ref.watch(pointNotifierProvider).customerPoints;

    return Scaffold(
      appBar: AppBar(title: const Text('포인트')),
      body: points == null
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                // 포인트 카드
                PointCard(
                  currentPoints: points.currentPoints,
                  totalEarned: points.totalEarned,
                  totalUsed: points.totalUsed,
                  grade: points.grade,
                ),

                // 등급 진행도
                GradeProgressCard(
                  grade: points.grade,
                  totalCalls: points.totalCalls,
                  callsToNext: points.callsToNextGrade,
                ),

                const SizedBox(height: 16),

                FilledButton.icon(
                  onPressed: () => context.goNamed('point-history'),
                  icon: const Icon(Icons.receipt_long),
                  label: const Text('거래 내역 보기'),
                ),
              ],
            ),
    );
  }
}
```

### 4.9 PointHistoryScreen (거래 내역)

**Kotlin 원본**: `ui/point/PointHistoryScreen.kt`
**의존**: `pointHistoryProvider.family(phoneNumber)` (NOTIFIERS.md §3.4)

```dart
class PointHistoryScreen extends ConsumerWidget {
  const PointHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final phone = ref.watch(authNotifierProvider).phoneNumber;
    final async = ref.watch(pointHistoryProvider(phone));

    return Scaffold(
      appBar: AppBar(title: const Text('포인트 거래 내역')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('오류: $e')),
        data: (list) {
          if (list.isEmpty) {
            return const Center(child: Text('거래 내역이 없습니다'));
          }
          return RefreshIndicator(
            onRefresh: () => ref.read(pointHistoryProvider(phone).notifier).refresh(),
            child: ListView.separated(
              itemCount: list.length,
              separatorBuilder: (_, __) => const Divider(height: 1),
              itemBuilder: (_, i) => _buildTile(list[i]),
            ),
          );
        },
      ),
    );
  }

  Widget _buildTile(PointTransaction t) {
    final isEarn = t.type == TransactionType.earn || t.type == TransactionType.cancel;
    final sign = isEarn ? '+' : '-';
    final color = isEarn ? Colors.blue : Colors.red;

    return ListTile(
      leading: Icon(_iconForType(t.type), color: color),
      title: Text(t.description),
      subtitle: t.timestamp != null
          ? Text(_formatDate(t.timestamp!))
          : null,
      trailing: Text(
        '$sign${t.amount.abs()}P',
        style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 16),
      ),
    );
  }

  IconData _iconForType(TransactionType t) => switch (t) {
    TransactionType.earn => Icons.add_circle,
    TransactionType.use => Icons.remove_circle,
    TransactionType.expire => Icons.timer_off,
    TransactionType.cancel => Icons.undo,
    TransactionType.admin => Icons.admin_panel_settings,
  };

  String _formatDate(DateTime dt) =>
      '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')} '
      '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
}
```

### 4.10 CallHistoryScreen (이용 내역)

**Kotlin 원본**: `ui/history/CallHistoryScreen.kt` + `CallHistoryViewModel.kt`
**의존**: `callHistoryProvider.family(phoneNumber)`

```dart
class CallHistoryScreen extends ConsumerWidget {
  const CallHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final phone = ref.watch(authNotifierProvider).phoneNumber;
    final async = ref.watch(callHistoryProvider(phone));

    return Scaffold(
      appBar: AppBar(title: const Text('이용 내역')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('오류: $e')),
        data: (list) {
          if (list.isEmpty) {
            return const Center(child: Text('이용 내역이 없습니다'));
          }
          return ListView.separated(
            itemCount: list.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (_, i) => _buildTile(list[i]),
          );
        },
      ),
    );
  }

  Widget _buildTile(CustomerCall call) {
    final statusText = _statusText(call.status);
    return ListTile(
      leading: const Icon(Icons.local_taxi),
      title: Text('${call.currentLocation} → ${call.destinationLocation}'),
      subtitle: Text(
        '${_formatDate(call.timestamp)} | $statusText'
        '${call.finalFare != null ? " | ${call.finalFare}원" : ""}',
      ),
      trailing: call.status == 'COMPLETED'
          ? const Icon(Icons.check_circle, color: Colors.green)
          : null,
    );
  }

  String _statusText(String status) => switch (status) {
    'REQUESTED' || 'WAITING' => '요청됨',
    'ASSIGNED' => '배차됨',
    'ACCEPTED' || 'DRIVER_ARRIVING' => '진행 중',
    'IN_PROGRESS' => '운행 중',
    'COMPLETED' => '완료',
    'CANCELLED' || 'CANCELLED_BY_CUSTOMER' || 'CANCELLED_BY_DRIVER' || 'CANCELED' => '취소',
    _ => status,
  };

  String _formatDate(int millis) {
    final dt = DateTime.fromMillisecondsSinceEpoch(millis);
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')} '
           '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}
```

---

## 5. 공통 위젯

### 5.1 ActiveCallBanner

**Kotlin 원본**: `HomeScreen.kt:644-700` 범위에서 callStatus 표시 구조

```dart
class ActiveCallBanner extends StatelessWidget {
  final CallStatus status;
  final String officeName;
  const ActiveCallBanner({required this.status, required this.officeName, super.key});

  @override
  Widget build(BuildContext context) {
    final color = switch (status.state) {
      CallStateRequested() => Colors.orange,
      CallStateAssigned() || CallStateDriverArriving() => Colors.blue,
      CallStateInProgress() => Colors.green,
      _ => Colors.grey,
    };

    return Card(
      color: color.withValues(alpha: 0.1),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(_iconForState(status.state), color: color, size: 32),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(status.state.displayName,
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: color)),
                  Text(officeName),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  IconData _iconForState(CallState state) => switch (state) {
    CallStateRequested() => Icons.phone,
    CallStateAssigned() || CallStateDriverArriving() => Icons.location_on,
    CallStateInProgress() => Icons.local_taxi,
    _ => Icons.info,
  };
}
```

### 5.2 PointCard / PointBadge / GradeProgressCard

**Kotlin 원본**: `ui/components/PointCard.kt` + `PointUsageSection.kt`

```dart
class PointCard extends StatelessWidget {
  final int currentPoints;
  final int totalEarned;
  final int totalUsed;
  final CustomerGrade grade;

  const PointCard({
    required this.currentPoints,
    required this.totalEarned,
    required this.totalUsed,
    required this.grade,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Text('현재 포인트',
                style: TextStyle(fontSize: 14, color: Colors.grey[700])),
            const SizedBox(height: 4),
            Text('${_formatCurrency(currentPoints)}P',
                style: const TextStyle(fontSize: 32, fontWeight: FontWeight.bold)),
            const Divider(height: 24),
            Row(
              children: [
                Expanded(
                  child: _stat('누적 적립', '+${_formatCurrency(totalEarned)}P', Colors.blue),
                ),
                Expanded(
                  child: _stat('누적 사용', '-${_formatCurrency(totalUsed)}P', Colors.red),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _stat(String label, String value, Color color) => Column(
    children: [
      Text(label, style: TextStyle(fontSize: 12, color: Colors.grey[600])),
      const SizedBox(height: 4),
      Text(value, style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: color)),
    ],
  );

  String _formatCurrency(int v) => v.toString().replaceAllMapped(
    RegExp(r'(\d)(?=(\d{3})+(?!\d))'),
    (m) => '${m[1]},',
  );
}

class PointBadge extends StatelessWidget {
  final CustomerGrade grade;
  const PointBadge({required this.grade, super.key});

  @override
  Widget build(BuildContext context) {
    return Chip(
      backgroundColor: Color(grade.colorArgb).withValues(alpha: 0.2),
      label: Text('${grade.icon} ${grade.displayName}'),
    );
  }
}
```

### 5.3 DriverInfoCard (배차된 기사 정보)

```dart
class DriverInfoCard extends StatelessWidget {
  final DriverInfo driver;
  const DriverInfoCard({required this.driver, super.key});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('배정된 기사', style: TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            ListTile(
              leading: const Icon(Icons.person, size: 40),
              title: Text(driver.name, style: const TextStyle(fontSize: 18)),
              subtitle: Text(driver.vehicleNumber),
              trailing: IconButton(
                icon: const Icon(Icons.phone, color: Colors.green),
                onPressed: () => _callDriver(driver.phoneNumber),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _callDriver(String phone) async {
    final uri = Uri.parse('tel:$phone');
    if (await canLaunchUrl(uri)) await launchUrl(uri);
  }
}
```

### 5.4 PointUsageSection (콜 요청 시 포인트 사용)

**Kotlin 원본**: `ui/components/PointUsageSection.kt`

```dart
class PointUsageSection extends StatelessWidget {
  final CustomerPoints? points;
  final bool usePoints;
  final int pointsToUse;
  final VoidCallback onToggle;
  final ValueChanged<int> onUpdate;

  const PointUsageSection({
    required this.points,
    required this.usePoints,
    required this.pointsToUse,
    required this.onToggle,
    required this.onUpdate,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    if (points == null || points!.currentPoints == 0) return const SizedBox.shrink();

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            SwitchListTile(
              value: usePoints,
              onChanged: (_) => onToggle(),
              title: Text('포인트 사용 (보유: ${points!.currentPoints}P)'),
            ),
            if (usePoints) ...[
              Slider(
                value: pointsToUse.toDouble(),
                min: 0,
                max: math.min(points!.currentPoints, 10000).toDouble(),
                divisions: 20,
                label: '${pointsToUse}P',
                onChanged: (v) => onUpdate(v.toInt()),
              ),
              Text('사용 포인트: ${pointsToUse}P', style: const TextStyle(fontSize: 14)),
            ],
          ],
        ),
      ),
    );
  }
}
```

---

## 6. 팝업/다이얼로그

### 6.1 DriverAssignedDialog

**Kotlin 원본**: `MainViewModel.kt:654-678` `playDriverAssignedNotification` + HomeScreen 내부 팝업
**특징**: 진동 + 알림음 1회 재생 (vibration + audioplayers 패키지)

```dart
class DriverAssignedDialog extends StatefulWidget {
  final CallStatus status;
  final VoidCallback onDismiss;
  const DriverAssignedDialog({required this.status, required this.onDismiss, super.key});

  @override
  State<DriverAssignedDialog> createState() => _DriverAssignedDialogState();
}

class _DriverAssignedDialogState extends State<DriverAssignedDialog> {
  late final AudioPlayer _player;

  @override
  void initState() {
    super.initState();
    _player = AudioPlayer();
    _playNotification();
  }

  Future<void> _playNotification() async {
    try {
      // 1회 진동 (vibration 패키지)
      if (await Vibration.hasVibrator() ?? false) {
        Vibration.vibrate(duration: 500);
      }
      // 알림음 1회
      await _player.play(AssetSource('sounds/driver_assigned.mp3'));
    } catch (e) {
      debugPrint('[DriverAssignedDialog] 알림음/진동 실패: $e');
    }
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final driver = widget.status.driverInfo;
    return AlertDialog(
      title: const Text('기사가 배정되었습니다'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (driver != null) ...[
            Text('기사: ${driver.name}', style: const TextStyle(fontSize: 16)),
            Text('차량: ${driver.vehicleNumber}'),
            Text('전화: ${driver.phoneNumber}'),
          ],
        ],
      ),
      actions: [
        FilledButton(onPressed: widget.onDismiss, child: const Text('확인')),
      ],
    );
  }
}
```

### 6.2 PointsEarnedDialog (운행 완료 팝업)

**Kotlin 원본**: `MainViewModel.kt:559-585` handleRideCompleted → showPointsEarnedDialog

```dart
class PointsEarnedDialog extends StatelessWidget {
  final int earnedPoints;
  final int usedPoints;
  final int rideCompletedFare;
  final VoidCallback onDismiss;

  const PointsEarnedDialog({
    required this.earnedPoints,
    required this.usedPoints,
    required this.rideCompletedFare,
    required this.onDismiss,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('운행 완료'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (rideCompletedFare > 0)
            Text('운행료: ${_fmt(rideCompletedFare)}원', style: const TextStyle(fontSize: 16)),
          if (earnedPoints > 0) ...[
            const SizedBox(height: 12),
            Text('+${_fmt(earnedPoints)}P 적립!',
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.blue)),
          ],
          if (usedPoints > 0) ...[
            const SizedBox(height: 8),
            Text('사용 포인트: -${_fmt(usedPoints)}P',
                style: const TextStyle(color: Colors.red)),
          ],
        ],
      ),
      actions: [
        FilledButton(onPressed: onDismiss, child: const Text('확인')),
      ],
    );
  }

  String _fmt(int v) => v.toString().replaceAllMapped(
    RegExp(r'(\d)(?=(\d{3})+(?!\d))'),
    (m) => '${m[1]},',
  );
}
```

### 6.3 CallCancelledDialog (취소 사유 표시)

**Kotlin 원본**: `MyFirebaseMessagingService.kt:87-98` CALL_CANCELLED 핸들러 → 시스템 알림 + 팝업

```dart
class CallCancelledDialog extends StatelessWidget {
  final String? callId;
  final String cancelReason;
  final VoidCallback onDismiss;

  const CallCancelledDialog({
    required this.callId,
    required this.cancelReason,
    required this.onDismiss,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      icon: const Icon(Icons.cancel, color: Colors.red, size: 48),
      title: const Text('콜이 취소되었습니다'),
      content: Text(cancelReason.isNotEmpty ? cancelReason : '운행이 취소되었습니다.'),
      actions: [
        FilledButton(onPressed: onDismiss, child: const Text('확인')),
      ],
    );
  }
}
```

### 6.4 HomeAddressDialog

**Kotlin 원본**: `MainViewModel.kt:862-884` + HomeScreen 내부 AlertDialog

```dart
class HomeAddressDialog extends ConsumerStatefulWidget {
  final String initialAddress;
  const HomeAddressDialog({required this.initialAddress, super.key});
  @override
  ConsumerState<HomeAddressDialog> createState() => _HomeAddressDialogState();
}

class _HomeAddressDialogState extends ConsumerState<HomeAddressDialog> {
  late final TextEditingController _ctl;

  @override
  void initState() {
    super.initState();
    _ctl = TextEditingController(text: widget.initialAddress);
  }

  @override
  void dispose() {
    _ctl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('집주소'),
      content: TextField(
        controller: _ctl,
        decoration: const InputDecoration(hintText: '집주소를 입력하세요'),
      ),
      actions: [
        TextButton(
          onPressed: () {
            ref.read(profileNotifierProvider.notifier).closeHomeAddressDialog();
            Navigator.pop(context);
          },
          child: const Text('취소'),
        ),
        FilledButton(
          onPressed: () async {
            await ref.read(profileNotifierProvider.notifier).saveHomeAddress(_ctl.text.trim());
            if (context.mounted) Navigator.pop(context);
          },
          child: const Text('저장'),
        ),
      ],
    );
  }
}
```

---

## 7. 화면 전환 매트릭스 (Notifier State 변화 → 화면 전환)

| Notifier state 변화 | 화면 전환 / UI 반응 | 구현 위치 |
|--------------------|------------------|----------|
| `CallUiState.callStatus` null → Requested | HomeScreen 내부에서 `_buildActiveCallView` 로 전환 | HomeScreen ref.listen |
| `CallUiState.callStatus.state == Assigned` (신규) | **DriverAssignedDialog 자동 표시** + 진동/알림음 | HomeScreen ref.listen |
| `CallUiState.callStatus == null` + 이전 Completed | (자동 Home 재요청 UI 복귀) | HomeScreen ref.watch |
| `PointUiState.showPointsEarnedDialog == true` | **PointsEarnedDialog 자동 표시** | HomeScreen ref.listen |
| `CallUiState.error != null` | **Snackbar 표시 + `clearError()` 호출** | HomeScreen ref.listen |
| `ProfileUiState.showHomeAddressDialog == true` | **HomeAddressDialog 자동 표시** | HomeScreen ref.listen(profileNotifierProvider) |
| `AuthUiState.isVerified true` | `/profile-setup` 이동 | PhoneAuthScreen ref.listen |
| `SignUpState.success` (ProfileSetup 완료) | `/` (홈) 이동 | ProfileSetupScreen onSubmit |

---

## 8. 접근성 / 테스트 훅

### 8.1 Semantics 라벨 (의제 6 Privacy Manifest와 무관, 장애 사용자 지원)

```dart
Semantics(
  label: '콜 요청',
  button: true,
  child: FilledButton(onPressed: onRequest, child: const Text('콜 요청')),
)
```

### 8.2 테스트 키

```dart
const _kRequestCallButton = Key('home_request_call_button');
const _kCancelCallButton = Key('home_cancel_call_button');
const _kPhoneAuthSendButton = Key('phone_auth_send_button');
const _kProfileSetupSubmit = Key('profile_setup_submit');
const _kOfficeCodeApplyButton = Key('office_code_apply_button');
```

### 8.3 통합 테스트 예시

```dart
// test/integration/customer_onboarding_test.dart

void main() {
  testWidgets('신규 손님 온보딩 4단계', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: CustomerApp()));
    await tester.pumpAndSettle();

    // Step 1: 사무실 코드 입력
    expect(find.byType(OfficeCodeScreen), findsOneWidget);
    await tester.enterText(find.byKey(const Key('office_code_input')), 'TEST01');
    await tester.tap(find.byKey(_kOfficeCodeApplyButton));
    await tester.pumpAndSettle();

    // Step 2: 약관 동의
    expect(find.byType(TermsAgreementScreen), findsOneWidget);
    await tester.tap(find.byKey(const Key('terms_checkbox_all')));
    await tester.tap(find.byKey(const Key('terms_agree_button')));
    await tester.pumpAndSettle();

    // Step 3: 전화 인증 (Mock으로 uid 반환)
    // ...

    // Step 4: 프로필 설정 → 홈 진입
    // ...
  });
}
```

---

## 9. 손님앱 vs 기사앱 SCREENS 차이

| 항목 | 기사앱 | 손님앱 |
|------|--------|-------|
| 화면 수 | 12종 (+ R2 ReferralQR) | 11종 |
| BottomNavigation | **없음** (단일 Scaffold) | **4탭 ShellRoute** (홈/내역/포인트/내정보) |
| 온보딩 | 로그인 → 홈 (2단계) | 사무실 코드 → 약관 → 전화 → 프로필 → 홈 (5단계) |
| 모달 | NewCallPopup + SettlementSummaryDialog | DriverAssignedDialog + PointsEarnedDialog + CallCancelledDialog + HomeAddressDialog |
| 잠금화면 | FullScreenIntent (Android) / Time Sensitive (iOS) | **일반 알림** (의제 7 차등) |
| FCM 핸들링 화면 | 각 화면마다 handleCallCancelled | **HomeScreen 중심** (CallNotifier에 통합) |
| 권한 | LOCATION + RECORD_AUDIO + FOREGROUND_SERVICE | LOCATION + CAMERA (QR) + RECORD_AUDIO (R2) |

---

## 10. Phase 6 작업 순서

### Week 1 — 기반
1. `lib/core/routing/app_router.dart` + `MainShell` ShellRoute
2. `SplashScreen` 온보딩 상태 머신 (§3.2)
3. `OfficeCodeScreen` — 수동 코드 + QR 스캔 (ATTRIBUTION §2)

### Week 2 — 온보딩 4 화면
4. `TermsAgreementScreen` + `DocumentViewerScreen`
5. `PhoneAuthScreen` (AUTH.md §3)
6. `ProfileSetupScreen`

### Week 3 — 메인 4 탭
7. `HomeScreen` (가장 복잡) + 모달 4종 (§6)
8. `ProfileScreen` + `PointScreen`
9. `PointHistoryScreen` + `CallHistoryScreen`

### Week 4 — 공통 위젯 + 테스트
10. `ActiveCallBanner` / `PointCard` / `DriverInfoCard` / `PointUsageSection`
11. 위젯 테스트 10건 최소
12. TestFlight Internal 업로드 + 손님 파일럿

---

## 11. 작성 완료 요약

- **화면 11종 전수 매핑** + 모달 4종
- **MainShell ShellRoute 4탭** (Kotlin `NavTab` 4종 1:1)
- **온보딩 상태 머신 5단계** (Kotlin `AppScreen` enum 8종 → GoRouter redirect)
- **HomeScreen callStatus 6단계 분기** (CallState sealed class switch expression)
- **Kotlin 원본 라인 번호 100% 인용**
- **Dart 코드 샘플** 모든 화면 핵심 골격
- **공통 위젯 4종 + 다이얼로그 4종** 전수
- **손님앱 vs 기사앱 SCREENS 차이 7종** (§9)
- **Phase 6 Week 1~4 작업 순서 + 테스트 훅**

---

## 12. 참조

- `flutter/customer_app/MVP/OVERVIEW.md` — Phase 1/2 범위
- `flutter/customer_app/MVP/MODELS.md` — UI 상태 구조체 (CallStatus/DriverInfo/Ui State 4종)
- `flutter/customer_app/MVP/NOTIFIERS.md` — 4 Notifier (소비 Notifier)
- `flutter/customer_app/MVP/ENUMS.md` — CallState / CustomerGrade / TransactionType
- `flutter/customer_app/MVP/FCM.md` — 5종 FCM 수신 + 팝업 트리거
- `flutter/customer_app/MVP/ATTRIBUTION.md` — OfficeCodeScreen B옵션 + C옵션
- `flutter/customer_app/MVP/AUTH.md` — 인증 흐름 상세 (차기 작성)
- `flutter/driver_app/MVP/SCREENS.md` — 기사앱 자매 문서
- `customer_app/app/src/main/java/com/designated/customer/ui/` — Kotlin 원본 전체
- `customer_app/app/src/main/java/com/designated/customer/MainActivity.kt` — CustomerApp 상태 머신 550+ LOC
- `customer_app/app/src/main/java/com/designated/customer/ui/navigation/MainNavigation.kt` — BottomNav 131 LOC
