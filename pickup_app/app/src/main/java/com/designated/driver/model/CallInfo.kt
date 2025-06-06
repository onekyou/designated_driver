package com.designated.driver.model // Define a common model package

// Data class copied from call_manager
data class CallInfo(
    val id: String = "",
    val customerName: String = "",
    val phoneNumber: String = "",
    val location: String = "",
    val timestamp: Long = 0,
    val status: String = "대기중",
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val assignedTimestamp: Long? = null,
    val contactName: String = "", // These seem duplicate? Check if needed
    val address: String = "",     // location vs address?
    val callType: String = "",
    val deviceName: String = ""
) 