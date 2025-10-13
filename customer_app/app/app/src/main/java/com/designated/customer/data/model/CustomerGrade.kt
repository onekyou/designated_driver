package com.designated.customer.data.model

/**
 * 고객 등급 정의
 * 콜매니저와 동일한 등급 체계 사용
 */
enum class CustomerGrade(
    val displayName: String,
    val icon: String,
    val pointRate: Double,
    val minCalls: Int,
    val color: Long
) {
    BRONZE(
        displayName = "브론즈",
        icon = "🥉",
        pointRate = 0.03,  // 3% 적립
        minCalls = 0,
        color = 0xFFCD7F32
    ),
    SILVER(
        displayName = "실버",
        icon = "🥈",
        pointRate = 0.05,  // 5% 적립
        minCalls = 10,     // 10회 이상 이용
        color = 0xFFC0C0C0
    ),
    GOLD(
        displayName = "골드",
        icon = "🥇",
        pointRate = 0.07,  // 7% 적립
        minCalls = 30,     // 30회 이상 이용
        color = 0xFFFFD700
    ),
    VIP(
        displayName = "VIP",
        icon = "⭐",
        pointRate = 0.09,  // 9% 적립
        minCalls = 50,     // 50회 이상 이용
        color = 0xFFFF6B6B
    );

    companion object {
        /**
         * 이용 횟수에 따른 등급 계산
         */
        fun fromCallCount(callCount: Int): CustomerGrade {
            return when {
                callCount >= 50 -> VIP
                callCount >= 30 -> GOLD
                callCount >= 10 -> SILVER
                else -> BRONZE
            }
        }

        /**
         * 문자열로부터 등급 변환
         */
        fun fromString(grade: String?): CustomerGrade {
            return when(grade?.uppercase()) {
                "VIP" -> VIP
                "GOLD" -> GOLD
                "SILVER" -> SILVER
                else -> BRONZE
            }
        }
    }

    /**
     * 다음 등급까지 필요한 콜 수
     */
    fun getCallsToNextGrade(currentCalls: Int): Int? {
        val nextGrade = when(this) {
            BRONZE -> SILVER
            SILVER -> GOLD
            GOLD -> VIP
            VIP -> return null // 최고 등급
        }
        return nextGrade.minCalls - currentCalls
    }

    /**
     * 포인트 적립 계산
     */
    fun calculatePoints(amount: Int): Int {
        return (amount * pointRate).toInt()
    }
}