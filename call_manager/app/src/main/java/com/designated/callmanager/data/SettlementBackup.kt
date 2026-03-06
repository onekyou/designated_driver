package com.designated.callmanager.data

/**
 * Firebase Realtime Database용 정산 백업 데이터 모델
 * - JSON 형태로 저장되어 비용 효율적
 * - 로컬 SettlementEntity와 호환
 */
data class SettlementBackup(
    val backupId: String = "",
    val provinceId: String = "",
    val officeId: String = "",
    val backupDate: String = "", // "yyyy-MM-dd" 형식
    val createdAt: Long = System.currentTimeMillis(),
    val settlements: List<SettlementBackupItem> = emptyList(),
    val metadata: SettlementBackupMetadata = SettlementBackupMetadata()
)

data class SettlementBackupItem(
    val callId: String = "",
    val driverName: String = "",
    val customerName: String = "",
    val departure: String = "",
    val destination: String = "",
    val waypoints: String = "",
    val fare: Int = 0,
    val paymentMethod: String = "",
    val cardAmount: Int? = null,
    val cashAmount: Int? = null,
    val creditAmount: Int = 0,
    val pointsUsed: Int = 0,
    val completedAt: Long = 0,
    val driverId: String = "",
    val workDate: String = ""
) {
    // SettlementData로 변환
    fun toSettlementData(regionId: String, officeId: String): SettlementData {
        return SettlementData(
            callId = callId,
            driverName = driverName,
            customerName = customerName,
            departure = departure,
            destination = destination,
            waypoints = waypoints,
            fare = fare,
            paymentMethod = paymentMethod,
            cardAmount = cardAmount,
            cashAmount = cashAmount,
            creditAmount = creditAmount,
            pointsUsed = pointsUsed,
            completedAt = completedAt,
            driverId = driverId,
            regionId = regionId,
            officeId = officeId,
            workDate = workDate
        )
    }

    companion object {
        // SettlementData에서 변환
        fun fromSettlementData(settlement: SettlementData): SettlementBackupItem {
            return SettlementBackupItem(
                callId = settlement.callId,
                driverName = settlement.driverName,
                customerName = settlement.customerName,
                departure = settlement.departure,
                destination = settlement.destination,
                waypoints = settlement.waypoints,
                fare = settlement.fare,
                paymentMethod = settlement.paymentMethod,
                cardAmount = settlement.cardAmount,
                cashAmount = settlement.cashAmount,
                creditAmount = settlement.creditAmount,
                pointsUsed = settlement.pointsUsed,
                completedAt = settlement.completedAt,
                driverId = settlement.driverId,
                workDate = settlement.workDate
            )
        }
    }
}

data class SettlementBackupMetadata(
    val totalCount: Int = 0,
    val totalFare: Int = 0,
    val paymentMethodCounts: Map<String, Int> = emptyMap(),
    val driverCounts: Map<String, Int> = emptyMap(),
    val appVersion: String = "",
    val deviceModel: String = ""
)