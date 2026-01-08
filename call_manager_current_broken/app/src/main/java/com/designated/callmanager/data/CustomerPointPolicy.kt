package com.designated.callmanager.data

import com.google.firebase.firestore.PropertyName

/**
 * 손님앱 포인트 정책을 나타내는 데이터 클래스
 */
data class CustomerPointPolicy(
    @PropertyName("bronzeRate") val bronzeRate: Int = 3, // 브론즈 등급 적립률 (%)
    @PropertyName("silverRate") val silverRate: Int = 5, // 실버 등급 적립률 (%)
    @PropertyName("goldRate") val goldRate: Int = 7, // 골드 등급 적립률 (%)
    @PropertyName("vipRate") val vipRate: Int = 9, // VIP 등급 적립률 (%)
    @PropertyName("bronzeThreshold") val bronzeThreshold: Int = 0, // 브론즈 등급 최소 이용 횟수
    @PropertyName("silverThreshold") val silverThreshold: Int = 6, // 실버 등급 최소 이용 횟수
    @PropertyName("goldThreshold") val goldThreshold: Int = 21, // 골드 등급 최소 이용 횟수
    @PropertyName("vipThreshold") val vipThreshold: Int = 51, // VIP 등급 최소 이용 횟수
    @PropertyName("minimumUsagePoints") val minimumUsagePoints: Int = 1000, // 최소 사용 가능 포인트
    @PropertyName("pointsExpireMonths") val pointsExpireMonths: Int = 12 // 포인트 만료 기간 (개월)
)