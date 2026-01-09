package com.designated.callmanager.data

import com.google.firebase.Timestamp

data class PendingDriverInfo(
    val authUid: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val driverType: String = "대리기사",
    val status: String = "승인대기중",
    val requestedAt: Timestamp? = null,
    val targetProvinceId: String = "",
    val targetCityId: String = "",
    val targetOfficeId: String = ""
)