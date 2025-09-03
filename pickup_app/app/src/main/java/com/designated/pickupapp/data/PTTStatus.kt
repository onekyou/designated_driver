package com.designated.pickupapp.data

/**
 * PTT 상태 정보
 */
data class PTTStatus(
    val userId: String = "",
    val userName: String = "",
    val isTransmitting: Boolean = false,
    val timestamp: Long = 0L,
    val source: String = "" // "pickup" or "call_manager"
)

/**
 * 활성 PTT 사용자 목록을 관리하는 상태
 */
data class PTTState(
    val activePTTUsers: Map<String, PTTStatus> = emptyMap(),
    val currentTransmitter: String? = null
) {
    /**
     * 현재 전송 중인 사용자가 있는지 확인
     */
    fun hasActiveTransmitter(): Boolean = currentTransmitter != null
    
    /**
     * 특정 사용자의 PTT 상태 가져오기
     */
    fun getUserPTTStatus(userId: String): PTTStatus? = activePTTUsers[userId]
    
    /**
     * 현재 전송 중인 사용자 정보 가져오기
     */
    fun getCurrentTransmitter(): PTTStatus? = currentTransmitter?.let { activePTTUsers[it] }
}