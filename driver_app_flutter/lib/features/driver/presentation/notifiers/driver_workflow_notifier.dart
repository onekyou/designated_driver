import 'dart:async';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../../core/constants/app_constants.dart';
import '../../../call/domain/entities/call.dart';
import '../../domain/entities/driver.dart';
import '../state/driver_screen_ui_state.dart';

/// DriverWorkflowNotifier — Kotlin DriverViewModel 1:1 포팅
/// 콜/운행/기사 상태를 Firestore 직접 처리, 낙관적 UI + 롤백
class DriverWorkflowNotifier extends StateNotifier<DriverScreenUiState> {
  final FirebaseFirestore _firestore;
  final FirebaseAuth _auth;
  final SharedPreferences _prefs;

  // ── 추가 StateFlow 대응 ──
  TodaySettlement _todaySettlement = const TodaySettlement();
  TodaySettlement get todaySettlement => _todaySettlement;

  List<TripHistoryItem> _tripHistoryList = [];
  List<TripHistoryItem> get tripHistoryList => _tripHistoryList;

  DriverCarryOver? _carryOver;
  DriverCarryOver? get carryOver => _carryOver;

  int _depositRatio = 60;
  int get depositRatio => _depositRatio;

  int _lastClearedMillis = 0;
  int get lastClearedMillis => _lastClearedMillis;

  DailySettlementStatus _dailySettlementStatus = DailySettlementStatus.working;
  DailySettlementStatus get dailySettlementStatus => _dailySettlementStatus;

  bool _isAccepting = false;
  bool get isAccepting => _isAccepting;

  bool _isSubmittingSettlement = false;
  bool get isSubmittingSettlement => _isSubmittingSettlement;

  // ── Firestore 리스너 ──
  StreamSubscription? _carryOverSubscription;

  DriverWorkflowNotifier({
    required FirebaseFirestore firestore,
    required FirebaseAuth auth,
    required SharedPreferences prefs,
  })  : _firestore = firestore,
        _auth = auth,
        _prefs = prefs,
        super(const DriverScreenUiState());

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 경로 헬퍼
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  String get _provinceId => _prefs.getString(AppConstants.keyProvinceId) ?? '';
  String get _cityId => _prefs.getString(AppConstants.keyCityId) ?? '';
  String get _officeId => _prefs.getString(AppConstants.keyOfficeId) ?? '';
  String get _driverId => _auth.currentUser?.uid ?? '';

  CollectionReference get _callsRef => _firestore
      .collection(AppConstants.collectionProvinces).doc(_provinceId)
      .collection(AppConstants.collectionCities).doc(_cityId)
      .collection(AppConstants.collectionOffices).doc(_officeId)
      .collection(AppConstants.collectionCalls);

  DocumentReference get _driverRef => _firestore
      .collection(AppConstants.collectionProvinces).doc(_provinceId)
      .collection(AppConstants.collectionCities).doc(_cityId)
      .collection(AppConstants.collectionOffices).doc(_officeId)
      .collection(AppConstants.collectionDrivers).doc(_driverId);

  DocumentReference get _officeRef => _firestore
      .collection(AppConstants.collectionProvinces).doc(_provinceId)
      .collection(AppConstants.collectionCities).doc(_cityId)
      .collection(AppConstants.collectionOffices).doc(_officeId);

  void _assertLocationInfo() {
    if (_provinceId.isEmpty || _cityId.isEmpty || _officeId.isEmpty) {
      throw StateError('Province/City/Office ID가 설정되지 않았습니다.');
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 초기화 (로그인 후 호출)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> initialize(String provinceId, String cityId, String officeId) async {
    await _prefs.setString(AppConstants.keyProvinceId, provinceId);
    await _prefs.setString(AppConstants.keyCityId, cityId);
    await _prefs.setString(AppConstants.keyOfficeId, officeId);

    await _loadCurrentActiveCall();
    _startCarryOverListener();
    await _loadSettlementData();
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 앱 시작 시 현재 콜 로드 (1회 조회, .get())
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> _loadCurrentActiveCall() async {
    try {
      _assertLocationInfo();
      final driverId = _driverId;

      // 기사 상태 조회
      final driverDoc = await _driverRef.get();
      if (driverDoc.exists) {
        final data = driverDoc.data() as Map<String, dynamic>?;
        final statusStr = data?[AppConstants.fieldStatus] as String?;
        final driverStatus = DriverStatus.fromString(statusStr);
        state = state.copyWith(driverStatus: driverStatus);
      }

      // 활성 콜 조회 (ASSIGNED, ACCEPTED, IN_PROGRESS, AWAITING_SETTLEMENT)
      final callsSnap = await _callsRef
          .where('assignedDriverId', isEqualTo: driverId)
          .where('status', whereIn: [
            AppConstants.callStatusAssigned,
            AppConstants.callStatusAccepted,
            AppConstants.callStatusInProgress,
            AppConstants.callStatusAwaitingSettlement,
          ])
          .get();

      final calls = callsSnap.docs.map(_docToCall).toList();

      Call? activeCall;
      Call? callForSettlement;
      Call? newCallPopup;
      final assignedCalls = <Call>[];

      for (final call in calls) {
        assignedCalls.add(call);
        switch (call.status) {
          case CallStatus.assigned:
            newCallPopup ??= call;
            break;
          case CallStatus.accepted:
          case CallStatus.inProgress:
            activeCall = call;
            break;
          case CallStatus.awaitingSettlement:
            callForSettlement = call;
            break;
          default:
            break;
        }
      }

      state = state.copyWith(
        assignedCalls: assignedCalls,
        activeCall: () => activeCall,
        callForSettlement: () => callForSettlement,
        newCallPopup: () => newCallPopup,
      );

      debugPrint('[Workflow] 초기 로드: ${calls.length}건 (active=$activeCall, popup=$newCallPopup)');
    } catch (e) {
      debugPrint('[Workflow] 초기 로드 실패: $e');
      state = state.copyWith(errorMessage: () => '초기 로드 실패: $e');
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 콜 수락 (낙관적 UI + 트랜잭션 + 롤백)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> acceptCall(String callId) async {
    if (_isAccepting) return;
    _isAccepting = true;

    try {
      _assertLocationInfo();

      // 1. 낙관적 UI 업데이트
      final acceptedCall = state.assignedCalls.where((c) => c.id == callId).firstOrNull;
      if (acceptedCall != null) {
        state = state.copyWith(
          assignedCalls: state.assignedCalls.map((c) =>
            c.id == callId ? _callWithStatus(c, CallStatus.accepted) : c
          ).toList(),
          activeCall: () => _callWithStatus(acceptedCall, CallStatus.accepted),
          newCallPopup: () => null,
          driverStatus: DriverStatus.accepted,
        );
      }

      // 2. Firestore 트랜잭션
      final callRef = _callsRef.doc(callId);
      await _firestore.runTransaction((tx) async {
        final snap = await tx.get(callRef);
        if (!snap.exists) throw Exception('콜 문서를 찾을 수 없습니다.');
        final currentStatus = snap.get(AppConstants.fieldStatus) as String?;
        if (currentStatus != AppConstants.callStatusAssigned) {
          throw Exception('수락 불가: 현재 상태 $currentStatus');
        }
        tx.update(callRef, {AppConstants.fieldStatus: AppConstants.callStatusAccepted});
        tx.update(_driverRef, {AppConstants.fieldStatus: DriverStatus.preparing.value});
      });

      debugPrint('[Workflow] 콜 수락 완료: $callId');
    } catch (e) {
      debugPrint('[Workflow] 콜 수락 실패: $e');
      // 롤백
      state = state.copyWith(
        assignedCalls: state.assignedCalls.map((c) =>
          c.id == callId ? _callWithStatus(c, CallStatus.assigned) : c
        ).toList(),
        activeCall: () => null,
        newCallPopup: () => state.assignedCalls.where((c) => c.id == callId).firstOrNull,
        driverStatus: DriverStatus.assigned,
        errorMessage: () => e.toString(),
      );
    } finally {
      _isAccepting = false;
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 콜 거절
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> rejectCall(String callId) async {
    try {
      _assertLocationInfo();
      final callRef = _callsRef.doc(callId);

      await _firestore.runTransaction((tx) async {
        tx.update(callRef, {
          AppConstants.fieldStatus: AppConstants.callStatusWaiting,
          'assignedDriverId': FieldValue.delete(),
          'assignedDriverName': FieldValue.delete(),
          'assignedDriverPhone': FieldValue.delete(),
          'rejectedByDriver': _driverId,
          'updatedAt': FieldValue.serverTimestamp(),
        });
        tx.update(_driverRef, {AppConstants.fieldStatus: DriverStatus.waiting.value});
      });

      state = state.copyWith(
        assignedCalls: state.assignedCalls.where((c) => c.id != callId).toList(),
        newCallPopup: () => null,
        activeCall: () => state.activeCall?.id == callId ? null : state.activeCall,
        driverStatus: DriverStatus.waiting,
      );

      debugPrint('[Workflow] 콜 거절 완료: $callId');
    } catch (e) {
      debugPrint('[Workflow] 콜 거절 실패: $e');
      state = state.copyWith(errorMessage: () => e.toString());
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 운행 시작
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> startDriving({
    required String callId,
    required String departure,
    required String destination,
    String waypoints = '',
    required int fare,
  }) async {
    try {
      _assertLocationInfo();
      final tripSummary = '출발: $departure, 도착: $destination, 경유: ${waypoints.isEmpty ? "없음" : waypoints}, 요금: $fare 원';

      // 1. 낙관적 UI
      state = state.copyWith(
        activeCall: () => state.activeCall != null
            ? _callWithStatus(state.activeCall!, CallStatus.inProgress)
            : null,
        driverStatus: DriverStatus.onTrip,
      );

      // 2. Firestore 트랜잭션
      final callRef = _callsRef.doc(callId);
      await _firestore.runTransaction((tx) async {
        tx.update(callRef, {
          AppConstants.fieldStatus: AppConstants.callStatusInProgress,
          'departure_set': departure,
          'destination_set': destination,
          'waypoints_set': waypoints,
          'fare_set': fare,
          'trip_summary': tripSummary,
          'updatedAt': FieldValue.serverTimestamp(),
        });
        tx.update(_driverRef, {AppConstants.fieldStatus: DriverStatus.onTrip.value});
      });

      debugPrint('[Workflow] 운행 시작: $callId');
    } catch (e) {
      debugPrint('[Workflow] 운행 시작 실패: $e');
      state = state.copyWith(errorMessage: () => e.toString());
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 운행 완료 (정산 대기)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> completeCall(String callId) async {
    try {
      _assertLocationInfo();

      // 1. 낙관적 UI
      final completedCall = state.activeCall != null
          ? _callWithStatus(state.activeCall!, CallStatus.awaitingSettlement)
          : null;

      state = state.copyWith(
        activeCall: () => null,
        callForSettlement: () => completedCall,
        isLoading: false,
      );

      // 2. Firestore 업데이트
      await _callsRef.doc(callId).update({
        AppConstants.fieldStatus: AppConstants.callStatusAwaitingSettlement,
      });

      debugPrint('[Workflow] 운행 완료: $callId');
    } catch (e) {
      debugPrint('[Workflow] 운행 완료 실패: $e');
      state = state.copyWith(errorMessage: () => e.toString());
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 운행 취소 (기사 취소)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> cancelTrip(String callId, {String reason = '운행취소'}) async {
    try {
      _assertLocationInfo();
      final callRef = _callsRef.doc(callId);

      await _firestore.runTransaction((tx) async {
        tx.update(callRef, {
          AppConstants.fieldStatus: AppConstants.callStatusCancelledByDriver,
          'assignedDriverId': FieldValue.delete(),
          'assignedDriverName': FieldValue.delete(),
          'assignedDriverPhone': FieldValue.delete(),
          'cancelReason': reason,
          'cancelledByDriver': true,
          'updatedAt': FieldValue.serverTimestamp(),
        });
        tx.update(_driverRef, {AppConstants.fieldStatus: DriverStatus.waiting.value});
      });

      state = state.copyWith(
        activeCall: () => null,
        driverStatus: DriverStatus.waiting,
      );

      debugPrint('[Workflow] 운행 취소: $callId');
    } catch (e) {
      debugPrint('[Workflow] 운행 취소 실패: $e');
      state = state.copyWith(errorMessage: () => e.toString());
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 정산 확정 (결제 완료 + COMPLETED)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> confirmAndFinalizeTrip({
    required String callId,
    required String paymentMethod,
    int? cashAmount,
    required int fare,
    String tripSummary = '',
    int pointsToUse = 0,
  }) async {
    try {
      _assertLocationInfo();
      state = state.copyWith(isLoading: true, errorMessage: () => null);

      final tripData = <String, dynamic>{
        'paymentMethod': paymentMethod,
        AppConstants.fieldStatus: AppConstants.callStatusCompleted,
        'fareFinal': fare,
        'fare': fare,
        'trip_summary_final': tripSummary,
        'completedAt': FieldValue.serverTimestamp(),
        'pointsUsed': pointsToUse,
        'finalFare': fare - pointsToUse,
      };

      if ((paymentMethod == '현금' || paymentMethod.startsWith('현금+')) && cashAmount != null) {
        tripData['cashReceived'] = cashAmount;
      }
      if (paymentMethod == '외상' || paymentMethod == '이체') {
        tripData['creditAmount'] = fare;
      }

      final callRef = _callsRef.doc(callId);
      await _firestore.runTransaction((tx) async {
        tx.update(callRef, tripData);
        tx.update(_driverRef, {AppConstants.fieldStatus: DriverStatus.waiting.value});
      });

      // 정산 데이터 로컬 업데이트
      final ratio = _depositRatio;
      final newCash = paymentMethod == '현금' ? fare
          : paymentMethod.startsWith('현금+') ? (cashAmount ?? 0)
          : 0;
      final newOfficeDeposit = (fare * ratio / 100).round();
      final newDriverShare = fare - newOfficeDeposit;

      _todaySettlement = TodaySettlement(
        totalFare: _todaySettlement.totalFare + fare,
        driverShare: _todaySettlement.driverShare + newDriverShare,
        cashReceived: _todaySettlement.cashReceived + newCash,
        realDeposit: (_todaySettlement.cashReceived + newCash) - (_todaySettlement.driverShare + newDriverShare),
        tripCount: _todaySettlement.tripCount + 1,
        totalCredit: (_todaySettlement.totalFare + fare) - (_todaySettlement.cashReceived + newCash) - (_todaySettlement.pointsUsed + pointsToUse),
        officeDeposit: ((_todaySettlement.totalFare + fare) * ratio / 100).round(),
        pointsUsed: _todaySettlement.pointsUsed + pointsToUse,
      );

      // 운행내역 추가
      _tripHistoryList = [
        TripHistoryItem(
          tripNumber: _tripHistoryList.length + 1,
          customerName: state.callForSettlement?.customerName ?? '고객',
          departure: state.callForSettlement?.pickupLocation ?? '출발지',
          destination: state.callForSettlement?.destination ?? '도착지',
          fare: fare,
          paymentMethod: paymentMethod,
          cashAmount: cashAmount,
          timestamp: DateTime.now().millisecondsSinceEpoch,
        ),
        ..._tripHistoryList,
      ];

      state = state.copyWith(
        activeCall: () => null,
        callForSettlement: () => null,
        driverStatus: DriverStatus.waiting,
        navigateToHistorySettlement: true,
        isLoading: false,
      );

      debugPrint('[Workflow] 정산 완료: $callId ($paymentMethod, $fare원)');
    } catch (e) {
      debugPrint('[Workflow] 정산 실패: $e');
      state = state.copyWith(isLoading: false, errorMessage: () => e.toString());
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // FCM 핸들링
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> handleNotificationCallId(String callId) async {
    try {
      _assertLocationInfo();
      final callDoc = await _callsRef.doc(callId).get();
      if (!callDoc.exists) return;

      final call = _docToCall(callDoc);

      if (call.status == CallStatus.assigned) {
        final alreadyExists = state.assignedCalls.any((c) => c.id == call.id);
        state = state.copyWith(
          assignedCalls: alreadyExists
              ? state.assignedCalls
              : [...state.assignedCalls, call],
          newCallPopup: () => state.newCallPopup ?? call,
          navigateToHome: true,
        );
      } else if (call.status.isCancelled) {
        state = state.copyWith(navigateToHome: true);
      }
    } catch (e) {
      debugPrint('[Workflow] FCM 처리 실패: $e');
    }
  }

  void handleCallCancelled(String callId) {
    final clearPopup = state.newCallPopup?.id == callId;
    final clearActive = state.activeCall?.id == callId;

    state = state.copyWith(
      assignedCalls: state.assignedCalls.where((c) => c.id != callId).toList(),
      newCallPopup: () => clearPopup ? null : state.newCallPopup,
      activeCall: () => clearActive ? null : state.activeCall,
      driverStatus: (clearActive || clearPopup) ? DriverStatus.waiting : null,
      errorMessage: () => '고객이 콜을 취소했습니다',
    );
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 앱 포그라운드 복귀 시 활성 콜 상태 확인
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> refreshActiveCallStatus() async {
    final callsToCheck = <String>[];
    if (state.activeCall != null) callsToCheck.add(state.activeCall!.id);
    for (final c in state.assignedCalls) {
      if (c.id != state.activeCall?.id) callsToCheck.add(c.id);
    }
    if (callsToCheck.isEmpty) return;

    try {
      _assertLocationInfo();
      for (final callId in callsToCheck) {
        try {
          final doc = await _callsRef.doc(callId).get();
          final status = doc.get(AppConstants.fieldStatus) as String?;
          if (!doc.exists ||
              status == AppConstants.callStatusCanceled ||
              status == AppConstants.callStatusCancelledByCustomer ||
              status == AppConstants.callStatusWaiting) {
            handleCallCancelled(callId);
          }
        } catch (_) {}
      }
    } catch (_) {}
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 이월금 실시간 리스너
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  void _startCarryOverListener() {
    _carryOverSubscription?.cancel();
    try {
      _assertLocationInfo();
    } catch (_) {
      return;
    }

    _carryOverSubscription = _driverRef.snapshots().listen((snap) {
      if (!snap.exists) return;
      final data = snap.data() as Map<String, dynamic>?;
      if (data == null) return;

      final coMap = data['carryOver'] as Map<String, dynamic>?;
      _carryOver = DriverCarryOver.fromMap(coMap);

      final dsMap = data['dailySettlement'] as Map<String, dynamic>?;
      final dsStatus = dsMap?['status'] as String?;
      _dailySettlementStatus = DailySettlementStatus.fromString(dsStatus);

      if (_dailySettlementStatus == DailySettlementStatus.pendingConfirm) {
        final calcCO = (dsMap?['calculatedCarryOver'] as num?)?.toInt();
        if (calcCO != null) {
          _carryOver = _carryOver?.copyWith(balance: calcCO);
        }
      }
    });
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 정산 데이터 로드
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> _loadSettlementData() async {
    try {
      _assertLocationInfo();

      // 1. 사무실 분배비율
      final officeDoc = await _officeRef.get();
      final officeData = officeDoc.data() as Map<String, dynamic>?;
      final ratio = (officeData?['depositRatio'] as num?)?.toInt() ?? 60;
      _depositRatio = ratio.clamp(30, 90);

      // 2. 기사 마지막 마감 시각
      final driverDoc = await _driverRef.get();
      final driverData = driverDoc.data() as Map<String, dynamic>?;
      final lastCleared = driverData?['settlementLastCleared'];
      if (lastCleared is Timestamp) {
        _lastClearedMillis = lastCleared.toDate().millisecondsSinceEpoch;
      }

      // 3. 오늘 정산
      await _loadTodaySettlement();

      debugPrint('[Workflow] 정산 데이터 로드: ratio=$_depositRatio, lastCleared=$_lastClearedMillis');
    } catch (e) {
      debugPrint('[Workflow] 정산 데이터 로드 실패: $e');
    }
  }

  Future<void> _loadTodaySettlement() async {
    try {
      final snap = await _callsRef
          .where('assignedDriverId', isEqualTo: _driverId)
          .where('status', isEqualTo: AppConstants.callStatusCompleted)
          .get();

      int totalFare = 0, totalCash = 0, totalPoints = 0, tripCount = 0;
      final items = <TripHistoryItem>[];

      for (final doc in snap.docs) {
        final data = doc.data() as Map<String, dynamic>;
        final completedAt = (data['completedAt'] as Timestamp?)?.toDate().millisecondsSinceEpoch
            ?? (data['updatedAt'] as Timestamp?)?.toDate().millisecondsSinceEpoch
            ?? 0;

        if (completedAt <= _lastClearedMillis) continue;

        final fare = (data['fareFinal'] as num?)?.toInt()
            ?? (data['fare_set'] as num?)?.toInt()
            ?? 0;
        final pm = data['paymentMethod'] as String? ?? '';
        final cash = pm == '현금' ? fare
            : pm.startsWith('현금+') ? ((data['cashReceived'] as num?)?.toInt() ?? 0)
            : 0;
        final pts = (data['pointsUsed'] as num?)?.toInt() ?? 0;

        totalFare += fare;
        totalCash += cash;
        totalPoints += pts;
        tripCount++;

        items.add(TripHistoryItem(
          tripNumber: tripCount,
          customerName: data['customerName'] as String? ?? '고객',
          departure: (data['departure_set'] as String?) ?? '출발지',
          destination: (data['destination_set'] as String?) ?? '도착지',
          fare: fare,
          paymentMethod: pm,
          cashAmount: (data['cashReceived'] as num?)?.toInt(),
          timestamp: completedAt,
        ));
      }

      // 시간순 정렬 + 번호 재할당 + 역순
      items.sort((a, b) => a.timestamp.compareTo(b.timestamp));
      _tripHistoryList = items
          .asMap()
          .entries
          .map((e) => e.value.copyWith(tripNumber: e.key + 1))
          .toList()
          .reversed
          .toList();

      final ratio = _depositRatio;
      final officeDeposit = (totalFare * ratio / 100).round();
      final driverShare = totalFare - officeDeposit;
      _todaySettlement = TodaySettlement(
        totalFare: totalFare,
        driverShare: driverShare,
        cashReceived: totalCash,
        realDeposit: totalCash - driverShare,
        tripCount: tripCount,
        totalCredit: totalFare - totalCash - totalPoints,
        officeDeposit: officeDeposit,
        pointsUsed: totalPoints,
      );
    } catch (e) {
      debugPrint('[Workflow] 오늘 정산 로드 실패: $e');
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 일일 정산 제출 (업무마감)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<(bool, String)> submitDailySettlement(int realDeposit) async {
    if (_isSubmittingSettlement) return (false, '이미 제출 중입니다.');
    _isSubmittingSettlement = true;

    try {
      _assertLocationInfo();
      final driverId = _driverId;

      // 기존 정산 확인 (통합 여부)
      final driverDoc = await _driverRef.get();
      final driverData = driverDoc.data() as Map<String, dynamic>?;
      final existingMap = driverData?['dailySettlement'] as Map<String, dynamic>?;
      final prevStatus = DailySettlementStatus.fromString(existingMap?['status'] as String?);
      final isIntegration = prevStatus == DailySettlementStatus.pendingConfirm;

      final settlement = _todaySettlement;
      final ratio = _depositRatio;

      // 이월금
      final originalCarryOverBalance = isIntegration
          ? (existingMap?['originalCarryOver'] as num?)?.toInt() ?? 0
          : _carryOver?.balance ?? 0;

      // 통합 병합
      final prevTripCount = isIntegration ? (existingMap?['tripCount'] as num?)?.toInt() ?? 0 : 0;
      final prevTotalFare = isIntegration ? (existingMap?['totalFare'] as num?)?.toInt() ?? 0 : 0;
      final prevTotalCredit = isIntegration ? (existingMap?['totalCredit'] as num?)?.toInt() ?? 0 : 0;
      final prevRealDeposit = isIntegration ? (existingMap?['realDeposit'] as num?)?.toInt() ?? 0 : 0;

      final mergedTripCount = prevTripCount + settlement.tripCount;
      final mergedTotalFare = prevTotalFare + settlement.totalFare;
      final mergedTotalCredit = prevTotalCredit + settlement.totalCredit;
      final mergedRealDeposit = prevRealDeposit + realDeposit;

      // 원본 보존 (2차 이상 통합 시)
      final origTripCount = isIntegration
          ? ((existingMap?['originalTripCount'] as num?)?.toInt() ?? prevTripCount)
          : 0;
      final origTotalFare = isIntegration
          ? ((existingMap?['originalTotalFare'] as num?)?.toInt() ?? prevTotalFare)
          : 0;
      final origRealDeposit = isIntegration
          ? ((existingMap?['originalRealDeposit'] as num?)?.toInt() ?? prevRealDeposit)
          : 0;

      final mergedOfficeDeposit = (mergedTotalFare * ratio / 100).round();
      final mergedFinalDeposit = mergedOfficeDeposit - mergedTotalCredit;
      final remainingCarryOver = originalCarryOverBalance - mergedFinalDeposit + mergedRealDeposit;

      // 날짜 (6시 이전 = 전날)
      final now = DateTime.now();
      final effectiveDate = now.hour < 6 ? now.subtract(const Duration(days: 1)) : now;
      final today = '${effectiveDate.year}-${effectiveDate.month.toString().padLeft(2, '0')}-${effectiveDate.day.toString().padLeft(2, '0')}';

      final dailySettlement = <String, dynamic>{
        'date': today,
        'finalDeposit': mergedFinalDeposit,
        'realDeposit': mergedRealDeposit,
        'settlementDiff': (mergedRealDeposit - mergedFinalDeposit) + originalCarryOverBalance,
        'totalFare': mergedTotalFare,
        'totalCredit': mergedTotalCredit,
        'tripCount': mergedTripCount,
        'status': DailySettlementStatus.pendingConfirm.value,
        'submittedAt': FieldValue.serverTimestamp(),
        'calculatedCarryOver': remainingCarryOver,
        'originalCarryOver': originalCarryOverBalance,
        'originalTripCount': origTripCount,
        'originalTotalFare': origTotalFare,
        'originalRealDeposit': origRealDeposit,
      };

      await _driverRef.update({
        'dailySettlement': dailySettlement,
        'status': 'PENDING_CONFIRM',
      });

      _dailySettlementStatus = DailySettlementStatus.pendingConfirm;
      state = state.copyWith(driverStatus: DriverStatus.waiting);

      final msg = isIntegration
          ? '정산 완료 (이전 $prevTripCount건 + 추가 ${settlement.tripCount}건 통합)'
          : '정산이 제출되었습니다. 매니저 확인을 기다려주세요.';

      debugPrint('[Workflow] 일일정산 제출: $mergedTripCount건, realDeposit=$mergedRealDeposit');
      return (true, msg);
    } catch (e) {
      debugPrint('[Workflow] 일일정산 제출 실패: $e');
      return (false, '정산 제출 실패: $e');
    } finally {
      _isSubmittingSettlement = false;
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 이월금 수령 확인
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> confirmReceiveCarryOver() async {
    try {
      _assertLocationInfo();
      await _driverRef.update({
        'carryOver.status': 'SETTLED',
        'carryOver.lastUpdatedAt': FieldValue.serverTimestamp(),
      });
      debugPrint('[Workflow] 이월금 수령 확인 완료');
    } catch (e) {
      debugPrint('[Workflow] 이월금 수령 확인 실패: $e');
      state = state.copyWith(errorMessage: () => e.toString());
    }
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 정산 데이터 새로고침 (외부 호출용)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Future<void> refreshSettlementData() async {
    await _loadSettlementData();
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 실납입 수정 (UI에서 호출)
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  void updateRealDeposit(int newRealDeposit) {
    _todaySettlement = _todaySettlement.copyWith(realDeposit: newRealDeposit);
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // UI 헬퍼
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  void clearError() {
    state = state.copyWith(errorMessage: () => null);
  }

  void clearNavigationFlags() {
    state = state.copyWith(
      navigateToHome: false,
      navigateToHistorySettlement: false,
    );
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // Firestore → Call 변환
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Call _docToCall(DocumentSnapshot doc) {
    final d = doc.data() as Map<String, dynamic>;
    return Call(
      id: doc.id,
      provinceId: _provinceId,
      cityId: _cityId,
      officeId: _officeId,
      phoneNumber: d['phoneNumber'] as String? ?? '',
      customerName: d['customerName'] as String?,
      pickupLocation: d['pickupLocation'] as String? ?? d['departure_set'] as String?,
      destination: d['destination'] as String? ?? d['destination_set'] as String?,
      status: CallStatus.fromString(d['status'] as String?),
      assignedDriverId: d['assignedDriverId'] as String?,
      assignedDriverName: d['assignedDriverName'] as String?,
      callTime: (d['callTime'] as Timestamp?)?.toDate(),
      assignedTime: (d['assignedTime'] as Timestamp?)?.toDate(),
      acceptedTime: (d['acceptedTime'] as Timestamp?)?.toDate(),
      startedTime: (d['startedTime'] as Timestamp?)?.toDate(),
      completedTime: (d['completedAt'] as Timestamp?)?.toDate(),
      fare: (d['fare'] as num?)?.toInt() ?? (d['fare_set'] as num?)?.toInt(),
      cashReceived: (d['cashReceived'] as num?)?.toInt(),
      paymentMethod: d['paymentMethod'] as String?,
      pointsUsed: (d['pointsUsed'] as num?)?.toInt(),
      pointsEarned: (d['pointsEarned'] as num?)?.toInt(),
      notes: d['notes'] as String?,
    );
  }

  Call _callWithStatus(Call call, CallStatus status) {
    return Call(
      id: call.id,
      provinceId: call.provinceId,
      cityId: call.cityId,
      officeId: call.officeId,
      phoneNumber: call.phoneNumber,
      customerName: call.customerName,
      pickupLocation: call.pickupLocation,
      destination: call.destination,
      status: status,
      assignedDriverId: call.assignedDriverId,
      assignedDriverName: call.assignedDriverName,
      callTime: call.callTime,
      assignedTime: call.assignedTime,
      acceptedTime: call.acceptedTime,
      startedTime: call.startedTime,
      completedTime: call.completedTime,
      fare: call.fare,
      cashReceived: call.cashReceived,
      paymentMethod: call.paymentMethod,
      pointsUsed: call.pointsUsed,
      pointsEarned: call.pointsEarned,
      notes: call.notes,
    );
  }

  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  // 리소스 정리
  // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  @override
  void dispose() {
    _carryOverSubscription?.cancel();
    super.dispose();
  }
}

// Provider는 core/providers.dart에 정의
