import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../notifiers/restaurant_streams.dart';

/// 양평 4개 콜택시 placeholder. 5/6 사용자 확정 (Plan §5 PR 2 결정).
const _yangpyeongTaxis = <(String, String)>[
  ('양평콜택시 1', '031-000-0001'),
  ('양평콜택시 2', '031-000-0002'),
  ('양평콜택시 3', '031-000-0003'),
  ('양평콜택시 4', '031-000-0004'),
];

class TaxiCallScreen extends ConsumerWidget {
  const TaxiCallScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final restAsync = ref.watch(restaurantStreamProvider);
    final myTaxi = restAsync.value?.taxiPhoneNumber ?? '';

    return Scaffold(
      appBar: AppBar(title: const Text('택시호출')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (myTaxi.isNotEmpty) ...[
              const Text('내 단골',
                  style:
                      TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Card(
                child: ListTile(
                  leading: const Icon(Icons.star, color: Colors.amber),
                  title: const Text('단골 콜택시'),
                  subtitle: Text(myTaxi),
                  trailing: const Icon(Icons.phone),
                  onTap: () => _dial(context, myTaxi),
                ),
              ),
              const Divider(height: 32),
            ],
            const Text('양평 콜택시',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            for (final (name, phone) in _yangpyeongTaxis)
              Card(
                child: ListTile(
                  leading: const Icon(Icons.local_taxi),
                  title: Text(name),
                  subtitle: Text(phone),
                  trailing: const Icon(Icons.phone),
                  onTap: () => _dial(context, phone),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _dial(BuildContext context, String phone) async {
    final uri = Uri(scheme: 'tel', path: phone.replaceAll('-', ''));
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri);
    } else if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('전화 앱을 열 수 없습니다: $phone')),
      );
    }
  }
}
