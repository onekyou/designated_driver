package com.designated.customer.data.model

data class CustomerCall(
    val id: String = "",
    val phoneNumber: String,
    val officeId: String,
    val currentLocation: String,
    val destinationLocation: String,
    val timestamp: Long,
    val status: String, // REQUESTED, ASSIGNED, DRIVER_ARRIVING, IN_PROGRESS, COMPLETED, CANCELLED
    val driverId: String? = null,
    val estimatedArrivalTime: Int? = null, // minutes
    val fare: Int? = null,
    val notes: String? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "phoneNumber" to phoneNumber,
            "officeId" to officeId,
            "currentLocation" to currentLocation,
            "destinationLocation" to destinationLocation,
            "timestamp" to timestamp,
            "status" to status,
            "driverId" to driverId,
            "estimatedArrivalTime" to estimatedArrivalTime,
            "fare" to fare,
            "notes" to notes
        )
    }

    companion object {
        fun fromMap(data: Map<String, Any>): CustomerCall {
            return CustomerCall(
                id = data["id"] as? String ?: "",
                phoneNumber = data["phoneNumber"] as? String ?: "",
                officeId = data["officeId"] as? String ?: "",
                currentLocation = data["currentLocation"] as? String ?: "",
                destinationLocation = data["destinationLocation"] as? String ?: "",
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                status = data["status"] as? String ?: "REQUESTED",
                driverId = data["driverId"] as? String,
                estimatedArrivalTime = (data["estimatedArrivalTime"] as? Number)?.toInt(),
                fare = (data["fare"] as? Number)?.toInt(),
                notes = data["notes"] as? String
            )
        }
    }
}