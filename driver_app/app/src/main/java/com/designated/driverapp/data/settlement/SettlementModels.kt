package com.designated.driverapp.data.settlement

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * 공유 정산 문서 전체 구조
 * Firestore 경로: provinces/{provinceId}/cities/{cityId}/offices/{officeId}/settlementSessions/{date}
 */
data class SettlementSession(
    val metadata: SettlementMetadata = SettlementMetadata(),
    val totals: SettlementTotals = SettlementTotals(),
    val calls: List<CallSettlement> = emptyList()
) {
    companion object {
        fun fromDocument(doc: DocumentSnapshot): SettlementSession? {
            if (!doc.exists()) return null

            return try {
                val metadata = doc.get("metadata")?.let { map ->
                    @Suppress("UNCHECKED_CAST")
                    SettlementMetadata.fromMap(map as Map<String, Any?>)
                } ?: SettlementMetadata()

                val totals = doc.get("totals")?.let { map ->
                    @Suppress("UNCHECKED_CAST")
                    SettlementTotals.fromMap(map as Map<String, Any?>)
                } ?: SettlementTotals()

                val calls = doc.get("calls")?.let { list ->
                    @Suppress("UNCHECKED_CAST")
                    (list as List<Map<String, Any?>>).mapNotNull { CallSettlement.fromMap(it) }
                } ?: emptyList()

                SettlementSession(metadata, totals, calls)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "metadata" to metadata.toMap(),
        "totals" to totals.toMap(),
        "calls" to calls.map { it.toMap() }
    )
}

/**
 * 정산 세션 메타데이터
 */
data class SettlementMetadata(
    val version: Long = 0,
    val lastUpdatedAt: Timestamp? = null,
    val lastUpdatedBy: String = "",  // "driver_app" | "call_manager"
    val depositRatio: Int = 60,      // 납입비율 (기본 60%)
    val createdAt: Timestamp? = null,
    val isFinalized: Boolean = false // 일일 마감 여부
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): SettlementMetadata {
            return SettlementMetadata(
                version = (map["version"] as? Long) ?: 0,
                lastUpdatedAt = map["lastUpdatedAt"] as? Timestamp,
                lastUpdatedBy = (map["lastUpdatedBy"] as? String) ?: "",
                depositRatio = (map["depositRatio"] as? Long)?.toInt() ?: 60,
                createdAt = map["createdAt"] as? Timestamp,
                isFinalized = (map["isFinalized"] as? Boolean) ?: false
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "version" to version,
        "lastUpdatedAt" to lastUpdatedAt,
        "lastUpdatedBy" to lastUpdatedBy,
        "depositRatio" to depositRatio,
        "createdAt" to createdAt,
        "isFinalized" to isFinalized
    )
}

/**
 * 정산 집계 데이터
 */
data class SettlementTotals(
    val totalFare: Long = 0,           // 총 운행료
    val totalDeposit: Long = 0,        // 총 납입액 (사무실 몫)
    val totalDriverShare: Long = 0,    // 총 기사 몫
    val totalCash: Long = 0,           // 총 현금
    val totalCard: Long = 0,           // 총 카드/이체
    val totalCredit: Long = 0,         // 총 외상
    val totalPoints: Long = 0,         // 총 포인트 사용
    val callCount: Int = 0             // 총 콜 수
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): SettlementTotals {
            return SettlementTotals(
                totalFare = (map["totalFare"] as? Long) ?: 0,
                totalDeposit = (map["totalDeposit"] as? Long) ?: 0,
                totalDriverShare = (map["totalDriverShare"] as? Long) ?: 0,
                totalCash = (map["totalCash"] as? Long) ?: 0,
                totalCard = (map["totalCard"] as? Long) ?: 0,
                totalCredit = (map["totalCredit"] as? Long) ?: 0,
                totalPoints = (map["totalPoints"] as? Long) ?: 0,
                callCount = (map["callCount"] as? Long)?.toInt() ?: 0
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "totalFare" to totalFare,
        "totalDeposit" to totalDeposit,
        "totalDriverShare" to totalDriverShare,
        "totalCash" to totalCash,
        "totalCard" to totalCard,
        "totalCredit" to totalCredit,
        "totalPoints" to totalPoints,
        "callCount" to callCount
    )

    /**
     * 새 콜 추가 시 집계 재계산
     */
    fun addCall(call: CallSettlement, depositRatio: Int): SettlementTotals {
        val newTotalFare = totalFare + call.fare
        val newTotalDeposit = (newTotalFare * depositRatio / 100)
        val newTotalDriverShare = newTotalFare - newTotalDeposit

        val cashAmount = when {
            call.paymentMethod == "현금" -> call.fare
            call.paymentMethod.startsWith("현금+") -> call.cashReceived
            else -> 0L
        }

        val cardAmount = when (call.paymentMethod) {
            "이체", "카드" -> call.fare
            else -> 0L
        }

        return SettlementTotals(
            totalFare = newTotalFare,
            totalDeposit = newTotalDeposit,
            totalDriverShare = newTotalDriverShare,
            totalCash = totalCash + cashAmount,
            totalCard = totalCard + cardAmount,
            totalCredit = totalCredit + call.creditAmount,
            totalPoints = totalPoints + call.pointsUsed,
            callCount = callCount + 1
        )
    }
}

/**
 * 개별 콜 정산 정보
 */
data class CallSettlement(
    val callId: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val departure: String = "",
    val destination: String = "",
    val fare: Long = 0,
    val paymentMethod: String = "",    // "현금" | "이체" | "외상" | "현금+포인트" | "포인트"
    val cashReceived: Long = 0,
    val creditAmount: Long = 0,
    val pointsUsed: Long = 0,
    val completedAt: Timestamp? = null,
    val confirmedByOffice: Boolean = false,  // 사무실 확인 여부
    val syncedAt: Timestamp? = null          // 동기화 시간
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): CallSettlement? {
            return try {
                CallSettlement(
                    callId = (map["callId"] as? String) ?: "",
                    driverId = (map["driverId"] as? String) ?: "",
                    driverName = (map["driverName"] as? String) ?: "",
                    customerName = (map["customerName"] as? String) ?: "",
                    customerPhone = (map["customerPhone"] as? String) ?: "",
                    departure = (map["departure"] as? String) ?: "",
                    destination = (map["destination"] as? String) ?: "",
                    fare = (map["fare"] as? Long) ?: 0,
                    paymentMethod = (map["paymentMethod"] as? String) ?: "",
                    cashReceived = (map["cashReceived"] as? Long) ?: 0,
                    creditAmount = (map["creditAmount"] as? Long) ?: 0,
                    pointsUsed = (map["pointsUsed"] as? Long) ?: 0,
                    completedAt = map["completedAt"] as? Timestamp,
                    confirmedByOffice = (map["confirmedByOffice"] as? Boolean) ?: false,
                    syncedAt = map["syncedAt"] as? Timestamp
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "callId" to callId,
        "driverId" to driverId,
        "driverName" to driverName,
        "customerName" to customerName,
        "customerPhone" to customerPhone,
        "departure" to departure,
        "destination" to destination,
        "fare" to fare,
        "paymentMethod" to paymentMethod,
        "cashReceived" to cashReceived,
        "creditAmount" to creditAmount,
        "pointsUsed" to pointsUsed,
        "completedAt" to completedAt,
        "confirmedByOffice" to confirmedByOffice,
        "syncedAt" to syncedAt
    )
}

/**
 * 동기화 상태
 */
enum class SyncStatus {
    PENDING,     // 동기화 대기 (오프라인에서 생성됨)
    SYNCING,     // 동기화 중
    SYNCED,      // 동기화 완료
    FAILED       // 동기화 실패
}

/**
 * 로컬 동기화 대기 데이터 (오프라인 지원용)
 */
data class PendingSync(
    val id: Long = 0,
    val callSettlement: CallSettlement,
    val sessionDate: String,         // YYYY-MM-DD
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null
)

/**
 * 기사앱 관점의 정산 요약 (UI 표시용)
 * 용어: 지출 관점
 */
data class DriverSettlementSummary(
    val totalFare: Long,           // 총 운행료
    val depositAmount: Long,       // 납부액 (사무실에 내야 할 금액)
    val myIncome: Long,            // 내 수익 (기사 몫)
    val unpaidAmount: Long,        // 미납금 (외상)
    val actualDeposit: Long,       // 실 납부액 (납부액 - 미납금)
    val callCount: Int,            // 운행 횟수
    val depositRatio: Int          // 납입비율
) {
    companion object {
        fun fromSession(session: SettlementSession): DriverSettlementSummary {
            val totals = session.totals
            val depositRatio = session.metadata.depositRatio

            return DriverSettlementSummary(
                totalFare = totals.totalFare,
                depositAmount = totals.totalDeposit,
                myIncome = totals.totalDriverShare,
                unpaidAmount = totals.totalCredit,
                actualDeposit = totals.totalDeposit - totals.totalCredit,
                callCount = totals.callCount,
                depositRatio = depositRatio
            )
        }
    }
}

/**
 * 이월 정산 상태
 */
enum class CarryOverStatus {
    PENDING,      // 미수령 상태
    TRANSFERRED,  // 이체됨 (수령 확인 대기)
    SETTLED       // 수령 완료
}

/**
 * 일일 정산 확인 상태
 */
enum class DailySettlementStatus {
    WORKING,          // 근무 중 (아직 마감 안 함)
    PENDING_CONFIRM,  // 마감 완료, 매니저 확인 대기
    CONFIRMED         // 매니저 확인 완료
}

/**
 * 기사의 일일 정산 데이터 (업무마감 시 저장)
 * Firestore 경로: drivers/{driverId} 문서의 dailySettlement 필드
 */
data class DriverDailySettlement(
    val date: String = "",                 // YYYY-MM-DD
    val finalDeposit: Long = 0,            // 최종 납입액 (사무실 몫 - 외상)
    val realDeposit: Long = 0,             // 실납입 (기사가 실제로 낸 금액)
    val settlementDiff: Long = 0,          // 정산 차액 (실납입 - 최종 납입액)
    val totalFare: Long = 0,               // 총 운행료
    val totalCredit: Long = 0,             // 총 외상
    val tripCount: Int = 0,                // 운행 횟수
    val status: DailySettlementStatus = DailySettlementStatus.WORKING,
    val submittedAt: Timestamp? = null,    // 기사 마감 시간
    val confirmedAt: Timestamp? = null,    // 매니저 확인 시간
    val confirmedBy: String? = null        // 확인한 매니저 ID
) {
    companion object {
        fun fromMap(map: Map<String, Any?>?): DriverDailySettlement {
            if (map == null) return DriverDailySettlement()
            return DriverDailySettlement(
                date = (map["date"] as? String) ?: "",
                finalDeposit = (map["finalDeposit"] as? Long) ?: 0,
                realDeposit = (map["realDeposit"] as? Long) ?: 0,
                settlementDiff = (map["settlementDiff"] as? Long) ?: 0,
                totalFare = (map["totalFare"] as? Long) ?: 0,
                totalCredit = (map["totalCredit"] as? Long) ?: 0,
                tripCount = (map["tripCount"] as? Long)?.toInt() ?: 0,
                status = try {
                    DailySettlementStatus.valueOf((map["status"] as? String) ?: "WORKING")
                } catch (e: Exception) {
                    DailySettlementStatus.WORKING
                },
                submittedAt = map["submittedAt"] as? Timestamp,
                confirmedAt = map["confirmedAt"] as? Timestamp,
                confirmedBy = map["confirmedBy"] as? String
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "date" to date,
        "finalDeposit" to finalDeposit,
        "realDeposit" to realDeposit,
        "settlementDiff" to settlementDiff,
        "totalFare" to totalFare,
        "totalCredit" to totalCredit,
        "tripCount" to tripCount,
        "status" to status.name,
        "submittedAt" to submittedAt,
        "confirmedAt" to confirmedAt,
        "confirmedBy" to confirmedBy
    )
}

/**
 * 기사의 이월 정산 (미수령금) 데이터
 * Firestore 경로: drivers/{driverId}/carryOver (필드)
 */
data class DriverCarryOver(
    val balance: Long = 0,                    // 누적 미수령금 (양수 = 내가 받아야 함)
    val status: CarryOverStatus = CarryOverStatus.PENDING,
    val lastUpdatedAt: Timestamp? = null,
    val transferredAt: Timestamp? = null,     // 사무실에서 이체한 시점
    val transferredBy: String? = null,        // 이체 처리한 매니저 ID
    val todayAmount: Long = 0                 // 오늘 발생한 미수령금
) {
    companion object {
        fun fromMap(map: Map<String, Any?>?): DriverCarryOver {
            if (map == null) return DriverCarryOver()
            return DriverCarryOver(
                balance = (map["balance"] as? Long) ?: 0,
                status = try {
                    CarryOverStatus.valueOf((map["status"] as? String) ?: "PENDING")
                } catch (e: Exception) {
                    CarryOverStatus.PENDING
                },
                lastUpdatedAt = map["lastUpdatedAt"] as? Timestamp,
                transferredAt = map["transferredAt"] as? Timestamp,
                transferredBy = map["transferredBy"] as? String,
                todayAmount = (map["todayAmount"] as? Long) ?: 0
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "balance" to balance,
        "status" to status.name,
        "lastUpdatedAt" to lastUpdatedAt,
        "transferredAt" to transferredAt,
        "transferredBy" to transferredBy,
        "todayAmount" to todayAmount
    )
}
