import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../notifiers/restaurant_ids_notifier.dart';
import '../notifiers/restaurant_streams.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ids = ref.watch(restaurantIdsProvider);
    if (ids == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (context.mounted) context.go('/signup');
      });
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    final restAsync = ref.watch(restaurantStreamProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(restAsync.value?.name ?? '식당앱'),
        actions: [
          IconButton(
            icon: const Icon(Icons.account_balance_wallet),
            onPressed: () => context.go('/points'),
          ),
        ],
      ),
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
                      const Text('현재 포인트',
                          style: TextStyle(color: Colors.grey)),
                      const SizedBox(height: 4),
                      Text(
                        '${restAsync.value?.points ?? 0}P',
                        style: const TextStyle(
                            fontSize: 32, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const Text('호출',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Expanded(
                child: GridView.count(
                  crossAxisCount: 2,
                  crossAxisSpacing: 12,
                  mainAxisSpacing: 12,
                  children: [
                    _menuTile(context, '단순호출', Icons.flash_on,
                        () => context.go('/call/simple')),
                    _menuTile(context, '앱호출', Icons.directions_car,
                        () => context.go('/call/app')),
                    _menuTile(context, '택시호출', Icons.local_taxi,
                        () => context.go('/call/taxi')),
                    _menuTile(context, '포인트', Icons.savings,
                        () => context.go('/points')),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _menuTile(BuildContext context, String label, IconData icon,
      VoidCallback onTap) {
    return Card(
      child: InkWell(
        onTap: onTap,
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 48),
              const SizedBox(height: 8),
              Text(label, style: const TextStyle(fontSize: 16)),
            ],
          ),
        ),
      ),
    );
  }
}
