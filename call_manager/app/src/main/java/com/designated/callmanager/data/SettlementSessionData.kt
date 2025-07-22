package com.designated.callmanager.data

import com.google.firebase.Timestamp

data class SettlementSessionData(
    val id: String = "",
    val status: String = STATUS_OPEN,
    val openedAt: Timestamp? = null,
    val closedAt: Timestamp? = null,
    val totalTrips: Int = 0,
    val totalFare: Int = 0,
    val memo: String? = null
){
    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_CLOSED = "CLOSED"
    }
} 