package com.designated.callmanager.data

import com.google.firebase.Timestamp

// Firestore의 /pending_drivers 문서 구조에 맞춤
data class PendingDriverInfo(
    val authUid: String = "", // Firebase Auth UID
    val name: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val driverType: String = "대리기사", // 또는 다른 기본값
    val status: String = "승인대기중",
    val requestedAt: Timestamp? = null,
    val targetRegionId: String = "", // 승인될 지역 ID
    val targetOfficeId: String = ""  // 승인될 사무실 ID
    // Firestore 문서 ID는 별도로 관리하거나 필요 시 추가
    // var documentId: String? = null
) 