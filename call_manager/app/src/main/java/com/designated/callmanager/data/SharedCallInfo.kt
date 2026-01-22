package com.designated.callmanager.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class SharedCallInfo(
    val id: String = "",
    val status: String = "OPEN",
    val departure: String? = null,
    val destination: String? = null,
    val fare: Int? = null,
    @get:PropertyName("sourceProvinceId") @set:PropertyName("sourceProvinceId")
    var sourceProvinceId: String = "",
    @get:PropertyName("sourceCityId") @set:PropertyName("sourceCityId")
    var sourceCityId: String = "",
    val sourceOfficeId: String = "",
    @get:PropertyName("targetProvinceId") @set:PropertyName("targetProvinceId")
    var targetProvinceId: String = "",
    @get:PropertyName("targetCityId") @set:PropertyName("targetCityId")
    var targetCityId: String = "",
    val claimedOfficeId: String? = null,
    val createdBy: String = "",
    val timestamp: Timestamp? = null,
    val phoneNumber: String? = null,
    val claimedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val destCallId: String? = null,
    val callType: String? = null
)
