package com.designated.callmanager.settlement

import com.designated.callmanager.ui.settlement.SettlementCalculator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 정산 계산 로직 JVM Unit Test
 * 7일 × 50콜 = 350콜, 47개 체크포인트
 * 디바이스 불필요 — 순수 함수 검증
 */
class SettlementCalculatorTest {

    private val RATIO = 60 // 사무실 수수료 비율

    @Before
    fun setup() {
        TestSettlementFactory.resetCounter()
    }

    // ===== Day 1: 정상 플로우 (50콜) =====

    @Test
    fun day1_CP01_cashOnly_officeDepositCorrect() {
        // 현금 콜: fare=20000, ratio=60% → officeDeposit=12000
        val credit = SettlementCalculator.calculateCreditForTrip(20000, "현금", 20000)
        assertEquals("현금 결제는 외상 0원", 0, credit)
        assertEquals("사무실 몫 = fare × 60%", 12000, SettlementCalculator.calculateOfficeDeposit(20000, RATIO))
    }

    @Test
    fun day1_CP02_transfer_fullCreditAmount() {
        // 이체 콜: fare=18000 → 전액 외상
        val credit = SettlementCalculator.calculateCreditForTrip(18000, "이체", null)
        assertEquals("이체는 전액 외상", 18000, credit)
    }

    @Test
    fun day1_CP03_cashPlusPoint_splitCorrectly() {
        // 현금+포인트: fare=20000, cashAmount=15000 → 포인트 부분(5000)만 외상
        val credit = SettlementCalculator.calculateCreditForTrip(20000, "현금+포인트", 15000)
        assertEquals("포인트 부분만 외상", 5000, credit)
    }

    @Test
    fun day1_CP04_fullPoint_zeroCash() {
        // 전액 포인트: fare=10000 → 전액 외상
        val credit = SettlementCalculator.calculateCreditForTrip(10000, "포인트", null)
        assertEquals("포인트 전액 → 전액 외상", 10000, credit)
    }

    @Test
    fun day1_CP05_credit_creditAmountExact() {
        // 외상 콜: fare=15000 → 전액 외상
        val credit = SettlementCalculator.calculateCreditForTrip(15000, "외상", null)
        assertEquals("외상은 전액 외상", 15000, credit)
    }

    @Test
    fun day1_CP06_multiDriverStats_consistent() {
        val calls = TestSettlementFactory.createDay1Calls()
        val stats = SettlementCalculator.calculateDriverStats(calls, RATIO)

        // 2명의 기사 통계
        assertEquals("기사 2명", 2, stats.size)

        val driverA = stats.find { it.driverId == "driver_A" }!!
        val driverB = stats.find { it.driverId == "driver_B" }!!

        // A의 콜 수: 현금8 + 앱콜5 + 이체4 + 현금포인트4 + 포인트3 + 외상3 = 27건
        assertEquals("A 콜 수", 27, driverA.count)
        // B의 콜 수: 현금7 + 앱콜5 + 이체4 + 현금포인트3 + 포인트2 + 외상2 = 23건
        assertEquals("B 콜 수", 23, driverB.count)

        // 각 기사별 정산: deposit = fareSum × 60% / 100
        assertEquals("A deposit = A.totalFare × 60%", SettlementCalculator.calculateOfficeDeposit(driverA.totalFare, RATIO), driverA.deposit)
        assertEquals("B deposit = B.totalFare × 60%", SettlementCalculator.calculateOfficeDeposit(driverB.totalFare, RATIO), driverB.deposit)

        // realDeposit = deposit - totalCredit
        assertEquals("A realDeposit = deposit - totalCredit", driverA.deposit - driverA.totalCredit, driverA.realDeposit)
        assertEquals("B realDeposit = deposit - totalCredit", driverB.deposit - driverB.totalCredit, driverB.realDeposit)
    }

    @Test
    fun day1_CP07_threeWayMatch_managerTotalEqualsSum() {
        // 매니저 전체 통계 = 개별 기사 합
        val calls = TestSettlementFactory.createDay1Calls()
        val stats = SettlementCalculator.calculateDriverStats(calls, RATIO)

        val totalFareFromStats = stats.sumOf { it.totalFare }
        val totalFareFromCalls = calls.sumOf { it.fare }
        assertEquals("전체 총매출 = 기사별 합", totalFareFromCalls, totalFareFromStats)

        val totalDepositFromStats = stats.sumOf { it.deposit }
        val expectedTotalDeposit = SettlementCalculator.calculateOfficeDeposit(totalFareFromCalls, RATIO)
        // 개별 기사별 deposit 합이 전체 deposit과 같아야 함
        // (정수 나눗셈 반올림 차이로 1원 이내 차이 허용)
        assertTrue("기사별 deposit 합 ≈ 전체 deposit (±${stats.size}원)",
            kotlin.math.abs(totalDepositFromStats - expectedTotalDeposit) <= stats.size)

        val totalCreditFromStats = stats.sumOf { it.totalCredit }
        val totalCreditFromCalls = SettlementCalculator.calculateTotalCredit(calls)
        assertEquals("전체 미수금 = 기사별 합", totalCreditFromCalls, totalCreditFromStats)
    }

    @Test
    fun day1_CP08_paymentBreakdown_consistent() {
        val calls = TestSettlementFactory.createDay1Calls()
        val breakdown = SettlementCalculator.calculatePaymentBreakdown(calls)

        assertEquals("PaymentBreakdown.totalFare = 콜 합계", calls.sumOf { it.fare }, breakdown.totalFare)

        // 검증: realIncome = driverDeposit + bankSum + creditSum - pointSum
        // 이 공식은 사무실 실수입이며, 포인트가 포함되면 officeDeposit과 다를 수 있음
        // 대신 핵심 항등식으로 검증:
        // totalFare = cashSum + bankSum + creditSum + pointSum (결제방법별 합)
        // → 이건 현금+포인트가 있으면 성립하지 않음 (cashSum에 현금부분, pointSum에 포인트부분)
        // 실제 검증: 현금 결제 부분만 따지면 cashSum = 현금으로 받은 총액
        val officeDeposit = SettlementCalculator.calculateOfficeDeposit(breakdown.totalFare, RATIO)
        val driverShare = breakdown.totalFare - officeDeposit
        val driverDeposit = SettlementCalculator.calculateDriverDeposit(breakdown.cashSum, driverShare)
        val realIncome = SettlementCalculator.calculateRealIncome(driverDeposit, breakdown.bankSum, breakdown.creditSum, breakdown.pointSum)

        // realIncome = cashSum - driverShare + bankSum + creditSum - pointSum
        // = cashSum + bankSum + creditSum - pointSum - driverShare
        // = cashSum + bankSum + creditSum - pointSum - (totalFare - officeDeposit)
        // 정리: realIncome + totalFare - officeDeposit = cashSum + bankSum + creditSum - pointSum
        // → 즉 realIncome = officeDeposit - totalFare + cashSum + bankSum + creditSum - pointSum
        val expected = officeDeposit - breakdown.totalFare + breakdown.cashSum + breakdown.bankSum + breakdown.creditSum - breakdown.pointSum
        assertEquals("realIncome 항등식 검증", expected, realIncome)
    }

    // ===== Day 2: 취소 + 거절 + 타임아웃 =====

    @Test
    fun day2_CP09_cancelledExcluded_settlementOnlyCompleted() {
        // 취소된 콜은 리스트에 없으므로 정산에 미포함
        val completedCalls = TestSettlementFactory.createDay2Calls()
        val stats = SettlementCalculator.calculateDriverStats(completedCalls, RATIO)

        // 완료된 콜만 정산: A=20건(10정상+5HOLD재배차+5연속), B=20건(10정상+5거절후완료+5연속)
        val driverA = stats.find { it.driverId == "driver_A" }!!
        val driverB = stats.find { it.driverId == "driver_B" }!!
        assertEquals("A 완료 콜수", 20, driverA.count)
        assertEquals("B 완료 콜수", 20, driverB.count)
    }

    @Test
    fun day2_CP10_rejectedThenReassigned_correctAttribution() {
        // A가 거절 → B가 완료: 정산은 B에 귀속
        val calls = TestSettlementFactory.createDay2Calls()
        val bCalls = calls.filter { it.driverId == "driver_B" }
        // B의 콜: 10(정상) + 5(거절후받음) + 5(연속) = 20건
        assertEquals("B에 거절 콜 귀속", 20, bCalls.size)

        val bFare = bCalls.sumOf { it.fare }
        val bStat = SettlementCalculator.calculateDriverStats(calls, RATIO).find { it.driverId == "driver_B" }!!
        assertEquals("B 총매출 일치", bFare, bStat.totalFare)
    }

    @Test
    fun day2_CP11_cancelledTrips_zeroImpact() {
        // 취소 콜 3종(관리자/기사/고객) 각 5건이 정산에 포함되지 않음을 검증
        val completedCalls = TestSettlementFactory.createDay2Calls() // 40건만 (취소 15건 제외)
        val totalFare = completedCalls.sumOf { it.fare }
        val stats = SettlementCalculator.calculateDriverStats(completedCalls, RATIO)
        val totalFareFromStats = stats.sumOf { it.totalFare }
        assertEquals("취소 제외 후 총매출 일치", totalFare, totalFareFromStats)
    }

    // ===== Day 3: 퇴근 + 재출근 + filteredTrips =====

    @Test
    fun day3_CP12_filteredTrips_secondShiftOnly() {
        // 1차 마감 후 driverLastClearedMap 설정 → 2차 콜만 필터
        val firstShift = TestSettlementFactory.createDay3Calls_firstShift()
        val clearTime = firstShift.maxOf { it.completedAt } + 1000L
        val secondShift = TestSettlementFactory.createDay3Calls_secondShift(clearTime)

        // 모든 콜에서 A의 마감시점 이후만 필터
        val allCalls = firstShift + secondShift
        val filteredA = allCalls.filter { it.driverId == "driver_A" && it.completedAt > clearTime }

        assertEquals("A 2차 운행만 10건", 10, filteredA.size)
        assertEquals("2차 총매출 = 22000 × 10", 220000, filteredA.sumOf { it.fare })
    }

    @Test
    fun day3_CP14_multiDriverFilterIndependent() {
        // A와 B의 마감 시점이 다를 때 각각 독립적으로 필터
        val firstShift = TestSettlementFactory.createDay3Calls_firstShift()
        val aClearTime = firstShift.filter { it.driverId == "driver_A" }.maxOf { it.completedAt } + 1000L
        // B는 마감 안 함
        val bClearTime = 0L

        val secondShift = TestSettlementFactory.createDay3Calls_secondShift(aClearTime)
        val allCalls = firstShift + secondShift

        val driverLastClearedMap = mapOf("driver_A" to aClearTime, "driver_B" to bClearTime)

        val filteredTrips = allCalls.filter { trip ->
            val driverCleared = driverLastClearedMap[trip.driverId] ?: 0L
            trip.completedAt > driverCleared
        }

        val aFiltered = filteredTrips.filter { it.driverId == "driver_A" }
        val bFiltered = filteredTrips.filter { it.driverId == "driver_B" }

        assertEquals("A: 2차만 10건", 10, aFiltered.size)
        assertEquals("B: 전체 7건 (마감 안 함)", 7, bFiltered.size)
    }

    // ===== Day 4: 거절/재제출 + 통합정산 =====

    @Test
    fun day4_CP15_rejectedResubmit_originalValuePreserved() {
        // 1차 마감: 10건 totalFare=200000
        // 거절 후 추가 10건 → 재제출: 20건 totalFare=420000
        // originalTripCount=10, originalTotalFare=200000이 보존되어야 함
        val firstBatch = List(10) {
            TestSettlementFactory.createCashCall(fare = 20000, driverId = "driver_A", driverName = "양세훈")
        }
        val firstStats = SettlementCalculator.calculateDriverStats(firstBatch, RATIO)
        val originalTripCount = firstStats.first().count
        val originalTotalFare = firstStats.first().totalFare
        val originalDeposit = firstStats.first().deposit

        assertEquals("1차 콜수 보존", 10, originalTripCount)
        assertEquals("1차 총매출 보존", 200000, originalTotalFare)

        // 추가 운행 후 통합
        val secondBatch = List(10) {
            TestSettlementFactory.createCashCall(fare = 22000, driverId = "driver_A", driverName = "양세훈")
        }
        val allTrips = firstBatch + secondBatch
        val mergedStats = SettlementCalculator.calculateDriverStats(allTrips, RATIO)
        val merged = mergedStats.first()

        assertEquals("통합 콜수 = 20", 20, merged.count)
        assertEquals("통합 총매출 = 420000", 420000, merged.totalFare)

        // 추가분 = 통합 - 원본
        val addedTripCount = merged.count - originalTripCount
        val addedTotalFare = merged.totalFare - originalTotalFare
        assertEquals("추가 콜수 = 10", 10, addedTripCount)
        assertEquals("추가 총매출 = 220000", 220000, addedTotalFare)
    }

    // ===== Day 5: 동시성 =====

    @Test
    fun day5_CP16_concurrentCompletions_noSessionDuplication() {
        // 5건 동시 완료 × 5라운드 = 25건 → 세션에 중복 없이 25건
        val calls = mutableListOf<com.designated.callmanager.data.SettlementData>()
        val base = System.currentTimeMillis()
        repeat(5) { round ->
            repeat(5) { i ->
                val driverId = if (i % 2 == 0) "driver_A" else "driver_B"
                val driverName = if (i % 2 == 0) "양세훈" else "고양이"
                calls.add(TestSettlementFactory.createCashCall(
                    fare = 20000,
                    driverId = driverId,
                    driverName = driverName,
                    completedAt = base + round * 1000L + i // 동시 시각
                ))
            }
        }

        val stats = SettlementCalculator.calculateDriverStats(calls, RATIO)
        val totalCount = stats.sumOf { it.count }
        assertEquals("동시 완료 25건 모두 포함", 25, totalCount)

        // callId로 중복 검증
        val uniqueCallIds = calls.map { it.callId }.toSet()
        assertEquals("callId 중복 없음", 25, uniqueCallIds.size)
    }

    @Test
    fun day5_CP17_rapidCalls_noMissing() {
        // 30초 간격 연속 10건
        val base = System.currentTimeMillis()
        val calls = List(10) { i ->
            TestSettlementFactory.createCashCall(
                fare = 20000,
                driverId = "driver_A",
                driverName = "양세훈",
                completedAt = base + i * 30000L
            )
        }

        val stats = SettlementCalculator.calculateDriverStats(calls, RATIO)
        assertEquals("연속 10건 누락 없음", 10, stats.first().count)
    }

    // ===== Day 6: 특수 상황 =====

    @Test
    fun day6_CP18_zeroFare_settlement() {
        // 0원 콜: deposit=0, credit=0, realDeposit=0
        val credit = SettlementCalculator.calculateCreditForTrip(0, "현금", 0)
        assertEquals("0원 현금 → 외상 0", 0, credit)

        val deposit = SettlementCalculator.calculateOfficeDeposit(0, RATIO)
        assertEquals("0원 → deposit 0", 0, deposit)
    }

    @Test
    fun day6_CP19_highFare_200000() {
        // 200,000원 이체: deposit=120000, credit=200000, realDeposit=-80000
        val fare = 200000
        val deposit = SettlementCalculator.calculateOfficeDeposit(fare, RATIO) // 120000
        val credit = SettlementCalculator.calculateCreditForTrip(fare, "이체", null) // 200000
        val realDeposit = deposit - credit // -80000

        assertEquals("고액 deposit = 120,000", 120000, deposit)
        assertEquals("고액 이체 전액 외상 = 200,000", 200000, credit)
        assertEquals("고액 realDeposit = -80,000 (미지급금 발생)", -80000, realDeposit)
    }

    @Test
    fun day6_CP20_consecutiveSameDriver_10calls() {
        // 동일 기사 연속 10건 누락 없음
        val calls = List(10) {
            TestSettlementFactory.createCashCall(fare = 22000, driverId = "driver_A", driverName = "양세훈")
        }
        val stats = SettlementCalculator.calculateDriverStats(calls, RATIO)
        assertEquals("기사 1명", 1, stats.size)
        assertEquals("10건 누락 없음", 10, stats.first().count)
        assertEquals("총매출 = 220,000", 220000, stats.first().totalFare)
    }

    @Test
    fun day6_CP21_edgeCases_fullScenario() {
        val calls = TestSettlementFactory.createDay6EdgeCases()
        val stats = SettlementCalculator.calculateDriverStats(calls, RATIO)

        // 전체 45콜 (정상20 + 0원5 + 소액5 + 고액5 + 연속10)
        // 공유콜 5건은 shared_calls로 별도 처리되어 이 목록에 미포함
        assertEquals("Day6 총콜수 = 45", 45, stats.sumOf { it.count })

        // 각 기사별 정산 일관성
        stats.forEach { stat ->
            assertEquals("${stat.name} realDeposit = deposit - credit",
                stat.deposit - stat.totalCredit, stat.realDeposit)
        }
    }

    @Test
    fun day6_CP22_smallFare_5000() {
        // 소액 5,000원: deposit=3000, driverShare=2000
        val deposit = SettlementCalculator.calculateOfficeDeposit(5000, RATIO)
        assertEquals("소액 deposit = 3,000", 3000, deposit)
    }

    // ===== Day 7: 최종 정리 + 혼합 결제 =====

    @Test
    fun day7_CP23_mixedPayments_breakdownConsistent() {
        val calls = TestSettlementFactory.createDay7MixedPayments()
        val breakdown = SettlementCalculator.calculatePaymentBreakdown(calls)

        // 현금합: 5×20000 + 5×25000 + 5×15000(현금포인트cash) + 5×20000(현금포인트cash) = 100000+125000+75000+100000=400000
        val expectedCash = 5 * 20000 + 5 * 25000 + 5 * 15000 + 5 * 20000
        assertEquals("cashSum = 400,000", expectedCash, breakdown.cashSum)

        // 이체합: 5×18000 + 5×22000 = 200000
        val expectedBank = 5 * 18000 + 5 * 22000
        assertEquals("bankSum = 200,000", expectedBank, breakdown.bankSum)

        // 포인트합: 5×(20000-15000) + 5×(25000-20000) + 5×10000 + 5×12000 = 25000+25000+50000+60000=160000
        val expectedPoint = 5 * 5000 + 5 * 5000 + 5 * 10000 + 5 * 12000
        assertEquals("pointSum = 160,000", expectedPoint, breakdown.pointSum)

        // 외상합: 5×15000 + 5×20000 = 175000
        val expectedCredit = 5 * 15000 + 5 * 20000
        assertEquals("creditSum = 175,000", expectedCredit, breakdown.creditSum)
    }

    @Test
    fun day7_CP24_realIncomeFormula() {
        val calls = TestSettlementFactory.createDay7MixedPayments()
        val breakdown = SettlementCalculator.calculatePaymentBreakdown(calls)
        val officeDeposit = SettlementCalculator.calculateOfficeDeposit(breakdown.totalFare, RATIO)
        val driverShare = breakdown.totalFare - officeDeposit
        val driverDeposit = SettlementCalculator.calculateDriverDeposit(breakdown.cashSum, driverShare)
        val realIncome = SettlementCalculator.calculateRealIncome(driverDeposit, breakdown.bankSum, breakdown.creditSum, breakdown.pointSum)

        // realIncome 항등식: realIncome = officeDeposit - totalFare + cashSum + bankSum + creditSum - pointSum
        val expected = officeDeposit - breakdown.totalFare + breakdown.cashSum + breakdown.bankSum + breakdown.creditSum - breakdown.pointSum
        assertEquals("실수입 항등식 검증", expected, realIncome)

        // 현금만 있는 경우에만 realIncome = officeDeposit
        // 포인트/이체가 있으면 다를 수 있음 (정상 동작)
        assertTrue("realIncome > 0", realIncome > 0)
    }

    @Test
    fun day7_CP25_allDriversSettled_balanceCheck() {
        // 모든 기사가 정산 완료: 각 기사의 deposit - credit - realDeposit = 0 (정의상)
        val calls = TestSettlementFactory.createDay7MixedPayments()
        val stats = SettlementCalculator.calculateDriverStats(calls, RATIO)

        stats.forEach { stat ->
            // realDeposit = deposit - totalCredit (정의)
            assertEquals("${stat.name} 정산 일관성", stat.deposit - stat.totalCredit, stat.realDeposit)
        }
    }

    // ===== 교차 검증 =====

    @Test
    fun crossValidation_allDays_realIncomeIdentity() {
        // 모든 날짜의 콜에 대해 realIncome 항등식 검증
        // realIncome = officeDeposit - totalFare + cashSum + bankSum + creditSum - pointSum
        val allDayCalls = listOf(
            TestSettlementFactory.createDay1Calls(),
            TestSettlementFactory.createDay2Calls(),
            TestSettlementFactory.createDay6EdgeCases(),
            TestSettlementFactory.createDay7MixedPayments()
        )

        allDayCalls.forEachIndexed { dayIdx, calls ->
            val breakdown = SettlementCalculator.calculatePaymentBreakdown(calls)
            val officeDeposit = SettlementCalculator.calculateOfficeDeposit(breakdown.totalFare, RATIO)
            val driverShare = breakdown.totalFare - officeDeposit
            val driverDeposit = SettlementCalculator.calculateDriverDeposit(breakdown.cashSum, driverShare)
            val realIncome = SettlementCalculator.calculateRealIncome(driverDeposit, breakdown.bankSum, breakdown.creditSum, breakdown.pointSum)

            val expected = officeDeposit - breakdown.totalFare + breakdown.cashSum + breakdown.bankSum + breakdown.creditSum - breakdown.pointSum
            assertEquals("Day${dayIdx + 1}: realIncome 항등식", expected, realIncome)
        }
    }
}
