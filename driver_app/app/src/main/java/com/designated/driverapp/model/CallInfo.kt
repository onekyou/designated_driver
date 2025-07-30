package com.designated.driverapp.model

import android.os.Parcelable
import com.designated.driverapp.data.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// 호출 상태를 나타내는 열거형 클래스
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
    var status: String = Constants.STATUS_WAITING,
    val fare: Int = 0,
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val assignedDriverPhone: String? = null,
    // Firestore documents created before 2025-07-08 saved this field as a Long (epoch millis)
    // while newer documents use a proper Timestamp.  We accept either type by deserialising into Any?.
    // The app never writes to this property directly and only orders by this field inside Firestore queries,
    // so keeping it as a raw value is sufficient and avoids ClassCastExceptions during toObject().
    @get:PropertyName("assignedTimestamp")
    val assignedTimestamp: @RawValue Any? = null,
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
    val cashReceived: Int? = null,
    val creditAmount: Int? = null,
    val isSummaryConfirmed: Boolean = false,
    val summaryConfirmedTimestamp: Timestamp? = null,

    // 정산 관련 필드
    var settlementStatus: String = Constants.SETTLEMENT_STATUS_PENDING,
    var settlementId: String? = null,

    // 선택 필드: 공유콜에서 사용
    var claimedDriverId: String? = null,
    var sourceSharedCallId: String? = null
) : Parcelable {
    @get:Exclude
    val statusEnum: CallStatus
        get() = CallStatus.fromFirestoreValue(status)
} 