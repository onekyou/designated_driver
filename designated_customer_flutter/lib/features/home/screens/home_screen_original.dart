import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../attribution/providers/attribution_provider.dart';
import '../../call/screens/call_request_screen.dart';
import '../../points/providers/point_provider.dart';
import '../widgets/step_counter_card.dart';
import 'package:url_launcher/url_launcher.dart';

/// 홈 화면 - 네이티브 앱과 동일한 레이아웃
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final attributionState = ref.watch(attributionNotifierProvider);
    final customerPointsAsync = ref.watch(customerPointsProvider);

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
