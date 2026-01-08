# Phase 4A: UI 골격 완성 작업 계획

**작성일**: 2025-11-18
**현재 완성도**: 35%
**목표 완성도**: 50%
**예상 소요 시간**: 3-4일

---

## 📋 현재 상태 요약

### ✅ 완료된 작업 (Phase 1-3)
- Firebase Anonymous Auth
- Attribution 시스템
- 전화번호 입력 화면
- 콜 요청 시스템 (기본)
- 콜 내역 조회
- 위치 서비스 (GPS, 음성 인식)

### ❌ 누락된 작업
- **BottomNavigation** (4탭 구조)
- **HomeScreen 개선** (만보기 카드, 포인트 표시)
- **PointScreen** (UI만)
- **ProfileScreen** (UI만)

### 🎯 Phase 4A 목표
**"기능 구현 전에 UI 골격부터 완성"**
- 원본 앱과 동일한 레이아웃
- 모든 화면/카드/위젯 배치
- 기능은 하드코딩 또는 비활성 상태

---

## 🚀 Phase 4A 상세 작업 계획

### **Day 1: MainNavigation + BottomNavigationBar (4시간)**

#### 작업 1: 디렉토리 생성
```
lib/core/navigation/
└── main_navigation.dart
```

#### 작업 2: MainNavigation 위젯 구현

**파일**: `lib/core/navigation/main_navigation.dart`

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../features/home/screens/home_screen.dart';
import '../../features/call/screens/call_history_screen.dart';
import '../../features/points/screens/point_screen.dart';
import '../../features/profile/screens/profile_screen.dart';

/// Main Navigation with BottomNavigationBar
class MainNavigation extends ConsumerStatefulWidget {
  final String regionId;
  final String officeId;
  final String phoneNumber;

  const MainNavigation({
    super.key,
    required this.regionId,
    required this.officeId,
    required this.phoneNumber,
  });

  @override
  ConsumerState<MainNavigation> createState() => _MainNavigationState();
}

class _MainNavigationState extends ConsumerState<MainNavigation> {
  int _currentIndex = 0;

  late final List<Widget> _screens;

  @override
  void initState() {
    super.initState();
    _screens = [
      HomeScreen(
        regionId: widget.regionId,
        officeId: widget.officeId,
        phoneNumber: widget.phoneNumber,
      ),
      CallHistoryScreen(
        regionId: widget.regionId,
        officeId: widget.officeId,
        phoneNumber: widget.phoneNumber,
      ),
      PointScreen(
        regionId: widget.regionId,
        officeId: widget.officeId,
        phoneNumber: widget.phoneNumber,
      ),
      ProfileScreen(
        regionId: widget.regionId,
        officeId: widget.officeId,
        phoneNumber: widget.phoneNumber,
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: BottomNavigationBar(
        type: BottomNavigationBarType.fixed,
        currentIndex: _currentIndex,
        onTap: (index) {
          setState(() {
            _currentIndex = index;
          });
        },
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.home),
            label: '홈',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.list),
            label: '내역',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.star),
            label: '포인트',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.person),
            label: '프로필',
          ),
        ],
      ),
    );
  }
}
```

#### 작업 3: main.dart 수정

**파일**: `lib/main.dart`

기존 HomeScreen 호출 부분을 MainNavigation으로 변경:

```dart
// 변경 전
Navigator.pushReplacement(
  context,
  MaterialPageRoute(
    builder: (context) => HomeScreen(
      regionId: regionId,
      officeId: officeId,
      phoneNumber: phoneNumber,
    ),
  ),
);

// 변경 후
Navigator.pushReplacement(
  context,
  MaterialPageRoute(
    builder: (context) => MainNavigation(
      regionId: regionId,
      officeId: officeId,
      phoneNumber: phoneNumber,
    ),
  ),
);
```

#### 체크리스트
- [ ] `lib/core/navigation/main_navigation.dart` 생성
- [ ] MainNavigation 위젯 구현
- [ ] IndexedStack으로 화면 전환
- [ ] BottomNavigationBar 구현 (4탭)
- [ ] main.dart 수정
- [ ] 컴파일 오류 확인 (PointScreen, ProfileScreen 아직 없음)

---

### **Day 2: HomeScreen 개선 + StepCounterCard (4시간)**

#### 작업 1: StepCounterCard 위젯 생성

**디렉토리 생성**:
```
lib/features/home/widgets/
└── step_counter_card.dart
```

**파일**: `lib/features/home/widgets/step_counter_card.dart`

```dart
import 'package:flutter/material.dart';

/// 만보기 카드 (기능 없이 UI만)
class StepCounterCard extends StatelessWidget {
  const StepCounterCard({super.key});

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 2,
      margin: const EdgeInsets.all(16),
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          children: [
            // 제목
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '오늘의 걸음',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                ),
                IconButton(
                  icon: const Icon(Icons.info_outline),
                  onPressed: () {
                    // TODO: 상세 정보 바텀시트
                  },
                ),
              ],
            ),

            const SizedBox(height: 24),

            // 원형 진행률 + 걸음수
            Stack(
              alignment: Alignment.center,
              children: [
                SizedBox(
                  width: 160,
                  height: 160,
                  child: CircularProgressIndicator(
                    value: 0.0, // TODO: 실제 센서 연동
                    strokeWidth: 12,
                    backgroundColor: Colors.grey[200],
                    valueColor: AlwaysStoppedAnimation<Color>(
                      Theme.of(context).colorScheme.primary,
                    ),
                  ),
                ),
                Column(
                  children: [
                    Text(
                      '0', // TODO: 실제 걸음수
                      style: Theme.of(context).textTheme.displayMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                    Text(
                      '걸음',
                      style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                            color: Colors.grey[600],
                          ),
                    ),
                  ],
                ),
              ],
            ),

            const SizedBox(height: 24),

            // 목표 표시
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  '목표: 10,000 걸음',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Colors.grey[600],
                      ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // 세션 버튼
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                OutlinedButton.icon(
                  onPressed: null, // TODO: 세션 시작
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('시작'),
                ),
                OutlinedButton.icon(
                  onPressed: null, // TODO: 세션 리셋
                  icon: const Icon(Icons.refresh),
                  label: const Text('리셋'),
                ),
              ],
            ),

            const SizedBox(height: 8),

            // 안내 문구
            Text(
              '만보기 기능은 추후 구현 예정입니다',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Colors.grey[500],
                    fontStyle: FontStyle.italic,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}
```

#### 작업 2: HomeScreen 수정

**파일**: `lib/features/home/screens/home_screen.dart`

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import '../widgets/step_counter_card.dart';
import '../../call/screens/call_request_screen.dart';
import '../../attribution/providers/attribution_provider.dart';

class HomeScreen extends ConsumerWidget {
  final String regionId;
  final String officeId;
  final String phoneNumber;

  const HomeScreen({
    super.key,
    required this.regionId,
    required this.officeId,
    required this.phoneNumber,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final attribution = ref.watch(attributionNotifierProvider).value;
    final officeName = attribution?.officeName ?? '사무실';
    final officePhone = attribution?.officePhone;

    return Scaffold(
      appBar: AppBar(
        title: Text(officeName),
        centerTitle: true,
        actions: [
          // 포인트 잔액 표시
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: Center(
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.star,
                      size: 16,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      '0 P', // TODO: 실제 포인트
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            // 만보기 카드
            const StepCounterCard(),

            const SizedBox(height: 16),

            // 대리운전 호출 버튼
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              child: Row(
                children: [
                  // 전화호출 버튼
                  Expanded(
                    child: _buildCallButton(
                      context: context,
                      icon: Icons.phone,
                      label: '전화호출',
                      color: Colors.blue,
                      onPressed: officePhone != null
                          ? () => _makePhoneCall(officePhone)
                          : null,
                    ),
                  ),
                  const SizedBox(width: 16),
                  // 앱호출 버튼
                  Expanded(
                    child: _buildCallButton(
                      context: context,
                      icon: Icons.directions_car,
                      label: '앱호출',
                      color: Colors.green,
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (context) => const CallRequestScreen(),
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  Widget _buildCallButton({
    required BuildContext context,
    required IconData icon,
    required String label,
    required Color color,
    required VoidCallback? onPressed,
  }) {
    return Container(
      height: 120,
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onPressed,
          borderRadius: BorderRadius.circular(16),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                icon,
                size: 48,
                color: color,
              ),
              const SizedBox(height: 8),
              Text(
                label,
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: color,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _makePhoneCall(String phoneNumber) async {
    final uri = Uri.parse('tel:$phoneNumber');
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri);
    }
  }
}
```

#### 작업 3: pubspec.yaml 확인

```yaml
dependencies:
  url_launcher: ^6.2.2  # 전화 앱 실행용
```

#### 체크리스트
- [ ] `lib/features/home/widgets/step_counter_card.dart` 생성
- [ ] StepCounterCard 위젯 구현 (기능 없이)
- [ ] HomeScreen 수정
  - [ ] StepCounterCard 추가
  - [ ] 상단 포인트 잔액 표시
  - [ ] 전화호출/앱호출 버튼 스타일 개선
  - [ ] "콜 내역" 버튼 제거 (BottomNav로 이동)
- [ ] url_launcher 패키지 확인

---

### **Day 3: PointScreen 기본 UI (3시간)**

#### 작업 1: 디렉토리 생성
```
lib/features/points/
├── screens/
│   └── point_screen.dart
└── widgets/
    ├── point_card.dart
    └── grade_info_card.dart
```

#### 작업 2: PointCard 위젯

**파일**: `lib/features/points/widgets/point_card.dart`

```dart
import 'package:flutter/material.dart';

class PointCard extends StatelessWidget {
  const PointCard({super.key});

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 4,
      margin: const EdgeInsets.all(16),
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Theme.of(context).colorScheme.primary,
              Theme.of(context).colorScheme.secondary,
            ],
          ),
          borderRadius: BorderRadius.circular(12),
        ),
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 등급 배지
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.3),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Row(
                    children: const [
                      Text(
                        '🥉',
                        style: TextStyle(fontSize: 16),
                      ),
                      SizedBox(width: 4),
                      Text(
                        '브론즈',
                        style: TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.refresh, color: Colors.white),
                  onPressed: () {
                    // TODO: 새로고침
                  },
                ),
              ],
            ),

            const SizedBox(height: 24),

            // 보유 포인트
            const Text(
              '보유 포인트',
              style: TextStyle(
                color: Colors.white70,
                fontSize: 14,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              '0 P', // TODO: 실제 포인트
              style: TextStyle(
                color: Colors.white,
                fontSize: 42,
                fontWeight: FontWeight.bold,
              ),
            ),

            const SizedBox(height: 24),

            // 진행률
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '실버 등급까지 10회 남음',
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 12,
                  ),
                ),
                const SizedBox(height: 8),
                LinearProgressIndicator(
                  value: 0.0, // TODO: 실제 진행률
                  backgroundColor: Colors.white.withOpacity(0.3),
                  valueColor: const AlwaysStoppedAnimation<Color>(Colors.white),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
```

#### 작업 3: GradeInfoCard 위젯

**파일**: `lib/features/points/widgets/grade_info_card.dart`

```dart
import 'package:flutter/material.dart';

class GradeInfoCard extends StatelessWidget {
  const GradeInfoCard({super.key});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '등급 혜택',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: 16),
            _buildGradeRow('🥉', '브론즈', '3% 적립', '0회 이상'),
            const Divider(),
            _buildGradeRow('🥈', '실버', '5% 적립', '10회 이상'),
            const Divider(),
            _buildGradeRow('🥇', '골드', '7% 적립', '30회 이상'),
            const Divider(),
            _buildGradeRow('⭐', 'VIP', '9% 적립', '50회 이상'),
          ],
        ),
      ),
    );
  }

  Widget _buildGradeRow(
    String emoji,
    String name,
    String benefit,
    String requirement,
  ) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        children: [
          Text(emoji, style: const TextStyle(fontSize: 24)),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 16,
                  ),
                ),
                Text(
                  requirement,
                  style: TextStyle(
                    color: Colors.grey[600],
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          Text(
            benefit,
            style: const TextStyle(
              fontWeight: FontWeight.bold,
              color: Colors.green,
            ),
          ),
        ],
      ),
    );
  }
}
```

#### 작업 4: PointScreen

**파일**: `lib/features/points/screens/point_screen.dart`

```dart
import 'package:flutter/material.dart';
import '../widgets/point_card.dart';
import '../widgets/grade_info_card.dart';

class PointScreen extends StatelessWidget {
  final String regionId;
  final String officeId;
  final String phoneNumber;

  const PointScreen({
    super.key,
    required this.regionId,
    required this.officeId,
    required this.phoneNumber,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('포인트'),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            // 포인트 카드
            const PointCard(),

            // 등급 안내
            const GradeInfoCard(),

            // 포인트 사용 안내
            Card(
              margin: const EdgeInsets.all(16),
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(
                          Icons.info_outline,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          '포인트 사용 안내',
                          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      '• 앱 호출 시 포인트를 사용하여 요금을 할인받을 수 있습니다\n'
                      '• 운행 완료 시 등급에 따라 포인트가 자동 적립됩니다\n'
                      '• 포인트는 1P = 1원으로 사용됩니다\n'
                      '• 이용 횟수가 증가하면 등급이 자동으로 올라갑니다',
                      style: TextStyle(height: 1.5),
                    ),
                    const SizedBox(height: 16),
                    Center(
                      child: Text(
                        '포인트 기능은 추후 구현 예정입니다',
                        style: TextStyle(
                          color: Colors.grey[500],
                          fontStyle: FontStyle.italic,
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

#### 체크리스트
- [ ] `lib/features/points/` 디렉토리 생성
- [ ] `point_card.dart` 생성 (그라데이션 배경)
- [ ] `grade_info_card.dart` 생성 (4단계 등급 안내)
- [ ] `point_screen.dart` 생성
- [ ] 레이아웃 확인

---

### **Day 4: ProfileScreen 기본 UI (2시간)**

#### 작업 1: 디렉토리 생성
```
lib/features/profile/
└── screens/
    └── profile_screen.dart
```

#### 작업 2: ProfileScreen

**파일**: `lib/features/profile/screens/profile_screen.dart`

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../../attribution/providers/attribution_provider.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  final String regionId;
  final String officeId;
  final String phoneNumber;

  const ProfileScreen({
    super.key,
    required this.regionId,
    required this.officeId,
    required this.phoneNumber,
  });

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  String _appVersion = '';

  @override
  void initState() {
    super.initState();
    _loadAppVersion();
  }

  Future<void> _loadAppVersion() async {
    final packageInfo = await PackageInfo.fromPlatform();
    setState(() {
      _appVersion = packageInfo.version;
    });
  }

  @override
  Widget build(BuildContext context) {
    final attribution = ref.watch(attributionNotifierProvider).value;
    final officeName = attribution?.officeName ?? '알 수 없음';
    final officePhone = attribution?.officePhone ?? '정보 없음';
    final bankName = attribution?.bankName ?? '정보 없음';
    final accountNumber = attribution?.accountNumber ?? '정보 없음';
    final accountHolder = attribution?.accountHolder ?? '정보 없음';

    return Scaffold(
      appBar: AppBar(
        title: const Text('프로필'),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            const SizedBox(height: 16),

            // 고객 정보 카드
            Card(
              margin: const EdgeInsets.all(16),
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(
                          Icons.person,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          '고객 정보',
                          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    _buildInfoRow('전화번호', widget.phoneNumber),
                    const Divider(),
                    _buildInfoRow('이름', '미입력'), // TODO: 실제 이름
                    const Divider(),
                    _buildInfoRow('지역', _getRegionName(widget.regionId)),
                    const Divider(),
                    _buildInfoRow('사무실', officeName),
                    const SizedBox(height: 16),
                    Center(
                      child: Text(
                        '프로필 수정 기능은 추후 구현 예정입니다',
                        style: TextStyle(
                          color: Colors.grey[500],
                          fontStyle: FontStyle.italic,
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // 사무실 정보 카드
            Card(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(
                          Icons.business,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          '사무실 정보',
                          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    _buildInfoRow('사무실명', officeName),
                    const Divider(),
                    _buildInfoRow('전화번호', officePhone),
                    const Divider(),
                    _buildInfoRow('은행', bankName),
                    const Divider(),
                    _buildInfoRow('계좌번호', accountNumber),
                    const Divider(),
                    _buildInfoRow('예금주', accountHolder),
                  ],
                ),
              ),
            ),

            // 앱 정보 카드
            Card(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(
                          Icons.info,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          '앱 정보',
                          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    _buildInfoRow('버전', _appVersion),
                    const Divider(),
                    _buildInfoRow('앱 이름', '대리운전 고객앱'),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: TextStyle(
              color: Colors.grey[600],
              fontSize: 14,
            ),
          ),
          Flexible(
            child: Text(
              value,
              style: const TextStyle(
                fontWeight: FontWeight.w500,
                fontSize: 14,
              ),
              textAlign: TextAlign.right,
            ),
          ),
        ],
      ),
    );
  }

  String _getRegionName(String regionId) {
    switch (regionId) {
      case 'seoul':
        return '서울';
      case 'gyeonggi':
        return '경기';
      case 'hongchon':
        return '홍천';
      default:
        return regionId;
    }
  }
}
```

#### 작업 3: pubspec.yaml 추가

```yaml
dependencies:
  package_info_plus: ^5.0.1  # 앱 버전 정보
```

#### 체크리스트
- [ ] `lib/features/profile/screens/profile_screen.dart` 생성
- [ ] 고객 정보 카드
- [ ] 사무실 정보 카드 (Attribution에서 가져오기)
- [ ] 앱 정보 카드
- [ ] package_info_plus 패키지 추가

---

## ✅ Phase 4A 완료 체크리스트

### Day 1
- [ ] MainNavigation.dart 생성
- [ ] BottomNavigationBar 4탭 구현
- [ ] main.dart 수정
- [ ] 빌드 확인 (오류 예상, PointScreen/ProfileScreen 없음)

### Day 2
- [ ] StepCounterCard 위젯 생성
- [ ] HomeScreen 수정 (만보기 카드, 포인트 표시)
- [ ] url_launcher 패키지 확인

### Day 3
- [ ] PointCard 위젯
- [ ] GradeInfoCard 위젯
- [ ] PointScreen 생성
- [ ] 레이아웃 확인

### Day 4
- [ ] ProfileScreen 생성
- [ ] package_info_plus 패키지 추가
- [ ] 전체 빌드 확인
- [ ] 4개 탭 전환 테스트

---

## 🚀 다음 세션 시작 방법

### 1. 프로젝트 열기
```bash
cd C:\app_dev\designated_driver\designated_customer_flutter
code .
```

### 2. 첫 작업 시작
```
Phase 4A Day 1: MainNavigation 생성
→ lib/core/navigation/main_navigation.dart 생성
→ 위의 코드 복사
```

### 3. 순차 진행
```
Day 1 완료 → 빌드 (오류 확인)
Day 2 완료 → 빌드
Day 3 완료 → 빌드
Day 4 완료 → 최종 빌드 및 테스트
```

---

## 📊 예상 결과

### Phase 4A 완료 후
- **완성도**: 35% → 50%
- **UI 골격**: 100% (모든 화면 존재)
- **기능**: 20% (대부분 하드코딩)
- **빌드**: ✅ 성공
- **실행**: ✅ 4개 탭 전환 가능

### 다음 단계 (Phase 4B)
- 랜딩페이지 연동 테스트
- Firebase 연동 검증
- 기본 플로우 확인

### 최종 목표 (Phase 4C)
- 포인트 시스템 구현
- FCM 푸시 알림
- 프로필 관리
- 통계 기능

---

**작성자**: Claude
**최종 수정**: 2025-11-18
**다음 작업**: Phase 4A Day 1 시작
