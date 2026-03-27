import 'dart:async';
import 'package:firebase_database/firebase_database.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Presence Service — Firebase Realtime DB (Kotlin PresenceManager 1:1 포팅)
/// 경로: presence/drivers/{userId}
/// 상태: online | background | offline
class PresenceService {
  final FirebaseDatabase _database;
  DatabaseReference? _presenceRef;
  StreamSubscription? _connectedSubscription;
  bool _initialized = false;

  /// 연결 상태 (UI에서 네트워크 끊김 배너 표시용)
  final ValueNotifier<bool> isConnected = ValueNotifier(true);

  PresenceService({FirebaseDatabase? database})
      : _database = database ?? FirebaseDatabase.instance;

  /// 초기화 (로그인 후 호출)
  Future<void> initialize(String userId) async {
    if (_initialized) return;
    _initialized = true;

    _presenceRef = _database.ref('presence/drivers/$userId');

    // onDisconnect → 자동 offline 설정
    await _presenceRef!.onDisconnect().set({
      'status': 'offline',
      'lastSeen': ServerValue.timestamp,
    });

    // 초기 상태: online
    await _presenceRef!.set({
      'status': 'online',
      'lastSeen': ServerValue.timestamp,
    });

    // .info/connected 리스너 → 연결 상태 추적
    _connectedSubscription = _database.ref('.info/connected').onValue.listen((event) {
      final connected = event.snapshot.value as bool? ?? false;
      isConnected.value = connected;

      if (connected && _presenceRef != null) {
        // 재연결 시 onDisconnect 재설정 + online 업데이트
        _presenceRef!.onDisconnect().set({
          'status': 'offline',
          'lastSeen': ServerValue.timestamp,
        });
        _presenceRef!.update({
          'status': 'online',
          'lastSeen': ServerValue.timestamp,
        });
      }

      debugPrint('[Presence] connected=$connected');
    });

    debugPrint('[Presence] 초기화 완료: $userId');
  }

  /// 앱 포그라운드
  Future<void> onAppForeground() async {
    if (_presenceRef == null) return;
    await _presenceRef!.update({
      'status': 'online',
      'lastSeen': ServerValue.timestamp,
    });
  }

  /// 앱 백그라운드
  Future<void> onAppBackground() async {
    if (_presenceRef == null) return;
    await _presenceRef!.update({
      'status': 'background',
      'lastSeen': ServerValue.timestamp,
    });
  }

  /// 로그아웃
  Future<void> onLogout() async {
    if (_presenceRef == null) return;
    await _presenceRef!.update({
      'status': 'offline',
      'lastSeen': ServerValue.timestamp,
    });
    await cleanup();
  }

  /// 리소스 정리
  Future<void> cleanup() async {
    _connectedSubscription?.cancel();
    _connectedSubscription = null;
    _presenceRef = null;
    _initialized = false;
  }
}

/// Riverpod Provider
final presenceServiceProvider = Provider((_) => PresenceService());
