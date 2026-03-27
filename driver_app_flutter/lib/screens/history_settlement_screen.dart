import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/providers.dart';
import '../core/theme.dart';
import '../features/driver/presentation/state/driver_screen_ui_state.dart';
import '../features/settlement/settlement_calc.dart';

/// 운행내역 + 정산 화면 (Kotlin HistorySettlementScreen 1:1 포팅)
class HistorySettlementScreen extends ConsumerStatefulWidget {
  const HistorySettlementScreen({super.key});

  @override
  ConsumerState<HistorySettlementScreen> createState() => _HistorySettlementScreenState();
}

class _HistorySettlementScreenState extends ConsumerState<HistorySettlementScreen> {
  bool _isSettlementExpanded = false;
  int? _editedRealDeposit;

  @override
  Widget build(BuildContext context) {
    final workflow = ref.read(driverWorkflowProvider.notifier);
    final settlement = workflow.todaySettlement;
    final tripHistory = workflow.tripHistoryList;
    final ratio = workflow.depositRatio;
    final carryOver = workflow.carryOver;
    final settlementStatus = workflow.dailySettlementStatus;

    // 계산
    final rawFinalDeposit = SettlementCalc.rawFinalDeposit(settlement.officeDeposit, settlement.totalCredit);
    final coBalance = carryOver?.balance ?? 0;
    final usedFromCO = SettlementCalc.usedFromCarryOver(rawFinalDeposit, coBalance);
    final remainingCO = SettlementCalc.remainingCarryOver(rawFinalDeposit, coBalance);
    final actualDeposit = _editedRealDeposit ?? settlement.realDeposit;
    final finalIncome = settlement.cashReceived - actualDeposit;

    // 정산 상태 변화 감지
    ref.listen<DriverScreenUiState>(driverWorkflowProvider, (prev, next) {
      final newStatus = ref.read(driverWorkflowProvider.notifier).dailySettlementStatus;
      if (newStatus == DailySettlementStatus.confirmed) {
        _showConfirmedDialog();
      } else if (newStatus == DailySettlementStatus.rejected) {
        _showRejectedDialog();
      }
    });

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: Colors.black,
        title: const Text('운행 내역', style: TextStyle(color: Colors.white)),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.white),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // ── 운행 내역 카드 ──
            _buildTripHistoryCard(tripHistory),
            const SizedBox(height: 12),

            // ── 오늘의 정산 카드 ──
            _buildSettlementCard(
              settlement: settlement,
              ratio: ratio,
              rawFinalDeposit: rawFinalDeposit,
              usedFromCO: usedFromCO,
              actualDeposit: actualDeposit,
              finalIncome: finalIncome,
              remainingCO: remainingCO,
              settlementStatus: settlementStatus,
            ),
            const SizedBox(height: 12),

            // ── 누적 미수령금 카드 ──
            if (remainingCO != 0 || usedFromCO > 0 || carryOver?.status == 'TRANSFERRED')
              _buildCarryOverCard(
                carryOver: carryOver,
                remainingCO: remainingCO,
                usedFromCO: usedFromCO,
                coBalance: coBalance,
                rawFinalDeposit: rawFinalDeposit,
              ),
            const SizedBox(height: 16),

            // ── 액션 버튼 ──
            _buildActionButtons(
              settlementStatus: settlementStatus,
              actualDeposit: actualDeposit,
              remainingCO: remainingCO,
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 운행 내역 카드
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Widget _buildTripHistoryCard(List<TripHistoryItem> tripHistory) {
    return Card(
      color: const Color(0xFF2A2A2A),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('운행 내역 (${tripHistory.length}건)',
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white)),
            const SizedBox(height: 12),
            if (tripHistory.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Center(child: Text('운행 내역이 없습니다', style: TextStyle(color: Colors.grey))),
              )
            else
              ...tripHistory.map((trip) => Container(
                margin: const EdgeInsets.only(bottom: 4),
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                decoration: BoxDecoration(
                  color: const Color(0xFF3A3A3A),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  '${trip.tripNumber}. ${trip.customerName} | ${trip.departure} → ${trip.destination} | ${SettlementCalc.formatAmount(trip.fare)} (${trip.paymentMethod})',
                  style: const TextStyle(fontSize: 14, color: Colors.white),
                ),
              )),
          ],
        ),
      ),
    );
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 오늘의 정산 카드 (접기/펼치기)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Widget _buildSettlementCard({
    required TodaySettlement settlement,
    required int ratio,
    required int rawFinalDeposit,
    required int usedFromCO,
    required int actualDeposit,
    required int finalIncome,
    required int remainingCO,
    required DailySettlementStatus settlementStatus,
  }) {
    return Card(
      color: const Color(0xFF2A2A2A),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // 헤더 (탭으로 접기/펼치기)
            InkWell(
              onTap: () => setState(() => _isSettlementExpanded = !_isSettlementExpanded),
              child: Row(
                children: [
                  const Text('오늘의 정산', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white)),
                  const SizedBox(width: 8),
                  GestureDetector(
                    onTap: () => _showRatioDialog(ratio),
                    child: const Icon(Icons.settings, size: 18, color: Colors.grey),
                  ),
                  const Spacer(),
                  Icon(_isSettlementExpanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down, color: Colors.grey),
                ],
              ),
            ),

            // 접힌 상태: 요약
            if (!_isSettlementExpanded)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  '${settlement.tripCount}건 · ${SettlementCalc.formatAmount(settlement.totalFare)} · 납입 ${SettlementCalc.formatAmount(actualDeposit)}',
                  style: const TextStyle(fontSize: 13, color: Colors.grey),
                ),
              ),

            // 펼친 상태: 상세
            AnimatedSize(
              duration: const Duration(milliseconds: 200),
              child: _isSettlementExpanded
                  ? Column(
                      children: [
                        const SizedBox(height: 16),
                        _amountRow('총 운행', '${settlement.tripCount}건'),
                        _amountRow('총 운임', SettlementCalc.formatAmount(settlement.totalFare)),
                        _amountRow('납입금 ($ratio%)', SettlementCalc.formatAmount(settlement.officeDeposit)),
                        const Divider(color: Colors.grey, height: 24),
                        _amountRow('현금 수령', SettlementCalc.formatAmount(settlement.cashReceived)),
                        _amountRow('외상(이체/포인트)', SettlementCalc.formatAmount(settlement.totalCredit)),
                        if (usedFromCO > 0) ...[
                          const SizedBox(height: 4),
                          _amountRow('미환급금 공제', '-${SettlementCalc.formatAmount(usedFromCO)}', valueColor: Colors.green),
                        ],
                        const SizedBox(height: 12),

                        // 실납입 (수정 가능)
                        Card(
                          color: const Color(0xFF3A3A3A),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                            child: Row(
                              children: [
                                const Text('실납입', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryColor)),
                                const Spacer(),
                                Text(SettlementCalc.formatAmount(actualDeposit),
                                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white)),
                                const SizedBox(width: 8),
                                if (settlementStatus == DailySettlementStatus.working)
                                  GestureDetector(
                                    onTap: () => _showEditDepositDialog(actualDeposit),
                                    child: const Icon(Icons.edit, size: 18, color: AppTheme.primaryColor),
                                  ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        _amountRow('최종 수입금', SettlementCalc.formatAmount(finalIncome),
                            valueColor: finalIncome >= 0 ? Colors.green : Colors.red,
                            isBold: true),
                        if (remainingCO != 0) ...[
                          const SizedBox(height: 4),
                          _amountRow(
                            remainingCO > 0 ? '총 환급금' : '총 미납금',
                            SettlementCalc.formatAmount(remainingCO.abs()),
                            valueColor: remainingCO > 0 ? Colors.red : const Color(0xFFFF9800),
                          ),
                        ],
                      ],
                    )
                  : const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 누적 미수령금 카드
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Widget _buildCarryOverCard({
    required DriverCarryOver? carryOver,
    required int remainingCO,
    required int usedFromCO,
    required int coBalance,
    required int rawFinalDeposit,
  }) {
    final isTransferred = carryOver?.status == 'TRANSFERRED';
    final bgColor = isTransferred ? const Color(0xFF2E4A2E) : const Color(0xFF4A3A2A);
    final titleColor = remainingCO > 0 ? Colors.red : (remainingCO < 0 ? const Color(0xFFFF9800) : Colors.green);

    return Card(
      color: bgColor,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              remainingCO >= 0 ? '누적 미수령금' : '누적 미납금',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: titleColor),
            ),
            const SizedBox(height: 12),
            _amountRow('이월', SettlementCalc.formatAmount(coBalance.abs())),
            if (usedFromCO > 0)
              _amountRow('오늘 공제', '-${SettlementCalc.formatAmount(usedFromCO)}'),
            if (rawFinalDeposit < 0)
              _amountRow('오늘 발생', SettlementCalc.formatAmount((-rawFinalDeposit).abs())),
            const Divider(color: Colors.grey, height: 16),
            _amountRow('합계', SettlementCalc.formatAmount(remainingCO.abs()),
                valueColor: titleColor, isBold: true),
            if (isTransferred) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(color: Colors.green.withValues(alpha: 0.2), borderRadius: BorderRadius.circular(4)),
                child: const Text('사무실에서 이체되었습니다!', style: TextStyle(color: Colors.green, fontWeight: FontWeight.bold)),
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () => _showReceiveConfirmDialog(remainingCO),
                  style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
                  child: const Text('수령완료'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 액션 버튼
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Widget _buildActionButtons({
    required DailySettlementStatus settlementStatus,
    required int actualDeposit,
    required int remainingCO,
  }) {
    switch (settlementStatus) {
      case DailySettlementStatus.pendingConfirm:
        return Card(
          color: const Color(0xFF3A3A2A),
          child: const Padding(
            padding: EdgeInsets.all(16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: AppTheme.primaryColor)),
                SizedBox(width: 12),
                Text('매니저 확인 대기 중...', style: TextStyle(color: AppTheme.primaryColor, fontWeight: FontWeight.bold)),
              ],
            ),
          ),
        );

      case DailySettlementStatus.confirmed:
        return SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: () => _handleLogout(),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.green,
              padding: const EdgeInsets.symmetric(vertical: 16),
            ),
            child: const Text('퇴근하기', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ),
        );

      case DailySettlementStatus.working:
      case DailySettlementStatus.rejected:
        final label = settlementStatus == DailySettlementStatus.rejected ? '재제출' : '업무마감';
        return SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: () => _showSubmitDialog(actualDeposit, remainingCO),
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.primaryColor,
              foregroundColor: Colors.black,
              padding: const EdgeInsets.symmetric(vertical: 16),
            ),
            child: Text(label, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ),
        );
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 다이얼로그들
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  void _showRatioDialog(int ratio) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: const Text('납부 비율 정보'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('사무실 납부: $ratio%', style: const TextStyle(fontSize: 16, color: Colors.white)),
            Text('기사 수입: ${100 - ratio}%', style: const TextStyle(fontSize: 16, color: Colors.white)),
            const SizedBox(height: 12),
            const Text('비율 변경은 사무실에서만 가능합니다', style: TextStyle(fontSize: 12, color: Colors.grey)),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('확인', style: TextStyle(color: AppTheme.primaryColor)),
          ),
        ],
      ),
    );
  }

  void _showEditDepositDialog(int currentDeposit) {
    final controller = TextEditingController(text: currentDeposit.toString());
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: const Text('실납입 금액 수정'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          textAlign: TextAlign.right,
          decoration: const InputDecoration(suffixText: '원'),
          autofocus: true,
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          TextButton(
            onPressed: () {
              final value = int.tryParse(controller.text) ?? currentDeposit;
              setState(() => _editedRealDeposit = value);
              ref.read(driverWorkflowProvider.notifier).updateRealDeposit(value);
              Navigator.pop(ctx);
            },
            child: const Text('확인', style: TextStyle(color: Colors.green)),
          ),
        ],
      ),
    );
  }

  void _showSubmitDialog(int actualDeposit, int remainingCO) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: const Text('업무마감'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('업무를 마감하시겠습니까?', style: TextStyle(color: Colors.white)),
            const SizedBox(height: 16),
            _dialogRow('실납입', SettlementCalc.formatAmount(actualDeposit)),
            if (remainingCO != 0)
              _dialogRow(remainingCO > 0 ? '총 환급금' : '총 미납금', SettlementCalc.formatAmount(remainingCO.abs())),
            const SizedBox(height: 16),
            const Text('마감 시 처리 내용:', style: TextStyle(fontSize: 13, color: Colors.grey)),
            const Text('• 매니저에게 정산 확인 요청', style: TextStyle(fontSize: 12, color: Colors.grey)),
            const Text('• 운행/정산 내역 저장', style: TextStyle(fontSize: 12, color: Colors.grey)),
            const Text('• 매니저 확인 후 퇴근 가능', style: TextStyle(fontSize: 12, color: Colors.grey)),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          TextButton(
            onPressed: () async {
              Navigator.pop(ctx);
              final (success, msg) = await ref.read(driverWorkflowProvider.notifier).submitDailySettlement(actualDeposit);
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                  content: Text(msg),
                  backgroundColor: success ? Colors.green : Colors.red,
                ));
                setState(() {}); // 상태 갱신
              }
            },
            child: const Text('마감하기', style: TextStyle(color: AppTheme.primaryColor)),
          ),
        ],
      ),
    );
  }

  void _showReceiveConfirmDialog(int amount) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: const Text('수령 확인'),
        content: Text('${SettlementCalc.formatAmount(amount.abs())}을 수령하셨습니까?', style: const TextStyle(color: Colors.white)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          TextButton(
            onPressed: () async {
              Navigator.pop(ctx);
              await ref.read(driverWorkflowProvider.notifier).confirmReceiveCarryOver();
              if (mounted) setState(() {});
            },
            child: const Text('예, 수령했습니다', style: TextStyle(color: Colors.green)),
          ),
        ],
      ),
    );
  }

  void _showConfirmedDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: const Text('정산 확인 완료'),
        content: const Text('매니저가 정산을 확인했습니다.\n퇴근하시겠습니까?', style: TextStyle(color: Colors.white)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('계속 대기')),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              _handleLogout();
            },
            child: const Text('퇴근하기', style: TextStyle(color: Colors.green)),
          ),
        ],
      ),
    );
  }

  void _showRejectedDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: const Text('정산 거절', style: TextStyle(color: Colors.red)),
        content: const Text('매니저가 정산을 거절했습니다.\n실납입액을 확인 후 다시 제출해주세요.', style: TextStyle(color: Colors.white)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('확인', style: TextStyle(color: AppTheme.primaryColor)),
          ),
        ],
      ),
    );
  }

  Future<void> _handleLogout() async {
    await ref.read(authNotifierProvider.notifier).logout();
    if (mounted) {
      Navigator.of(context).pushNamedAndRemoveUntil('/login', (route) => false);
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // UI 헬퍼
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Widget _amountRow(String label, String value, {Color? valueColor, bool isBold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 14, color: Colors.grey)),
          Text(value, style: TextStyle(
            fontSize: 14,
            color: valueColor ?? Colors.white,
            fontWeight: isBold ? FontWeight.bold : FontWeight.normal,
          )),
        ],
      ),
    );
  }

  Widget _dialogRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 14, color: Colors.white70)),
          Text(value, style: const TextStyle(fontSize: 14, color: Colors.white, fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}
