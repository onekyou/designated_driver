/// 콜 상태
enum CallState {
  requested('REQUESTED', '콜 요청됨'),
  assigned('ASSIGNED', '기사 배정됨'),
  driverArriving('DRIVER_ARRIVING', '기사 이동 중'),
  inProgress('IN_PROGRESS', '운행 중'),
  completed('COMPLETED', '운행 완료'),
  cancelled('CANCELLED', '취소됨');

  const CallState(this.value, this.displayText);

  final String value;
  final String displayText;

  /// 문자열에서 CallState로 변환
  static CallState fromString(String value) {
    return CallState.values.firstWhere(
      (state) => state.value == value,
      orElse: () => CallState.requested,
    );
  }

  /// CallState가 활성 상태인지 확인
  bool get isActive {
    return this == CallState.requested ||
        this == CallState.assigned ||
        this == CallState.driverArriving ||
        this == CallState.inProgress;
  }

  /// CallState가 종료 상태인지 확인
  bool get isTerminated {
    return this == CallState.completed || this == CallState.cancelled;
  }
}
