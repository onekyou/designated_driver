package com.designated.callmanager.data

import com.google.firebase.Timestamp

/**
 * 포인트 거래 내역 데이터 모델
 */
data class PointTransaction(
    val amount: Long = 0,
    val fromOfficeId: String = "",
    val toOfficeId: String = "",
    val callId: String = "",
    val timestamp: Timestamp? = null
) 