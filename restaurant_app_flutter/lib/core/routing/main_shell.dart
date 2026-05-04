import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// BottomNavigation 4탭 ShellRoute 컨테이너 (SCREENS.md §2.2)
/// Kotlin MainNavigation.kt:31-131 이관 — BackHandler 홈 복귀 포함
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
      canPop: idx == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && idx != 0) {
          context.go('/');
        }
      },
      child: Scaffold(
        body: child,
        bottomNavigationBar: NavigationBar(
          selectedIndex: idx,
          onDestinationSelected: (i) => context.go(_tabs[i].path),
          destinations: _tabs
              .map((t) => NavigationDestination(
                    icon: Icon(t.icon),
                    label: t.label,
                  ))
              .toList(),
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
