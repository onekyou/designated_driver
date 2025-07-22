package com.designated.callmanager.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * /dailySettlements/{workDate} 문서 매핑용
 */
data class DailySummary(
    @PropertyName("totalFare")      var totalFare: Long? = 0,
    @PropertyName("totalTrips")     var totalTrips: Long? = 0,
    @PropertyName("updatedAt")      var updatedAt: Timestamp? = null
) 