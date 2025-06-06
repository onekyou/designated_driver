package com.designated.driverapp.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize
import com.google.firebase.firestore.Exclude

// 기사 운행 상태 Enum (추가)
enum class DriverStatus(val value: String) {
    ONLINE("온라인"),       // 앱 실행 및 근무 가능 상태
    OFFLINE("오프라인"),     // 앱 종료 또는 근무 불가능 상태
    PREPARING("운행준비중"), // 콜 수락 후 운행 시작 전 상태 (추가)
    ON_TRIP("운행중"),      // 콜을 받아 운행중인 상태
    WAITING("대기중")      // 온라인 상태에서 콜을 기다리는 중
}

// 기사 승인 상태 Enum (추가)
enum class DriverApprovalStatus {
    PENDING,    // 승인 대기
    APPROVED,   // 승인됨
    REJECTED    // 거부됨
}

// 대리기사 정보를 담는 데이터 클래스
data class Driver(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    var status: DriverStatus = DriverStatus.OFFLINE,
    val currentCallId: String? = null,
    val rating: Float = 0f,
    val totalTrips: Int = 0,
    val registrationDate: Timestamp? = null,
    val isActive: Boolean = true,
    var approvalStatus: DriverApprovalStatus = DriverApprovalStatus.PENDING
)

// 픽업기사 정보를 담는 데이터 클래스
data class PickupDriver(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    var status: DriverStatus = DriverStatus.OFFLINE,
    val currentCallId: String? = null,
    val vehicleInfo: String = "",
    val isActive: Boolean = true,
    var approvalStatus: DriverApprovalStatus = DriverApprovalStatus.PENDING
)

// 호출 정보를 담는 데이터 클래스
@Parcelize
data class CallInfo(
    var id: String = "",
    val customerName: String = "",
    val phoneNumber: String = "",
    val customerAddress: String = "",
    val destination: String = "",
    val detectedTimestamp: Timestamp = Timestamp.now(),
    val timestamp: Timestamp = Timestamp.now(),
    var status: String = CallStatus.WAITING.firestoreValue,
    val fare: Int = 0,
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val assignedDriverPhone: String? = null,
    val assignedTimestamp: Timestamp? = null,
    val assignedPickupDriverId: String? = null,
    val deviceName: String = "",
    val officeId: String = "",
    val regionId: String = "",
    val callType: String = "",
    val memo: String = "",
    val departure_set: String = "",
    val destination_set: String = "",
    val waypoints_set: String = "",
    val fare_set: Int = 0,
    val trip_summary: String = "",
    val paymentMethod: String = "",
    val cashAmount: Int? = null,
    val isSummaryConfirmed: Boolean = false,
    val summaryConfirmedTimestamp: Timestamp? = null
) : Parcelable {
    @get:Exclude
    val statusEnum: CallStatus
        get() = CallStatus.fromFirestoreValue(status)
}