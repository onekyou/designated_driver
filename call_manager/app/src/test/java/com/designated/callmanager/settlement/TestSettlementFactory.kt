package com.designated.callmanager.settlement

import com.designated.callmanager.data.SettlementData

/**
 * 테스트용 SettlementData 팩토리
 * 7일 × 50콜 = 350콜 시나리오 생성
 */
object TestSettlementFactory {

    private var callIdCounter = 0

    fun resetCounter() { callIdCounter = 0 }

    private fun nextCallId(): String = "call_${++callIdCounter}"

    fun createCashCall(
        fare: Int,
        driverId: String = "driver_A",
        driverName: String = "양세훈",
        completedAt: Long = System.currentTimeMillis()
    ) = SettlementData(
        callId = nextCallId(),
        driverName = driverName,
        customerName = "고객_${callIdCounter}",
        departure = "출발지",
        destination = "도착지",
        fare = fare,
        paymentMethod = "현금",
        cardAmount = null,
        cashAmount = fare,
        completedAt = completedAt,
        driverId = driverId
    )

    fun createTransferCall(
        fare: Int,
        driverId: String = "driver_A",
        driverName: String = "양세훈",
        completedAt: Long = System.currentTimeMillis()
    ) = SettlementData(
        callId = nextCallId(),
        driverName = driverName,
        customerName = "고객_${callIdCounter}",
        fare = fare,
        paymentMethod = "이체",
        cardAmount = null,
        cashAmount = null,
        completedAt = completedAt,
        driverId = driverId
    )

    fun createCashPlusPointCall(
        fare: Int,
        cashAmount: Int,
        driverId: String = "driver_A",
        driverName: String = "양세훈",
        completedAt: Long = System.currentTimeMillis()
    ) = SettlementData(
        callId = nextCallId(),
        driverName = driverName,
        customerName = "고객_${callIdCounter}",
        fare = fare,
        paymentMethod = "현금+포인트",
        cardAmount = null,
        cashAmount = cashAmount,
        pointsUsed = fare - cashAmount,
        completedAt = completedAt,
        driverId = driverId
    )

    fun createPointCall(
        fare: Int,
        driverId: String = "driver_A",
        driverName: String = "양세훈",
        completedAt: Long = System.currentTimeMillis()
    ) = SettlementData(
        callId = nextCallId(),
        driverName = driverName,
        customerName = "고객_${callIdCounter}",
        fare = fare,
        paymentMethod = "포인트",
        cardAmount = null,
        cashAmount = null,
        pointsUsed = fare,
        completedAt = completedAt,
        driverId = driverId
    )

    fun createCreditCall(
        fare: Int,
        creditAmount: Int = fare,
        driverId: String = "driver_A",
        driverName: String = "양세훈",
        completedAt: Long = System.currentTimeMillis()
    ) = SettlementData(
        callId = nextCallId(),
        driverName = driverName,
        customerName = "고객_${callIdCounter}",
        fare = fare,
        paymentMethod = "외상",
        cardAmount = null,
        cashAmount = null,
        creditAmount = creditAmount,
        completedAt = completedAt,
        driverId = driverId
    )

    // ========== Day 1: 정상 플로우 (50콜) ==========
    fun createDay1Calls(): List<SettlementData> {
        val base = System.currentTimeMillis()
        val calls = mutableListOf<SettlementData>()

        // 현금 15건: A 8건, B 7건
        repeat(8) { calls.add(createCashCall(fare = 20000 + it * 1000, driverId = "driver_A", driverName = "양세훈", completedAt = base + it * 60000L)) }
        repeat(7) { calls.add(createCashCall(fare = 15000 + it * 1000, driverId = "driver_B", driverName = "고양이", completedAt = base + (8 + it) * 60000L)) }

        // 앱콜→현금 10건: A 5건, B 5건
        repeat(5) { calls.add(createCashCall(fare = 25000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (15 + it) * 60000L)) }
        repeat(5) { calls.add(createCashCall(fare = 30000, driverId = "driver_B", driverName = "고양이", completedAt = base + (20 + it) * 60000L)) }

        // 이체 8건: A 4건, B 4건
        repeat(4) { calls.add(createTransferCall(fare = 18000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (25 + it) * 60000L)) }
        repeat(4) { calls.add(createTransferCall(fare = 22000, driverId = "driver_B", driverName = "고양이", completedAt = base + (29 + it) * 60000L)) }

        // 현금+포인트 혼합 7건: A 4건, B 3건
        repeat(4) { calls.add(createCashPlusPointCall(fare = 20000, cashAmount = 15000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (33 + it) * 60000L)) }
        repeat(3) { calls.add(createCashPlusPointCall(fare = 25000, cashAmount = 20000, driverId = "driver_B", driverName = "고양이", completedAt = base + (37 + it) * 60000L)) }

        // 전액 포인트 5건: A 3건, B 2건
        repeat(3) { calls.add(createPointCall(fare = 10000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (40 + it) * 60000L)) }
        repeat(2) { calls.add(createPointCall(fare = 12000, driverId = "driver_B", driverName = "고양이", completedAt = base + (43 + it) * 60000L)) }

        // 외상 5건: A 3건, B 2건
        repeat(3) { calls.add(createCreditCall(fare = 15000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (45 + it) * 60000L)) }
        repeat(2) { calls.add(createCreditCall(fare = 20000, driverId = "driver_B", driverName = "고양이", completedAt = base + (48 + it) * 60000L)) }

        return calls
    }

    // ========== Day 2: 취소/거절 (50콜, 완료된 것만) ==========
    fun createDay2Calls(): List<SettlementData> {
        val base = System.currentTimeMillis()
        val calls = mutableListOf<SettlementData>()

        // 정상 완료 20건
        repeat(10) { calls.add(createCashCall(fare = 20000, driverId = "driver_A", driverName = "양세훈", completedAt = base + it * 60000L)) }
        repeat(10) { calls.add(createCashCall(fare = 25000, driverId = "driver_B", driverName = "고양이", completedAt = base + (10 + it) * 60000L)) }

        // 거절→재배차→완료 5건 (B가 완료, A가 원래 배정되었지만 거절)
        repeat(5) { calls.add(createCashCall(fare = 30000, driverId = "driver_B", driverName = "고양이", completedAt = base + (20 + it) * 60000L)) }

        // HOLD→재배차→완료 5건 (A가 최종 완료)
        repeat(5) { calls.add(createTransferCall(fare = 22000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (25 + it) * 60000L)) }

        // 연속 빠른 콜 10건
        repeat(5) { calls.add(createCashCall(fare = 18000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (30 + it) * 30000L)) }
        repeat(5) { calls.add(createCashCall(fare = 15000, driverId = "driver_B", driverName = "고양이", completedAt = base + (35 + it) * 30000L)) }

        // 취소된 콜은 이 리스트에 포함하지 않음 (COMPLETED만)
        // 취소 5+5+5=15건은 별도 검증 (정산에 포함되지 않아야 함)
        return calls
    }

    // ========== Day 3: 퇴근/재출근 ==========
    fun createDay3Calls_firstShift(): List<SettlementData> {
        val base = System.currentTimeMillis()
        val calls = mutableListOf<SettlementData>()

        // A,B 1차운행 15건
        repeat(8) { calls.add(createCashCall(fare = 20000, driverId = "driver_A", driverName = "양세훈", completedAt = base + it * 60000L)) }
        repeat(7) { calls.add(createTransferCall(fare = 25000, driverId = "driver_B", driverName = "고양이", completedAt = base + (8 + it) * 60000L)) }

        return calls
    }

    fun createDay3Calls_secondShift(afterClearedAt: Long): List<SettlementData> {
        val calls = mutableListOf<SettlementData>()

        // A 2차운행 10건 (마감 이후)
        repeat(10) { calls.add(createCashCall(fare = 22000, driverId = "driver_A", driverName = "양세훈", completedAt = afterClearedAt + (it + 1) * 60000L)) }

        return calls
    }

    fun createDay3Calls_others(base: Long): List<SettlementData> {
        val calls = mutableListOf<SettlementData>()

        // C,D,E 일반운행 20건
        repeat(7) { calls.add(createCashCall(fare = 18000, driverId = "driver_C", driverName = "기사C", completedAt = base + it * 60000L)) }
        repeat(7) { calls.add(createCashCall(fare = 20000, driverId = "driver_D", driverName = "기사D", completedAt = base + (7 + it) * 60000L)) }
        repeat(6) { calls.add(createTransferCall(fare = 15000, driverId = "driver_E", driverName = "기사E", completedAt = base + (14 + it) * 60000L)) }

        // B 마감 전 이체 5건
        repeat(5) { calls.add(createCashCall(fare = 25000, driverId = "driver_B", driverName = "고양이", completedAt = base + (20 + it) * 60000L)) }

        return calls
    }

    // ========== Day 6: 특수 상황 ==========
    fun createDay6EdgeCases(): List<SettlementData> {
        val base = System.currentTimeMillis()
        val calls = mutableListOf<SettlementData>()

        // 정상 20건
        repeat(10) { calls.add(createCashCall(fare = 20000, driverId = "driver_A", driverName = "양세훈", completedAt = base + it * 60000L)) }
        repeat(10) { calls.add(createCashCall(fare = 25000, driverId = "driver_B", driverName = "고양이", completedAt = base + (10 + it) * 60000L)) }

        // 0원 콜 5건
        repeat(5) { calls.add(createCashCall(fare = 0, driverId = "driver_A", driverName = "양세훈", completedAt = base + (20 + it) * 60000L)) }

        // 소액 5,000원 5건
        repeat(5) { calls.add(createCashCall(fare = 5000, driverId = "driver_B", driverName = "고양이", completedAt = base + (25 + it) * 60000L)) }

        // 고액 200,000원 5건
        repeat(5) { calls.add(createTransferCall(fare = 200000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (30 + it) * 60000L)) }

        // 동일기사 연속 10건
        repeat(10) { calls.add(createCashCall(fare = 22000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (35 + it) * 60000L)) }

        return calls
    }

    // ========== Day 7: 다양한 결제 혼합 ==========
    fun createDay7MixedPayments(): List<SettlementData> {
        val base = System.currentTimeMillis()
        val calls = mutableListOf<SettlementData>()

        // 현금 10건
        repeat(5) { calls.add(createCashCall(fare = 20000, driverId = "driver_A", driverName = "양세훈", completedAt = base + it * 60000L)) }
        repeat(5) { calls.add(createCashCall(fare = 25000, driverId = "driver_B", driverName = "고양이", completedAt = base + (5 + it) * 60000L)) }

        // 이체 10건
        repeat(5) { calls.add(createTransferCall(fare = 18000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (10 + it) * 60000L)) }
        repeat(5) { calls.add(createTransferCall(fare = 22000, driverId = "driver_B", driverName = "고양이", completedAt = base + (15 + it) * 60000L)) }

        // 현금+포인트 10건
        repeat(5) { calls.add(createCashPlusPointCall(fare = 20000, cashAmount = 15000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (20 + it) * 60000L)) }
        repeat(5) { calls.add(createCashPlusPointCall(fare = 25000, cashAmount = 20000, driverId = "driver_B", driverName = "고양이", completedAt = base + (25 + it) * 60000L)) }

        // 포인트 전액 10건
        repeat(5) { calls.add(createPointCall(fare = 10000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (30 + it) * 60000L)) }
        repeat(5) { calls.add(createPointCall(fare = 12000, driverId = "driver_B", driverName = "고양이", completedAt = base + (35 + it) * 60000L)) }

        // 외상 10건
        repeat(5) { calls.add(createCreditCall(fare = 15000, driverId = "driver_A", driverName = "양세훈", completedAt = base + (40 + it) * 60000L)) }
        repeat(5) { calls.add(createCreditCall(fare = 20000, driverId = "driver_B", driverName = "고양이", completedAt = base + (45 + it) * 60000L)) }

        return calls
    }
}
