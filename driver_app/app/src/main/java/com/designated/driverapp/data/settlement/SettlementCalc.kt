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
}
