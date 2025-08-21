package com.designated.pickupapp.data

data class DriverInfo(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val status: String = Constants.STATUS_OFFLINE,
    val fcmToken: String? = null,
    val authUid: String? = null
)