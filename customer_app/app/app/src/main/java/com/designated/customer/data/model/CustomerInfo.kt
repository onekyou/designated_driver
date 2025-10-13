package com.designated.customer.data.model

import com.google.firebase.Timestamp

/**
 * 고객 정보 데이터 모델
 */
data class CustomerInfo(
    val id: String = "",
    val phoneNumber: String = "",
    val name: String = "",
    val grade: String = "bronze", // bronze/silver/gold/vip
    val points: Int = 0,
    val totalRides: Int = 0,
    val totalSpent: Long = 0L,
    val linkedOfficeId: String = "",
    val primaryOfficeId: String = "", // 최초 연결 사무실 (불변)
    val attributionScore: Int? = null,
    val attributionSource: String? = null, // landing/qr_scan/referral
    val registeredAt: Timestamp = Timestamp.now(),
    val lastRideAt: Timestamp? = null,

    // 사무실 연락처 정보 (신규 추가)
    val officePhone: String = "",      // 사무실 전화번호
    val bankName: String = "",         // 은행명
    val accountNumber: String = "",    // 계좌번호
    val accountHolder: String = "",    // 예금주

    // 주소 정보
    val homeAddress: String = "",      // 집 주소
    val favoriteAddresses: List<String> = emptyList() // 즐겨찾기 주소 목록
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "phoneNumber" to phoneNumber,
            "name" to name,
            "grade" to grade,
            "points" to points,
            "totalRides" to totalRides,
            "totalSpent" to totalSpent,
            "linkedOfficeId" to linkedOfficeId,
            "primaryOfficeId" to primaryOfficeId,
            "attributionScore" to attributionScore,
            "attributionSource" to attributionSource,
            "registeredAt" to registeredAt,
            "lastRideAt" to lastRideAt,
            "officePhone" to officePhone,
            "bankName" to bankName,
            "accountNumber" to accountNumber,
            "accountHolder" to accountHolder,
            "homeAddress" to homeAddress,
            "favoriteAddresses" to favoriteAddresses
        )
    }

    companion object {
        fun fromMap(data: Map<String, Any>): CustomerInfo {
            return CustomerInfo(
                id = data["id"] as? String ?: "",
                phoneNumber = data["phoneNumber"] as? String ?: "",
                name = data["name"] as? String ?: "",
                grade = data["grade"] as? String ?: "bronze",
                points = (data["points"] as? Long)?.toInt() ?: 0,
                totalRides = (data["totalRides"] as? Long)?.toInt() ?: 0,
                totalSpent = (data["totalSpent"] as? Long) ?: 0L,
                linkedOfficeId = data["linkedOfficeId"] as? String ?: "",
                primaryOfficeId = data["primaryOfficeId"] as? String ?: "",
                attributionScore = (data["attributionScore"] as? Long)?.toInt(),
                attributionSource = data["attributionSource"] as? String,
                registeredAt = data["registeredAt"] as? Timestamp ?: Timestamp.now(),
                lastRideAt = data["lastRideAt"] as? Timestamp,
                officePhone = data["officePhone"] as? String ?: "",
                bankName = data["bankName"] as? String ?: "",
                accountNumber = data["accountNumber"] as? String ?: "",
                accountHolder = data["accountHolder"] as? String ?: "",
                homeAddress = data["homeAddress"] as? String ?: "",
                favoriteAddresses = (data["favoriteAddresses"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }
}
