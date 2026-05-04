import 'package:flutter/material.dart';

/// 결제 방식 선택 라디오 (CASH / RESTAURANT_POINT).
/// PR 4 통합 결제 옵션 — 호출 화면 두 곳(simple/app)에서 공통 사용.
class PaymentMethodPicker extends StatelessWidget {
  const PaymentMethodPicker({
    super.key,
    required this.value,
    required this.onChanged,
  });

  final String value;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 4, vertical: 6),
              child: Text('결제 방식',
                  style: TextStyle(fontWeight: FontWeight.bold)),
            ),
            RadioListTile<String>(
              title: const Text('현금 (CASH)'),
              value: 'CASH',
              groupValue: value,
              onChanged: (v) => v != null ? onChanged(v) : null,
              dense: true,
            ),
            RadioListTile<String>(
              title: const Text('포인트 결제 (RESTAURANT_POINT)'),
              subtitle: const Text('식당 잔액에서 차감 + 적립 +1,000'),
              value: 'RESTAURANT_POINT',
              groupValue: value,
              onChanged: (v) => v != null ? onChanged(v) : null,
              dense: true,
            ),
          ],
        ),
      ),
    );
  }
}
