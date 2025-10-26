package com.designated.callmanager.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * 손님앱 고객 정보를 나타내는 데이터 클래스
 */
data class CustomerInfo(
    @PropertyName("id") val id: String = "",
    @PropertyName("phoneNumber") val phoneNumber: String = "",
    @PropertyName("name") val name: String = "",
    @PropertyName("grade") val grade: String = "bronze", // bronze/silver/gold/vip
    @PropertyName("points") val points: Int = 0,
    @PropertyName("totalRides") val totalRides: Int = 0,
    @PropertyName("totalSpent") val totalSpent: Long = 0L,
    @PropertyName("linkedOfficeId") val linkedOfficeId: String = "",
    @PropertyName("primaryOfficeId") val primaryOfficeId: String = "", // 최초 연결 사무실 (불변)
    @PropertyName("attributionScore") val attributionScore: Int? = null,
    @PropertyName("attributionSource") val attributionSource: String? = null, // landing/qr_scan/referral
    @PropertyName("registeredAt") val registeredAt: Timestamp = Timestamp.now(),
    @PropertyName("lastRideAt") val lastRideAt: Timestamp? = null,
    @PropertyName("lastActiveAt") val lastActiveAt: Timestamp? = null // 마지막 앱 실행 시간
) {
    /**
     * 회원 활동 상태 계산
     * @return "active" (30일 이내), "warning" (30-90일), "dormant" (90일 이상)
     */
    fun getActivityStatus(): String {
        val lastActive = lastActiveAt ?: registeredAt
        val daysSinceActive = ((Timestamp.now().seconds - lastActive.seconds) / (24 * 60 * 60)).toInt()

        return when {
            daysSinceActive < 30 -> "active"
            daysSinceActive < 90 -> "warning"
            else -> "dormant"
        }
    }

    /**
     * 마지막 활동으로부터 경과한 일수
     */
    fun getDaysSinceActive(): Int {
        val lastActive = lastActiveAt ?: registeredAt
        return ((Timestamp.now().seconds - lastActive.seconds) / (24 * 60 * 60)).toInt()
    }
}