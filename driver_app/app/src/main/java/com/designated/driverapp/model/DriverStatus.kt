package com.designated.driverapp.model

enum class DriverStatus(val value: String) {
    ONLINE("ONLINE"),        // 처음 로그인 시 상태
    OFFLINE("OFFLINE"),      // 오프라인
    WAITING("WAITING"),      // 콜 대기중 (로그인 후, 또는 운행 완료 후)
    ASSIGNED("ASSIGNED"),    // 콜 배정됨
    ACCEPTED("ACCEPTED"),    // 기사가 콜 수락함
    ON_TRIP("ON_TRIP"),      // 운행중
    UNKNOWN("UNKNOWN");      // 알수없음

    fun getDisplayName(): String {
        return when (this) {
            ONLINE -> "온라인"
            OFFLINE -> "오프라인"
            WAITING -> "대기중"
            ASSIGNED -> "배정됨"
            ACCEPTED -> "수락함"
            ON_TRIP -> "운행중"
            UNKNOWN -> "알수없음"
        }
    }

    companion object {
        fun fromString(value: String?): DriverStatus {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
} 