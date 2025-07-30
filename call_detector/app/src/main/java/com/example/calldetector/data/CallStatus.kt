package com.example.calldetector.data

enum class CallStatus(val firestoreValue: String) {
    WAITING("WAITING"),
    PENDING("PENDING"),
    MATCHED("MATCHED"),
    DISPATCHED("DISPATCHED"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED");

    companion object {
        fun fromFirestoreValue(value: String): CallStatus? {
            return values().find { it.firestoreValue == value }
        }
    }
}