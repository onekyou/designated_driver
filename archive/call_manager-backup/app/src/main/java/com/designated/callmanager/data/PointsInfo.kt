package com.designated.callmanager.data

import com.google.firebase.Timestamp

/**
 * 사무실 포인트 잔액 정보를 나타내는 데이터 클래스
 */
data class PointsInfo(
    val balance: Long = 0,
    val updatedAt: Timestamp? = null
) 