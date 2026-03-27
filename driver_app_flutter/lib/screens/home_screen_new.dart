import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

import '../core/providers.dart';
import '../core/routes.dart';
import '../core/theme.dart';
import '../features/auth/presentation/providers/auth_state.dart';
import '../features/driver/domain/entities/driver.dart';
import '../features/driver/presentation/state/driver_screen_ui_state.dart';
import '../features/call/domain/entities/call.dart';
import '../services/fcm_service.dart';

/// HomeScreen — DriverWorkflowNotifier 기반
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> with WidgetsBindingObserver {
  final FcmService _fcmService = FcmService();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initializeWorkflow();
      _setupFcmListeners();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// AppLifecycleObserver (Phase 1.10)
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      ref.read(driverWorkflowProvider.notifier).refreshActiveCallStatus();
    }
  }

  /// WorkflowNotifier 초기화
  Future<void> _initializeWorkflow() async {
    final session = ref.read(currentSessionProvider);
    if (session == null) return;

    // WorkflowNotifier에 위치 정보 전달
    await ref.read(driverWorkflowProvider.notifier).initialize(
      session.provinceId,
      session.cityId,
      session.officeId,
    );

    // FCM 초기화
    try {
      await _fcmService.initialize(
        provinceId: session.provinceId,
        cityId: session.cityId,
        officeId: session.officeId,
        driverId: session.driverId,
      );
    } catch (e) {
      debugPrint('[HomeScreen] FCM 초기화 실패: $e');
    }
  }

  /// FCM 리스너
  void _setupFcmListeners() {
    _fcmService.listenToForegroundMessages(_handleFcmMessage);
    _fcmService.listenToMessageOpenedApp(_handleFcmMessage);
  }

  void _handleFcmMessage(RemoteMessage message) {
    final type = message.data['type'] as String?;
    final callId = message.data['callId'] as String?;
    final workflow = ref.read(driverWorkflowProvider.notifier);

    switch (type) {
      case FcmService.typeCallAssigned:
        if (callId != null) workflow.handleNotificationCallId(callId);
        // Delivery ACK
        if (message.messageId != null) _fcmService.sendDeliveryAck(message.messageId!);
        break;
      case FcmService.typeCallCancelled:
        if (callId != null) workflow.handleCallCancelled(callId);
        break;
      case FcmService.typeSettlementFinalized:
      case FcmService.typeSettlementConfirmed:
      case FcmService.typeSettlementRejected:
        // 정산 상태 변경 → CarryOverListener가 자동 감지
        workflow.refreshSettlementData();
        break;
      case FcmService.typeCarryoverTransferred:
        // 이월금 이체 → CarryOverListener가 자동 감지
        break;
      default:
        debugPrint('[FCM] 알 수 없는 타입: $type');
    }
  }

  Future<void> _logout() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('로그아웃'),
        content: const Text('로그아웃 하시겠습니까?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('로그아웃')),
        ],
      ),
    );
    if (confirm == true) {
      await ref.read(authNotifierProvider.notifier).logout();
      if (mounted) Navigator.pushReplacementNamed(context, AppRoutes.login);
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authNotifierProvider);
    final uiState = ref.watch(driverWorkflowProvider);

    // 에러 메시지 Snackbar
    ref.listen<DriverScreenUiState>(driverWorkflowProvider, (prev, next) {
      if (next.errorMessage != null && prev?.errorMessage != next.errorMessage) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(next.errorMessage!)),
        );
        ref.read(driverWorkflowProvider.notifier).clearError();
      }
      if (next.navigateToHistorySettlement && !(prev?.navigateToHistorySettlement ?? false)) {
        Navigator.pushNamed(context, AppRoutes.historySettlement);
        ref.read(driverWorkflowProvider.notifier).clearNavigationFlags();
      }
    });

    return authState.when(
      initial: () => const _LoadingScreen(),
      loading: () => const _LoadingScreen(),
      authenticated: (session) {
        return Scaffold(
          appBar: AppBar(
            backgroundColor: Colors.black,
            title: Text(
              '기사앱 · ${uiState.driverStatus.displayName}',
              style: const TextStyle(color: Colors.white, fontSize: 18),
            ),
            leading: IconButton(
              icon: const Icon(Icons.exit_to_app, color: Colors.white),
              onPressed: _logout,
            ),
            actions: [
              IconButton(
                icon: const Icon(Icons.history, color: Colors.white),
                onPressed: () => Navigator.pushNamed(context, AppRoutes.historySettlement),
              ),
            ],
          ),
          body: Stack(
            children: [
              _buildBodyByStatus(uiState),
              // 신규 콜 팝업 오버레이
              if (uiState.newCallPopup != null)
                _NewCallPopup(
                  call: uiState.newCallPopup!,
                  onAccept: () => ref.read(driverWorkflowProvider.notifier).acceptCall(uiState.newCallPopup!.id),
                  onReject: () => ref.read(driverWorkflowProvider.notifier).rejectCall(uiState.newCallPopup!.id),
                ),
            ],
          ),
        );
      },
      unauthenticated: () {
        Future.microtask(() => Navigator.pushReplacementNamed(context, AppRoutes.login));
        return const _LoadingScreen();
      },
      error: (msg) => Scaffold(body: Center(child: Text('에러: $msg'))),
    );
  }

  Widget _buildBodyByStatus(DriverScreenUiState uiState) {
    // 정산 대기 콜이 있으면 정산 화면
    if (uiState.callForSettlement != null) {
      return _SettlementPendingView(call: uiState.callForSettlement!);
    }

    // 활성 콜이 있으면 운행 화면
    if (uiState.activeCall != null) {
      return _InProgressView(call: uiState.activeCall!);
    }

    // 기본: 대기 화면
    return const _WaitingView();
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 로딩
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class _LoadingScreen extends StatelessWidget {
  const _LoadingScreen();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(body: Center(child: CircularProgressIndicator()));
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 대기 화면
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class _WaitingView extends ConsumerWidget {
  const _WaitingView();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      color: AppTheme.background,
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.access_time, size: 80, color: AppTheme.primaryColor),
            const SizedBox(height: 24),
            const Text(
              '새로운 콜을 기다리고 있습니다...',
              style: TextStyle(fontSize: 20, color: Colors.white70),
            ),
            const SizedBox(height: 40),
            ElevatedButton.icon(
              onPressed: () => ref.read(driverWorkflowProvider.notifier).refreshActiveCallStatus(),
              icon: const Icon(Icons.refresh),
              label: const Text('배차 확인'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
              ),
            ),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: () => Navigator.pushNamed(context, AppRoutes.referralQR),
              icon: const Icon(Icons.qr_code),
              label: const Text('고객 추천하기'),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppTheme.primaryColor,
                side: const BorderSide(color: AppTheme.primaryColor),
                padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 신규 콜 팝업 오버레이
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class _NewCallPopup extends StatelessWidget {
  final Call call;
  final VoidCallback onAccept;
  final VoidCallback onReject;

  const _NewCallPopup({
    required this.call,
    required this.onAccept,
    required this.onReject,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.black87,
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                '새로운 콜 배정',
                style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold, color: AppTheme.primaryColor),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 32),
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                    color: AppTheme.surface,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: AppTheme.primaryColor, width: 2),
                  ),
                  child: SingleChildScrollView(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _infoRow(Icons.person, '고객명', call.customerName ?? '정보 없음'),
                        const SizedBox(height: 20),
                        _infoRow(Icons.phone, '전화번호', call.phoneNumber),
                        const SizedBox(height: 20),
                        _infoRow(Icons.location_on, '출발지', call.pickupLocation ?? '정보 없음'),
                        const SizedBox(height: 20),
                        _infoRow(Icons.flag, '목적지', call.destination ?? '정보 없음'),
                        const SizedBox(height: 20),
                        _infoRow(Icons.attach_money, '예상 요금', call.fare != null ? '${call.fare}원' : '미정'),
                        if (call.notes != null && call.notes!.isNotEmpty) ...[
                          const SizedBox(height: 20),
                          _infoRow(Icons.note, '메모', call.notes!),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton(
                      onPressed: onReject,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.grey[800],
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      child: const Text('거부', style: TextStyle(fontSize: 18)),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    flex: 2,
                    child: ElevatedButton(
                      onPressed: onAccept,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.primaryColor,
                        foregroundColor: Colors.black,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      child: const Text('확인', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _infoRow(IconData icon, String label, String value) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: AppTheme.primaryColor, size: 24),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: const TextStyle(fontSize: 14, color: Colors.grey)),
              const SizedBox(height: 4),
              Text(value, style: const TextStyle(fontSize: 18, color: Colors.white, fontWeight: FontWeight.w500)),
            ],
          ),
        ),
      ],
    );
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 운행 중 화면
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class _InProgressView extends ConsumerWidget {
  final Call call;
  const _InProgressView({required this.call});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      color: AppTheme.background,
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Container(width: 12, height: 12, decoration: const BoxDecoration(color: Colors.green, shape: BoxShape.circle)),
                  const SizedBox(width: 12),
                  Text('운행 중 · ${call.status.displayName}', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Colors.white)),
                ],
              ),
              const SizedBox(height: 24),
              Expanded(
                child: SingleChildScrollView(
                  child: Column(
                    children: [
                      _card('고객 정보', Icons.person, [
                        _detailRow('이름', call.customerName ?? '정보 없음'),
                        _detailRow('전화', call.phoneNumber),
                      ]),
                      const SizedBox(height: 16),
                      _card('경로 정보', Icons.directions, [
                        _detailRow('출발지', call.pickupLocation ?? '정보 없음'),
                        _detailRow('목적지', call.destination ?? '정보 없음'),
                      ]),
                      const SizedBox(height: 16),
                      _card('요금 정보', Icons.attach_money, [
                        _detailRow('예상 요금', call.fare != null ? '${call.fare}원' : '미정'),
                      ]),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () async {
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('운행 취소'),
                            content: const Text('운행을 취소하시겠습니까?'),
                            actions: [
                              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('아니오')),
                              TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('취소하기')),
                            ],
                          ),
                        );
                        if (confirm == true) {
                          ref.read(driverWorkflowProvider.notifier).cancelTrip(call.id);
                        }
                      },
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.red,
                        side: const BorderSide(color: Colors.red),
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      child: const Text('취소', style: TextStyle(fontSize: 16)),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    flex: 2,
                    child: ElevatedButton(
                      onPressed: () async {
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('운행 완료'),
                            content: const Text('운행을 완료하시겠습니까?'),
                            actions: [
                              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
                              TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('완료')),
                            ],
                          ),
                        );
                        if (confirm == true) {
                          ref.read(driverWorkflowProvider.notifier).completeCall(call.id);
                        }
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.primaryColor,
                        foregroundColor: Colors.black,
                        padding: const EdgeInsets.symmetric(vertical: 18),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      child: const Text('운행 완료', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _card(String title, IconData icon, List<Widget> children) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade800),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Icon(icon, color: AppTheme.primaryColor, size: 24),
            const SizedBox(width: 12),
            Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppTheme.primaryColor)),
          ]),
          const SizedBox(height: 16),
          ...children,
        ],
      ),
    );
  }

  Widget _detailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 80, child: Text(label, style: const TextStyle(fontSize: 14, color: Colors.grey))),
          Expanded(child: Text(value, style: const TextStyle(fontSize: 16, color: Colors.white, fontWeight: FontWeight.w500))),
        ],
      ),
    );
  }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 정산 대기 화면 (간략)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class _SettlementPendingView extends ConsumerWidget {
  final Call call;
  const _SettlementPendingView({required this.call});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      color: AppTheme.background,
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.receipt_long, size: 64, color: AppTheme.primaryColor),
              const SizedBox(height: 24),
              const Text('정산 대기 중', style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Colors.white)),
              const SizedBox(height: 8),
              Text('${call.customerName ?? "고객"} · ${call.destination ?? ""}',
                style: const TextStyle(fontSize: 16, color: Colors.white70)),
              const SizedBox(height: 32),
              const Text(
                'Phase 2 정산 시스템에서 상세 구현 예정',
                style: TextStyle(fontSize: 12, color: Colors.grey),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
