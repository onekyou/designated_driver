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
    val customerAddress: String? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    val destination: String? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    val detectedTimestamp: Timestamp? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    val timestamp: Timestamp? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    var status: String = Constants.STATUS_WAITING,
    val fare: Int? = null,
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val assignedDriverPhone: String? = null,
    val assignedTimestamp: Timestamp? = null,
    val assignedPickupDriverId: String? = null,
    val deviceName: String? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    val officeId: String = "",
    val provinceId: String = "",
    val cityId: String = "",
    val callType: String? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    val isAppCustomer: Boolean = false,  // ✅ 추가: 앱 회원 여부
    val memo: String = "",
    val departure_set: String? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    val destination_set: String? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    val waypoints_set: String? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    val fare_set: Int? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    val trip_summary: String? = null,  // ✅ nullable로 변경 (Firebase에서 null이 올 수 있음)
    val paymentMethod: String = "",
    val cashAmount: Int? = null,
    val cashReceived: Int? = null,
    val creditAmount: Int? = null,
    val isSummaryConfirmed: Boolean = false,
    val summaryConfirmedTimestamp: Timestamp? = null,

    var settlementStatus: String = Constants.SETTLEMENT_STATUS_PENDING,
    var settlementId: String? = null,

    var claimedDriverId: String? = null,
    var sourceSharedCallId: String? = null,

    // 운행 완료 시 최종 정보 (Cloud Function에서 저장)
    val tripSummaryFinal: String? = null,
    val finalFare: Int? = null,
    val fareFinal: Int? = null
) : Parcelable {
    @get:Exclude
    val statusEnum: CallStatus
        get() = CallStatus.fromFirestoreValue(status)
}