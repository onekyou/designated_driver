import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'main_shell.dart';
import 'placeholder_screen.dart';

/// GoRouter 골격 (SCREENS.md §2.1 1:1 이관)
/// Chunk 3: 라우트만 선언 + PlaceholderScreen. 실제 화면은 Chunk 4+.
final goRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/splash',
    routes: [
      GoRoute(
        path: '/splash',
        name: 'splash',
        builder: (_, _) => const PlaceholderScreen(title: 'Splash'),
      ),
      GoRoute(
        path: '/office-code',
        name: 'office-code',
        builder: (_, _) => const PlaceholderScreen(title: 'OfficeCode'),
      ),
      GoRoute(
        path: '/auth/terms',
        name: 'terms',
        builder: (_, _) => const PlaceholderScreen(title: 'Terms'),
        routes: [
          GoRoute(
            path: 'view',
            name: 'terms-view',
            builder: (_, state) => PlaceholderScreen(
              title:
                  'DocumentViewer(${state.uri.queryParameters['type'] ?? 'terms'})',
            ),
          ),
        ],
      ),
      GoRoute(
        path: '/auth/privacy',
        name: 'privacy-view',
        builder: (_, _) =>
            const PlaceholderScreen(title: 'DocumentViewer(privacy)'),
      ),
      GoRoute(
        path: '/auth/phone',
        name: 'phone-auth',
        builder: (_, _) => const PlaceholderScreen(title: 'PhoneAuth'),
      ),
      GoRoute(
        path: '/profile/setup',
        name: 'profile-setup',
        builder: (_, _) => const PlaceholderScreen(title: 'ProfileSetup'),
      ),
      ShellRoute(
        builder: (_, _, child) => MainShell(child: child),
        routes: [
          GoRoute(
            path: '/',
            name: 'home',
            pageBuilder: (_, _) => const NoTransitionPage(
              child: PlaceholderScreen(title: 'Home'),
            ),
          ),
          GoRoute(
            path: '/history',
            name: 'history',
            pageBuilder: (_, _) => const NoTransitionPage(
              child: PlaceholderScreen(title: 'History'),
            ),
          ),
          GoRoute(
            path: '/points',
            name: 'points',
            pageBuilder: (_, _) => const NoTransitionPage(
              child: PlaceholderScreen(title: 'Points'),
            ),
            routes: [
              GoRoute(
                path: 'history',
                name: 'point-history',
                builder: (_, _) =>
                    const PlaceholderScreen(title: 'PointHistory'),
              ),
            ],
          ),
          GoRoute(
            path: '/profile',
            name: 'profile',
            pageBuilder: (_, _) => const NoTransitionPage(
              child: PlaceholderScreen(title: 'Profile'),
            ),
          ),
        ],
      ),
    ],
  );
});
