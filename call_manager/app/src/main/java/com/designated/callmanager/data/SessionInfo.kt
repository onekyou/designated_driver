package com.designated.callmanager.data

import com.google.firebase.Timestamp

data class SessionInfo(
    val sessionId: String = "",
    val endAt: Timestamp? = null,
    val totalFare: Long = 0,
    val totalTrips: Int = 0
) 