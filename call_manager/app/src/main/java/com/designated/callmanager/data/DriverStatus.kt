package com.designated.callmanager.data

enum class DriverStatus(val value: String) {
    WAITING("WAITING"),
    ASSIGNED("ASSIGNED"),
    ON_TRIP("ON_TRIP"),
    PREPARING("PREPARING"),
    ONLINE("ONLINE"),
    OFFLINE("OFFLINE"),
    PENDING_CONFIRM("PENDING_CONFIRM"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromString(value: String): DriverStatus {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}