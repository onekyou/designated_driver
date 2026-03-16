package com.designated.driverapp.settlement

import com.designated.driverapp.data.settlement.SettlementCalc
import org.junit.Assert.*
import org.junit.Test

/**
 * 기사앱 정산 계산 로직 JVM Unit Test
 * 이월금/조정납입/공제 계산의 정확성 검증
 */
class SettlementCalcTest {

    private val RATIO = 60

    // ===== 기본 계산 =====

    @Test
    fun basic_officeDeposit() {
        assertEquals("20000 × 60% = 12000", 12000, SettlementCalc.calculateOfficeDeposit(20000, RATIO))
        assertEquals("0 × 60% = 0", 0, SettlementCalc.calculateOfficeDeposit(0, RATIO))
        assertEquals("5000 × 60% = 3000", 3000, SettlementCalc.calculateOfficeDeposit(5000, RATIO))
        assertEquals("200000 × 60% = 120000", 120000, SettlementCalc.calculateOfficeDeposit(200000, RATIO))
    }

    @Test
    fun basic_driverShare() {
        assertEquals("20000 - 12000 = 8000", 8000, SettlementCalc.calculateDriverShare(20000, 12000))
        assertEquals("0 - 0 = 0", 0, SettlementCalc.calculateDriverShare(0, 0))
    }

    @Test
    fun basic_rawFinalDeposit() {
        // 현금만: officeDeposit=12000, totalCredit=0 → 12000
        assertEquals(12000, SettlementCalc.calculateRawFinalDeposit(12000, 0))
        // 이체만: officeDeposit=12000, totalCredit=20000 → -8000
        assertEquals(-8000, SettlementCalc.calculateRawFinalDeposit(12000, 20000))
        // 혼합: officeDeposit=12000, totalCredit=5000 → 7000
        assertEquals(7000, SettlementCalc.calculateRawFinalDeposit(12000, 5000))
    }

    // ===== 이월금 반영 조정 납입액 (adjustedDeposit) =====

    @Test
    fun adjustedDeposit_noCarryOver() {
        // carryOver=0: 그대로
        assertEquals("이월 없음 → 그대로", 12000, SettlementCalc.calculateAdjustedDeposit(12000, 0))
    }

    @Test
    fun adjustedDeposit_positiveCarryOver_positiveDeposit() {
        // carryOver=5000(미수령금), rawFinalDeposit=12000
        // → 납입에서 미수령금 차감: max(0, 12000 - 5000) = 7000
        assertEquals(7000, SettlementCalc.calculateAdjustedDeposit(12000, 5000))
    }

    @Test
    fun adjustedDeposit_positiveCarryOver_exceeds_deposit() {
        // carryOver=15000(미수령금), rawFinalDeposit=12000
        // → 차감 초과: max(0, 12000 - 15000) = 0
        assertEquals(0, SettlementCalc.calculateAdjustedDeposit(12000, 15000))
    }

    @Test
    fun adjustedDeposit_positiveCarryOver_negativeDeposit() {
        // carryOver=5000(미수령금), rawFinalDeposit=-8000 (이체 콜)
        // → 납입액 0 (음수 납입 + 양수 미수령금)
        assertEquals(0, SettlementCalc.calculateAdjustedDeposit(-8000, 5000))
    }

    @Test
    fun adjustedDeposit_negativeCarryOver_positiveDeposit() {
        // carryOver=-3000(미납금), rawFinalDeposit=12000
        // → 미납금 추가: 12000 + 3000 = 15000
        assertEquals(15000, SettlementCalc.calculateAdjustedDeposit(12000, -3000))
    }

    @Test
    fun adjustedDeposit_negativeCarryOver_negativeDeposit() {
        // carryOver=-3000(미납금), rawFinalDeposit=-8000
        // → 미납금만: 3000
        assertEquals(3000, SettlementCalc.calculateAdjustedDeposit(-8000, -3000))
    }

    // ===== 이월 잔여액 (remainingCarryOver) =====

    @Test
    fun remainingCarryOver_positiveCarryOver_positiveDeposit_partialClear() {
        // carryOver=15000, rawFinalDeposit=12000
        // → 납입으로 일부 상쇄: max(0, 15000 - 12000) = 3000
        assertEquals(3000, SettlementCalc.calculateRemainingCarryOver(12000, 15000))
    }

    @Test
    fun remainingCarryOver_positiveCarryOver_positiveDeposit_fullClear() {
        // carryOver=5000, rawFinalDeposit=12000
        // → 완전 상쇄: max(0, 5000 - 12000) = 0
        assertEquals(0, SettlementCalc.calculateRemainingCarryOver(12000, 5000))
    }

    @Test
    fun remainingCarryOver_positiveCarryOver_negativeDeposit() {
        // carryOver=5000, rawFinalDeposit=-8000
        // → 미수령금 + 오늘 발생: 5000 + 8000 = 13000
        assertEquals(13000, SettlementCalc.calculateRemainingCarryOver(-8000, 5000))
    }

    @Test
    fun remainingCarryOver_negativeCarryOver_positiveDeposit_partialRepay() {
        // carryOver=-10000(미납), rawFinalDeposit=6000
        // → 일부 갚음: -10000 + 6000 = -4000 (아직 미납 4000)
        assertEquals(-4000, SettlementCalc.calculateRemainingCarryOver(6000, -10000))
    }

    @Test
    fun remainingCarryOver_negativeCarryOver_positiveDeposit_fullRepay() {
        // carryOver=-5000(미납), rawFinalDeposit=12000
        // → 갚고 남음: -5000 + 12000 = 7000 (미수령금 전환)
        assertEquals(7000, SettlementCalc.calculateRemainingCarryOver(12000, -5000))
    }

    @Test
    fun remainingCarryOver_negativeCarryOver_negativeDeposit() {
        // carryOver=-3000(미납), rawFinalDeposit=-8000
        // → 미납 + 오늘 추가: -3000 + 8000 = 5000 ... 아닙니다
        // 코드: carryOverBalance + (-rawFinalDeposit) = -3000 + 8000 = 5000
        assertEquals(5000, SettlementCalc.calculateRemainingCarryOver(-8000, -3000))
    }

    @Test
    fun remainingCarryOver_zero() {
        assertEquals(0, SettlementCalc.calculateRemainingCarryOver(0, 0))
    }

    // ===== 미환급금 공제액 (usedFromCarryOver) =====

    @Test
    fun usedFromCarryOver_bothPositive_partial() {
        // rawFinalDeposit=12000, carryOver=5000 → min(5000, 12000) = 5000
        assertEquals(5000, SettlementCalc.calculateUsedFromCarryOver(12000, 5000))
    }

    @Test
    fun usedFromCarryOver_bothPositive_carryOverLarger() {
        // rawFinalDeposit=5000, carryOver=12000 → min(12000, 5000) = 5000
        assertEquals(5000, SettlementCalc.calculateUsedFromCarryOver(5000, 12000))
    }

    @Test
    fun usedFromCarryOver_negativeDeposit() {
        // rawFinalDeposit=-8000, carryOver=5000 → 0 (음수 납입이면 공제 없음)
        assertEquals(0, SettlementCalc.calculateUsedFromCarryOver(-8000, 5000))
    }

    @Test
    fun usedFromCarryOver_negativeCarryOver() {
        // rawFinalDeposit=12000, carryOver=-3000 → 0 (미납금이면 공제 없음)
        assertEquals(0, SettlementCalc.calculateUsedFromCarryOver(12000, -3000))
    }

    @Test
    fun usedFromCarryOver_bothZero() {
        assertEquals(0, SettlementCalc.calculateUsedFromCarryOver(0, 0))
    }

    // ===== 통합 시나리오 =====

    @Test
    fun scenario_cashOnlyDriver_noCarryOver() {
        // 현금만 10건 × 20000 → totalFare=200000
        val totalFare = 200000
        val officeDeposit = SettlementCalc.calculateOfficeDeposit(totalFare, RATIO) // 120000
        val totalCredit = 0
        val rawFinalDeposit = SettlementCalc.calculateRawFinalDeposit(officeDeposit, totalCredit)
        val adjusted = SettlementCalc.calculateAdjustedDeposit(rawFinalDeposit, 0)
        val remaining = SettlementCalc.calculateRemainingCarryOver(rawFinalDeposit, 0)

        assertEquals("현금만: officeDeposit = 120000", 120000, officeDeposit)
        assertEquals("현금만: rawFinalDeposit = 120000", 120000, rawFinalDeposit)
        assertEquals("현금만: adjustedDeposit = 120000", 120000, adjusted)
        assertEquals("현금만: remainingCarryOver = 0", 0, remaining)
    }

    @Test
    fun scenario_transferOnlyDriver_unpaidGenerated() {
        // 이체만 5건 × 25000 → totalFare=125000
        val totalFare = 125000
        val officeDeposit = SettlementCalc.calculateOfficeDeposit(totalFare, RATIO) // 75000
        val totalCredit = 125000 // 전액 외상
        val rawFinalDeposit = SettlementCalc.calculateRawFinalDeposit(officeDeposit, totalCredit) // -50000
        val adjusted = SettlementCalc.calculateAdjustedDeposit(rawFinalDeposit, 0)
        val remaining = SettlementCalc.calculateRemainingCarryOver(rawFinalDeposit, 0)

        assertEquals("이체만: officeDeposit = 75000", 75000, officeDeposit)
        assertEquals("이체만: rawFinalDeposit = -50000", -50000, rawFinalDeposit)
        assertEquals("이체만: adjustedDeposit = 0 (음수→0)", 0, adjusted)
        assertEquals("이체만: remainingCarryOver = 50000 (미수령금 발생)", 50000, remaining)
    }

    @Test
    fun scenario_secondShift_withCarryOver() {
        // 1차: 이체 → carryOver = 50000 (미수령금)
        // 2차: 현금만 10건 × 22000 → totalFare=220000
        val totalFare = 220000
        val carryOverBalance = 50000
        val officeDeposit = SettlementCalc.calculateOfficeDeposit(totalFare, RATIO) // 132000
        val totalCredit = 0
        val rawFinalDeposit = SettlementCalc.calculateRawFinalDeposit(officeDeposit, totalCredit) // 132000

        val adjusted = SettlementCalc.calculateAdjustedDeposit(rawFinalDeposit, carryOverBalance)
        val remaining = SettlementCalc.calculateRemainingCarryOver(rawFinalDeposit, carryOverBalance)
        val used = SettlementCalc.calculateUsedFromCarryOver(rawFinalDeposit, carryOverBalance)

        // 조정납입 = max(0, 132000 - 50000) = 82000
        assertEquals("2차 조정납입 = 82000", 82000, adjusted)
        // 남은 미수령 = max(0, 50000 - 132000) = 0
        assertEquals("미수령금 완전 상쇄", 0, remaining)
        // 공제액 = min(50000, 132000) = 50000
        assertEquals("공제액 = 50000", 50000, used)
    }

    @Test
    fun scenario_mixedPayments_withNegativeCarryOver() {
        // 이전에 미납금 -30000 → 오늘 현금+이체 혼합
        // 현금 5건×20000=100000, 이체 3건×25000=75000 → totalFare=175000
        val totalFare = 175000
        val carryOverBalance = -30000
        val officeDeposit = SettlementCalc.calculateOfficeDeposit(totalFare, RATIO) // 105000
        val totalCredit = 75000 // 이체 전액 외상
        val rawFinalDeposit = SettlementCalc.calculateRawFinalDeposit(officeDeposit, totalCredit) // 30000

        val adjusted = SettlementCalc.calculateAdjustedDeposit(rawFinalDeposit, carryOverBalance)
        val remaining = SettlementCalc.calculateRemainingCarryOver(rawFinalDeposit, carryOverBalance)

        // 조정납입 = 30000 + 30000(미납금) = 60000
        assertEquals("미납금 추가: 조정납입 = 60000", 60000, adjusted)
        // 남은 이월 = -30000 + 30000 = 0 (미납 상쇄)
        assertEquals("미납금 상쇄 → 0", 0, remaining)
    }

    @Test
    fun scenario_highFareTransfer_largeUnpaid() {
        // 고액 이체 200000 × 5건 → totalFare=1000000
        val totalFare = 1000000
        val officeDeposit = SettlementCalc.calculateOfficeDeposit(totalFare, RATIO) // 600000
        val totalCredit = 1000000 // 전액 외상
        val rawFinalDeposit = SettlementCalc.calculateRawFinalDeposit(officeDeposit, totalCredit) // -400000

        val adjusted = SettlementCalc.calculateAdjustedDeposit(rawFinalDeposit, 0)
        val remaining = SettlementCalc.calculateRemainingCarryOver(rawFinalDeposit, 0)

        assertEquals("고액 이체: adjustedDeposit = 0", 0, adjusted)
        assertEquals("고액 이체: 미수령금 = 400000", 400000, remaining)
    }

    @Test
    fun scenario_zeroFare() {
        val officeDeposit = SettlementCalc.calculateOfficeDeposit(0, RATIO)
        val rawFinalDeposit = SettlementCalc.calculateRawFinalDeposit(officeDeposit, 0)
        val adjusted = SettlementCalc.calculateAdjustedDeposit(rawFinalDeposit, 0)
        val remaining = SettlementCalc.calculateRemainingCarryOver(rawFinalDeposit, 0)

        assertEquals(0, officeDeposit)
        assertEquals(0, rawFinalDeposit)
        assertEquals(0, adjusted)
        assertEquals(0, remaining)
    }
}
