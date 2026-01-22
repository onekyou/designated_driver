import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import '../features/auth/presentation/providers/auth_notifier.dart';
import '../features/driver/presentation/providers/driver_notifier.dart';
import '../features/driver/domain/entities/driver.dart';
import '../features/call/presentation/providers/call_notifier.dart';
import '../services/fcm_service.dart';
import '../core/routes.dart';

/// HomeScreen - Riverpod 기반 완전 재구현
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  final FcmService _fcmService = FcmService();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initializeFcm();
      _setupFcmListeners();
    });
  }

  /// FCM 초기화
  Future<void> _initializeFcm() async {
    final session = ref.read(currentSessionProvider);
    if (session == null) return;

    try {
      await _fcmService.initialize(
        regionId: session.regionId,
        officeId: session.officeId,
        driverId: session.driverId,
      );
      debugPrint('[HomeScreen] FCM 초기화 완료');
    } catch (e) {
      debugPrint('[HomeScreen] FCM 초기화 실패: $e');
    }
  }

  /// FCM 메시지 리스너 설정
  void _setupFcmListeners() {
    // Foreground 메시지
    _fcmService.listenToForegroundMessages((message) {
      _handleFcmMessage(message);
    });

    // Background/Terminated에서 앱 열림
    _fcmService.listenToMessageOpenedApp((message) {
      _handleFcmMessage(message);
    });
  }

  /// FCM 메시지 처리
  void _handleFcmMessage(RemoteMessage message) {
    final notificationType = message.data['type'] as String?;

    switch (notificationType) {
      case FcmService.notificationTypeCallAssigned:
        // 새 콜 배정 - 콜 정보 새로고침
        ref.read(callNotifierProvider.notifier).fetchAssignedCall();
        _showCallAssignedDialog(message.data);
        break;

      case FcmService.notificationTypeCallCancelled:
        // 콜 취소
        ref.read(callNotifierProvider.notifier).reset();
        _showSnackBar('콜이 취소되었습니다');
        break;

      case FcmService.notificationTypeCallUpdated:
        // 콜 업데이트
        ref.read(callNotifierProvider.notifier).fetchAssignedCall();
        _showSnackBar('콜 정보가 업데이트되었습니다');
        break;

      default:
        debugPrint('[FCM] 알 수 없는 알림 타입: $notificationType');
    }
  }

  /// 콜 배정 다이얼로그
  void _showCallAssignedDialog(Map<String, dynamic> data) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: const Text('🚕 신규 콜 배정'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('고객: ${data['customerName'] ?? '알 수 없음'}'),
            Text('전화: ${data['phoneNumber'] ?? '-'}'),
            Text('목적지: ${data['destination'] ?? '-'}'),
            const SizedBox(height: 8),
            const Text(
              '콜 목록에서 확인하세요',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('확인'),
          ),
        ],
      ),
    );
  }

  void _showSnackBar(String message) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message)),
      );
    }
  }

  /// 로그아웃
  Future<void> _logout() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('로그아웃'),
        content: const Text('로그아웃 하시겠습니까?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('취소'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('로그아웃'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      await ref.read(authNotifierProvider.notifier).logout();
      if (mounted) {
        Navigator.pushReplacementNamed(context, AppRoutes.login);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authNotifierProvider);
    final driverState = ref.watch(driverNotifierProvider);

    return authState.when(
      initial: () => const _LoadingScreen(),
      loading: () => const _LoadingScreen(),
      authenticated: (session) {
        return Scaffold(
          appBar: AppBar(
            backgroundColor: Colors.black,
            title: const Text(
              '기사앱',
              style: TextStyle(color: Colors.white),
            ),
            leading: IconButton(
              icon: const Icon(Icons.exit_to_app, color: Colors.white),
              onPressed: _logout,
              tooltip: '로그아웃',
            ),
            actions: [
              IconButton(
                icon: const Icon(Icons.history, color: Colors.white),
                onPressed: () {
                  Navigator.pushNamed(context, AppRoutes.historySettlement);
                },
                tooltip: '운행내역',
              ),
            ],
          ),
          body: driverState.when(
            initial: () => _WaitingScreen(session: session),
            loading: () => const Center(child: CircularProgressIndicator()),
            loaded: (driver) => _buildBodyByDriverStatus(driver, session),
            error: (message) => Center(child: Text('에러: $message')),
          ),
        );
      },
      unauthenticated: () {
        Future.microtask(() {
          Navigator.pushReplacementNamed(context, AppRoutes.login);
        });
        return const _LoadingScreen();
      },
      error: (message) => Scaffold(
        body: Center(child: Text('에러: $message')),
      ),
    );
  }

  /// 기사 상태별 화면 빌드
  Widget _buildBodyByDriverStatus(Driver driver, session) {
    switch (driver.status) {
      case DriverStatus.online:
      case DriverStatus.waiting:
        return _WaitingScreen(session: session);
      case DriverStatus.assigned:
      case DriverStatus.accepted:
        return _CallAssignedScreen();
      case DriverStatus.inProgress:
        return _OnTripScreen();
      case DriverStatus.offline:
      default:
        return _WaitingScreen(session: session);
    }
  }
}

/// 로딩 화면
class _LoadingScreen extends StatelessWidget {
  const _LoadingScreen();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(child: CircularProgressIndicator()),
    );
  }
}

/// 대기 화면
class _WaitingScreen extends ConsumerWidget {
  final dynamic session;

  const _WaitingScreen({required this.session});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      color: const Color(0xFF121212),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.access_time,
              size: 80,
              color: Color(0xFFFFB000),
            ),
            const SizedBox(height: 24),
            const Text(
              '새로운 콜을 기다리고 있습니다...',
              style: TextStyle(
                fontSize: 20,
                color: Colors.white70,
              ),
            ),
            const SizedBox(height: 40),
            ElevatedButton.icon(
              onPressed: () {
                // 배차 확인 - 콜 새로고침
                ref.read(callNotifierProvider.notifier).fetchAssignedCall();
              },
              icon: const Icon(Icons.refresh),
              label: const Text('배차 확인'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 32,
                  vertical: 16,
                ),
              ),
            ),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: () {
                Navigator.pushNamed(context, AppRoutes.referralQR);
              },
              icon: const Icon(Icons.qr_code),
              label: const Text('고객 추천하기'),
              style: OutlinedButton.styleFrom(
                foregroundColor: const Color(0xFFFFB000),
                side: const BorderSide(color: Color(0xFFFFB000)),
                padding: const EdgeInsets.symmetric(
                  horizontal: 32,
                  vertical: 16,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 콜 배정됨 화면
class _CallAssignedScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final callState = ref.watch(callNotifierProvider);

    return callState.when(
      initial: () => const Center(child: Text('콜 정보를 불러오는 중...')),
      loading: () => const Center(child: CircularProgressIndicator()),
      loaded: (assignedCall) {
        if (assignedCall == null) {
          return const Center(child: Text('배정된 콜이 없습니다.'));
        }

        return Container(
          color: const Color(0xFF121212),
          child: SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(24.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // 헤더
                  const Text(
                    '🚕 새로운 콜 배정',
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFFFFB000),
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 32),

                  // 콜 정보 카드
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        color: const Color(0xFF1E1E1E),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(
                          color: const Color(0xFFFFB000),
                          width: 2,
                        ),
                      ),
                      child: SingleChildScrollView(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _buildInfoRow(
                              icon: Icons.person,
                              label: '고객명',
                              value: assignedCall.customerName ?? '정보 없음',
                            ),
                            const SizedBox(height: 20),
                            _buildInfoRow(
                              icon: Icons.phone,
                              label: '전화번호',
                              value: assignedCall.phoneNumber ?? '정보 없음',
                            ),
                            const SizedBox(height: 20),
                            _buildInfoRow(
                              icon: Icons.location_on,
                              label: '출발지',
                              value: assignedCall.pickupLocation ?? '정보 없음',
                            ),
                            const SizedBox(height: 20),
                            _buildInfoRow(
                              icon: Icons.flag,
                              label: '목적지',
                              value: assignedCall.destination ?? '정보 없음',
                            ),
                            const SizedBox(height: 20),
                            _buildInfoRow(
                              icon: Icons.attach_money,
                              label: '예상 요금',
                              value: assignedCall.fare != null
                                  ? '${assignedCall.fare}원'
                                  : '미정',
                            ),
                            if (assignedCall.notes != null &&
                                assignedCall.notes!.isNotEmpty) ...[
                              const SizedBox(height: 20),
                              _buildInfoRow(
                                icon: Icons.note,
                                label: '메모',
                                value: assignedCall.notes!,
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ),

                  const SizedBox(height: 24),

                  // 버튼
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton(
                          onPressed: () async {
                            await ref
                                .read(callNotifierProvider.notifier)
                                .rejectCall(assignedCall.id, '기사가 거부');
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('콜을 거부했습니다')),
                              );
                            }
                          },
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.grey[800],
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: const Text(
                            '거부',
                            style: TextStyle(fontSize: 18),
                          ),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        flex: 2,
                        child: ElevatedButton(
                          onPressed: () async {
                            await ref
                                .read(callNotifierProvider.notifier)
                                .acceptCall(assignedCall.id);
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('콜을 수락했습니다')),
                              );
                            }
                          },
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFFFFB000),
                            foregroundColor: Colors.black,
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: const Text(
                            '수락하고 운행 시작',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
      error: (message) => Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 64, color: Colors.red),
            const SizedBox(height: 16),
            Text(
              '에러: $message',
              style: const TextStyle(fontSize: 16, color: Colors.white),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow({
    required IconData icon,
    required String label,
    required String value,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: const Color(0xFFFFB000), size: 24),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  fontSize: 14,
                  color: Colors.grey,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                value,
                style: const TextStyle(
                  fontSize: 18,
                  color: Colors.white,
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

/// 운행 중 화면
class _OnTripScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final callState = ref.watch(callNotifierProvider);

    return Container(
      color: const Color(0xFF121212),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 헤더
              Row(
                children: [
                  Container(
                    width: 12,
                    height: 12,
                    decoration: const BoxDecoration(
                      color: Colors.green,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Text(
                    '운행 중',
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 32),

              // 콜 정보
              callState.when(
                loaded: (assignedCall) {
                  if (assignedCall == null) {
                    return const Center(child: Text('콜 정보를 불러올 수 없습니다.'));
                  }

                  return Expanded(
                    child: SingleChildScrollView(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // 고객 정보 카드
                          _buildCard(
                            title: '고객 정보',
                            icon: Icons.person,
                            children: [
                              _buildDetailRow('이름', assignedCall.customerName ?? '정보 없음'),
                              const Divider(height: 24, color: Colors.grey),
                              _buildDetailRow('전화', assignedCall.phoneNumber ?? '정보 없음'),
                            ],
                          ),
                          const SizedBox(height: 16),

                          // 경로 정보 카드
                          _buildCard(
                            title: '경로 정보',
                            icon: Icons.directions,
                            children: [
                              _buildDetailRow('출발지', assignedCall.pickupLocation ?? '정보 없음'),
                              const Divider(height: 24, color: Colors.grey),
                              _buildDetailRow('목적지', assignedCall.destination ?? '정보 없음'),
                            ],
                          ),
                          const SizedBox(height: 16),

                          // 요금 정보 카드
                          _buildCard(
                            title: '요금 정보',
                            icon: Icons.attach_money,
                            children: [
                              _buildDetailRow(
                                '예상 요금',
                                assignedCall.fare != null
                                    ? '${assignedCall.fare}원'
                                    : '미정',
                              ),
                            ],
                          ),

                          if (assignedCall.notes != null && assignedCall.notes!.isNotEmpty) ...[
                            const SizedBox(height: 16),
                            _buildCard(
                              title: '메모',
                              icon: Icons.note,
                              children: [
                                Text(
                                  assignedCall.notes!,
                                  style: const TextStyle(
                                    fontSize: 16,
                                    color: Colors.white70,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ],
                      ),
                    ),
                  );
                },
                initial: () => const Center(child: Text('정보를 불러오는 중...')),
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (message) => Center(child: Text('에러: $message')),
              ),

              const SizedBox(height: 24),

              // 운행 완료 버튼
              ElevatedButton(
                onPressed: () async {
                  final confirm = await showDialog<bool>(
                    context: context,
                    builder: (context) => AlertDialog(
                      title: const Text('운행 완료'),
                      content: const Text('운행을 완료하시겠습니까?\n정산 화면으로 이동합니다.'),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(context, false),
                          child: const Text('취소'),
                        ),
                        TextButton(
                          onPressed: () => Navigator.pop(context, true),
                          child: const Text('완료'),
                        ),
                      ],
                    ),
                  );

                  if (confirm == true && context.mounted) {
                    final currentCall = ref.read(currentCallProvider);
                    if (currentCall != null) {
                      await ref
                          .read(callNotifierProvider.notifier)
                          .completeCall(currentCall.id);
                      if (context.mounted) {
                        // TODO: Navigate to settlement screen
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('운행이 완료되었습니다')),
                        );
                      }
                    }
                  }
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFFFB000),
                  foregroundColor: Colors.black,
                  padding: const EdgeInsets.symmetric(vertical: 18),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: const Text(
                  '운행 완료',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCard({
    required String title,
    required IconData icon,
    required List<Widget> children,
  }) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF1E1E1E),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey[800]!),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: const Color(0xFFFFB000), size: 24),
              const SizedBox(width: 12),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFFFFB000),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          ...children,
        ],
      ),
    );
  }

  Widget _buildDetailRow(String label, String value) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 80,
          child: Text(
            label,
            style: const TextStyle(
              fontSize: 14,
              color: Colors.grey,
            ),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(
              fontSize: 16,
              color: Colors.white,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
      ],
    );
  }
}
