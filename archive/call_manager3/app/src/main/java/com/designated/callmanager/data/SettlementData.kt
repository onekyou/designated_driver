package com.designated.callmanager.data

data class SettlementData(
    val callId: String,
    val settlementId: String,
    val driverName: String,
    val customerName: String,
    val customerPhone: String = "",
    val departure: String = "",
    val destination: String = "",
    val waypoints: String = "",
    val fare: Int,
    val paymentMethod: String,
    val cardAmount: Int?,
    val cashAmount: Int?,
    val creditAmount: Int = 0,  // 외상 처리된 금액
    val completedAt: Long,
    val driverId: String = "",
    val regionId: String = "",
    val officeId: String = "",
    val settlementStatus: String = Constants.SETTLEMENT_STATUS_PENDING,
    val workDate: String = "",
    val isFinalized: Boolean = false
) 