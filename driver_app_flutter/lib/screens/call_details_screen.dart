import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/providers.dart';
import '../core/theme.dart';
import '../features/call/domain/entities/call.dart';

/// 콜 상세정보 화면
class CallDetailsScreen extends ConsumerStatefulWidget {
  final String? callId;
  const CallDetailsScreen({super.key, this.callId});

  @override
  ConsumerState<CallDetailsScreen> createState() => _CallDetailsScreenState();
}

class _CallDetailsScreenState extends ConsumerState<CallDetailsScreen> {
  Call? _call;
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadCallDetails();
  }

  Future<void> _loadCallDetails() async {
    if (widget.callId == null) {
      setState(() { _isLoading = false; _error = '콜 ID가 없습니다.'; });
      return;
    }

    try {
      // WorkflowNotifier의 assignedCalls에서 먼저 찾기
      final uiState = ref.read(driverWorkflowProvider);
      final found = uiState.assignedCalls.where((c) => c.id == widget.callId).firstOrNull
          ?? (uiState.activeCall?.id == widget.callId ? uiState.activeCall : null)
          ?? (uiState.callForSettlement?.id == widget.callId ? uiState.callForSettlement : null);

      if (found != null) {
        setState(() { _call = found; _isLoading = false; });
      } else {
        setState(() { _isLoading = false; _error = '콜 정보를 찾을 수 없습니다.'; });
      }
    } catch (e) {
      setState(() { _isLoading = false; _error = '로드 실패: $e'; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('콜 상세정보')),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!, style: const TextStyle(color: Colors.red, fontSize: 16)))
              : _call != null
                  ? _buildCallDetails(_call!)
                  : const Center(child: Text('콜 정보 없음')),
    );
  }

  Widget _buildCallDetails(Call call) {
    final workflow = ref.read(driverWorkflowProvider.notifier);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 상태 배지
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            decoration: BoxDecoration(
              color: AppTheme.getStatusColor(call.status.value).withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              call.status.displayName,
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.getStatusColor(call.status.value)),
            ),
          ),
          const SizedBox(height: 24),

          _infoCard('고객 정보', [
            _row('이름', call.customerName ?? '정보 없음'),
            _row('전화', call.phoneNumber),
          ]),
          const SizedBox(height: 16),

          _infoCard('경로 정보', [
            _row('출발지', call.pickupLocation ?? '정보 없음'),
            _row('목적지', call.destination ?? '정보 없음'),
          ]),
          const SizedBox(height: 16),

          _infoCard('요금 정보', [
            _row('요금', call.fare != null ? '${call.fare}원' : '미정'),
            if (call.paymentMethod != null) _row('결제', call.paymentMethod!),
            _row('소요시간', call.getFormattedElapsedTime()),
          ]),

          if (call.notes != null && call.notes!.isNotEmpty) ...[
            const SizedBox(height: 16),
            _infoCard('메모', [Text(call.notes!, style: const TextStyle(color: Colors.white70, fontSize: 14))]),
          ],

          const SizedBox(height: 32),

          // 상태별 버튼
          if (call.status == CallStatus.assigned) ...[
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () async {
                      await workflow.rejectCall(call.id);
                      if (mounted) Navigator.pop(context);
                    },
                    style: OutlinedButton.styleFrom(foregroundColor: Colors.red, side: const BorderSide(color: Colors.red)),
                    child: const Text('거절'),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  flex: 2,
                  child: ElevatedButton(
                    onPressed: () async {
                      await workflow.acceptCall(call.id);
                      if (mounted) Navigator.pop(context);
                    },
                    child: const Text('수락'),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _infoCard(String title, List<Widget> children) {
    return Card(
      color: AppTheme.surface,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryColor)),
            const SizedBox(height: 12),
            ...children,
          ],
        ),
      ),
    );
  }

  Widget _row(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 80, child: Text(label, style: const TextStyle(color: Colors.grey, fontSize: 14))),
          Expanded(child: Text(value, style: const TextStyle(color: Colors.white, fontSize: 14))),
        ],
      ),
    );
  }
}
