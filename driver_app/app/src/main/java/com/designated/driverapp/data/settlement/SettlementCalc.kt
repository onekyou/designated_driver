package com.designated.driverapp.data.settlement

/**
 * 기사앱 정산 계산 순수 함수 모음 (ViewModel/Firestore 의존 없음)
 * HistorySettlementScreen에서 사용하던 계산 로직을 추출
 */
object SettlementCalc {

    /**
     * 사무실 몫 (납입금) = totalFare × ratio / 100
     */
    fun calculateOfficeDeposit(totalFare: Int, ratio: Int): Int {
        return (totalFare * ratio / 100.0).toInt()
    }

    /**
     * 기사 몫 = totalFare - officeDeposit
     */
    fun calculateDriverShare(totalFare: Int, officeDeposit: Int): Int {
        return totalFare - officeDeposit
    }

    /**
     * 최종 납입액 (이월금 반영 전) = officeDeposit - totalCredit
     */
    fun calculateRawFinalDeposit(officeDeposit: Int, totalCredit: Int): Int {
        return officeDeposit - totalCredit
    }

    /**
     * 이월금 반영 조정 납입액
     * - carryOverBalance >= 0 (미수령금): 납입액에서 미수령금 차감
     * - carryOverBalance < 0 (미납금): 납입액에 미납금 추가
     */
    fun calculateAdjustedDeposit(rawFinalDeposit: Int, carryOverBalance: Int): Int {
        return if (carryOverBalance >= 0) {
            // 미수령금(양수): 납입액에서 미수령금 차감
            if (rawFinalDeposit > 0) maxOf(0, rawFinalDeposit - carryOverBalance) else 0
        } else {
            // 미납금(음수): 납입액에 미납금 추가
            if (rawFinalDeposit > 0) rawFinalDeposit + (-carryOverBalance) else (-carryOverBalance)
        }
    }

    /**
     * 오늘 운행 후 남은 이월금
     * - carryOverBalance >= 0 (미수령금):
     *   - rawFinalDeposit > 0: 납입으로 미수령금 상쇄
     *   - rawFinalDeposit <= 0: 미수령금 + 오늘 발생분
     * - carryOverBalance < 0 (미납금):
     *   - rawFinalDeposit > 0: 납입으로 미납금 갚기
     *   - rawFinalDeposit <= 0: 미납금 일부 상쇄
     */
    fun calculateRemainingCarryOver(rawFinalDeposit: Int, carryOverBalance: Int): Int {
        return if (carryOverBalance >= 0) {
            // 미수령금(양수)
            if (rawFinalDeposit > 0) {
                maxOf(0, carryOverBalance - rawFinalDeposit)
            } else {
                carryOverBalance + (-rawFinalDeposit)
            }
        } else {
            // 미납금(음수)
            if (rawFinalDeposit > 0) {
                val netBalance = carryOverBalance + rawFinalDeposit
                netBalance // 음수면 미납 잔여, 양수면 미수령금 발생
            } else {
                carryOverBalance + (-rawFinalDeposit)
            }
        }
    }

    /**
     * 미환급금 공제액 (양수 carryOver + 양수 rawFinalDeposit일 때만)
     */
    fun calculateUsedFromCarryOver(rawFinalDeposit: Int, carryOverBalance: Int): Int {
        return if (rawFinalDeposit > 0 && carryOverBalance > 0) {
            minOf(carryOverBalance, rawFinalDeposit)
        } else {
            0
        }
    }
}
