package com.designated.callmanager.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * Firestore settlements 컬렉션 문서 매핑용 데이터 클래스
 */
data class SettlementInfo(
    var id: String = "",
    @PropertyName("callId") var callId: String? = null,
    @PropertyName("driverId") var driverId: String? = null,
    @PropertyName("driverName") var driverName: String? = null,
    @PropertyName("customerName") var customerName: String? = null,
    @PropertyName("customerPhone") var customerPhone: String? = null,
    @PropertyName("departure") var departure: String? = null,
    @PropertyName("destination") var destination: String? = null,
    @PropertyName("waypoints") var waypoints: String? = "",
    @PropertyName("fare") var fare: Long? = 0,
    @PropertyName("paymentMethod") var paymentMethod: String? = "",
    @PropertyName("cashAmount") var cashAmount: Long? = null,
    @PropertyName("creditAmount") var creditAmount: Long? = null,
    @PropertyName("fareFinal") var fareFinal: Long? = null,
    @PropertyName("settlementStatus") var settlementStatus: String? = Constants.SETTLEMENT_STATUS_PENDING,
    @PropertyName("createdAt") var createdAt: Timestamp? = null,
    @PropertyName("completedAt") var completedAt: Timestamp? = null,
    @PropertyName("officeId") var officeId: String? = null,
    @PropertyName("regionId") var regionId: String? = null,
    @PropertyName("isFinalized") var isFinalized: Boolean? = false, // 마감 처리 여부
    @PropertyName("lastUpdate") var lastUpdate: Timestamp? = null
) 