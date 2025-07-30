package com.designated.callmanager.data

import com.google.firebase.Timestamp

// 공유 콜 문서 매핑용
// Firestore 컬렉션: /shared_calls/{docId}

data class SharedCallInfo(
    val id: String = "",
    val status: String = "OPEN",      // OPEN | CLAIMED | COMPLETED 등
    val departure: String? = null,
    val destination: String? = null,
    val fare: Int? = null,
    val sourceRegionId: String = "",
    val sourceOfficeId: String = "",
    val targetRegionId: String = "",
    val claimedOfficeId: String? = null,
    val createdBy: String = "",
    val timestamp: Timestamp? = null,
    val phoneNumber: String? = null,  // 고객 전화번호 (선택)
    val claimedAt: Timestamp? = null, // CLAIMED 시각
    val completedAt: Timestamp? = null, // COMPLETED 시각
    val destCallId: String? = null    // 복사된 콜의 ID
) 