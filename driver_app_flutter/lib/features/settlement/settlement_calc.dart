/// 정산 계산 유틸리티 (Kotlin SettlementCalc 1:1 포팅)
/// 순수 함수 — 외부 의존성 없음
class SettlementCalc {
  SettlementCalc._();

  /// 사무실 몫 = totalFare × ratio / 100
  static int officeDeposit(int totalFare, int ratio) {
    return (totalFare * ratio / 100).round();
  }

  /// 기사 몫 = totalFare - officeDeposit
  static int driverShare(int totalFare, int depositAmount) {
    return totalFare - depositAmount;
  }

  /// 최종 납입액 (이월금 반영 전) = officeDeposit - totalCredit
  static int rawFinalDeposit(int depositAmount, int totalCredit) {
    return depositAmount - totalCredit;
  }

  /// 이월금 반영 조정 납입액
  /// carryOverBalance >= 0 (미수령금): 납입액에서 차감
  /// carryOverBalance < 0 (미납금): 납입액에 추가
  static int adjustedDeposit(int rawDeposit, int carryOverBalance) {
    if (carryOverBalance >= 0) {
      return rawDeposit > 0 ? (rawDeposit - carryOverBalance).clamp(0, rawDeposit) : 0;
    } else {
      return rawDeposit > 0 ? rawDeposit + (-carryOverBalance) : (-carryOverBalance);
    }
  }

  /// 오늘 운행 후 남은 이월금
  static int remainingCarryOver(int rawDeposit, int carryOverBalance) {
    if (carryOverBalance >= 0) {
      if (rawDeposit > 0) {
        return (carryOverBalance - rawDeposit).clamp(0, carryOverBalance);
      } else {
        return carryOverBalance + (-rawDeposit);
      }
    } else {
      if (rawDeposit > 0) {
        return carryOverBalance + rawDeposit;
      } else {
        return carryOverBalance + (-rawDeposit);
      }
    }
  }

  /// 미수령금 공제액 (양수 carryOver + 양수 rawDeposit일 때만)
  static int usedFromCarryOver(int rawDeposit, int carryOverBalance) {
    if (rawDeposit > 0 && carryOverBalance > 0) {
      return carryOverBalance < rawDeposit ? carryOverBalance : rawDeposit;
    }
    return 0;
  }

  /// 금액 포맷 (1000 → "1,000원")
  static String formatAmount(int amount) {
    if (amount == 0) return '0원';
    final negative = amount < 0;
    final abs = amount.abs();
    final str = abs.toString();
    final buf = StringBuffer();
    for (var i = 0; i < str.length; i++) {
      if (i > 0 && (str.length - i) % 3 == 0) buf.write(',');
      buf.write(str[i]);
    }
    return '${negative ? "-" : ""}${buf}원';
  }
}
