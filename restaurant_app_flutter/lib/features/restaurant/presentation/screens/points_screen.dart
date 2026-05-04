import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../domain/entities/restaurant_transaction.dart';
import '../notifiers/restaurant_streams.dart';

class PointsScreen extends ConsumerWidget {
  const PointsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final restAsync = ref.watch(restaurantStreamProvider);
    final txAsync = ref.watch(restaurantTransactionsStreamProvider);
    final formatter = NumberFormat('#,###');

    return Scaffold(
      appBar: AppBar(title: const Text('포인트')),
      body: SafeArea(
        child: Column(
          children: [
            Card(
              margin: const EdgeInsets.all(16),
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('현재 잔액',
                        style: TextStyle(color: Colors.grey)),
                    const SizedBox(height: 8),
                    Text(
                      '${formatter.format(restAsync.value?.points ?? 0)}P',
                      style: const TextStyle(
                          fontSize: 36, fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
              ),
            ),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text('거래 내역',
                    style: TextStyle(
                        fontSize: 16, fontWeight: FontWeight.bold)),
              ),
            ),
            Expanded(
              child: txAsync.when(
                data: (list) {
                  if (list.isEmpty) {
                    return const Center(child: Text('거래 내역이 없습니다.'));
                  }
                  return ListView.builder(
                    padding: const EdgeInsets.all(8),
                    itemCount: list.length,
                    itemBuilder: (_, i) => _txTile(list[i], formatter),
                  );
                },
                loading: () =>
                    const Center(child: CircularProgressIndicator()),
                error: (e, _) => Center(child: Text('오류: $e')),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _txTile(RestaurantTransaction tx, NumberFormat formatter) {
    final isPositive = tx.amount >= 0;
    final dateStr = tx.timestamp != null
        ? DateFormat('MM/dd HH:mm').format(tx.timestamp!)
        : '-';
    return Card(
      child: ListTile(
        leading: Icon(
          isPositive ? Icons.add_circle : Icons.remove_circle,
          color: isPositive ? Colors.green : Colors.red,
        ),
        title: Text(tx.description.isEmpty ? tx.type : tx.description),
        subtitle: Text(dateStr),
        trailing: Text(
          '${isPositive ? '+' : ''}${formatter.format(tx.amount)}P',
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: isPositive ? Colors.green : Colors.red,
          ),
        ),
      ),
    );
  }
}
