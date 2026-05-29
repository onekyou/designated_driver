package com.designated.callmanager.ui.settlement

import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.ui.settlement.screen.DriverStat

/**
 * 정산 계산 순수 함수 모음 (ViewModel/Firestore 의존 없음)
 * DriverSummaryScreen, AllTripsScreen에서 사용하던 계산 로직을 추출
 */
object SettlementCalculator {

    /**
     * 결제방법별 외상 금액 계산
     * - 현금: 0
     * - 현금+포인트: fare - cashAmount (포인트 부분이 외상)
     * - 이체/외상/포인트: 전액 외상
     */
    fun calculateCreditForTrip(fare: Int, paymentMethod: String, cashAmount: Int?): Int {
        return when {
            paymentMethod == "현금" -> 0
            paymentMethod == "현금+포인트" -> {
                val cash = cashAmount ?: 0
                if (cash > 0) fare - cash else fare
            }
            else -> fare // 이체, 외상, 포인트는 전액 외상
        }
    }

    /**
     * 콜 목록의 총 외상 합계
     */
    fun calculateTotalCredit(trips: List<SettlementData>): Int {
        return trips.sumOf { trip ->
            calculateCreditForTrip(trip.fare, trip.paymentMethod, trip.cashAmount)
        }
    }

    /**
     * 사무실 수수료 (사무실 몫) = totalFare × ratio / 100
     */
    fun calculateOfficeDeposit(totalFare: Int, ratio: Int): Int {
        return (totalFare * ratio / 100.0).toInt()
    }

    /**
     * 기사별 통계 계산
     * - filteredTrips를 기사 이름으로 그룹핑
     * - 각 기사별 fareSum, totalCredit, deposit, realDeposit 계산
     * - totalFare 내림차순 정렬
     */
    fun calculateDriverStats(trips: List<SettlementData>, ratio: Int): List<DriverStat> {
        return trips.groupBy { it.driverName.ifBlank { "미지정" } }
            .mapValues { (_, list) ->
                val fareSum = list.sumOf { it.fare }
                val totalCredit = list.sumOf { trip ->
                    calculateCreditForTrip(trip.fare, trip.paymentMethod, trip.cashAmount)
                }
                val deposit = calculateOfficeDeposit(fareSum, ratio)
                val realDeposit = deposit - totalCredit
                val driverId = list.first().driverId
                DriverStat(
                    list.first().driverName.ifBlank { "미지정" },
                    list.size, fareSum, deposit, totalCredit, realDeposit, driverId
                )
            }
            .values
            .sortedByDescending { it.totalFare }
    }

    /**
     * AllTripsScreen용: 결제방법별 합계 계산
     */
    data class PaymentBreakdown(
        val totalFare: Int,
        val cashSum: Int,
        val bankSum: Int,
        val creditSum: Int,
        val pointSum: Int
    )

    fun calculatePaymentBreakdown(trips: List<SettlementData>): PaymentBreakdown {
        val totalFare = trips.sumOf { it.fare }
        val cashTrips = trips.filter { it.paymentMethod == "현금" }
        val bankTrips = trips.filter { it.paymentMethod == "이체" }
        val creditTrips = trips.filter { it.paymentMethod == "외상" }
        val cashPlusPointTrips = trips.filter { it.paymentMethod == "현금+포인트" }
        val pointOnlyTrips = trips.filter { it.paymentMethod == "포인트" }

        val cashSum = cashTrips.sumOf { it.fare } +
                cashPlusPointTrips.sumOf { trip -> trip.cashAmount ?: 0 }
        val bankSum = bankTrips.sumOf { it.fare }
        val creditSum = creditTrips.sumOf { if (it.creditAmount > 0) it.creditAmount else it.fare }
        val pointSum = cashPlusPointTrips.sumOf { trip ->
            val cashReceived = trip.cashAmount ?: 0
            trip.fare - cashReceived
        } + pointOnlyTrips.sumOf { it.fare }

        return PaymentBreakdown(totalFare, cashSum, bankSum, creditSum, pointSum)
    }

    /**
     * 기사 납입금 = 현금합계 - 기사몫
     */
    fun calculateDriverDeposit(cashSum: Int, driverShare: Int): Int {
        return cashSum - driverShare
    }

    /**
     * 실수입 = 기사납입 + 이체 + 미수금 - 포인트차감
     */
    fun calculateRealIncome(driverDeposit: Int, bankSum: Int, creditSum: Int, pointSum: Int): Int {
        return driverDeposit + bankSum + creditSum - pointSum
    }
}
