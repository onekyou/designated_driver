package com.designated.pickupapp.data

import com.google.firebase.Timestamp

data class CallInfo(
    val id: String = "",
    val phoneNumber: String? = null,
    val customerName: String? = null,
    val customerAddress: String? = null,
    val status: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val callType: String? = null,
    val departure: String = "",
    val destination: String = "",
    val waypoints: String = "",
    val fare: Int = 0,
    val paymentMethod: String = "",
    val tripSummary: String = "",
    val departure_set: String? = null,
    val destination_set: String? = null,
    val waypoints_set: String? = null,
    val fare_set: Int? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "phoneNumber" to phoneNumber,
            "customerName" to customerName,
            "customerAddress" to customerAddress,
            "status" to status,
            "timestamp" to timestamp,
            "assignedDriverId" to assignedDriverId,
            "assignedDriverName" to assignedDriverName,
            "callType" to callType,
            "departure" to departure,
            "destination" to destination,
            "waypoints" to waypoints,
            "fare" to fare,
            "paymentMethod" to paymentMethod,
            "tripSummary" to tripSummary,
            "departure_set" to departure_set,
            "destination_set" to destination_set,
            "waypoints_set" to waypoints_set,
            "fare_set" to fare_set
        )
    }
}

