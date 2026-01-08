package com.designated.driverapp.data.model

/**
 * 고객 포인트 정보 데이터 모델
 */
data class CustomerPoints(
    val customerId: String = "",
    val phoneNumber: String = "",
    val currentPoints: Int = 0,
    val totalEarned: Int = 0,
    val totalUsed: Int = 0,
    val grade: String = "BRONZE",
    val totalCalls: Int = 0,
    val lastUpdated: Any? = null,
    val createdAt: Any? = null
) {
    /**
     * Firestore 저장용 Map 변환
     */
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "customerId" to customerId,
            "phoneNumber" to phoneNumber,
            "currentPoints" to currentPoints,
            "totalEarned" to totalEarned,
            "totalUsed" to totalUsed,
            "grade" to grade,
            "totalCalls" to totalCalls
        )
        lastUpdated?.let { map["lastUpdated"] = it }
        createdAt?.let { map["createdAt"] = it }
        return map
    }

    companion object {
        /**
         * Firestore Document에서 변환
         */
        fun fromMap(map: Map<String, Any>): CustomerPoints {
            return CustomerPoints(
                customerId = map["customerId"] as? String ?: "",
                phoneNumber = map["phoneNumber"] as? String ?: "",
                currentPoints = (map["currentPoints"] as? Long)?.toInt() ?: 0,
                totalEarned = (map["totalEarned"] as? Long)?.toInt() ?: 0,
                totalUsed = (map["totalUsed"] as? Long)?.toInt() ?: 0,
                grade = map["grade"] as? String ?: "BRONZE",
                totalCalls = (map["totalCalls"] as? Long)?.toInt() ?: 0,
                lastUpdated = map["lastUpdated"],
                createdAt = map["createdAt"]
            )
        }
    }
}
