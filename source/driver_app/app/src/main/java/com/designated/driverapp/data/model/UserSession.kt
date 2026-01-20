package com.designated.driverapp.data.model

/**
 * 사용자 세션 정보 (오프라인 지원용)
 */
data class UserSession(
    val userId: String,
    val email: String,
    val provinceId: String,
    val cityId: String,
    val officeId: String,
    val driverId: String,
    val driverName: String,
    val fcmToken: String?,
    val lastLoginTime: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true
) {
    /**
     * 세션이 만료되었는지 확인 (7일 기준)
     */
    fun isExpired(): Boolean {
        val sessionValidityMs = 7 * 24 * 60 * 60 * 1000L // 7일
        return System.currentTimeMillis() - lastLoginTime > sessionValidityMs
    }

    /**
     * SharedPreferences 저장용 Map 변환
     */
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "email" to email,
            "provinceId" to provinceId,
            "cityId" to cityId,
            "officeId" to officeId,
            "driverId" to driverId,
            "driverName" to driverName,
            "lastLoginTime" to lastLoginTime,
            "isOnline" to isOnline
        )
        fcmToken?.let { map["fcmToken"] = it }
        return map
    }

    companion object {
        /**
         * Map에서 UserSession 복원
         */
        fun fromMap(map: Map<String, Any>): UserSession? {
            return try {
                UserSession(
                    userId = map["userId"] as? String ?: return null,
                    email = map["email"] as? String ?: return null,
                    provinceId = map["provinceId"] as? String ?: return null,
                    cityId = map["cityId"] as? String ?: return null,
                    officeId = map["officeId"] as? String ?: return null,
                    driverId = map["driverId"] as? String ?: return null,
                    driverName = map["driverName"] as? String ?: "",
                    fcmToken = map["fcmToken"] as? String,
                    lastLoginTime = (map["lastLoginTime"] as? Long) ?: System.currentTimeMillis(),
                    isOnline = (map["isOnline"] as? Boolean) ?: true
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
