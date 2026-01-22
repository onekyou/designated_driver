import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import '../../features/home/screens/home_screen.dart';
import '../../features/call/screens/call_history_screen.dart';
import '../../features/points/screens/point_screen.dart';
import '../../features/profile/screens/profile_screen.dart';
import '../services/storage_service.dart';
import '../../services/fcm_service.dart';

/// MainNavigation - BottomNavigationBar로 4개 탭 관리 (Native 앱과 동일)
class MainNavigation extends ConsumerStatefulWidget {
  const MainNavigation({super.key});

  @override
  ConsumerState<MainNavigation> createState() => _MainNavigationState();
}

class _MainNavigationState extends ConsumerState<MainNavigation> {
  int _currentIndex = 0;
  final FcmService _fcmService = FcmService();

  // 탭별 화면 목록
  final List<Widget> _screens = const [
    HomeScreen(),
    CallHistoryScreen(),
    PointScreen(),
    ProfileScreen(),
  ];

  @override
  void initState() {
    super.initState();
    _initializeFcm();
    _setupFcmListeners();
  }

  /// FCM 초기화
  Future<void> _initializeFcm() async {
    try {
      final storage = StorageService();
      final phoneNumber = await storage.getString('phoneNumber');
      final regionId = await storage.getString('regionId');
      final officeId = await storage.getString('officeId');

      if (phoneNumber == null || regionId == null || officeId == null) {
        debugPrint('[FCM] 필수 정보 없음 - FCM 초기화 스킵');
        return;
      }

      await _fcmService.initialize(
        regionId: regionId,
        officeId: officeId,
        phoneNumber: phoneNumber,
      );
    } catch (e) {
      debugPrint('[FCM] 초기화 실패: $e');
    }
  }

  /// FCM 메시지 리스너 설정
  void _setupFcmListeners() {
    // Foreground 메시지 수신
    _fcmService.listenToForegroundMessages((message) {
      _handleFcmMessage(message);
    });

    // Background → Foreground 전환 시 메시지 처리
    _fcmService.listenToMessageOpenedApp((message) {
      _handleFcmMessage(message);
    });

    // 앱이 종료된 상태에서 알림 클릭으로 실행된 경우
    _fcmService.getInitialMessage().then((message) {
      if (message != null) {
        _handleFcmMessage(message);
      }
    });
  }

  /// FCM 메시지 처리
  void _handleFcmMessage(RemoteMessage message) {
    final data = FcmService.parseNotificationData(message);
    final type = data['type'];

    debugPrint('[MainNavigation] FCM 메시지 처리: $type');

    switch (type) {
      case FcmService.notificationTypeDriverAssigned:
        _showDriverAssignedDialog(data);
        break;
      case FcmService.notificationTypeRideCompleted:
        _showRideCompletedDialog(data);
        break;
      case FcmService.notificationTypeCallCancelled:
        _showCallCancelledDialog(data);
        break;
      default:
        debugPrint('[MainNavigation] 알 수 없는 알림 타입: $type');
    }
  }

  /// 기사 배정 완료 다이얼로그
  void _showDriverAssignedDialog(Map<String, dynamic> data) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: const Text('기사 배정 완료'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('기사님: ${data['driverName'] ?? '알 수 없음'}'),
            Text('차량번호: ${data['vehicleNumber'] ?? '알 수 없음'}'),
            Text('연락처: ${data['driverPhone'] ?? '알 수 없음'}'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('확인'),
          ),
        ],
      ),
    );
  }

  /// 운행 완료 다이얼로그
  void _showRideCompletedDialog(Map<String, dynamic> data) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: const Text('운행 완료'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('요금: ${data['fare'] ?? 0}원'),
            Text('적립 포인트: ${data['earnedPoints'] ?? 0}P'),
            const SizedBox(height: 8),
            const Text(
              '이용해 주셔서 감사합니다!',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('확인'),
          ),
        ],
      ),
    );
  }

  /// 콜 취소 다이얼로그
  void _showCallCancelledDialog(Map<String, dynamic> data) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: const Text('콜 취소'),
        content: Text(data['message'] ?? '콜이 취소되었습니다.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('확인'),
          ),
        ],
      ),
    );
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
        backgroundColor: const Color(0xFF1E1E1E),
        selectedItemColor: const Color(0xFFFFAB00),
        unselectedItemColor: Colors.grey,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.home),
            label: '홈',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.list),
            label: '이용내역',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.star),
            label: '포인트',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.person),
            label: '내정보',
          ),
        ],
      ),
    );
  }
}
