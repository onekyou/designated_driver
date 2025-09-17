package com.designated.callmanager.data

import com.google.firebase.Timestamp

data class SharedCallInfo(
    val id: String = "",
    val status: String = "OPEN",
    val departure: String? = null,
    val destination: String? = null,
    val fare: Int? = null,
    val sourceRegionId: String = "",
    val sourceOfficeId: String = "",
    val targetRegionId: String = "",
    val claimedOfficeId: String? = null,
    val createdBy: String = "",
    val timestamp: Timestamp? = null,
    val phoneNumber: String? = null,
    val claimedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val destCallId: String? = null,
    val callType: String? = null
)