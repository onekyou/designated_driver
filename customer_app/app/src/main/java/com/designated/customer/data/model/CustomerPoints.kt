package com.designated.customer.data.model

import com.google.firebase.Timestamp

/**
 * 고객 포인트 정보 데이터 모델
 */
data class CustomerPoints(
    val customerId: String = "",
    val phoneNumber: String = "",
    val currentPoints: Int = 0,
    val totalEarned: Int = 0,  // 총 적립 포인트
    val totalUsed: Int = 0,    // 총 사용 포인트
    val grade: CustomerGrade = CustomerGrade.BRONZE,
    val totalCalls: Int = 0,   // 총 이용 횟수
    val lastUpdated: Timestamp = Timestamp.now(),
    val createdAt: Timestamp = Timestamp.now()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "customerId" to customerId,
            "phoneNumber" to phoneNumber,
            "currentPoints" to currentPoints,
            "totalEarned" to totalEarned,
            "totalUsed" to totalUsed,
            "grade" to grade.name,
            "totalCalls" to totalCalls,
            "lastUpdated" to lastUpdated,
            "createdAt" to createdAt
        )
    }

    companion object {
        fun fromMap(data: Map<String, Any>): CustomerPoints {
            return CustomerPoints(
                customerId = data["customerId"] as? String ?: "",
                phoneNumber = data["phoneNumber"] as? String ?: "",
                currentPoints = (data["currentPoints"] as? Long)?.toInt() ?: 0,
                totalEarned = (data["totalEarned"] as? Long)?.toInt() ?: 0,
                totalUsed = (data["totalUsed"] as? Long)?.toInt() ?: 0,
                grade = CustomerGrade.fromString(data["grade"] as? String),
                totalCalls = (data["totalCalls"] as? Long)?.toInt() ?: 0,
                lastUpdated = data["lastUpdated"] as? Timestamp ?: Timestamp.now(),
                createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now()
            )
        }
    }

    /**
     * 등급 업데이트 필요 여부 확인
     */
    fun shouldUpdateGrade(): Boolean {
        val expectedGrade = CustomerGrade.fromCallCount(totalCalls)
        return expectedGrade != grade
    }

    /**
     * 새로운 등급 계산
     */
    fun getUpdatedGrade(): CustomerGrade {
        return CustomerGrade.fromCallCount(totalCalls)
    }

    /**
     * 포인트 사용 가능 여부
     */
    fun canUsePoints(amount: Int): Boolean {
        return currentPoints >= amount
    }

    /**
     * 다음 등급까지 필요한 콜 수
     */
    fun getCallsToNextGrade(): Int? {
        return grade.getCallsToNextGrade(totalCalls)
    }

    /**
     * 포인트 적립 계산 (등급별 적립률 적용)
     */
    fun calculateEarnPoints(fare: Int): Int {
        return grade.calculatePoints(fare)
    }
}