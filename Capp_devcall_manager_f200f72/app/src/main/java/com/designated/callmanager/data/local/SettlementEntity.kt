package com.designated.callmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.designated.callmanager.data.SettlementData

@Entity(tableName = "settlements")
data class SettlementEntity(
    @PrimaryKey val callId: String,
    val driverName: String,
    val customerName: String,
    val departure: String = "",
    val destination: String = "",
    val waypoints: String = "",
    val fare: Int,
    val paymentMethod: String,
    val cardAmount: Int?,
    val cashAmount: Int?,
    val creditAmount: Int = 0,
    val completedAt: Long,
    val driverId: String = "",
    val regionId: String = "",
    val officeId: String = "",
    val workDate: String = "",
    val isFinalized: Boolean = false,
    val sessionId: String? = null,
    val isWaitingSettlement: Boolean = false
) {
    fun toData() = SettlementData(
        callId, driverName, customerName, departure, destination, waypoints, fare,
        paymentMethod, cardAmount, cashAmount, creditAmount, completedAt,
        driverId, regionId, officeId, workDate
    )

    companion object {
        fun fromData(d: SettlementData) = SettlementEntity(
            d.callId, d.driverName, d.customerName, d.departure, d.destination, d.waypoints,
            d.fare, d.paymentMethod, d.cardAmount, d.cashAmount, d.creditAmount,
            d.completedAt, d.driverId, d.regionId, d.officeId, d.workDate
        )
    }
} 