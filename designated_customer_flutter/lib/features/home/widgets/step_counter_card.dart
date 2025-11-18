import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

/// 메인 화면 만보기 카드 - 가로 레이아웃 (TODAY 버튼 - 원형 - SESSION 버튼/리셋)
class StepCounterCard extends StatefulWidget {
  const StepCounterCard({super.key});

  @override
  State<StepCounterCard> createState() => _StepCounterCardState();
}

class _StepCounterCardState extends State<StepCounterCard> {
  bool _isSessionMode = false;
  final int _todaySteps = 5234;
  final int _sessionSteps = 1250;
  final int _goal = 10000;

  @override
  Widget build(BuildContext context) {
    final todayProgress = (_todaySteps / _goal).clamp(0.0, 1.0);
    final sessionGoal = _goal / 2;
    final sessionProgress = (_sessionSteps / sessionGoal).clamp(0.0, 1.0);
    final displaySteps = _isSessionMode ? _sessionSteps : _todaySteps;
    final displayProgress = _isSessionMode ? sessionProgress : todayProgress;
    final displayLabel = _isSessionMode ? 'SESSION' : 'TODAY';

    return Card(
      elevation: 2,
      margin: EdgeInsets.zero,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      color: const Color(0xFF2C2C2C),
      child: SizedBox(
        height: 200,
        child: Stack(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Expanded(
                    flex: 1,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text('TODAY', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: !_isSessionMode ? const Color(0xFFFFAB00) : Colors.grey)),
                        const SizedBox(height: 4),
                        _buildCircleButton(steps: _todaySteps, isActive: !_isSessionMode, color: const Color(0xFFFFAB00), onPressed: () => setState(() => _isSessionMode = false)),
                      ],
                    ),
                  ),
                  Expanded(flex: 2, child: Center(child: _CircularStepCounter(steps: displaySteps, progress: displayProgress, label: displayLabel))),
                  Expanded(
                    flex: 1,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text('SESSION', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: _isSessionMode ? const Color(0xFF00BCD4) : Colors.grey)),
                        const SizedBox(height: 4),
                        _buildCircleButton(steps: _sessionSteps, isActive: false, color: const Color(0xFF00BCD4), onPressed: () => setState(() => _isSessionMode = true)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            Positioned(
              top: 4, right: 4,
              child: IconButton(onPressed: () {}, icon: const Icon(Icons.settings, size: 18, color: Color(0x80FFFFFF)), padding: EdgeInsets.zero, constraints: const BoxConstraints(minWidth: 32, minHeight: 32)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCircleButton({required int steps, required bool isActive, required Color color, required VoidCallback onPressed}) {
    return SizedBox(
      width: 80, height: 80,
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(shape: const CircleBorder(), side: BorderSide(width: 3, color: isActive ? color : Colors.grey), backgroundColor: isActive ? color.withOpacity(0.2) : Colors.transparent, padding: EdgeInsets.zero),
        child: Text(_formatSteps(steps), style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: isActive ? color : Colors.grey)),
      ),
    );
  }

  String _formatSteps(int steps) {
    if (steps >= 1000) return NumberFormat('#,###').format(steps);
    return steps.toString();
  }
}

class _CircularStepCounter extends StatelessWidget {
  final int steps;
  final double progress;
  final String label;

  const _CircularStepCounter({required this.steps, required this.progress, required this.label});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 140, height: 140,
      child: Stack(
        alignment: Alignment.center,
        children: [
          CustomPaint(size: const Size(140, 140), painter: _CircularProgressPainter(progress: progress)),
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Text('👟', style: TextStyle(fontSize: 24)),
              const SizedBox(height: 4),
              Text(_formatSteps(steps), style: const TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: Color(0xFF00BCD4), letterSpacing: 0)),
              const SizedBox(height: 2),
              Text(label, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w500, color: Color(0x99FFFFFF), letterSpacing: 0.5)),
            ],
          ),
        ],
      ),
    );
  }

  String _formatSteps(int steps) {
    if (steps >= 1000) return NumberFormat('#,###').format(steps);
    return steps.toString();
  }
}

class _CircularProgressPainter extends CustomPainter {
  final double progress;
  const _CircularProgressPainter({required this.progress});

  @override
  void paint(Canvas canvas, Size size) {
    const orangeColor = Color(0xFFFFAB00);
    const lightGray = Color(0xFFE0E0E0);
    const strokeWidth = 10.0;
    final center = Offset(size.width / 2, size.height / 2);
    final radius = (size.width - strokeWidth) / 2;

    canvas.drawCircle(center, radius, Paint()..color = lightGray..style = PaintingStyle.stroke..strokeWidth = strokeWidth..strokeCap = StrokeCap.round);

    if (progress > 0) {
      final sweepAngle = 2 * math.pi * progress;
      canvas.drawArc(Rect.fromCircle(center: center, radius: radius), -math.pi / 2, sweepAngle, false, Paint()..color = orangeColor..style = PaintingStyle.stroke..strokeWidth = strokeWidth..strokeCap = StrokeCap.round);
    }
  }

  @override
  bool shouldRepaint(_CircularProgressPainter oldDelegate) => oldDelegate.progress != progress;
}
