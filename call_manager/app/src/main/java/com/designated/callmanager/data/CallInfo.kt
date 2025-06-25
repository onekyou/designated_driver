package com.designated.callmanager.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * Firestore의 콜 정보를 나타내는 데이터 클래스
 */
data class CallInfo(
    var id: String = "", // Firestore document ID
    @get:PropertyName("phoneNumber") @set:PropertyName("phoneNumber") var phoneNumber: String = "",
    @get:PropertyName("customerAddress") @set:PropertyName("customerAddress") var customerAddress: String? = null,

    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Timestamp = Timestamp.now(),
    @get:PropertyName("status") @set:PropertyName("status") var status: String = Constants.STATUS_WAITING,

    @get:PropertyName("assignedDriverId") @set:PropertyName("assignedDriverId") var assignedDriverId: String? = null,
    @get:PropertyName("assignedDriverName") @set:PropertyName("assignedDriverName") var assignedDriverName: String? = null,
    @get:PropertyName("assignedTimestamp") @set:PropertyName("assignedTimestamp") var assignedTimestamp: Timestamp? = null,
    @get:PropertyName("customerName") @set:PropertyName("customerName") var customerName: String? = null,
    @get:PropertyName("address") @set:PropertyName("address") var address: String? = null,
    @get:PropertyName("callType") @set:PropertyName("callType") var callType: String? = null,
    @get:PropertyName("deviceName") @set:PropertyName("deviceName") var deviceName: String? = null,
    @get:PropertyName("detectedTimestamp") @set:PropertyName("detectedTimestamp") var detectedTimestamp: Timestamp? = null,
    @get:PropertyName("timestampClient") @set:PropertyName("timestampClient") var timestampClient: Long? = null,
    @PropertyName("trip_summary") var trip_summary: String? = null,
    @get:PropertyName("regionId") @set:PropertyName("regionId") var regionId: String? = null,
    @get:PropertyName("officeId") @set:PropertyName("officeId") var officeId: String? = null,
    @get:Exclude @set:Exclude var memo: String? = null,
    @get:PropertyName("departure_set") @set:PropertyName("departure_set") var departure_set: String? = null,
    @get:PropertyName("destination_set") @set:PropertyName("destination_set") var destination_set: String? = null,
    @get:PropertyName("waypoints_set") @set:PropertyName("waypoints_set") var waypoints_set: String? = null,
    @get:PropertyName("fare_set") @set:PropertyName("fare_set") var fare_set: Long? = null,
    @get:PropertyName("assignedDriverPhone") @set:PropertyName("assignedDriverPhone") var assignedDriverPhone: String? = null,
    @PropertyName("fare") var fare: Long? = null,
    @PropertyName("paymentMethod") var paymentMethod: String? = "",
    @PropertyName("cashAmount") var cashAmount: Long? = null,
    @PropertyName("isSummaryConfirmed") var isSummaryConfirmed: Boolean? = false,
    @PropertyName("summaryConfirmedTimestamp") var summaryConfirmedTimestamp: Timestamp? = null
) {
    // Firestore에서 객체 매핑 시 빈 생성자 필요 -> 모든 파라미터에 기본값이 있으므로 자동 생성됨. 명시적 정의 불필요.
    // constructor() : this("", "", "", Timestamp.now(), CallStatus.PENDING.value)
} 