package com.example.calldetector.data

enum class CallStatus(val firestoreValue: String, val displayName: String) {
    WAITING("WAITING", "대기중"),
    ASSIGNED("ASSIGNED", "배차완료"),
    CANCELED("CANCELED", "취소"),
    COMPLETED("COMPLETED", "완료");

    companion object {
        fun fromFirestoreValue(value: String?): CallStatus? {
            return entries.find { it.firestoreValue == value }
        }
    }
} 