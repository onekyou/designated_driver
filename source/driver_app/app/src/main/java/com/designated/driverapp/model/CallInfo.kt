package com.designated.driverapp.model

import android.os.Parcelable
import com.designated.driverapp.data.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

enum class CallStatus(val firestoreValue: String, val displayName: String) {
    WAITING(Constants.STATUS_WAITING, "대기중"),
    ASSIGNED(Constants.STATUS_ASSIGNED, "배차완료"),
    ACCEPTED(Constants.STATUS_ACCEPTED, "수락"),
    IN_PROGRESS(Constants.STATUS_IN_PROGRESS, "운행중"),
    AWAITING_SETTLEMENT(Constants.STATUS_AWAITING_SETTLEMENT, "정산대기"),
    COMPLETED(Constants.STATUS_COMPLETED, "운행완료"),
    CANCELLED(Constants.STATUS_CANCELED, "취소");

    companion object {
        fun fromFirestoreValue(value: String): CallStatus {
            return entries.find { it.firestoreValue == value } ?: WAITING
        }
    }
}

@Parcelize
data class CallInfo(
    var id: String = "",
    val customerName: String = "",
    val phoneNumber: String = "",
    val customerAddress: String = "",
    val destination: String = "",
    val detectedTimestamp: Timestamp = Timestamp.now(),
    val timestamp: Timestamp = Timestamp.now(),
    var status: String = Constants.STATUS_WAITING,
    val fare: Int? = null,
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val assignedDriverPhone: String? = null,
    val assignedTimestamp: Timestamp? = null,
    val assignedPickupDriverId: String? = null,
    val deviceName: String = "",
    val officeId: String = "",
    val regionId: String = "",
    val callType: String = "",
    val isAppCustomer: Boolean = false,  // ✅ 추가: 앱 회원 여부
    val memo: String = "",
    val departure_set: String = "",
    val destination_set: String = "",
    val waypoints_set: String = "",
    val fare_set: Int = 0,
    val trip_summary: String = "",
    val paymentMethod: String = "",
    val cashAmount: Int? = null,
    val cashReceived: Int? = null,
    val creditAmount: Int? = null,
    val isSummaryConfirmed: Boolean = false,
    val summaryConfirmedTimestamp: Timestamp? = null,

    var settlementStatus: String = Constants.SETTLEMENT_STATUS_PENDING,
    var settlementId: String? = null,

    var claimedDriverId: String? = null,
    var sourceSharedCallId: String? = null
) : Parcelable {
    @get:Exclude
    val statusEnum: CallStatus
        get() = CallStatus.fromFirestoreValue(status)
}