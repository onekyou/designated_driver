package com.designated.pickupapp.data

enum class CallStatus(val value: String, val displayName: String) {
    WAITING("WAITING", "대기"),
    ASSIGNED("ASSIGNED", "배차완료"),
    ACCEPTED("ACCEPTED", "수락"),
    IN_PROGRESS("IN_PROGRESS", "운행중"),
    AWAITING_SETTLEMENT("AWAITING_SETTLEMENT", "정산대기"),
    COMPLETED("COMPLETED", "완료");

    companion object {
        fun fromString(status: String): CallStatus {
            return entries.find { it.value == status } ?: WAITING
        }
    }
}