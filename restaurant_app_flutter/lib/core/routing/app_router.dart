import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/restaurant/presentation/notifiers/restaurant_ids_notifier.dart';
import '../../features/restaurant/presentation/screens/app_call_screen.dart';
import '../../features/restaurant/presentation/screens/home_screen.dart';
import '../../features/restaurant/presentation/screens/points_screen.dart';
import '../../features/restaurant/presentation/screens/signup_screen.dart';
import '../../features/restaurant/presentation/screens/simple_call_screen.dart';
import '../../features/restaurant/presentation/screens/taxi_call_screen.dart';

/// 식당앱 root navigator key — RestaurantNotificationService.attach() 가 사용.
final navigatorKeyProvider = Provider<GlobalKey<NavigatorState>>(
  (_) => GlobalKey<NavigatorState>(),
);

/// 식당앱 라우팅.
/// 진입 흐름: 가입 전(ids null) → /signup, 이후 → /
/// home의 4메뉴 → /call/simple, /call/app, /call/taxi, /points
final goRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    navigatorKey: ref.watch(navigatorKeyProvider),
    initialLocation: '/',
    redirect: (context, state) {
      final ids = ref.read(restaurantIdsProvider);
      final loc = state.matchedLocation;
      if (ids == null && loc != '/signup') return '/signup';
      if (ids != null && loc == '/signup') return '/';
      return null;
    },
    routes: [
      GoRoute(
        path: '/signup',
        name: 'signup',
        builder: (_, __) => const SignupScreen(),
      ),
      GoRoute(
        path: '/',
        name: 'home',
        builder: (_, __) => const HomeScreen(),
      ),
      GoRoute(
        path: '/call/simple',
        name: 'call-simple',
        builder: (_, __) => const SimpleCallScreen(),
      ),
      GoRoute(
        path: '/call/app',
        name: 'call-app',
        builder: (_, __) => const AppCallScreen(),
      ),
      GoRoute(
        path: '/call/taxi',
        name: 'call-taxi',
        builder: (_, __) => const TaxiCallScreen(),
      ),
      GoRoute(
        path: '/points',
        name: 'points',
        builder: (_, __) => const PointsScreen(),
      ),
    ],
  );
});
