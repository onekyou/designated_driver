package com.designated.callmanager.data

enum class DriverStatus(val value: String) {
    WAITING("WAITING"),
    ASSIGNED("ASSIGNED"),
    ON_TRIP("ON_TRIP"),
    PREPARING("PREPARING"),
    ONLINE("ONLINE"),
    OFFLINE("OFFLINE"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromString(value: String): DriverStatus {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
        // Firestore는 enum을 직접 저장하지 못하므로, firestoreValue를 사용했던 CallStatus와 달리
        // 이 클래스는 value 프로퍼티를 사용합니다. 필요시 fromFirestoreValue 같은 함수를 추가할 수 있습니다.
    }
}