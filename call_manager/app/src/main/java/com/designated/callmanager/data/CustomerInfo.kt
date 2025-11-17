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
    @PropertyName("lastActiveAt") val lastActiveAt: Timestamp? = null, // 마지막 앱 실행 시간

    // 기사 추천 정보
    @PropertyName("referralDriverId") val referralDriverId: String? = null, // 추천한 기사 ID
    @PropertyName("referralDriverName") val referralDriverName: String? = null, // 추천한 기사 이름

    // ✅ customerPoints 컬렉션에서 조회한 실제 포인트 정보
    val currentPoints: Int = 0, // 실제 사용 가능한 포인트 잔액
    val totalEarned: Int = 0,   // 총 적립 포인트
    val totalUsed: Int = 0,     // 총 사용 포인트
    val totalCalls: Int = 0,    // 총 이용 횟수 (customerPoints 기준)
    val customerGrade: String = "BRONZE" // 포인트 시스템의 등급 (BRONZE/SILVER/GOLD/PLATINUM/DIAMOND)
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