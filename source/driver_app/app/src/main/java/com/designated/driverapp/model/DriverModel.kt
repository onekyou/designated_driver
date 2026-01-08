package com.designated.driverapp.model

import android.os.Parcelable
import com.designated.driverapp.data.Constants
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize
import com.google.firebase.firestore.Exclude

enum class DriverApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}

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