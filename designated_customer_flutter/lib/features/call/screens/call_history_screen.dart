import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../models/call_model.dart';
import '../models/call_state.dart';
import '../providers/call_provider.dart';

/// 콜 내역 화면
class CallHistoryScreen extends ConsumerWidget {
  const CallHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final callHistoryAsync = ref.watch(callHistoryProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('콜 내역'),
        centerTitle: true,
      ),
      body: callHistoryAsync.when(
        data: (calls) {
          if (calls.isEmpty) {
            return const _EmptyHistoryView();
          }
          return _CallHistoryList(calls: calls);
        },
        loading: () => const Center(
          child: CircularProgressIndicator(),
        ),
        error: (error, stack) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline, size: 64, color: Colors.red.shade300),
              const SizedBox(height: 16),
              Text(
                '콜 내역을 불러올 수 없습니다',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              Text(
                error.toString(),
                style: Theme.of(context).textTheme.bodySmall,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              ElevatedButton.icon(
                onPressed: () => ref.refresh(callHistoryProvider),
                icon: const Icon(Icons.refresh),
                label: const Text('다시 시도'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 콜 내역 리스트
class _CallHistoryList extends StatelessWidget {
  const _CallHistoryList({required this.calls});

  final List<CustomerCall> calls;

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async {
        // Provider refresh는 여기서 할 수 없으므로,
        // 실제로는 Consumer 외부에서 처리해야 함
      },
      child: ListView.builder(
        padding: const EdgeInsets.all(8.0),
        itemCount: calls.length,
        itemBuilder: (context, index) {
          final call = calls[index];
          return _CallHistoryCard(call: call);
        },
      ),
    );
  }
}

/// 콜 내역 카드
class _CallHistoryCard extends StatelessWidget {
  const _CallHistoryCard({required this.call});

  final CustomerCall call;

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('yyyy.MM.dd HH:mm');
    final statusColor = _getStatusColor(call.status);
    final statusIcon = _getStatusIcon(call.status);

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: InkWell(
        onTap: () => _showCallDetails(context),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 상태 및 날짜
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  // 상태
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: statusColor.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: statusColor),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(statusIcon, size: 16, color: statusColor),
                        const SizedBox(width: 4),
                        Text(
                          call.status.displayText,
                          style: TextStyle(
                            color: statusColor,
                            fontWeight: FontWeight.bold,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),
                  // 날짜
                  Text(
                    dateFormat.format(call.timestamp),
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Colors.grey.shade600,
                        ),
                  ),
                ],
              ),

              const SizedBox(height: 12),

              // 출발지
              Row(
                children: [
                  Icon(Icons.my_location,
                      size: 20, color: Colors.blue.shade600),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      call.currentLocation,
                      style: const TextStyle(fontSize: 15),
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 8),

              // 목적지
              Row(
                children: [
                  Icon(Icons.location_on,
                      size: 20, color: Colors.red.shade600),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      call.destinationLocation,
                      style: const TextStyle(fontSize: 15),
                    ),
                  ),
                ],
              ),

              // 요금 정보 (있는 경우)
              if (call.fare != null) ...[
                const SizedBox(height: 12),
                const Divider(),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text('요금'),
                    Row(
                      children: [
                        if (call.pointsUsed > 0) ...[
                          Text(
                            '${NumberFormat('#,###').format(call.fare)}원',
                            style: TextStyle(
                              decoration: TextDecoration.lineThrough,
                              color: Colors.grey.shade600,
                              fontSize: 13,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            '${NumberFormat('#,###').format(call.finalFare ?? call.fare)}원',
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 16,
                              color: Colors.green,
                            ),
                          ),
                        ] else
                          Text(
                            '${NumberFormat('#,###').format(call.fare)}원',
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 16,
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
                if (call.pointsUsed > 0)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        Icon(Icons.loyalty,
                            size: 14, color: Colors.orange.shade700),
                        const SizedBox(width: 4),
                        Text(
                          '포인트 ${NumberFormat('#,###').format(call.pointsUsed)}원 사용',
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.orange.shade700,
                          ),
                        ),
                      ],
                    ),
                  ),
              ],

              // 메모 (있는 경우)
              if (call.notes != null && call.notes!.isNotEmpty) ...[
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.grey.shade100,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.note, size: 16, color: Colors.grey.shade600),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          call.notes!,
                          style: TextStyle(
                            fontSize: 13,
                            color: Colors.grey.shade700,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  /// 상태별 색상
  Color _getStatusColor(CallState status) {
    switch (status) {
      case CallState.requested:
        return Colors.blue;
      case CallState.assigned:
        return Colors.orange;
      case CallState.driverArriving:
        return Colors.purple;
      case CallState.inProgress:
        return Colors.green;
      case CallState.completed:
        return Colors.grey;
      case CallState.cancelled:
        return Colors.red;
    }
  }

  /// 상태별 아이콘
  IconData _getStatusIcon(CallState status) {
    switch (status) {
      case CallState.requested:
        return Icons.access_time;
      case CallState.assigned:
        return Icons.person_pin_circle;
      case CallState.driverArriving:
        return Icons.directions_car;
      case CallState.inProgress:
        return Icons.navigation;
      case CallState.completed:
        return Icons.check_circle;
      case CallState.cancelled:
        return Icons.cancel;
    }
  }

  /// 콜 상세 정보 표시
  void _showCallDetails(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => _CallDetailSheet(call: call),
    );
  }
}

/// 콜 상세 정보 시트
class _CallDetailSheet extends StatelessWidget {
  const _CallDetailSheet({required this.call});

  final CustomerCall call;

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('yyyy년 MM월 dd일 HH:mm');

    return Container(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 제목
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '콜 상세 정보',
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              IconButton(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close),
              ),
            ],
          ),

          const SizedBox(height: 16),
          const Divider(),
          const SizedBox(height: 16),

          // 콜 ID
          _DetailRow(
            icon: Icons.tag,
            label: '콜 ID',
            value: call.id,
          ),

          const SizedBox(height: 12),

          // 요청 시간
          _DetailRow(
            icon: Icons.schedule,
            label: '요청 시간',
            value: dateFormat.format(call.timestamp),
          ),

          const SizedBox(height: 12),

          // 상태
          _DetailRow(
            icon: Icons.info_outline,
            label: '상태',
            value: call.status.displayText,
          ),

          const SizedBox(height: 12),

          // 출발지
          _DetailRow(
            icon: Icons.my_location,
            label: '출발지',
            value: call.currentLocation,
          ),

          const SizedBox(height: 12),

          // 목적지
          _DetailRow(
            icon: Icons.location_on,
            label: '목적지',
            value: call.destinationLocation,
          ),

          if (call.notes != null && call.notes!.isNotEmpty) ...[
            const SizedBox(height: 12),
            _DetailRow(
              icon: Icons.note,
              label: '메모',
              value: call.notes!,
            ),
          ],

          const SizedBox(height: 24),

          // 닫기 버튼
          FilledButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('닫기'),
          ),

          const SizedBox(height: 8),
        ],
      ),
    );
  }
}

/// 상세 정보 행
class _DetailRow extends StatelessWidget {
  const _DetailRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 20, color: Colors.grey.shade600),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey.shade600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                value,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// 빈 내역 뷰
class _EmptyHistoryView extends StatelessWidget {
  const _EmptyHistoryView();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.history,
            size: 80,
            color: Colors.grey.shade300,
          ),
          const SizedBox(height: 16),
          Text(
            '콜 내역이 없습니다',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: Colors.grey.shade600,
                ),
          ),
          const SizedBox(height: 8),
          Text(
            '대리운전을 요청하시면 여기에 표시됩니다',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Colors.grey.shade500,
                ),
          ),
        ],
      ),
    );
  }
}
