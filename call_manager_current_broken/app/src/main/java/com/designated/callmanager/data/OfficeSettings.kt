package com.designated.callmanager.data

data class OfficeSettings(
    val officeId: String = "",
    val qrCode: String = "",
    val inviteCode: String = "",
    val landingPageUrl: String = "",
    val attributionThreshold: Int = 70
)