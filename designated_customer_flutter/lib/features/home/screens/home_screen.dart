import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../attribution/providers/attribution_provider.dart';
import '../../auth/providers/auth_provider.dart';
import '../../call/repositories/call_repository.dart';
import '../../call/screens/call_request_screen.dart';
import '../../points/providers/point_provider.dart';
import '../providers/point_earn_watcher_provider.dart';
import '../widgets/step_counter_card.dart';
import 'package:url_launcher/url_launcher.dart';

/// 홈 화면 - 네이티브 앱과 동일한 레이아웃
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  Timer? _activeCallTimer;
  String? _lastActiveCallId;
  final CallRepository _callRepository = CallRepository();

  @override
  void initState() {
    super.initState();
    // 활성 콜 감시 시작 (네이티브 앱의 MainViewModel과 동일)
    _startWatchingActiveCall();
  }

  @override
  void dispose() {
    _activeCallTimer?.cancel();
    super.dispose();
  }

  /// 활성 콜 감시 시작 (1초마다 폴링)
  void _startWatchingActiveCall() {
    _activeCallTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      _checkActiveCall();
    });
  }

  /// 활성 콜 확인 (네이티브 앱의 MainViewModel 로직)
  Future<void> _checkActiveCall() async {
    try {
      final attribution = ref.read(attributionNotifierProvider).value;
      final user = ref.read(authNotifierProvider).value;

      if (attribution == null || user?.phoneNumber == null) {
        return;
      }

      // 활성 콜 조회
      final activeCall = await _callRepository.getActiveCall(
        regionId: attribution.regionId,
        officeId: attribution.officeId,
        phoneNumber: user!.phoneNumber!,
      );

      final currentCallId = activeCall?.id;

      // 활성 콜이 사라진 경우 (네이티브 앱의 패턴과 동일)
      if (_lastActiveCallId != null && currentCallId == null) {
        debugPrint('[HomeScreen] 활성 콜 사라짐 감지: $_lastActiveCallId');

        // PointEarnWatcher에 알림
        ref.read(pointEarnWatcherProvider.notifier)
            .onActiveCallDisappeared(_lastActiveCallId!);
      }

      // 마지막 활성 콜 ID 업데이트
      _lastActiveCallId = currentCallId;
    } catch (e) {
      debugPrint('[HomeScreen] 활성 콜 확인 중 오류: $e');
    }
  }

  /// 포인트 적립 다이얼로그 표시
  void _showPointsEarnedDialog(BuildContext context, int earnedPoints, int usedPoints, int fare)
  {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) {
        return AlertDialog(
          backgroundColor: const Color(0xFF1E1E1E),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          title: const Row(
            children: [
              Icon(
                Icons.check_circle,
                color: Color(0xFFFFAB00),
                size: 28,
              ),
              SizedBox(width: 8),
              Text(
                '포인트 적립 완료',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Divider(color: Colors.white24),
              const SizedBox(height: 16),

              // 요금 정보
              _DialogInfoRow(
                label: '운행 요금',
                value: '${NumberFormat('#,###').format(fare)}원',
                valueColor: Colors.white,
              ),
              const SizedBox(height: 12),

              // 사용 포인트 (있는 경우만)
              if (usedPoints > 0) ...[
                _DialogInfoRow(
                  label: '사용 포인트',
                  value: '-${NumberFormat('#,###').format(usedPoints)}P',
                  valueColor: Colors.red[300]!,
                ),
                const SizedBox(height: 12),
              ],

              // 적립 포인트
              _DialogInfoRow(
                label: '적립 포인트',
                value: '+${NumberFormat('#,###').format(earnedPoints)}P',
                valueColor: const Color(0xFFFFAB00),
              ),

              const SizedBox(height: 16),
              const Divider(color: Colors.white24),
              const SizedBox(height: 8),

              // 안내 문구
              const Text(
                '포인트가 적립되었습니다.\n다음 이용 시 사용하실 수 있습니다.',
                style: TextStyle(
                  color: Colors.white70,
                  fontSize: 14,
                ),
                textAlign: TextAlign.center,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () {
                // 다이얼로그 닫기
                Navigator.of(context).pop();
                // PointEarnWatcher 상태 초기화
                ref.read(pointEarnWatcherProvider.notifier).dismissDialog();
              },
              child: const Text(
                '확인',
                style: TextStyle(
                  color: Color(0xFFFFAB00),
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final attributionState = ref.watch(attributionNotifierProvider);
    final customerPointsAsync = ref.watch(customerPointsProvider);
    final pointEarnWatcherState = ref.watch(pointEarnWatcherProvider);

    // 포인트 적립 다이얼로그 표시 (네이티브 앱과 동일)
    if (pointEarnWatcherState.showEarnedDialog) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _showPointsEarnedDialog(
          context,
          pointEarnWatcherState.earnedPoints,
          pointEarnWatcherState.usedPoints,
          pointEarnWatcherState.fare,
        );
      });
    }

    return Scaffold(
      backgroundColor: const Color(0xFF121212),
      body: SafeArea(
        child: Column(
          children: [
            // 상단 헤더 - 사무실명과 포인트 한 줄 배치 (Native 앱과 동일)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  // 왼쪽: 사무실명
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      attributionState.when(
                        data: (attribution) => Text(
                          attribution?.officeName ?? '대리운전',
                          style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                        loading: () => const Text(
                          '대리운전',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                        error: (_, __) => const Text(
                          '대리운전',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ],
                  ),

                  // 오른쪽: 포인트 (오렌지색)
                  Row(
                    children: [
                      customerPointsAsync.when(
                        data: (points) {
                          final pointValue = points?.currentPoints ?? 0;
                          return Text(
                            '${NumberFormat('#,###').format(pointValue)}P',
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                              color: Color(0xFFFFAB00),
                            ),
                          );
                        },
                        loading: () => const Text(
                          '0P',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFFFFAB00),
                          ),
                        ),
                        error: (_, __) => const Text(
                          '0P',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFFFFAB00),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            // 상단: 만보기 카드 (좌우 여백 없음, Native 앱과 동일)
            const StepCounterCard(),

            // 중앙 Spacer
            const Spacer(),

            // 하단: 전화호출/앱호출 버튼 (세로 배치, Native 앱과 동일)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 56),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // 전화호출 버튼
                  _CircularMenuButton(
                    icon: Icons.phone,
                    label: '전화호출',
                    onPressed: () {
                      final attribution = attributionState.value;
                      if (attribution != null && attribution.officePhone.isNotEmpty) {
                        launchUrl(Uri.parse('tel:${attribution.officePhone}'));
                      }
                    },
                  ),

                  const SizedBox(height: 16),

                  // 앱호출 버튼
                  _CircularMenuButton(
                    icon: Icons.location_on,
                    label: '앱호출',
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const CallRequestScreen(),
                        ),
                      );
                    },
                  ),
                ],
              ),
            ),

            // 하단 Spacer
            const Spacer(),
          ],
        ),
      ),
    );
  }
}

/// 다이얼로그 정보 행
class _DialogInfoRow extends StatelessWidget {
  final String label;
  final String value;
  final Color valueColor;

  const _DialogInfoRow({
    required this.label,
    required this.value,
    required this.valueColor,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: Colors.white70,
            fontSize: 16,
          ),
        ),
        Text(
          value,
          style: TextStyle(
            color: valueColor,
            fontSize: 18,
            fontWeight: FontWeight.bold,
          ),
        ),
      ],
    );
  }
}

/// 원형 메뉴 버튼 (Native 앱의 CircularMenuButton과 동일)
class _CircularMenuButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onPressed;

  const _CircularMenuButton({
    required this.icon,
    required this.label,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    const mainColor = Color(0xFFFFAB00);
    const lightColor = Color(0xFFE69500);

    return SizedBox(
      width: 160,
      height: 160,
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          shape: const CircleBorder(),
          side: const BorderSide(width: 3, color: mainColor),
          backgroundColor: lightColor.withOpacity(0.3),
          padding: EdgeInsets.zero,
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: 50,
              color: mainColor,
            ),
            const SizedBox(height: 8),
            Text(
              label,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: mainColor,
              ),
            ),
          ],
        ),
      ),
    );
  }
}