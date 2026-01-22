import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import '../../auth/providers/auth_provider.dart';
import '../../attribution/providers/attribution_provider.dart';
import '../../points/providers/point_provider.dart';
import '../../call/repositories/call_repository.dart';

/// 포인트 적립 감시 상태
class PointEarnWatcherState {
  final String? lastActiveCallId;
  final bool showEarnedDialog;
  final int earnedPoints;
  final int usedPoints;
  final int fare;

  const PointEarnWatcherState({
    this.lastActiveCallId,
    this.showEarnedDialog = false,
    this.earnedPoints = 0,
    this.usedPoints = 0,
    this.fare = 0,
  });

  PointEarnWatcherState copyWith({
    String? lastActiveCallId,
    bool? showEarnedDialog,
    int? earnedPoints,
    int? usedPoints,
    int? fare,
  }) {
    return PointEarnWatcherState(
      lastActiveCallId: lastActiveCallId ?? this.lastActiveCallId,
      showEarnedDialog: showEarnedDialog ?? this.showEarnedDialog,
      earnedPoints: earnedPoints ?? this.earnedPoints,
      usedPoints: usedPoints ?? this.usedPoints,
      fare: fare ?? this.fare,
    );
  }
}

/// 포인트 적립 감시 Notifier (네이티브 앱의 MainViewModel 로직과 동일)
class PointEarnWatcherNotifier extends StateNotifier<PointEarnWatcherState> {
  final Ref _ref;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  PointEarnWatcherNotifier(this._ref) : super(const PointEarnWatcherState());

  /// 활성 콜이 사라졌을 때 호출 (네이티브 앱의 checkAndHandleCompletedCall)
  Future<void> onActiveCallDisappeared(String callId) async {
    debugPrint('[PointEarnWatcher] 활성 콜 사라짐 감지: $callId');
    
    final attribution = _ref.read(attributionNotifierProvider).value;
    final user = _ref.read(authNotifierProvider).value;
    
    if (attribution == null || user?.phoneNumber == null) {
      debugPrint('[PointEarnWatcher] attribution 또는 phoneNumber 없음');
      return;
    }

    try {
      // Firestore에서 콜 정보 가져오기
      final callDoc = await _firestore
          .collection('regions')
          .doc(attribution.regionId)
          .collection('offices')
          .doc(attribution.officeId)
          .collection('calls')
          .doc(callId)
          .get();

      if (!callDoc.exists) {
        debugPrint('[PointEarnWatcher] 콜 문서가 존재하지 않음: $callId');
        return;
      }

      final status = callDoc.data()?['status'] as String?;
      debugPrint('[PointEarnWatcher] 콜 상태 확인: callId=$callId, status=$status');

      if (status == 'COMPLETED') {
        debugPrint('[PointEarnWatcher] 운행 완료 확인! 포인트 적립 시작');
        await _awardPointsForCompletedRide(
          callId: callId,
          callData: callDoc.data()!,
          phoneNumber: user!.phoneNumber!,
          regionId: attribution.regionId,
          officeId: attribution.officeId,
        );
      }
    } catch (e, stack) {
      debugPrint('[PointEarnWatcher] 콜 완료 확인 실패: $e');
      debugPrint('[PointEarnWatcher] $stack');
    }
  }

  /// 포인트 적립 처리 (네이티브 앱의 awardPointsForCompletedRide)
  Future<void> _awardPointsForCompletedRide({
    required String callId,
    required Map<String, dynamic> callData,
    required String phoneNumber,
    required String regionId,
    required String officeId,
  }) async {
    try {
      // 사용된 포인트 가져오기
      final pointsUsed = (callData['pointsUsed'] as num?)?.toInt() ?? 0;

      // 중복 적립 방지 - 이미 거래 내역이 있으면 스킵
      final existingTransactions = await _firestore
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('pointTransactions')
          .where('callId', isEqualTo: callId)
          .where('customerId', isEqualTo: phoneNumber)
          .limit(1)
          .get();

      if (existingTransactions.docs.isNotEmpty) {
        debugPrint('[PointEarnWatcher] 이미 포인트 처리됨 - 팝업만 표시');

        // 적립 포인트 계산 (팝업용)
        final fare = (callData['fare_set'] as num?)?.toInt() ??
            (callData['finalFare'] as num?)?.toInt() ??
            (callData['fare'] as num?)?.toInt() ??
            0;

        final customerPoints = _ref.read(customerPointsProvider).value;
        final earnAmount = customerPoints?.calculateEarnPoints(fare) ?? 0;

        // 팝업 표시
        state = PointEarnWatcherState(
          showEarnedDialog: true,
          earnedPoints: earnAmount,
          usedPoints: pointsUsed,
          fare: fare,
        );
        return;
      }

      // 거래 내역 없음 - 포인트 적립 진행
      final fare = (callData['fare'] as num?)?.toInt() ??
          (callData['finalFare'] as num?)?.toInt() ??
          (callData['fare_set'] as num?)?.toInt();

      debugPrint('[PointEarnWatcher] 요금 확인: fare=${callData['fare']}, finalFare=${callData['finalFare']}, fare_set=${callData['fare_set']}, 최종=$fare');

      if (fare != null && fare > 0) {
        debugPrint('[PointEarnWatcher] 포인트 적립 시도: phoneNumber=$phoneNumber, callId=$callId, fare=$fare');

        // 포인트 적립
        final pointNotifier = _ref.read(pointNotifierProvider.notifier);
        final success = await pointNotifier.earnPoints(
          callId: callId,
          fare: fare,
          description: '대리운전 이용 완료',
        );

        debugPrint('[PointEarnWatcher] 포인트 적립 결과: success=$success');

        if (success) {
          // 적립된 포인트 계산
          final customerPoints = _ref.read(customerPointsProvider).value;
          final earnedPoints = customerPoints?.calculateEarnPoints(fare) ?? 0;

          debugPrint('[PointEarnWatcher] 포인트 적립 성공: $fare 원에 대한 $earnedPoints 포인트');

          // 포인트 적립 팝업 표시
          state = PointEarnWatcherState(
            showEarnedDialog: true,
            earnedPoints: earnedPoints,
            usedPoints: pointsUsed,
            fare: fare,
          );
        }
      }
    } catch (e, stack) {
      debugPrint('[PointEarnWatcher] 포인트 적립 실패: $e');
      debugPrint('[PointEarnWatcher] $stack');
    }
  }

  /// 다이얼로그 닫기
  void dismissDialog() {
    state = const PointEarnWatcherState(
      showEarnedDialog: false,
      earnedPoints: 0,
      usedPoints: 0,
      fare: 0,
    );
  }

  /// 마지막 활성 콜 ID 업데이트
  void updateLastActiveCallId(String? callId) {
    state = state.copyWith(lastActiveCallId: callId);
  }
}

/// PointEarnWatcher Provider
final pointEarnWatcherProvider =
    StateNotifierProvider<PointEarnWatcherNotifier, PointEarnWatcherState>((ref) {
  return PointEarnWatcherNotifier(ref);
});
