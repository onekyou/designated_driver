/// 손님 UI 관점 콜 상태 6종.
///
/// Firestore 원본 `calls/{callId}.status`는 기사앱 관점 11종(CallStatus)이나,
/// 손님 UI는 6종으로 축소 매핑 (ENUMS.md §1).
sealed class CallState {
  const CallState();

  const factory CallState.requested() = _Requested;
  const factory CallState.assigned() = _Assigned;
  const factory CallState.driverArriving() = _DriverArriving;
  const factory CallState.inProgress() = _InProgress;
  const factory CallState.completed() = _Completed;
  const factory CallState.cancelled() = _Cancelled;

  /// Firestore 원본 status → 손님 UI CallState 매핑.
  ///
  /// 12종 Firestore 상태(WAITING/SHARED_WAITING/ASSIGNED/RESERVED/ACCEPTED/PREPARING/
  /// IN_PROGRESS/AWAITING_SETTLEMENT/COMPLETED/CANCELED/CANCELLED_BY_DRIVER/
  /// CANCELLED_BY_CUSTOMER/HOLD/CLAIMED) → 6종 UI 상태.
  /// RESERVED는 운영 디테일이라 손님 측은 WAITING과 동일하게 "배차 요청 중"으로 표시.
  /// 미매칭은 안전 기본값 `cancelled`.
  static CallState fromFirestoreStatus(String firestoreStatus) =>
      switch (firestoreStatus) {
        'WAITING' || 'SHARED_WAITING' || 'RESERVED' => const CallState.requested(),
        'ASSIGNED' => const CallState.assigned(),
        'ACCEPTED' || 'PREPARING' => const CallState.driverArriving(),
        'IN_PROGRESS' => const CallState.inProgress(),
        'AWAITING_SETTLEMENT' || 'COMPLETED' => const CallState.completed(),
        'CANCELED' ||
        'CANCELLED_BY_DRIVER' ||
        'CANCELLED_BY_CUSTOMER' ||
        'HOLD' ||
        'CLAIMED' =>
          const CallState.cancelled(),
        _ => const CallState.cancelled(),
      };

  /// UI 표시 한글.
  String get displayName => switch (this) {
        _Requested() => '배차 요청 중',
        _Assigned() => '기사 배차됨',
        _DriverArriving() => '기사 오는 중',
        _InProgress() => '운행 중',
        _Completed() => '완료',
        _Cancelled() => '취소됨',
      };

  /// 취소 가능 여부. REQUESTED/ASSIGNED/DRIVER_ARRIVING까지 허용
  /// (CLAUDE.md 2026-03-24 결정: ACCEPTED/PREPARING 단계에서도 고객 취소 허용).
  bool get isCancellable => switch (this) {
        _Requested() || _Assigned() || _DriverArriving() => true,
        _ => false,
      };

  /// 최종 상태 여부 (UI 플로우 종료).
  bool get isFinal => switch (this) {
        _Completed() || _Cancelled() => true,
        _ => false,
      };
}

class _Requested extends CallState {
  const _Requested();
}

class _Assigned extends CallState {
  const _Assigned();
}

class _DriverArriving extends CallState {
  const _DriverArriving();
}

class _InProgress extends CallState {
  const _InProgress();
}

class _Completed extends CallState {
  const _Completed();
}

class _Cancelled extends CallState {
  const _Cancelled();
}
