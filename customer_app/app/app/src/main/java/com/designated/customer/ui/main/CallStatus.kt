package com.designated.customer.ui.main

data class CallStatus(
    val callId: String,
    val state: CallState,
    val timestamp: Long,
    val driverInfo: DriverInfo? = null,
    val estimatedArrivalTime: Int = 0 // minutes
) {
    fun getStatusText(): String {
        return when (state) {
            CallState.REQUESTED -> "콜 요청됨"
            CallState.ASSIGNED -> "기사 배정됨"
            CallState.DRIVER_ARRIVING -> "기사 이동 중"
            CallState.IN_PROGRESS -> "운행 중"
            CallState.COMPLETED -> "운행 완료"
            CallState.CANCELLED -> "취소됨"
        }
    }
}

enum class CallState {
    REQUESTED,
    ASSIGNED,
    DRIVER_ARRIVING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

data class DriverInfo(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val vehicleNumber: String,
    val rating: Float = 0f
)