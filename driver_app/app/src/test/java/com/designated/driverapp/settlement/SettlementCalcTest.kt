package com.designated.driverapp.settlement

import com.designated.driverapp.data.settlement.SettlementCalc
import org.junit.Assert.*
import org.junit.Test

/**
 * 기사앱 정산 계산 로직 JVM Unit Test
 * 당일 정산 기본 계산 검증 (이월 폐기 — PTT §3 정산 단순화)
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
}
