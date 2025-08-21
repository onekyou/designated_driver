package com.designated.pickupapp.data

enum class PickupStatus(val value: String, val displayName: String) {
    AVAILABLE("AVAILABLE", "대기중"),
    BUSY("BUSY", "운행중"),
    OFFLINE("OFFLINE", "오프라인");

    companion object {
        fun fromString(status: String): PickupStatus {
            return entries.find { it.value == status } ?: OFFLINE
        }
    }
}