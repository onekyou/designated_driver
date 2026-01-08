package com.designated.callmanager.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * 손님앱 포인트 거래 내역을 나타내는 데이터 클래스
 * (기존 PointTransaction은 공유콜용, 이것은 손님앱용)
 */
data class CustomerPointTransaction(
    @PropertyName("id") val id: String = "",
    @PropertyName("customerId") val customerId: String = "",
    @PropertyName("customerPhoneNumber") val customerPhoneNumber: String = "",
    @PropertyName("type") val type: String = "", // EARN/USE/EXPIRE/ADMIN/BONUS
    @PropertyName("amount") val amount: Int = 0,
    @PropertyName("balance") val balance: Int = 0,
    @PropertyName("description") val description: String = "",
    @PropertyName("relatedCallId") val relatedCallId: String? = null,
    @PropertyName("customerGrade") val customerGrade: String? = null, // bronze/silver/gold/vip
    @PropertyName("rideNumber") val rideNumber: Int? = null, // 몇 번째 이용인지
    @PropertyName("timestamp") val timestamp: Timestamp = Timestamp.now()
)