import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/point_constants.dart';
import '../providers/point_provider.dart';

/// 포인트 화면 (Phase 4C: 실제 데이터 연결)
class PointScreen extends ConsumerWidget {
  const PointScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final customerPointsAsync = ref.watch(customerPointsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('포인트'),
        elevation: 0,
      ),
      body: customerPointsAsync.when(
        data: (points) {
          if (points == null) {
            return const Center(
              child: Text('포인트 정보가 없습니다'),
            );
          }

          final grade = points.grade;
          final nextGrade = grade.nextGrade;
          final callsToNextGrade = points.getCallsToNextGrade();

          return SingleChildScrollView(
            child: Column(
              children: [
                // 포인트 요약 카드
                Container(
                  width: double.infinity,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        grade.color,
                        grade.color.withOpacity(0.7),
                      ],
                    ),
                  ),
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            grade.icon,
                            style: const TextStyle(fontSize: 32),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            grade.displayName,
                            style: const TextStyle(
                              fontSize: 24,
                              fontWeight: FontWeight.bold,
                              color: Colors.white,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      const Text(
                        '보유 포인트',
                        style: TextStyle(
                          fontSize: 16,
                          color: Colors.white70,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '${points.currentPoints} P',
                        style: const TextStyle(
                          fontSize: 48,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
                      const SizedBox(height: 24),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          _buildStatItem('총 적립', '${points.totalEarned} P'),
                          Container(
                            width: 1,
                            height: 30,
                            color: Colors.white30,
                          ),
                          _buildStatItem('총 사용', '${points.totalUsed} P'),
                          Container(
                            width: 1,
                            height: 30,
                            color: Colors.white30,
                          ),
                          _buildStatItem('이용 횟수', '${points.totalCalls} 회'),
                        ],
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 16),

                // 등급 진행률 카드
                if (nextGrade != null && callsToNextGrade != null)
                  Card(
                    margin: const EdgeInsets.all(16),
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Text(
                                '다음 등급까지',
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              Text(
                                '$callsToNextGrade 회 남음',
                                style: TextStyle(
                                  fontSize: 14,
                                  color: grade.color,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Row(
                            children: [
                              Text(
                                grade.icon,
                                style: const TextStyle(fontSize: 24),
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: ClipRRect(
                                  borderRadius: BorderRadius.circular(8),
                                  child: LinearProgressIndicator(
                                    value: points.totalCalls / nextGrade.minCalls,
                                    minHeight: 12,
                                    backgroundColor: Colors.grey[200],
                                    valueColor: AlwaysStoppedAnimation(grade.color),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                nextGrade.icon,
                                style: const TextStyle(fontSize: 24),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text(
                            '${nextGrade.displayName} 등급이 되면 ${(nextGrade.pointRate * 100).toInt()}% 적립!',
                            style: const TextStyle(
                              fontSize: 12,
                              color: Colors.grey,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),

                // 최고 등급 달성 메시지
                if (nextGrade == null)
                  Card(
                    margin: const EdgeInsets.all(16),
                    color: Colors.amber.shade50,
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Row(
                        children: [
                          Icon(Icons.emoji_events, color: Colors.amber.shade700, size: 32),
                          const SizedBox(width: 12),
                          const Expanded(
                            child: Text(
                              '최고 등급을 달성하셨습니다!',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),

                // 등급별 혜택 안내
                Card(
                  margin: const EdgeInsets.symmetric(horizontal: 16),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '등급별 혜택',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 16),
                        ...CustomerGrade.values.map((g) => _buildGradeRow(g, g == grade)),
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 16),

                // 포인트 사용 안내
                Card(
                  margin: const EdgeInsets.symmetric(horizontal: 16),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '포인트 사용 안내',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 12),
                        _buildInfoRow(Icons.check_circle, '대리운전 요금 결제 시 사용 가능'),
                        _buildInfoRow(Icons.check_circle, '1P = 1원으로 현금처럼 사용'),
                        _buildInfoRow(Icons.check_circle, '최소 1,000P부터 사용 가능'),
                        _buildInfoRow(Icons.check_circle, '운행 완료 후 자동 적립'),
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 80),
              ],
            ),
          );
        },
        loading: () => const Center(
          child: CircularProgressIndicator(),
        ),
        error: (error, stack) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error, size: 64, color: Colors.red),
              const SizedBox(height: 16),
              Text('오류: $error'),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () => ref.invalidate(customerPointsProvider),
                child: const Text('다시 시도'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatItem(String label, String value) {
    return Column(
      children: [
        Text(
          value,
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(
            fontSize: 12,
            color: Colors.white70,
          ),
        ),
      ],
    );
  }

  Widget _buildGradeRow(CustomerGrade grade, bool isCurrent) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isCurrent ? grade.color.withOpacity(0.1) : null,
        borderRadius: BorderRadius.circular(8),
        border: isCurrent ? Border.all(color: grade.color, width: 2) : null,
      ),
      child: Row(
        children: [
          Text(
            grade.icon,
            style: const TextStyle(fontSize: 24),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      grade.displayName,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: isCurrent ? grade.color : null,
                      ),
                    ),
                    if (isCurrent) ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color: grade.color,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: const Text(
                          '현재',
                          style: TextStyle(
                            fontSize: 10,
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  '${grade.minCalls}회 이상 · ${(grade.pointRate * 100).toInt()}% 적립',
                  style: TextStyle(
                    fontSize: 12,
                    color: isCurrent ? grade.color.withOpacity(0.7) : Colors.grey,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(IconData icon, String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(icon, size: 20, color: Colors.green),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(fontSize: 14),
            ),
          ),
        ],
      ),
    );
  }
}
