package com.designated.pickupdriver.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database용 픽업앱 콜 Entity (사무실 전체 콜 미러)
 *
 * - Firestore `provinces/{p}/cities/{c}/offices/{o}/calls/{id}` 의 부분 미러
 * - FCM (`NEW_CALL`, `CALL_STATUS_UPDATE`) 트리거로 INSERT/UPDATE
 * - 첫 진입 시 1회 fetch (`CallRepository.refreshData`)
 *
 * 컬럼명 표준 — Firestore 의 `_set` 접미사 필드는 단일 이름으로 통일.
 * 진입점에서 매핑:
 *  - Firestore: departure_set → LocalCall.departure
 *  - FCM payload: departure → LocalCall.departure
 */
@Entity(tableName = "calls")
data class LocalCall(
    @PrimaryKey
    val id: String,

    // 사무실 정보
    val provinceId: String,
    val cityId: String,
    val officeId: String,

    // 기본
    val status: String,
    val timestamp: Long,

    // 고객
    val customerName: String?,
    val customerPhone: String?,
    val customerAddress: String?,

    // 운행 (Firestore _set 접미사 → 단일 컬럼)
    val departure: String?,
    val destination: String?,
    val waypoints: String?,
    val fare: Long?,

    // 배차
    val assignedDriverId: String?,
    val assignedDriverName: String?,
    val assignedDriverPhone: String?,

    // 분류
    val callType: String?,

    // 메타데이터
    val lastUpdated: Long = System.currentTimeMillis()
)
