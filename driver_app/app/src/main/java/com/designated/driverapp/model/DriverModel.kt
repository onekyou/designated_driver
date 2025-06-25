package com.designated.driverapp.model

import android.os.Parcelable
import com.designated.driverapp.data.Constants
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize
import com.google.firebase.firestore.Exclude

// 기사 운행 상태 Enum (추가)
// enum class DriverStatus(val value: String) {
//     ONLINE(Constants.DRIVER_STATUS_ONLINE),
//     OFFLINE(Constants.DRIVER_STATUS_OFFLINE),
//     PREPARING(Constants.DRIVER_STATUS_PREPARING),
//     ON_TRIP(Constants.DRIVER_STATUS_ON_TRIP),
//     WAITING(Constants.DRIVER_STATUS_WAITING)
// }

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