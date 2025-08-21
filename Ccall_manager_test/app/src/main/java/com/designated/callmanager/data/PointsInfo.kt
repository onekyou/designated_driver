package com.designated.callmanager.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * 포인트 잔액 정보를 나타내는 데이터 클래스
 */
data class PointsInfo(
    @PropertyName("balance") val balance: Int = 0,
    @PropertyName("updatedAt") val updatedAt: Timestamp? = null
) {
    constructor() : this(0, null)
}

/**
 * 포인트 거래 내역을 나타내는 데이터 클래스
 */
data class PointTransaction(
    var id: String = "",
    @PropertyName("type") val type: String = "", // "CHARGE", "SHARED_CALL_SEND", "SHARED_CALL_RECEIVE"
    @PropertyName("amount") val amount: Int = 0,
    @PropertyName("description") val description: String = "",
    @PropertyName("timestamp") val timestamp: Timestamp? = null,
    @PropertyName("regionId") val regionId: String = "",
    @PropertyName("officeId") val officeId: String = "",
    @PropertyName("relatedSharedCallId") val relatedSharedCallId: String? = null
) {
    constructor() : this("", "", 0, "", null, "", "", null)
}