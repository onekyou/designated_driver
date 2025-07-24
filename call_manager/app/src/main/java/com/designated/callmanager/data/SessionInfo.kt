package com.designated.callmanager.data

import com.google.firebase.Timestamp

/**
 * 업무 마감(초기화) 시 서버 Cloud Function 이 생성하는 세션 카드 정보
 */
data class SessionInfo(
    val sessionId: String = "",
    val endAt: Timestamp? = null,
    val totalFare: Long = 0,
    val totalTrips: Int = 0
) 