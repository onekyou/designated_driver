package com.designated.callmanager.data

enum class CallStatus(val firestoreValue: String, val displayName: String) {
    WAITING("WAITING", "대기"),
    PENDING("PENDING", "기사승인대기"),
    ASSIGNED("ASSIGNED", "배차완료"),
    ACCEPTED("ACCEPTED", "수락"),
    PICKUP_COMPLETE("PICKUP_COMPLETE", "픽업완료"),
    IN_PROGRESS("IN_PROGRESS", "운행중"),
    AWAITING_SETTLEMENT("AWAITING_SETTLEMENT", "정산대기"),
    COMPLETED("COMPLETED", "완료"),
    CANCELED("CANCELED", "취소"),
    HOLD("HOLD", "보류"),
    UNKNOWN("UNKNOWN", "알수없음");

    companion object {
        fun fromFirestoreValue(value: String): CallStatus {
            return entries.find { it.firestoreValue == value } ?: UNKNOWN
        }
    }
}